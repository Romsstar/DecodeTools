package net.digimonworld.decodetools.res.payload;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import net.digimonworld.decodetools.Main;
import net.digimonworld.decodetools.core.Access;
import net.digimonworld.decodetools.res.ResData;
import net.digimonworld.decodetools.res.ResPayload;
import net.digimonworld.decodetools.res.kcap.AbstractKCAP;
import net.digimonworld.decodetools.res.payload.hsem.HSEMEntry;

/*-
 * HSEM "head" (0x40 byte)
 *  ID (4 byte)
 *  size (4 byte)
 *  entry count (4 byte)
 *  unk2 (4 byte)
 *  10x unk3 (float)
 *  unk3 (4 byte)
 *  unk4 (4 byte)
 * HSEM "payload" (size - 0x40 byte)
 *  variable amount of payload entries
 *   id (short)
 *   size (short)
 *   data (size - 4)
 * 
 * 
 * 0x58                     HSEM into data
 * 0x14 byte                intro data
 * 0x04 * short(0x12) byte  joint data
 * 0x14 byte                outro data
 */
public class HSEMPayload extends ResPayload {
    private int id;
    // int size
    // int numEntries;
    private short unknown1_1;
    private byte unknown1_2;
    private byte unknown1_3;

    /*
     * visible distance X?
     * visible rotation? 0?
     * visible distance Y?
     * rotation something?
     */
    private float[] headerData = new float[10];
    private int unknown2;
    private int unknown3;

    private List<HSEMEntry> entries = new ArrayList<>();

    public HSEMPayload(AbstractKCAP parent, List<HSEMEntry> entries, int id, short unknown1_1, byte unknown1_2,
                       byte unknown1_3, float[] headerData, int unknown2, int unknown3) {
        super(parent);

        this.id = id;
        this.unknown1_1 = unknown1_1;
        this.unknown1_2 =  unknown1_2;
        this.unknown1_3 =  unknown1_3;
        this.headerData = Arrays.copyOf(headerData, 10);
        this.unknown2 = unknown2;
        this.unknown3 = unknown3;
        this.entries.addAll(entries);
    }

    public HSEMPayload(Access source, int dataStart, AbstractKCAP parent, int size, String name) {
        super(parent);

        long start = source.getPosition();

        this.id = source.readInteger();
        source.readInteger();
        int numEntries = source.readInteger();
        this.unknown1_1 = source.readShort();
        this.unknown1_2 = source.readByte();
        this.unknown1_3 = source.readByte();

        for (int i = 0; i < 10; i++) {
            this.headerData[i] = source.readFloat();
        }

        this.unknown2 = source.readInteger();
        this.unknown3 = source.readInteger();

        for (int i = 0; i < numEntries; i++)
            entries.add(HSEMEntry.loadEntry(source));

        if (source.getPosition() - start != size)
            Main.LOGGER.warning("HSEM Payload was smaller than advertised.");
    }

    @Override
    public int getSize() {
        return 0x40 + entries.stream().collect(Collectors.summingInt(HSEMEntry::getSize));
    }

    @Override
    public Payload getType() {
        return Payload.HSEM;
    }

    @Override
    public void writeKCAP(Access dest, ResData dataStream) {
        dest.writeInteger(id);
        dest.writeInteger(getSize());

        dest.writeInteger(entries.size());
        dest.writeShort(unknown1_1);
        dest.writeByte(unknown1_2);
        dest.writeByte(unknown1_3);

        for (float f : headerData)
            dest.writeFloat(f);

        dest.writeInteger(unknown2);
        dest.writeInteger(unknown3);

        entries.forEach(a -> a.writeKCAP(dest));
    }

    public List<HSEMEntry> getEntries() {
        return entries;
    }

    public float[] getHeaderData() {
        return headerData;
    }

    public int getId() {
        return id;
    }

    public int getUnknown1_1() {
        return unknown1_1;
    }

    public short getUnknown1_2() {
        return unknown1_2;
    }

    public byte getUnknown1_3() {
        return unknown1_3;
    }

    public int getUnknown2() {
        return unknown2;
    }

    public int getUnknown3() {
        return unknown3;
    }
}
