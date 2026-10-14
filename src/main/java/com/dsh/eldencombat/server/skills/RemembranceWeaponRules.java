package com.dsh.eldencombat.server.skills;

import com.dsh.eldencombat.EldenCombat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class RemembranceWeaponRules {
    private RemembranceWeaponRules() {}

    public static boolean matches(ServerPlayer player, RemembranceSkillSpec spec) {
        var resolved=AshBindings.resolve(player.getMainHandItem());
        return spec!=null&&resolved!=null&&resolved.id().equals(spec.id())&&SkillDriver.runtime(spec.id())!=null;
    }
}
