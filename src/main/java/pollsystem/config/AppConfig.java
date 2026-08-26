package pollsystem.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * טעינת הגדרות (טוקן הבוט, מפתח OpenAI) מקובץ config.properties בתיקיית
 * העבודה, עם אפשרות override ממשתני סביבה - נוח למי שלא רוצה להשאיר מפתחות
 * בקובץ על הדיסק (למשל בזמן בדיקות ב-CI).
 */
public final class AppConfig {
    private final String telegramBotToken;
    private final String openAiApiKey;
    private final String openAiModel;

    private AppConfig(String telegramBotToken, String openAiApiKey, String openAiModel) {
        this.telegramBotToken = telegramBotToken;
        this.openAiApiKey = openAiApiKey;
        this.openAiModel = openAiModel;
    }

    public String getTelegramBotToken() { return telegramBotToken; }
    public String getOpenAiApiKey() { return openAiApiKey; }
    public String getOpenAiModel() { return openAiModel; }

    public static AppConfig load() throws IOException {
        Properties props = new Properties();
        Path configPath = Path.of("config.properties");
             if (Files.exists(configPath)) {
            // Properties.load(InputStream) מניח ISO-8859-1 לפי המפרט; קוראים
            // דרך Reader עם UTF-8 מפורש כדי להיות בטוחים גם אם בעתיד יתווספו
            // לקובץ ערכים לא-ASCII.
            try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
        }

        String token = firstNonBlank(
                System.getenv("TELEGRAM_BOT_TOKEN"),
                props.getProperty("telegram.bot.token"));
        String openAiKey = firstNonBlank(
                System.getenv("OPENAI_API_KEY"),
                props.getProperty("openai.api.key"));
        String openAiModel = firstNonBlank(
                System.getenv("OPENAI_MODEL"),
                props.getProperty("openai.model"),
                "");

        if (token == null || token.isBlank()) {
            throw new IOException(
                    "לא נמצא טוקן לבוט הטלגרם. יש להגדיר אותו בקובץ config.properties " +
                            "(מפתח telegram.bot.token) או במשתנה הסביבה TELEGRAM_BOT_TOKEN.");
        }
        // openai.api.key כאן מכיל בפועל את טוקן הפרוקסי של המרצה (לא מפתח OpenAI אמיתי) -
        // עדיין נדרש כי הפרוקסי דורש אותו בכל בקשה.
        if (openAiKey == null || openAiKey.isBlank()) {
            throw new IOException(
                    "לא נמצא טוקן ה-API (של הפרוקסי של המרצה). יש להגדיר אותו בקובץ config.properties " +
                            "(מפתח openai.api.key) או במשתנה הסביבה OPENAI_API_KEY.");
        }

        return new AppConfig(token, openAiKey, openAiModel);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
