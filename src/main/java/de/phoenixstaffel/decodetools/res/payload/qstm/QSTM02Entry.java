package de.phoenixstaffel.decodetools.res.payload.qstm;

import de.phoenixstaffel.decodetools.core.Access;

public class QSTM02Entry implements QSTMEntry {
    private int unk1;
    private int jointId;
    
    public QSTM02Entry(Access source) {
        this.unk1 = source.readInteger();
        this.jointId = source.readInteger();
    }
    
    @Override
    public short getSize() {
        return 8;
    }
    
    @Override
    public QSTMEntryType getType() {
        return QSTMEntryType.UNK02;
    }
    
    @Override
    public void writeKCAP(Access dest) {
        dest.writeShort(getType().getId());
        dest.writeShort(getSize());
        
        dest.writeInteger(unk1);
        dest.writeInteger(jointId);
    }
}