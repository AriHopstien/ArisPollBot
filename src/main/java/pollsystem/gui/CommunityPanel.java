package pollsystem.gui;

import pollsystem.core.AppEvent;
import pollsystem.core.AppState;
import pollsystem.model.CommunityMember;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * מציג את רשימת חברי הקהילה הגלובלית ומתעדכן בזמן אמת ללא Refresh (סעיף 2).
 * אינו מציג שום מידע ספציפי לסקר - המידע כאן גלובלי בלבד, כנדרש בסעיף 2:
 * "העובדה שמשתמש ענה או לא ענה לסקר אינה מאפיין של המשתמש בקהילה".
 */
public final class CommunityPanel extends JPanel {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final MemberTableModel tableModel = new MemberTableModel();
    private final JLabel countLabel = new JLabel();

    public CommunityPanel(AppState state) {
        super(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        for (CommunityMember m : state.getCommunity().snapshot()) {
            tableModel.addMember(m);
        }

        JTable table = new JTable(tableModel);
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);

        add(new JScrollPane(table), BorderLayout.CENTER);
        countLabel.setFont(countLabel.getFont().deriveFont(Font.BOLD));
        add(countLabel, BorderLayout.SOUTH);
        updateCountLabel();

        state.getEventBus().subscribe(event -> {
            if (event instanceof AppEvent.MemberJoined joined) {
                tableModel.addMember(joined.member());
                updateCountLabel();
            }
        });
    }

    private void updateCountLabel() {
        countLabel.setText("סה\"כ חברי קהילה: " + tableModel.getRowCount());
    }

    private static final class MemberTableModel extends AbstractTableModel {
        private final String[] columns = {"שם", "שם משתמש בטלגרם", "מועד הצטרפות"};
        private final List<CommunityMember> members = new ArrayList<>();

        void addMember(CommunityMember member) {
            members.add(member);
            fireTableRowsInserted(members.size() - 1, members.size() - 1);
        }

        @Override public int getRowCount() { return members.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            CommunityMember m = members.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> m.getDisplayName();
                case 1 -> m.getTelegramUsername() != null ? "@" + m.getTelegramUsername() : "\u2013";
                case 2 -> m.getJoinTime().format(TIME_FORMAT);
                default -> "";
            };
        }
    }
}
