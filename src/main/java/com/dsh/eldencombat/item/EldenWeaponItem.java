package com.dsh.eldencombat.item;

import com.dsh.eldencombat.core.WeaponDefinition;
import com.dsh.eldencombat.core.WeaponState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 精细武器共用的物品类型。
 *
 * <p>刻意不继承 {@code SwordItem} / {@code AxeItem}：33 件不能都走原版工具语义
 * （横扫、伐木、附魔书限制等），伤害与攻速只作为属性修饰写在物品属性里，
 * 战斗行为由本模组的服务端判定 + 原版普攻提供。
 *
 * <p>弓弩与盾牌仍用原版 {@code BowItem} / {@code CrossbowItem} / {@code ShieldItem}，
 * 以便直接复用原版装填、弹药与格挡逻辑。
 *
 * <p>Tooltip 展示 §2 的成长状态（+N / 质变 / 当前伤害倍率）；数值本身不在物品属性里写死，
 * 而是命中结算时现算，避免 buff / 附魔 / 质变双重计算。
 */
public class EldenWeaponItem extends Item {

    public EldenWeaponItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        WeaponDefinition definition = WeaponStacks.definition(stack);
        if (definition == null) {
            return;
        }
        WeaponState state = WeaponStacks.state(stack);
        tooltip.add(Component.literal(String.format("强化 +%d / +%d", state.upgradeLevel(), definition.maxLevel()))
                .withStyle(state.isMaxed(definition) ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        if (!state.affinity().isNone()) {
            tooltip.add(Component.literal("质变：" + state.affinity().label()
                            + (state.affinity().hasCorrection() ? "" : "（无补正）"))
                    .withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.literal(String.format("伤害倍率 ×%.2f", WeaponStacks.damageMultiplier(
                stack, com.dsh.eldencombat.core.AttributeSet.EMPTY))).withStyle(ChatFormatting.DARK_GRAY));
        if (state.artLocked()) {
            tooltip.add(Component.literal("追忆武器：战灰不可替换").withStyle(ChatFormatting.DARK_PURPLE));
        }
        if (!definition.profile().recommendedAffinity().equals("NONE")) {
            tooltip.add(Component.literal("推荐质变：" + definition.profile().recommendedAffinity())
                    .withStyle(ChatFormatting.DARK_GREEN));
        }
    }
}
