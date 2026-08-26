package pollsystem.gui;
import pollsystem.ai.ChatGptPollGenerator;
import pollsystem.core.AppEvent;
import pollsystem.core.AppState;
import pollsystem.core.PollManager;
import pollsystem.model.Question;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static pollsystem.core.PollManager.MIN_COMMUNITY_SIZE;

/**
 * יצירת סקר חדש - ידנית או באמצעות ChatGPT API (סעיף 3), ובחירת מועד שליחה
 * מיידי או מעוכב (סעיף 4). מונע מראש התחלת סקר עם פחות מ-3 חברי קהילה על ידי
 * נטרול הכפתור מראש, ולא רק דרך הודעת שגיאה אחרי הלחיצה (תוספת UX).
 */
public final class PollControlPanel extends JPanel {

    private final AppState state;
    private final PollManager pollManager;
    private final ChatGptPollGenerator aiGenerator;

    private final JRadioButton manualModeBtn = new JRadioButton("יצירה ידנית", true);
    private final JRadioButton aiModeBtn = new JRadioButton("יצירה באמצעות ChatGPT");
    private final CardLayout modeCards = new CardLayout();
    private final JPanel modePanel = new JPanel(modeCards);

    // מצב ידני
    private final JPanel questionsContainer = new JPanel();
    private final List<QuestionEditorRow> questionRows = new ArrayList<>();
    private final JButton addQuestionBtn = new JButton("+ הוספת שאלה");

    // מצב AI
    private final JTextField topicField = new JTextField();
    private final JSpinner aiQuestionCountSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 3, 1));
    private final JButton generateBtn = new JButton("צור שאלות");
    private final JTextArea aiPreviewArea = new JTextArea(10, 30);
    private final JLabel aiStatusLabel = new JLabel(" ");
    private List<Question> generatedQuestions = null;

    // תזמון שליחה
    private final JRadioButton sendNowBtn = new JRadioButton("שליחה מיידית", true);
    private final JRadioButton sendDelayedBtn = new JRadioButton("שליחה מאוחרת בעוד (דקות):");
    private final JSpinner delayMinutesSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 60, 1));

    private final JButton startPollBtn = new JButton("התחל סקר");
    private final JLabel communityHintLabel = new JLabel();
    private final JLabel statusLabel = new JLabel(" ");

    public PollControlPanel(AppState state, PollManager pollManager, ChatGptPollGenerator aiGenerator) {
        super(new BorderLayout(10, 10));
        this.state = state;
        this.pollManager = pollManager;
        this.aiGenerator = aiGenerator;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(buildModeSelector(), BorderLayout.NORTH);
        add(buildModePanel(), BorderLayout.CENTER);
        add(buildBottomPanel(), BorderLayout.SOUTH);

        addQuestionRow(); // מתחילים עם שאלה אחת כברירת מחדל
        updateCommunityHint();

        state.getEventBus().subscribe(event -> {
            if (event instanceof AppEvent.MemberJoined) {
                updateCommunityHint();
            }
        });
    }

    private JComponent buildModeSelector() {
        ButtonGroup group = new ButtonGroup();
        group.add(manualModeBtn);
        group.add(aiModeBtn);
        manualModeBtn.addActionListener(e -> modeCards.show(modePanel, "manual"));
        aiModeBtn.addActionListener(e -> modeCards.show(modePanel, "ai"));

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING, 12, 0));
        panel.add(manualModeBtn);
        panel.add(aiModeBtn);
        return panel;
    }

    private JComponent buildModePanel() {
        questionsContainer.setLayout(new BoxLayout(questionsContainer, BoxLayout.Y_AXIS));
        addQuestionBtn.addActionListener(e -> addQuestionRow());

        JPanel manualPanel = new JPanel(new BorderLayout(6, 6));
        manualPanel.add(new JScrollPane(questionsContainer), BorderLayout.CENTER);
        JPanel addBtnRow = new JPanel(new FlowLayout(FlowLayout.LEADING));
        addBtnRow.add(addQuestionBtn);
        manualPanel.add(addBtnRow, BorderLayout.SOUTH);

        modePanel.add(manualPanel, "manual");
        modePanel.add(buildAiPanel(), "ai");
        return modePanel;
    }

    private JPanel buildAiPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JPanel topRow = new JPanel(new BorderLayout(6, 6));
        topRow.add(new JLabel("נושא הסקר:"), BorderLayout.WEST);
        topRow.add(topicField, BorderLayout.CENTER);

        JPanel countRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
        countRow.add(new JLabel("מספר שאלות:"));
        countRow.add(aiQuestionCountSpinner);
        countRow.add(generateBtn);

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.add(topRow);
        north.add(countRow);

        aiPreviewArea.setEditable(false);
        aiPreviewArea.setLineWrap(true);
        aiPreviewArea.setWrapStyleWord(true);
        aiPreviewArea.setText("השאלות שייווצרו יוצגו כאן לפני שליחת הסקר.");

        panel.add(north, BorderLayout.NORTH);
        panel.add(new JScrollPane(aiPreviewArea), BorderLayout.CENTER);
        panel.add(aiStatusLabel, BorderLayout.SOUTH);

        generateBtn.addActionListener(e -> onGenerateClicked());
        return panel;
    }

    private JComponent buildBottomPanel() {
        ButtonGroup group = new ButtonGroup();
        group.add(sendNowBtn);
        group.add(sendDelayedBtn);

        JPanel schedulingPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 4));
        schedulingPanel.add(sendNowBtn);
        schedulingPanel.add(sendDelayedBtn);
        schedulingPanel.add(delayMinutesSpinner);
        delayMinutesSpinner.setEnabled(false);
        sendDelayedBtn.addActionListener(e -> delayMinutesSpinner.setEnabled(true));
        sendNowBtn.addActionListener(e -> delayMinutesSpinner.setEnabled(false));

        startPollBtn.addActionListener(e -> onStartPollClicked());
        JPanel startRow = new JPanel(new FlowLayout(FlowLayout.LEADING));
        startRow.add(startPollBtn);
        startRow.add(communityHintLabel);
        startRow.add(statusLabel);

        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBorder(BorderFactory.createTitledBorder("מועד שליחה"));
        bottom.add(schedulingPanel);
        bottom.add(startRow);
        return bottom;
    }

    private void updateCommunityHint() {
        int size = state.getCommunity().size();
        boolean enough = size >= MIN_COMMUNITY_SIZE;
        startPollBtn.setEnabled(enough);
        communityHintLabel.setText(enough
                ? "חברי קהילה: " + size
                : "נדרשים לפחות " + MIN_COMMUNITY_SIZE + " חברי קהילה כדי להתחיל סקר (קיימים " + size + ")");
    }

    private void addQuestionRow() {
        if (questionRows.size() >= 3) {
            statusLabel.setText("ניתן להוסיף עד 3 שאלות בלבד");
            return;
        }
        QuestionEditorRow row = new QuestionEditorRow(this::removeQuestionRow);
        questionRows.add(row);
        questionsContainer.add(row);
        questionsContainer.revalidate();
        questionsContainer.repaint();
    }

    private void removeQuestionRow(QuestionEditorRow row) {
        if (questionRows.size() <= 1) {
            statusLabel.setText("חייבת להישאר לפחות שאלה אחת");
            return;
        }
        questionRows.remove(row);
        questionsContainer.remove(row);
        questionsContainer.revalidate();
        questionsContainer.repaint();
    }

    private void onGenerateClicked() {
        String topic = topicField.getText().trim();
        if (topic.isEmpty()) {
            aiStatusLabel.setText("נא להזין נושא לפני היצירה");
            return;
        }
        int count = (Integer) aiQuestionCountSpinner.getValue();
        generateBtn.setEnabled(false);
        aiStatusLabel.setText("יוצר שאלות...");
        aiPreviewArea.setText("");

        // קריאת רשת ל-ChatGPT API - חייבת לרוץ מחוץ ל-EDT כדי לא להקפיא את הממשק
        SwingWorker<List<Question>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Question> doInBackground() {
                return aiGenerator.generateQuestions(topic, count);
            }

            @Override
            protected void done() {
                generateBtn.setEnabled(true);
                try {
                    generatedQuestions = get();
                    aiPreviewArea.setText(renderPreview(generatedQuestions));
                    aiStatusLabel.setText("נוצרו " + generatedQuestions.size() + " שאלות. ניתן להתחיל את הסקר או ליצור מחדש.");
                } catch (Exception ex) {
                    generatedQuestions = null;
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    aiStatusLabel.setText("שגיאה: " + cause.getMessage());
                    aiPreviewArea.setText("");
                }
            }
        };
        worker.execute();
    }

    private String renderPreview(List<Question> questions) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            sb.append(i + 1).append(". ").append(q.text()).append('\n');
            for (String opt : q.options()) {
                sb.append("     - ").append(opt).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private void onStartPollClicked() {
        List<Question> questions;
        if (aiModeBtn.isSelected()) {
            if (generatedQuestions == null || generatedQuestions.isEmpty()) {
                statusLabel.setText("יש ליצור שאלות באמצעות ChatGPT לפני התחלת הסקר");
                return;
            }
            questions = generatedQuestions;
        } else {
            questions = collectManualQuestions();
            if (questions == null) return; // הודעת השגיאה כבר הוצגה
        }

        int delayMinutes = sendDelayedBtn.isSelected() ? (Integer) delayMinutesSpinner.getValue() : 0;

        if (!pollManager.canStartNewPoll()) {
            statusLabel.setText("לא ניתן להתחיל סקר כרגע (סקר אחר פעיל, או פחות מ-3 חברי קהילה)");
            return;
        }

        pollManager.createAndSchedule(questions, delayMinutes);
        statusLabel.setText(" ");
        resetForm();
    }

    private List<Question> collectManualQuestions() {
        List<Question> result = new ArrayList<>();
        for (QuestionEditorRow row : questionRows) {
            String text = row.getQuestionText();
            if (text.isEmpty()) {
                statusLabel.setText("כל השאלות חייבות להכיל טקסט");
                return null;
            }
            List<String> options = row.getOptions();
            if (options.size() < 2) {
                statusLabel.setText("כל שאלה חייבת לפחות 2 אפשרויות תשובה");
                return null;
            }
            try {
                result.add(new Question(text, options));
            } catch (IllegalArgumentException ex) {
                statusLabel.setText(ex.getMessage());
                return null;
            }
        }
        return result;
    }

    private void resetForm() {
        questionsContainer.removeAll();
        questionRows.clear();
        addQuestionRow();
        topicField.setText("");
        aiPreviewArea.setText("השאלות שייווצרו יוצגו כאן לפני שליחת הסקר.");
        generatedQuestions = null;
        sendNowBtn.setSelected(true);
        delayMinutesSpinner.setEnabled(false);
        questionsContainer.revalidate();
        questionsContainer.repaint();
    }

    /** שורת עריכה לשאלה בודדת: נוסח + 2-4 שדות אפשרות דינמיים, עם הוספה/הסרה (סעיף 3). */
    private static final class QuestionEditorRow extends JPanel {
        private final JTextField questionField = new JTextField();
        private final JPanel optionsPanel = new JPanel();
        private final List<JTextField> optionFields = new ArrayList<>();
        private final List<JLabel> optionLabels = new ArrayList<>();

        QuestionEditorRow(Consumer<QuestionEditorRow> onRemove) {
            super(new BorderLayout(4, 4));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(6, 0, 6, 0),
                    BorderFactory.createTitledBorder("שאלה")));

            JPanel top = new JPanel(new BorderLayout(4, 4));
            top.add(new JLabel("נוסח השאלה:"), BorderLayout.WEST);
            top.add(questionField, BorderLayout.CENTER);
            JButton removeBtn = new JButton("הסר שאלה");
            removeBtn.addActionListener(e -> onRemove.accept(this));
            top.add(removeBtn, BorderLayout.EAST);

            optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
            addOptionField();
            addOptionField();

            JButton addOptionBtn = new JButton("+ הוספת אפשרות");
            addOptionBtn.addActionListener(e -> addOptionField());
            JPanel addOptionRow = new JPanel(new FlowLayout(FlowLayout.LEADING));
            addOptionRow.add(addOptionBtn);

            JPanel center = new JPanel(new BorderLayout());
            center.add(optionsPanel, BorderLayout.CENTER);
            center.add(addOptionRow, BorderLayout.SOUTH);

            add(top, BorderLayout.NORTH);
            add(center, BorderLayout.CENTER);
        }

        private void addOptionField() {
            if (optionFields.size() >= 4) return; // סעיף 3: מקסימום 4 אפשרויות
            JPanel row = new JPanel(new BorderLayout(4, 2));
            JTextField field = new JTextField();
            JLabel label = new JLabel();
            optionFields.add(field);
            optionLabels.add(label);
            row.add(label, BorderLayout.WEST);
            row.add(field, BorderLayout.CENTER);
            JButton removeOptBtn = new JButton("\u2013");
            removeOptBtn.setToolTipText("הסר אפשרות זו");
            removeOptBtn.addActionListener(e -> removeOptionRow(row, field, label));
            row.add(removeOptBtn, BorderLayout.EAST);
            optionsPanel.add(row);
            relabelOptions();
            optionsPanel.revalidate();
            optionsPanel.repaint();
        }

        private void removeOptionRow(JPanel row, JTextField field, JLabel label) {
            if (optionFields.size() <= 2) return; // סעיף 3: מינימום 2 אפשרויות
            optionFields.remove(field);
            optionLabels.remove(label);
            optionsPanel.remove(row);
            relabelOptions();
            optionsPanel.revalidate();
            optionsPanel.repaint();
        }

        private void relabelOptions() {
            for (int i = 0; i < optionLabels.size(); i++) {
                optionLabels.get(i).setText("אפשרות " + (i + 1) + ":");
            }
        }

        String getQuestionText() {
            return questionField.getText().trim();
        }

        List<String> getOptions() {
            List<String> result = new ArrayList<>();
            for (JTextField f : optionFields) {
                String text = f.getText().trim();
                if (!text.isEmpty()) result.add(text);
            }
            return result;
        }
    }
}
