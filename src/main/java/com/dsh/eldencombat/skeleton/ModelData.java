package com.dsh.eldencombat.skeleton;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目自定义的方块骨架模型数据（schemaVersion = 1）。
 * 这不是 Minecraft 原版实体 JSON，需要一个小的加载器；语义与 art/tooling/viewer.mjs 保持一致。
 *
 * <ul>
 *   <li>坐标右手系，Y 向上，模型朝向 -Z，unitsPerBlock = 16，根部原点在脚底。</li>
 *   <li>骨骼 pivot / 方块坐标都是未施加父级旋转的建模坐标。</li>
 *   <li>旋转为度数、XYZ 欧拉顺序（等价于 three.js Euler 'XYZ'）。</li>
 * </ul>
 */
public final class ModelData {

    public record BoneSpec(String name, String parent, Vector3f pivot, Vector3f rotation) {
    }

    public record CubeSpec(String name, String bone, Vector3f from, Vector3f to, String material,
                           Vector3f pivot, Vector3f rotation, boolean hasPivot, boolean hasRotation) {

        public Vector3f size() {
            return new Vector3f(to).sub(from);
        }

        public Vector3f center() {
            return new Vector3f(from).add(to).mul(0.5f);
        }

        /** 旋转枢轴：无 cube pivot 时使用 from */
        public Vector3f effectivePivot() {
            return hasPivot ? pivot : from;
        }

        public Vector3f effectiveRotation() {
            return hasRotation ? rotation : new Vector3f();
        }

        /** 8 个角点（建模坐标） */
        public List<Vector3f> corners() {
            List<Vector3f> corners = new ArrayList<>(8);
            for (int i = 0; i < 8; i++) {
                corners.add(new Vector3f(
                        (i & 1) == 0 ? from.x : to.x,
                        (i & 2) == 0 ? from.y : to.y,
                        (i & 4) == 0 ? from.z : to.z));
            }
            return corners;
        }
    }

    /** 贴图矩形（PNG 顶部原点的像素坐标） */
    public record MaterialSpec(String name, int u0, int v0, int u1, int v1, int colorRgb, boolean emissive) {
    }

    public final String id;
    public final int unitsPerBlock;
    public final int textureWidth;
    public final int textureHeight;
    public final String texture;
    public final List<BoneSpec> bones;
    public final List<CubeSpec> cubes;
    public final Map<String, MaterialSpec> materials;
    /** 逐方块逐面 UV（cube 名 -> 6 面 -> [u0, v0, u1, v1]，图集像素坐标，可为浮点）。 */
    private final Map<String, float[][]> faceUVs;
    private final Map<String, Integer> boneIndex = new LinkedHashMap<>();

    private ModelData(String id, int unitsPerBlock, int textureWidth, int textureHeight, String texture,
                      List<BoneSpec> bones, List<CubeSpec> cubes, Map<String, MaterialSpec> materials,
                      Map<String, float[][]> faceUVs) {
        this.id = id;
        this.unitsPerBlock = unitsPerBlock;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.texture = texture;
        this.bones = List.copyOf(bones);
        this.cubes = List.copyOf(cubes);
        this.materials = Map.copyOf(materials);
        this.faceUVs = Map.copyOf(faceUVs);
        for (int i = 0; i < this.bones.size(); i++) {
            boneIndex.put(this.bones.get(i).name(), i);
        }
    }

    public static ModelData parse(JsonObject root) {
        int schema = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 1;
        if (schema != 1) {
            throw new IllegalStateException("不支持的模型 schemaVersion: " + schema);
        }
        String id = root.get("id").getAsString();
        int units = root.has("unitsPerBlock") ? root.get("unitsPerBlock").getAsInt() : 16;
        JsonArray size = root.getAsJsonArray("textureSize");
        int texWidth = size.get(0).getAsInt();
        int texHeight = size.get(1).getAsInt();
        String texture = root.has("texture") ? root.get("texture").getAsString() : "";

        List<BoneSpec> bones = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            bones.add(new BoneSpec(
                    bone.get("name").getAsString(),
                    bone.get("parent").isJsonNull() ? null : bone.get("parent").getAsString(),
                    vector(bone.getAsJsonArray("pivot")),
                    bone.has("rotation") ? vector(bone.getAsJsonArray("rotation")) : new Vector3f()));
        }

        List<CubeSpec> cubes = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("cubes")) {
            JsonObject cube = element.getAsJsonObject();
            cubes.add(new CubeSpec(
                    cube.get("name").getAsString(),
                    cube.get("bone").getAsString(),
                    vector(cube.getAsJsonArray("from")),
                    vector(cube.getAsJsonArray("to")),
                    cube.get("material").getAsString(),
                    cube.has("pivot") ? vector(cube.getAsJsonArray("pivot")) : new Vector3f(),
                    cube.has("rotation") ? vector(cube.getAsJsonArray("rotation")) : new Vector3f(),
                    cube.has("pivot"),
                    cube.has("rotation")));
        }

        Map<String, MaterialSpec> materials = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("materials").entrySet()) {
            JsonObject material = entry.getValue().getAsJsonObject();
            JsonArray uv = material.getAsJsonArray("uv");
            int rgb = 0xFFFFFF;
            if (material.has("color")) {
                JsonArray color = material.getAsJsonArray("color");
                rgb = (color.get(0).getAsInt() << 16) | (color.get(1).getAsInt() << 8) | color.get(2).getAsInt();
            }
            materials.put(entry.getKey(), new MaterialSpec(entry.getKey(),
                    uv.get(0).getAsInt(), uv.get(1).getAsInt(), uv.get(2).getAsInt(), uv.get(3).getAsInt(),
                    rgb, material.has("emissive") && material.get("emissive").getAsBoolean()));
        }

        return new ModelData(id, units, texWidth, texHeight, texture, bones, cubes, materials,
                parseFaceUVs(root, cubes, texWidth, texHeight));
    }

    /** 面序与 {@code CubeGeometry.FACE_CORNERS} 一致：+X, -X, +Y, -Y, +Z, -Z。 */
    private static final String[] FACE_KEYS = {"east", "west", "up", "down", "south", "north"};

    private static Map<String, float[][]> parseFaceUVs(JsonObject root, List<CubeSpec> cubes, int texWidth, int texHeight) {
        Map<String, float[][]> result = new LinkedHashMap<>();
        if (!root.has("faceUVs")) {
            return result;
        }
        JsonObject all = root.getAsJsonObject("faceUVs");
        for (Map.Entry<String, JsonElement> element : all.entrySet()) {
            JsonObject faces = element.getValue().getAsJsonObject();
            float[][] perFace = new float[6][];
            for (int face = 0; face < 6; face++) {
                if (!faces.has(FACE_KEYS[face])) {
                    continue;
                }
                JsonArray rect = faces.getAsJsonArray(FACE_KEYS[face]);
                if (rect.size() != 4) {
                    continue;
                }
                float[] uv = new float[4];
                boolean ok = true;
                for (int i = 0; i < 4; i++) {
                    float value = rect.get(i).getAsFloat();
                    if (!Float.isFinite(value)) {
                        ok = false;
                        break;
                    }
                    uv[i] = value;
                }
                if (!ok) {
                    continue;
                }
                // 面必须落在图集内且是正向矩形，否则退回材质矩形，不能用负宽度贴图。
                float u0 = Math.min(uv[0], uv[2]);
                float u1 = Math.max(uv[0], uv[2]);
                float v0 = Math.min(uv[1], uv[3]);
                float v1 = Math.max(uv[1], uv[3]);
                if (u0 < 0 || v0 < 0 || u1 > texWidth || v1 > texHeight || u1 - u0 <= 0 || v1 - v0 <= 0) {
                    continue;
                }
                perFace[face] = new float[]{u0, v0, u1, v1};
            }
            result.put(element.getKey(), perFace);
        }
        return result;
    }

    /** 逐面 UV（TL, TR, BL, BR 展开成 8 个分量）；没有逐面数据时返回 null，由调用方退回材质矩形。 */
    public float[] faceUvFor(String cubeName, int face) {
        float[][] perFace = faceUVs.get(cubeName);
        if (perFace == null || perFace[face] == null) {
            return null;
        }
        // rect = [u0, v0, u1, v1] -> 按各自 textureSize 归一化，半像素内缩防渗色。
        float[] rect = perFace[face];
        float insetU = Math.min(.5f, (rect[2] - rect[0]) / 3);
        float insetV = Math.min(.5f, (rect[3] - rect[1]) / 3);
        float u0 = (rect[0] + insetU) / textureWidth;
        float u1 = (rect[2] - insetU) / textureWidth;
        float v0 = (rect[1] + insetV) / textureHeight;
        float v1 = (rect[3] - insetV) / textureHeight;
        // 极窄面内缩后可能翻转，钳回正向，不允许负宽度。
        if (u1 < u0) {
            u1 = u0;
        }
        if (v1 < v0) {
            v1 = v0;
        }
        // TL, TR, BL, BR
        return new float[]{u0, v0, u1, v0, u0, v1, u1, v1};
    }

    private static Vector3f vector(JsonArray array) {
        return new Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
    }

    public int boneIndex(String name) {
        Integer index = boneIndex.get(name);
        if (index == null) {
            throw new IllegalArgumentException("模型 " + id + " 缺少骨骼: " + name);
        }
        return index;
    }

    public BoneSpec bone(String name) {
        return bones.get(boneIndex(name));
    }

    public boolean hasBone(String name) {
        return boneIndex.containsKey(name);
    }

    public MaterialSpec material(String name) {
        MaterialSpec spec = materials.get(name);
        if (spec == null) {
            throw new IllegalArgumentException("模型 " + id + " 缺少材质: " + name);
        }
        return spec;
    }

    /** head 方块的 north / -Z 面使用 face 材质，这是 schema v1 的约定。 */
    public String materialForFace(CubeSpec cube, int faceIndex) {
        if ("eldencombat:margit".equals(id) && "head".equals(cube.name()) && faceIndex == FaceIndex.NORTH_Z) {
            return "face";
        }
        return cube.material();
    }

    /** 方块六面顺序与 three.js BoxGeometry 一致：+X, -X, +Y, -Y, +Z, -Z。 */
    public static final class FaceIndex {
        public static final int EAST_X = 0;
        public static final int WEST_X = 1;
        public static final int UP_Y = 2;
        public static final int DOWN_Y = 3;
        public static final int SOUTH_Z = 4;
        public static final int NORTH_Z = 5;

        private FaceIndex() {
        }
    }
}
