package com.dsh.eldencombat.registry;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.entity.MargitEntity;
import com.dsh.eldencombat.entity.skill.GoldenWaveEntity;
import com.dsh.eldencombat.entity.skill.SkillEffectEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, EldenCombat.MODID);

    /** 常规实体碰撞盒 1.35 x 3.1，长杖/角/尾巴/黄金锤都不进碰撞盒。 */
    public static final DeferredHolder<EntityType<?>, EntityType<MargitEntity>> MARGIT = ENTITIES.register("margit",
            () -> EntityType.Builder.of(MargitEntity::new, MobCategory.MONSTER)
                    .sized(1.35f, 3.1f)
                    .clientTrackingRange(10)
                    .build("margit"));

    /**
     * 战技地面波命中体（黄金波动 / 掠夺火焰共用）。
     * MISC 分类、不落盘、不参与自然生成；只有技能在服务端生成它。
     */
    public static final DeferredHolder<EntityType<?>, EntityType<GoldenWaveEntity>> GOLDEN_WAVE =
            ENTITIES.register("golden_wave", () -> EntityType.Builder.of(GoldenWaveEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .noSave()
                    .build("golden_wave"));

    public static final DeferredHolder<EntityType<?>, EntityType<SkillEffectEntity>> SKILL_EFFECT =
            ENTITIES.register("skill_effect", () -> EntityType.Builder.of(SkillEffectEntity::new, MobCategory.MISC)
                    .sized(.1f, .1f).clientTrackingRange(8).updateInterval(1).noSave().build("skill_effect"));

    private ModEntities() {
    }
}
