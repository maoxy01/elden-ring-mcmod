package com.dsh.eldencombat;

import com.dsh.eldencombat.effects.VfxCameraMath;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** VFX 参考相机：设计朝向与屏幕投影门禁的纯逻辑回归（不依赖客户端线程/窗口）。 */
class VfxCameraMathTest {
    private static final Vec3 EYE=Vec3.ZERO;
    /** 看向 +Z 的正交基：left=(-1,0,0) 使屏幕 X 向右为正。 */
    private static final Vector3f FORWARD=new Vector3f(0,0,1);
    private static final Vector3f LEFT=new Vector3f(-1,0,0);
    private static final Vector3f UP=new Vector3f(0,1,0);
    private static final double FOV=70.0, ASPECT=1.0;

    private static AABB box(double x0,double y0,double z0,double x1,double y1,double z1) {
        return new AABB(x0,y0,z0,x1,y1,z1);
    }

    @Test void aimFollowsTheFourCardinalDirectionsAndVerticalTargets() {
        assertArrayEquals(new float[]{0f,0f},VfxCameraMath.aimAt(EYE,new Vec3(0,0,10)),1e-3f);          // +Z 正前方
        assertArrayEquals(new float[]{-90f,0f},VfxCameraMath.aimAt(EYE,new Vec3(10,0,0)),1e-3f);        // +X
        assertEquals(180f,Math.abs(VfxCameraMath.aimAt(EYE,new Vec3(0,0,-10))[0]),1e-3f);               // -Z（±180 等价）
        assertArrayEquals(new float[]{90f,0f},VfxCameraMath.aimAt(EYE,new Vec3(-10,0,0)),1e-3f);        // -X
        assertTrue(VfxCameraMath.aimAt(EYE,new Vec3(0,5,10))[1]<-20f);                                  // 向上看：pitch 负
        assertTrue(VfxCameraMath.aimAt(EYE,new Vec3(0,-5,10))[1]>20f);                                  // 向下看：pitch 正
        // 正上方：水平分量为零，yaw 仍需有限且 pitch 夹到合法范围，绝不 NaN
        float[] straightUp=VfxCameraMath.aimAt(EYE,new Vec3(0,10,0));
        assertTrue(Float.isFinite(straightUp[0])&&Float.isFinite(straightUp[1]));
        assertEquals(-89f,straightUp[1],1e-3f);
    }

    @Test void coincidentTargetIsRejectedInsteadOfProducingNaN() {
        assertThrows(IllegalArgumentException.class,()->VfxCameraMath.aimAt(EYE,EYE));
        assertThrows(IllegalArgumentException.class,()->VfxCameraMath.aimAt(new Vec3(1,2,3),new Vec3(1,2,3)));
    }

    @Test void projectedBoundsIntersectTheScreenOnlyWhenTheTargetIsInFront() {
        // 眼位正前方的小盒子：八个角都落在屏幕内
        double[] front=VfxCameraMath.ndcExtent(EYE,FORWARD,LEFT,UP,box(-0.5,-0.5,5,0.5,0.5,6),FOV,ASPECT);
        assertTrue(VfxCameraMath.intersectsScreen(front));
        assertEquals(8,(int)front[5]);
        assertEquals(8,(int)front[6]);
        assertEquals(5.0,front[4],1e-6);

        // 背后的盒子：没有任何角在近平面前 → 不可见
        double[] behind=VfxCameraMath.ndcExtent(EYE,FORWARD,LEFT,UP,box(-0.5,-0.5,-6,0.5,0.5,-5),FOV,ASPECT);
        assertFalse(VfxCameraMath.intersectsScreen(behind));
        assertEquals(0,(int)behind[5]);

        // 侧后方 50 格的盒子：在近平面之前但完全出画
        double[] aside=VfxCameraMath.ndcExtent(EYE,FORWARD,LEFT,UP,box(50,0,5,51,1,6),FOV,ASPECT);
        assertTrue(aside[5]>0);
        assertFalse(VfxCameraMath.intersectsScreen(aside));

        // 负例相机语义：同一盒子，视线反向后必须判为不可见
        double[] reversed=VfxCameraMath.ndcExtent(EYE,new Vector3f(0,0,-1),new Vector3f(1,0,0),UP,box(-0.5,-0.5,5,0.5,0.5,6),FOV,ASPECT);
        assertFalse(VfxCameraMath.intersectsScreen(reversed));
    }

    @Test void screenCoverageIsSymmetricForLeftAndRightTargets() {
        double[] right=VfxCameraMath.ndcExtent(EYE,FORWARD,LEFT,UP,box(0.4,-0.2,5,0.6,0.2,5.1),FOV,ASPECT);
        double[] left=VfxCameraMath.ndcExtent(EYE,FORWARD,LEFT,UP,box(-0.6,-0.2,5,-0.4,0.2,5.1),FOV,ASPECT);
        // 基向量约定由引擎决定；本测例固定 left=(-1,0,0)，因此 +X 目标落在 NDC X 负侧、-X 落在正侧。
        // 只要求：两侧严格反号、范围等宽、都在屏幕内（换基向量时这条对称关系必须仍然成立）。
        assertTrue(right[0]<0&&right[2]<0,"+X 与 -X 的 NDC X 必须反号");
        assertTrue(left[0]>0&&left[2]>0,"+X 与 -X 的 NDC X 必须反号");
        assertEquals(right[2],-left[0],1e-6);
        assertEquals(right[0],-left[2],1e-6);
        assertTrue(VfxCameraMath.intersectsScreen(right)&&VfxCameraMath.intersectsScreen(left));
    }

    @Test void eyeInsideTheBoxIsReportedSoTheBackwardsControlCanBeSkipped() {
        AABB around=box(-2,-2,-2,2,2,2);
        assertTrue(VfxCameraMath.eyeInside(EYE,around));
        assertFalse(VfxCameraMath.eyeInside(new Vec3(5,0,0),around));
    }

    @Test void centreBehindTheEyeRejectsAStraddlingEffectVolume() {
        // 以施法者为中心、向前延伸的效果体积：中心已落在眼位后方，但仍有角点在近平面之前。
        AABB straddle=box(-1,-1,-19,1,1,1);
        double[] extent=VfxCameraMath.ndcExtent(EYE,FORWARD,LEFT,UP,straddle,FOV,ASPECT);
        assertEquals(8,extent.length,"投影结果必须带上包围盒中心深度，供严格判据使用");
        assertTrue(VfxCameraMath.intersectsScreen(extent));
        assertEquals(-9.0,extent[7],1e-6);
        assertFalse(VfxCameraMath.intersectsScreen(extent,true),"中心在眼位后方：负例严格判据必须判为不可见");

        // 中心在眼位前方的同一类体积：两种判据都算可见。
        AABB ahead=box(-1,-1,2,1,1,12);
        assertTrue(VfxCameraMath.intersectsScreen(VfxCameraMath.ndcExtent(EYE,FORWARD,LEFT,UP,ahead,FOV,ASPECT),true));

        // 背后的盒子没有角点在近平面之前：常规与严格判据都判为不可见。
        assertFalse(VfxCameraMath.intersectsScreen(VfxCameraMath.ndcExtent(EYE,FORWARD,LEFT,UP,box(-1,-1,-21,1,1,-19),FOV,ASPECT),true));
    }

    @Test void invalidProjectionAndAngleMathsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                ()->VfxCameraMath.ndcExtent(EYE,FORWARD,LEFT,UP,box(0,0,1,1,1,2),0,1));
        assertThrows(IllegalArgumentException.class,
                ()->VfxCameraMath.ndcExtent(EYE,FORWARD,LEFT,UP,box(0,0,1,1,1,2),70,0));
        assertEquals(0.0,VfxCameraMath.angleBetween(30f,10f,30f,10f),1e-6);
        assertEquals(180.0,VfxCameraMath.angleBetween(0f,0f,180f,0f),1e-6);
        assertFalse(VfxCameraMath.intersectsScreen(null));
    }

    /** 视锥相交（六半空间 p-vertex）：门禁的可见判据，比"角点投影"更能覆盖长条/包围镜头的大体积。 */
    private static boolean frustum(AABB box) {
        return VfxCameraMath.intersectsFrustum(EYE,FORWARD,LEFT,UP,box,FOV,ASPECT,VfxCameraMath.NEAR,VfxCameraMath.FAR);
    }

    @Test void frustumRejectsVolumesBehindOrBesideTheCamera() {
        assertFalse(frustum(box(-1,-1,-21,1,1,-19)),"整盒在相机背后：必须判为不可见");
        assertFalse(frustum(box(40,-1,2,42,1,4)),"整盒在视锥侧面之外：必须判为不可见");
        assertFalse(frustum(box(-1,-1,300,1,1,320)),"整盒超过远平面：必须判为不可见");
        assertFalse(frustum(box(-1,-1,VfxCameraMath.NEAR-0.01,1,1,VfxCameraMath.NEAR-0.005)),"整盒落在近平面之前：必须判为不可见");
        assertFalse(VfxCameraMath.intersectsFrustum(EYE,FORWARD,LEFT,UP,box(0,0,1,1,1,2),70,1,0,VfxCameraMath.FAR),"非法 near 直接拒绝");
        assertFalse(VfxCameraMath.intersectsFrustum(EYE,FORWARD,LEFT,UP,null,FOV,ASPECT,VfxCameraMath.NEAR,VfxCameraMath.FAR));
    }

    @Test void frustumAcceptsVolumesInFrontWrappingTheCameraAndLongCorridors() {
        assertTrue(frustum(box(-0.5,-0.5,2,0.5,0.5,3)),"正前方的普通体积");
        assertTrue(frustum(box(-1,-1,-1,1,1,1)),"眼位在盒内：近身裁剪不允许被当成不可见");
        assertTrue(frustum(box(-6,-1,-20,6,3,40)),"长条走廊包围镜头：角点全在屏幕外也必须算相交");
        // 大过整个画面的体积：角点投影都在屏幕外，但盒子与视锥确有交集。
        assertTrue(frustum(box(-40,-40,0.2,40,40,0.4)));
    }
}
