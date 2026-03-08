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
import net.digimonworld.decodetools.res.payload.hsem.HSEMMaterialEntry;
import net.digimonworld.decodetools.res.payload.hsem.HSEMTextureEntry;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.JTextPane;
import java.awt.CardLayout;
import java.awt.Font;
import java.util.Map;
import java.util.TreeMap;

// TODO quick 'n' dirty, make proper
public class HSEMPanel extends PayloadPanel {

    private static final long serialVersionUID = -4369075808768544826L;

    private static final String CARD_EMPTY = "empty";
    private static final String CARD_TEXTURE = "texture";
    private static final String CARD_MATERIAL = "material";

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

    // --- Always-visible add/remove buttons ---
    private final JButton btnAddTexEntry = new JButton("Add Texture Entry");
    private final JButton btnAddMatEntry = new JButton("Add Material Entry");
    private final JButton btnRemoveEntry = new JButton("Remove Selected Entry");

    // --- Card panel that swaps between texture / material / empty ---
    private final CardLayout entryCardLayout = new CardLayout();
    private final JPanel entryEditCards = new JPanel(entryCardLayout);

    // --- Texture editing components (inside card) ---
    private final JPanel texEditPanel = new JPanel();
    private final JList<String> texSlotList = new JList<>();
    private final JScrollPane texSlotScrollPane = new JScrollPane();
    private final JLabel lblSlot = new JLabel("Slot:");
    private final JLabel lblTexId = new JLabel("Tex ID:");
    private final JSpinner spinSlot = new JSpinner(new SpinnerNumberModel(0, 0, Short.MAX_VALUE, 1));
    private final JSpinner spinTexId = new JSpinner(new SpinnerNumberModel(0, 0, Short.MAX_VALUE, 1));
    private final JButton btnSetSlot = new JButton("Set Slot");
    private final JButton btnRemoveSlot = new JButton("Remove Slot");

    // --- Material editing components (inside card) ---
    private final JPanel matEditPanel = new JPanel();
    private final JLabel lblMatId = new JLabel("Material ID:");
    private final JLabel lblMatUnk1 = new JLabel("Unkn1:");
    private final JSpinner spinMatId = new JSpinner(new SpinnerNumberModel(0, 0, Short.MAX_VALUE, 1));
    private final JSpinner spinMatUnk1 = new JSpinner(new SpinnerNumberModel(0, 0, Short.MAX_VALUE, 1));
    private final JButton btnApplyMat = new JButton("Apply");

    private transient HSEMTextureEntry selectedTexEntry;
    private transient HSEMMaterialEntry selectedMatEntry;

    public HSEMPanel(Object obj) {
        setSelectedFile(obj);

        texEditPanel.setBorder(BorderFactory.createTitledBorder("Texture Entry"));
        texSlotList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        texSlotScrollPane.setViewportView(texSlotList);

        matEditPanel.setBorder(BorderFactory.createTitledBorder("Material Entry"));

        setupTexEditPanelLayout();
        setupMatEditPanelLayout();
        setupCardPanel();
        setupListeners();
        setupLayout();
    }

    private void setupCardPanel() {
        JPanel emptyPanel = new JPanel();
        entryEditCards.add(emptyPanel, CARD_EMPTY);
        entryEditCards.add(texEditPanel, CARD_TEXTURE);
        entryEditCards.add(matEditPanel, CARD_MATERIAL);
        entryCardLayout.show(entryEditCards, CARD_EMPTY);
    }

    private void setupListeners() {
        // When an HSEM entry is selected, show the appropriate edit card
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting())
                return;

            HSEMEntry entry = list.getSelectedValue();
            if (entry instanceof HSEMTextureEntry texEntry) {
                selectedTexEntry = texEntry;
                selectedMatEntry = null;
                refreshTexSlotList();
                setTexEditEnabled(true);
                setMatEditEnabled(false);
                entryCardLayout.show(entryEditCards, CARD_TEXTURE);
            } else if (entry instanceof HSEMMaterialEntry matEntry) {
                selectedMatEntry = matEntry;
                selectedTexEntry = null;
                spinMatId.setValue((int) matEntry.getMaterialId());
                spinMatUnk1.setValue((int) matEntry.getUnkn1());
                setMatEditEnabled(true);
                setTexEditEnabled(false);
                entryCardLayout.show(entryEditCards, CARD_MATERIAL);
            } else {
                selectedTexEntry = null;
                selectedMatEntry = null;
                clearTexSlotList();
                setTexEditEnabled(false);
                setMatEditEnabled(false);
                entryCardLayout.show(entryEditCards, CARD_EMPTY);
            }

            btnRemoveEntry.setEnabled(entry instanceof HSEMTextureEntry
                                   || entry instanceof HSEMMaterialEntry);
        });

        // --- Texture slot listeners ---

        texSlotList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || selectedTexEntry == null)
                return;

            int idx = texSlotList.getSelectedIndex();
            if (idx < 0)
                return;

            Map<Short, Short> assignments = selectedTexEntry.getTextureAssignment();
            Short[] slots = assignments.keySet().stream().sorted().toArray(Short[]::new);
            if (idx < slots.length) {
                spinSlot.setValue((int) slots[idx]);
                spinTexId.setValue((int) assignments.get(slots[idx]));
            }
        });

        btnSetSlot.addActionListener(a -> {
            if (selectedTexEntry == null)
                return;

            short slot = (short) (int) spinSlot.getValue();
            short texId = (short) (int) spinTexId.getValue();
            selectedTexEntry.getTextureAssignment().put(slot, texId);
            refreshTexSlotList();
            refreshMainList();
        });

        btnRemoveSlot.addActionListener(a -> {
            if (selectedTexEntry == null)
                return;

            int idx = texSlotList.getSelectedIndex();
            if (idx < 0)
                return;

            Map<Short, Short> assignments = selectedTexEntry.getTextureAssignment();
            Short[] slots = assignments.keySet().stream().sorted().toArray(Short[]::new);
            if (idx < slots.length) {
                assignments.remove(slots[idx]);
                refreshTexSlotList();
                refreshMainList();
            }
        });

        // --- Material listeners ---

        btnApplyMat.addActionListener(a -> {
            if (selectedMatEntry == null)
                return;

            selectedMatEntry.setMaterialId((short) (int) spinMatId.getValue());
            selectedMatEntry.setUnkn1((short) (int) spinMatUnk1.getValue());
            refreshMainList();
        });

        // --- Always-visible add/remove listeners ---

        btnAddTexEntry.addActionListener(a -> {
            if (selected == null)
                return;

            int insertIdx = list.getSelectedIndex();
            if (insertIdx < 0)
                insertIdx = selected.getEntries().size();
            else
                insertIdx++;

            HSEMTextureEntry newEntry = new HSEMTextureEntry(new TreeMap<>());
            selected.getEntries().add(insertIdx, newEntry);
            refreshMainList();
            list.setSelectedIndex(insertIdx);
        });

        btnAddMatEntry.addActionListener(a -> {
            if (selected == null)
                return;

            int insertIdx = list.getSelectedIndex();
            if (insertIdx < 0)
                insertIdx = selected.getEntries().size();
            else
                insertIdx++;

            HSEMMaterialEntry newEntry = new HSEMMaterialEntry((short) 0, (short) 0);
            selected.getEntries().add(insertIdx, newEntry);
            refreshMainList();
            list.setSelectedIndex(insertIdx);
        });

        btnRemoveEntry.addActionListener(a -> {
            if (selected == null)
                return;

            int idx = list.getSelectedIndex();
            if (idx < 0)
                return;

            HSEMEntry entry = list.getSelectedValue();
            if (!(entry instanceof HSEMTextureEntry) && !(entry instanceof HSEMMaterialEntry))
                return;

            selected.getEntries().remove(idx);
            selectedTexEntry = null;
            selectedMatEntry = null;
            clearTexSlotList();
            setTexEditEnabled(false);
            setMatEditEnabled(false);
            btnRemoveEntry.setEnabled(false);
            entryCardLayout.show(entryEditCards, CARD_EMPTY);
            refreshMainList();
        });
    }

    private void setupTexEditPanelLayout() {
        GroupLayout gl = new GroupLayout(texEditPanel);
        texEditPanel.setLayout(gl);
        gl.setAutoCreateGaps(true);
        gl.setAutoCreateContainerGaps(true);

        gl.setHorizontalGroup(gl.createParallelGroup(Alignment.LEADING)
            .addComponent(texSlotScrollPane, GroupLayout.DEFAULT_SIZE, 200, Short.MAX_VALUE)
            .addGroup(gl.createSequentialGroup()
                .addComponent(lblSlot)
                .addComponent(spinSlot, GroupLayout.PREFERRED_SIZE, 70, GroupLayout.PREFERRED_SIZE)
                .addComponent(lblTexId)
                .addComponent(spinTexId, GroupLayout.PREFERRED_SIZE, 70, GroupLayout.PREFERRED_SIZE))
            .addGroup(gl.createSequentialGroup()
                .addComponent(btnSetSlot)
                .addComponent(btnRemoveSlot))
        );

        gl.setVerticalGroup(gl.createSequentialGroup()
            .addComponent(texSlotScrollPane, GroupLayout.PREFERRED_SIZE, 100, GroupLayout.PREFERRED_SIZE)
            .addGroup(gl.createParallelGroup(Alignment.BASELINE)
                .addComponent(lblSlot)
                .addComponent(spinSlot, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addComponent(lblTexId)
                .addComponent(spinTexId, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
            .addGroup(gl.createParallelGroup(Alignment.BASELINE)
                .addComponent(btnSetSlot)
                .addComponent(btnRemoveSlot))
        );
    }

    private void setupMatEditPanelLayout() {
        GroupLayout gl = new GroupLayout(matEditPanel);
        matEditPanel.setLayout(gl);
        gl.setAutoCreateGaps(true);
        gl.setAutoCreateContainerGaps(true);

        gl.setHorizontalGroup(gl.createParallelGroup(Alignment.LEADING)
            .addGroup(gl.createSequentialGroup()
                .addComponent(lblMatId)
                .addComponent(spinMatId, GroupLayout.PREFERRED_SIZE, 80, GroupLayout.PREFERRED_SIZE)
                .addComponent(lblMatUnk1)
                .addComponent(spinMatUnk1, GroupLayout.PREFERRED_SIZE, 80, GroupLayout.PREFERRED_SIZE))
            .addComponent(btnApplyMat)
        );

        gl.setVerticalGroup(gl.createSequentialGroup()
            .addGroup(gl.createParallelGroup(Alignment.BASELINE)
                .addComponent(lblMatId)
                .addComponent(spinMatId, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addComponent(lblMatUnk1)
                .addComponent(spinMatUnk1, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
            .addComponent(btnApplyMat)
        );
    }

    private void setupLayout() {
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

                    .addGroup(layout.createSequentialGroup()
                        .addComponent(btnAddTexEntry)
                        .addComponent(btnAddMatEntry)
                        .addComponent(btnRemoveEntry))

                    .addComponent(entryEditCards,
                            GroupLayout.DEFAULT_SIZE,
                            GroupLayout.DEFAULT_SIZE,
                            Short.MAX_VALUE)
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

                    .addPreferredGap(ComponentPlacement.UNRELATED)
                    .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                        .addComponent(btnAddTexEntry)
                        .addComponent(btnAddMatEntry)
                        .addComponent(btnRemoveEntry))

                    .addPreferredGap(ComponentPlacement.RELATED)
                    .addComponent(entryEditCards,
                            GroupLayout.PREFERRED_SIZE,
                            GroupLayout.DEFAULT_SIZE,
                            GroupLayout.PREFERRED_SIZE)
                )
        );

        textPane.setFont(new Font("Inconsolata", Font.PLAIN, 12));
        setTexEditEnabled(false);
        setMatEditEnabled(false);
        btnRemoveEntry.setEnabled(false);
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

        selectedTexEntry = null;
        selectedMatEntry = null;
        clearTexSlotList();
        setTexEditEnabled(false);
        setMatEditEnabled(false);
        btnAddTexEntry.setEnabled(true);
        btnAddMatEntry.setEnabled(true);
        btnRemoveEntry.setEnabled(false);
        entryCardLayout.show(entryEditCards, CARD_EMPTY);
    }

    public HSEMPayload getSelectedFile() {
        return selected;
    }

    private void refreshMainList() {
        if (selected == null)
            return;
        int selIdx = list.getSelectedIndex();
        list.setListData(selected.getEntries().toArray(new HSEMEntry[0]));
        if (selIdx >= 0 && selIdx < selected.getEntries().size())
            list.setSelectedIndex(selIdx);
    }

    private void refreshTexSlotList() {
        if (selectedTexEntry == null)
            return;

        DefaultListModel<String> model = new DefaultListModel<>();
        selectedTexEntry.getTextureAssignment().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> model.addElement("Slot " + e.getKey() + " \u2192 Tex " + e.getValue()));
        texSlotList.setModel(model);
    }

    private void clearTexSlotList() {
        texSlotList.setModel(new DefaultListModel<>());
        spinSlot.setValue(0);
        spinTexId.setValue(0);
    }

    private void setTexEditEnabled(boolean enabled) {
        texSlotList.setEnabled(enabled);
        spinSlot.setEnabled(enabled);
        spinTexId.setEnabled(enabled);
        btnSetSlot.setEnabled(enabled);
        btnRemoveSlot.setEnabled(enabled);
    }

    private void setMatEditEnabled(boolean enabled) {
        spinMatId.setEnabled(enabled);
        spinMatUnk1.setEnabled(enabled);
        btnApplyMat.setEnabled(enabled);
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
