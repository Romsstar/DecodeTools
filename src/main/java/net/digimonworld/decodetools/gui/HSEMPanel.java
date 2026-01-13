package net.digimonworld.decodetools.gui;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;

import net.digimonworld.decodetools.Main;
import net.digimonworld.decodetools.res.ResPayload;
import net.digimonworld.decodetools.res.kcap.AbstractKCAP;
import net.digimonworld.decodetools.res.kcap.XTVPKCAP;
import net.digimonworld.decodetools.res.payload.HSEMPayload;
import net.digimonworld.decodetools.res.payload.XTVOPayload;
import net.digimonworld.decodetools.res.payload.hsem.HSEMDrawEntry;
import net.digimonworld.decodetools.res.payload.hsem.HSEMEntry;

import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JLabel;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.JTextPane;
import java.awt.Font;

// TODO quick 'n' dirty, make proper
public class HSEMPanel extends PayloadPanel {

    private static final long serialVersionUID = -4369075808768544826L;

    private transient HSEMPayload selected;
    private final JList<HSEMEntry> list = new JList<>();
    private final JScrollPane scrollPane = new JScrollPane();
    
    private final JLabel lblNewLabel = new JLabel("ID:");
    private final JLabel idLabel = new JLabel("<idLabel>");
   
    private final JLabel lblUnk1 = new JLabel("Unk1:");
    private final JLabel unk1label = new JLabel("<unk1Label>");
    
    private final JLabel lblUnk_2 = new JLabel("Unk2:");
    private final JLabel unk2label = new JLabel("<unk2Label>");
    
    private final JLabel lblUnk_3 = new JLabel("Unk3:");
    private final JLabel unk3label = new JLabel("<unk3Label>");
    
    private final JLabel lblUnk_4 = new JLabel("Unk4:");
    private final JLabel unk4label = new JLabel("<unk4Label>");
    
    private final JLabel lblUnk_5 = new JLabel("Unk5:");
    private final JLabel unk5label = new JLabel("<unk5Label>");
    
    private final JTextPane textPane = new JTextPane();


    public HSEMPanel(Object obj) {
        setSelectedFile(obj);

        GroupLayout layout = new GroupLayout(this);
        setLayout(layout);

        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        // ---------- Horizontal ----------
        layout.setHorizontalGroup(
            layout.createSequentialGroup()
            .addComponent(scrollPane, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                .addGroup(layout.createParallelGroup(Alignment.LEADING)

                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lblNewLabel)
                        .addComponent(idLabel))

                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lblUnk1)
                        .addComponent(unk1label))

                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lblUnk_2)
                        .addComponent(unk2label))

                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lblUnk_3)
                        .addComponent(unk3label))

                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lblUnk_4)
                        .addComponent(unk4label))

                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lblUnk_5)
                        .addComponent(unk5label))

                    .addComponent(textPane,
                    	    GroupLayout.PREFERRED_SIZE,
                    	    GroupLayout.PREFERRED_SIZE,
                    	    GroupLayout.PREFERRED_SIZE)
                )
        );

        // ---------- Vertical ----------
        layout.setVerticalGroup(
            layout.createParallelGroup(Alignment.LEADING)
            .addComponent(scrollPane,
                    GroupLayout.DEFAULT_SIZE,
                    GroupLayout.DEFAULT_SIZE,
                    Short.MAX_VALUE)

                .addGroup(layout.createSequentialGroup()

                    .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                        .addComponent(lblNewLabel)
                        .addComponent(idLabel))

                    .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                        .addComponent(lblUnk1)
                        .addComponent(unk1label))

                    .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                        .addComponent(lblUnk_2)
                        .addComponent(unk2label))

                    .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                        .addComponent(lblUnk_3)
                        .addComponent(unk3label))

                    .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                        .addComponent(lblUnk_4)
                        .addComponent(unk4label))

                    .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                        .addComponent(lblUnk_5)
                        .addComponent(unk5label))

                    .addPreferredGap(ComponentPlacement.UNRELATED)
                    .addComponent(textPane,
                    	    GroupLayout.PREFERRED_SIZE,
                    	    GroupLayout.PREFERRED_SIZE,
                    	    GroupLayout.PREFERRED_SIZE)
                )
        );

        textPane.setFont(new Font("Inconsolata", Font.PLAIN, 12));
    

        setLayout(layout);
        //@formatter:on
    }

    @Override
    public void setSelectedFile(Object file) {
        if (file == null)
            return;

        if (!(file instanceof HSEMPayload)) {
            Main.LOGGER.warning("Tried to select non-HSEM File in HSEMPanel.");
            return;
        }

        selected = (HSEMPayload) file;
        linkXTVOOffsets(selected);
        scrollPane.setViewportView(list);
        list.setListData(selected.getEntries().toArray(new HSEMEntry[0]));

        idLabel.setText(Integer.toString(selected.getId()));
        unk1label.setText(Integer.toString(selected.getUnknown1()));
        unk2label.setText(Integer.toString(selected.getUnknown2()));
        unk3label.setText(Integer.toString(selected.getUnknown3()));
        unk4label.setText(Integer.toString(selected.getUnknown4()));
        unk5label.setText(Integer.toString(selected.getUnknown5()));

        float[] arr = selected.getHeaderData();
        String s = String.format("%10.4f %10.4f\n%10.4f %10.4f\n%10.4f %10.4f\n%10.4f %10.4f\n%10.4f %10.4f\n", arr[0],
                                 arr[1], arr[2], arr[3], arr[4], arr[5], arr[6], arr[7], arr[8], arr[9]);
        textPane.setText(s);
    }

    public HSEMPayload getSelectedFile() {
        return selected;
    }
    private void linkXTVOOffsets(HSEMPayload hsem) {

        // 1) Go to the owning KCAP (HSEMKCAP)
        AbstractKCAP hsemKcap = hsem.getParent();
        if (hsemKcap == null)
            return;

        // 2) Go to the container KCAP (HSMP / NormalKCAP)
        AbstractKCAP container = hsemKcap.getParent();
        if (container == null)
            return;

        // 3) Find XTVPKCAP among sibling entries
        XTVPKCAP xtvp = null;
        for (ResPayload entry : container.getEntries()) {
            if (entry instanceof XTVPKCAP) {
                xtvp = (XTVPKCAP) entry;
                break;
            }
        }

        if (xtvp == null) {
            Main.LOGGER.warning("XTVPKCAP not found for HSEM");
            return;
        }

        // 4) Link XTVO offsets using drawIndex
        for (HSEMEntry e : hsem.getEntries()) {
            if (e instanceof HSEMDrawEntry draw) {
                int idx = draw.getDrawIndex();
                if (idx >= 0 && idx < xtvp.getEntryCount()) {
                    XTVOPayload xtvo = xtvp.get(idx);
                    draw.setXtvoOffset(xtvo.getXtvoDataOffset());
                }
            }
        }
    }



}
