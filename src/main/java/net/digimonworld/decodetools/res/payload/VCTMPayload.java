package net.digimonworld.decodetools.res.payload;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.lwjgl.assimp.AIQuatKey;
import org.lwjgl.assimp.AIQuaternion;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.assimp.AIVectorKey;
import org.lwjgl.system.MemoryStack;

import net.digimonworld.decodetools.core.Access;
import net.digimonworld.decodetools.core.Utils;
import net.digimonworld.decodetools.res.ResData;
import net.digimonworld.decodetools.res.ResPayload;
import net.digimonworld.decodetools.res.kcap.AbstractKCAP;

/*
 * 4-byte       magic value
 * 4-byte       number of entries
 * 4-byte       offset of entries #2
 * 4-byte       offset of entries #1
 * 
 * 4-byte       unknown
 * 2-byte       size of entry #2
 * 2-byte       size of entry #1
 * float        unknown
 * float        unknown
 * 
 * <entries #1> 
 * <entries #2> 
 * 
 * TODO implement proper structure
 */
public class VCTMPayload extends ResPayload {
    private int numEntries;
    private int coordStart;
    private int entriesStart;
    
    private InterpolationMode interpolationMode; // iterpolation type (< 0xC)
    private byte componentCount;
    private ComponentType componentType;
    private TimeScale timeScale;
    private TimeType timeType;
    
    /*
     * Some loop modes?
     * Upper 4 bits for startTime < currentTime && currentTime <= endTime
     */
    private byte unk4;
    
    private short coordSize;
    private short entrySize;
    private float unknown4;
    private float unknown5;
    
    private VCTMEntry[] data1; // frame count
    private VCTMEntry[] data2; // frame data
    
    public VCTMPayload(Access source, int dataStart, AbstractKCAP parent, int size, String name) {
        this(source, dataStart, parent);
    }
    
    
    //Read
    public VCTMPayload(Access source, int dataStart, AbstractKCAP parent) {
        super(parent);
        long start = source.getPosition();
        
        source.readInteger(); // magic value
        numEntries = source.readInteger();
        coordStart = source.readInteger();
        entriesStart = source.readInteger();
        
        interpolationMode = InterpolationMode.values()[source.readByte()];
        
        int componentFlags = source.readByte();
        componentCount = (byte) (componentFlags >> 4);
        componentType = ComponentType.values()[componentFlags & 0xF];
        
        int timeFlags = source.readByte();
        timeScale = TimeScale.values()[timeFlags >> 4];
        timeType = TimeType.values()[timeFlags & 0xF];
        
        unk4 = source.readByte();
        
        coordSize = source.readShort();
        entrySize = source.readShort();
        unknown4 = source.readFloat();
        unknown5 = source.readFloat();
        
        data1 = new VCTMEntry[numEntries];
        data2 = new VCTMEntry[numEntries];        
        
        source.setPosition(start + entriesStart);
        for (int i = 0; i < numEntries; i++) {
        data1[i] = new VCTMEntry(source.readByteArray(entrySize));    
         }
                
        source.setPosition(start + coordStart);
        for (int i = 0; i < numEntries; i++)
            data2[i] = new VCTMEntry(source.readByteArray(coordSize));
        
        source.setPosition(Utils.align(source.getPosition(), 0x04));
    }

        
    // Position/Scale (3 Values)
    public VCTMPayload(AbstractKCAP parent, List<AIVectorKey> keys, float ticks, float scale) {
        super(parent);
        numEntries = keys.size();    
       
        float[] timeValues = new float[numEntries];
        for (int i = 0; i < numEntries; i++) {
            timeValues[i] = (float) keys.get(i).mTime();         
        }                   

        float firstTime = timeValues[0]; // Get the first keyframe time
        for (int i = 0; i < timeValues.length; i++) {
            timeValues[i] -= firstTime; // Normalize time so first keyframe is at 0.0
        }
        // timeScale = getTimeScale(timeValues);
        timeScale = pickTimeScale(timeValues, ticks);
    
        InitializeVCTM(3);

        data1 = new VCTMEntry[numEntries];
        data2 = new VCTMEntry[numEntries];                  
        
        for (int i = 0; i < numEntries; i++) {               
      	byte[] timeBytes = { (byte)Math.round((timeValues[i] / ticks) * 30.0f * 10.0f / timeScale.value) };
        data1[i] = new VCTMEntry(timeBytes);
       } 
        
        for (int i = 0; i < numEntries; i++) {

            short xVal =  toFloat16(keys.get(i).mValue().x());
            short yVal =  toFloat16(keys.get(i).mValue().y());
            short zVal =  toFloat16(keys.get(i).mValue().z());

            byte[] xBytes = { (byte) (xVal), (byte) (xVal >> 8) };
            byte[] yBytes = { (byte) (yVal), (byte) (yVal >> 8) };
            byte[] zBytes = { (byte) (zVal), (byte) (zVal >> 8) };

            byte[] allBytes = {xBytes[0], xBytes[1], yBytes[0], yBytes[1], zBytes[0], zBytes[1] };

            data2[i] = new VCTMEntry(allBytes);            
             }       
        	
        //Match first+last frame
        	//if (numEntries > 1) {
           // data2[numEntries - 1] = new VCTMEntry(data2[0].getData().clone());
        	//}
    		}

    
 // Rotation
    public VCTMPayload(AbstractKCAP parent, List<AIQuatKey> keys, float ticks) {
        super(parent);
        
        numEntries = keys.size();

        float[] timeValues = new float[numEntries];
        for (int i = 0; i < numEntries; i++) {
            timeValues[i] = (float) keys.get(i).mTime();
        }      
        
        float firstTime = timeValues[0]; // Get the first keyframe time
        for (int i = 0; i < timeValues.length; i++) {
            timeValues[i] -= firstTime; // Normalize time so first keyframe is at 0.0
        }
        // Use pickTimeScale to choose the most compact representation
        timeScale = pickTimeScale(timeValues, ticks);
         
        InitializeVCTM(4);

        data1 = new VCTMEntry[numEntries];
        data2 = new VCTMEntry[numEntries];
        
        // Process time values as before.
        for (int i = 0; i < numEntries; i++) {       	
            byte[] timeBytes = { (byte)Math.round((timeValues[i] / ticks) * 30.0f * 10.0f / timeScale.value) };
            data1[i] = new VCTMEntry(timeBytes);
        }                         

        // Build a temporary array of quaternions as floatsI have 
        float[][] quats = new float[numEntries][4];
        for (int i = 0; i < numEntries; i++) {       
            AIQuatKey key = keys.get(i);
            quats[i][0] = key.mValue().x();
            quats[i][1] = key.mValue().y();
            quats[i][2] = key.mValue().z();
            quats[i][3] = key.mValue().w();
        }
        
        // Ensure consistent quaternion orientation:
        // For each keyframe (from the second onward), check the dot product with the previous keyframe.
        // If negative, flip the quaternion.
        for (int i = 1; i < numEntries; i++) {
            float dot = quats[i-1][0] * quats[i][0] +
                        quats[i-1][1] * quats[i][1] +
                        quats[i-1][2] * quats[i][2] +
                        quats[i-1][3] * quats[i][3];
            if (dot < 0) {
                quats[i][0] = -quats[i][0];
                quats[i][1] = -quats[i][1];
                quats[i][2] = -quats[i][2];
                quats[i][3] = -quats[i][3];
            }
        }
        
        // Convert the adjusted quaternions to half-float (FLOAT16) and pack them into bytes.
        for (int i = 0; i < numEntries; i++) {       
            short xVal = toFloat16(quats[i][0]);
            short yVal = toFloat16(quats[i][1]);
            short zVal = toFloat16(quats[i][2]);
            short wVal = toFloat16(quats[i][3]);

            byte[] xBytes = { (byte) (xVal), (byte) (xVal >> 8) };
            byte[] yBytes = { (byte) (yVal), (byte) (yVal >> 8) };
            byte[] zBytes = { (byte) (zVal), (byte) (zVal >> 8) };
            byte[] wBytes = { (byte) (wVal), (byte) (wVal >> 8) };

            byte[] allBytes = {xBytes[0], xBytes[1], yBytes[0], yBytes[1],
                               zBytes[0], zBytes[1], wBytes[0], wBytes[1]};
     
            data2[i] = new VCTMEntry(allBytes);
        }
        //Match first+last frame
    	//if (numEntries > 1) {
        //data2[numEntries - 1] = new VCTMEntry(data2[0].getData().clone());
    	//}
    }


    public static short toFloat16(float value) {
        // Snap to 1.0 if within float16 rounding noise — 0.999512 is the adjacent
        // float16 below 1.0 and is never intentional in animation data.
        if (Math.abs(value - 1.0f) < 0.001f) value = 1.0f;
        if (Math.abs(value + 1.0f) < 0.001f) value = -1.0f;
        if (Math.abs(value) < 0.001f) value = 0.0f;

        int intBits = Float.floatToIntBits(value);
        int sign = (intBits >>> 16) & 0x8000; 
        int exp = ((intBits >>> 23) & 0xFF) - (127 - 15);
        int mantissa = intBits & 0x007FFFFF;
        
        if (exp <= 0) {
            // Underflow to zero
            return (short) sign;
        } else if (exp >= 31) {
            // Overflow to max float16 value
            return (short) (sign | 0x7C00);
        } else {
            return (short) (sign | (exp << 10) | (mantissa >>> 13));
        }
    }

    private void InitializeVCTM(int components) {
        coordSize = (short)(2*components); // byte length of coord values
        entrySize = 1; // byte length of time values
        entriesStart = 0x20;
        coordStart = Utils.align(entriesStart + numEntries * entrySize, 0x4);
        
        if (components == 3) {
            interpolationMode = InterpolationMode.LINEAR_3D;
        }
        else if (components == 4) {
            //interpolationMode = InterpolationMode.LINEAR_4D;
            interpolationMode = InterpolationMode.SPHERICAL_LINEAR;
        }
        else {
            interpolationMode = InterpolationMode.LINEAR_1D;
        }
        
        componentCount = (byte)(components & 0xff);
        componentType = ComponentType.FLOAT16;
        
        timeType = TimeType.UINT8;
        
        unk4 = 0;
        unknown4 = 0;
        unknown5 = 0;
    }

    private float[] cachedFrameList = null;  

    public float[] getFrameTimes() {
        if (cachedFrameList != null) {
            return cachedFrameList;
        }

         cachedFrameList = new float[numEntries];

        for (int i = 0; i < numEntries; i++) {
            byte[] data = data1[i].getData();
        cachedFrameList[i] = convertBytesToTime(data) * timeScale.value;
             
        }

        return cachedFrameList;
    }


    
    public float convertBytesToTime(byte[] data) {
        
        float finalVal;
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN); 
        
        switch(timeType) {
            case FLOAT: 
                finalVal = buffer.getFloat(); 
                break;
            case INT16:
                short int16Value = buffer.getShort();
                finalVal = (float) int16Value;
                break;
            case INT8:
                byte int8Value = buffer.get();
                finalVal = (float) int8Value;
                break;
            case UINT16:
                int uint16Value = Short.toUnsignedInt(buffer.getShort()); 
                finalVal = (float) uint16Value;
                break;
            case UINT8:
                int uint8Value = Byte.toUnsignedInt(buffer.get());
                finalVal = (float) uint8Value;
                break;
            default:
                finalVal = 0;
                break;
        }

        return finalVal;
    }

    public float convertBytesToValue(byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data array cannot be null or empty.");
        }

        float finalVal = 0;
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN); 

        switch (componentType) {
            case FLOAT32:
                if (data.length < 4) throw new IllegalArgumentException("Insufficient bytes for FLOAT32.");
                finalVal = buffer.getFloat();
                break;
                
            case FLOAT16:
                if (data.length < 2) throw new IllegalArgumentException("Insufficient bytes for FLOAT16.");
                finalVal = Float.float16ToFloat(buffer.getShort()); 
                break;
                
            case INT16:
                if (data.length < 2) throw new IllegalArgumentException("Insufficient bytes for INT16.");
                finalVal = (float) buffer.getShort(); 
                break;
                
            case INT8:
                if (data.length < 1) throw new IllegalArgumentException("Insufficient bytes for INT8.");
                finalVal = (float) buffer.get(); 
                break;
                
            case UINT16:
                if (data.length < 2) throw new IllegalArgumentException("Insufficient bytes for UINT16.");
                finalVal = (float) Short.toUnsignedInt(buffer.getShort());
                break;
                
            case UINT8:
                if (data.length < 1) throw new IllegalArgumentException("Insufficient bytes for UINT8.");
                finalVal = (float) Byte.toUnsignedInt(buffer.get()); // Correct unsigned conversion
                break;
                
            default:
                finalVal = 0;
                break;
        }

        return finalVal;
    }

    public Byte[][] getRawFrameData(int entryIndex) {
        int compCount = getComponentCount();

        int dataSize = coordSize / compCount;

        byte[] data = data2[entryIndex].getData();

        Byte[][] splitData = new Byte[compCount][dataSize];
        int k = 0;

        for (int x = 0; x < compCount; x++) {
            for (int y = 0; y < dataSize; y++) {
                splitData[x][y] = data[k];
                k++;
            }
        }

        return splitData;
    }

    public int getComponentCount() {
        byte[] compCountData = {0x00, 0x00, 0x00, componentCount};
        int compCount = ByteBuffer.wrap(compCountData).getInt();
        return compCount;
    }

    public int getNumEntries() {
        return numEntries;
    }

    public int GetValueBytes() {
        if (componentType == ComponentType.FLOAT32) {
            return 4;
        }
        else if (componentType == ComponentType.INT8 || componentType == ComponentType.UINT8) {
            return 1;
        }
        else {
            return 2;
        }
    }
    
    @SuppressWarnings("exports")
	public ComponentType getComponentType() {
        return componentType;
    }
    
    public InterpolationMode getInterpolationMode() {
        return interpolationMode;
    }

    public TimeScale getTimeScale() {
        return timeScale;
    }
    
    public byte getUnk4() {
        return unk4;
    }
    
    class VCTMEntry {
        private final byte[] data;
        
        public VCTMEntry(byte[] data) {
            this.data = data;
        }
        
        public byte[] getData() {
            return data;
        }
    }
    
    // -------------------------------------------------------------------------
    // Mutation helpers
    // -------------------------------------------------------------------------

    /** Recomputes coordStart after numEntries / entrySize has changed. */
    private void recalculateOffsets() {
        entriesStart = 0x20;
        coordStart = Utils.align(entriesStart + numEntries * entrySize, 0x04);
    }

    /**
     * Converts a <em>display</em> time value (i.e. already multiplied by
     * {@code timeScale.value}) back to the raw byte representation used in
     * {@code data1}.
     */
    private byte[] convertTimeToBytes(float displayTime) {
        float raw = displayTime / timeScale.value;
        ByteBuffer buf;
        switch (timeType) {
            case UINT8:
                return new byte[]{ (byte) Math.round(raw) };
            case INT8:
                return new byte[]{ (byte) Math.round(raw) };
            case UINT16: {
                int v = Math.round(raw);
                return new byte[]{ (byte)(v & 0xFF), (byte)((v >> 8) & 0xFF) };
            }
            case INT16: {
                short v = (short) Math.round(raw);
                return new byte[]{ (byte)(v & 0xFF), (byte)((v >> 8) & 0xFF) };
            }
            case FLOAT:
                buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                buf.putFloat(raw);
                return buf.array();
            default:
                return new byte[]{ 0 };
        }
    }

    /**
     * Converts an array of component float values back to the packed byte
     * representation used in {@code data2}, honouring {@code componentType}.
     */
    private byte[] convertValuesToBytes(float[] values) {
        int bpc = GetValueBytes();
        byte[] result = new byte[values.length * bpc];
        for (int i = 0; i < values.length; i++) {
            byte[] chunk;
            switch (componentType) {
                case FLOAT16: {
                    short f16 = toFloat16(values[i]);
                    chunk = new byte[]{ (byte)(f16 & 0xFF), (byte)((f16 >> 8) & 0xFF) };
                    break;
                }
                case FLOAT32: {
                    ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                    buf.putFloat(values[i]);
                    chunk = buf.array();
                    break;
                }
                case UINT8:
                    chunk = new byte[]{ (byte) Math.round(values[i]) };
                    break;
                case INT8:
                    chunk = new byte[]{ (byte) Math.round(values[i]) };
                    break;
                case UINT16: {
                    int v = Math.round(values[i]);
                    chunk = new byte[]{ (byte)(v & 0xFF), (byte)((v >> 8) & 0xFF) };
                    break;
                }
                case INT16: {
                    short v = (short) Math.round(values[i]);
                    chunk = new byte[]{ (byte)(v & 0xFF), (byte)((v >> 8) & 0xFF) };
                    break;
                }
                default:
                    chunk = new byte[]{ 0 };
                    break;
            }
            System.arraycopy(chunk, 0, result, i * bpc, bpc);
        }
        return result;
    }

    /**
     * Appends a new keyframe at the end of this VCTM.
     *
     * @param displayTime      the time value as shown in the UI (= raw * timeScale)
     * @param componentValues  one float per component (X [Y [Z [W]]])
     */
    public void addEntry(float displayTime, float[] componentValues) {
        if (componentValues.length != getComponentCount())
            throw new IllegalArgumentException("Expected " + getComponentCount() + " components, got " + componentValues.length);

        data1 = Arrays.copyOf(data1, numEntries + 1);
        data2 = Arrays.copyOf(data2, numEntries + 1);
        data1[numEntries] = new VCTMEntry(convertTimeToBytes(displayTime));
        data2[numEntries] = new VCTMEntry(convertValuesToBytes(componentValues));
        numEntries++;
        cachedFrameList = null;
        recalculateOffsets();
    }

    /**
     * Removes the keyframe at {@code index}.
     */
    public void removeEntry(int index) {
        if (index < 0 || index >= numEntries)
            throw new IndexOutOfBoundsException("index " + index + " out of range [0, " + numEntries + ")");

        VCTMEntry[] nd1 = new VCTMEntry[numEntries - 1];
        VCTMEntry[] nd2 = new VCTMEntry[numEntries - 1];
        System.arraycopy(data1, 0, nd1, 0, index);
        System.arraycopy(data1, index + 1, nd1, index, numEntries - index - 1);
        System.arraycopy(data2, 0, nd2, 0, index);
        System.arraycopy(data2, index + 1, nd2, index, numEntries - index - 1);
        data1 = nd1;
        data2 = nd2;
        numEntries--;
        cachedFrameList = null;
        recalculateOffsets();
    }

    /**
     * Overwrites the keyframe at {@code index} with new time and component values.
     *
     * @param index            row to update
     * @param displayTime      new display-space time
     * @param componentValues  new component values
     */
    public void updateEntry(int index, float displayTime, float[] componentValues) {
        if (index < 0 || index >= numEntries)
            throw new IndexOutOfBoundsException("index " + index + " out of range [0, " + numEntries + ")");
        if (componentValues.length != getComponentCount())
            throw new IllegalArgumentException("Expected " + getComponentCount() + " components, got " + componentValues.length);

        data1[index] = new VCTMEntry(convertTimeToBytes(displayTime));
        data2[index] = new VCTMEntry(convertValuesToBytes(componentValues));
        cachedFrameList = null;
    }

    // -------------------------------------------------------------------------

    @Override
    public int getSize() {
        return 0x20 + Utils.align(data1.length * entrySize, 0x04) + Utils.align(data2.length * coordSize, 0x04);
    }
    
    @Override
    public Payload getType() {
        return Payload.VCTM;
    }
    
    @Override
    public void writeKCAP(Access dest, ResData dataStream) {
        dest.writeInteger(getType().getMagicValue());
        dest.writeInteger(numEntries);
        dest.writeInteger(coordStart);
        dest.writeInteger(entriesStart);
        
        dest.writeByte((byte) interpolationMode.ordinal());
        dest.writeByte((byte) (componentCount << 4 | componentType.ordinal()));
        dest.writeByte((byte) (timeScale.ordinal() << 4 | timeType.ordinal()));
        dest.writeByte(unk4);
        
        dest.writeShort(coordSize);
        dest.writeShort(entrySize);
        dest.writeFloat(unknown4);
        dest.writeFloat(unknown5);
        
        for (VCTMEntry entry : data1)
            for (byte b : entry.getData())
                dest.writeByte(b);
            
        dest.setPosition(Utils.align(dest.getPosition(), 0x4));
        
        for (VCTMEntry entry : data2)
            for (byte b : entry.getData())
                dest.writeByte(b);
            
        long diff = Utils.align(dest.getPosition(), 0x04) - dest.getPosition();
        dest.writeByteArray(new byte[(int) diff]);
    }
    
    public enum InterpolationMode {
        UNK0,
        UNK1,
        UNK2,
        UNK3,
        LINEAR_1D,
        LINEAR_2D,
        LINEAR_3D,
        LINEAR_4D,
        SPHERICAL_LINEAR, // slerp
        NONE,
        UNKA,
        UNKB;
    }
    
    public enum TimeScale {
        EVERY_1_FRAMES(1f),
        EVERY_5_FRAMES(5f),
        EVERY_6_FRAMES(6f),
        EVERY_10_FRAMES(10f),
        EVERY_12_FRAMES(12f),
        EVERY_15_FRAMES(15f),
        EVERY_20_FRAMES(20f),
        EVERY_30_FRAMES(30f),
        
        FPS_1(1 / 1f),
        FPS_5(1 / 5f),
        FPS_6(1 / 6f),
        FPS_10(1 / 10f),
        FPS_12(1 / 12f),
        FPS_15(1 / 15f),
        FPS_20(1 / 20f),
        FPS_30(1 / 30f);
        
        final float value;
        
        private TimeScale(float value) {
            this.value = value;
        }

        public float getValue() {
            return value;
        }
    }
    
    
    /**
     * Picks the largest TimeScale whose value evenly divides the minimum
     * interval between consecutive keyframes (expressed in frames at 30fps).
     * This produces the most compact raw byte values while staying exact.
     *
     * @param timeValues  keyframe times in DCC-tool ticks
     * @param ticks       ticks per second of the DCC tool
     */
    public static TimeScale pickTimeScale(float[] timeValues, float ticks) {
        if (timeValues == null || timeValues.length < 2)
            return TimeScale.EVERY_1_FRAMES;

        // Convert to frame numbers at 30fps
        int[] frames = new int[timeValues.length];
        for (int i = 0; i < timeValues.length; i++)
            frames[i] = Math.round((timeValues[i] / ticks) * 30.0f);

        // Find the minimum positive interval between consecutive frames
        int minInterval = Integer.MAX_VALUE;
        for (int i = 1; i < frames.length; i++) {
            int interval = frames[i] - frames[i - 1];
            if (interval > 0)
                minInterval = Math.min(minInterval, interval);
        }
        if (minInterval == Integer.MAX_VALUE)
            return TimeScale.EVERY_1_FRAMES;

        // Pick the largest TimeScale value where (minInterval * 10) is divisible by
        // the scale — ensuring rawByte = frame*10/scale is always a whole number.
        TimeScale[] ordered = {
            TimeScale.EVERY_30_FRAMES,
            TimeScale.EVERY_20_FRAMES,
            TimeScale.EVERY_15_FRAMES,
            TimeScale.EVERY_12_FRAMES,
            TimeScale.EVERY_10_FRAMES,
            TimeScale.EVERY_6_FRAMES,
            TimeScale.EVERY_5_FRAMES,
            TimeScale.EVERY_1_FRAMES
        };
        for (TimeScale ts : ordered) {
            if ((minInterval * 10) % Math.round(ts.value) == 0)
                return ts;
        }
        return TimeScale.EVERY_1_FRAMES;
    }

    // Legacy overload kept for compatibility
    public TimeScale getTimeScale(float[] timeValues) {
        throw new UnsupportedOperationException("Use pickTimeScale(timeValues, ticks) instead");
    }





    enum TimeType {
        FLOAT,
        NONE,
        INT16,
        INT8,
        UINT16,
        UINT8;
    }
    
    public enum ComponentType {
        FLOAT32,
        FLOAT16,
        INT16,
        INT8,
        UINT16,
        UINT8;
    }
}