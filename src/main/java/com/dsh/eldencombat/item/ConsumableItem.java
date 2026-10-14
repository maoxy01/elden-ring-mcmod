package com.dsh.eldencombat.item;

import com.dsh.eldencombat.server.Consumables;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 药物（消耗品）：右键饮用，喝下后按数据包施加药水效果与治疗量。
 *
 * <p>和药瓶的区别：药瓶（{@code HealingFlaskItem}）接在战斗系统的 {@code DRINK} 意图上，
 * 有独立的起手/可打断时间线；药物走**原版右键使用**，更接近原版药水的手感。
 * 两者的加成数值都来自数据包，并且都吃护符的治疗加成。
 */
public final class ConsumableItem extends Item {

    public ConsumableItem(Properties properties) {
        super(properties.stacksTo(8));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 24;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide() && entity instanceof ServerPlayer player) {
            Consumables.consume(player, stack);
            stack.shrink(1);
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        var spec = Consumables.specFor(stack);
        if (spec.isNone()) {
            lines.add(Component.literal("药物数据缺失（data/eldencombat/consumables/）").withStyle(ChatFormatting.DARK_RED));
            return;
        }
        if (spec.heal() > 0) {
            lines.add(Component.literal("立即治疗 " + spec.heal()).withStyle(ChatFormatting.RED));
        }
        for (var effect : spec.effects()) {
            lines.add(Component.literal(effect.effectId() + " " + roman(effect.amplifier() + 1)
                    + "（" + effect.durationTicks() / 20 + " 秒）").withStyle(ChatFormatting.BLUE));
        }
        lines.add(Component.literal("右键饮用").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> String.valueOf(level);
        };
    }
}
