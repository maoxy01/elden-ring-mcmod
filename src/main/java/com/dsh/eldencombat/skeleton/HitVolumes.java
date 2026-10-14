package com.dsh.eldencombat.skeleton;

import com.dsh.eldencombat.core.geom.Box;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.List;

/**
 * 判定体采样：从服务端骨骼姿态取出木杖长轴胶囊、匕首短轴胶囊与黄金锤锤头盒。
 * 第一轮禁止把整个人体或整条尾巴当作伤害体。
 */
public final class HitVolumes {

    /** 胶囊判定体（模型空间，单位方块） */
    public record Capsule(Vector3d a, Vector3d b, double radius) {
    }

    private HitVolumes() {
    }

    /** 沿方块某个轴的两端中心生成胶囊（木杖、匕首这类长条判定体）。 */
    public static Capsule cubeAxisCapsule(SkeletonPose pose, String cubeName, double radius) {
        ModelData.CubeSpec cube = findCube(pose.model(), cubeName);
        Vector3f center = cube.center();
        // 取最长轴作为长轴
        Vector3f size = cube.size();
        Vector3f localA = new Vector3f(center);
        Vector3f localB = new Vector3f(center);
        if (size.y >= size.x && size.y >= size.z) {
            localA.y = cube.from().y;
            localB.y = cube.to().y;
        } else if (size.x >= size.z) {
            localA.x = cube.from().x;
            localB.x = cube.to().x;
        } else {
            localA.z = cube.from().z;
            localB.z = cube.to().z;
        }
        return new Capsule(toVec(pose, cube.bone(), localA), toVec(pose, cube.bone(), localB), radius);
    }

    /** 整个模型（或某件附属武器）的 AABB，可当盒判定体用。 */
    public static Box modelAabb(SkeletonPose pose) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (ModelData.CubeSpec cube : pose.model().cubes) {
            Box box = pose.cubeWorldBox(cube);
            minX = Math.min(minX, box.minX());
            minY = Math.min(minY, box.minY());
            minZ = Math.min(minZ, box.minZ());
            maxX = Math.max(maxX, box.maxX());
            maxY = Math.max(maxY, box.maxY());
            maxZ = Math.max(maxZ, box.maxZ());
        }
        if (minX > maxX) {
            return new Box(0, 0, 0, 0, 0, 0);
        }
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** 指定方块的 AABB（黄金锤锤头）。 */
    public static Box cubeBox(SkeletonPose pose, String cubeName) {
        return pose.cubeWorldBox(findCube(pose.model(), cubeName));
    }

    /** 骨骼末端方向上的点（用于武器判定体的端部采样）。 */
    public static Vector3d boneTip(SkeletonPose pose, String bone, double localY) {
        Matrix4f matrix = pose.boneWorld(bone);
        Vector3f point = new Vector3f(0, (float) localY, 0);
        Vector3f world = matrix.transformPosition(point);
        return new Vector3d(world.x, world.y, world.z);
    }

    private static Vector3d toVec(SkeletonPose pose, String bone, Vector3f modelCoords) {
        Vector3f world = pose.modelToWorld(bone, modelCoords.x, modelCoords.y, modelCoords.z);
        return new Vector3d(world.x, world.y, world.z);
    }

    private static ModelData.CubeSpec findCube(ModelData model, String name) {
        List<ModelData.CubeSpec> cubes = model.cubes;
        for (ModelData.CubeSpec cube : cubes) {
            if (cube.name().equals(name)) {
                return cube;
            }
        }
        throw new IllegalArgumentException("模型 " + model.id + " 缺少方块: " + name);
    }
}
