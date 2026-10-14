package com.dsh.eldencombat.server.skills;

import java.util.List;

/**
 * 普通战灰（Ash of War）的**实际支持列表**——不是"全部战灰"。
 *
 * <p>用户要求"简单的战技释放动作、普攻照原版"，因此这里的每一件都是：
 * <ul>
 *   <li>**单段**：蓄势 → 生效（一次判定）→ 收招，没有追击段（{@code followupWindowTicks=0, maxStages=1}）。
 *       顺带避开 {@code SkillTransactions} 里那条已知的多段索引待查项（§2.6），
 *       不让新内容踩在还没修的东西上；</li>
 *   <li>**不位移**：没有跃起/突进，所以不需要落地安全网与悬空残留清理；</li>
 *   <li>表现复用已有特效与音效（不新增美术资源）。</li>
 * </ul>
 *
 * <p>数值是**同人草稿值**，不是原作数值；平衡调整只改这张表。
 *
 * <p>本类不依赖 Minecraft（只用到 {@link RemembranceSkillSpec}），所以目录本身可单测。
 */
public final class OrdinaryAshCatalog {

    /**
     * @param id       完整 namespaced 战灰 id（必须与 {@code RemembranceSkillSpec.id()} 一致）
     * @param label    界面文案
     * @param groupKey {@code AshWeaponGroups} 的分组键 = 可用武器类型白名单
     * @param spec     单段参数
     */
    public record Entry(String id, String label, String groupKey, RemembranceSkillSpec spec) {}

    private static final List<Entry> ENTRIES = List.of(
            new Entry("eldencombat:spinning_slash", "回旋斩", "slashing",
                    spec("eldencombat:spinning_slash", "curved_sword", 8f,
                            6, 6, 8, 2.4, 3.2, 12f, 14f, 20)),
            new Entry("eldencombat:heavy_thrust", "重刺", "thrusting",
                    spec("eldencombat:heavy_thrust", "spear", 9f,
                            8, 5, 9, 4.4, 1.6, 15f, 20f, 25)),
            new Entry("eldencombat:stamp_upward_cut", "踏前上斩", "cleaving",
                    spec("eldencombat:stamp_upward_cut", "great_sword", 11f,
                            9, 6, 11, 3.2, 2.0, 17f, 26f, 30)));

    private OrdinaryAshCatalog() {
    }

    /** 实际支持列表（顺序即展示顺序）。 */
    public static List<Entry> entries() {
        return ENTRIES;
    }

    private static RemembranceSkillSpec spec(String id, String itemId, float fpCost,
                                             int windup, int active, int recovery,
                                             double range, double radius,
                                             float damage, float poise, int cooldown) {
        return new RemembranceSkillSpec(
                id, itemId,
                fpCost, 0f,
                windup, active, recovery,
                0, 1,
                false, 0.0, 0.0,
                range, 10, radius,
                0f, cooldown, 0,
                damage, poise, 0, 0);
    }
}
