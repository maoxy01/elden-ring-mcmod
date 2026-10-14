package com.dsh.eldencombat.core;

/**
 * 一段命中窗口。[start, end) 为相对动作开始的服务端整数 tick 区间。
 *
 * @param start        起始 tick（含）
 * @param endExclusive 结束 tick（不含）
 * @param damage       伤害
 * @param poiseDamage  削韧（对 Boss 失衡值）
 * @param guardCost    被盾防住时攻击方定义的精力消耗
 * @param parryable    是否可被弹反
 */
public record HitWindow(int start, int endExclusive, float damage, float poiseDamage, float guardCost, boolean parryable) {

    public HitWindow {
        if (endExclusive <= start) {
            throw new IllegalArgumentException("命中窗口必须满足 end > start: [" + start + "," + endExclusive + ")");
        }
        if (damage < 0 || poiseDamage < 0 || guardCost < 0) {
            throw new IllegalArgumentException("伤害/削韧/防御消耗不能为负");
        }
    }

    public boolean activeAt(long tickInAction) {
        return tickInAction >= start && tickInAction < endExclusive;
    }
}
