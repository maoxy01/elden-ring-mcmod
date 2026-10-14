package com.dsh.eldencombat.item;

import com.dsh.eldencombat.core.AttributeSet;
import com.dsh.eldencombat.core.WeaponDefinition;
import com.dsh.eldencombat.core.WeaponDefinitions;
import com.dsh.eldencombat.core.WeaponState;
import com.dsh.eldencombat.registry.ModDataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * 物品栈 ↔ 武器定义/成长状态 的读写（§2 的"这一层"）。
 *
 * <p>集中在一处的理由：组件读取、id 归一化、非法值夹紧这三件事任何一个写漏，
 * 都会出现"读到的等级和存档不一致"或"某件武器绕过上限"。
 */
public final class WeaponStacks {

    private WeaponStacks() {
    }

    /** 该物品栈对应的武器定义；不是本模组的武器返回 null。 */
    public static WeaponDefinition definition(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        var id=BuiltInRegistries.ITEM.getKey(stack.getItem());
        return "eldencombat".equals(id.getNamespace())?WeaponDefinitions.of(id.getPath()):null;
    }

    /** 当前成长状态；没有组件时返回该武器的初始状态（并夹紧到合法范围）。 */
    public static WeaponState state(ItemStack stack) {
        WeaponDefinition definition = definition(stack);
        if (definition == null) {
            return WeaponState.FRESH;
        }
        WeaponState raw = stack.getOrDefault(ModDataComponents.WEAPON_STATE.get(), definition.fresh());
        return raw.sanitized(definition);
    }

    /** 写入成长状态（先夹紧再写，保证物品上的数据永远合法）。 */
    public static void setState(ItemStack stack, WeaponState state) {
        WeaponDefinition definition = definition(stack);
        if (definition == null || state == null) {
            return;
        }
        stack.set(ModDataComponents.WEAPON_STATE.get(), state.sanitized(definition));
    }

    /**
     * 相对"全新武器"的伤害倍率：{@code damageAt(state, attrs) / baseDamage}。
     *
     * <p>战斗结算乘这个倍率（而不是把最终伤害写进物品属性）——否则 buff / 附魔 / 质变会双重计算。
     * 全新且无属性点时正好是 1.0。
     */
    public static double damageMultiplier(ItemStack stack, AttributeSet attributes) {
        WeaponDefinition definition = definition(stack);
        if (definition == null) {
            return 1.0;
        }
        WeaponState state = state(stack);
        return definition.damageAt(state, attributes) / definition.baseDamage();
    }

    /** 诊断文本（命令 / tooltip 共用） */
    public static String describe(ItemStack stack) {
        WeaponDefinition definition = definition(stack);
        if (definition == null) {
            return "不是本模组武器";
        }
        return state(stack).describe(definition) + String.format(" 伤害倍率×%.2f", damageMultiplier(stack, AttributeSet.EMPTY));
    }
}
