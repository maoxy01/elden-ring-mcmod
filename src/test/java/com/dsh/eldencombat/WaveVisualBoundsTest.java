package com.dsh.eldencombat;

import com.dsh.eldencombat.client.effects.WaveVisualBounds;
import com.dsh.eldencombat.effects.FxArtwork;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 波客户端可视包围的纯逻辑回归：3.5 格火墙、非居中 anchor、地形上下界与前后插值都必须落在框内，
 * 且包围盒有界（不得靠无限 AABB 或关闭 culling 通过）。
 */
class WaveVisualBoundsTest {
    /** 现有火墙作者几何：高 3.5、anchorV=0.90625、offset 0、roll/scale 为 1。 */
    private static final FxArtwork FLAME=new FxArtwork(2.0f,3.5f,0.5f,0.90625f,0,0,0,false,List.of(0f),List.of(1f));

    private static AABB base(double y) {
        return WaveVisualBounds.of(0,y,0, 0,y,0, 0,1, 3.0,0, FLAME, new AABB(-1,y-1,-1,1,y+1,1));
    }

    @Test void flameHeightCoversSupportWindowAndAuthorVerticalRange() {
        // base.y=81、路径 y=81：支撑窗口 [-2.5,+1.5]，作者范围 bottom=-0.328125、top=3.171875。
        AABB box=base(81);
        assertTrue(box.minY<=81-2.5-0.5,"必须覆盖向下 2.5 格支撑射线窗口"+box);
        assertTrue(box.maxY>=81+1.5+3.171875-0.5,"必须覆盖向上 1.5 格支撑窗口与 3.5 格火墙上沿"+box);
        // 有界：不能出现无限/荒谬范围
        assertTrue(box.maxY-box.minY<20,"包围盒必须有限");
        assertTrue(box.maxX-box.minX<20,"包围盒必须有限");
    }

    @Test void nonCentredAnchorAndOffsetWideningAreRespected() {
        // anchorV=0.90625 时竖直方向实际覆盖 -0.328125..3.171875，而不是上下各半。
        AABB lower=WaveVisualBounds.of(0,81,0, 0,81,0, 0,1, 3,0,
                new FxArtwork(2f,3.5f,0.5f,0f,0,0,0,false,List.of(0f),List.of(1f)), null);
        assertTrue(lower.minY<=81-2.5,"anchorV=0 时贴地平面也必须覆盖支撑窗口下界");
        AABB offset=WaveVisualBounds.of(0,81,0, 0,81,0, 0,1, 3,0,
                new FxArtwork(2f,3.5f,0.5f,1f,0,1.5f,0,false,List.of(0f),List.of(1f)), null);
        assertTrue(offset.maxY>=81+1.5+1.5,"作者 offsetY=1.5 必须抬升包围盒上界");
    }

    @Test void slopeAndInterpolationBetweenBaseAndCurrentAreCovered() {
        // 上坡：base 在 y=81、当前位置在 y=85，两个位置都必须被覆盖。
        AABB slope=WaveVisualBounds.of(0,81,0, 4,85,12, 0,1, 3,12, FLAME, null);
        assertTrue(slope.minY<=81-3.0,"必须覆盖起点(低处)");
        assertTrue(slope.maxY>=85+1.5,"必须覆盖当前位置(高处)");
        assertTrue(slope.minZ<=0&&slope.maxZ>=12,"必须覆盖沿传播方向的 base 与当前位置");
    }

    @Test void lateralRadiusAndTravelSpanAreCovered() {
        AABB box=WaveVisualBounds.of(0,81,0, 0,81,10, 0,1, 3,10, FLAME, null);
        assertTrue(box.minX<=-3.5&&box.maxX>=3.5,"横向必须覆盖 lane 半宽 ±radius");
        assertTrue(box.minZ<=-0.5&&box.maxZ>=10.5,"纵向必须覆盖 base 与当前位置（含半径余量）");
    }

    @Test void zeroDirectionFallsBackToPositiveZWithoutNaN() {
        AABB box=WaveVisualBounds.of(5,81,5, 5,81,5, 0,0, 2,0, null, null);
        assertTrue(Double.isFinite(box.minX)&&Double.isFinite(box.maxX)&&Double.isFinite(box.minY)&&Double.isFinite(box.maxY));
        assertTrue(box.maxZ>box.minZ&&box.maxX>box.minX,"零方向也要给出有界的有效包围");
    }
}
