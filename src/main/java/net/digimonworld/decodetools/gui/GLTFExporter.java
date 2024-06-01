package net.digimonworld.decodetools.gui;

import static de.javagl.jgltf.model.GltfConstants.GL_ARRAY_BUFFER;
import static de.javagl.jgltf.model.GltfConstants.GL_ELEMENT_ARRAY_BUFFER;
import static de.javagl.jgltf.model.GltfConstants.GL_FLOAT;
import static de.javagl.jgltf.model.GltfConstants.GL_UNSIGNED_BYTE;
import static de.javagl.jgltf.model.GltfConstants.GL_UNSIGNED_INT;
import static de.javagl.jgltf.model.GltfConstants.GL_NEAREST;
import static de.javagl.jgltf.model.GltfConstants.GL_LINEAR;
import static de.javagl.jgltf.model.GltfConstants.GL_UNSIGNED_SHORT;
import static de.javagl.jgltf.model.GltfConstants.GL_SHORT;

import static de.javagl.jgltf.model.GltfConstants.GL_CLAMP_TO_BORDER;
import static de.javagl.jgltf.model.GltfConstants.GL_CLAMP_TO_EDGE;
import static de.javagl.jgltf.model.GltfConstants.GL_REPEAT;
import static de.javagl.jgltf.model.GltfConstants.GL_MIRRORED_REPEAT;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import de.javagl.jgltf.impl.v2.Accessor;
import de.javagl.jgltf.impl.v2.Asset;
import de.javagl.jgltf.impl.v2.Buffer;
import de.javagl.jgltf.impl.v2.BufferView;
import de.javagl.jgltf.impl.v2.GlTF;
import de.javagl.jgltf.impl.v2.Image;
import de.javagl.jgltf.impl.v2.Material;
import de.javagl.jgltf.impl.v2.MaterialPbrMetallicRoughness;
import de.javagl.jgltf.impl.v2.Mesh;
import de.javagl.jgltf.impl.v2.MeshPrimitive;
import de.javagl.jgltf.impl.v2.Node;
import de.javagl.jgltf.impl.v2.Sampler;
import de.javagl.jgltf.impl.v2.Scene;
import de.javagl.jgltf.impl.v2.Skin;
import de.javagl.jgltf.impl.v2.Texture;
import de.javagl.jgltf.impl.v2.TextureInfo;
import de.javagl.jgltf.model.io.GltfWriter;
import net.digimonworld.decodetools.Main;
import net.digimonworld.decodetools.core.Vector4;
import net.digimonworld.decodetools.res.kcap.HSMPKCAP;
import net.digimonworld.decodetools.res.payload.GMIOPayload;
import net.digimonworld.decodetools.res.payload.GMIOPayload.TextureFiltering;
import net.digimonworld.decodetools.res.payload.GMIOPayload.TextureWrap;
import net.digimonworld.decodetools.res.payload.HSEMPayload;
import net.digimonworld.decodetools.res.payload.RTCLPayload;
import net.digimonworld.decodetools.res.payload.TNOJPayload;
import net.digimonworld.decodetools.res.payload.XDIOPayload;
import net.digimonworld.decodetools.res.payload.XTVOPayload;
import net.digimonworld.decodetools.res.payload.PADHPayload;
import net.digimonworld.decodetools.res.payload.hsem.HSEM07Entry;
import net.digimonworld.decodetools.res.payload.hsem.HSEMDrawEntry;
import net.digimonworld.decodetools.res.payload.hsem.HSEMEntry;
import net.digimonworld.decodetools.res.payload.hsem.HSEMJointEntry;
import net.digimonworld.decodetools.res.payload.hsem.HSEMMaterialEntry;
import net.digimonworld.decodetools.res.payload.hsem.HSEMTextureEntry;
import net.digimonworld.decodetools.res.payload.hsem.HSEMMaterialEntry;
import net.digimonworld.decodetools.res.payload.xtvo.XTVOAttribute;
import net.digimonworld.decodetools.res.payload.xtvo.XTVORegisterType;
import net.digimonworld.decodetools.res.payload.xtvo.XTVOValueType;
import net.digimonworld.decodetools.res.payload.xtvo.XTVOVertex;

public class GLTFExporter {
    private static final String BUFFER_URI = "data:application/octet-stream;base64,";

    private final HSMPKCAP hsmp;
    private final GlTF instance;

    private Map<Short, Short> jointAssignment = new HashMap<>();

    private short activeMaterial=-1;
    private Map<Short, Short> currentTexture = new HashMap<>();
    private int geomId = 0;
    private Node rootNode = new Node();

    public GLTFExporter(HSMPKCAP hsmp) {
        this.hsmp = hsmp;
        this.instance = new GlTF();

        initialize();
    }

    public void export(File output) {
        File outputFile = new File(output, hsmp.getName() + ".gltf");
        try (OutputStream os = new FileOutputStream(outputFile)) {
            GltfWriter gltfWriter = new GltfWriter();
            gltfWriter.write(instance, os);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        
    }

    private void initialize() {
        createAssetTag();
        createTextures();
        createJoints();
        createLocations();
        createGeometry();

        Scene scene = new Scene();
        rootNode.setName(hsmp.getName());
        instance.addNodes(rootNode);
        if (hsmp.getTNOJ() != null)
            scene.addNodes(0); // skin root
        scene.addNodes(instance.getNodes().size() - 1); // Add each node to the scene

        instance.addScenes(scene);
        instance.setScene(instance.getScenes().size() - 1);
    }

    private void createAssetTag() {
        Asset inputAsset = new Asset();
        inputAsset.setVersion("2.0");
        inputAsset.setGenerator("jgltf-parent-2.0.3");
        instance.setAsset(inputAsset);
    }

    
    private void exportCollision(PADHPayload padhPayload, String outputPath) throws IOException {
        // Extract vertices and faces
        List<float[]> vertices = new ArrayList<>();
        List<int[]> faces = new ArrayList<>();

        for (PADHPayload.MNKCSection section : padhPayload.getMNKCSections()) {
            for (PADHPayload.MNKCVertex vertex : section.getVertices()) {
                vertices.add(new float[]{vertex.getPosX(), vertex.getPosY(), vertex.getPosZ()});
            }
            for (PADHPayload.MNKCFace face : section.getFaces()) {
                faces.add(new int[]{face.getVertex1(), face.getVertex2(), face.getVertex3()});
            }
        }
     // Write to OBJ file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            writer.write("# OBJ file generated from PADHPayload\n");

            // Write vertices
            for (float[] vertex : vertices) {
                writer.write(String.format("v %f %f %f\n", vertex[0], vertex[1], vertex[2]));
            }

            // Write faces
            for (int[] face : faces) {
                writer.write(String.format("f %d %d %d\n", face[0], face[1], face[2]));
            }
        }
    }
    
    

        
    private void createTextures() {
        int imageId = 0;
        for (GMIOPayload gmio : hsmp.getGMIP().getGMIOEntries()) {
            String imageName = gmio.hasName() ? gmio.getName() : ("image-" + imageId++);

            // Convert Buffered Images to Byte Array
            ByteArrayOutputStream imagebuffer = new ByteArrayOutputStream();
            try {
                ImageIO.write(gmio.getImage(), "PNG", imagebuffer);
            }
            catch (IOException e) {
                // shouldn't ever happen
                e.printStackTrace();
            }

            // Embed Textures into GLTF
            Map<String, String> extra = new HashMap<>();
            extra.put("format", gmio.getFormat().name());
            Image image = new Image();
            image.setName(imageName);
            image.setUri("data:image/png;base64," + Base64.getEncoder().encodeToString(imagebuffer.toByteArray()));
            image.setExtras(extra);
            instance.addImages(image);

            // create sampler
            Sampler sampler = new Sampler();
            sampler.setMagFilter(convertFilterToGL(gmio.getMagFilter()));
            sampler.setMinFilter(convertFilterToGL(gmio.getMinFilter()));
            sampler.setWrapS(convertWrapToGL(gmio.getWrapS()));
            sampler.setWrapT(convertWrapToGL(gmio.getWrapT()));
            instance.addSamplers(sampler);

            // Create Texture and link it to the Image
            Texture texture = new Texture();
            texture.setSampler(instance.getSamplers().size() - 1);
            texture.setName(imageName + "_texture");
            texture.setSource(instance.getImages().size() - 1); // Set the image index
            instance.addTextures(texture);

            // Create Material and link it to the Texture
            // TODO use actual material data from LRTM section
            Material material = new Material();
            material.setDoubleSided(false);
            material.setName(imageName + "_material");
            if (gmio.getFormat().hasAlpha())
                material.setAlphaMode("BLEND");

            MaterialPbrMetallicRoughness pbrMetallicRoughness = new MaterialPbrMetallicRoughness();
            TextureInfo baseColorTextureInfo = new TextureInfo();
            baseColorTextureInfo.setIndex(instance.getTextures().indexOf(texture)); // Set the texture index
            pbrMetallicRoughness.setBaseColorTexture(baseColorTextureInfo);
            material.setPbrMetallicRoughness(pbrMetallicRoughness);

            instance.addMaterials(material);
        }
    }

    private void createJoints() {
        if (hsmp.getTNOJ() == null)
            return;

        List<float[]> matrixList = new ArrayList<>();
        Skin jointsSkin = new Skin();
        jointsSkin.setName(hsmp.getName() + "-joint");

        for (int i = 0; i < hsmp.getTNOJ().getEntryCount(); i++) {
            TNOJPayload j = hsmp.getTNOJ().get(i);

            float[] rotation = new float[] { j.getRotationX(), j.getRotationY(), j.getRotationZ(), j.getRotationW() };
            float[] scale = new float[] { j.getLocalScaleX(), j.getLocalScaleY(), j.getLocalScaleZ() };
            float[] translation = new float[] { j.getXOffset(), j.getYOffset(), j.getZOffset() };

            Main.LOGGER.info("Joint " + i + " Rotation: " + Arrays.toString(rotation));
            Main.LOGGER.info("Joint " + i + " Scale: " + Arrays.toString(scale));
            Main.LOGGER.info("Joint " + i + " Translation: " + Arrays.toString(translation));
            
            Node node = new Node();
            node.setName(j.getName());
            node.setScale(scale);
            node.setRotation(rotation);
            node.setTranslation(translation);
            instance.addNodes(node);
  
  
            jointsSkin.addJoints(instance.getNodes().size() - 1);
       
            
            matrixList.add(j.getOffsetMatrix());

            if (j.getParentId() != -1) {
                Node parent = instance.getNodes().get(j.getParentId());

                // Add the current node as a child of the parent
                if (parent != null)
                    parent.addChildren(instance.getNodes().size() - 1);
            }
        }

        int bindPoseBuffer = matrixListToBuffer(matrixList);
        int bindPoseBufferView = createBufferView(bindPoseBuffer, 0, "bindPoseBufferView");
        int bindAccessor = createAccessor(bindPoseBufferView, GL_FLOAT, matrixList.size(), "MAT4", "BINDS",false);
        jointsSkin.setInverseBindMatrices(bindAccessor);
        instance.addSkins(jointsSkin);
        printInverseBindMatrices(matrixList);
    }
    
    private void printInverseBindMatrices(List<float[]> matrixList) {
        System.out.println("Inverse Bind Matrices:");
        for (float[] matrix : matrixList) {
            System.out.println(Arrays.toString(matrix));
        }
    }

    private void createLocations() {
        if (hsmp.getRTCL() == null)
            return;

        for (int i = 0; i < hsmp.getRTCL().getEntryCount(); i++) {
            RTCLPayload loc = hsmp.getRTCL().get(i);

            Map<String, String> extra = new HashMap<>();
            extra.put("loc_id", Integer.toString(loc.getLocIndex()));

            Node node = new Node();
            node.setName(loc.getName());
            // gltf prefers for default transform matrices to be not specified
            if (!isIdentityMatrix(loc.getMatrix()))
                node.setMatrix(mirrorMatrix(loc.getMatrix()));
            node.setExtras(extra);
            instance.addNodes(node);

            if (loc.getParentBone() != -1) {
                Node parent = instance.getNodes().get(loc.getParentBone());

                // Add the current node as a child of the parent
                if (parent != null)
                    parent.addChildren(instance.getNodes().size() - 1);
            }
        }
    }


    private void createGeometry() {
        for (HSEMPayload hsem : hsmp.getHSEM().getHSEMEntries()) {  
            jointAssignment.clear();
            currentTexture.clear(); // Reset at the start of each new mesh processing                
            activeMaterial = -1; // Reset at the start of each new mesh processing
            Map<String, String> extra = new HashMap<>();
            extra.put("id", Integer.toString(hsem.getId()));
            extra.put("unk1_1", Integer.toString(hsem.getUnknown1_1()));
            extra.put("unk1_2", Integer.toString(hsem.getUnknown1_2()));
            extra.put("unk1_3", Integer.toString(hsem.getUnknown1_3()));
            extra.put("unk2", Integer.toString(hsem.getUnknown2()));
            extra.put("unk3", Integer.toString(hsem.getUnknown3()));
            extra.put("headerData", floatArrayToString(hsem.getHeaderData()));

            for (HSEMEntry entry : hsem.getEntries())
            {
            processHSEM(entry, extra);
          
            }}
    }

    private void processHSEMDraw(HSEMDrawEntry draw, Map<String, String> hsemExtra) {
        final XTVOPayload xtvo = hsmp.getXTVP().get(draw.getVertexId());
        final XDIOPayload xdio = hsmp.getXDIP().get(draw.getIndexId());
        final int vertexCount = xtvo.getVertices().size();
        Map<String, String> extra = new HashMap<>();
        extra.put("shader", Integer.toString(xtvo.getShaderId()));
        extra.put("mTex0", floatArrayToString(xtvo.getMTex0()));
        extra.put("mTex1", floatArrayToString(xtvo.getMTex1()));
        extra.put("mTex2", floatArrayToString(xtvo.getMTex2()));
        extra.put("mTex3", floatArrayToString(xtvo.getMTex3()));
        extra.putAll(hsemExtra);

        MeshPrimitive primitive = new MeshPrimitive();

        // build indices
        List<Integer> indices = xdio.getFaces().stream()
                                    .flatMap(a -> Stream.of(a.getVert1(), a.getVert2(), a.getVert3()))
                                    .collect(Collectors.toList());

        int facesBuffer = intListToBuffer(indices);
        int indexBufferView = createBufferView(facesBuffer, GL_ELEMENT_ARRAY_BUFFER, "facesBufferView");
        int indexAccessor = createAccessor(indexBufferView, GL_UNSIGNED_INT, indices.size(), "SCALAR", "INDICES",false);
        primitive.setIndices(indexAccessor);

        // build positions
        int posBuffer = vertexAttribToBuffer(xtvo.getVertices(), XTVORegisterType.POSITION);
        int posBufferView = createBufferView(posBuffer, GL_ARRAY_BUFFER, "posBufferView");
        int posAccessor = createPosAccessor(posBufferView, xtvo.getVertices());
        primitive.addAttributes("POSITION", posAccessor);

        // build normals
        if (xtvo.getAttribute(XTVORegisterType.NORMAL).isPresent()) {
            int normalBuffer = vertexAttribToBuffer(xtvo.getVertices(), XTVORegisterType.NORMAL);
            int normalBufferView = createBufferView(normalBuffer, GL_ARRAY_BUFFER, "normalBufferView");
            int normalAccessor = createAccessor(normalBufferView, GL_FLOAT, vertexCount, "VEC3", "NORMALS",false);
            primitive.addAttributes("NORMAL", normalAccessor);
        }

        // build colors
        if (xtvo.getAttribute(XTVORegisterType.COLOR).isPresent()) {
            int colorsBuffer = colorAttribToBuffer(xtvo.getVertices(), XTVORegisterType.COLOR);
            int colorsBufferView = createBufferView(colorsBuffer, GL_ARRAY_BUFFER, "colorBufferView");
            int colorsAccessor = createAccessor(colorsBufferView, GL_UNSIGNED_BYTE, vertexCount, "VEC4", "COLOR",true);
            primitive.addAttributes("COLOR_0", colorsAccessor);
        }

        // build UVSet 0
        if (xtvo.getAttribute(XTVORegisterType.TEXTURE0).isPresent()) {
            int tex0Buffer = textureCoordToBuffer(xtvo.getVertices(), xtvo.getMTex0());
            int tex0BufferView = createBufferView(tex0Buffer, GL_ARRAY_BUFFER, "tex0BufferView");
            int tex0Accessor = createAccessor(tex0BufferView, GL_FLOAT, vertexCount, "VEC2", "TEXTURE0",false);
            primitive.addAttributes("TEXCOORD_0", tex0Accessor);
        }

        // build UVSet 1
        if (xtvo.getAttribute(XTVORegisterType.TEXTURE1).isPresent()) {
            int tex1Buffer = textureCoordToBuffer(xtvo.getVertices(), xtvo.getMTex1());
            int tex1BufferView = createBufferView(tex1Buffer, GL_ARRAY_BUFFER, "tex1BufferView");
            int tex1Accessor = createAccessor(tex1BufferView, GL_FLOAT, vertexCount, "VEC2", "TEXTURE1",false);
            primitive.addAttributes("TEXCOORD_1", tex1Accessor);
        }

        // build vertex weights
        if (xtvo.getAttribute(XTVORegisterType.WEIGHT).isPresent()) {
            int weightsBuffer = vertexAttribToBuffer(xtvo.getVertices(), XTVORegisterType.WEIGHT);
            int weightsBufferView = createBufferView(weightsBuffer, GL_ARRAY_BUFFER, "weightsBufferView");
            int weightsAccessor = createAccessor(weightsBufferView, GL_FLOAT, vertexCount, "VEC4", "WEIGHTS",false);
            primitive.addAttributes("WEIGHTS_0", weightsAccessor);

        }

        // build joint mapping
        if (xtvo.getAttribute(XTVORegisterType.IDX).isPresent()) {
            int jointsBuffer = jointDataToBuffer(xtvo.getVertices(), jointAssignment);
            int jointsBufferView = createBufferView(jointsBuffer, GL_ARRAY_BUFFER, "jointsBufferView");
            int jointsAccessor = createAccessor(jointsBufferView, GL_UNSIGNED_BYTE, vertexCount, "VEC4", "JOINTS",false);
            primitive.addAttributes("JOINTS_0", jointsAccessor);
        }

       
        
        if (!currentTexture.isEmpty()) {
            List<String> texEntries = new ArrayList<>(); // Create a list to hold all texture entries
                              
            for (Map.Entry<Short, Short> textureEntry : currentTexture.entrySet()) {
                // Format each entry as "key value" and add to the list
                  if (textureEntry.getValue()!=-1) {
                      primitive.setMaterial((int) textureEntry.getValue());  // Set material from specific texture role
                  }  
                texEntries.add(textureEntry.getKey().toString() + " " + textureEntry.getValue().toString());
            }
            // Convert the list to a single string and store it in 'extra' under the key 'texEntry'
            extra.put("texEntry", String.join(", ", texEntries));
        }
            
                        
        Mesh mesh = new Mesh();
        mesh.setExtras(extra);
        mesh.addPrimitives(primitive);
        instance.addMeshes(mesh);

        Node node = new Node();
        node.setName("geom "+ geomId++);
        node.setMesh(instance.getMeshes().size() - 1);
        
        int meshId= instance.getMeshes().size() - 1;
        extra.put("meshId",  Integer.toString(meshId));
        
  
        
        extra.put("materialId" , Integer.toString(activeMaterial));
        if (xtvo.getAttribute(XTVORegisterType.IDX).isPresent())
        node.setSkin(0);
        instance.addNodes(node);
        rootNode.addChildren(instance.getNodes().size() - 1);
        
        
    }

    private void processHSEM(HSEMEntry entry, Map<String, String> extra) {

        switch (entry.getHSEMType()) {
            // unknown/unhandled
            case UNK03:
            case UNK07:
                if (entry instanceof HSEM07Entry) {
                    HSEM07Entry hsem07 = (HSEM07Entry) entry;
                    extra.put("hsem07_unk1", Short.toString(hsem07.getUnkn1()));
                    extra.put("hsem07_unk2", Short.toString(hsem07.getUnkn2()));
                    extra.put("hsem07_unk3", Short.toString(hsem07.getUnkn3()));
                    extra.put("hsem07_unk4", Short.toString(hsem07.getUnkn4()));
                }
                break;

            case JOINT:
                ((HSEMJointEntry) entry).getJointAssignment().forEach(jointAssignment::put);               
                break;

            case TEXTURE:            
                 currentTexture.clear();
                 currentTexture.putAll(((HSEMTextureEntry) entry).getTextureAssignment());
                 break;
                
            case MATERIAL:
                HSEMMaterialEntry materialEntry = (HSEMMaterialEntry) entry;
                activeMaterial = materialEntry.getMaterialId();
                break;

            case DRAW:
                processHSEMDraw((HSEMDrawEntry) entry, extra);
                break;
        }

    }

    // =========================
    // Accessor and View builder
    // =========================

    private int createPosAccessor(int bufferView, List<XTVOVertex> vertices) {
        Number[] minValues = new Number[] { Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY };
        Number[] maxValues = new Number[] { Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY };

        for (XTVOVertex vertex : vertices) {
            Entry<XTVOAttribute, List<Number>> entry = vertex.getParameter(XTVORegisterType.POSITION);

            float x = entry.getKey().getValue(entry.getValue().get(0));
            float y = entry.getKey().getValue(entry.getValue().get(1));
            float z = entry.getKey().getValue(entry.getValue().get(2));

            minValues[0] = Math.min(minValues[0].floatValue(), x);
            minValues[1] = Math.min(minValues[1].floatValue(), y);
            minValues[2] = Math.min(minValues[2].floatValue(), z);

            maxValues[0] = Math.max(maxValues[0].floatValue(), x);
            maxValues[1] = Math.max(maxValues[1].floatValue(), y);
            maxValues[2] = Math.max(maxValues[2].floatValue(), z);
        }

        Accessor accessor = new Accessor();
        accessor.setBufferView(bufferView);
        accessor.setComponentType(GL_FLOAT);
        accessor.setCount(vertices.size());
        accessor.setType("VEC3");
        accessor.setMin(minValues);
        accessor.setMax(maxValues);
        accessor.setName("POS");
        instance.addAccessors(accessor);

        return instance.getAccessors().size() - 1;
    }

    private int createAccessor(int bufferView, int componentType, int count, String type, String name,boolean normalized) {
        Accessor accessor = new Accessor();
        accessor.setBufferView(bufferView);
        accessor.setComponentType(componentType);
        accessor.setCount(count);
        accessor.setType(type);
        accessor.setName(name);
        accessor.setNormalized(normalized); 
        instance.addAccessors(accessor);
        return instance.getAccessors().size() - 1;
    }

    private int createBufferView(int buffer, int target, String name) {
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

    // ==============
    // Buffer Builder
    // ==============

    private int intListToBuffer(List<Integer> data) {
        ByteBuffer buffer = ByteBuffer.allocate(data.size() * Integer.BYTES);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        for (int i : data)
            buffer.putInt(i);
        buffer.flip(); // Prepare the buffer for reading

        Buffer gltfBuffer = new Buffer();
        gltfBuffer.setByteLength(buffer.remaining());
        gltfBuffer.setUri(BUFFER_URI + Base64.getEncoder().encodeToString(buffer.array()));
        instance.addBuffers(gltfBuffer);
        return instance.getBuffers().size() - 1;
    }

    private int textureCoordToBuffer(List<XTVOVertex> vertices, float[] mTex) {
        ByteBuffer buffer = ByteBuffer.allocate(vertices.size() * 2 * Float.BYTES);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        Vector4 mTex00 = new Vector4(mTex[2], 0f, 0f, mTex[0]);
        Vector4 mTex01 = new Vector4(0f, mTex[3], 0f, mTex[1]);

        for (XTVOVertex vertex : vertices) {
            Entry<XTVOAttribute, List<Number>> entry = vertex.getParameter(XTVORegisterType.TEXTURE0);
            if (entry == null)
                continue;

            Vector4 uvs = new Vector4(entry.getKey().getValue(entry.getValue().get(0)),
                                      entry.getKey().getValue(entry.getValue().get(1)), 0f, 1f);

            // Flip the V coordinate
            float u = uvs.dot(mTex00);
            float v = 1.0f - uvs.dot(mTex01);
            buffer.putFloat(u);
            buffer.putFloat(v);
        }

        buffer.flip(); // Prepare the buffer for reading

        Buffer gltfBuffer = new Buffer();
        gltfBuffer.setByteLength(buffer.remaining());
        gltfBuffer.setUri(BUFFER_URI + Base64.getEncoder().encodeToString(buffer.array()));
        instance.addBuffers(gltfBuffer);
        return instance.getBuffers().size() - 1;
    }
    
    private int colorAttribToBuffer(List<XTVOVertex> vertices, XTVORegisterType type) {
  

        List<Byte> byteList = vertices.stream()
                .map(vertex -> vertex.getParameter(type))
                .flatMap(entry -> entry.getValue().stream()
                    .map(value -> (byte) (value.intValue() & 0xFF))) // Ensure values are within byte range
                .collect(Collectors.toList());

            // Convert List<Byte> to ByteBuffer
            ByteBuffer byteBuffer = ByteBuffer.allocate(byteList.size());
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            byteList.forEach(byteBuffer::put);
            byteBuffer.flip(); // Prepare buffer for reading

       
            Buffer gltfBuffer = new Buffer();
            gltfBuffer.setByteLength(byteBuffer.remaining());
            gltfBuffer.setUri(BUFFER_URI + Base64.getEncoder().encodeToString(byteBuffer.array()));
            instance.addBuffers(gltfBuffer);

            return instance.getBuffers().size() - 1; // Return the index of the newly added buffer
        }


    
    
    private int vertexAttribToBuffer(List<XTVOVertex> vertices, XTVORegisterType type) {
        List<Float> floatList = vertices.stream().map(a -> a.getParameter(type))
                                        .flatMap(a -> a.getValue().stream().map(b -> a.getKey().getValue(b)))
                                        .collect(Collectors.toList());

        // Convert List<Float> to ByteBuffer
        ByteBuffer byteBuffer = ByteBuffer.allocate(floatList.size() * Float.BYTES);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);

        for (float f : floatList)
            byteBuffer.putFloat(f);

        byteBuffer.flip(); // Prepare the buffer for reading

        Buffer gltfBuffer = new Buffer();
        gltfBuffer.setByteLength(byteBuffer.remaining());
        gltfBuffer.setUri(BUFFER_URI + Base64.getEncoder().encodeToString(byteBuffer.array()));
        instance.addBuffers(gltfBuffer);

        return instance.getBuffers().size() - 1;
    }

    private int jointDataToBuffer(List<XTVOVertex> vertices, Map<Short, Short> jointMapping) {
        ByteBuffer jointsBuffer = ByteBuffer.allocate(vertices.size() * 4);
        jointsBuffer.order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < vertices.size(); i++) {
            XTVOVertex vertex = vertices.get(i);
            Entry<XTVOAttribute, List<Number>> entry = vertex.getParameter(XTVORegisterType.IDX);
            Entry<XTVOAttribute, List<Number>> weight = vertex.getParameter(XTVORegisterType.WEIGHT);

            for (int j = 0; j < 4; j++) {
                 int joint = jointMapping.get((short) (entry.getValue().get(j).intValue() / 3));
          
                if (weight.getValue().get(j).floatValue() != 0.0f)
                    jointsBuffer.put((byte) joint);
                else
                    jointsBuffer.put((byte) 0);
               }
        }
        jointsBuffer.flip();

        Buffer gltfBuffer = new Buffer();
        gltfBuffer.setUri(BUFFER_URI + Base64.getEncoder().encodeToString(jointsBuffer.array()));
        gltfBuffer.setByteLength(jointsBuffer.remaining());
        instance.addBuffers(gltfBuffer);

        return instance.getBuffers().size() - 1;
    }


    

    private int matrixListToBuffer(List<float[]> matrices) {
        ByteBuffer mBuffer = ByteBuffer.allocate(matrices.size() * 16 * 4);
        mBuffer.order(ByteOrder.LITTLE_ENDIAN);

        // Java doesn't have a Arrays.stream overload for float arrays, doing everything in the steam would be a PITA.
        List<float[]> mirroredMatrices = matrices.stream().map(GLTFExporter::mirrorMatrix).collect(Collectors.toList());

        for (float[] matrix : mirroredMatrices)
            for (float value : matrix)
                mBuffer.putFloat(value);

        mBuffer.flip();

        Buffer gltfBuffer = new Buffer();
        gltfBuffer.setUri(BUFFER_URI + Base64.getEncoder().encodeToString(mBuffer.array()));
        gltfBuffer.setByteLength(mBuffer.remaining());
        instance.addBuffers(gltfBuffer);

        return instance.getBuffers().size() - 1;
    }

    // ================
    // Helper Functions
    // ================

    private static boolean isIdentityMatrix(float[] matrix) {
        // @formatter:off
        float[] identity = new float[] { 1.0f, 0.0f, 0.0f, 0.0f, 
                                         0.0f, 1.0f, 0.0f, 0.0f, 
                                         0.0f, 0.0f, 1.0f, 0.0f, 
                                         0.0f, 0.0f, 0.0f, 1.0f };
        // @formatter:on

        if (matrix.length != 16)
            return false;

        for (int i = 0; i < 16; i++)
            if (identity[i] != matrix[i])
                return false;

        return true;
    }

    private static float[] mirrorMatrix(float[] matrix) {
        float[] mirroredMatrix = new float[16];

        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++)
                mirroredMatrix[i * 4 + j] = matrix[j * 4 + i];

        return mirroredMatrix;
    }

    private static int convertFilterToGL(TextureFiltering filter) {
        switch (filter) {
            case LINEAR:
                return GL_LINEAR;
            default:
            case NEAREST:
                return GL_NEAREST;
        }
    }

    private static int convertWrapToGL(TextureWrap wrap) {
        switch (wrap) {
            default:
            case REPEAT:
                return GL_REPEAT;
            case MIRRORED_REPEAT:
                return GL_MIRRORED_REPEAT;
            case CLAMP_TO_EDGE:
                return GL_CLAMP_TO_EDGE;
            case CLAMP_TO_BORDER:
                return GL_CLAMP_TO_BORDER;
        }
    }

    private static String floatArrayToString(float[] arr) {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < arr.length; i++) {
            if (i != 0)
                builder.append(" ");
            builder.append(arr[i]);
        }

        return builder.toString();
    }
}