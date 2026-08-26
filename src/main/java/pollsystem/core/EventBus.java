package pollsystem.core;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Observer pattern פשוט: מאזינים (בעיקר פאנלים ב-Swing) נרשמים דרך subscribe,
 * ומקבלים הודעה בכל פעם ש-publish נקרא (בדרך כלל מ-thread הבוט או מ-PollManager).
 * <p>
 * ההפצה תמיד מתבצעת על ה-EDT (Swing Event Dispatch Thread) דרך
 * SwingUtilities.invokeLater, כך שכל מאזין יכול לעדכן קומפוננטות Swing
 * ישירות בתוך ה-listener שלו בלי לדאוג ל-thread safety בעצמו.
 */
public final class EventBus {
    private final List<Consumer<AppEvent>> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<AppEvent> listener) {
        listeners.add(listener);
    }

    public void publish(AppEvent event) {
        SwingUtilities.invokeLater(() -> {
            for (Consumer<AppEvent> listener : listeners) {
                listener.accept(event);
            }
        });
    }
}
