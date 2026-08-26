package pollsystem.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * עזר משותף לקריאה/כתיבה בטוחה של קובצי JSON על הדיסק (תיקיית data/), עבור
 * CommunityStore ו-PollHistoryStore. הכתיבה היא אטומית (כתיבה לקובץ זמני
 * ואז החלפה) כדי שקריסה/סגירה של התוכנה באמצע כתיבה לא תשאיר קובץ חצי-כתוב
 * ופגום.
 */
final class JsonFileIO {
    private JsonFileIO() {}

    /** קורא את תוכן הקובץ כמחרוזת, או null אם הקובץ לא קיים. */
    static String readIfExists(Path path) {
        try {
            if (!Files.exists(path)) return null;
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("שגיאה בקריאת קובץ נתונים " + path + ": " + e.getMessage());
            return null;
        }
    }

    /** כותב מחרוזת לקובץ באופן אטומי (יוצר גם את תיקיית האב אם חסרה). */
    static synchronized void writeAtomic(Path path, String content) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Path tmp = Path.of(path.toString() + ".tmp");
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // כשל בשמירה לא אמור להפיל את האפליקציה - רק מתועד. הנתונים
            // עדיין תקינים בזיכרון, רק לא יישרדו סגירה של התוכנה הפעם.
            System.err.println("שגיאה בשמירת קובץ נתונים " + path + ": " + e.getMessage());
        }
    }
}
