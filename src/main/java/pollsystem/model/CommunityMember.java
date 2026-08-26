package pollsystem.model;

import java.time.LocalDateTime;

/**
 * חבר בקהילה הגלובלית (מסמך המפרט, סעיף 1).
 * נשאר רשום גם אחרי שסקר מסתיים - אינו שייך לסקר מסוים.
 */
public final class CommunityMember {
    private final long userId;
    private final String telegramUsername; // עשוי להיות null אם למשתמש אין @username בטלגרם
    private final String displayName;      // first_name (+ last_name) כפי שמופיע בטלגרם
    private final LocalDateTime joinTime;

    public CommunityMember(long userId, String telegramUsername, String displayName, LocalDateTime joinTime) {
        this.userId = userId;
        this.telegramUsername = telegramUsername;
        this.displayName = displayName;
        this.joinTime = joinTime;
    }

    public long getUserId() { return userId; }
    public String getTelegramUsername() { return telegramUsername; }
    public String getDisplayName() { return displayName; }
    public LocalDateTime getJoinTime() { return joinTime; }
}
