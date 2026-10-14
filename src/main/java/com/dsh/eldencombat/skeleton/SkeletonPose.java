package com.dsh.eldencombat.skeleton;

import com.dsh.eldencombat.core.geom.Box;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 计算好的骨架姿态。
 *
 * <p>矩阵约定（与交接说明一致）：
 * <ul>
 *   <li>骨骼局部平移 = (pivot - parent.pivot) / 16，根部 = pivot / 16。</li>
 *   <li>W_bone = W_parent · T(局部平移) · R(静态旋转 + 动画增量，逐轴相加，XYZ 欧拉顺序)。</li>
 *   <li>A_bone = W_bone · T(-pivot / 16)，把"建模坐标 / 16"直接变换到模型空间（Y 向上、朝向 -Z、原点在脚底）；
 *       因为建模坐标的原点在模型原点而不是骨骼原点。</li>
 * </ul>
 * 旋转不要逐级累加世界角度，只做局部旋转再串矩阵。
 */
public final class SkeletonPose {

    private final ModelData model;
    private final Matrix4f[] boneWorld;
    private final Matrix4f[] boneFromModel;
    private final Map<String, Integer> index = new HashMap<>();

    private SkeletonPose(ModelData model, Matrix4f[] boneWorld, Matrix4f[] boneFromModel) {
        this.model = model;
        this.boneWorld = boneWorld;
        this.boneFromModel = boneFromModel;
        for (int i = 0; i < model.bones.size(); i++) {
            index.put(model.bones.get(i).name(), i);
        }
    }

    /** 静态姿态（无动画增量） */
    public static SkeletonPose rest(ModelData model) {
        return of(model, Map.of());
    }

    /**
     * @param animationDelta 骨骼 -> 动画旋转增量（度，逐轴加到静态旋转上）；缺失轨道为零增量
     */
    public static SkeletonPose of(ModelData model, Map<String, Vector3f> animationDelta) {
        return of(model, animationDelta, Map.of());
    }

    public static SkeletonPose of(ModelData model, Map<String, Vector3f> animationDelta, Map<String, Vector3f> positionDelta) {
        int count = model.bones.size();
        Matrix4f[] world = new Matrix4f[count];
        Matrix4f[] fromModel = new Matrix4f[count];
        float unit = model.unitsPerBlock;

        for (int i = 0; i < count; i++) {
            ModelData.BoneSpec bone = model.bones.get(i);
            Matrix4f matrix = bone.parent() == null ? new Matrix4f() : new Matrix4f(world[model.boneIndex(bone.parent())]);
            Vector3f parentPivot = bone.parent() == null ? new Vector3f() : model.bone(bone.parent()).pivot();
            Vector3f offset = positionDelta.getOrDefault(bone.name(), new Vector3f());
            matrix.translate(
                    (bone.pivot().x - parentPivot.x + offset.x) / unit,
                    (bone.pivot().y - parentPivot.y + offset.y) / unit,
                    (bone.pivot().z - parentPivot.z + offset.z) / unit);

            Vector3f delta = animationDelta.get(bone.name());
            float rx = bone.rotation().x + (delta == null ? 0f : delta.x);
            float ry = bone.rotation().y + (delta == null ? 0f : delta.y);
            float rz = bone.rotation().z + (delta == null ? 0f : delta.z);
            // XYZ 欧拉顺序：R = Rx · Ry · Rz（JOML 的 rotateX/Y/Z 为右乘）
            matrix.rotateX((float) Math.toRadians(rx));
            matrix.rotateY((float) Math.toRadians(ry));
            matrix.rotateZ((float) Math.toRadians(rz));

            world[i] = matrix;
            fromModel[i] = new Matrix4f(matrix).translate(-bone.pivot().x / unit, -bone.pivot().y / unit, -bone.pivot().z / unit);
        }
        return new SkeletonPose(model, world, fromModel);
    }

    public ModelData model() {
        return model;
    }

    private int indexOf(String bone) {
        Integer i = index.get(bone);
        if (i == null) {
            throw new IllegalArgumentException("模型 " + model.id + " 缺少骨骼: " + bone);
        }
        return i;
    }

    /** W_bone：该骨骼原点在世界（模型空间）中的位置与朝向 */
    public Matrix4f boneWorld(String bone) {
        return new Matrix4f(boneWorld[indexOf(bone)]);
    }

    /** 骨骼原点在模型空间中的坐标（单位方块，原点在脚底） */
    public Vector3f boneOrigin(String bone) {
        return boneWorld[indexOf(bone)].getTranslation(new Vector3f());
    }

    /** 建模坐标（像素）-> 模型空间坐标（方块） */
    public Vector3f modelToWorld(String bone, float x, float y, float z) {
        Vector3f point = new Vector3f(x / model.unitsPerBlock, y / model.unitsPerBlock, z / model.unitsPerBlock);
        return boneFromModel[indexOf(bone)].transformPosition(point);
    }

    /** 把本模型（根原点为握持点）挂到插槽骨骼矩阵下：p = W_socket · A_local · v */
    public Matrix4f socketWorld(String socketBone) {
        return boneWorld(socketBone);
    }

    /** 某个方块在模型空间中的 AABB（用于锤头等盒状判定） */
    public Box cubeWorldBox(ModelData.CubeSpec cube) {
        Matrix4f matrix = boneFromModel[indexOf(cube.bone())];
        Vector3f rotation = cube.effectiveRotation();
        Vector3f pivot = cube.effectivePivot();
        Matrix4f local = new Matrix4f()
                .translate(pivot.x, pivot.y, pivot.z)
                .rotateX((float) Math.toRadians(rotation.x))
                .rotateY((float) Math.toRadians(rotation.y))
                .rotateZ((float) Math.toRadians(rotation.z))
                .translate(-pivot.x, -pivot.y, -pivot.z);

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        List<Vector3f> corners = cube.corners();
        for (Vector3f corner : corners) {
            Vector3f localPoint = local.transformPosition(new Vector3f(corner));
            Vector3f world = matrix.transformPosition(new Vector3f(localPoint.x / model.unitsPerBlock,
                    localPoint.y / model.unitsPerBlock, localPoint.z / model.unitsPerBlock));
            minX = Math.min(minX, world.x);
            minY = Math.min(minY, world.y);
            minZ = Math.min(minZ, world.z);
            maxX = Math.max(maxX, world.x);
            maxY = Math.max(maxY, world.y);
            maxZ = Math.max(maxZ, world.z);
        }
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** 方块角点在模型空间中的位置（渲染器用） */
    public Vector3f cubeCorner(ModelData.CubeSpec cube, int cornerIndex) {
        Vector3f rotation = cube.effectiveRotation();
        Vector3f pivot = cube.effectivePivot();
        Vector3f raw = new Vector3f(
                (cornerIndex & 1) == 0 ? cube.from().x : cube.to().x,
                (cornerIndex & 2) == 0 ? cube.from().y : cube.to().y,
                (cornerIndex & 4) == 0 ? cube.from().z : cube.to().z);
        Matrix4f local = new Matrix4f()
                .translate(pivot.x, pivot.y, pivot.z)
                .rotateX((float) Math.toRadians(rotation.x))
                .rotateY((float) Math.toRadians(rotation.y))
                .rotateZ((float) Math.toRadians(rotation.z))
                .translate(-pivot.x, -pivot.y, -pivot.z);
        Vector3f localPoint = local.transformPosition(raw);
        return boneFromModel[indexOf(cube.bone())].transformPosition(new Vector3f(
                localPoint.x / model.unitsPerBlock, localPoint.y / model.unitsPerBlock, localPoint.z / model.unitsPerBlock));
    }

    /** 把另一个模型（如黄金武器，根原点为握持点）的骨骼矩阵接到本模型的插槽上 */
    public static Matrix4f attach(SkeletonPose host, String socketBone, Matrix4f accessoryFromModel) {
        return host.boneWorld(socketBone).mul(accessoryFromModel, new Matrix4f());
    }

    /**
     * 本姿态（如黄金武器，根原点为握持点）挂到宿主模型的插槽骨骼下，
     * 得到一份已经在模型空间中的姿态，可直接用于判定体与渲染。
     */
    public SkeletonPose attachTo(SkeletonPose host, String socketBone) {
        return attachTo(host.boneWorld(socketBone));
    }

    public SkeletonPose attachTo(Matrix4f socket) {
        Matrix4f[] world = new Matrix4f[boneWorld.length];
        Matrix4f[] fromModel = new Matrix4f[boneFromModel.length];
        for (int i = 0; i < boneWorld.length; i++) {
            world[i] = socket.mul(boneWorld[i], new Matrix4f());
            fromModel[i] = socket.mul(boneFromModel[i], new Matrix4f());
        }
        return new SkeletonPose(model, world, fromModel);
    }

    public SkeletonPose transformed(Matrix4f matrix) { return attachTo(matrix); }
}
