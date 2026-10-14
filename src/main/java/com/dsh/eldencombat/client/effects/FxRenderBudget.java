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
}
