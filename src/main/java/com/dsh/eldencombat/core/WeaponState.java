package com.dsh.eldencombat.core;

/**
 * 一把武器当前的成长状态（《成长路径与手感收口方案》§2.7 的 Data Component 内容）。
 *
 * <p>三件事装在一起，因为它们是"同一次锻造操作"的原子结果：
 * 强化等级、质变类型、战灰是否锁定（追忆武器 {@code replaceableArt=false}）。
 *
 * <p>纯数据；落到物品上时序列化成数据组件（{@code upgradeLevel} / {@code affinity} / {@code locked}）。
 */
public record WeaponState(int upgradeLevel, Affinity affinity, boolean artLocked) {

    public static final WeaponState FRESH = new WeaponState(0, Affinity.NONE, false);

    public WeaponState {
        upgradeLevel = Math.max(0, upgradeLevel);
        affinity = affinity == null ? Affinity.NONE : affinity;
    }

    /**
     * 按武器定义夹紧非法值：等级不超上限、追忆武器天然锁定战灰。
     * 反序列化与配方产出都必须过这里，避免出现 "+12 的直剑"或"追忆武器被重铸"。
     */
    public WeaponState sanitized(WeaponDefinition definition) {
        int max = definition.maxLevel();
        int level = Math.max(0, Math.min(max, upgradeLevel));
        boolean locked = definition.remembrance() || artLocked;
        return new WeaponState(level, affinity, locked);
    }

    public boolean canUpgrade(WeaponDefinition definition) {
        return upgradeLevel < definition.maxLevel();
    }

    public WeaponState upgraded(WeaponDefinition definition) {
        return new WeaponState(Math.min(definition.maxLevel(), upgradeLevel + 1), affinity, artLocked);
    }

    /** 换质变：**等级保留**（方案 §2.4：质变是构建实验，实验成本不能高到玩家不实验） */
    public WeaponState withAffinity(Affinity next) {
        return new WeaponState(upgradeLevel, next, artLocked);
    }

    /** 重铸：清空质变、等级保留；战灰锁定的武器不允许 */
    public WeaponState reforged(WeaponDefinition definition) {
        if (artLocked) {
            return this;
        }
        return new WeaponState(upgradeLevel, Affinity.NONE, false);
    }

    public double progress(WeaponDefinition definition) {
        return UpgradeMath.progress(upgradeLevel, definition.maxLevel());
    }

    public boolean isMaxed(WeaponDefinition definition) {
        return upgradeLevel >= definition.maxLevel();
    }

    public String describe(WeaponDefinition definition) {
        return String.format("%s +%d/%d 质变=%s%s", definition.id(), upgradeLevel, definition.maxLevel(),
                affinity.label(), artLocked ? " 战灰锁定" : "");
    }
}
