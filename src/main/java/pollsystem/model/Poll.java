package pollsystem.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * סקר בודד: השאלות שלו, רשימת המשתתפים שלו (snapshot נפרד מהקהילה הגלובלית)
 * ומצבו הנוכחי (סעיפים 3-11).
 */
public final class Poll {
    /** סעיף 9: הזמן המרבי למענה על סקר. */
    public static final int MAX_DURATION_SECONDS = 5 * 60;
    /** סעיף 10: מועד שליחת התזכורת למי שטרם השלים. */
    public static final int REMINDER_AFTER_SECONDS = 3 * 60;

    private final String id;
    private final List<Question> questions;
    private final LocalDateTime createdAt;
    private final LocalDateTime scheduledSendTime; // null אם שליחה מיידית (סעיף 4)
    private volatile LocalDateTime startedAt;       // כאשר הסקר בפועל נשלח והופך ל-ACTIVE
    private volatile LocalDateTime closesAt;        // startedAt + 5 דקות
    private volatile LocalDateTime closedAt;
    private final List<PollParticipant> participants = new CopyOnWriteArrayList<>();
    private volatile PollState state = PollState.PENDING;

    public Poll(List<Question> questions, LocalDateTime createdAt, LocalDateTime scheduledSendTime) {
        if (questions == null || questions.isEmpty() || questions.size() > 3) {
            throw new IllegalArgumentException("סקר חייב להכיל בין 1 ל-3 שאלות"); // סעיף 3
        }
        this.id = UUID.randomUUID().toString();
        this.questions = List.copyOf(questions);
        this.createdAt = createdAt;
        this.scheduledSendTime = scheduledSendTime;
    }

    public String getId() { return id; }
    public List<Question> getQuestions() { return questions; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getScheduledSendTime() { return scheduledSendTime; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getClosesAt() { return closesAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public PollState getState() { return state; }
    public List<PollParticipant> getParticipants() { return participants; }

    /**
     * נקרא מ-PollManager ברגע שהסקר בפועל מתחיל (לא ברגע היצירה!).
     * סעיף 5: "כאשר סקר מתחיל, חברי הקהילה הקיימים באותו רגע יהיו המשתתפים
     * של אותו סקר" - זו הפעולה שלוקחת את ה-snapshot הזה.
     */
    public void activate(List<CommunityMember> communitySnapshot, LocalDateTime now) {
        for (CommunityMember m : communitySnapshot) {
            participants.add(new PollParticipant(m.getUserId(), m.getDisplayName()));
        }
        this.startedAt = now;
        this.closesAt = now.plusSeconds(MAX_DURATION_SECONDS);
        this.state = PollState.ACTIVE;
    }

    public void close(LocalDateTime now) {
        this.closedAt = now;
        this.state = PollState.CLOSED;
    }

    public PollParticipant getParticipant(long userId) {
        for (PollParticipant p : participants) {
            if (p.getUserId() == userId) return p;
        }
        return null;
    }

    public boolean allComplete() {
        if (participants.isEmpty()) return false;
        for (PollParticipant p : participants) {
            if (!p.isComplete(questions.size())) return false;
        }
        return true;
    }

    public int completedCount() {
        int count = 0;
        for (PollParticipant p : participants) {
            if (p.isComplete(questions.size())) count++;
        }
        return count;
    }

    /**
     * תוצאות סופיות, ממוינות לפי שכיחות יורדת בתוך כל שאלה (סעיף 11).
     * המיון יציב (List.sort של Java הוא stable), כך שבמקרה של שוויון בין
     * שתי אפשרויות, סדר ההזנה המקורי נשמר כטיברייקר.
     */
    public List<QuestionResult> computeResults() {
        List<QuestionResult> results = new ArrayList<>();
        int totalParticipants = participants.size();
        for (int qIdx = 0; qIdx < questions.size(); qIdx++) {
            Question q = questions.get(qIdx);
            int[] counts = new int[q.options().size()];
            for (PollParticipant p : participants) {
                Integer answer = p.getAnswer(qIdx);
                if (answer != null && answer >= 0 && answer < counts.length) {
                    counts[answer]++;
                }
            }
            List<OptionResult> optionResults = new ArrayList<>();
            for (int optIdx = 0; optIdx < q.options().size(); optIdx++) {
                double pct = totalParticipants == 0 ? 0.0 : (100.0 * counts[optIdx] / totalParticipants);
                optionResults.add(new OptionResult(q.options().get(optIdx), counts[optIdx], pct));
            }
            optionResults.sort((a, b) -> Integer.compare(b.votes(), a.votes()));
            results.add(new QuestionResult(q.text(), optionResults));
        }
        return results;
    }

    public record OptionResult(String optionText, int votes, double percentage) {}
    public record QuestionResult(String questionText, List<OptionResult> options) {}
}
