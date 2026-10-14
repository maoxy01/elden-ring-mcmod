package com.dsh.eldencombat.server;

import com.dsh.eldencombat.core.AttributeKind;
import com.dsh.eldencombat.core.AttributeSet;
import com.dsh.eldencombat.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;

/**
 * 玩家属性的读写（§2.5）。
 *
 * <p>本轮只做"数值契约 + 命令式加点"：加点 UI 归 Codex 的交互层，
 * 但补正系统要能立刻跑通、能被验收，所以属性先作为可序列化的附件存在。
 */
public final class PlayerAttributes {

    private PlayerAttributes() {
    }

    public static AttributeSet of(ServerPlayer player) {
        AttributeSet set = player.getData(ModAttachments.ATTRIBUTES);
        return set == null ? AttributeSet.EMPTY : set;
    }

    public static void set(ServerPlayer player, AttributeSet set) {
        player.setData(ModAttachments.ATTRIBUTES, set == null ? AttributeSet.EMPTY : set);
    }

    /** 加点：返回加点后的值（夹在 0..99） */
    public static int add(ServerPlayer player, AttributeKind kind, int delta) {
        AttributeSet current = of(player);
        int next = Math.max(0, Math.min(AttributeKind.MAX_POINTS, current.points(kind) + delta));
        set(player, current.with(kind, next));
        return next;
    }

    public static String describe(ServerPlayer player) {
        AttributeSet set = of(player);
        return set.describe() + String.format("（合计 %d 点）", set.total());
    }
}
