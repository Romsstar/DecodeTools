package net.digimonworld.decodetools.gui;

import java.util.HashMap;
import java.util.Map;

import javax.swing.JPanel;

import net.digimonworld.decodetools.res.ResPayload.Payload;
import net.digimonworld.decodetools.res.kcap.AbstractKCAP;

public abstract class PayloadPanel extends JPanel {
    private static final long serialVersionUID = -889159315719439977L;
    
    public static final PayloadPanel NULL_PANEL = new PayloadPanel() {
        private static final long serialVersionUID = -1637343165773635969L;
        
        @Override
        public void setSelectedFile(Object file) {
            // no implementation
        }
    };
    
    public abstract void setSelectedFile(Object file);
    
    public static Map<Enum<?>, PayloadPanel> generatePayloadPanels() {
        Map<Enum<?>, PayloadPanel> tempPanels = new HashMap<>();
        
        tempPanels.put(Payload.GMIO, new GMIOPanel(null));
        tempPanels.put(AbstractKCAP.KCAPType.GMIP, new GMIPPanel(null));
        tempPanels.put(AbstractKCAP.KCAPType.TDTM, new TDTMPanel(null));        
        tempPanels.put(AbstractKCAP.KCAPType.KPTF, new KPTFPanel(null));
        tempPanels.put(Payload.HSEM, new HSEMPanel(null));
        tempPanels.put(Payload.QSTM, new QSTMPanel(null));
        tempPanels.put(Payload.VCTM, new VCTMPanel(null));
        tempPanels.put(AbstractKCAP.KCAPType.HSMP, new ModelImporter(null));
        tempPanels.put(Payload.GENERIC, new GenericPanel(null));
        tempPanels.put(Payload.BTX, new BTXPanel(null));
        tempPanels.put(Payload.LRTM, new LRTMPanel(null));
        
        return tempPanels;
    }
}
