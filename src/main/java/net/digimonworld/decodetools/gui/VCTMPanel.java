package net.digimonworld.decodetools.gui;

import javax.swing.*;
import javax.swing.GroupLayout.Alignment;
import javax.swing.event.ChangeEvent;
import javax.swing.table.DefaultTableModel;

import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Locale;

import net.digimonworld.decodetools.Main;
import net.digimonworld.decodetools.res.ResPayload;
import net.digimonworld.decodetools.res.kcap.AbstractKCAP;
import net.digimonworld.decodetools.res.kcap.HSMPKCAP;
import net.digimonworld.decodetools.res.kcap.NormalKCAP;
import net.digimonworld.decodetools.res.kcap.TDTMKCAP;
import net.digimonworld.decodetools.res.kcap.TDTMKCAP.TDTMEntry;
import net.digimonworld.decodetools.res.payload.QSTMPayload;
import net.digimonworld.decodetools.res.payload.TNOJPayload;
import net.digimonworld.decodetools.res.payload.VCTMPayload;
import net.digimonworld.decodetools.res.payload.qstm.QSTM02Entry;
import net.digimonworld.decodetools.res.payload.qstm.QSTMEntry;

public class VCTMPanel extends PayloadPanel {
    private static final long serialVersionUID = 1L;

    private VCTMPayload vctm;

    private final JLabel lblJointInfo = new JLabel(" ");
    private final DefaultTableModel tableModel;
    private final JTable entryTable;

    // Set true while updateTable() is running so editingStopped commits are ignored.
    private boolean suppressCommit = false;

    // Column indices
    private static final int COL_FRAME  = 0;
    private static final int COL_TIME   = 1;
    private static final int COL_TSCALE = 2;
    private static final int COL_COUNT  = 3;
    private static final int COL_CTYPE  = 4;
    private static final int COL_INTERP = 5;
    private static final int COL_X      = 6;
    private static final int COL_Y      = 7;
    private static final int COL_Z      = 8;
    private static final int COL_W      = 9;

    public VCTMPanel(Object selected) {

        String[] columnNames = {
            "Frame Index", "Time", "Time Scale", "Component Count",
            "Component Type", "Interpolation",
            "X", "Y", "Z", "W"
        };

        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == COL_TIME
                    || column == COL_X
                    || column == COL_Y
                    || column == COL_Z
                    || column == COL_W;
            }
        };

        // Override editingStopped instead of using a TableModelListener.
        //
        // Why: TableModelListener.tableChanged fires for a single column, so reading
        // other columns from the model at that moment is unreliable (timing, cast
        // failures, etc.). editingStopped fires after the editor has committed the
        // value into the model, and we can capture row/col BEFORE calling super —
        // giving us a consistent snapshot of the full row.
        entryTable = new JTable(tableModel) {
            @Override
            public void editingStopped(ChangeEvent e) {
                // Capture row/col NOW — super.editingStopped() clears them to -1.
                int row = getEditingRow();
                int col = getEditingColumn();
                super.editingStopped(e); // commits typed value into the model
                if (!suppressCommit && vctm != null && row >= 0 && isEditableColumn(col))
                    commitRow(row);
            }
        };

        entryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Commit (not cancel) the active edit when focus leaves the table.
        entryTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        JScrollPane tableScrollPane = new JScrollPane(entryTable);

        lblJointInfo.setFont(lblJointInfo.getFont().deriveFont(Font.BOLD));

        JButton btnAdd    = new JButton("Add Entry");
        JButton btnRemove = new JButton("Remove Selected");
        btnAdd.addActionListener(e -> onAddEntry());
        btnRemove.addActionListener(e -> onRemoveEntry());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttonPanel.add(lblJointInfo);
        buttonPanel.add(Box.createHorizontalStrut(12));
        buttonPanel.add(btnAdd);
        buttonPanel.add(btnRemove);

        GroupLayout groupLayout = new GroupLayout(this);
        this.setLayout(groupLayout);
        groupLayout.setHorizontalGroup(
            groupLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(groupLayout.createParallelGroup(Alignment.LEADING)
                    .addComponent(buttonPanel,
                        GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(tableScrollPane,
                        GroupLayout.PREFERRED_SIZE, 800, GroupLayout.PREFERRED_SIZE))
                .addContainerGap(20, Short.MAX_VALUE)
        );
        groupLayout.setVerticalGroup(
            groupLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(buttonPanel,
                    GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addGap(6)
                .addComponent(tableScrollPane, GroupLayout.PREFERRED_SIZE, 560, GroupLayout.PREFERRED_SIZE)
                .addContainerGap()
        );

        setSelectedFile(selected);
    }

    // -------------------------------------------------------------------------
    // Commit helper
    // -------------------------------------------------------------------------

    private static boolean isEditableColumn(int col) {
        return col == COL_TIME || col == COL_X || col == COL_Y || col == COL_Z || col == COL_W;
    }

    /**
     * Reads the full editable state of {@code row} from the table model and
     * writes it back to the VCTM. Called from {@code editingStopped}.
     */
    private void commitRow(int row) {
        if (vctm == null || row < 0 || row >= vctm.getNumEntries()) return;

        float time = parseFloat(tableModel.getValueAt(row, COL_TIME));

        int compCount = vctm.getComponentCount();
        float[] vals = new float[compCount];
        for (int i = 0; i < compCount; i++)
            vals[i] = parseFloat(tableModel.getValueAt(row, COL_X + i));

        try {
            vctm.updateEntry(row, time, vals);
        } catch (Exception ex) {
            Main.LOGGER.warning("VCTMPanel: updateEntry failed: " + ex.getMessage());
            JOptionPane.showMessageDialog(this,
                "Could not update entry: " + ex.getMessage(),
                "Update Error", JOptionPane.ERROR_MESSAGE);
            suppressCommit = true;
            updateTable();
            suppressCommit = false;
        }
    }

    // -------------------------------------------------------------------------
    // Button handlers
    // -------------------------------------------------------------------------

    private void onAddEntry() {
        if (vctm == null) return;

        int compCount = vctm.getComponentCount();

        float suggestedTime = 0f;
        float[] frameTimes = vctm.getFrameTimes();
        if (frameTimes.length > 0)
            suggestedTime = frameTimes[frameTimes.length - 1] + vctm.getTimeScale().getValue();

        JPanel panel = new JPanel(new GridLayout(0, 2, 4, 4));
        JTextField timeField = new JTextField(String.format(Locale.ROOT, "%.6f", suggestedTime), 10);
        panel.add(new JLabel("Time:"));
        panel.add(timeField);

        String[] compNames = { "X:", "Y:", "Z:", "W:" };
        JTextField[] compFields = new JTextField[compCount];
        for (int i = 0; i < compCount; i++) {
            compFields[i] = new JTextField("0.0", 10);
            panel.add(new JLabel(compNames[i]));
            panel.add(compFields[i]);
        }

        int result = JOptionPane.showConfirmDialog(this, panel,
            "Add New Entry", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        float time;
        float[] vals = new float[compCount];
        try {
            time = Float.parseFloat(timeField.getText().trim());
            for (int i = 0; i < compCount; i++)
                vals[i] = Float.parseFloat(compFields[i].getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid number: " + ex.getMessage(),
                "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            vctm.addEntry(time, vals);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not add entry: " + ex.getMessage(),
                "Add Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        suppressCommit = true;
        updateTable();
        suppressCommit = false;

        int newRow = tableModel.getRowCount() - 1;
        entryTable.scrollRectToVisible(entryTable.getCellRect(newRow, 0, true));
        entryTable.setRowSelectionInterval(newRow, newRow);
    }

    private void onRemoveEntry() {
        if (vctm == null) return;

        int selectedRow = entryTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a row to remove.",
                "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (JOptionPane.showConfirmDialog(this,
                "Remove the entry at frame index " + selectedRow + "?",
                "Confirm Removal", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
            return;

        try {
            vctm.removeEntry(selectedRow);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not remove entry: " + ex.getMessage(),
                "Remove Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        suppressCommit = true;
        updateTable();
        suppressCommit = false;
    }

    // -------------------------------------------------------------------------
    // PayloadPanel contract
    // -------------------------------------------------------------------------

    @Override
    public void setSelectedFile(Object file) {
        if (file == null) return;

        if (!(file instanceof VCTMPayload)) {
            Main.LOGGER.warning("Tried to select non-VCTM File in VCTMPanel.");
            return;
        }

        // terminateEditOnFocusLost + editingStopped override means any active edit
        // is committed to the current vctm before focus moves away, so nothing
        // special is needed here before swapping vctm.
        this.vctm = (VCTMPayload) file;
        lblJointInfo.setText(resolveJointInfo());

        suppressCommit = true;
        updateTable();
        suppressCommit = false;
    }

    // -------------------------------------------------------------------------
    // Joint resolution
    // -------------------------------------------------------------------------

    private String resolveJointInfo() {
        if (vctm == null) return " ";
        try {
            ResPayload vctmKcapRaw = vctm.getParent();
            if (!(vctmKcapRaw instanceof AbstractKCAP)) return "Joint: (unknown)";

            ResPayload tdtmRaw = ((AbstractKCAP) vctmKcapRaw).getParent();
            if (!(tdtmRaw instanceof TDTMKCAP)) return "Joint: (unknown)";

            TDTMKCAP tdtm = (TDTMKCAP) tdtmRaw;

            List<VCTMPayload> vctmList = tdtm.getVctmEntries();
            int vctmIndex = -1;
            for (int i = 0; i < vctmList.size(); i++) {
                if (vctmList.get(i) == vctm) { vctmIndex = i; break; }
            }
            if (vctmIndex == -1) return "Joint: (not referenced)";

            List<QSTMPayload> qstmList = tdtm.getQstmEntries();
            int matchedQstmId = -1;
            for (int qi = 0; qi < qstmList.size(); qi++) {
                for (QSTMEntry qe : qstmList.get(qi).getEntries()) {
                    if (qe instanceof QSTM02Entry
                            && ((QSTM02Entry) qe).getVctmId() == vctmIndex) {
                        matchedQstmId = qi;
                        break;
                    }
                }
                if (matchedQstmId != -1) break;
            }
            if (matchedQstmId == -1) return "Joint: (no QSTM reference found)";

            List<TDTMEntry> tdtmEntries = tdtm.getTdtmEntries();
            TDTMEntry matched = null;
            for (TDTMEntry te : tdtmEntries) {
                if (te.getqstmId() == matchedQstmId) { matched = te; break; }
            }
            if (matched == null) return "Joint: (no TDTM entry found)";

            int jointId   = matched.getjointId();
            String mode   = matched.getMode().name();
            String jname  = resolveJointName(tdtm, jointId);

            return jname != null
                ? String.format("Joint: %s (ID: %d)  |  Transform: %s", jname, jointId, mode)
                : String.format("Joint ID: %d  |  Transform: %s", jointId, mode);

        } catch (Exception ex) {
            Main.LOGGER.warning("VCTMPanel: could not resolve joint info: " + ex.getMessage());
            return "Joint: (error resolving)";
        }
    }

    private String resolveJointName(TDTMKCAP tdtm, int jointId) {
        try {
            ResPayload topRaw = tdtm.getParent();
            if (!(topRaw instanceof NormalKCAP)) return null;
            NormalKCAP topKcap = (NormalKCAP) topRaw;
            ResPayload first = topKcap.getEntries().stream().findFirst().orElse(null);
            if (!(first instanceof HSMPKCAP)) return null;
            HSMPKCAP hsmp = (HSMPKCAP) first;
            if (hsmp.getTNOJ() == null) return null;
            List<ResPayload> tnojEntries = hsmp.getTNOJ().getEntries();
            if (jointId < 0 || jointId >= tnojEntries.size()) return null;
            return ((TNOJPayload) tnojEntries.get(jointId)).getName();
        } catch (Exception ex) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Table rendering
    // -------------------------------------------------------------------------

    private void updateTable() {
        tableModel.setRowCount(0);
        if (vctm == null) return;

        float[] frameTimes    = vctm.getFrameTimes();
        int     compCount     = vctm.getComponentCount();
        String  compType      = vctm.getComponentType().toString();
        String  interpolation = vctm.getInterpolationMode().toString();

        for (int i = 0; i < frameTimes.length; i++) {
            Byte[][] raw = vctm.getRawFrameData(i);
            String[] comp = { "", "", "", "" };

            for (int j = 0; j < compCount; j++) {
                byte[] bytes = new byte[raw[j].length];
                for (int k = 0; k < bytes.length; k++) bytes[k] = raw[j][k];
                comp[j] = decodeComponent(bytes, compType);
            }

            tableModel.addRow(new Object[]{
                i,
                String.format(Locale.ROOT, "%.6f", frameTimes[i]),
                vctm.getTimeScale(),
                compCount,
                compType,
                interpolation,
                comp[0], comp[1], comp[2], comp[3]
            });
        }
    }

    private static String decodeComponent(byte[] bytes, String compType) {
        switch (compType) {
            case "FLOAT16": {
                short bits = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getShort();
                return String.format(Locale.ROOT, "%.6f", Float.float16ToFloat(bits));
            }
            case "FLOAT32": {
                float v = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                return String.format(Locale.ROOT, "%.6f", v);
            }
            case "INT8":
                return String.valueOf(bytes[0]);
            case "UINT8":
                return String.valueOf(Byte.toUnsignedInt(bytes[0]));
            case "INT16": {
                short v = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getShort();
                return String.valueOf(v);
            }
            case "UINT16": {
                short v = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getShort();
                return String.valueOf(Short.toUnsignedInt(v));
            }
            default:
                return "UNKNOWN(" + compType + ")";
        }
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    /** Safely reads a cell value as a float regardless of whether it is stored
     *  as a String, Number, or other Object. */
    private static float parseFloat(Object o) {
        if (o == null) return 0f;
        if (o instanceof Number) return ((Number) o).floatValue();
        String s = o.toString().trim();
        if (s.isEmpty()) return 0f;
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            Main.LOGGER.warning("VCTMPanel: could not parse float from '" + s + "'");
            return 0f;
        }
    }
}
