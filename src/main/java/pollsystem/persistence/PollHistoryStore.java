package pollsystem.persistence;

import pollsystem.model.Poll;
import pollsystem.model.Question;
import pollsystem.util.Json;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * שומר היסטוריית סקרים שנסגרו לקובץ JSON על הדיסק (data/poll-history.json),
 * כדי שתוצאות סקרים קודמים לא ייעלמו כשהתוכנה נסגרת. כל סקר שנסגר נוסף
 * כרשומה חדשה למערך שבקובץ (append) - לא נשמר per-participant answer גולמי,
 * אלא סיכום: השאלות, מס' המשתתפים/שהשלימו, והתוצאות המחושבות
 * (Poll.computeResults(), אותו חישוב שמוצג ב-PollResultsPanel).
 */
public final class PollHistoryStore {
    private final Path filePath;

    public PollHistoryStore() {
        this(Path.of("data", "poll-history.json"));
    }

    public PollHistoryStore(Path filePath) {
        this.filePath = filePath;
    }

    /** מוסיף את הסקר שזה עתה נסגר לקובץ ההיסטוריה (קורא את הקיים, מוסיף, וכותב הכל מחדש). */
    public synchronized void append(Poll poll) {
        List<Object> history = loadRaw();
        history.add(toJson(poll));
        JsonFileIO.writeAtomic(filePath, Json.stringify(history));
    }

    /** טוען את כל היסטוריית הסקרים השמורה, כרשומות JSON גולמיות (Map/List), לצורך תצוגה עתידית ב-GUI. */
    public List<Map<String, Object>> loadAll() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object o : loadRaw()) {
            Map<String, Object> m = Json.asMap(o);
            if (m != null) result.add(m);
        }
        return result;
    }

    private List<Object> loadRaw() {
        String content = JsonFileIO.readIfExists(filePath);
        if (content == null || content.isBlank()) return new ArrayList<>();
        try {
            List<Object> raw = Json.asList(Json.parse(content));
            return raw == null ? new ArrayList<>() : new ArrayList<>(raw);
        } catch (RuntimeException e) {
            System.err.println("קובץ poll-history.json פגום, מתעלמים מההיסטוריה הקיימת: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private Map<String, Object> toJson(Poll poll) {
        Map<String, Object> obj = Json.obj();
        obj.put("id", poll.getId());
        obj.put("createdAt", poll.getCreatedAt() == null ? null : poll.getCreatedAt().toString());
        obj.put("startedAt", poll.getStartedAt() == null ? null : poll.getStartedAt().toString());
        obj.put("closedAt", poll.getClosedAt() == null ? null : poll.getClosedAt().toString());
        obj.put("participantCount", poll.getParticipants().size());
        obj.put("completedCount", poll.completedCount());

        List<Object> questions = new ArrayList<>();
        for (Question q : poll.getQuestions()) {
            Map<String, Object> qObj = Json.obj();
            qObj.put("text", q.text());
            qObj.put("options", new ArrayList<Object>(q.options()));
            questions.add(qObj);
        }
        obj.put("questions", questions);

        List<Object> results = new ArrayList<>();
        for (Poll.QuestionResult qr : poll.computeResults()) {
            Map<String, Object> qrObj = Json.obj();
            qrObj.put("questionText", qr.questionText());
            List<Object> options = new ArrayList<>();
            for (Poll.OptionResult opt : qr.options()) {
                Map<String, Object> optObj = Json.obj();
                optObj.put("optionText", opt.optionText());
                optObj.put("votes", opt.votes());
                optObj.put("percentage", opt.percentage());
                options.add(optObj);
            }
            qrObj.put("options", options);
            results.add(qrObj);
        }
        obj.put("results", results);

        return obj;
    }
}
