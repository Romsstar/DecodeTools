package de.phoenixstaffel.decodetools.res.payload.xtvo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import de.phoenixstaffel.decodetools.core.Utils;

//TODO allow change of vertex order?
public class XTVOVertex {
    private SortedMap<XTVOAttribute, List<Number>> vertexParams = new TreeMap<>();
    
    public XTVOVertex(ByteBuffer source, List<XTVOAttribute> attributes) {
        attributes.forEach(a -> {
            List<Number> list = new ArrayList<>();
            
            source.position(Utils.align(source.position(), a.getValueType().getAlignment()));
            
            for (int i = 0; i < a.getCount(); i++)
                list.add(a.getValueType().read(source));
            
            vertexParams.put(a, list);
        });
    }
    
    public ByteBuffer write() {
        int size = 0;
        
        for (XTVOAttribute attr : vertexParams.keySet()) {
            size = Utils.align(size, attr.getValueType().getAlignment());
            size += attr.getCount() * attr.getValueType().getAlignment();
        }
        
        size = Utils.align(size, 2); // all data is aligned to two bytes
        
        ByteBuffer buff = ByteBuffer.allocate(size);
        buff.order(ByteOrder.LITTLE_ENDIAN);
        
        vertexParams.forEach((a, b) -> {
            buff.position(Utils.align(buff.position(), a.getValueType().getAlignment()));
            b.forEach(c -> a.getValueType().write(buff, c));
        });

        buff.position(Utils.align(buff.position(), 2));
        buff.flip();
        return buff;
    }
    
    public Entry<XTVOAttribute, List<Number>> getParameter(XTVORegisterType position) {
        return vertexParams.entrySet().stream().filter(a -> a.getKey().getRegisterId() == position).findFirst().orElse(null);
    }
}
