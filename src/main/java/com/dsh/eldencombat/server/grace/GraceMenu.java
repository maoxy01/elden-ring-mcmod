package com.dsh.eldencombat.server.grace;

import com.dsh.eldencombat.registry.ModMenus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * 赐福菜单：0 槽位的服务菜单，不搬运任何物品。
 *
 * <p>{@link #graceId} 在服务端由 {@code MenuProvider} 打开时捕获，**不是客户端传来的**；
 * 客户端从额外 buf 读到的那份只用于对齐展示。
 *
 * <p>{@link #stillValid} 每次重新校验访问权限（维度/距离/发现/权限/战斗状态），失效时服务端关掉菜单。
 * 但它**不是唯一授权**：每个按钮在 {@link GraceMenuActions} 里还会完整再校验一遍，
 * "打开过菜单"永远不是永久授权。
 */
public final class GraceMenu extends AbstractContainerMenu {

    private final UUID graceId;
    private final String graceName;
    /** 最近一次结果提示：周期刷新时沿用，免得刷新把玩家刚看到的失败原因冲掉。 */
    private String notice = "";

    public GraceMenu(int windowId, Inventory inventory, UUID graceId, String graceName) {
        super(ModMenus.GRACE.get(), windowId);
        this.graceId = graceId == null ? new UUID(0, 0) : graceId;
        this.graceName = graceName == null ? "" : graceName;
    }

    public String notice() {
        return notice;
    }

    public void notice(String value) {
        this.notice = value == null ? "" : value;
    }

    /** 服务端权威的赐福 UUID。 */
    public UUID graceId() {
        return graceId;
    }

    public String graceName() {
        return graceName;
    }

    @Override public boolean stillValid(Player player) {
        // 客户端不做权威判定（它拿不到 Waystones 的服务端状态），服务端每次现校验。
        if (!(player instanceof ServerPlayer serverPlayer)) return true;
        return GraceOperations.localAccess(serverPlayer, graceId).accepted();
    }

    /** 没有槽位：Shift 点击不搬运任何东西。 */
    @Override public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
