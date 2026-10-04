package com.nafim.ar_tryon_view.ar;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;

import com.google.ar.core.Anchor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Small GLB 2.0 renderer for a user-selected GLB file.
 *
 * The renderer intentionally stays OpenGL ES 2 compatible so the ARCore
 * camera preview keeps using the stable ES2 path.
 *
 * Supported:
 * - POSITION
 * - optional TEXCOORD_0
 * - unsigned byte/short/int GLB indices, including 32-bit indices when the device supports them
 * - node matrix / TRS transforms
 * - optional embedded baseColorTexture
 */
public final class GlbRenderer {
    private static final String TAG = "GLB_RENDERER";
    private static final int JSON_CHUNK = 0x4E4F534A;
    private static final int BIN_CHUNK = 0x004E4942;

    private static final class BufferView {
        int offset;
        int length;
        int stride;
    }

    private static final class Accessor {
        int bufferView;
        int offset;
        int componentType;
        int count;
        String shape;
        float[] min;
        float[] max;
    }

    private static final class MeshData {
        int positionBuffer;
        int uvBuffer;
        int indexBuffer;
        int indexCount;
        int indexType;
        int positionAccessor;
    }

    private static final class InstanceData {
        int meshIndex;
        final float[] matrix = new float[16];
    }

    private final List<MeshData> meshes = new ArrayList<>();
    private final List<InstanceData> instances = new ArrayList<>();

    private JSONObject json;
    private byte[] bin;
    private BufferView[] views;
    private Accessor[] accessors;

    private int program;
    private int aPosition;
    private int aUv;
    private int uMvp;
    private int uTexture;
    private int texture;

    private boolean ready;
    private String error;

    private final float[] normalize = new float[16];
    private final float[] anchorMatrix = new float[16];
    private final float[] temp = new float[16];
    private final float[] modelMatrix = new float[16];
    private final float[] mv = new float[16];
    private final float[] mvp = new float[16];

    public void createOnGlThread(InputStream inputStream, float sizeMeters) {
        ready = false;
        error = null;

        try {
            if (inputStream == null) {
                throw new Exception("Selected GLB file could not be opened");
            }

            byte[] glb = readAll(inputStream);
            parse(glb);

            createProgram();
            createTexture();
            createMeshes();
            createInstances();
            createNormalization(sizeMeters);
            ready = true;

            Log.i(
                    TAG,
                    "Selected GLB ready. bytes=" + glb.length
                            + ", meshes=" + meshes.size()
                            + ", instances=" + instances.size());
        } catch (Throwable t) {
            error = t.getMessage() == null
                    ? t.getClass().getSimpleName()
                    : t.getClass().getSimpleName() + ": " + t.getMessage();
            Log.e(TAG, "Selected GLB load failed", t);
        }
    }

    public boolean isReady() {
        return ready;
    }

    public String getErrorMessage() {
        return error == null ? "unknown GLB error" : error;
    }

    public void draw(Anchor anchor, float[] view, float[] projection) {
        if (!ready || anchor == null) return;

        anchor.getPose().toMatrix(anchorMatrix, 0);
        Matrix.multiplyMM(temp, 0, anchorMatrix, 0, normalize, 0);

        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glUniform1i(uTexture, 0);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        for (InstanceData instance : instances) {
            if (instance.meshIndex < 0 || instance.meshIndex >= meshes.size()) continue;

            MeshData mesh = meshes.get(instance.meshIndex);

            Matrix.multiplyMM(modelMatrix, 0, temp, 0, instance.matrix, 0);
            Matrix.multiplyMM(mv, 0, view, 0, modelMatrix, 0);
            Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0);

            GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0);

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.positionBuffer);
            GLES20.glEnableVertexAttribArray(aPosition);
            GLES20.glVertexAttribPointer(
                    aPosition,
                    3,
                    GLES20.GL_FLOAT,
                    false,
                    12,
                    0);

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.uvBuffer);
            GLES20.glEnableVertexAttribArray(aUv);
            GLES20.glVertexAttribPointer(
                    aUv,
                    2,
                    GLES20.GL_FLOAT,
                    false,
                    8,
                    0);

            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mesh.indexBuffer);
            GLES20.glDrawElements(
                    GLES20.GL_TRIANGLES,
                    mesh.indexCount,
                    mesh.indexType,
                    0);
        }

        GLES20.glDisableVertexAttribArray(aPosition);
        GLES20.glDisableVertexAttribArray(aUv);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    private void parse(byte[] glb) throws Exception {
        if (glb.length < 20) {
            throw new Exception("GLB is too small: " + glb.length + " bytes");
        }

        ByteBuffer buffer = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN);

        int magic = buffer.getInt();
        int version = buffer.getInt();
        int declaredLength = buffer.getInt();

        if (magic != 0x46546C67 || version != 2) {
            throw new Exception("Invalid GLB 2.0 header");
        }

        if (declaredLength != glb.length) {
            throw new Exception(
                    "GLB file is truncated/corrupt. Header says "
                            + declaredLength + " bytes, asset has " + glb.length + " bytes");
        }

        byte[] jsonChunk = null;
        byte[] binChunk = null;

        while (buffer.position() + 8 <= declaredLength) {
            int chunkLength = buffer.getInt();
            int chunkType = buffer.getInt();

            if (chunkLength < 0 || chunkLength > buffer.remaining()) {
                throw new Exception(
                        "GLB chunk is incomplete. Need " + chunkLength
                                + " bytes, remaining " + buffer.remaining());
            }

            byte[] chunk = new byte[chunkLength];
            buffer.get(chunk);

            if (chunkType == JSON_CHUNK) {
                jsonChunk = chunk;
            } else if (chunkType == BIN_CHUNK) {
                binChunk = chunk;
            }
        }

        if (jsonChunk == null || binChunk == null) {
            throw new Exception("GLB JSON/BIN chunk missing");
        }

        json = new JSONObject(new String(jsonChunk, StandardCharsets.UTF_8).trim());
        bin = binChunk;

        JSONArray viewArray = json.getJSONArray("bufferViews");
        views = new BufferView[viewArray.length()];

        for (int i = 0; i < viewArray.length(); i++) {
            JSONObject object = viewArray.getJSONObject(i);
            BufferView view = new BufferView();
            view.offset = object.optInt("byteOffset", 0);
            view.length = object.getInt("byteLength");
            view.stride = object.optInt("byteStride", 0);

            if (view.offset < 0
                    || view.length < 0
                    || view.offset + view.length > bin.length) {
                throw new Exception("bufferView " + i + " points outside BIN chunk");
            }
            views[i] = view;
        }

        JSONArray accessorArray = json.getJSONArray("accessors");
        accessors = new Accessor[accessorArray.length()];

        for (int i = 0; i < accessorArray.length(); i++) {
            JSONObject object = accessorArray.getJSONObject(i);
            Accessor accessor = new Accessor();
            accessor.bufferView = object.getInt("bufferView");
            accessor.offset = object.optInt("byteOffset", 0);
            accessor.componentType = object.getInt("componentType");
            accessor.count = object.getInt("count");
            accessor.shape = object.getString("type");
            accessor.min = floats(object.optJSONArray("min"));
            accessor.max = floats(object.optJSONArray("max"));
            accessors[i] = accessor;
        }
    }

    private void createProgram() {
        String vertexShader =
                "attribute vec3 a_Position;"
                        + "attribute vec2 a_TexCoord;"
                        + "uniform mat4 u_MVP;"
                        + "varying vec2 v_Uv;"
                        + "void main(){"
                        + "gl_Position=u_MVP*vec4(a_Position,1.0);"
                        + "v_Uv=vec2(a_TexCoord.x,1.0-a_TexCoord.y);"
                        + "}";

        String fragmentShader =
                "precision mediump float;"
                        + "uniform sampler2D u_Texture;"
                        + "varying vec2 v_Uv;"
                        + "void main(){"
                        + "gl_FragColor=texture2D(u_Texture,v_Uv);"
                        + "}";

        program = GlUtil.createProgram(vertexShader, fragmentShader);
        aPosition = GLES20.glGetAttribLocation(program, "a_Position");
        aUv = GLES20.glGetAttribLocation(program, "a_TexCoord");
        uMvp = GLES20.glGetUniformLocation(program, "u_MVP");
        uTexture = GLES20.glGetUniformLocation(program, "u_Texture");
    }

    private void createTexture() {
        try {
            if (!json.has("materials") || !json.has("textures") || !json.has("images")) {
                createSolidTexture();
                return;
            }

            JSONArray materials = json.getJSONArray("materials");
            if (materials.length() == 0) {
                createSolidTexture();
                return;
            }

            JSONObject pbr = materials
                    .getJSONObject(0)
                    .optJSONObject("pbrMetallicRoughness");

            if (pbr == null || !pbr.has("baseColorTexture")) {
                createSolidTexture();
                return;
            }

            int textureIndex = pbr
                    .getJSONObject("baseColorTexture")
                    .getInt("index");
            int source = json
                    .getJSONArray("textures")
                    .getJSONObject(textureIndex)
                    .getInt("source");

            JSONObject imageObject = json
                    .getJSONArray("images")
                    .getJSONObject(source);

            if (!imageObject.has("bufferView")) {
                createSolidTexture();
                return;
            }

            int bufferViewIndex = imageObject.getInt("bufferView");
            BufferView view = views[bufferViewIndex];
            byte[] imageBytes = Arrays.copyOfRange(
                    bin,
                    view.offset,
                    view.offset + view.length);

            Bitmap bitmap = BitmapFactory.decodeByteArray(
                    imageBytes,
                    0,
                    imageBytes.length);

            if (bitmap == null) {
                createSolidTexture();
                return;
            }

            int[] id = new int[1];
            GLES20.glGenTextures(1, id, 0);
            texture = id[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            setTextureParams();
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
            bitmap.recycle();
        } catch (Throwable textureError) {
            Log.w(TAG, "Texture unavailable; using solid fallback", textureError);
            createSolidTexture();
        }
    }

    private void createSolidTexture() {
        int[] id = new int[1];
        GLES20.glGenTextures(1, id, 0);
        texture = id[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        setTextureParams();

        ByteBuffer pixel = ByteBuffer.allocateDirect(4);
        pixel.put((byte) 80);
        pixel.put((byte) 80);
        pixel.put((byte) 80);
        pixel.put((byte) 255);
        pixel.position(0);

        GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                1,
                1,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                pixel);
    }

    private void setTextureParams() {
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_REPEAT);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_REPEAT);
    }

    private void createMeshes() throws Exception {
        JSONArray meshArray = json.getJSONArray("meshes");

        for (int i = 0; i < meshArray.length(); i++) {
            JSONArray primitives = meshArray
                    .getJSONObject(i)
                    .getJSONArray("primitives");

            if (primitives.length() == 0) continue;

            JSONObject primitive = primitives.getJSONObject(0);
            JSONObject attributes = primitive.getJSONObject("attributes");

            int positionAccessor = attributes.getInt("POSITION");
            int indexAccessor = primitive.getInt("indices");

            FloatBuffer positions = floatAccessor(positionAccessor, 3);
            int vertexCount = accessors[positionAccessor].count;

            FloatBuffer uvs;
            if (attributes.has("TEXCOORD_0")) {
                uvs = floatAccessor(attributes.getInt("TEXCOORD_0"), 2);
            } else {
                uvs = ByteBuffer
                        .allocateDirect(vertexCount * 2 * 4)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer();
                for (int v = 0; v < vertexCount; v++) {
                    uvs.put(0.5f);
                    uvs.put(0.5f);
                }
                uvs.position(0);
            }

            IndexData indices = indexAccessorData(indexAccessor);

            int[] ids = new int[3];
            GLES20.glGenBuffers(3, ids, 0);

            MeshData mesh = new MeshData();
            mesh.positionBuffer = ids[0];
            mesh.uvBuffer = ids[1];
            mesh.indexBuffer = ids[2];
            mesh.indexCount = accessors[indexAccessor].count;
            mesh.indexType = indices.glType;
            mesh.positionAccessor = positionAccessor;

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.positionBuffer);
            GLES20.glBufferData(
                    GLES20.GL_ARRAY_BUFFER,
                    positions.remaining() * 4,
                    positions,
                    GLES20.GL_STATIC_DRAW);

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.uvBuffer);
            GLES20.glBufferData(
                    GLES20.GL_ARRAY_BUFFER,
                    uvs.remaining() * 4,
                    uvs,
                    GLES20.GL_STATIC_DRAW);

            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mesh.indexBuffer);
            GLES20.glBufferData(
                    GLES20.GL_ELEMENT_ARRAY_BUFFER,
                    indices.buffer.remaining(),
                    indices.buffer,
                    GLES20.GL_STATIC_DRAW);

            meshes.add(mesh);
        }

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        if (meshes.isEmpty()) {
            throw new Exception("No mesh primitives found in GLB");
        }
    }

    private void createInstances() throws Exception {
        JSONArray nodes = json.getJSONArray("nodes");
        JSONArray scenes = json.getJSONArray("scenes");
        JSONArray roots = scenes
                .getJSONObject(json.optInt("scene", 0))
                .getJSONArray("nodes");

        float[] identity = new float[16];
        Matrix.setIdentityM(identity, 0);

        for (int i = 0; i < roots.length(); i++) {
            walk(nodes, roots.getInt(i), identity);
        }

        if (instances.isEmpty()) {
            throw new Exception("No mesh instances found in GLB scene");
        }
    }

    private void walk(JSONArray nodes, int nodeIndex, float[] parent) throws Exception {
        JSONObject node = nodes.getJSONObject(nodeIndex);
        float[] local = nodeMatrix(node);
        float[] world = new float[16];
        Matrix.multiplyMM(world, 0, parent, 0, local, 0);

        if (node.has("mesh")) {
            InstanceData instance = new InstanceData();
            instance.meshIndex = node.getInt("mesh");
            System.arraycopy(world, 0, instance.matrix, 0, 16);
            instances.add(instance);
        }

        JSONArray children = node.optJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.length(); i++) {
                walk(nodes, children.getInt(i), world);
            }
        }
    }

    private float[] nodeMatrix(JSONObject node) throws Exception {
        float[] matrix = new float[16];
        JSONArray directMatrix = node.optJSONArray("matrix");

        if (directMatrix != null) {
            for (int i = 0; i < 16; i++) {
                matrix[i] = (float) directMatrix.getDouble(i);
            }
            return matrix;
        }

        Matrix.setIdentityM(matrix, 0);

        JSONArray translation = node.optJSONArray("translation");
        if (translation != null) {
            Matrix.translateM(
                    matrix,
                    0,
                    (float) translation.getDouble(0),
                    (float) translation.getDouble(1),
                    (float) translation.getDouble(2));
        }

        JSONArray rotation = node.optJSONArray("rotation");
        if (rotation != null) {
            float x = (float) rotation.getDouble(0);
            float y = (float) rotation.getDouble(1);
            float z = (float) rotation.getDouble(2);
            float w = (float) rotation.getDouble(3);
            float[] r = quaternionMatrix(x, y, z, w);
            float[] result = new float[16];
            Matrix.multiplyMM(result, 0, matrix, 0, r, 0);
            matrix = result;
        }

        JSONArray scale = node.optJSONArray("scale");
        if (scale != null) {
            Matrix.scaleM(
                    matrix,
                    0,
                    (float) scale.getDouble(0),
                    (float) scale.getDouble(1),
                    (float) scale.getDouble(2));
        }

        return matrix;
    }

    private static float[] quaternionMatrix(float x, float y, float z, float w) {
        float[] m = new float[16];
        Matrix.setIdentityM(m, 0);

        float xx = x * x;
        float yy = y * y;
        float zz = z * z;
        float xy = x * y;
        float xz = x * z;
        float yz = y * z;
        float wx = w * x;
        float wy = w * y;
        float wz = w * z;

        m[0] = 1f - 2f * (yy + zz);
        m[1] = 2f * (xy + wz);
        m[2] = 2f * (xz - wy);

        m[4] = 2f * (xy - wz);
        m[5] = 1f - 2f * (xx + zz);
        m[6] = 2f * (yz + wx);

        m[8] = 2f * (xz + wy);
        m[9] = 2f * (yz - wx);
        m[10] = 1f - 2f * (xx + yy);

        return m;
    }

    private void createNormalization(float requestedSize) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (InstanceData instance : instances) {
            if (instance.meshIndex < 0 || instance.meshIndex >= meshes.size()) continue;

            MeshData mesh = meshes.get(instance.meshIndex);
            Accessor accessor = accessors[mesh.positionAccessor];
            if (accessor.min == null || accessor.max == null) continue;

            for (float x : new float[]{accessor.min[0], accessor.max[0]}) {
                for (float y : new float[]{accessor.min[1], accessor.max[1]}) {
                    for (float z : new float[]{accessor.min[2], accessor.max[2]}) {
                        float[] point = point(instance.matrix, x, y, z);
                        minX = Math.min(minX, point[0]);
                        minY = Math.min(minY, point[1]);
                        minZ = Math.min(minZ, point[2]);
                        maxX = Math.max(maxX, point[0]);
                        maxY = Math.max(maxY, point[1]);
                        maxZ = Math.max(maxZ, point[2]);
                    }
                }
            }
        }

        if (!Float.isFinite(minX) || !Float.isFinite(maxX)) {
            Matrix.setIdentityM(normalize, 0);
            return;
        }

        float dimension = Math.max(
                maxX - minX,
                Math.max(maxY - minY, maxZ - minZ));
        float size = requestedSize > 0.05f ? requestedSize : 0.55f;
        float scale = size / Math.max(dimension, 0.001f);
        float centerX = (minX + maxX) * 0.5f;
        float centerZ = (minZ + maxZ) * 0.5f;

        Matrix.setIdentityM(normalize, 0);
        Matrix.scaleM(normalize, 0, scale, scale, scale);
        Matrix.translateM(normalize, 0, -centerX, -minY, -centerZ);
    }

    private FloatBuffer floatAccessor(int accessorIndex, int components) throws Exception {
        Accessor accessor = accessors[accessorIndex];
        if (accessor.componentType != 5126) {
            throw new Exception("Accessor " + accessorIndex + " must use FLOAT");
        }

        BufferView view = views[accessor.bufferView];
        int stride = view.stride > 0 ? view.stride : components * 4;
        int base = view.offset + accessor.offset;

        ByteBuffer source = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN);
        FloatBuffer output = ByteBuffer
                .allocateDirect(accessor.count * components * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        for (int i = 0; i < accessor.count; i++) {
            for (int c = 0; c < components; c++) {
                int offset = base + i * stride + c * 4;
                if (offset + 4 > bin.length) {
                    throw new Exception("Accessor " + accessorIndex + " exceeds BIN chunk");
                }
                output.put(source.getFloat(offset));
            }
        }

        output.position(0);
        return output;
    }

    private static final class IndexData {
        final ByteBuffer buffer;
        final int glType;

        IndexData(ByteBuffer buffer, int glType) {
            this.buffer = buffer;
            this.glType = glType;
        }
    }

    /**
     * Preserve the GLB's legal unsigned index width instead of forcing every model
     * into 16-bit indices. Modern ARCore phones commonly support 32-bit element
     * indices through GL_OES_element_index_uint even on our stable ES2 context.
     */
    private IndexData indexAccessorData(int accessorIndex) throws Exception {
        Accessor accessor = accessors[accessorIndex];
        BufferView view = views[accessor.bufferView];

        int bytes;
        int glType;
        if (accessor.componentType == 5121) {
            bytes = 1;
            glType = GLES20.GL_UNSIGNED_BYTE;
        } else if (accessor.componentType == 5123) {
            bytes = 2;
            glType = GLES20.GL_UNSIGNED_SHORT;
        } else if (accessor.componentType == 5125) {
            bytes = 4;
            glType = GLES20.GL_UNSIGNED_INT;

            String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
            boolean supportsUint =
                    extensions != null && extensions.contains("GL_OES_element_index_uint");
            if (!supportsUint) {
                throw new Exception(
                        "This GLB uses 32-bit mesh indices, but this device's ES2 renderer "
                                + "does not expose GL_OES_element_index_uint. Optimize the GLB "
                                + "or split the mesh into smaller parts.");
            }
        } else {
            throw new Exception(
                    "Unsupported index componentType: " + accessor.componentType);
        }

        int stride = view.stride > 0 ? view.stride : bytes;
        int base = view.offset + accessor.offset;
        ByteBuffer source = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer output = ByteBuffer
                .allocateDirect(accessor.count * bytes)
                .order(ByteOrder.nativeOrder());

        for (int i = 0; i < accessor.count; i++) {
            int offset = base + i * stride;
            if (offset + bytes > bin.length) {
                throw new Exception("Index accessor exceeds BIN chunk");
            }

            if (accessor.componentType == 5121) {
                output.put(source.get(offset));
            } else if (accessor.componentType == 5123) {
                output.putShort(source.getShort(offset));
            } else {
                output.putInt(source.getInt(offset));
            }
        }

        output.position(0);
        return new IndexData(output, glType);
    }

    private static float[] floats(JSONArray array) throws Exception {
        if (array == null) return null;
        float[] result = new float[array.length()];
        for (int i = 0; i < result.length; i++) {
            result[i] = (float) array.getDouble(i);
        }
        return result;
    }

    private static float[] point(float[] matrix, float x, float y, float z) {
        float[] input = {x, y, z, 1f};
        float[] output = new float[4];
        Matrix.multiplyMV(output, 0, matrix, 0, input, 0);
        return new float[]{output[0], output[1], output[2]};
    }

    private static byte[] readAll(InputStream inputStream) throws Exception {
        try (InputStream input = inputStream;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }
}
