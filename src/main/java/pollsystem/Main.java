package pollsystem;

import pollsystem.ai.ChatGptPollGenerator;
import pollsystem.bot.PollDispatcher;
import pollsystem.bot.PollTelegramBot;
import pollsystem.bot.TelegramApiClient;
import pollsystem.config.AppConfig;
import pollsystem.core.AppState;
import pollsystem.core.PollManager;
import pollsystem.gui.MainFrame;
import pollsystem.persistence.CommunityStore;
import pollsystem.persistence.PollHistoryStore;

import javax.swing.*;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * נקודת הכניסה: טוען הגדרות, מקים את כל השכבות (state, בוט, מפיץ, מנהל
 * סקרים, מחולל AI), מפעיל את thread ה-polling של הבוט, ופותח את חלון ה-Swing
 * על ה-EDT. הכל בתהליך Java אחד - אין שרת חיצוני ואין בסיס נתונים; קהילה
 * והיסטוריית סקרים נשמרות בקובצי JSON תחת data/ כדי לשרוד סגירה/פתיחה
 * מחדש של התוכנה (ראו pollsystem.persistence).
 */
public final class Main {
    public static void main(String[] args) {
        // הגנה מפני מכונות שבהן ה-locale ברירת המחדל אינו UTF-8 (למשל מכונות
        // Linux עם locale מסוג POSIX/C) - אחרת שורות לוג בעברית (System.err
        // וכו') היו עלולות להידפס כ-"?????" בקונסולה. אין לזה שום השפעה על
        // תצוגת ה-Swing עצמה (שלעולם לא עוברת דרך System.out), אבל זה משפר
        // את קריאות הלוגים בכל סביבה.
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        AppConfig config;
        try {
            config = AppConfig.load();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "שגיאת הגדרות", JOptionPane.ERROR_MESSAGE);
            System.err.println(e.getMessage());
            return;
        }

        AppState state = AppState.getInstance();

        // טעינת קהילה שמורה מהדיסק (אם קיימת) + חיבור שמירה אוטומטית בכל הצטרפות חדשה
        CommunityStore communityStore = new CommunityStore();
        state.getCommunity().loadAll(communityStore.loadAll());
        state.getCommunity().setOnMemberAdded(member -> communityStore.saveAll(state.getCommunity().snapshot()));

        PollHistoryStore pollHistoryStore = new PollHistoryStore();

        TelegramApiClient api = new TelegramApiClient(config.getTelegramBotToken());
        PollDispatcher dispatcher = new PollDispatcher(api, state);
        PollManager pollManager = new PollManager(state);
        ChatGptPollGenerator aiGenerator = new ChatGptPollGenerator(config.getOpenAiApiKey(), config.getOpenAiModel());

        pollManager.setOnPollActivated(dispatcher::sendPollToParticipants);
        pollManager.setOnReminderDue(dispatcher::sendReminder);
        pollManager.setOnPollClosed(poll -> {
            dispatcher.notifyPollClosed(poll);
            pollHistoryStore.append(poll); // שומר סיכום + תוצאות הסקר לקובץ ההיסטוריה
        });

        PollTelegramBot bot = new PollTelegramBot(api, state, pollManager, dispatcher);
        bot.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            bot.stop();
            pollManager.shutdown();
        }));

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame(state, pollManager, aiGenerator);
            frame.setVisible(true);
        });
    }
}
