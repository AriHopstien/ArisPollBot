package pollsystem.gui;

import pollsystem.model.Poll;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Locale;

/** תצוגת תוצאות סופיות של סקר שנסגר, ממוינות לפי שכיחות יורדת (סעיף 11). */
public final class PollResultsPanel extends JPanel {
    private final JPanel resultsContainer = new JPanel();
    private final JButton newPollBtn = new JButton("התחל סקר חדש");
    private Runnable onStartNewPoll;

    public PollResultsPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        resultsContainer.setLayout(new BoxLayout(resultsContainer, BoxLayout.Y_AXIS));
        add(new JScrollPane(resultsContainer), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEADING));
        newPollBtn.addActionListener(e -> { if (onStartNewPoll != null) onStartNewPoll.run(); });
        bottom.add(newPollBtn);
        add(bottom, BorderLayout.SOUTH);
    }

    public void setOnStartNewPoll(Runnable callback) {
        this.onStartNewPoll = callback;
    }

    public void showResults(Poll poll) {
        resultsContainer.removeAll();
        List<Poll.QuestionResult> results = poll.computeResults();
        for (int i = 0; i < results.size(); i++) {
            resultsContainer.add(buildQuestionResultCard(i + 1, results.get(i)));
            resultsContainer.add(Box.createVerticalStrut(10));
        }
        resultsContainer.revalidate();
        resultsContainer.repaint();
    }

    private JComponent buildQuestionResultCard(int number, Poll.QuestionResult result) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(4, 4, 4, 4),
                BorderFactory.createTitledBorder(number + ". " + result.questionText())));

        for (Poll.OptionResult opt : result.options()) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            JLabel label = new JLabel(opt.optionText());
            label.setPreferredSize(new Dimension(160, 22));

            JProgressBar bar = new JProgressBar(0, 100);
            bar.setValue((int) Math.round(opt.percentage()));
            bar.setStringPainted(true);
            bar.setString(String.format(Locale.forLanguageTag("he"), "%.0f%% (%d)", opt.percentage(), opt.votes()));

            row.add(label, BorderLayout.WEST);
            row.add(bar, BorderLayout.CENTER);
            row.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            card.add(row);
        }
        return card;
    }
}
