package pollsystem.bot;

import pollsystem.core.AppState;
import pollsystem.model.CommunityMember;
import pollsystem.model.Poll;
import pollsystem.model.PollParticipant;
import pollsystem.model.Question;

import java.util.List;

/**
 * אחראי על כל התקשורת היוצאת מהבוט: שידור הצטרפות חברים חדשים, שליחת שאלות
 * הסקר, תזכורות, והודעת סיום (סעיפים 1, 6, 10).
 */
public final class PollDispatcher {
    private final TelegramApiClient api;
    private final AppState state;

    public PollDispatcher(TelegramApiClient api, AppState state) {
        this.api = api;
        this.state = state;
    }

    /** סעיף 1: "כל שאר חברי הקהילה יקבלו הודעה על הצטרפות החבר החדש... שם + גודל קהילה עדכני". */
    public void broadcastJoin(CommunityMember newMember, int communitySize) {
        String text = newMember.getDisplayName() + " הצטרפ/ה לקהילה! (סה\"כ חברים כעת: " + communitySize + ")";
        for (CommunityMember member : state.getCommunity().snapshot()) {
            if (member.getUserId() == newMember.getUserId()) continue; // לא לשלוח למצטרף עצמו
            api.sendMessage(member.getUserId(), text);
        }
    }

    /** סעיף 6: שליחת כל שאלות הסקר לכל משתתפי הסקר, כל שאלה עם כפתורי בחירה משלה. */
    public void sendPollToParticipants(Poll poll) {
        List<Question> questions = poll.getQuestions();
        for (PollParticipant participant : poll.getParticipants()) {
            long chatId = participant.getUserId(); // בצ'אט פרטי מול הבוט, chat_id == user_id
            api.sendMessage(chatId, "סקר חדש התחיל! " + questions.size() +
                    (questions.size() == 1 ? " שאלה ממתינה לך." : " שאלות ממתינות לך."));
            for (int i = 0; i < questions.size(); i++) {
                Question q = questions.get(i);
                String callbackPrefix = "poll:" + poll.getId() + ":" + i;
                Object keyboard = TelegramApiClient.buildOptionsKeyboard(q.options(), callbackPrefix);
                api.sendMessage(chatId, (i + 1) + ". " + q.text(), keyboard);
            }
        }
    }

    /** סעיף 10: תזכורת למשתתף שטרם השלים - נשלחת פעם אחת בלבד לכל סקר (האכיפה נמצאת ב-PollManager). */
    public void sendReminder(Poll poll, PollParticipant participant) {
        int total = poll.getQuestions().size();
        int answered = participant.answeredCount();
        int remaining = total - answered;
        String questionWord = remaining == 1 ? "שאלה" : "שאלות";
        api.sendMessage(participant.getUserId(),
                "תזכורת: ענית על " + answered + " מתוך " + total +
                        " שאלות בסקר הפעיל. נותרו לך עוד " + remaining + " " + questionWord + " להשלמה.");
    }

    /**
     * תוספת UX שאינה נדרשת במפורש במפרט: הודעת סיום נעימה למשתתפים כשהסקר
     * נסגר. אפשר להסיר בקלות אם רוצים להיצמד ללשון המפרט המדויקת בלבד.
     */
    public void notifyPollClosed(Poll poll) {
        for (PollParticipant participant : poll.getParticipants()) {
            api.sendMessage(participant.getUserId(), "הסקר הסתיים. תודה שהשתתפת!");
        }
    }
}
