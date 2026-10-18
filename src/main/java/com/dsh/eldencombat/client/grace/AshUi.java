package com.dsh.eldencombat.client.grace;

import com.dsh.eldencombat.network.GraceMenuStatePayload;
import com.dsh.eldencombat.network.GraceMenuActionPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Display-only interpretation of the existing server snapshot; server validation remains final. */
public final class AshUi {
    private AshUi() {}

    public static String nameKey(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        return parsed == null ? id : "ash." + parsed.getNamespace() + "." + parsed.getPath().replace('/', '.');
    }

    public static Component name(String id) {
        return Component.translatableWithFallback(nameKey(id), id);
    }

    public static boolean canApply(GraceMenuStatePayload.Ash ash, String heldRejection) {
        return heldRejection.isEmpty() && ash.implemented() && ash.owned() && ash.compatible() && !ash.bound();
    }

    public static int action(String boundAsh) {
        return boundAsh.isEmpty() ? GraceMenuActionPayload.BIND : GraceMenuActionPayload.REPLACE;
    }

    public static String status(GraceMenuStatePayload.Ash ash) {
        if (!ash.implemented()) return "未实现";
        if (!ash.owned()) return "未获得";
        if (ash.bound()) return "已绑定";
        return ash.compatible() ? "可装配" : "武器不匹配";
    }

    public static Component label(GraceMenuStatePayload.Ash ash) {
        return name(ash.id()).copy().append(" · " + status(ash));
    }
}
