package net.digimonworld.decodetools.res.payload;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private short unknown1; // 1, 2 = normal loop, 3-4 have functions
    
    private List<QSTMEntry> entries = new ArrayList<>();

    private short unkDefault = 2;
    
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

        unknown1 = unkDefault;

        List<Float> vals = new ArrayList<Float>();

        float valx=roundTo6DecimalPlaces(key.mValue().x());
        float valy=roundTo6DecimalPlaces(key.mValue().y());
        float valz=roundTo6DecimalPlaces(key.mValue().z());
        vals.add(valx);
        vals.add(valy);
        vals.add(valz);


        QSTMEntry qstmEntry = new QSTM00Entry(vals);

        entries.add(qstmEntry);
    }

    public QSTMPayload(AbstractKCAP parent, AIQuatKey key) {
        super(parent);

        unknown1 = unkDefault;

        List<Float> vals = new ArrayList<Float>();

        float valx=roundTo6DecimalPlaces(key.mValue().x());
        float valy=roundTo6DecimalPlaces(key.mValue().y());
        float valz=roundTo6DecimalPlaces(key.mValue().z());
        float valw=roundTo6DecimalPlaces(key.mValue().w());
     
        vals.add(valx);
        vals.add(valy);
        vals.add(valz);
        vals.add(valw);
      

        QSTMEntry qstmEntry = new QSTM00Entry(vals);

        entries.add(qstmEntry);
    }

    public QSTMPayload(AbstractKCAP parent, List<Float> vals) {
        super(parent);

        unknown1 = unkDefault;

        QSTMEntry qstmEntry = new QSTM00Entry(vals);

        entries.add(qstmEntry);
    }

    public QSTMPayload(AbstractKCAP parent, int vctmId) {
        super(parent);

        unknown1 = unkDefault;

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
    
    public void setUnknown1(short unknown1) {
        this.unknown1 = unknown1;
    }
    @Override
    public void writeKCAP(Access dest, ResData dataStream) {
        dest.writeInteger(getType().getMagicValue());
        dest.writeShort(unknown1);
        dest.writeShort((short) entries.size());
        
        entries.forEach(a -> a.writeKCAP(dest));
    }
    
    private static float roundTo6DecimalPlaces(float value) {
        return BigDecimal.valueOf(value)
                         .setScale(6, RoundingMode.HALF_UP)
                         .floatValue();
    }
}
