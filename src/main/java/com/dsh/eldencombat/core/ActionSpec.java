package com.dsh.eldencombat.core;

import java.util.List;

/**
 * 一个动作的静态定义（时长、精力、命中窗口、取消规则）。
 * 数值全部来自 {@link CombatConfig}/动作数据，不散落在业务代码里。
 */
public record ActionSpec(
        ActionId id,
        int totalTicks,
        float staminaCost,
        List<HitWindow> windows,
        int rollCancelFrom,
        int consumeTick,
        float heal,
        int guardBreakTicks) {

    public ActionSpec {
        windows = List.copyOf(windows);
        if (totalTicks <= 0) {
            throw new IllegalArgumentException("动作总长必须为正: " + id);
        }
        for (HitWindow window : windows) {
            if (window.endExclusive() > totalTicks) {
                throw new IllegalArgumentException("命中窗口超出动作总长: " + id + " " + window);
            }
        }
    }

    public static ActionSpec simple(ActionId id, int totalTicks, float staminaCost) {
        return new ActionSpec(id, totalTicks, staminaCost, List.of(), -1, -1, 0f, 0);
    }

    public boolean isFinished(long tickInAction) {
        return tickInAction >= totalTicks;
    }

    /** 该 tick 是否处于某个命中窗口，返回窗口；否则 null。多段攻击会在不同窗口返回不同窗口。 */
    public HitWindow activeWindow(long tickInAction) {
        for (HitWindow window : windows) {
            if (window.activeAt(tickInAction)) {
                return window;
            }
        }
        return null;
    }

    /** 后摇最后 N tick 允许翻滚取消；返回 -1 表示该动作不可被翻滚取消。 */
    public boolean rollCancelAllowed(long tickInAction) {
        return rollCancelFrom >= 0 && tickInAction >= rollCancelFrom;
    }
}
