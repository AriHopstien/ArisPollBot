package pollsystem.core;

import pollsystem.model.CommunityMember;
import pollsystem.model.Poll;
import pollsystem.model.PollParticipant;
import pollsystem.model.PollState;
import pollsystem.model.Question;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * מנהל את מחזור החיים המלא של סקר: יצירה ותזמון (מיידי/מעוכב), הפעלה בפועל
 * (כולל snapshot של המשתתפים), מעקב תשובות, תזכורת אחרי 3 דקות, וסגירה אחרי
 * 5 דקות או בהשלמה מוקדמת של כולם (סעיפים 4, 5, 6-10, 12).
 * <p>
 * שולח עדכונים דרך ה-EventBus כדי שה-GUI יתעדכן בזמן אמת, ומפעיל callbacks
 * (שמוזרקים מ-Main) כדי לגרום לבוט לשלוח בפועל הודעות בטלגרם - כך ש-PollManager
 * עצמו אינו תלוי ב-Telegram API הקונקרטי.
 */
public final class PollManager {
    public static final int MIN_COMMUNITY_SIZE = 1; // סעיף 4

    private final AppState state;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "poll-scheduler");
        t.setDaemon(true);
        return t;
    });

    private Consumer<Poll> onPollActivated;                 // לשליחה בפועל של השאלות דרך הבוט
    private BiConsumer<Poll, PollParticipant> onReminderDue; // לשליחת תזכורת למשתתף בודד
    private Consumer<Poll> onPollClosed;                      // הודעת סיום אופציונלית

    private ScheduledFuture<?> reminderTask;
    private ScheduledFuture<?> closeTask;

    public PollManager(AppState state) {
        this.state = state;
    }

    public void setOnPollActivated(Consumer<Poll> callback) { this.onPollActivated = callback; }
    public void setOnReminderDue(BiConsumer<Poll, PollParticipant> callback) { this.onReminderDue = callback; }
    public void setOnPollClosed(Consumer<Poll> callback) { this.onPollClosed = callback; }

    public boolean canStartNewPoll() {
        Poll current = state.getCurrentPoll();
        boolean noActivePoll = current == null || current.getState() == PollState.CLOSED;
        return noActivePoll && state.getCommunity().size() >= MIN_COMMUNITY_SIZE;
    }

    /**
     * יוצר סקר חדש ומתזמן את שליחתו. מחזיר את הסקר שנוצר, או null אם לא ניתן
     * להתחיל (סקר אחר כבר פעיל, או פחות מ-3 חברי קהילה - סעיפים 4, 12) - במקרה
     * כזה משודר PollRejected עם הודעה מתאימה להצגה ב-GUI.
     */
    public synchronized Poll createAndSchedule(List<Question> questions, int delayMinutes) {
        Poll current = state.getCurrentPoll();
        if (current != null && current.getState() != PollState.CLOSED) {
            state.getEventBus().publish(new AppEvent.PollRejected("כבר קיים סקר פעיל. יש להמתין לסיומו."));
            return null;
        }
        int communitySize = state.getCommunity().size();
        if (communitySize < MIN_COMMUNITY_SIZE) {
            state.getEventBus().publish(new AppEvent.PollRejected(
                    "לא ניתן להתחיל סקר: נדרשים לפחות " + MIN_COMMUNITY_SIZE +
                            " חברי קהילה (קיימים כרגע " + communitySize + ")."));
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scheduledTime = delayMinutes > 0 ? now.plusMinutes(delayMinutes) : null;
        Poll poll = new Poll(questions, now, scheduledTime);
        state.setCurrentPoll(poll);
        state.getEventBus().publish(new AppEvent.PollCreated(poll));

        long delaySeconds = delayMinutes > 0 ? delayMinutes * 60L : 0L;
        scheduler.schedule(() -> activatePoll(poll), delaySeconds, TimeUnit.SECONDS);
        return poll;
    }

    private synchronized void activatePoll(Poll poll) {
        LocalDateTime now = LocalDateTime.now();
        // סעיף 5: המשתתפים הם חברי הקהילה הקיימים ברגע שהסקר *בפועל* מתחיל -
        // לא ברגע היצירה. מי שהצטרף במהלך ה-Countdown עדיין ייכלל; מי שמצטרף
        // אחרי הנקודה הזו - לא (למרות שהוא כן יופיע ברשימת הקהילה מיד).
        List<CommunityMember> snapshot = state.getCommunity().snapshot();
        poll.activate(snapshot, now);

        if (onPollActivated != null) onPollActivated.accept(poll);
        state.getEventBus().publish(new AppEvent.PollStarted(poll));

        reminderTask = scheduler.schedule(() -> sendReminders(poll),
                Poll.REMINDER_AFTER_SECONDS, TimeUnit.SECONDS); // סעיף 10
        closeTask = scheduler.schedule(() -> closePoll(poll),
                Poll.MAX_DURATION_SECONDS, TimeUnit.SECONDS);   // סעיף 9
    }

    private void sendReminders(Poll poll) {
        if (poll.getState() != PollState.ACTIVE) return; // כבר נסגר - אין לשלוח תזכורות (סעיף 10)
        for (PollParticipant p : poll.getParticipants()) {
            if (!p.isComplete(poll.getQuestions().size()) && !p.isReminderSent()) {
                p.markReminderSent(); // לכל היותר תזכורת אחת למשתתף (סעיף 10)
                if (onReminderDue != null) onReminderDue.accept(poll, p);
            }
        }
    }

    /**
     * נקרא מהבוט בכל פעם שמתקבל callback עם תשובה. מחזיר true אם התשובה נקלטה
     * בפועל (כלומר: הסקר פעיל, המשתמש משתתף בו, והוא לא ענה כבר על השאלה הזו).
     */
    public synchronized boolean recordAnswer(Poll poll, long userId, int questionIndex, int optionIndex) {
        if (poll == null || poll.getState() != PollState.ACTIVE) return false;
        PollParticipant participant = poll.getParticipant(userId);
        if (participant == null) return false; // המשתמש אינו נמנה עם משתתפי הסקר הזה (סעיף 5)
        if (questionIndex < 0 || questionIndex >= poll.getQuestions().size()) return false;
        int optionCount = poll.getQuestions().get(questionIndex).options().size();
        if (optionIndex < 0 || optionIndex >= optionCount) return false;

        boolean recorded = participant.recordAnswer(questionIndex, optionIndex); // סעיף 7
        if (!recorded) return false; // כבר היה קיים מענה לשאלה זו

        state.getEventBus().publish(new AppEvent.PollProgressUpdated(poll, userId));

        if (poll.allComplete()) {
            closePoll(poll); // סעיף 9: סגירה מיידית כשכולם השלימו את כל השאלות
        }
        return true;
    }

    private synchronized void closePoll(Poll poll) {
        if (poll.getState() != PollState.ACTIVE) return; // כבר נסגר קודם לכן
        poll.close(LocalDateTime.now());
        if (reminderTask != null) reminderTask.cancel(false);
        if (closeTask != null) closeTask.cancel(false);
        state.getEventBus().publish(new AppEvent.PollClosed(poll));
        if (onPollClosed != null) onPollClosed.accept(poll);
    }

    /** כיבוי מסודר של ה-scheduler (נקרא מ-shutdown hook ב-Main). */
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
