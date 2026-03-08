package net.digimonworld.decodetools.gui;

import java.awt.CardLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Observable;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JFileChooser;
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
    private JMenuItem refeshItem = new JMenuItem("Refresh");
    private JMenuItem exportVctmCsvItem = new JMenuItem("Export VCTMs as CSV");
    
    private Map<Enum<?>, PayloadPanel> panels = PayloadPanel.generatePayloadPanels();
    private final JPanel panel = new JPanel();
    private CardLayout cardLayout = new CardLayout(0, 0);
    
    public KCAPPanel(EditorModel model) {
        super(model);
        popupMenu.add(importKcapItem);
        popupMenu.add(exportItem);
        popupMenu.add(refeshItem);
        popupMenu.addSeparator();
        popupMenu.add(exportVctmCsvItem);

        exportVctmCsvItem.setAction(new FunctionAction("Export VCTMs as CSV", a -> {
            if (tree.getSelectionPath() == null ||
                !(tree.getSelectionPath().getLastPathComponent() instanceof ResPayloadTreeNode))
                return;

            JFileChooser chooser = new JFileChooser("./");
            chooser.setDialogTitle("Select output folder for VCTM CSV files");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION)
                return;

            File outputDir = chooser.getSelectedFile();
            if (outputDir == null) return;
            outputDir.mkdirs();

            ResPayloadTreeNode node =
                (ResPayloadTreeNode) tree.getSelectionPath().getLastPathComponent();

            // Collect all VCTMPayloads under the selected node, tagged with their
            // index within their parent KCAP.
            List<int[]> ids = new ArrayList<>();
            List<net.digimonworld.decodetools.res.payload.VCTMPayload> found = new ArrayList<>();
            collectVctms(node.getPayload(), found, ids);

            if (found.isEmpty()) {
                javax.swing.JOptionPane.showMessageDialog(null,
                    "No VCTM payloads found under the selected node.",
                    "Export VCTMs", javax.swing.JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            int exported = 0;
            for (int i = 0; i < found.size(); i++) {
                net.digimonworld.decodetools.res.payload.VCTMPayload vctm = found.get(i);
                int idx = ids.get(i)[0];

                String label = VCTMPanel.resolveJointLabel(vctm);
                if (label == null) label = "vctm_" + idx;

                File csv = new File(outputDir, label + ".csv");
                try (java.io.PrintWriter pw = new java.io.PrintWriter(
                        new java.io.FileWriter(csv))) {
                    pw.println("Frame Index,Time,Time Scale,Component Count," +
                               "Component Type,Interpolation,X,Y,Z,W");
                    float[] times  = vctm.getFrameTimes();
                    int compCount  = vctm.getComponentCount();
                    String ctype   = vctm.getComponentType().toString();
                    String interp  = vctm.getInterpolationMode().toString();
                    String scale   = vctm.getTimeScale().toString();
                    for (int f = 0; f < times.length; f++) {
                        Byte[][] raw = vctm.getRawFrameData(f);
                        String[] comp = {"", "", "", ""};
                        for (int c = 0; c < compCount; c++) {
                            byte[] bytes = new byte[raw[c].length];
                            for (int b = 0; b < bytes.length; b++) bytes[b] = raw[c][b];
                            comp[c] = VCTMPanel.decodeComponent(bytes, ctype);
                        }
                        pw.printf(java.util.Locale.ROOT,
                            "%d,%.6f,%s,%d,%s,%s,%s,%s,%s,%s%n",
                            f, times[f], scale, compCount, ctype, interp,
                            comp[0], comp[1], comp[2], comp[3]);
                    }
                    exported++;
                } catch (IOException ex) {
                    Main.LOGGER.severe("Failed to write " + csv.getName() + ": " + ex.getMessage());
                }
            }

            javax.swing.JOptionPane.showMessageDialog(null,
                "Exported " + exported + " VCTM file(s) to " + outputDir.getPath(),
                "Export VCTMs", javax.swing.JOptionPane.INFORMATION_MESSAGE);
        }));
        
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

                // Find the child index and replace it in place
                int idx = parent.getEntries().indexOf(selected);
                if (idx < 0) return;

                newKcap.setParent(parent);               // keep the tree consistent
                parent.getEntries().set(idx, newKcap);   // NormalKCAP etc. allow this

                // No manual offset math needed: writers recompute sizes/offsets on export
                getModel().update();
            }
            catch (IOException ex) {
                Main.LOGGER.severe("Exception while importing KCAP: " + ex.getMessage());
            }
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
    
    /**
     * Recursively walks the payload tree rooted at {@code payload}, collecting
     * every VCTMPayload into {@code result}. {@code ids} receives a single-element
     * int[] per entry containing the payload's index inside its parent KCAP.
     */
    private static void collectVctms(
            Object payload,
            List<net.digimonworld.decodetools.res.payload.VCTMPayload> result,
            List<int[]> ids) {

        if (payload instanceof net.digimonworld.decodetools.res.payload.VCTMPayload) {
            // ID = position in parent's entry list (best-effort; -1 if unresolvable)
            net.digimonworld.decodetools.res.payload.VCTMPayload vctm =
                (net.digimonworld.decodetools.res.payload.VCTMPayload) payload;
            int idx = -1;
            if (vctm.getParent() != null) {
                List<ResPayload> siblings = vctm.getParent().getEntries();
                idx = siblings.indexOf(vctm);
            }
            result.add(vctm);
            ids.add(new int[]{ idx });
            return;
        }

        if (payload instanceof AbstractKCAP) {
            List<ResPayload> entries = ((AbstractKCAP) payload).getEntries();
            for (ResPayload child : entries) {
                if (child != null)
                    collectVctms(child, result, ids);
            }
        }
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
