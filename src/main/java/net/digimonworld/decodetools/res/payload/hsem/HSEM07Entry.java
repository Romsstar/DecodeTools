package net.digimonworld.decodetools.res.payload.hsem;

import net.digimonworld.decodetools.core.Access;

public class HSEM07Entry implements HSEMEntry {
    private short unkn1; // culling mode?
    private short unkn2;
    private byte unkn3_1; // transparency mode?
    private byte unkn3_2; // transparency mode?
    private short unkn4;
    
    public HSEM07Entry(Access source) {
        unkn1 = source.readShort();
        unkn2 = source.readShort();
        unkn3_1 = source.readByte();
        unkn3_2 = source.readByte();
        unkn4 = source.readShort();
    }
    
    public HSEM07Entry(short b, short c, byte d, byte e,short f) {
        this.unkn1 = b;
        this.unkn2 = c;
        this.unkn3_1 = d;
        this.unkn3_2 = e;
        this.unkn4 = f;
    }

    public int getCulling() {
        return unkn1;
    }
    
    public int getTransparency() {
        return unkn3_1;
    }
    
    public int getUnk4() {
        return unkn4;
    }
    
    public int getMask() {
        return unkn3_2;
    }

    @Override
    public void writeKCAP(Access dest) {
        dest.writeShort((short) getHSEMType().getId());
        dest.writeShort((short) getSize());
        
        dest.writeShort(unkn1);
        dest.writeShort(unkn2);
        dest.writeByte(unkn3_1);
        dest.writeByte(unkn3_2);
        dest.writeShort(unkn4);
    }
    
    @Override
    public int getSize() {
        return 0x0C;
    }
    
    @Override
    public HSEMEntryType getHSEMType() {
        return HSEMEntryType.UNK07;
    }
    

    @Override
    public String toString() {
        return String.format(
            "Entry07 | U1: %s | U2: %s | U3a: %s | U3b: %s | U4: %s",
            unkn1, unkn2, unkn3_1, unkn3_2, unkn4
        );
    }

}