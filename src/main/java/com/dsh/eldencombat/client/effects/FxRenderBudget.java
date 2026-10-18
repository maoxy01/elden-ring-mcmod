package com.dsh.eldencombat.client.effects;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.effects.VfxFrameBudget;
import com.dsh.eldencombat.entity.skill.SkillEffectEntity;
import com.dsh.eldencombat.entity.skill.GoldenWaveEntity;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = EldenCombat.MODID, value = Dist.CLIENT)
public final class FxRenderBudget {
    static final VfxFrameBudget FRAME = new VfxFrameBudget();
    private static int debrisRemaining;
    static boolean pressure;
    private FxRenderBudget() {}
    @SubscribeEvent public static void begin(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        var mc = Minecraft.getInstance();
        int count = 0;
        if (mc.level != null) for (var entity : mc.level.entitiesForRendering()) {
            if ((entity instanceof SkillEffectEntity || entity instanceof GoldenWaveEntity)
                    && entity.position().distanceToSqr(event.getCamera().getPosition()) <= 128 * 128) count++;
        }
        FRAME.begin(count);pressure = count > 40;debrisRemaining = 32;
    }
    static int debris(int requested) {
        int admitted = Math.min(requested, debrisRemaining);debrisRemaining -= admitted;return admitted;
    }

    /**
     * 取证专用：{@code -D eldencombat.vfxForceCore=true} 时把本帧 LOD 强制成 CORE，
     * 用来实机验证"CORE 在同一路由内提交合法轮廓，而不是回落到旧盒体"。
     * 生产默认（属性未设）恒为 false，行为不变。
     */
    static boolean forcedCore() { return Boolean.getBoolean("eldencombat.vfxForceCore"); }

    /**
     * 取证专用：{@code -D eldencombat.vfxBudgetZero=true} 时主贴片/轮廓预算视为 0，
     * 用来实机验证"预算拒绝 → SUPPRESSED(NO_BUDGET) 且不回落 legacy"。
     * 生产默认恒为 false，不改 {@link com.dsh.eldencombat.effects.VfxFrameBudget} 的真实记账。
     */
    static boolean budgetZero() { return Boolean.getBoolean("eldencombat.vfxBudgetZero"); }
}
