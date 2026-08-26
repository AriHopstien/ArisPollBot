package pollsystem.persistence;

import pollsystem.model.CommunityMember;
import pollsystem.util.Json;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * שומר וטוען את רשימת חברי הקהילה מקובץ JSON על הדיסק (data/community.json),
 * כך שחברים שנרשמו לא יצטרכו להירשם מחדש בכל פעם שהתוכנה נסגרת ונפתחת
 * מחדש. אין כאן שרת/בסיס נתונים אמיתי - זה עדיין תהליך Java יחיד שרץ
 * מקומית, בהתאם להחלטת הארכיטקטורה הקיימת של הפרויקט (ראו README) - רק
 * שהמצב עכשיו נשמר גם על הדיסק ולא רק בזיכרון.
 */
public final class CommunityStore {
    private final Path filePath;

    public CommunityStore() {
        this(Path.of("data", "community.json"));
    }

    public CommunityStore(Path filePath) {
        this.filePath = filePath;
    }

    /** טוען את כל חברי הקהילה השמורים מהדיסק. מחזיר רשימה ריקה אם הקובץ לא קיים/פגום. */
    public List<CommunityMember> loadAll() {
        String content = JsonFileIO.readIfExists(filePath);
        if (content == null || content.isBlank()) return List.of();

        List<CommunityMember> members = new ArrayList<>();
        try {
            List<Object> raw = Json.asList(Json.parse(content));
            if (raw == null) return List.of();
            for (Object o : raw) {
                Map<String, Object> m = Json.asMap(o);
                if (m == null) continue;
                long userId = Json.asLong(m.get("userId"));
                String username = Json.asString(m.get("telegramUsername"));
                String displayName = Json.asString(m.get("displayName"));
                String joinTimeStr = Json.asString(m.get("joinTime"));
                LocalDateTime joinTime = joinTimeStr == null ? LocalDateTime.now() : LocalDateTime.parse(joinTimeStr);
                members.add(new CommunityMember(userId, username, displayName, joinTime));
            }
        } catch (RuntimeException e) {
            System.err.println("קובץ community.json פגום, מתעלמים ומתחילים מקהילה ריקה: " + e.getMessage());
            return List.of();
        }
        return members;
    }

    /** שומר את כל רשימת חברי הקהילה הנוכחית לדיסק (מחליף את הקובץ כולו). */
    public void saveAll(List<CommunityMember> members) {
        List<Object> raw = new ArrayList<>();
        for (CommunityMember m : members) {
            Map<String, Object> obj = Json.obj();
            obj.put("userId", m.getUserId());
            obj.put("telegramUsername", m.getTelegramUsername());
            obj.put("displayName", m.getDisplayName());
            obj.put("joinTime", m.getJoinTime().toString());
            raw.add(obj);
        }
        JsonFileIO.writeAtomic(filePath, Json.stringify(raw));
    }
}
