package net.digimonworld.decodetools.res.payload.hsem;

import java.util.HashMap;
import java.util.Map;

import net.digimonworld.decodetools.core.Access;


//mapping from Joint ID (as in TNOJ) to the joint ID used inside the vertex data
//which is used because the game supports only up to 16 joints per batch

public class HSEMJointEntry implements HSEMEntry {
    private short unkn1;
    // short jointCount
    
    private Map<Short, Short> jointAssignment = new HashMap<>();
    
    public HSEMJointEntry(Map<Short, Short> assignment) {
        unkn1 = 0;
        jointAssignment.putAll(assignment);
    }    
    public HSEMJointEntry(Access source) {
        unkn1 = source.readShort();
        short jointCount = source.readShort();
        
        for (int i = 0; i < jointCount; i++)
            jointAssignment.put(source.readShort(), source.readShort());
    }
    
    @Override
    public void writeKCAP(Access dest) {
        dest.writeShort((short) getHSEMType().getId());
        dest.writeShort((short) getSize());
        
        dest.writeShort(unkn1);
        dest.writeShort((short) jointAssignment.size());
        
        jointAssignment.forEach((a, b) -> {
            dest.writeShort(a);
            dest.writeShort(b);
        });
    }
    
    @Override
    public int getSize() {
        return 0x08 + 0x04 * jointAssignment.size();
    }
    
    @Override
    public HSEMEntryType getHSEMType() {
        return HSEMEntryType.JOINT;
    }
    
    public int getJointCount() {
        return jointAssignment.size();
    }
    
    public Map<Short, Short> getJointAssignment() {
        return jointAssignment;
    }
    
    @Override
    public String toString() {
        return String.format("Joint | U1: %s | JCnt: %s", unkn1, jointAssignment.size());
    }
}
