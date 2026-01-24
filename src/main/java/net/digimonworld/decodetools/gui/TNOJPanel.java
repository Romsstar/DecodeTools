package net.digimonworld.decodetools.gui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.util.List;

import net.digimonworld.decodetools.Main;
import net.digimonworld.decodetools.res.kcap.AbstractKCAP;
import net.digimonworld.decodetools.res.kcap.TNOJKCAP;
import net.digimonworld.decodetools.res.payload.TNOJPayload;

public class TNOJPanel extends PayloadPanel {

    private static final long serialVersionUID = 1L;

    private TNOJKCAP tnoj;

    private final JLabel jointCountLabel = new JLabel("Joint Count: 0");

    private final JTable jointTable;
    private final DefaultTableModel tableModel;

    public TNOJPanel(Object selected) {
        setSelectedFile(selected);

        String[] columns = {
            "Index",
            "Name",
            "Parent ID",
            "Offset X", "Offset Y", "Offset Z",
            "Rot X", "Rot Y", "Rot Z", "Rot W",
            "Scale X", "Scale Y", "Scale Z",
            "Local Scale X", "Local Scale Y", "Local Scale Z"
        };

        tableModel = new DefaultTableModel(columns, 0);
        jointTable = new JTable(tableModel);
        jointTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        JScrollPane tableScrollPane = new JScrollPane(jointTable);

        GroupLayout layout = new GroupLayout(this);
        setLayout(layout);

        layout.setHorizontalGroup(
            layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addComponent(jointCountLabel)
                    .addComponent(tableScrollPane, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap()
        );

        layout.setVerticalGroup(
            layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jointCountLabel)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tableScrollPane, GroupLayout.PREFERRED_SIZE, 300, GroupLayout.PREFERRED_SIZE)
                .addContainerGap()
        );
    }

    @Override
    public void setSelectedFile(Object file) {
        if (file == null)
            return;

        if (!(file instanceof AbstractKCAP)) {
            Main.LOGGER.warning("Tried to select non-KCAP file in TNOJPanel.");
            return;
        }

        if (((AbstractKCAP) file).getKCAPType() != AbstractKCAP.KCAPType.TNOJ) {
            Main.LOGGER.warning("Tried to select non-TNOJ KCAP in TNOJPanel.");
            return;
        }

        this.tnoj = (TNOJKCAP) file;
        jointCountLabel.setText("Joint Count: " + tnoj.getEntryCount());

        updateTable();
    }

    private void updateTable() {
        tableModel.setRowCount(0);

        List<TNOJPayload> joints = tnoj.getTNOJEntries();

        for (int i = 0; i < joints.size(); i++) {
            TNOJPayload j = joints.get(i);

            tableModel.addRow(new Object[] {
                i,
                j.hasName() ? j.getName() : "-",
                j.getParentId(),

                j.getXOffset(),
                j.getYOffset(),
                j.getZOffset(),

                j.getRotationX(),
                j.getRotationY(),
                j.getRotationZ(),
                j.getRotationW(),

                j.getScaleX(),
                j.getScaleY(),
                j.getScaleZ(),

                j.getLocalScaleX(),
                j.getLocalScaleY(),
                j.getLocalScaleZ()
            });
        }
    }
}
