package net.digimonworld.decodetools.gui;

import java.awt.CardLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Observable;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreeModel;

import net.digimonworld.decodetools.Main;
import net.digimonworld.decodetools.core.Access;
import net.digimonworld.decodetools.core.FileAccess;
import net.digimonworld.decodetools.core.Utils;
import net.digimonworld.decodetools.gui.util.FunctionAction;
import net.digimonworld.decodetools.gui.util.ResPayloadTreeNode;
import net.digimonworld.decodetools.res.ResData;
import net.digimonworld.decodetools.res.ResPayload;
import net.digimonworld.decodetools.res.kcap.AbstractKCAP;

public class KCAPPanel extends EditorPanel {
    private static final long serialVersionUID = -8718473237761608043L;
    
    private JScrollPane scrollPane = new JScrollPane();
    private JTree tree = new JTree((TreeModel) null);
    private JPopupMenu popupMenu = new JPopupMenu();
    private JMenuItem importKcapItem = new JMenuItem("Import KCAP");

    private JMenuItem exportItem = new JMenuItem("Export");
    private JMenuItem removeChildItem = new JMenuItem("Remove Child");
    private JMenuItem refeshItem = new JMenuItem("Refresh");
    
    private Map<Enum<?>, PayloadPanel> panels = PayloadPanel.generatePayloadPanels();
    private final JPanel panel = new JPanel();
    private CardLayout cardLayout = new CardLayout(0, 0);
    
    public KCAPPanel(EditorModel model) {
        super(model);
        popupMenu.add(importKcapItem);
        popupMenu.add(removeChildItem);
        popupMenu.add(exportItem);
        popupMenu.add(refeshItem);
        
        importKcapItem.setAction(new FunctionAction("Import KCAP", a -> {
            if (tree.getSelectionPath() == null ||
                !(tree.getSelectionPath().getLastPathComponent() instanceof ResPayloadTreeNode))
                return;

            JFileChooser chooser = new JFileChooser("./");
            chooser.setDialogTitle("Select exported KCAP to import");
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
                return;

            File file = chooser.getSelectedFile();
            if (file == null || !file.exists())
                return;

            ResPayloadTreeNode node = (ResPayloadTreeNode) tree.getSelectionPath().getLastPathComponent();
            Object selected = node.getPayload();

            try (Access src = new FileAccess(file)) {
                // Read whatever is in the file; must be a KCAP for this flow
                ResPayload imported = ResPayload.craft(src);
                if (!(imported instanceof AbstractKCAP))
                    return; // (optional) show a dialog/toast: "Not a KCAP file"

                AbstractKCAP newKcap = (AbstractKCAP) imported;

                // If the selected node is the root, just swap the root
                ResPayloadTreeNode parentNode = (ResPayloadTreeNode) node.getParent();
                if (parentNode == null) {
                    getModel().setSelectedResource(newKcap);
                    return; // getModel().update() happens in setSelectedResource()
                }

                // Otherwise, replace the selected child inside its parent KCAP
                Object parentObj = parentNode.getPayload();
                if (!(parentObj instanceof AbstractKCAP))
                    return;

                AbstractKCAP parent = (AbstractKCAP) parentObj;

                if (!replaceChild(parent, (ResPayload) selected, newKcap))
                    return;

                // No manual offset math needed: writers recompute sizes/offsets on export
                getModel().update();
            }
            catch (IOException ex) {
                Main.LOGGER.severe("Exception while importing KCAP: " + ex.getMessage());
            }
        }));
        
        removeChildItem.setAction(new FunctionAction("Remove Child", a -> {
            if (tree.getSelectionPath() == null ||
                !(tree.getSelectionPath().getLastPathComponent() instanceof ResPayloadTreeNode))
                return;

            ResPayloadTreeNode node = (ResPayloadTreeNode) tree.getSelectionPath().getLastPathComponent();
            if (!(node.getPayload() instanceof ResPayload))
                return;

            ResPayloadTreeNode parentNode = (ResPayloadTreeNode) node.getParent();
            if (parentNode == null || !(parentNode.getPayload() instanceof AbstractKCAP))
                return;

            if (JOptionPane.showConfirmDialog(this,
                    "Remove the selected child from its parent KCAP?",
                    "Remove child",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION)
                return;

            if (removeChild((AbstractKCAP) parentNode.getPayload(), (ResPayload) node.getPayload()))
                getModel().update();
        }));

        refeshItem.setAction(new FunctionAction("Refresh", e -> getModel().update()));
        
        exportItem.setAction(new FunctionAction("Export", a -> {
            JFileChooser inputFileDialogue = new JFileChooser("./");
            inputFileDialogue.setDialogTitle("Where to save the exported file?");
            inputFileDialogue.setFileSelectionMode(JFileChooser.FILES_ONLY);
            inputFileDialogue.showOpenDialog(null);
            
            Object selected = ((ResPayloadTreeNode) tree.getSelectionPath().getLastPathComponent()).getPayload();
            File file = inputFileDialogue.getSelectedFile();
            if(!(selected instanceof ResPayload) || file == null)
                return;

            if(file.exists() && !file.delete())
                Main.LOGGER.severe("Could not delete already existing " + file.getName() + ". Aborting.");
            
            try (Access dest = new FileAccess(file); ResData data = new ResData()) {
                ((ResPayload) selected).writeKCAP(dest, data);
                
                if(data.getSize() != 0) {
                    dest.setPosition(Utils.align(((ResPayload) selected).getSize(), 0x80));
                    dest.writeByteArray(data.getStream().toByteArray());
                }
            }
            catch(IOException ex) {
                Main.LOGGER.severe("Exception while exporting file: " + ex.getMessage());
            }
        }));
        
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        
        tree.setShowsRootHandles(true);
        tree.addTreeSelectionListener(a -> {
            Object selected = ((ResPayloadTreeNode) a.getPath().getLastPathComponent()).getPayload();
            Enum<?> type = null;
            
            if (selected instanceof ResPayload && panels.containsKey(((ResPayload) selected).getType()))
                type = ((ResPayload) selected).getType();
            else if (selected instanceof AbstractKCAP && panels.containsKey(((AbstractKCAP) selected).getKCAPType()))
                type = ((AbstractKCAP) selected).getKCAPType();
            
            if (type != null) {
                cardLayout.show(panel, type.name());
                panels.get(type).setSelectedFile(selected);
            }
            else
                cardLayout.show(panel, "NULL");
        });
        
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = tree.getClosestRowForLocation(e.getX(), e.getY());
                    tree.setSelectionRow(row);
                    if(row != -1)
                        popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
        
        scrollPane.setViewportView(tree);
        
        //@formatter:off
        panel.setLayout(cardLayout);

        panel.add(PayloadPanel.NULL_PANEL, "NULL");
        panels.forEach((a, b) -> panel.add(b, a.name()));
        
        GroupLayout groupLayout = new GroupLayout(this);
        groupLayout.setHorizontalGroup(
            groupLayout.createParallelGroup(Alignment.TRAILING)
                .addGroup(groupLayout.createSequentialGroup()
                    .addComponent(scrollPane, GroupLayout.PREFERRED_SIZE, 231, GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap(ComponentPlacement.RELATED)
                    .addPreferredGap(ComponentPlacement.RELATED)
                    .addComponent(panel))
        );
        groupLayout.setVerticalGroup(
            groupLayout.createParallelGroup(Alignment.LEADING)
                .addComponent(panel, Alignment.TRAILING, GroupLayout.DEFAULT_SIZE, 642, Short.MAX_VALUE)
                .addComponent(scrollPane, Alignment.TRAILING, GroupLayout.DEFAULT_SIZE, 642, Short.MAX_VALUE)
                .addGroup(groupLayout.createSequentialGroup()
                    .addContainerGap()
                    .addContainerGap(621, Short.MAX_VALUE))
        );
        //@formatter:on
        
        setLayout(groupLayout);
    }
    
    private boolean replaceChild(AbstractKCAP parent, ResPayload selected, ResPayload replacement) {
        AbstractKCAP target = resolveMutationTarget(parent);
        if (target == null || selected == null || replacement == null)
            return false;

        if (replaceListChild(target, selected, replacement))
            return true;

        return replaceDirectChild(target, selected, replacement);
    }

    private boolean removeChild(AbstractKCAP parent, ResPayload selected) {
        AbstractKCAP target = resolveMutationTarget(parent);
        if (target == null || selected == null)
            return false;

        if (removeListChild(target, selected))
            return true;

        return clearDirectChild(target, selected);
    }

    private AbstractKCAP resolveMutationTarget(AbstractKCAP parent) {
        if (parent != null && parent.getClass().getSimpleName().equals("NormalKCAP")
                && parent.getParent() != null
                && parent.getParent().getClass().getSimpleName().equals("TDTMKCAP"))
            return parent.getParent();

        return parent;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private boolean replaceListChild(AbstractKCAP parent, ResPayload selected, ResPayload replacement) {
        for (Field field : getFields(parent.getClass())) {
            if (!List.class.isAssignableFrom(field.getType()))
                continue;

            try {
                field.setAccessible(true);
                List list = (List) field.get(parent);
                if (list == null)
                    continue;

                int idx = list.indexOf(selected);
                if (idx < 0)
                    continue;

                if (!selected.getClass().isInstance(replacement))
                    return false;

                replacement.setParent(parent);
                list.set(idx, replacement);
                return true;
            }
            catch (IllegalAccessException | UnsupportedOperationException ex) {
                Main.LOGGER.severe("Could not replace KCAP child: " + ex.getMessage());
                return false;
            }
        }

        return false;
    }

    private boolean removeListChild(AbstractKCAP parent, ResPayload selected) {
        for (Field field : getFields(parent.getClass())) {
            if (!List.class.isAssignableFrom(field.getType()))
                continue;

            try {
                field.setAccessible(true);
                @SuppressWarnings("rawtypes")
                List list = (List) field.get(parent);
                if (list != null && list.remove(selected))
                    return true;
            }
            catch (IllegalAccessException | UnsupportedOperationException ex) {
                Main.LOGGER.severe("Could not remove KCAP child: " + ex.getMessage());
                return false;
            }
        }

        return false;
    }

    private boolean replaceDirectChild(AbstractKCAP parent, ResPayload selected, ResPayload replacement) {
        for (Field field : getFields(parent.getClass())) {
            if (!ResPayload.class.isAssignableFrom(field.getType()))
                continue;

            try {
                field.setAccessible(true);
                Object current = field.get(parent);
                if (current != selected)
                    continue;

                if (!field.getType().isInstance(replacement))
                    return false;

                replacement.setParent(parent);
                field.set(parent, replacement);
                return true;
            }
            catch (IllegalAccessException ex) {
                Main.LOGGER.severe("Could not replace KCAP child field: " + ex.getMessage());
                return false;
            }
        }

        return false;
    }

    private boolean clearDirectChild(AbstractKCAP parent, ResPayload selected) {
        for (Field field : getFields(parent.getClass())) {
            if (!ResPayload.class.isAssignableFrom(field.getType()))
                continue;

            try {
                field.setAccessible(true);
                if (field.get(parent) != selected)
                    continue;

                field.set(parent, null);
                return true;
            }
            catch (IllegalAccessException ex) {
                Main.LOGGER.severe("Could not clear KCAP child field: " + ex.getMessage());
                return false;
            }
        }

        return false;
    }

    private List<Field> getFields(Class<?> type) {
        java.util.ArrayList<Field> fields = new java.util.ArrayList<>();

        for (Class<?> current = type; current != null; current = current.getSuperclass())
            fields.addAll(java.util.Arrays.asList(current.getDeclaredFields()));

        return fields;
    }

    @Override
    public void update(Observable o, Object arg) {
        if (tree.getModel() != getModel().getTreeModel())
            tree.setModel(getModel().getTreeModel());
    }
    
    public JTree getTree() {
        return tree;
    }
}
