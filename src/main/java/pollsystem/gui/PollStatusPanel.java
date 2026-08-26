package pollsystem.gui;

import pollsystem.core.AppEvent;
import pollsystem.core.AppState;
import pollsystem.model.Poll;
import pollsystem.model.PollParticipant;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * מציג את מצב הסקר: Countdown חי לפני שליחה (סעיף 4), ולאחר מכן התקדמות
 * משתתפים חיה כל עוד הסקר פעיל (סעיף 8). זהו אזור נפרד לגמרי ממידע הקהילה
 * הגלובלי (CommunityPanel) - אף פעם לא באותה תצוגה.
 * <p>
 * המעבר בין Countdown ל"נשלח" תמיד מפורש (סעיף 4: "אין להשאיר את הממשק
 * במצב עמום") - יש כרטיס נפרד לכל מצב: ריק / Countdown / פעיל.
 */
public final class PollStatusPanel extends JPanel {
    private final CardLayout cards = new CardLayout();
    private final JPanel cardsPanel = new JPanel(cards);

    private final JLabel countdownLabel = new JLabel("", SwingConstants.CENTER);
    private final Timer countdownTimer;
    private Poll pendingPoll;

    private final ParticipantTableModel tableModel = new ParticipantTableModel();
    private final JLabel statsLabel = new JLabel();
    private final JLabel timeRemainingLabel = new JLabel();
    private final Timer activeTimer;
    private Poll activePoll;

    public PollStatusPanel(AppState state) {
        super(new BorderLayout());

        cardsPanel.add(buildEmptyView(), "empty");
        cardsPanel.add(buildCountdownView(), "countdown");
        cardsPanel.add(buildActiveView(), "active");
        add(cardsPanel, BorderLayout.CENTER);
        cards.show(cardsPanel, "empty");

        countdownTimer = new Timer(1000, e -> tickCountdown());
        activeTimer = new Timer(1000, e -> tickActiveTimer());

        state.getEventBus().subscribe(event -> {
            if (event instanceof AppEvent.PollProgressUpdated updated && activePoll != null
                    && updated.poll().getId().equals(activePoll.getId())) {
                refreshActiveTable();
            }
        });
    }

    private JComponent buildEmptyView() {
        JLabel label = new JLabel("אין סקר פעיל כרגע", SwingConstants.CENTER);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 14f));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildCountdownView() {
        countdownLabel.setFont(countdownLabel.getFont().deriveFont(Font.BOLD, 36f));
        JLabel caption = new JLabel("הסקר יישלח בעוד:", SwingConstants.CENTER);

        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        caption.setAlignmentX(Component.CENTER_ALIGNMENT);
        countdownLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        inner.add(caption);
        inner.add(countdownLabel);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.add(inner);
        return panel;
    }

    private JComponent buildActiveView() {
        JTable table = new JTable(tableModel);
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);

        JPanel statsPanel = new JPanel(new GridLayout(1, 2, 12, 0));
        statsLabel.setFont(statsLabel.getFont().deriveFont(Font.BOLD));
        statsPanel.add(statsLabel);
        statsPanel.add(timeRemainingLabel);
        statsPanel.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(statsPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    public void showCountdown(Poll poll) {
        this.pendingPoll = poll;
        activeTimer.stop();
        tickCountdown();
        countdownTimer.start();
        cards.show(cardsPanel, "countdown");
    }

    private void tickCountdown() {
        if (pendingPoll == null || pendingPoll.getScheduledSendTime() == null) {
            countdownTimer.stop();
            return;
        }
        Duration remaining = Duration.between(LocalDateTime.now(), pendingPoll.getScheduledSendTime());
        if (remaining.isNegative()) remaining = Duration.ZERO;
        countdownLabel.setText(formatDuration(remaining));
        if (remaining.isZero()) {
            countdownTimer.stop();
        }
    }

    public void showActive(Poll poll) {
        this.activePoll = poll;
        this.pendingPoll = null;
        countdownTimer.stop();
        tableModel.setParticipants(poll.getParticipants(), poll.getQuestions().size());
        refreshStats();
        activeTimer.start();
        cards.show(cardsPanel, "active");
    }

    private void refreshActiveTable() {
        tableModel.fireUpdate();
        refreshStats();
    }

    private void refreshStats() {
        if (activePoll == null) return;
        int total = activePoll.getParticipants().size();
        int completed = activePoll.completedCount();
        statsLabel.setText("משתתפים: " + total + "   |   השלימו: " + completed + "   |   טרם השלימו: " + (total - completed));
        tickActiveTimer();
    }

    private void tickActiveTimer() {
        if (activePoll == null || activePoll.getClosesAt() == null) return;
        Duration remaining = Duration.between(LocalDateTime.now(), activePoll.getClosesAt());
        if (remaining.isNegative()) remaining = Duration.ZERO;
        timeRemainingLabel.setText("זמן שנותר: " + formatDuration(remaining));
        if (remaining.isZero()) {
            activeTimer.stop();
        }
    }

    private String formatDuration(Duration d) {
        long totalSeconds = d.getSeconds();
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private static final class ParticipantTableModel extends AbstractTableModel {
        private final String[] columns = {"שם", "התקדמות", "מצב"};
        private List<PollParticipant> participants = new ArrayList<>();
        private int totalQuestions;

        void setParticipants(List<PollParticipant> participants, int totalQuestions) {
            this.participants = new ArrayList<>(participants);
            this.totalQuestions = totalQuestions;
            fireTableDataChanged();
        }

        void fireUpdate() {
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return participants.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            PollParticipant p = participants.get(rowIndex);
            int answered = p.answeredCount();
            return switch (columnIndex) {
                case 0 -> p.getDisplayName();
                case 1 -> answered + "/" + totalQuestions;
                case 2 -> answered == 0 ? "טרם ענה" : (answered >= totalQuestions ? "השלים" : "בתהליך");
                default -> "";
            };
        }
    }
}
