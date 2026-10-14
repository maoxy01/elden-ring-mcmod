package com.dsh.eldencombat.server;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.core.WeaponClass;
import com.dsh.eldencombat.core.WeaponProfile;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 武器识别：优先用 §5 的数值档（weight）判类别，物品标签作为回退。
 *
 * <p>历史上这里只认 {@code light_swords} / {@code great_swords} 两个标签，结果是
 * **33 件类型武器里有 31 件被判成 NONE**——`startAttack` 直接 return，等于不能普攻。
 * 现在按重量分档：轻档走轻型时序、重档走重型时序，再叠加每件武器自己的
 * reach / 削韧 / 重量系数，差异才真正落到手感上。
 */
public final class Weapons {

    public static final TagKey<Item> LIGHT_SWORDS = tag("light_swords");
    public static final TagKey<Item> GREAT_SWORDS = tag("great_swords");
    public static final TagKey<Item> SHIELDS = tag("shields");

    /**
     * 轻/重分界（方案 §5.3 的重量表）：
     * 3–5 档（直剑/曲剑/刀/刺剑/鞭）走轻型；5.5 以上（斧/槌/大剑/矛/戟/镰刀/特大）走重型。
     */
    public static final double HEAVY_WEIGHT_THRESHOLD = 5.5;

    private Weapons() {
    }

    private static TagKey<Item> tag(String name) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID, name));
    }

    public static WeaponClass classify(LivingEntity entity) {
        ItemStack main = entity.getMainHandItem();
        WeaponProfile profile = profileOf(main);
        if (profile != null) {
            // 弓弩与盾不参与近战分类（它们走原版的装填/格挡逻辑）
            if (!profile.isMeleeProfile()) {
                return WeaponClass.NONE;
            }
            return profile.weight() >= HEAVY_WEIGHT_THRESHOLD ? WeaponClass.GREAT_SWORD : WeaponClass.LIGHT_SWORD;
        }
        // 回退：原版武器与原版标签（测试载体）
        if (main.is(GREAT_SWORDS)) {
            return WeaponClass.GREAT_SWORD;
        }
        if (main.is(LIGHT_SWORDS)) {
            return WeaponClass.LIGHT_SWORD;
        }
        return WeaponClass.NONE;
    }

    /** 该物品栈的数值档（不是本模组武器则 null） */
    public static WeaponProfile profileOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return com.dsh.eldencombat.core.WeaponProfiles.of(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    public static boolean hasShield(LivingEntity entity) {
        return entity.getMainHandItem().is(SHIELDS) || entity.getOffhandItem().is(SHIELDS);
    }
}
