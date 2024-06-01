package net.digimonworld.decodetools.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

public class HSEMData {

    public static class MeshInfo {
        int meshId;
        Map<Short, Short> texIds;
        int materialId;
        short hsemId;
        HSEM07Data hsem07Data;
        float[] headerData;
        UnkData unkData;
        int shader;

        public MeshInfo(int meshId, Map<Short, Short> texIds, int materialId, short hsemId, HSEM07Data hsem07Data,
                        float[] headerData, UnkData unkData, int shader) {
            this.meshId = meshId;
            this.texIds = texIds;
            this.materialId = materialId;
            this.hsemId = hsemId;
            this.hsem07Data = hsem07Data;
            this.headerData = headerData;
            this.unkData = unkData;
            this.shader = shader;
        }

        @Override
        public String toString() {
            return "MeshInfo{" +
                    "meshId=" + meshId +
                    ", texIds=" + texIds +
                    ", materialId=" + materialId +
                    ", hsemId=" + hsemId +
                    ", hsem07Data=" + hsem07Data +
                    ", headerData=" + Arrays.toString(headerData) +
                    ", unkData=" + unkData +
                    ", shader=" + shader +
                    '}';
        }
    }

    public static class HSEM07Data {
        short unk1, unk2, unk3, unk4;

        public HSEM07Data(short unk1, short unk2, short unk3, short unk4) {
            this.unk1 = unk1;
            this.unk2 = unk2;
            this.unk3 = unk3;
            this.unk4 = unk4;
        }

        @Override
        public String toString() {
            return "HSEM07Data{" +
                    "unk1=" + unk1 +
                    ", unk2=" + unk2 +
                    ", unk3=" + unk3 +
                    ", unk4=" + unk4 +
                    '}';
        }
    }

    public static class UnkData {
        short unk1_1;
        byte unk1_2;
        byte unk1_3;
        int unk2;
        int unk3;

        public UnkData(short unk1_1, byte unk1_2, byte unk1_3, int unk2, int unk3) {
            this.unk1_1 = unk1_1;
            this.unk1_2 = unk1_2;
            this.unk1_3 = unk1_3;
            this.unk2 = unk2;
            this.unk3 = unk3;
        }

        @Override
        public String toString() {
            return "UnkData{" +
                    "unk1_1=" + unk1_1 +
                    ", unk1_2=" + unk1_2 +
                    ", unk1_3=" + unk1_3 +
                    ", unk2=" + unk2 +
                    ", unk3=" + unk3 +
                    '}';
        }
    }

    private Map<Integer, MeshInfo> hsemDataMap;

    public HSEMData() {
        this.hsemDataMap = new HashMap<>();
    }

    public void getExtraData(String filePath) throws IOException {
        String gltfData = new String(Files.readAllBytes(Paths.get(filePath)));
        
        JSONObject jsonObject = new JSONObject(gltfData);
        JSONArray meshes = jsonObject.getJSONArray("meshes");
      
        for (int i = 0; i < meshes.length(); i++) {
            JSONObject meshObj = meshes.getJSONObject(i);
          
            if (meshObj.has("extras")) {
                JSONObject extras = meshObj.getJSONObject("extras");
           
                short hsemId = (short) (extras.optInt("id", -1));
                int meshId = extras.optInt("meshId", -1);
                int materialId = extras.optInt("materialId", 0);
                int shader = extras.optInt("shader", 8);

                           String texEntry = extras.optString("texEntry", "");
                Map<Short, Short> texIds = parseTexEntry(texEntry);

                HSEM07Data hsem07Data = new HSEM07Data((short) extras.optInt("hsem07_unk1", 15),
                        (short) extras.optInt("hsem07_unk2", 0), (short) extras.optInt("hsem07_unk3", 0),
                        (short) extras.optInt("hsem07_unk4", 0));

           
                float[] headerData = parseHeaderData(extras.optString("headerData", ""));
            
                UnkData unkData = parseUnkData(extras);
            
                MeshInfo meshInfo = new MeshInfo(meshId, texIds, materialId, hsemId, hsem07Data, headerData, unkData, shader);
                hsemDataMap.put(i, meshInfo);
        
            }}
    }


    private float[] parseHeaderData(String data) {
        if (data.isEmpty())
            return new float[0];

        String[] parts = data.split("\\s+");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i]);
        }
        return result;
    }

    private UnkData parseUnkData(JSONObject extras) {
        short unk1_1 = (short) extras.optInt("unk1_1", 0);
        byte unk1_2 = (byte) extras.optInt("unk1_2", 0);
        byte unk1_3 = (byte) extras.optInt("unk1_3", 0);
        int unk2 = extras.optInt("unk2", 0);
        int unk3 = extras.optInt("unk3", 0);

        return new UnkData(unk1_1, unk1_2, unk1_3, unk2, unk3);
    }

    public Map<Integer, MeshInfo> getHSEMDataMap() {
        return hsemDataMap;
    }

    public MeshInfo getMeshInfo(int meshId) {
        return hsemDataMap.get(meshId);
    }

    private Map<Short, Short> parseTexEntry(String texEntry) {
        Map<Short, Short> result = new HashMap<>();

        if (!texEntry.isEmpty()) {
            String[] parts = texEntry.split("\\s+");
            if (parts.length % 2 != 0) {
                throw new IllegalArgumentException("Invalid texEntry: " + texEntry + ". It must contain an even number of values.");
            }

            for (int i = 0; i < parts.length; i += 2) {
                short key = Short.parseShort(parts[i]);
                short value = Short.parseShort(parts[i + 1]);
                result.put(key, value);
            }
        }

        // Print the parsed short values
        System.out.println("Parsed short values:");
        for (Map.Entry<Short, Short> entry : result.entrySet()) {
            System.out.println(entry.getKey() + " -> " + entry.getValue());
        }

        return result;
    }

    public int countUniqueHsemIds() {
        return hsemDataMap.values().stream()
                .map(meshInfo -> meshInfo.hsemId)
                .collect(Collectors.toSet())
                .size();
    }
}
