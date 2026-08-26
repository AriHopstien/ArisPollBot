package pollsystem.model;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * הקהילה הגלובלית (סעיף 1). אינה שייכת לאף סקר ספציפי - חבר קהילה נשאר
 * רשום בה גם אחרי שסקר מסתיים, ומשתתף אוטומטית (כמועמד) בסקרים הבאים.
 * <p>
 * ניגשים אליה משני threads שונים - thread ה-polling של הבוט ו-EDT של Swing -
 * ולכן מבני הנתונים כאן חייבים להיות בטוחים ל-concurrency.
 */
public final class Community {
    private final Map<Long, CommunityMember> membersById = new ConcurrentHashMap<>();
    private final List<CommunityMember> orderedMembers = new CopyOnWriteArrayList<>();
    private volatile java.util.function.Consumer<CommunityMember> onMemberAdded;

    public boolean isMember(long userId) {
        return membersById.containsKey(userId);
    }

    /**
     * מוסיף חבר חדש לקהילה. מחזיר את החבר שנוסף, או null אם המשתמש כבר היה
     * חבר בקהילה (סעיף 1: "משתמש שכבר חבר בקהילה לא יצורף אליה פעם נוספת").
     */
    public CommunityMember addIfAbsent(CommunityMember member) {
        CommunityMember existing = membersById.putIfAbsent(member.getUserId(), member);
        if (existing != null) {
            return null;
        }
        orderedMembers.add(member);
        if (onMemberAdded != null) onMemberAdded.accept(member);
        return member;
    }

    /**
     * מאכלס את הקהילה בבת אחת מרשימה שמורה (למשל מ-CommunityStore בעת עליית
     * התוכנה) - בלי להפעיל את onMemberAdded (אין טעם לשמור בחזרה נתונים
     * שהגיעו הרגע מהשמירה עצמה).
     */
    public void loadAll(List<CommunityMember> members) {
        for (CommunityMember m : members) {
            if (membersById.putIfAbsent(m.getUserId(), m) == null) {
                orderedMembers.add(m);
            }
        }
    }

    /** נקרא בכל פעם שחבר חדש מצטרף בפועל (לא בטעינה הראשונית) - משמש לחיבור שמירה לדיסק. */
    public void setOnMemberAdded(java.util.function.Consumer<CommunityMember> listener) {
        this.onMemberAdded = listener;
    }

    public int size() {
        return orderedMembers.size();
    }

    public List<CommunityMember> snapshot() {
        return List.copyOf(orderedMembers);
    }

    public CommunityMember get(long userId) {
        return membersById.get(userId);
    }
}
