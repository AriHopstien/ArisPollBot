package pollsystem.bot;

import pollsystem.core.AppEvent;
import pollsystem.core.AppState;
import pollsystem.core.PollManager;
import pollsystem.model.Community;
import pollsystem.model.CommunityMember;
import pollsystem.model.Poll;
import pollsystem.model.PollParticipant;
import pollsystem.model.PollState;
import pollsystem.util.Json;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * הלולאה הראשית של הבוט: long polling מול getUpdates, וניתוב כל update
 * להצטרפות לקהילה (סעיף 1) או לתשובה על שאלת סקר (סעיף 7).
 */
public final class PollTelegramBot implements Runnable {
    // סעיף 1: שלוש הפעולות היחידות שגורמות להצטרפות - לחיצת Start שולחת "/start",
    // ובנוסף "Hi" (לא תלוי רישיות) ו-"היי" כטקסט חופשי.
    private static final Set<String> JOIN_TRIGGERS = Set.of("/start", "hi", "היי");

    private final TelegramApiClient api;
    private final AppState state;
    private final PollManager pollManager;
    private final PollDispatcher dispatcher;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong updateOffset = new AtomicLong(0);
    private Thread thread;

    public PollTelegramBot(TelegramApiClient api, AppState state, PollManager pollManager, PollDispatcher dispatcher) {
        this.api = api;
        this.state = state;
        this.pollManager = pollManager;
        this.dispatcher = dispatcher;
    }

    public void start() {
        thread = new Thread(this, "telegram-bot-poll-loop");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running.set(false);
        if (thread != null) thread.interrupt();
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                List<Object> updates = api.getUpdates(updateOffset.get(), 30);
                for (Object updateObj : updates) {
                    Map<String, Object> update = Json.asMap(updateObj);
                    if (update == null) continue;
                    long updateId = Json.asLong(update.get("update_id"));
                    updateOffset.set(updateId + 1);
                    handleUpdate(update);
                }
            } catch (Exception e) {
                if (running.get()) {
                    System.err.println("[bot] שגיאה בלולאת ה-polling: " + e.getMessage());
                    sleepQuietly(2000); // מונע לולאת שגיאות מהירה מדי אם הרשת/הטוקן לא תקינים
                }
            }
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleUpdate(Map<String, Object> update) {
        Map<String, Object> message = Json.get(update, "message");
        if (message != null) {
            handleMessage(message);
            return;
        }
        Map<String, Object> callback = Json.get(update, "callback_query");
        if (callback != null) {
            handleCallbackQuery(callback);
        }
    }

    private void handleMessage(Map<String, Object> message) {
        Map<String, Object> from = Json.get(message, "from");
        Map<String, Object> chat = Json.get(message, "chat");
        if (from == null || chat == null) return;

        long userId = Json.asLong(from.get("id"));
        long chatId = Json.asLong(chat.get("id"));
        String text = Json.asString(message.get("text"));
        String normalized = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);

        if (!JOIN_TRIGGERS.contains(normalized)) {
            // סעיף 1: "כל הודעה אחרת לא תגרום לצירוף המשתמש לקהילה" - אין כאן שינוי
            // מצב. תוספת UX קלה: הכוונה עדינה למי שעדיין אינו חבר (לא נדרשת במפרט).
            if (!state.getCommunity().isMember(userId)) {
                api.sendMessage(chatId, "שילחו 'Hi' או 'היי', או לחצו על Start, כדי להצטרף לקהילה.");
            }
            return;
        }

        String username = Json.asString(from.get("username"));
        String firstName = Json.asString(from.get("first_name"));
        String lastName = Json.asString(from.get("last_name"));
        String displayName = (firstName == null ? "" : firstName) + (lastName == null ? "" : " " + lastName);
        displayName = displayName.isBlank() ? ("משתמש " + userId) : displayName.trim();

        CommunityMember candidate = new CommunityMember(userId, username, displayName, LocalDateTime.now());
        CommunityMember added = state.getCommunity().addIfAbsent(candidate);

        if (added == null) {
            api.sendMessage(chatId, "כבר רשומ/ה בקהילה."); // סעיף 1: לא מצורף פעם נוספת
            return;
        }

        Community community = state.getCommunity();
        int size = community.size();
        api.sendMessage(chatId, "ברוך/ה הבא/ה, " + displayName + "! הצטרפת לקהילה בהצלחה.");
        dispatcher.broadcastJoin(added, size); // סעיף 1: שידור לכל שאר החברים
        state.getEventBus().publish(new AppEvent.MemberJoined(added, size)); // עדכון ה-GUI
    }

    private void handleCallbackQuery(Map<String, Object> callback) {
        String callbackId = Json.asString(callback.get("id"));
        Map<String, Object> from = Json.get(callback, "from");
        Map<String, Object> msg = Json.get(callback, "message");
        String data = Json.asString(callback.get("data"));
        if (from == null || msg == null || data == null) {
            api.answerCallbackQuery(callbackId, null);
            return;
        }
        long userId = Json.asLong(from.get("id"));
        Map<String, Object> chat = Json.get(msg, "chat");
        long chatId = Json.asLong(chat.get("id"));
        long messageId = Json.asLong(msg.get("message_id"));

        // callback_data בפורמט: poll:<pollId>:<questionIndex>:<optionIndex>
        String[] parts = data.split(":", 4);
        if (parts.length != 4 || !parts[0].equals("poll")) {
            api.answerCallbackQuery(callbackId, "בקשה לא תקינה");
            return;
        }
        String pollId = parts[1];
        int questionIndex;
        int optionIndex;
        try {
            questionIndex = Integer.parseInt(parts[2]);
            optionIndex = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            api.answerCallbackQuery(callbackId, "בקשה לא תקינה");
            return;
        }

        Poll poll = state.getCurrentPoll();
        if (poll == null || !poll.getId().equals(pollId) || poll.getState() != PollState.ACTIVE) {
            api.answerCallbackQuery(callbackId, "הסקר כבר אינו פעיל");
            return;
        }

        PollParticipant participant = poll.getParticipant(userId);
        if (participant != null && participant.hasAnswered(questionIndex)) {
            api.answerCallbackQuery(callbackId, "כבר ענית על שאלה זו");
            return;
        }

        boolean ok = pollManager.recordAnswer(poll, userId, questionIndex, optionIndex);
        if (!ok) {
            api.answerCallbackQuery(callbackId, "לא ניתן היה לרשום את התשובה");
            return;
        }

        String chosenOption = poll.getQuestions().get(questionIndex).options().get(optionIndex);
        String questionText = poll.getQuestions().get(questionIndex).text();
        api.answerCallbackQuery(callbackId, "התשובה נקלטה");
        api.editMessageText(chatId, messageId,
                questionText + "\n\nבחרת: " + chosenOption,
                TelegramApiClient.emptyKeyboard());
    }
}
