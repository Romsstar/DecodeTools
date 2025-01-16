package net.digimonworld.decodetools.res.kcap;

import static de.javagl.jgltf.model.GltfConstants.GL_FLOAT;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.NoSuchElementException;

import de.javagl.jgltf.impl.v2.Accessor;
import de.javagl.jgltf.impl.v2.Animation;
import de.javagl.jgltf.impl.v2.AnimationChannel;
import de.javagl.jgltf.impl.v2.AnimationChannelTarget;
import de.javagl.jgltf.impl.v2.AnimationSampler;
import de.javagl.jgltf.impl.v2.Buffer;
import de.javagl.jgltf.impl.v2.BufferView;
import de.javagl.jgltf.impl.v2.GlTF;
import net.digimonworld.decodetools.Main;
import net.digimonworld.decodetools.core.Access;
import net.digimonworld.decodetools.core.Utils;
import net.digimonworld.decodetools.gui.GLTFExporter;
import net.digimonworld.decodetools.res.ResData;
import net.digimonworld.decodetools.res.ResPayload;
import net.digimonworld.decodetools.res.payload.QSTMPayload;
import net.digimonworld.decodetools.res.payload.VCTMPayload;
import net.digimonworld.decodetools.res.payload.qstm.Axis;
import net.digimonworld.decodetools.res.payload.qstm.QSTM00Entry;
import net.digimonworld.decodetools.res.payload.qstm.QSTM01Entry;
import net.digimonworld.decodetools.res.payload.qstm.QSTM02Entry;
import net.digimonworld.decodetools.res.payload.qstm.QSTMEntry;
import net.digimonworld.decodetools.res.payload.qstm.QSTMEntryType;


// sets up the animation data, i.e. initial positions, which anim data to use and more
public class TDTMKCAP extends AbstractKCAP {
    private static final int TDTM_VERSION = 2;
    
    private List<TDTMEntry> tdtmEntry = new ArrayList<>();
    
    private List<QSTMPayload> qstm = new ArrayList<>();
    private List<VCTMPayload> vctm = new ArrayList<>();
    
    private float time1; // start
    private float time2; // end
    private float time3; // loop start
    private float time4; // loop end
    
    protected TDTMKCAP(AbstractKCAP parent, Access source, int dataStart, KCAPInformation info) {
        super(parent, info.flags);
        
        // make sure it's actually a TDTM
        if (source.readInteger() != KCAPType.TDTM.getMagicValue())
            throw new IllegalArgumentException("Tried to instanciate TDTM KCAP, but didn't find a TDTM header.");
        
        int version = source.readInteger();
        if (version != TDTM_VERSION)
            throw new IllegalArgumentException("Tried to instanciate TDTM KCAP and expected version 2, but got " + version);
        
        int numEntries = source.readInteger();
        source.readInteger(); // padding
        
        time1 = source.readFloat();
        time2 = source.readFloat();
        time3 = source.readFloat();
        time4 = source.readFloat();
        
        // one TDTM entry per QSTM in qstmKCAP?
        for (int i = 0; i < numEntries; ++i)
            tdtmEntry.add(new TDTMEntry(source));
        
        // TODO inconsistent by design?
        if (numEntries % 2 == 1 && info.headerSize % 0x10 == 0x00)
            source.readLong(); // padding
            
        List<KCAPPointer> pointer = loadKCAPPointer(source, info.entries);
        
        if (pointer.size() != 2)
            throw new IllegalArgumentException("A TDTM KCAP always has two elements, but this one has " + pointer.size() + "!");
        
        source.setPosition(info.startAddress + pointer.get(0).getOffset());
        NormalKCAP qstmKCAP = (NormalKCAP) AbstractKCAP.craftKCAP(source, this, dataStart);
        
        source.setPosition(info.startAddress + pointer.get(1).getOffset());
        NormalKCAP vctmKCAP = (NormalKCAP) AbstractKCAP.craftKCAP(source, this, dataStart);
        
        for (ResPayload entry : qstmKCAP) {
            if (entry.getType() != Payload.QSTM)
                throw new IllegalArgumentException("Got a " + entry.getType() + " entry, but only QSTM entries are allowed.");
            
            qstm.add((QSTMPayload) entry);
        }
        
        for (ResPayload entry : vctmKCAP) {
            if (entry.getType() != Payload.VCTM)
                throw new IllegalArgumentException("Got a " + entry.getType() + " entry, but only VCTM entries are allowed.");
            
            vctm.add((VCTMPayload) entry);
        }
        
        // make sure we're at the end of the KCAP
        long expectedEnd = info.startAddress + info.size;
        if (source.getPosition() != expectedEnd)
            Main.LOGGER.warning(() -> "Final position for TDTM KCAP does not match the header. Current: " + source.getPosition() + " Expected: " + expectedEnd);
    }

    public void exportGLTFAnimation(GlTF instance, int index) {
        List<AnimationChannel> channels = new ArrayList<AnimationChannel>();
        List<AnimationSampler> samplers = new ArrayList<AnimationSampler>();

        String name;

        switch (index) {
            case 0: name = "idle"; break;
            case 1: name = "run"; break;
            case 2: name = "happy"; break;
            case 3: name = "refuse"; break;
            case 4: name = "sleep"; break;
            case 5: name = "exhausted"; break;
            case 6: name = "attack"; break;
            case 7: name = "attack2"; break;
            case 8: name = "attack3"; break;
            case 9: name = "guard"; break;
            case 10: name = "hit"; break;
            case 11: name = "defeated"; break;
            case 12: name = "specialattack"; break;
            case 13: name = "backstep"; break;

            default: name = "anim_" + index; break;
        }

        //System.out.println("Animation: " + name);

        // Each TDTM Entry can only map one joint, contains translation OR rotation OR scale
        for (int i = 0; i < tdtmEntry.size(); i++) {

            TDTMEntry tEntry = tdtmEntry.get(i);
            int jointId = tEntry.jointId;
            float animDuration = (time2-time1)/333;

            //System.out.println("Duration: " + animDuration);

            // Create an animation channel target
            AnimationChannelTarget act = new AnimationChannelTarget();
            act.setNode(jointId); // Set Node

            // Sort the QSTM Entries by type
            QSTM00Entry qstm00Entry = null;
            List<QSTM01Entry> qstm01Entries = new ArrayList<QSTM01Entry>();
            QSTM02Entry qstm02Entry = null;

            QSTMPayload qstmPayload = qstm.get(tEntry.qstmId);

            for (int j = 0; j < qstmPayload.getEntries().size(); j++) {
                QSTMEntry qEntry = qstmPayload.getEntries().get(j);
                QSTMEntryType type = qEntry.getType();

                switch(type.getId()) {
                    case 0: qstm00Entry = (QSTM00Entry)qEntry; break;
                    case 1: qstm01Entries.add((QSTM01Entry)qEntry); break;
                    case 2: qstm02Entry = (QSTM02Entry)qEntry; break;
                    default: break;
                }
            }

            List<Float> times = new ArrayList<Float>();

            Hashtable<Float, Float> xValues = new Hashtable<Float, Float>();
            Hashtable<Float, Float>  yValues = new Hashtable<Float, Float>();
            Hashtable<Float, Float>  zValues = new Hashtable<Float, Float>();
            Hashtable<Float, Float>  wValues = new Hashtable<Float, Float>();

            // Get QSTM02Entry and VCTM first, then apply QSTM01Entry changes
            if (qstm02Entry != null) {
                List<Byte[]> rawXBytes = new ArrayList<Byte[]>();
                List<Byte[]> rawYBytes = new ArrayList<Byte[]>();
                List<Byte[]> rawZBytes = new ArrayList<Byte[]>();
                List<Byte[]> rawWBytes = new ArrayList<Byte[]>();

                Axis axis = (qstm02Entry).getAxis();

                VCTMPayload vctmPayload = vctm.get(qstm02Entry.getVctmId());

                //interMode = vctmPayload.getInterpolationMode();

                int numEntries = vctmPayload.getNumEntries();
                int numBytes = vctmPayload.GetValueBytes();

                Byte[] zeros = new Byte[numBytes];
                for (int j = 0; j < numBytes; j++) {
                    zeros[j] = 0x00;
                }

                for (int j = 0; j < numEntries; j++) {
                    Byte[][] rawFrameData = vctmPayload.getRawFrameData(j);

                    boolean[] dataEntered = {false, false, false, false};
                    
                    int start = 0;

                    switch(axis) {
                        case Y: start = 1; break;
                        case Z: start = 2; break;
                        case W: start = 3; break;
                        default: break;
                    }

                    for (int k = 0; k < rawFrameData.length; k++) {
                        switch(k+start) {
                            case 0: rawXBytes.add(rawFrameData[k]); dataEntered[0] = true; break;
                            case 1: rawYBytes.add(rawFrameData[k]); dataEntered[1] = true; break;
                            case 2: rawZBytes.add(rawFrameData[k]); dataEntered[2] = true; break;
                            case 3: rawWBytes.add(rawFrameData[k]); dataEntered[3] = true; break;
                        }
                    }

                    if (!dataEntered[0]) rawXBytes.add(zeros);
                    if (!dataEntered[1]) rawYBytes.add(zeros);
                    if (!dataEntered[2]) rawZBytes.add(zeros);
                    if (!dataEntered[3] && tEntry.mode == TDTMMode.ROTATION) rawWBytes.add(zeros);
                }

                //Handle QSTM01 Entries
                for (int j = 0; j < qstm01Entries.size(); j++) {
                    QSTM01Entry qEntry = qstm01Entries.get(j);

                    int dest = qEntry.getDestId();
                    int src = qEntry.getSrcId();
                    int size = qEntry.getSizeData();
                    int mode = qEntry.getMode();

                    //System.out.println("QSTM01: mode " + mode + " size " + size + " src " + src + " dest " + dest);

                    Byte[] rawSrcData = new Byte[numBytes];

                    if (mode == 0) {
                        for (int k = 0; k < numEntries; k++) {
                            for (int b = 0; b < numBytes; b++) {
                                switch(src) {
                                    case 0: rawSrcData[b] = rawZBytes.get(k)[b]; break;
                                    case 1: rawSrcData[b] = rawXBytes.get(k)[b]; break;
                                    case 2: rawSrcData[b] = rawYBytes.get(k)[b]; break;
                                }
                            }
    
                            for (int b = 0; b < numBytes; b++) {
                                switch(dest) {
                                    case 0: rawZBytes.get(k)[b] = rawSrcData[b]; break;
                                    case 1: rawXBytes.get(k)[b] = rawSrcData[b]; break;
                                    case 2: rawYBytes.get(k)[b] = rawSrcData[b]; break;
                                }
                            }
                        }
                    }
                    else {
                        System.out.println("Encountered QSTM01 with mode != 0");
                    }
                }

                // Handle QSTM00 Entries
                float[] qstm00Mask = {0, 0, 0, 0};

                if (qstm00Entry != null) {
                    if (qstm00Entry.getMode() == 0) {
                        //System.out.println("QSTM00 Found with QSTM02");
                        axis = qstm00Entry.getAxis();
                        List<Float> qstm0Values = (qstm00Entry).getValues();

                        int start = 0;
    
                        switch(axis) {
                            case Y: start = 1; break;
                            case Z: start = 2; break;
                            case W: start = 3; break;
                            default: break;
                        }
    
                        for (int k = 0; k < qstm0Values.size(); k++) {
                            qstm00Mask[k+start] = qstm0Values.get(k);
                        }
                    }
                    else {
                        System.out.println("Encountered QSTM00 with mode != 0");
                    }
                }

                float[] qstmTimes = vctmPayload.getFrameList();
                float[] timestamps = new float[qstmTimes.length];
                
                for (int j = 0; j < qstmTimes.length; j++) {
                    timestamps[j] = animDuration*((qstmTimes[j])/qstmTimes[qstmTimes.length-1]);
                }
                for (int j = 0; j < timestamps.length; j++) {
                    if (!times.contains(timestamps[j])) {
                        times.add(timestamps[j]);
                    }
                }

                Collections.sort(times);

                // Convert all raw bytes to floats or use QSTM 0 Mask
                for (int j = 0; j < times.size(); j++) {
                    if (qstm00Mask[0] == 0) {
                        if (rawXBytes.size() > j) {
                            byte[] rawXbytes = new byte[rawXBytes.get(j).length];
    
                            for (int k = 0; k < rawXBytes.get(j).length; k++) {
                                rawXbytes[k] = rawXBytes.get(j)[k].byteValue();
                            }
                            xValues.put(times.get(j), vctmPayload.convertBytesToValue(rawXbytes));
                        }
                    }
                    else {
                        xValues.put(times.get(j), qstm00Mask[0]);
                    }

                    if (qstm00Mask[1] == 0) {
                        if (rawYBytes.size() > j) {
                            byte[] rawYbytes = new byte[rawYBytes.get(j).length];
        
                            for (int k = 0; k < rawYBytes.get(j).length; k++) {
                                rawYbytes[k] = rawYBytes.get(j)[k].byteValue();
                            }
                            
                            yValues.put(times.get(j), vctmPayload.convertBytesToValue(rawYbytes));
                        }
                    }
                    else {
                        yValues.put(times.get(j), qstm00Mask[1]);
                    }

                    if (qstm00Mask[2] == 0) {
                        if (rawZBytes.size() > j) {
                            byte[] rawZbytes = new byte[rawZBytes.get(j).length];
        
                            for (int k = 0; k < rawZBytes.get(j).length; k++) {
                                rawZbytes[k] = rawZBytes.get(j)[k].byteValue();
                            }
                            
                            zValues.put(times.get(j), vctmPayload.convertBytesToValue(rawZbytes));
                        }
                    }
                    else {
                        zValues.put(times.get(j), qstm00Mask[2]);
                    }

                    if (qstm00Mask[3] == 0) {
                        if (rawWBytes.size() > j) {
                            byte[] rawWbytes = new byte[rawWBytes.get(j).length];
        
                            for (int k = 0; k < rawWBytes.get(j).length; k++) {
                                rawWbytes[k] = rawWBytes.get(j)[k].byteValue();
                            }
                            
                            wValues.put(times.get(j), vctmPayload.convertBytesToValue(rawWbytes));
                        }
                    }
                    else {
                        wValues.put(times.get(j), qstm00Mask[3]);
                    }
                }
            }
            // When there's no QSTM02 Entry, but a QSTM00 Entry: Just add the floats...
            else if (qstm00Entry != null) {
                if (qstm00Entry.getMode() == 0) {
                    Axis axis = qstm00Entry.getAxis();
                    List<Float> qstm0Values = (qstm00Entry).getValues();

                    times.add((float)0);

                    int start = 0;

                    switch(axis) {
                        case Y: start = 1; break;
                        case Z: start = 2; break;
                        case W: start = 3; break;
                        default: break;
                    }

                    for (int k = 0; k < qstm0Values.size(); k++) {
                        switch(k+start) {
                            case 0: xValues.put((float)0, qstm0Values.get(k)); break;
                            case 1: yValues.put((float)0, qstm0Values.get(k)); break;
                            case 2: zValues.put((float)0, qstm0Values.get(k)); break;
                            case 3: wValues.put((float)0, qstm0Values.get(k)); break;
                        }
                    }
                }
                else {
                    System.out.println("Encountered QSTM00 with mode != 0");
                }
            }

            float xMin = Float.POSITIVE_INFINITY;
            float xMax = Float.NEGATIVE_INFINITY;
            float yMin = Float.POSITIVE_INFINITY;
            float yMax = Float.NEGATIVE_INFINITY;
            float zMin = Float.POSITIVE_INFINITY;
            float zMax = Float.NEGATIVE_INFINITY;
            float wMin = Float.POSITIVE_INFINITY;
            float wMax = Float.NEGATIVE_INFINITY;

            float[] finalTimes = new float[times.size()];
            float[][] finalValues3 = new float[times.size()][3];
            float[][] finalValues4 = new float[times.size()][4];

            float x, y, z, w;

            float timeMin = Float.POSITIVE_INFINITY;
            float timeMax = Float.NEGATIVE_INFINITY;

            for (int k = 0; k < times.size(); k++) {
                finalTimes[k] = times.get(k);
                timeMin = Math.min(timeMin, finalTimes[k]);
                timeMax = Math.max(timeMax, finalTimes[k]);

                x = xValues.get(times.get(k));
                y = yValues.get(times.get(k));
                z = zValues.get(times.get(k));

                xMin = Math.min(xMin, x); xMax = Math.max(xMax, x);
                yMin = Math.min(yMin, y); yMax = Math.max(yMax, y);
                zMin = Math.min(zMin, z); zMax = Math.max(zMax, z);

                if (tEntry.mode == TDTMMode.ROTATION) {
                    w = wValues.get(times.get(k));
                    wMin = Math.min(wMin, w); wMax = Math.max(wMax, w);

                    finalValues4[k][0] = x;
                    finalValues4[k][1] = y;
                    finalValues4[k][2] = z;
                    finalValues4[k][3] = w;
                }
                else { // TRANSLATION, SCALE or LOCAL_SCALE
                    finalValues3[k][0] = x;
                    finalValues3[k][1] = y;
                    finalValues3[k][2] = z;
                }
            }

            AnimationSampler as = new AnimationSampler();
            as.setInterpolation("LINEAR");

            int timeBuffer, timeBufferView, timeAccessor = 0;
            int valBuffer, valBufferView, valAccessor = 0;

            Number[] inMin = { timeMin };
            Number[] inMax = { timeMax };

            Number[] outMin = { xMin, yMin, zMin };
            Number[] outMax = { xMax, yMax, zMax };

            timeBuffer = arrayToBuffer(finalTimes, instance);
            timeBufferView = createBufferView(timeBuffer, 0, "timeBufferView_"+i, instance);
            timeAccessor = createAccessor(timeBufferView, GL_FLOAT, finalTimes.length, "SCALAR", "timeAccessor"+i, inMax, inMin, instance);

            if (tEntry.mode == TDTMMode.TRANSLATION) {
                act.setPath("translation");

                valBuffer = vectorToBuffer(finalValues3, instance);
                valBufferView = createBufferView(valBuffer, 0, "valueBufferView_"+i, instance);
                valAccessor = createAccessor(valBufferView, GL_FLOAT, finalValues3.length, "VEC3", "valueAccessor"+i, outMax, outMin, instance);
            }
            else if (tEntry.mode == TDTMMode.ROTATION) {
                act.setPath("rotation");

                outMin = new Number[4];
                outMin[0] = xMin; outMin[1] = yMin; outMin[2] = zMin; outMin[3] = wMin;
                outMax = new Number[4];
                outMax[0] = xMax; outMax[1] = yMax; outMax[2] = zMax; outMax[3] = wMax;

                valBuffer = vectorToBuffer(finalValues4, instance);
                valBufferView = createBufferView(valBuffer, 0, "valueBufferView_"+i, instance);
                valAccessor = createAccessor(valBufferView, GL_FLOAT, finalValues4.length, "VEC4", "valueAccessor"+i, outMax, outMin, instance);
            }
            else {
                act.setPath("scale");

                valBuffer = vectorToBuffer(finalValues3, instance);
                valBufferView = createBufferView(valBuffer, 0, "valueBufferView_"+i, instance);
                valAccessor = createAccessor(valBufferView, GL_FLOAT, finalValues3.length, "VEC3", "valueAccessor"+i, outMax, outMin, instance);
            }

            as.setInput(timeAccessor);
            as.setOutput(valAccessor);

            samplers.add(as);

            AnimationChannel ac = new AnimationChannel();
            ac.setTarget(act);
            ac.setSampler(samplers.size() - 1);
            
            channels.add(ac);
        }

        Animation anim = new Animation();

        anim.setName(name);
        anim.setChannels(channels);
        anim.setSamplers(samplers);

        instance.addAnimations(anim);
    }

    private int arrayToBuffer(float[] arr, GlTF instance) {
        ByteBuffer buffer = ByteBuffer.allocate(arr.length*4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        for (float x : arr)
            buffer.putFloat(x);
        
        buffer.flip();

        Buffer gltfBuffer = new Buffer();
        gltfBuffer.setUri(GLTFExporter.BUFFER_URI + Base64.getEncoder().encodeToString(buffer.array()));
        gltfBuffer.setByteLength(arr.length*4);
        instance.addBuffers(gltfBuffer);

        return instance.getBuffers().size() - 1;
    }

    private int vectorToBuffer(float[][] vec, GlTF instance) {
        ByteBuffer buffer = ByteBuffer.allocate(vec.length*vec[0].length*4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < vec.length; i++) {
            for (int j = 0; j < vec[0].length; j++) {
                buffer.putFloat(vec[i][j]);
            }
        }

        buffer.flip();

        Buffer gltfBuffer = new Buffer();
        gltfBuffer.setUri(GLTFExporter.BUFFER_URI + Base64.getEncoder().encodeToString(buffer.array()));
        gltfBuffer.setByteLength(vec.length*vec[0].length*4);
        instance.addBuffers(gltfBuffer);

        return instance.getBuffers().size() - 1;
    }

    private int createBufferView(int buffer, int target, String name, GlTF instance) {
        BufferView bufferView = new BufferView();
        bufferView.setBuffer(buffer);
        bufferView.setByteOffset(0);
        bufferView.setByteLength(instance.getBuffers().get(buffer).getByteLength());
        if (target != 0)
            bufferView.setTarget(target);
        bufferView.setName(name);
        instance.addBufferViews(bufferView);
        return instance.getBufferViews().size() - 1;
    }

    private int createAccessor(int bufferView, int componentType, int count, String type, String name, Number[] max, Number[] min, GlTF instance) {
        Accessor accessor = new Accessor();
        accessor.setBufferView(bufferView);
        accessor.setComponentType(componentType);
        accessor.setCount(count);
        accessor.setType(type);
        accessor.setName(name);
        accessor.setMax(max);
        accessor.setMin(min);
        instance.addAccessors(accessor);
        return instance.getAccessors().size() - 1;
    }
    
    @Override
    public List<ResPayload> getEntries() {
        List<ResPayload> entries = new ArrayList<>();
        entries.add(new NormalKCAP(this, qstm, true, false));
        entries.add(new NormalKCAP(this, vctm, true, false));
        
        return Collections.unmodifiableList(entries);
    }
    
    @Override
    public ResPayload get(int i) {
        switch (i) {
            case 0:
                return new NormalKCAP(this, qstm, true, false);
            case 1:
                return new NormalKCAP(this, vctm, true, false);
            default:
                throw new NoSuchElementException();
        }
    }
    
    @Override
    public int getEntryCount() {
        return 2;
    }
    
    @Override
    public KCAPType getKCAPType() {
        return KCAPType.TDTM;
    }
    
    @Override
    public int getSize() {
        int size = 0x40; // header
        size += tdtmEntry.size() * 0x08; // TDTM header entries
        size = Utils.align(size, 0x10); // padding
        size += getEntryCount() * 8; // pointer table
        
        size += get(0).getSize();
        size = Utils.align(size, 0x10); // padding
        size += get(1).getSize();
        
        return size;
    }
    
    @Override
    public void writeKCAP(Access dest, ResData dataStream) {
        long start = dest.getPosition();
        
        int headerSize = 0x40; // header
        headerSize += tdtmEntry.size() * 0x08; // TDTM header entries
        headerSize = Utils.align(headerSize, 0x10); // padding
        
        // write KCAP/TDTM header
        dest.writeInteger(getType().getMagicValue());
        dest.writeInteger(VERSION);
        dest.writeInteger(getSize());
        dest.writeInteger(getUnknown());
        
        dest.writeInteger(getEntryCount());
        dest.writeInteger(0); // type count
        dest.writeInteger(headerSize); // header size, always 0x30 for this type
        dest.writeInteger(0); // type payload start, after the pointer table or 0 if empty
        
        dest.writeInteger(getKCAPType().getMagicValue());
        dest.writeInteger(TDTM_VERSION);
        dest.writeInteger(tdtmEntry.size());
        dest.writeInteger(0); // padding
        
        dest.writeFloat(time1);
        dest.writeFloat(time2);
        dest.writeFloat(time3);
        dest.writeFloat(time4);
        
        // write TDTM entries
        tdtmEntry.forEach(a -> a.writeKCAP(dest));
        
        if (tdtmEntry.size() % 2 == 1)
            dest.writeLong(0); // padding
            
        // write pointer table
        int fileStart = (int) (dest.getPosition() - start + 0x10);
        
        ResPayload qstmKCAP = get(0);
        dest.writeInteger(fileStart);
        dest.writeInteger(qstmKCAP.getSize());
        fileStart += qstmKCAP.getSize();
        
        fileStart = Utils.align(fileStart, 0x10); // align content start
        
        ResPayload vctmKCAP = get(1);
        dest.writeInteger(fileStart);
        dest.writeInteger(vctmKCAP.getSize());
        
        // write entries
        try (ResData localDataStream = new ResData(dataStream)) {
            qstmKCAP.writeKCAP(dest, localDataStream);
            
            long aligned = Utils.align(dest.getPosition() - start, 0x10);
            dest.setPosition(start + aligned);
            
            vctmKCAP.writeKCAP(dest, localDataStream);
            dataStream.add(localDataStream);
        }
    }
    
    class TDTMEntry {
        private TDTMMode mode;
        
        /*
         * 0x10 joint    -> 3x translation | 4x rotation | 3x scale | 4x local scale
         * 0x20 texture  -> 2x translation | 1x rotation | 2x scale | none
         * 0x30 material -> 3x color (RGB) | 1x transparency
         */
        private byte unknown2;
        private short jointId;
        private int qstmId;
        
        public TDTMEntry(TDTMMode unknown1, byte unknown2, short jointId, int qstmId) {
            this.mode = unknown1;
            this.unknown2 = unknown2;
            this.jointId = jointId;
            this.qstmId = qstmId;
        }
        
        public TDTMEntry(Access source) {
            this.mode = TDTMMode.values()[source.readByte()];
            this.unknown2 = source.readByte();
            this.jointId = source.readShort();
            this.qstmId = source.readInteger();
        }
        
        public void writeKCAP(Access dest) {
            dest.writeByte((byte) mode.ordinal());
            dest.writeByte(unknown2);
            dest.writeShort(jointId);
            dest.writeInteger(qstmId);
        }
    }
    
    enum TDTMMode {
        TRANSLATION,
        ROTATION,
        SCALE,
        LOCAL_SCALE;
    }
}
