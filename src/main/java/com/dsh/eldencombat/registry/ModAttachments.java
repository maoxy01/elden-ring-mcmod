package com.dsh.eldencombat.registry;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.core.CombatState;
import com.dsh.eldencombat.server.skills.FpStateCodec;
import com.dsh.eldencombat.server.skills.PlayerFpState;
import com.dsh.eldencombat.server.skills.SkillCastState;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * 玩家战斗状态用数据附件挂载：不落盘（读档不残留无敌与武器），
 * 玩家实体被移除时附件随之消失，重生时自动回到初始状态。
 *
 * <p>追忆战技的 FP 是独立附件：需要落盘（退出保存、重登不刷恢复），并在死亡克隆时带给新玩家实体。
 */
public final class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, EldenCombat.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CombatState>> COMBAT_STATE =
            ATTACHMENTS.register("combat_state", () -> AttachmentType.builder(CombatState::new).build());

    /** 全局 FP：服务端权威、独立于 EF 的 SkillContainer，可序列化并随死亡克隆传递 */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerFpState>> PLAYER_FP =
            ATTACHMENTS.register("player_fp", () -> AttachmentType.builder(PlayerFpState::new)
                    .serialize(FpStateCodec.CODEC)
                    .copyOnDeath()
                    .build());

    /** 当前施放实例（阶段/段数/命中去重）：不落盘，死亡/换维度/离线后自然消失 */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SkillCastState>> PLAYER_SKILL_CAST =
            ATTACHMENTS.register("player_skill_cast", () -> AttachmentType.builder(SkillCastState::new).build());

    /**
     * **旧版**护符栏（v1）：只存一个护符 id 字符串（空串 = 没戴）。
     *
     * <p>唯一用途是 §3.2 的**一次性迁移**读取；新代码**不再写入**它
     * （{@code Talismans.migrateLegacy} 迁移成功后清空）。不要在别处读它——
     * 两个装备权威并存正是 §3.1 明令禁止的。
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> TALISMAN =
            ATTACHMENTS.register("talisman", () -> AttachmentType.builder(() -> "")
                    .serialize(com.mojang.serialization.Codec.STRING)
                    .copyOnDeath()
                    .build());

    /**
     * 护符栏（§3.2/§3.3）：**存完整 ItemStack**，附魔/自定义名/组件都不丢；
     * 落盘并随死亡继承（{@code copyOnDeath}）。
     *
     * <p>槽位数来自 {@code config/eldencombat-talisman.json}（{@link com.dsh.eldencombat.core.TalismanRules}），
     * 解析时按 {@code max(存档长度, 当前槽数)} 对齐——**改配置不会销毁物品**；
     * 上限被调小时超出的物品由 {@code Talismans.refresh} 退回背包并记日志。
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.dsh.eldencombat.server.TalismanLoadout>> TALISMAN_SLOTS =
            ATTACHMENTS.register("talisman_slots", () -> AttachmentType
                    // 显式 lambda：TalismanLoadout::new 在 builder(Supplier) / builder(Function) 之间有歧义
                    .builder(() -> new com.dsh.eldencombat.server.TalismanLoadout())
                    .serialize(com.dsh.eldencombat.server.TalismanLoadout.CODEC)
                    .copyOnDeath()
                    .build());

    /**
     * §2.5 五项属性（力量/灵巧/智力/信仰/神秘）：落盘并随死亡继承。
     *
     * <p>本轮只有"数值 + 命令式加点"，加点 UI 后置；补正系统读的就是这份数据
     * （见 {@code WeaponDefinition#damageAt}）。
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.dsh.eldencombat.core.AttributeSet>> ATTRIBUTES =
            ATTACHMENTS.register("attributes", () -> AttachmentType
                    .builder(() -> com.dsh.eldencombat.core.AttributeSet.EMPTY)
                    .serialize(AttributeSetCodec.CODEC)
                    .copyOnDeath()
                    .build());

    private ModAttachments() {
    }
}
