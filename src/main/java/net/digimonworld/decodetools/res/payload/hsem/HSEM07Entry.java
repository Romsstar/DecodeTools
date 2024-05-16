package net.digimonworld.decodetools.res.payload.hsem;

import net.digimonworld.decodetools.core.Access;

public class HSEM07Entry implements HSEMEntry {
    private short unkn1; // culling mode?
    private short unkn2;
    private short unkn3; // transparency mode?
    private short unkn4;
    
    public HSEM07Entry(Access source) {
        setUnkn1(source.readShort());
        setUnkn2(source.readShort());
        setUnkn3(source.readShort());
        setUnkn4(source.readShort());
    }
    
    public HSEM07Entry(short b, short c, short d, short e) {
        this.setUnkn1(b);
        this.setUnkn2(c);
        this.setUnkn3(d);
        this.setUnkn4(e);
    }

    @Override
    public void writeKCAP(Access dest) {
        dest.writeShort((short) getHSEMType().getId());
        dest.writeShort((short) getSize());
        
        dest.writeShort(getUnkn1());
        dest.writeShort(getUnkn2());
        dest.writeShort(getUnkn3());
        dest.writeShort(getUnkn4());
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
        return String.format("Entry07 | U1: %s | U2: %s | U3: %s | U4: %s", getUnkn1(), getUnkn2(), getUnkn3(), getUnkn4());
    }

    public short getUnkn1() {
        return unkn1;
    }

    public void setUnkn1(short unkn1) {
        this.unkn1 = unkn1;
    }

    public short getUnkn2() {
        return unkn2;
    }

    public void setUnkn2(short unkn2) {
        this.unkn2 = unkn2;
    }

    public short getUnkn3() {
        return unkn3;
    }

    public void setUnkn3(short unkn3) {
        this.unkn3 = unkn3;
    }

    public short getUnkn4() {
        return unkn4;
    }

    public void setUnkn4(short unkn4) {
        this.unkn4 = unkn4;
    }
}
