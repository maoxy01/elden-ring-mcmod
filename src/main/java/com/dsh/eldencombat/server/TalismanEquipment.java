package com.dsh.eldencombat.server;

import com.dsh.eldencombat.core.TalismanAggregate;
import com.dsh.eldencombat.core.TalismanRules;
import com.dsh.eldencombat.core.TalismanSpec;
import com.dsh.eldencombat.registry.ModAttachments;
import com.dsh.eldencombat.server.skills.SkillDriver;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 护符装备的服务端入口（菜单/右键共用）。
 *
 * <p>容器是 {@link TalismanLoadout}（真实 ItemStack 多槽），**不是** Curios：
 * §3.1 已确认本工程没有任何 Curios 依赖（{@code modpack/mods.lock.json} 无该项、构建为
 * {@code --offline}），所以本自制附件是**唯一**的装备权威——不允许再有一个并行的权威。
 *
 * <p>忙碌（施放中/战斗动作中）一律拒绝换装，避免在施放中间改数值。
 */
public final class TalismanEquipment {

    public enum Status {
        EQUIPPED, UNEQUIPPED, BUSY, EMPTY, REJECTED, FULL, INVENTORY_FULL, NOT_EQUIPPED
    }

    /** 只读快照：服务端算好再给界面，客户端不自己拼权威数值。 */
    public record Snapshot(List<String> equippedIds, List<TalismanSpec> effects,
                           TalismanAggregate totals, int slots) {
    }

    private TalismanEquipment() {
    }

    public static Snapshot snapshot(ServerPlayer player) {
        requireServerThread(player);
        return new Snapshot(Talismans.equippedIds(player), Talismans.equippedSpecs(player),
                Talismans.totals(player), TalismanRules.current().activeSlots());
    }

    /** 右键装备：解析**服务端**真实手持物品，客户端从不提交 ItemStack 或数值。 */
    public static Status equipFromHand(ServerPlayer player, InteractionHand hand) {
        requireServerThread(player);
        if (busy(player)) {
            return Status.BUSY;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) {
            return Status.EMPTY;
        }
        return map(Talismans.equip(player, stack));
    }

    /** 换装：把指定槽位替换成手持的护符（旧护符退回背包/脚边）。 */
    public static Status replace(ServerPlayer player, int slot, InteractionHand hand) {
        requireServerThread(player);
        if (busy(player)) {
            return Status.BUSY;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) {
            return Status.EMPTY;
        }
        return map(Talismans.replace(player, slot, stack));
    }

    /** 卸下第一个槽位。 */
    public static Status unequip(ServerPlayer player) {
        return unequip(player, 0);
    }

    public static Status unequip(ServerPlayer player, int slot) {
        requireServerThread(player);
        if (busy(player)) {
            return Status.BUSY;
        }
        return map(Talismans.unequip(player, slot));
    }

    /** 施放中或战斗动作中：不允许换护符（数值不能在施放中间变）。 */
    public static boolean busy(ServerPlayer player) {
        return SkillDriver.cast(player).isCasting() || player.getData(ModAttachments.COMBAT_STATE).isBusy();
    }

    private static Status map(Talismans.Result result) {
        return switch (result) {
            case EQUIPPED -> Status.EQUIPPED;
            case UNEQUIPPED -> Status.UNEQUIPPED;
            case FULL -> Status.FULL;
            case INVENTORY_FULL -> Status.INVENTORY_FULL;
            case NOT_EQUIPPED -> Status.NOT_EQUIPPED;
            case REJECTED, DUPLICATE, SAME_SERIES, BUSY -> Status.REJECTED;
        };
    }

    private static void requireServerThread(ServerPlayer player) {
        if (!player.server.isSameThread()) {
            throw new IllegalStateException("Talisman equipment requires server thread");
        }
    }
}
