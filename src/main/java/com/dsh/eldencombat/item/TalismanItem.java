package com.dsh.eldencombat.item;

import com.dsh.eldencombat.core.TalismanIds;
import com.dsh.eldencombat.core.TalismanSpec;
import com.dsh.eldencombat.server.TalismanEquipment;
import com.dsh.eldencombat.server.Talismans;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 护符：右键装备到护符栏（真实 ItemStack 容器），栏满时再右键同一件即卸下。
 *
 * <p>护符不消耗：装备时把背包里**这一件原样**放进护符栏（附魔/自定义名/组件都保留），
 * 卸下/换下时退回背包。具体数值由 {@link Talismans} 从数据包读取并**只在服务端**应用。
 *
 * <p>§3.5：tooltip 只读**服务端同步过来**的展示数据（{@link com.dsh.eldencombat.client.TalismanClientData}），
 * 不再依赖只在服务端存在的静态 specs 表——远程客户端因此不再显示"数据缺失"。
 * 这份数据只用于显示，不参与任何结算。
 */
public final class TalismanItem extends Item {

    public TalismanItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer server)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        TalismanEquipment.Status result = TalismanEquipment.equipFromHand(server, hand);
        return switch (result) {
            case EQUIPPED, UNEQUIPPED -> InteractionResultHolder.success(stack);
            case BUSY, EMPTY, REJECTED, FULL, INVENTORY_FULL, NOT_EQUIPPED ->
                    InteractionResultHolder.fail(stack);
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        String id = TalismanIds.normalize(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        TalismanSpec spec = com.dsh.eldencombat.client.TalismanClientData.spec(id);
        if (spec.isNone()) {
            lines.add(Component.literal("护符数据未同步（服务端尚未下发，或该护符没有数据）")
                    .withStyle(ChatFormatting.DARK_RED));
            return;
        }
        lines.add(Component.literal(spec.displayName()).withStyle(ChatFormatting.GRAY));
        if (spec.maxFp() != 0) lines.add(Component.literal("FP 上限 +" + (int) spec.maxFp()).withStyle(ChatFormatting.BLUE));
        if (spec.staminaRegen() != 0) lines.add(Component.literal("精力恢复 " + percent(spec.staminaRegen())).withStyle(ChatFormatting.GREEN));
        if (spec.damage() != 0) lines.add(Component.literal("伤害 " + percent(spec.damage())).withStyle(ChatFormatting.RED));
        if (spec.damageReduction() != 0) lines.add(Component.literal("受伤 " + percent(-spec.damageReduction())).withStyle(ChatFormatting.GOLD));
        if (spec.heal() != 0) lines.add(Component.literal("治疗量 +" + spec.heal()).withStyle(ChatFormatting.LIGHT_PURPLE));
        var effects = spec.effects();
        if (effects.skillDamage() > 0) lines.add(Component.literal("战技直接伤害 " + percent(effects.skillDamage()))
                .withStyle(ChatFormatting.RED));
        if (effects.physicalReduction() > 0) lines.add(Component.literal("物理减伤：PvE "
                + percent(effects.physicalReduction()) + " / PvP " + percent(effects.pvpPhysicalReduction()))
                .withStyle(ChatFormatting.GOLD));
        if (effects.staminaRegenPerSecond() > 0) lines.add(Component.literal(String.format("精力恢复 +%.2f/秒（MC单位）",
                effects.staminaRegenPerSecond())).withStyle(ChatFormatting.GREEN));
        if (effects.strength() > 0 || effects.dexterity() > 0) lines.add(Component.literal("力量 +"
                + effects.strength() + " / 灵巧 +" + effects.dexterity()).withStyle(ChatFormatting.RED));
        if (effects.maxHealth() > 0 || effects.maxStamina() > 0) lines.add(Component.literal("生命上限 +"
                + effects.maxHealth() + " / 精力上限 +" + effects.maxStamina() + "（MC适配）").withStyle(ChatFormatting.GREEN));
        if (effects.attackDamageTaken() > 0) lines.add(Component.literal("受到攻击伤害 "
                + percent(effects.attackDamageTaken())).withStyle(ChatFormatting.DARK_RED));
        lines.add(Component.literal("右键装备（第一个空护符槽）/ 栏满时再右键卸下").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String percent(float ratio) {
        return String.format("%+.0f%%", ratio * 100f);
    }
}
