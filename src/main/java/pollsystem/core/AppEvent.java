package pollsystem.core;

import pollsystem.model.CommunityMember;
import pollsystem.model.Poll;

/**
 * כל האירועים שמשודרים דרך ה-EventBus כדי לעדכן את ממשק ה-Swing בזמן אמת,
 * בלי שה-GUI יצטרך לבצע polling על ה-state (סעיפים 2 ו-8: "הנתונים יתעדכנו
 * בזמן אמת... ללא צורך בסגירת החלון, טעינה מחדש או לחיצה על כפתור Refresh").
 */
public sealed interface AppEvent {
    /** חבר קהילה חדש הצטרף (סעיף 1). */
    record MemberJoined(CommunityMember member, int communitySize) implements AppEvent {}

    /** סקר נוצר ותוזמן (מיידי או בעיכוב) - משמש להצגת ה-Countdown (סעיף 4). */
    record PollCreated(Poll poll) implements AppEvent {}

    /** הסקר בפועל נשלח למשתתפים והפך ל-ACTIVE. */
    record PollStarted(Poll poll) implements AppEvent {}

    /** משתתף ענה על שאלה נוספת בסקר הפעיל (סעיף 8). */
    record PollProgressUpdated(Poll poll, long userId) implements AppEvent {}

    /** הסקר נסגר - הזמן תם או כולם השלימו (סעיף 9). */
    record PollClosed(Poll poll) implements AppEvent {}

    /** ניסיון להתחיל סקר נדחה (פחות מ-3 חברים, או סקר אחר כבר פעיל - סעיפים 4, 12). */
    record PollRejected(String reason) implements AppEvent {}
}
