package net.digimonworld.decodetools.res.payload;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.lwjgl.assimp.AIQuatKey;
import org.lwjgl.assimp.AIVectorKey;

import net.digimonworld.decodetools.core.Access;
import net.digimonworld.decodetools.res.ResData;
import net.digimonworld.decodetools.res.ResPayload;
import net.digimonworld.decodetools.res.kcap.AbstractKCAP;
import net.digimonworld.decodetools.res.payload.qstm.QSTM00Entry;
import net.digimonworld.decodetools.res.payload.qstm.QSTM02Entry;
import net.digimonworld.decodetools.res.payload.qstm.QSTMEntry;

public class QSTMPayload extends ResPayload {
    private short unknown1; // known values 0 1 2 4, never read? QSTM 0 = 2, QSTM 2 = 4
    
    private List<QSTMEntry> entries = new ArrayList<>();
    
    public QSTMPayload(Access source, int dataStart, AbstractKCAP parent, int size, String name) {
        super(parent);
        
        source.readInteger(); // magic value
        unknown1 = source.readShort();
        short numEntries = source.readShort();
        
        for(int i = 0; i < numEntries; i++) 
            entries.add(QSTMEntry.loadEntry(source));
    }

    public QSTMPayload(AbstractKCAP parent, AIVectorKey key) {
        super(parent);

        unknown1 = 2; // Figure out what this value means?

        List<Float> vals = new ArrayList<Float>();

        vals.add(key.mValue().x());
        vals.add(key.mValue().y());
        vals.add(key.mValue().z());

        QSTMEntry qstmEntry = new QSTM00Entry(vals);

        entries.add(qstmEntry);
    }

    public QSTMPayload(AbstractKCAP parent, AIQuatKey key) {
        super(parent);

        unknown1 = 2; // Figure out what this value means?

        List<Float> vals = new ArrayList<Float>();

        vals.add(key.mValue().x());
        vals.add(key.mValue().y());
        vals.add(key.mValue().z());
        vals.add(key.mValue().w());

        QSTMEntry qstmEntry = new QSTM00Entry(vals);

        entries.add(qstmEntry);
    }

    public QSTMPayload(AbstractKCAP parent, int vctmId) {
        super(parent);

        unknown1 = 4; // Figure out what this value means?

        QSTMEntry qstmEntry = new QSTM02Entry(vctmId);

        entries.add(qstmEntry);
    }
    
    public short getUnknown1() {
        return unknown1;
    }
    
    public List<QSTMEntry> getEntries() {
        return entries;
    }
    
    @Override
    public int getSize() {
        return 8 + entries.stream().collect(Collectors.summingInt(QSTMEntry::getSize));
    }
    
    @Override
    public Payload getType() {
        return Payload.QSTM;
    }
    
    @Override
    public void writeKCAP(Access dest, ResData dataStream) {
        dest.writeInteger(getType().getMagicValue());
        dest.writeShort(unknown1);
        dest.writeShort((short) entries.size());
        
        entries.forEach(a -> a.writeKCAP(dest));
    }
}
