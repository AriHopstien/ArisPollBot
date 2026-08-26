package pollsystem.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * משתתף בסקר ספציפי (סעיף 5) - נפרד לחלוטין מהמידע הגלובלי על חבר הקהילה.
 * מנהל אילו שאלות (לפי אינדקס) כבר נענו על ידי המשתמש הזה, ובאיזו אופציה
 * בכל אחת. אותו משתמש יכול להיות "השלים" בסקר אחד ו-"טרם ענה" בסקר הבא -
 * המצב הזה חי כאן ולא על CommunityMember (סעיף 5, ההערה על דני).
 */
public final class PollParticipant {
    private final long userId;
    private final String displayName; // תמונת מצב של השם בזמן תחילת הסקר
    private final Map<Integer, Integer> answers = new ConcurrentHashMap<>(); // questionIndex -> optionIndex
    private volatile boolean reminderSent = false;

    public PollParticipant(long userId, String displayName) {
        this.userId = userId;
        this.displayName = displayName;
    }

    public long getUserId() { return userId; }
    public String getDisplayName() { return displayName; }

    public boolean hasAnswered(int questionIndex) {
        return answers.containsKey(questionIndex);
    }

    /**
     * רושם תשובה לשאלה. מחזיר false אם כבר היה קיים מענה לשאלה זו -
     * סעיף 7: "לאחר שמשתמש ענה על שאלה, אין לאפשר לו לענות עליה פעם נוספת".
     */
    public boolean recordAnswer(int questionIndex, int optionIndex) {
        return answers.putIfAbsent(questionIndex, optionIndex) == null;
    }

    public int answeredCount() {
        return answers.size();
    }

    public boolean isComplete(int totalQuestions) {
        return answers.size() >= totalQuestions;
    }

    public Integer getAnswer(int questionIndex) {
        return answers.get(questionIndex);
    }

    public boolean isReminderSent() { return reminderSent; }
    public void markReminderSent() { reminderSent = true; }
}
