package net.digimonworld.decodetools.res;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JFileChooser;
import javax.swing.UIManager;

import net.digimonworld.decodetools.core.Access;
import net.digimonworld.decodetools.core.FileAccess;
import net.digimonworld.decodetools.core.StreamAccess;
import net.digimonworld.decodetools.core.Utils;
import net.digimonworld.decodetools.res.kcap.AbstractKCAP;
import net.digimonworld.decodetools.res.payload.GenericPayload;

public class DecodeScript {
        
	static final Map<Integer, String> nativeNames = new HashMap<>();
	static {
	    nativeNames.put(0x1F48F8, "Script::hasMedalSeen");
	    nativeNames.put(0x1F5378, "Script::getKCAPEntry");
	    nativeNames.put(0x1F50B4, "Script::isTriggerSet");
	    nativeNames.put(0x309D14, "Script::getCurrentBagCapacity");
	    nativeNames.put(0x309CA8, "Script::getBagItem");
	    nativeNames.put(0x1F7B8C, "Script::getFishBait");
	    nativeNames.put(0x2c88a0,"Script::playMusic");
	    nativeNames.put(0x213318, "Script::degreeToRadians");
	    nativeNames.put(0x308CA0, "Script::getItemCountOfType");
	    nativeNames.put(0x2C9B14, "Script::countStorageItemOfType");
	    nativeNames.put(0x1f54b4, "Script::getButtonMask");
	    nativeNames.put(0x1F84B4, "Script::addOrTakeItems");
	    nativeNames.put(0x1f4970, "Script::getMapKCAPEntry");
	    nativeNames.put(0x310d90, "Script::addTiredness");
	    nativeNames.put(0x1f3fb0, "Script::healSicknessOrInjury");
	    nativeNames.put(0x1f585c, "Script::SetProgressValue");
	    nativeNames.put(0x1f5788, "Script::setMailCleared");
	}

	static final class NativeSignature {
	    final String name;
	    final Map<Integer, Map<Integer, String>> argEnums;

	    NativeSignature(String name) {
	        this.name = name;
	        this.argEnums = new HashMap<>();
	    }

	    NativeSignature enumArg(int index, Map<Integer, String> values) {
	        argEnums.put(index, values);
	        return this;
	        
	        
	    }
	}

	static BasicBlock resolveBlockAtOrAfter(
	        Map<Integer, BasicBlock> blocks,
	        int rawTarget
	) {
	    return blocks.get(rawTarget);
	}

	static final Map<Integer, NativeSignature> nativeSigs = new HashMap<>();

	static {
	    nativeSigs.put(0x2c88a0,
	        new NativeSignature("Script::playMusic")
	            .enumArg(0, Map.of(
	                0x1000002, "Victory",
	                0x1000003, "bossBattle"
	            ))

	    );
	}

	public static final class DecodedInstr {
	    public final int offset;
	    public final int size;
	    public final Instruction opcode;
	    public final ParamType paramType;
	    public final Object operand;

	    public DecodedInstr(int offset,
	                         int size,
	                         Instruction opcode,
	                         ParamType paramType,
	                         Object operand) {
	        this.offset = offset;
	        this.size = size;
	        this.opcode = opcode;
	        this.paramType = paramType;
	        this.operand = operand;
	    }
	}

    private enum Instruction {
        END("END"),
        MOV_BYTE("MOV", ParamType.VAL8),
        MOV_SHORT("MOV", ParamType.VAL16),
        MOV_WORD("MOV", ParamType.VAL32),
        MOV_LONG("MOV", ParamType.VAL64),
        MVN_UBYTE("MOV", ParamType.VAL8),
        MVN_USHORT("MOV", ParamType.VAL16),
        MOV_WORD2("MOV", ParamType.VAL32),
        FPADD_WORD("FPADD", ParamType.VAL32),
        FPADD_BYTE("FPADD", ParamType.VAL8),
        LD_UBYTE("LD.UB", ParamType.VAL32),
        LD_USHORT("LD.UH", ParamType.VAL32),
        LD_WORD("LD.W", ParamType.VAL32),
        LD_LONG("LD.L", ParamType.VAL32),
        LD_BYTE("LD.B", ParamType.VAL32),
        LD_SHORT("LD.H", ParamType.VAL32),
        FPLD32_UBYTE("FPLD.UB", ParamType.VAL32),
        FPLD32_USHORT("FPLD.UH", ParamType.VAL32),
        FPLD32_WORD("FPLD.W", ParamType.VAL32),
        FPLD32_LONG("FPLD.L", ParamType.VAL32),
        FPLD32_BYTE("FPLD.B", ParamType.VAL32),
        FPLD32_SHORT("FPLD.H", ParamType.VAL32),
        FPLD8_UBYTE("FPLD.UB", ParamType.VAL8),
        FPLD8_USHORT("FPLD.UH", ParamType.VAL8),
        FPLD8_WORD("FPLD.W", ParamType.VAL8),
        FPLD8_LONG("FPLD.L", ParamType.VAL8),
        FPLD8_BYTE("FPLD.B", ParamType.VAL8),
        FPLD8_SHORT("FPLD.H", ParamType.VAL8),
        CVT_UB_W("CVT.UB.W"),
        CVT_UH_W("CVT.UH.W"),
        CVT_UW_W("CVT.UW.W"),
        CVT_L_W("CVT.L.W"),
        CVT_B_W("CVT.B.W"),
        CVT_H_W("CVT.H.W"),
        STR_UBYTE("STR.UB"),
        STR_USHORT("STR.UH"),
        STR_UINT("STR.UW"),
        STR_LONG("STR.L"),
        MEMCOPY32("MEMCOPY", ParamType.VAL32),
        MEMCOPY8("MEMCOPY", ParamType.VAL8),
        FILLZ_1("FILLZ 1"),
        FILLZ_2("FILLZ 2"),
        FILLZ_4("FILLZ 4"),
        FILLZ_8("FILLZ 8"),
        FILLZ_V32("FILLZ", ParamType.VAL32),
        FILLZ_V8("FILLZ", ParamType.VAL8),
        CVT_F_W("CVT.F.W"),
        CVT_D_W("CVT.D.W"),
        CVT_F_UW("CVT.F.UW"),
        CVT_D_UW("CVT.D.UW"),
        CVT_W_F("CVT.W.F"),
        CVT_D_F("CVT.D.F"),
        CVT_W_D("CVT.W.D"),
        CVT_F_D("CVT.F.D"),
        CVT_BOOL_W("CVT.BOOL.W"),
        CVT_BOOL_D("CVT.BOOL.D"),
        ADD_W("ADD.W"),
        ADD_F("ADD.F"),
        ADD_D("ADD.D"),
        SUB_W("SUB.W"),
        SUB_F("SUB.F"),
        SUB_D("SUB.D"),
        MUL_W("MUL.W"),
        MUL_W2("MUL.W"),
        MUL_F("MUL.F"),
        MUL_D("MUL.D"),
        DIV_UW("DIV.UW"),
        DIV_W("DIV.W"),
        DIV_F("DIV.F"),
        DIV_D("DIV.D"),
        UMOD("UMOD"),
        MOD("MOD"),
        LSR("LSR"),
        ASR("ASR"),
        LSL("LSL"),
        AND("AND"),
        XOR("XOR"),
        OR("OR"),
        NEG_INT("NEG.W"),
        NEG_FLOAT("NEG.F"),
        NEG_DOUBLE("NEG.D"),
        NOT("NOT"),
        UNK53("UNK53"),
        PADD_UBYTE("PADD.UB", ParamType.VAL8),
        PADD_USHORT("PADD.UH", ParamType.VAL8),
        PADD_UINT("PADD.UW", ParamType.VAL8),
        PADD_FLOAT("PADD.F", ParamType.VAL8),
        PADD_DOUBLE("PADD.D", ParamType.VAL8),
        PADD_BYTE("PADD.B", ParamType.VAL8),
        PADD_SHORT("PADD.H", ParamType.VAL8),
        PADD("PADD", ParamType.VAL32),
        LPADD_UBYTE("LPADD.UB", ParamType.VAL8),
        LPADD_USHORT("LPADD.UH", ParamType.VAL8),
        LPADD_UINT("LPADD.UW", ParamType.VAL8),
        LPADD_FLOAT("LPADD.F", ParamType.VAL8),
        LPADD_DOUBLE("LPADD.D", ParamType.VAL8),
        LPADD_BYTE("LPADD.B", ParamType.VAL8),
        LPADD_SHORT("LPADD.H", ParamType.VAL8),
        LPADD("LPADD", ParamType.VAL32),
        EQ_WORD("EQ.W"),
        EQ_FLOAT("EQ.F"),
        EQ_DOUBLE("EQ.D"),
        NE_WORD("NE.W"),
        NE_FLOAT("NE.F"),
        NE_DOUBLE("NE.D"),
        GT_WORD("GT.W"),
        GT_FLOAT("GT.F"),
        GT_DOUBLE("GT.D"),
        GTE_INT("GTE.W"),
        GTE_FLOAT("GTE.F"),
        GTE_DOUBLE("GTE.D"),
        GT_UINT("GT.UW"),
        GTE_UINT("GTE.UW"),
        LT_WORD("LT.W"),
        LT_FLOAT("LT.F"),
        LT_DOUBLE("LT.D"),
        LTE_INT("LTE.W"),
        LTE_FLOAT("LTE.F"),
        LTE_DOUBLE("LTE.D"),
        LT_UINT("LT.UW"),
        LTE_UINT("LTE.UW"),
        EQZ("EQZ"),
        J32("J", ParamType.VAL32),
        JZ32("JZ", ParamType.VAL32),
        JNZ32("JNZ", ParamType.VAL32),
        J8("J", ParamType.VAL8),
        JZ8("JZ", ParamType.VAL8),
        JNZ8("JNZ", ParamType.VAL8),
        CALL("CALL", ParamType.VAL32),
        SWITCH("SWITCH", ParamType.VAL32),
        RET("RET"),
        NATIVECALL("NATIVECALL", ParamType.VAL32),
        ENDNZ("ENDNZ"),
        ALLOC32("ALLOC", ParamType.VAL32),
        ALLOC8("ALLOC", ParamType.VAL8),
        PUSHW("PUSHW"),
        PUSHL("PUSHL"),
        PUSH32("PUSH", ParamType.VAL32),
        PUSH8("PUSH", ParamType.VAL8),
        POPW("POPW"),
        POPL("POPL"),
        POP32("POP", ParamType.VAL32),
        POP8("POP", ParamType.VAL8),
        ADVANCE("ADVANCE", ParamType.VAL32);
        
        private final String code;
        private final ParamType paramType;
        
        private Instruction(String code) {
            this(code, ParamType.NONE);
        }
        
        private Instruction(String code, ParamType paramType) {
            this.code = code;
            this.paramType = paramType;
        }
    }
    
    static boolean isTerminator(Instruction instr) {
        switch (instr) {
            case J32:
            case J8:
            case JZ32:
            case JZ8:
            case JNZ32:
            case JNZ8:
            case RET:
            case END:
            case ENDNZ:
                return true;
            default:
                return false;
        }
    }

    static abstract class IRValue {}

    static final class IRTemp extends IRValue {
        public final int id;
        IRTemp(int id) { this.id = id; }
        public String toString() { return "t" + id; }
    }

    static final class IRConst extends IRValue {
        public final Object value;

        IRConst(Object value) {
            this.value = value;
        }

        @Override
        public String toString() {
            if (value instanceof Integer i) {
                String floatStr = tryFormatFloat(i);
                if (floatStr != null)
                    return floatStr;

                if (i < 0)
                    return String.format("0x%08X", i);

                return Integer.toString(i);
            }
            return value.toString();
        }

        private static String tryFormatFloat(int bits) {
            float f = Float.intBitsToFloat(bits);

            if (!Float.isFinite(f)) return null;
            if (f == 0.0f) return null; // 0 is ambiguous, keep as int

            float abs = Math.abs(f);
            if (abs < 1e-3f || abs > 1e7f) return null;

            // Heuristic: if the int interpretation is "weird-big" (> ~16M)
            // but the float interpretation is sane, prefer float.
            int absInt = Math.abs(bits);
            if (absInt > 0x01000000) {
                return trimFloat(f) + "f";
            }

            // Small ints: only treat as float if fractional or known constant
            if (f != (int) f || isCommonFloat(bits)) {
                return trimFloat(f) + "f";
            }

            return null;
        }

        private static boolean isCommonFloat(int bits) {
            return bits == 0x3F800000 || // 1.0
                   bits == 0x3F000000 || // 0.5
                   bits == 0x40000000 || // 2.0
                   bits == 0x40400000;   // 3.0
        }

        private static String trimFloat(float f) {
            String s = Float.toString(f);
            return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
        }
    }

    static int nextTempId = 0;
    static IRTemp newTemp() {
        return new IRTemp(nextTempId++);
    }

    static abstract class IRInstr {}

    static final class IRAssign extends IRInstr {
        public final IRValue dst;   // ✅ allow IRLocal or IRTemp
        public final String op;
        public final IRValue src1;
        public final IRValue src2; // nullable

        IRAssign(IRValue dst, String op, IRValue src1, IRValue src2) {
            this.dst = dst;
            this.op = op;
            this.src1 = src1;
            this.src2 = src2;
        }
    
    }

    static final class IRJump extends IRInstr {
        public final BasicBlock target;
        IRJump(BasicBlock target) { this.target = target; }
    }

    static final class IRCJump extends IRInstr {
        public final IRValue cond;
        public final BasicBlock trueTarget;
        public final BasicBlock falseTarget;

        IRCJump(IRValue cond, BasicBlock t, BasicBlock f) {
            this.cond = cond;
            this.trueTarget = t;
            this.falseTarget = f;
        }
    }

    static final class IRReturn extends IRInstr {}

    private enum ParamType {
        NONE,
        VAL8,
        VAL16,
        VAL32,
        VAL64;
    }
    
    public static List<DecodedInstr> disassemble(Access access)
 {
        // header 0x18
        // code
        // data?
        // string table?
    	List<DecodedInstr> instructions = new ArrayList<>();
        int magicValue = access.readInteger();
        int unk1 = access.readInteger();
        int unk2 = access.readInteger();
        int totalSize = access.readInteger(); // total size
        int codeSize = access.readInteger(); // code+data size?
        int stringSize = access.readInteger(); // string table size
        
        /*- 0x2CA3D8
        // after string table:
        // int - number of internal pointer
        //   int per entry, pointing to an internal address (starting from 0x00)
        // int - number of string pointer
        //   int per entry, pointing to a string address (starting from code+data+0x18)
        // int - number of native pointer | mapped at 0x4EB89C
        //   int function ID
        //   int data offset
        // int - number of lib.e/external script pointer -> 0x2F80B0
        //   int function ID 
        //   int data offset
        // int - number of exported functions | mapped at 0x4EB8A0
        //   int function ID
        //   int function Ptr
        // int - number of unknown
        */
        
        byte[] code = access.readByteArray(codeSize);
        byte[] string = access.readByteArray(stringSize);
        byte[] rest = access.readByteArray(totalSize - codeSize - stringSize - 0x18);
        
        StreamAccess codeAccess = new StreamAccess(code);
        StreamAccess stringAccess = new StreamAccess(string);
        StreamAccess restAccess = new StreamAccess(rest);
        
        Map<Integer, String> labelMap = new HashMap<>();
        
        int codePtr = restAccess.readInteger();
        for(int i = 0; i < codePtr; i++) {
            int offset = restAccess.readInteger();
            int initVal = codeAccess.readInteger(offset - 0x18L);
            codeAccess.writeInteger(initVal - 0x18, offset - 0x18L);
            
            labelMap.put(offset - 0x18, String.format("local_0x%X", initVal - 0x18L));
        }
        
        int stringPtr = restAccess.readInteger();
        for(int i = 0; i < stringPtr; i++) {
            int offset = restAccess.readInteger();
            int initVal = codeAccess.readInteger(offset - 0x18L);
            codeAccess.writeInteger(initVal + codeSize, offset - 0x18L);

            labelMap.put(offset - 0x18, String.format("string_0x%X", initVal + codeSize));
        }

        int nativePtr = restAccess.readInteger();
        for(int i = 0; i < nativePtr; i++) {
            int functionId = restAccess.readInteger();
            int offset = restAccess.readInteger();
            
            int functionAddress = map.get(functionId);

            int initVal = codeAccess.readInteger(offset - 0x18L);
            if(initVal != 0)
                System.out.println("Non-Zero Nativecall detected");
            
            codeAccess.writeInteger(functionAddress, offset - 0x18L);

            labelMap.put(offset - 0x18, String.format("native_0x%X", functionAddress));
        }

        int externalPtr = restAccess.readInteger();
        for(int i = 0; i < externalPtr; i++) {
            int functionId = restAccess.readInteger();
            int offset = restAccess.readInteger();
            
            int functionAddress = map.getOrDefault(functionId, -1);

            int initVal = codeAccess.readInteger(offset - 0x18L);
            if(initVal != 0)
                System.out.println("Non-Zero external call detected");
            
            codeAccess.writeInteger(functionAddress, offset - 0x18L);

            labelMap.put(offset - 0x18, String.format("external_%d", functionId));
        }
        int exportsPtr = restAccess.readInteger();
        for(int i = 0; i < exportsPtr; i++) {
            int functionId = restAccess.readInteger();
            int offset = restAccess.readInteger();
        }
        int unkPtr = restAccess.readInteger();
        for(int i = 0; i < unkPtr; i++) {
            int functionId = restAccess.readInteger();
            int offset = restAccess.readInteger();
        }
        
        if(restAccess.getPosition() != restAccess.getSize())
            System.out.println("Not at end");

        Instruction last = Instruction.RET;
      
        while (codeAccess.getPosition() < codeAccess.getSize()) {

            int instrOffset = (int) codeAccess.getPosition();
            Instruction instr =
                Instruction.values()[Byte.toUnsignedInt(codeAccess.readByte())];

            if (instr == Instruction.END && last == Instruction.RET)
                break;

            last = instr;

            int size = 1; // opcode byte
            Object operand = null;

            switch (instr.paramType) {
            case NONE:
                break;
            case VAL8:
                operand = codeAccess.readByte();
                size += 1;
                break;
            case VAL16:
                operand = codeAccess.readShort();
                size += 2;
                break;
            case VAL32:
                operand = codeAccess.readInteger();
                size += 4;
                break;
            case VAL64:
                operand = codeAccess.readLong();
                size += 8;
                break;
            }

            instructions.add(
            		 new DecodedInstr(instrOffset, size, instr, instr.paramType, operand)
            );
        }

        System.out.println();
        codeAccess.setPosition(Utils.align(codeAccess.getPosition(), 4));
        while (codeAccess.getPosition() < codeAccess.getSize()) {
            System.out.println(String.format("0x%06X 0x%08X", codeAccess.getPosition(), codeAccess.readInteger()));
        }

        System.out.println();
        
        while (stringAccess.getPosition() < stringAccess.getSize()) {
            int val = stringAccess.readInteger();
            
            System.out.println(String.format("0x%06X 0x%08X %4s", stringAccess.getPosition() + codeSize - 4, val, toASCIIString(val)));
        }
        return instructions;
    }
    
    static Set<Integer> findBlockStarts(List<DecodedInstr> instructions) {
        Set<Integer> blockStarts = new HashSet<>();

        // entry point
        blockStarts.add(instructions.get(0).offset);

        for (int i = 0; i < instructions.size(); i++) {
            DecodedInstr di = instructions.get(i);

            // ---- jump targets (ONLY real jumps with operands) ----
            switch (di.opcode) {
                case J32:
                case J8:
                case JZ8:
                case JZ32:
                case JNZ8:
                case JNZ32:
                	int raw = resolveJumpTarget(di);
                	int canon = canonicalTargetOffset(instructions, raw);

                	if (canon != -1) {
                	    blockStarts.add(canon);
                	} else {
                	    System.out.printf(
                	        "Ignoring non-instruction jump target 0x%X from 0x%X (%s)%n",
                	        raw,
                	        di.offset,
                	        di.opcode.name()
                	    );
                	}
                    // conditional jumps also have fallthrough
                    if (di.opcode != Instruction.J32 && di.opcode != Instruction.J8) {
                        blockStarts.add(di.offset + di.size);
                    }
                    break;

                default:
                    break;
            }

               if (isTerminator(di.opcode) && i + 1 < instructions.size()) {
                blockStarts.add(instructions.get(i + 1).offset);
            }

        }

        return blockStarts;
    }


    static boolean isComparison(Instruction i) {
        switch (i) {
            case EQ_WORD:
            case EQ_FLOAT:
            case EQ_DOUBLE:
            case EQZ:

            case NE_WORD:
            case NE_FLOAT:
            case NE_DOUBLE:

            case GT_WORD:
            case GT_FLOAT:
            case GT_DOUBLE:
            case GTE_INT:
            case GTE_FLOAT:
            case GTE_DOUBLE:
            case GT_UINT:
            case GTE_UINT:

            case LT_WORD:
            case LT_FLOAT:
            case LT_DOUBLE:
            case LTE_INT:
            case LTE_FLOAT:
            case LTE_DOUBLE:
            case LT_UINT:
            case LTE_UINT:
                return true;
            default:
                return false;
        }
    }


    
    public static final class BasicBlock {
        public  int stackMark = 0;
		public final int startOffset;
        public final List<DecodedInstr> instructions = new ArrayList<>();
        public final Set<BasicBlock> successors = new HashSet<>();
        public final Set<BasicBlock> predecessors = new HashSet<>();
        public final List<IRInstr> ir = new ArrayList<>();
        public List<IRValue> inStack;
        public List<IRValue> outStack;
        public IRValue inAcc;
        public IRValue outAcc;

        public boolean simulated = false;
        public int stackArgCount = 0;
        public BasicBlock(int startOffset) {
            this.startOffset = startOffset;
        }
    }


    static Map<Integer, BasicBlock> buildBasicBlocks(
            List<DecodedInstr> instructions,
            Set<Integer> blockStarts) {

        Map<Integer, BasicBlock> blocks = new HashMap<>();
        BasicBlock current = null;
        
        
        for (DecodedInstr di : instructions) {
            if (current == null || blockStarts.contains(di.offset)) {
                current = new BasicBlock(di.offset);
                blocks.put(di.offset, current);
            }

            current.instructions.add(di);

            if (isTerminator(di.opcode)) {
                current = null;
            }
        }


        return blocks;
    }

    static void buildCFG(
            Map<Integer, BasicBlock> blocks,
            List<DecodedInstr> instructions
    ) {
        for (BasicBlock bb : blocks.values()) {
            DecodedInstr last = bb.instructions.get(bb.instructions.size() - 1);

            switch (last.opcode) {

            case J32:
            case J8: {
                int targetOff = resolveJumpTarget(last);
                connect(bb, resolveBlockAtOrAfter(blocks, targetOff));
                break;
            }

            case JZ8:
            case JZ32: {
                int targetOff = resolveJumpTarget(last);
                BasicBlock zeroTarget =
                        resolveBlockAtOrAfter(blocks, targetOff);
                BasicBlock fallthrough =
                        blocks.get(last.offset + last.size);

                if (zeroTarget != null) connect(bb, zeroTarget);
                if (fallthrough != null) connect(bb, fallthrough);
                break;
            }

            case JNZ8:
            case JNZ32: {
                int targetOff = resolveJumpTarget(last);
                BasicBlock nonZeroTarget =
                        resolveBlockAtOrAfter(blocks, targetOff);
                BasicBlock fallthrough =
                        blocks.get(last.offset + last.size);

                if (nonZeroTarget != null) connect(bb, nonZeroTarget);
                if (fallthrough != null) connect(bb, fallthrough);
                break;
            }

            case RET:
            case END:
            case ENDNZ:
                break;

            default: {
                BasicBlock fallthrough =
                        blocks.get(last.offset + last.size);
                if (fallthrough != null)
                    connect(bb, fallthrough);
            }
            }
        }
    }




    static void connect(BasicBlock from, BasicBlock to) {
        if (to == null) return;
        from.successors.add(to);
        to.predecessors.add(from);
    }

    static int nextOffset(BasicBlock bb) {
        DecodedInstr last = bb.instructions.get(bb.instructions.size() - 1);

        
        int idx = bb.instructions.indexOf(last);
        // simpler: store end offset during block build
        return last.offset  + last.size;
    }
    static final class IRStackArg extends IRValue {
        public final int index;

        IRStackArg(int index) {
            this.index = index;
        }

        public String toString() {
            return "arg" + index;
        }
    }
    static IRValue popOrArg(BasicBlock bb, List<IRValue> stack) {
        if (!stack.isEmpty()) {
            return stack.remove(stack.size() - 1);
        }

        // stack underflow → implicit input
        IRStackArg arg = new IRStackArg(bb.stackArgCount++);
        return arg;
    }

    static List<IRValue> simulateBlock(
    	    BasicBlock bb,
    	    List<IRValue> inputStack,
    	    IRValue inputAcc,
    	    Map<Integer, BasicBlock> blocks,
    	    LocalTracker locals
    	)


 {
    	    List<IRValue> stack = new ArrayList<>(inputStack);

    	    IRValue acc = inputAcc;

    	    bb.stackMark = stack.size();
    	    for (DecodedInstr di : bb.instructions) {
    	        switch (di.opcode) {

    	        // ----------- ACCUMULATOR PRODUCERS -----------
    	        case FPADD_BYTE:
    	        case FPADD_WORD: {
    	            IRTemp t = newTemp();
    	            bb.ir.add(new IRAssign(
    	                t,
    	                "FPADD",
    	                new IRConst(di.operand),
    	                null
    	            ));
    	            acc = t;
    	            break;
    	        }

    	        case MOV_BYTE:
    	        case MOV_SHORT:
    	        case MOV_WORD:
    	        case MOV_WORD2:
    	        case MOV_LONG: {
    	            acc = new IRConst(di.operand); 
    	            break;
    	        }

    	        case LD_WORD:
    	        case LD_UBYTE:
    	        case LD_USHORT:
    	        case LD_BYTE:
    	        case LD_SHORT: {
    	            IRConst addr = new IRConst(di.operand);
    	            acc = addr;              // address, not loaded value
    	            break;
    	        }


    	        case FPLD32_UBYTE:
    	        case FPLD32_USHORT:
    	        case FPLD32_WORD:
    	        case FPLD32_BYTE:
    	        case FPLD32_SHORT:
    	        case FPLD32_LONG:

    	        case FPLD8_UBYTE:
    	        case FPLD8_USHORT:
    	        case FPLD8_WORD:
    	        case FPLD8_BYTE:
    	        case FPLD8_SHORT:
    	        case FPLD8_LONG: {
    	            int addr = ((Number) di.operand).intValue();
    	            IRTemp t = newTemp();
    	            bb.ir.add(new IRAssign(t, "FPLOAD", new IRConst(addr), null));
    	            acc = t;
    	            break;
    	        }


    	        case ADD_W:
    	        case ADD_F:
    	        case ADD_D: {
    	            // VM style: ACC = pop() (+) ACC
    	        	IRValue left = materializeLoad(bb, popOrArg(bb, stack));
    	        	IRValue right = materializeLoad(
    	        	    bb,
    	        	    (acc != null) ? acc : popOrArg(bb, stack)
    	        	);

    	        	IRTemp t = newTemp();
    	        	bb.ir.add(new IRAssign(t, di.opcode.code, left, right));
    	        	acc = t;

    	            break;
    	        }

    	        case EQZ: {
    	            IRTemp t = newTemp();
    	            bb.ir.add(new IRAssign(t, "!", acc != null ? acc : popOrArg(bb, stack), null));
    	            acc = t;
    	            break;
    	        }

    	        // ----------- STACK / ARGUMENT OPS -----------

    	        case PUSHW: {
    	            IRValue v = (acc != null) ? acc : new IRStackArg(bb.stackArgCount++);
    	            stack.add(v);
  
    	            break;
    	        }


    	        case NE_WORD:
    	        case NE_FLOAT:
    	        case NE_DOUBLE: {
    	            IRValue left;
    	            if (!stack.isEmpty()) {
    	                left = materializeLoad(bb, stack.remove(stack.size() - 1));
    	            } else {
    	                throw new IllegalStateException("NE_WORD: stack underflow");
    	            }

    	            IRValue right;
    	            if (acc != null) {
    	                right = materializeLoad(bb, acc);
    	            } else {
    	                throw new IllegalStateException("NE_WORD: missing ACC");
    	            }

    	            IRTemp t = newTemp();
    	            bb.ir.add(new IRAssign(t, "NE", left, right));
    	            acc = t;
    	            bb.stackMark = stack.size();
    	            break;
    	        }


    	        case POPW: {
    	            if (!stack.isEmpty()) stack.remove(stack.size() - 1);
    	            
    	            break;
    	        }

    	        case POP8:
    	        case POP32: {
    	            acc = null;
    	            break; // do NOT touch logical stack
    	        }


    	        // ----------- STORE: addr from stack, value from ACC -----------
    	        case STR_UINT: {
    	            // If we do not have both components, this is not a real store
    	            if (acc == null || stack.isEmpty()) {
    	                acc = null;
    	                break; // 🔥 discard-only STR
    	            }

    	            IRValue addr = popOrArg(bb, stack);
    	            IRValue value = acc;

    	            if (addr instanceof IRConst c) {
    	                int a = ((Number) c.value).intValue();
    	                IRLocal local = locals.getLocal(a);
    	                bb.ir.add(new IRAssign(local, "=", value, null));
    	            }
    	            else if (addr instanceof IRLocal local) {
    	                bb.ir.add(new IRAssign(local, "=", value, null));
    	            }
    	            else {
    	                bb.ir.add(new IRAssign(null, "STORE", addr, value));
    	            }

    	            acc = null;
    	            break;
    	        }




    	        // ----------- CONTROL FLOW -----------
    	     // ----------- CONTROL FLOW -----------

    	        case J32:
    	        case J8: {
    	            int targetOff = resolveJumpTarget(di);
    	            BasicBlock target =
    	                    resolveBlockAtOrAfter(blocks, targetOff);
    	            bb.ir.add(new IRJump(target));
    	            bb.outAcc = null;
    	            return stack;
    	        }

    	        case JZ32:
    	        case JZ8: {
    	            IRValue cond = acc;
    	            int targetOff = resolveJumpTarget(di);

    	            BasicBlock zeroTarget =
    	                    resolveBlockAtOrAfter(blocks, targetOff);
    	            BasicBlock nonZeroTarget =
    	                    blocks.get(di.offset + di.size);

    	            bb.ir.add(new IRCJump(cond, zeroTarget, nonZeroTarget));
    	            bb.outAcc = null;
    	            return stack;
    	        }

    	        case JNZ32:
    	        case JNZ8: {
    	            IRValue cond = acc;
    	            int targetOff = resolveJumpTarget(di);

    	            BasicBlock nonZeroTarget =
    	                    resolveBlockAtOrAfter(blocks, targetOff);
    	            BasicBlock zeroTarget =
    	                    blocks.get(di.offset + di.size);

    	            bb.ir.add(new IRCJump(cond, nonZeroTarget, zeroTarget));
    	            bb.outAcc = null;
    	            return stack;
    	        }

  

    	        // ----------- CALLS: args are exactly what was PUSHed -----------

    	        case CALL: {
    	            int target = ((Number) di.operand).intValue();

    	            List<IRValue> args = new ArrayList<>();
    	            while (stack.size() > bb.stackMark) {
    	                args.add(0, stack.remove(stack.size() - 1));
    	            }

    	            bb.stackMark = stack.size();

    	            IRTemp result = (target == -1) ? null : newTemp();
    	            bb.ir.add(new IRCall(target, args, result, false));
    	            acc = result;
    	            break;
    	        }
    	        case NEG_INT: {
    	            if (acc == null)
    	                throw new IllegalStateException("NEG_INT: missing ACC");

    	            IRTemp t = newTemp();
    	            bb.ir.add(new IRAssign(t, "NEG", acc, null));
    	            acc = t;
    	            break;
    	        }


    	        case NATIVECALL: {
    	            int target = ((Number) di.operand).intValue();

    	            List<IRValue> args = new ArrayList<>();

    	            while (stack.size() > bb.stackMark) {
    	                args.add(0, stack.remove(stack.size() - 1));
    	            }

    	            // 🔥 FIX: implicit ACC argument
    	            if (args.isEmpty() && acc != null) {
    	                args.add(acc);
    	            }

    	            bb.stackMark = stack.size();
    	            bb.ir.add(new IRCall(target, args, null, true));
    	            acc = null;
    	            break;
    	        }



    	        case RET:
    	            bb.ir.add(new IRReturn());
    	            bb.outAcc = acc;
    	            return stack;
    	        case ENDNZ:
    	            bb.ir.add(new IRReturn());
    	            return stack;

    	        default:
    	            break;
    	        }
    	    }

    	    bb.outAcc = acc;
    	    return stack;
    	}


    static IRValue mergeAcc(Set<BasicBlock> preds) {
        IRValue ref = null;

        for (BasicBlock p : preds) {
            if (p.outAcc == null)
                continue;

            if (ref == null) {
                ref = p.outAcc;
            } else if (ref != p.outAcc) {
                return null; // conflicting ACCs
            }
        }

        return ref;
    }

    static void cleanupIR(BasicBlock bb, LocalTracker locals) {
        Map<IRTemp, IRValue> subst = new HashMap<>();
        List<IRInstr> newIR = new ArrayList<>();
        
        for (IRInstr ir : bb.ir) {

        	
            if (ir instanceof IRAssign a) {

                // Apply substitutions to sources first
                IRValue src1 = substitute(a.src1, subst);
                IRValue src2 = substitute(a.src2, subst);

             // Fold: tX = NEG CONST → tX = CONST(-value)
                if (a.dst instanceof IRTemp t &&
                    a.op.equals("NEG") &&
                    a.src1 instanceof IRConst c &&
                    a.src2 == null) {

                    Object v = c.value;

                    if (v instanceof Integer i) {
                        subst.put(t, new IRConst(-i));
                        continue;
                    }
                }


             // STORE <addr>, <value>
// If <addr> is a constant, turn it into a write to the corresponding IRLocal.
if (a.op.equals("STORE") && src1 instanceof IRConst c) {
    int addr = ((Number) c.value).intValue();
    IRLocal dst = locals.getLocal(addr);
    newIR.add(new IRAssign(dst, "=", src2, null));
    continue;
}



                // Pattern: tX = CONST c
                if (a.dst instanceof IRTemp t &&
                    a.op.equals("CONST")) {

                    subst.put(t, src1);
                    continue; // drop this instruction
                }

                // Pattern: local = tX   → local = value
                if (a.dst instanceof IRLocal &&
                    src2 == null &&
                    src1 instanceof IRTemp t &&
                    subst.containsKey(t)) {

                    newIR.add(new IRAssign(
                        a.dst,
                        "=",
                        subst.get(t),
                        null
                    ));
                    continue;
                }

                // General case
                newIR.add(new IRAssign(a.dst, a.op, src1, src2));
            }
            else if (ir instanceof IRCall c) {
                // substitute inside arguments too
                List<IRValue> newArgs = new ArrayList<>(c.args.size());
                for (IRValue a : c.args) newArgs.add(substitute(a, subst));

                newIR.add(new IRCall(c.target, newArgs, substitute(c.result, subst), c.isNative));
            }
            else {
                newIR.add(ir);
            }
        }

        bb.ir.clear();
        bb.ir.addAll(newIR);
    }
    static final class IRCall extends IRInstr {
        final int target;
        final List<IRValue> args;
        final IRValue result;
        final boolean isNative;

        IRCall(int target, List<IRValue> args, IRValue result, boolean isNative) {
            this.target = target;
            this.args = args;
            this.result = result;
            this.isNative = isNative;
        }

        @Override
        public String toString() {
            String name;

            if (target == -1) {
                name = "internal_alloc";
            }
            else if (isNative) {
                name = nativeNames.getOrDefault(
                    target,
                    String.format("native_0x%X", target)
                );
            }
            else {
                name = String.format("func_0x%X", target);
            }

            String argStr = args.isEmpty()
                ? ""
                : args.stream().map(Object::toString).collect(Collectors.joining(", "));

            if (result != null) {
                return result + " = call " + name + "(" + argStr + ")";
            } else {
                return "call " + name + "(" + argStr + ")";
            }
        }

    }


    static IRValue substitute(IRValue v, Map<IRTemp, IRValue> subst) {
        if (v instanceof IRTemp t && subst.containsKey(t))
            return subst.get(t);
        return v;
    }
    static void inlineSingleUseTemps(BasicBlock bb) {
        Map<IRTemp, Integer> uses = new HashMap<>();

        // Count uses
        for (IRInstr ir : bb.ir) {
            if (ir instanceof IRAssign a) {
                countUse(a.src1, uses);
                countUse(a.src2, uses);
            }
        }

        List<IRInstr> newIR = new ArrayList<>();
        Map<IRTemp, IRAssign> defs = new HashMap<>();

        // Collect definitions
        for (IRInstr ir : bb.ir) {
            if (ir instanceof IRAssign a && a.dst instanceof IRTemp t) {
                defs.put(t, a);
            }
        }

        for (IRInstr ir : bb.ir) {
            if (ir instanceof IRAssign a &&
                a.dst instanceof IRLocal &&
                a.src1 instanceof IRTemp t &&
                uses.getOrDefault(t, 0) == 1 &&
                defs.containsKey(t)) {

                IRAssign def = defs.get(t);

                newIR.add(new IRAssign(
                    a.dst,
                    def.op,
                    def.src1,
                    def.src2
                ));
            }
            else if (!(ir instanceof IRAssign a2 &&
                    a2.dst instanceof IRTemp &&
                    uses.getOrDefault(a2.dst, 0) == 1 &&
                    a2.op.equals("CONST"))) {   // 🔥 ONLY inline CONST temps
             newIR.add(ir);
         }

        }

        bb.ir.clear();
        bb.ir.addAll(newIR);
    }

    static void countUse(IRValue v, Map<IRTemp, Integer> uses) {
        if (v instanceof IRTemp t)
            uses.merge(t, 1, Integer::sum);
    }

    static List<IRValue> popCallArgs(List<IRValue> stack) {
        List<IRValue> args = new ArrayList<>();

        // heuristic: consume until empty or stack arg boundary
        while (!stack.isEmpty()) {
            IRValue v = stack.remove(stack.size() - 1);
            args.add(0, v); // preserve order
        }

        return args;
    }

    static List<IRValue> mergeStacks(Set<BasicBlock> preds) {
        List<IRValue> ref = null;

        for (BasicBlock p : preds) {
            if (p.outStack == null)
                continue;

            if (ref == null) {
                ref = p.outStack;
            } else if (!sameShape(ref, p.outStack)) {
                // give up – stack mismatch
                return new ArrayList<>();
            }
        }

        return ref == null ? new ArrayList<>() : new ArrayList<>(ref);
    }

    static boolean sameShape(List<IRValue> a, List<IRValue> b) {
        if (a.size() != b.size())
            return false;

        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).getClass() != b.get(i).getClass())
                return false;
        }
        return true;
    }

    static void simulateAllBlocks(Map<Integer, BasicBlock> blocks) {
        LocalTracker locals = new LocalTracker();

        // sort blocks by address (good enough for now)
        List<BasicBlock> ordered = new ArrayList<>(blocks.values());
        ordered.sort(Comparator.comparingInt(b -> b.startOffset));

        for (BasicBlock bb : ordered) {

            if (bb.predecessors.isEmpty()) {
                bb.inStack = new ArrayList<>();
            } else {
                bb.inStack = mergeStacks(bb.predecessors);
                bb.inAcc = mergeAcc(bb.predecessors);
            }

            bb.outStack = simulateBlock(
            	    bb,
            	    bb.inStack,
            	    bb.inAcc,
            	    blocks,
            	    locals
            	);
        }

        for (BasicBlock bb : ordered) {
            cleanupIR(bb, locals);
            inlineSingleUseTemps(bb);
        }
    }




    private static String toASCIIString(int val) {
        return new String(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(val).array()).replaceAll("\\p{C}", " ");
    }
    
    private static Map<Integer, Integer> map = new HashMap<>();
    
    static void dumpIRToFile(
            Map<Integer, BasicBlock> blocks,
            File outFile) throws IOException {

        try (PrintWriter out = new PrintWriter(
                new BufferedWriter(new FileWriter(outFile)))) {

            List<BasicBlock> ordered =
                    new ArrayList<>(blocks.values());

            ordered.sort(Comparator.comparingInt(bb -> bb.startOffset));

            for (BasicBlock bb : ordered) {
                out.printf("BLOCK 0x%06X%n", bb.startOffset);

                out.println("  ; raw instructions");
                for (DecodedInstr di : bb.instructions) {
                    out.printf(
                        "  ; %06X: %-12s %s%n",
                        di.offset,
                        di.opcode.name(),
                        di.operand == null ? "" : di.operand.toString()
                    );
                }

                out.println("  ; lifted IR");
                for (String line : formatBlockIR(bb)) {
                    out.println("  " + line);
                }

                out.println();
            }
        }
    }
    private static IRValue materializeLoad(BasicBlock bb, IRValue v) {
 
        if (v instanceof IRConst)
            return v;

        return v;
    }


    static List<String> formatBlockIR(BasicBlock bb) {
        List<String> out = new ArrayList<>();

        for (int i = 0; i < bb.ir.size(); i++) {
            IRInstr ir = bb.ir.get(i);

            // Fold:
            //   call Foo(...)
            //   tX = !arg0        OR tX = !<callResultTemp>
            //   if tX goto A else B
            if (i + 2 < bb.ir.size()
                    && ir instanceof IRCall call
                    && bb.ir.get(i + 1) instanceof IRAssign a
                    && bb.ir.get(i + 2) instanceof IRCJump c
                    && isNegatedCondAssign(a, c.cond)
                    && isCallResultSource(call, a.src1)) {

                out.add(formatNegatedCallJump(call, c));
                i += 2;
                continue;
            }

            // Fold:
            //   tX = !expr
            //   if tX goto A else B
            if (i + 1 < bb.ir.size()
                    && ir instanceof IRAssign a
                    && bb.ir.get(i + 1) instanceof IRCJump c
                    && isNegatedCondAssign(a, c.cond)) {

                out.add(formatNegatedJump(a.src1, c));
                i += 1;
                continue;
            }

            out.add(formatIR(ir));
        }

        return out;
    }

    static boolean isNegatedCondAssign(IRAssign a, IRValue cond) {
        return "!".equals(a.op)
                && a.src2 == null
                && sameValue(a.dst, cond);
    }

    static boolean isCallResultSource(IRCall call, IRValue v) {
        // Native calls often feed EQZ through implicit arg0
        if (call.result == null) {
            return isArg0(v);
        }

        // Non-native / explicit-result calls can feed the temp directly
        return sameValue(call.result, v);
    }

    static boolean isArg0(IRValue v) {
        return v instanceof IRStackArg a && a.index == 0;
    }

    static boolean sameValue(IRValue a, IRValue b) {
        if (a == b) return true;
        if (a == null || b == null) return false;

        if (a instanceof IRTemp ta && b instanceof IRTemp tb) {
            return ta.id == tb.id;
        }
        if (a instanceof IRStackArg sa && b instanceof IRStackArg sb) {
            return sa.index == sb.index;
        }
        if (a instanceof IRLocal la && b instanceof IRLocal lb) {
            return la.index == lb.index;
        }
        if (a instanceof IRConst ca && b instanceof IRConst cb) {
            return java.util.Objects.equals(ca.value, cb.value);
        }

        return false;
    }

    static String formatNegatedCallJump(IRCall call, IRCJump c) {
        return "if (!" + formatCallExpr(call) + ") goto "
                + formatTarget(c.trueTarget)
                + " else goto "
                + formatTarget(c.falseTarget);
    }

    static String formatNegatedJump(IRValue value, IRCJump c) {
        return "if (!" + formatUnaryOperand(value) + ") goto "
                + formatTarget(c.trueTarget)
                + " else goto "
                + formatTarget(c.falseTarget);
    }

    static String formatTarget(BasicBlock bb) {
        return (bb != null)
                ? "0x" + Integer.toHexString(bb.startOffset)
                : "<unknown>";
    }

    static String formatValue(IRValue v) {
        return v == null ? "<null>" : v.toString();
    }

    static String formatUnaryOperand(IRValue v) {
        String s = formatValue(v);

        if (v instanceof IRTemp || v instanceof IRStackArg || v instanceof IRLocal || v instanceof IRConst) {
            return s;
        }

        return "(" + s + ")";
    }

    static String formatCallExpr(IRCall c) {
        return formatCallName(c) + "(" + String.join(", ", formatCallArgs(c)) + ")";
    }

    static String formatCallName(IRCall c) {
        NativeSignature sig = nativeSigs.get(c.target);

        if (c.target == -1) {
            return "internal_alloc";
        }

        if (c.isNative) {
            if (sig != null) {
                return sig.name;
            }
            return nativeNames.getOrDefault(
                    c.target,
                    String.format("native_0x%X", c.target)
            );
        }

        return String.format("func_0x%X", c.target);
    }

    static List<String> formatCallArgs(IRCall c) {
        NativeSignature sig = nativeSigs.get(c.target);
        List<String> args = new ArrayList<>();

        // Native calls: script push order is reversed vs param_1[] layout
        List<IRValue> source = c.isNative
            ? new ArrayList<>(c.args)
            : c.args;
        if (c.isNative) Collections.reverse(source);

        for (int i = 0; i < source.size(); i++) {
            IRValue v = source.get(i);

            if (sig != null
                    && v instanceof IRConst k
                    && sig.argEnums.containsKey(i)) {

                String sym = sig.argEnums.get(i).get(k.value);
                if (sym != null) {
                    args.add(sym);
                    continue;
                }
            }

            args.add(formatValue(v));
        }

        return args;
    }
    
    static String formatIR(IRInstr ir) {

        if (ir instanceof IRAssign a) {
            if ("=".equals(a.op)) {
                return formatValue(a.dst) + " = " + formatValue(a.src1);
            }

            if ("!".equals(a.op) && a.src2 == null) {
                return formatValue(a.dst) + " = !" + formatUnaryOperand(a.src1);
            }

            if ("NEG".equals(a.op) && a.src2 == null) {
                return formatValue(a.dst) + " = -" + formatUnaryOperand(a.src1);
            }

            if (a.src2 != null) {
                return formatValue(a.dst) + " = " + a.op + " " + formatValue(a.src1) + ", " + formatValue(a.src2);
            }

            return formatValue(a.dst) + " = " + a.op + " " + formatValue(a.src1);
        }

        if (ir instanceof IRCJump c) {
            return "if " + formatValue(c.cond)
                    + " goto " + formatTarget(c.trueTarget)
                    + " else goto " + formatTarget(c.falseTarget);
        }

        if (ir instanceof IRJump j) {
            return "goto " + formatTarget(j.target);
        }

        if (ir instanceof IRCall c) {
            String expr = formatCallExpr(c);
            return c.result != null
                    ? formatValue(c.result) + " = call " + expr
                    : "call " + expr;
        }

        if (ir instanceof IRReturn) {
            return "return";
        }

        return ir.toString();
    }

    
    static final class IRLocal extends IRValue {
        final int index;
        IRLocal(int index) { this.index = index; }
        public String toString() { return "local_" + index; }
    }
    static final class LocalTracker {
        private final Map<Integer, IRLocal> locals = new HashMap<>();
        private int nextIndex = 0;

        IRLocal getLocal(int address) {
            return locals.computeIfAbsent(
                address,
                a -> new IRLocal(nextIndex++)
            );
        }
    }

    static void dumpDisassemblyToFile(
            List<DecodedInstr> instrs,
            File outFile) throws IOException {

        try (PrintWriter out = new PrintWriter(
                new BufferedWriter(new FileWriter(outFile)))) {

            for (DecodedInstr di : instrs) {
                out.printf(
                    "0x%06X  %-12s",
                    di.offset,
                    di.opcode.name()
                );

                if (di.operand != null) {
                    if (di.operand instanceof Number n) {
                        out.printf(" 0x%X", n.longValue());
                    } else {
                        out.printf(" %s", di.operand);
                    }
                }

                out.println();
            }
        }
    }

    static void mergeBasicBlocks(Map<Integer, BasicBlock> blocks) {
        boolean changed;

        do {
            changed = false;

            List<BasicBlock> list = new ArrayList<>(blocks.values());

            for (BasicBlock a : list) {
                if (a.successors.size() != 1)
                    continue;

                BasicBlock b = a.successors.iterator().next();

                if (b.predecessors.size() != 1)
                    continue;

                DecodedInstr lastInstr =
                    a.instructions.get(a.instructions.size() - 1);

                if (isTerminator(lastInstr.opcode))
                    continue;

                // ---- merge B into A ----
                a.instructions.addAll(b.instructions);
                a.ir.addAll(b.ir);

                a.successors.clear();
                a.successors.addAll(b.successors);

                for (BasicBlock succ : b.successors) {
                    succ.predecessors.remove(b);
                    succ.predecessors.add(a);
                }

                blocks.remove(b.startOffset);
                changed = true;
                break;
            }
        } while (changed);
    }
    
    static int canonicalTargetOffset(List<DecodedInstr> instrs, int rawTargetOff) {
        for (DecodedInstr di : instrs) {
            if (di.offset == rawTargetOff) {
                return di.offset;
            }
        }
        return -1;
    }

    static void dumpBinary(byte[] data, File outFile) throws IOException {
        try (var out = new java.io.FileOutputStream(outFile)) {
            out.write(data);
        }
    }
    static BasicBlock resolveTargetBlock(
    	    Map<Integer, BasicBlock> blocks,
    	    List<DecodedInstr> instrs,
    	    int rawTargetOff
    	) {
    	    int canon = canonicalTargetOffset(instrs, rawTargetOff);
    	    return canon == -1 ? null : blocks.get(canon);
    	}


    static int resolveJumpTarget(DecodedInstr di) {
        // HARD GUARD — never trust callers
        if (di.paramType == ParamType.NONE || di.operand == null) {
            throw new IllegalStateException(
                "resolveJumpTarget called on non-jump instruction: " +
                di.opcode + " at 0x" + Integer.toHexString(di.offset)
            );
        }

        int off = ((Number) di.operand).intValue();

        if (di.paramType == ParamType.VAL8) {
            byte rel = (byte) off;
            return di.offset + di.size + rel;
        }

        // VAL32 = absolute
        return off;
    }

    static File chooseInputFile() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select .res file to decode");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        int result = chooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        }

        throw new RuntimeException("No input file selected.");
    }
    
    static File withSuffix(File input, String suffix) {
        return new File(input.getParentFile(), input.getName() + suffix);
    }

    public static void main(String[] args) throws IOException {
        try (Access acc = new FileAccess(new File("./code.bin"), true)) {
            for (ScriptFunction f : ScriptFunction.values()) {
                acc.setPosition(f.getOffset() - 0x100000L);
                
                for (int i = 0; i < f.getNumFunctions(); i++) {
                    int val = acc.readInteger();
                    map.put(f.getStartingId() + i, val);
                }
            }
        }
        
        // ---- pick input file ----
        File input = chooseInputFile();

        File disasmOut = withSuffix(input, ".disasm");
        File irOut     = withSuffix(input, ".ir");
        File binOut    = withSuffix(input, ".bin");
        
        try (Access acc = new FileAccess(input, true)) {
            AbstractKCAP kcap = (AbstractKCAP) ResPayload.craft(acc);
            GenericPayload pl = (GenericPayload) kcap.get(0);
            dumpBinary(pl.getData(), binOut);
            try (StreamAccess access = new StreamAccess(pl.getData())) {

                List<DecodedInstr> instrs = disassemble(access);
                dumpDisassemblyToFile(instrs, disasmOut);

                Set<Integer> blockStarts = findBlockStarts(instrs);
                Map<Integer, BasicBlock> blocks =
                        buildBasicBlocks(instrs, blockStarts);

                buildCFG(blocks, instrs);
                simulateAllBlocks(blocks);
                mergeBasicBlocks(blocks);

                dumpIRToFile(blocks, irOut);
            }
        }

        System.out.println("Written:");
        System.out.println("  " + disasmOut.getAbsolutePath());
        System.out.println("  " + irOut.getAbsolutePath());
    }
    
    enum ScriptFunction {
        FUNCTIONS_1(1, 27, 0x4EB8C0),
        FUNCTIONS_1000(1000, 20, 0x4EB92C),
        FUNCTIONS_10000(10000, 6, 0x4EB97C),
        FUNCTIONS_20000(20000, 58, 0x4E70D8),
        FUNCTIONS_20300(20300, 34, 0x4E71C0),
        FUNCTIONS_20500(20500, 10, 0x4E7248),
        FUNCTIONS_20600(20600, 29, 0x4E7270),
        FUNCTIONS_20700(20700, 35, 0x4E72E4),
        FUNCTIONS_20800(20800, 55, 0x4E7370),
        FUNCTIONS_20900(20900, 11, 0x4E744C),
        FUNCTIONS_21000(21000, 20, 0x4E7478),
        FUNCTIONS_21200(21200, 59, 0x4E74C8),
        FUNCTIONS_21400(21400, 34, 0x4E75B4),
        FUNCTIONS_21500(21500, 13, 0x4E763C),
        FUNCTIONS_21900(21900, 1, 0x4E70C4),
        FUNCTIONS_22000(22000, 55, 0x4E65F0),
        FUNCTIONS_22200(22200, 6, 0x4E66CC),
        FUNCTIONS_22250(22250, 25, 0x4E66E4),
        FUNCTIONS_22300(22300, 61, 0x4E6748),
        FUNCTIONS_22400(22400, 15, 0x4E683C),
        FUNCTIONS_22500(22500, 17, 0x4E6878),
        FUNCTIONS_22600(22600, 29, 0x4E68BC),
        FUNCTIONS_22650(22650, 17, 0x4E6930),
        FUNCTIONS_22700(22700, 32, 0x4E6974),
        FUNCTIONS_22800(22800, 31, 0x4E69F4),
        FUNCTIONS_22900(22900, 15, 0x4E6A70),
        FUNCTIONS_23000(23000, 67, 0x4E6AAC),
        FUNCTIONS_23300(23300, 142, 0x4E6BB8),
        FUNCTIONS_23600(23600, 13, 0x4E6DF0),
        FUNCTIONS_23650(23650, 17, 0x4E6E24),
        FUNCTIONS_23700(23700, 22, 0x4E6E68),
        FUNCTIONS_24000(24000, 35, 0x4E6538),
        FUNCTIONS_25000(25000, 32, 0x4E7698),
        FUNCTIONS_26000(26000, 85, 0x4E6EE0),
        FUNCTIONS_26100(26100, 32, 0x4E7034);
        
        private final int startingId;
        private final int numFunctions;
        private final int offset;
        
        private ScriptFunction(int startingId, int numFunctions, int offset) {
            this.startingId = startingId;
            this.numFunctions = numFunctions;
            this.offset = offset;
        }
        
        public int getNumFunctions() {
            return numFunctions;
        }
        
        public int getOffset() {
            return offset;
        }
        
        public int getStartingId() {
            return startingId;
        }
    }
}
