package com.dsh.eldencombat.skeleton;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.dsh.eldencombat.core.geom.Box;
import org.joml.Vector3f;

/** CPU-only adapter; cached block positions and Minecraft top-origin UVs. */
public final class WeaponMeshData {
    private static final int STRIDE = 8;
    private final float[] vertices;
    private final int triangles;

    private WeaponMeshData(float[] vertices, int triangles) {
        this.vertices = vertices;
        this.triangles = triangles;
    }

    public int vertexCount() { return vertices.length / STRIDE; }
    public int triangleCount() { return triangles; }
    public float value(int vertex, int component) { return vertices[vertex * STRIDE + component]; }

    public void validateBounds(Box bounds) {
        for (int i = 0; i < vertexCount(); i++) {
            require(value(i, 0) >= bounds.minX() - .002 && value(i, 0) <= bounds.maxX() + .002
                    && value(i, 1) >= bounds.minY() - .002 && value(i, 1) <= bounds.maxY() + .002
                    && value(i, 2) >= bounds.minZ() - .002 && value(i, 2) <= bounds.maxZ() + .002,
                    "Mesh lies outside model bounds");
        }
    }

    public static WeaponMeshData parse(JsonObject root) {
        require(root.get("schemaVersion").getAsInt() == 1, "Unsupported mesh schema");
        require("root".equals(root.get("bone").getAsString()), "Mesh must use root bone");
        require("blocks".equals(root.get("units").getAsString()), "Mesh positions must be in blocks");
        String type = root.get("indexType").getAsString();
        require("uint16".equals(type) || "uint32".equals(type), "Unsupported index type");
        require(root.get("materialGroups").getAsInt() == 1, "Only one atlas material is supported");
        int count = root.get("vertexCount").getAsInt();
        int triangles = root.get("triangleCount").getAsInt();
        require(count > 0 && count <= 1_000_000 && triangles > 0 && triangles <= 1_000_000,
                "Invalid or excessive mesh counts");
        float[] positions = array(root.getAsJsonArray("positions"), count * 3);
        float[] normals = array(root.getAsJsonArray("normals"), count * 3);
        float[] uvs = array(root.getAsJsonArray("uvs"), count * 2);
        JsonArray indices = root.getAsJsonArray("indices");
        require(indices.size() == triangles * 3, "Index count mismatch");
        for (int i = 0; i < count; i++) {
            int n = i * 3;
            float length = normals[n] * normals[n] + normals[n + 1] * normals[n + 1] + normals[n + 2] * normals[n + 2];
            require(Math.abs(length - 1) < .002f, "Normal must be unit length");
            require(uvs[i * 2] >= 0 && uvs[i * 2] <= 1 && uvs[i * 2 + 1] >= 0 && uvs[i * 2 + 1] <= 1,
                    "UV outside atlas");
        }
        float[] output = new float[triangles * 4 * STRIDE];
        for (int t = 0; t < triangles; t++) {
            int[] ids = new int[3];
            for (int j = 0; j < 3; j++) {
                double number = indices.get(t * 3 + j).getAsDouble();
                require(Double.isFinite(number) && number == Math.rint(number) && number >= 0 && number < count,
                        "Invalid triangle index");
                require(!"uint16".equals(type) || number <= 65535, "uint16 index overflow");
                ids[j] = (int) number;
            }
            Vector3f a = point(positions, ids[0]), b = point(positions, ids[1]), c = point(positions, ids[2]);
            Vector3f cross = b.sub(a).cross(c.sub(a));
            require(cross.lengthSquared() > 1e-20f, "Degenerate triangle");
            require(cross.dot(point(normals, ids[0])) > 0, "Triangle winding opposes normal");
            // Vanilla entity and glint buffers consume QUADS. a,b,c,c preserves the source triangle.
            for (int j = 0; j < 4; j++) {
                int index = ids[Math.min(j, 2)], p = index * 3, uv = index * 2, out = (t * 4 + j) * STRIDE;
                System.arraycopy(positions, p, output, out, 3);
                output[out + 3] = uvs[uv];
                output[out + 4] = 1 - uvs[uv + 1];
                System.arraycopy(normals, p, output, out + 5, 3);
            }
        }
        return new WeaponMeshData(output, triangles);
    }

    /** Bake static cuboids once at reload, including rotations and per-face UVs. */
    public static WeaponMeshData bake(ModelData model) {
        SkeletonPose pose = SkeletonPose.rest(model);
        float[] output = new float[model.cubes.size() * 6 * 4 * STRIDE];
        int out = 0;
        for (ModelData.CubeSpec cube : model.cubes) {
            for (int face = 0; face < 6; face++) {
                float[] uv = CubeGeometry.faceUv(model, cube, model.material(model.materialForFace(cube, face)), face);
                Vector3f rotation = cube.effectiveRotation();
                Vector3f normal = pose.boneWorld(cube.bone())
                        .rotateXYZ((float) Math.toRadians(rotation.x), (float) Math.toRadians(rotation.y), (float) Math.toRadians(rotation.z))
                        .transformDirection(new Vector3f(CubeGeometry.FACE_NORMALS[face])).normalize();
                for (int slot : CubeGeometry.SUBMIT_ORDER) {
                    Vector3f p = pose.cubeCorner(cube, CubeGeometry.FACE_CORNERS[face][slot]);
                    output[out++] = p.x; output[out++] = p.y; output[out++] = p.z;
                    output[out++] = uv[slot * 2]; output[out++] = uv[slot * 2 + 1];
                    output[out++] = normal.x; output[out++] = normal.y; output[out++] = normal.z;
                }
            }
        }
        return new WeaponMeshData(output, model.cubes.size() * 12);
    }

    private static Vector3f point(float[] data, int i) {
        return new Vector3f(data[i * 3], data[i * 3 + 1], data[i * 3 + 2]);
    }

    private static float[] array(JsonArray input, int size) {
        require(input != null && input.size() == size, "Mesh array length mismatch");
        float[] result = new float[size];
        for (int i = 0; i < size; i++) {
            result[i] = input.get(i).getAsFloat();
            require(Float.isFinite(result[i]), "Non-finite mesh value");
        }
        return result;
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalArgumentException(message);
    }
}
