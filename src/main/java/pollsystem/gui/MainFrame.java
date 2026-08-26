package pollsystem.gui;

import pollsystem.ai.ChatGptPollGenerator;
import pollsystem.core.AppEvent;
import pollsystem.core.AppState;
import pollsystem.core.PollManager;

import javax.swing.*;
import java.awt.*;

/**
 * החלון הראשי - שני אזורים נפרדים וברורים, כנדרש בסעיף 8: קהילה (קבוע, תמיד
 * גלוי) וסקר (Card שמתחלף לפי המצב: יצירה / Countdown+התקדמות חיה / תוצאות).
 * כל מעבר מצב מגיע אך ורק מ-AppEvent שמתקבל מה-EventBus - אין כאן קריאה
 * ישירה בין הפאנלים, כל התיאום עובר דרך ה-state המשותף.
 */
public final class MainFrame extends JFrame {
    private static final String CARD_CREATE = "create";
    private static final String CARD_STATUS = "status"; // מכיל בתוכו גם Countdown וגם תצוגה פעילה
    private static final String CARD_RESULTS = "results";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel pollArea = new JPanel(cardLayout);

    private final PollStatusPanel statusPanel;
    private final PollResultsPanel resultsPanel;

    public MainFrame(AppState state, PollManager pollManager, ChatGptPollGenerator aiGenerator) {
        super("מערכת ניהול סקרים - קהילת טלגרם");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        CommunityPanel communityPanel = new CommunityPanel(state);
        PollControlPanel controlPanel = new PollControlPanel(state, pollManager, aiGenerator);
        statusPanel = new PollStatusPanel(state);
        resultsPanel = new PollResultsPanel();
        resultsPanel.setOnStartNewPoll(() -> cardLayout.show(pollArea, CARD_CREATE));

        pollArea.add(wrapTitled(controlPanel, "יצירת סקר חדש"), CARD_CREATE);
        pollArea.add(wrapTitled(statusPanel, "מצב הסקר"), CARD_STATUS);
        pollArea.add(wrapTitled(resultsPanel, "תוצאות הסקר"), CARD_RESULTS);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrapTitled(communityPanel, "חברי הקהילה"), pollArea);
        splitPane.setResizeWeight(0.38);
        splitPane.setDividerSize(6);

        setContentPane(splitPane);
        setSize(1050, 640);
        setMinimumSize(new Dimension(820, 520));
        setLocationRelativeTo(null);

        state.getEventBus().subscribe(this::onEvent);
    }

    private JComponent wrapTitled(JComponent inner, String title) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createTitledBorder(title));
        wrapper.add(inner, BorderLayout.CENTER);
        return wrapper;
    }

    private void onEvent(AppEvent event) {
        if (event instanceof AppEvent.PollCreated created) {
            if (created.poll().getScheduledSendTime() != null) {
                statusPanel.showCountdown(created.poll());
                cardLayout.show(pollArea, CARD_STATUS);
            }
            // אם השליחה מיידית, PollStarted יגיע כמעט מיד ויחליף את התצוגה בעצמו
        } else if (event instanceof AppEvent.PollStarted started) {
            statusPanel.showActive(started.poll());
            cardLayout.show(pollArea, CARD_STATUS);
        } else if (event instanceof AppEvent.PollClosed closed) {
            resultsPanel.showResults(closed.poll());
            cardLayout.show(pollArea, CARD_RESULTS);
        } else if (event instanceof AppEvent.PollRejected rejected) {
            JOptionPane.showMessageDialog(this, rejected.reason(), "לא ניתן להתחיל סקר", JOptionPane.WARNING_MESSAGE);
        }
    }
}
