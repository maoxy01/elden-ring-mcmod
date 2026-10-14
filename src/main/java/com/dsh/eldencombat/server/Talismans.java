package com.dsh.eldencombat.server;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.core.TalismanAggregate;
import com.dsh.eldencombat.core.TalismanIds;
import com.dsh.eldencombat.core.TalismanRules;
import com.dsh.eldencombat.core.TalismanSpec;
import com.dsh.eldencombat.network.TalismanDataPayload;
import com.dsh.eldencombat.registry.ModAttachments;
import com.dsh.eldencombat.server.skills.SkillServerEvents;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 护符系统的服务端权威实现（§3 闭环）。
 *
 * <p>规则：
 * <ul>
 *   <li>护符栏是**玩家附件里的真实 {@link ItemStack} 容器**（{@link TalismanLoadout}）：
 *       附魔/自定义名/组件原样保留；落盘并随死亡继承（{@code copyOnDeath}）。</li>
 *   <li>槽位数、重复限制、倍率上下限全部集中在 {@link TalismanRules}（{@code config/eldencombat-talisman.json}），
 *       **默认单槽 = 既有行为**。</li>
 *   <li>战斗数值出口（伤害/受伤/精力/治疗/FP 上限）一律读 {@link TalismanAggregate}
 *       的聚合结果，战斗代码不直接读容器、也不自己乘。</li>
 *   <li>旧版单槽字符串附件 {@code eldencombat:talisman} **只读一次**用于迁移，
 *       迁移后不再写入（见 {@link #migrateLegacy}）。</li>
 *   <li>参数来自数据包 {@code data/<namespace>/talismans/<id>.json}，键是**完整 namespaced id**，
 *       {@code /reload} 即可生效；客户端只收到展示数据（{@link TalismanDataPayload}）。</li>
 * </ul>
 */
public final class Talismans {

    /** 装备/卸下的结果，附带给玩家看的原因（可读失败反馈）。 */
    public enum Result {
        EQUIPPED("已装备护符"),
        UNEQUIPPED("已卸下护符"),
        REJECTED("不是有效护符，或没有对应数据"),
        FULL("护符栏已满，请先卸下一件"),
        DUPLICATE("同款护符已经在护符栏里（不允许重复）"),
        SAME_SERIES("同系列护符已经在护符栏里（不允许重复）"),
        BUSY("施放或战斗动作进行中，无法换护符"),
        INVENTORY_FULL("背包已满，无法卸下护符"),
        NOT_EQUIPPED("该护符栏位是空的");

        private final String reason;

        Result(String reason) {
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }
    }

    /** 护符目录：键 = 完整 namespaced id（不同 namespace 的同名护符各自独立）。 */
    private static volatile Map<String, TalismanSpec> specs = Map.of();

    private Talismans() {
    }

    public static Map<String, TalismanSpec> all() {
        return specs;
    }

    /** 传裸路径也认（补 {@code eldencombat:}），但**只**用于兼容旧调用点；新代码一律传完整 ID。 */
    public static TalismanSpec spec(String id) {
        String normalized = TalismanIds.normalize(id);
        if (normalized.isEmpty()) {
            return TalismanSpec.NONE;
        }
        return specs.getOrDefault(normalized, TalismanSpec.NONE);
    }

    public static TalismanRules rules() {
        return TalismanRules.current();
    }

    // ------------------------------------------------------------------
    // 容器访问（所有其他入口都从这里取，保证迁移只发生一次）
    // ------------------------------------------------------------------

    /**
     * 护符容器。**取容器前会先跑一次一次性旧数据迁移**（幂等、廉价）：
     * 这样任何调用点都不可能读到"迁移还没发生"的中间状态。
     */
    public static TalismanLoadout loadout(ServerPlayer player) {
        migrateLegacy(player);
        return player.getData(ModAttachments.TALISMAN_SLOTS);
    }

    /** 已装备护符的完整 ID（含"物品还在但数据缺失"的，用于重复判定与诊断）。 */
    public static List<String> equippedIds(ServerPlayer player) {
        return loadout(player).ids();
    }

    /** 已装备护符中**有数据**的那些参数（聚合只吃这些）。 */
    public static List<TalismanSpec> equippedSpecs(ServerPlayer player) {
        List<TalismanSpec> out = new ArrayList<>();
        for (String id : equippedIds(player)) {
            TalismanSpec spec = spec(id);
            if (!spec.isNone()) {
                out.add(spec);
            }
        }
        return out;
    }

    /** 兼容旧调用点：第一个槽位的 ID（空串 = 没戴）。 */
    public static String equippedId(ServerPlayer player) {
        List<String> ids = equippedIds(player);
        return ids.isEmpty() ? "" : ids.get(0);
    }

    /** 兼容旧调用点：第一个槽位的参数。 */
    public static TalismanSpec equipped(ServerPlayer player) {
        return spec(equippedId(player));
    }

    // ------------------------------------------------------------------
    // 数值出口：战斗代码只调这些方法，不直接读附件
    // ------------------------------------------------------------------

    /** 聚合结果（含 FP 上限加成、倍率与件数）。 */
    public static TalismanAggregate totals(ServerPlayer player) {
        return TalismanAggregate.of(equippedSpecs(player), TalismanRules.current());
    }

    public static float damageMultiplier(ServerPlayer player) {
        return totals(player).damageMultiplier();
    }

    public static float damageTakenMultiplier(ServerPlayer player) {
        return totals(player).damageTakenMultiplier();
    }

    public static float staminaRegenMultiplier(ServerPlayer player) {
        return totals(player).staminaRegenMultiplier();
    }

    public static float healBonus(ServerPlayer player) {
        return totals(player).healBonus();
    }

    /** FP 上限加成（绝对值），由 {@link #refresh} 应用到服务端权威状态。 */
    public static float maxFpBonus(ServerPlayer player) {
        return totals(player).maxFpBonus();
    }

    // ------------------------------------------------------------------
    // 装备 / 卸下 / 替换
    // ------------------------------------------------------------------

    /** 右键装备：优先装进第一个空格；栏满且同款已戴则当作"卸下"（保留旧的右键切换手感）。 */
    public static Result equip(ServerPlayer player, ItemStack talisman) {
        return equip(player, talisman, -1);
    }

    /**
     * 装备到指定槽位。
     *
     * @param slot &lt; 0 = 自动选第一个空格；否则必须落在当前配置的槽位范围内（防越界写）
     */
    public static Result equip(ServerPlayer player, ItemStack talisman, int slot) {
        if (talisman == null || talisman.isEmpty()
                || !(talisman.getItem() instanceof com.dsh.eldencombat.item.TalismanItem)) {
            return reject(player, Result.REJECTED);
        }
        TalismanLoadout loadout = loadout(player);
        TalismanRules rules = TalismanRules.current();
        String id = TalismanLoadout.idOf(talisman);
        TalismanSpec spec = spec(id);
        if (spec.isNone()) {
            return reject(player, Result.REJECTED);
        }
        int occupied = loadout.indexOf(talisman);
        int target = slot >= 0 ? slot : loadout.firstEmpty();
        if (target < 0) {
            // 栏满：同款已戴 → 右键切换成卸下；否则明确报满
            if (!rules.allowDuplicates && occupied >= 0) {
                return unequip(player, occupied);
            }
            return reject(player, Result.FULL);
        }
        if (target >= rules.activeSlots()) {
            return reject(player, Result.FULL);
        }
        if (target >= loadout.size()) {
            // 配置调大了槽数但容器还没长出来：先按配置补齐
            loadout.resize(rules.activeSlots());
        }
        ItemStack existing = loadout.get(target);
        if (!existing.isEmpty()) {
            // 指定槽位已有护符：走替换（旧护符退回背包），不静默覆盖
            return replace(player, target, talisman);
        }
        Result duplicate = duplicateRejection(player, loadout, rules, spec);
        if (duplicate != null) {
            return reject(player, duplicate);
        }
        ItemStack placed = talisman.copyWithCount(1);
        loadout.set(target, placed);
        talisman.shrink(1);
        refresh(player);
        feedback(player, "已装备护符：" + spec.displayName() + "（槽 " + (target + 1) + "/"
                + rules.activeSlots() + "，" + spec.describe() + "）");
        return Result.EQUIPPED;
    }

    /** 换装：槽位里的旧护符退回背包（背包满则落在脚边并明确提示），新护符从手持扣除。 */
    public static Result replace(ServerPlayer player, int slot, ItemStack talisman) {
        if (talisman == null || talisman.isEmpty()
                || !(talisman.getItem() instanceof com.dsh.eldencombat.item.TalismanItem)) {
            return reject(player, Result.REJECTED);
        }
        TalismanLoadout loadout = loadout(player);
        TalismanRules rules = TalismanRules.current();
        if (slot < 0 || slot >= rules.activeSlots()) {
            return reject(player, Result.REJECTED);
        }
        String id = TalismanLoadout.idOf(talisman);
        TalismanSpec spec = spec(id);
        if (spec.isNone()) {
            return reject(player, Result.REJECTED);
        }
        ItemStack previous = loadout.get(slot);
        if (!previous.isEmpty() && previous.getItem() == talisman.getItem() && !rules.allowDuplicates) {
            return reject(player, Result.DUPLICATE);
        }
        ItemStack placed = talisman.copyWithCount(1);
        loadout.set(slot, placed);
        talisman.shrink(1);
        if (!previous.isEmpty()) {
            returnToInventory(player, previous, "护符换装：被替换下来的护符");
        }
        refresh(player);
        feedback(player, "已换上护符：" + spec.displayName() + "（槽 " + (slot + 1) + "/"
                + rules.activeSlots() + "）");
        return Result.EQUIPPED;
    }

    /** 卸下第一个槽位（兼容旧调用点）。 */
    public static Result unequip(ServerPlayer player) {
        return unequip(player, 0);
    }

    /**
     * 卸下指定槽位。
     *
     * <p>**背包满时拒绝卸下**（而不是把护符丢在地上）：这条路径是玩家主动操作，
     * 宁可让他看到"背包已满"，也不制造"护符掉地上了"的意外。
     */
    public static Result unequip(ServerPlayer player, int slot) {
        TalismanLoadout loadout = loadout(player);
        if (slot < 0 || slot >= loadout.size() || loadout.get(slot).isEmpty()) {
            return reject(player, Result.NOT_EQUIPPED);
        }
        if (player.getInventory().getFreeSlot() < 0) {
            return reject(player, Result.INVENTORY_FULL);
        }
        ItemStack removed = loadout.take(slot);
        String name = spec(TalismanLoadout.idOf(removed)).displayName();
        boolean returned = player.getInventory().add(removed);
        if (!returned) {
            // 理论上前面已经查过空格；真发生就放回去，绝不让物品消失
            loadout.set(slot, removed);
            EldenCombat.LOGGER.error("[talismans] 卸下时背包拒收（已放回护符栏）：player={} slot={}",
                    player.getUUID(), slot);
            return reject(player, Result.INVENTORY_FULL);
        }
        refresh(player);
        feedback(player, "已卸下护符：" + (name == null || name.isEmpty() ? TalismanLoadout.idOf(removed) : name));
        return Result.UNEQUIPPED;
    }

    // ------------------------------------------------------------------
    // 生命周期：登录 / 重生 / 换维度 / /reload 后重新应用
    // ------------------------------------------------------------------

    /**
     * 重新应用护符效果（附件本身会落盘）。
     *
     * <p>顺序刻意是「先对齐槽位、再聚合、再写 FP 上限」：
     * 槽位上限被改小时超出的物品会先退回背包，不会留在聚合里。
     * {@link com.dsh.eldencombat.server.skills.PlayerFpState#setBonusMaxFp} 只向下夹当前 FP，
     * 所以反复换护符**不能**补满 FP。
     */
    public static void refresh(ServerPlayer player) {
        TalismanLoadout loadout = loadout(player);
        reconcile(player, loadout);
        TalismanAggregate totals = totals(player);
        SkillServerEvents.fp(player).setBonusMaxFp(totals.maxFpBonus());
        SkillServerEvents.sync(player);
    }

    /**
     * 配置（槽数/上限）改过之后，让所有在线玩家重新对齐容器并重新应用数值。
     * 没有服务端时（单元测试、客户端早期阶段）直接返回。
     */
    public static void refreshAll() {
        net.minecraft.server.MinecraftServer server =
                net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refresh(player);
        }
    }

    private static void reconcile(ServerPlayer player, TalismanLoadout loadout) {        TalismanRules rules = TalismanRules.current();
        int limit = rules.activeSlots();
        if (loadout.size() > limit) {
            for (int slot = loadout.size() - 1; slot >= limit; slot--) {
                ItemStack stack = loadout.take(slot);
                if (!stack.isEmpty()) {
                    EldenCombat.LOGGER.warn("[talismans] 护符槽上限 {} 小于存档槽数 {}：槽 {} 的物品退回背包（不销毁）",
                            limit, loadout.size(), slot + 1);
                    returnToInventory(player, stack, "护符槽上限下调");
                }
            }
        }
        loadout.resize(limit);
    }

    // ------------------------------------------------------------------
    // §3.2 旧数据迁移（只读旧附件，一次性、幂等）
    // ------------------------------------------------------------------

    /**
     * 把旧版 {@code eldencombat:talisman}（只有 id 字符串）迁移成容器里的真实物品。
     *
     * <p>幂等：旧记录清空后本方法直接返回。**不重复返还**：容器里已经有护符时不发放，
     * 只清掉旧记录并 WARN。**不静默清掉**：物品不存在 / 字符串非法 / 没有空位时，
     * 旧记录原样保留并打 WARN，等人工处理（宁可留下一条可疑记录，也不吃掉玩家的护符）。
     *
     * @return 本次是否发生了迁移（true = 刚刚迁移成功）
     */
    public static boolean migrateLegacy(ServerPlayer player) {
        String legacy = player.getData(ModAttachments.TALISMAN);
        if (legacy == null || legacy.isEmpty()) {
            return false;
        }
        TalismanLoadout loadout = player.getData(ModAttachments.TALISMAN_SLOTS);
        String id = TalismanIds.normalize(legacy);
        if (id.isEmpty()) {
            EldenCombat.LOGGER.warn("[talismans] 旧护符记录 '{}' 不是合法 ID，保持原样（不静默清掉）：player={}",
                    legacy, player.getUUID());
            return false;
        }
        if (!loadout.isEmpty()) {
            player.setData(ModAttachments.TALISMAN, "");
            EldenCombat.LOGGER.warn("[talismans] 旧护符记录 '{}' 未迁移：容器里已有 {} 件（避免重复返还），旧记录已清空",
                    legacy, loadout.equippedCount());
            return false;
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        Item item = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        if (item == null) {
            EldenCombat.LOGGER.warn("[talismans] 旧护符记录 '{}' 对应物品不存在，保持原样（不静默清掉）：player={}",
                    id, player.getUUID());
            return false;
        }
        int slot = loadout.firstEmpty() >= 0 ? loadout.firstEmpty() : 0;
        loadout.set(slot, new ItemStack(item));
        player.setData(ModAttachments.TALISMAN, "");
        EldenCombat.LOGGER.info("TALISMAN_MIGRATED: player={} legacy='{}' -> {} 槽 {}（完整 ItemStack），旧记录已清空",
                player.getUUID(), legacy, id, slot + 1);
        return true;
    }

    // ------------------------------------------------------------------
    // §3.5 展示数据同步（客户端 tooltip 不读服务端静态表）
    // ------------------------------------------------------------------

    /** 给单个玩家下发完整护符目录（登录时调用）。 */
    public static void syncDisplay(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, displayPayload());
    }

    /** {@code /reload} 后给所有在线玩家重新下发（客户端不会留着已删除的条目）。 */
    public static void broadcastDisplay() {
        // 数据包加载在**客户端主菜单**也会发生（同一份基础数据）：那时还没有服务端，
        // NeoForge 的 PacketDistributor 会抛 "Cannot send clientbound payloads on the client"，
        // 直接把客户端启动画面打崩。所以在源头挡住，而不是指望调用方自觉。
        net.minecraft.server.MinecraftServer server =
                net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        PacketDistributor.sendToAllPlayers(displayPayload());
    }

    public static TalismanDataPayload displayPayload() {
        return new TalismanDataPayload(List.copyOf(specs.values()));
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /** 重复/同系列判定；允许则返回 null。 */
    private static Result duplicateRejection(ServerPlayer player, TalismanLoadout loadout,
            TalismanRules rules, TalismanSpec candidate) {
        List<String> ids = loadout.ids();
        List<String> series = new ArrayList<>(ids.size());
        for (String existing : ids) {
            series.add(spec(existing).series());
        }
        if (rules.accepts(ids, series, candidate)) {
            return null;
        }
        return ids.contains(candidate.id()) ? Result.DUPLICATE : Result.SAME_SERIES;
    }

    /** 退回背包；背包满则落在玩家脚边（并记 WARN + 明确提示），绝不静默销毁。 */
    private static void returnToInventory(ServerPlayer player, ItemStack stack, String reason) {
        if (stack.isEmpty()) {
            return;
        }
        if (player.getInventory().add(stack)) {
            return;
        }
        EldenCombat.LOGGER.warn("[talismans] 背包已满，{}落在脚下：player={} item={}",
                reason, player.getUUID(), TalismanLoadout.idOf(stack));
        player.drop(stack, false);
        player.displayClientMessage(Component.literal(reason + "：背包已满，物品落在脚边"), false);
    }

    private static Result reject(ServerPlayer player, Result result) {
        feedback(player, result.reason());
        return result;
    }

    private static void feedback(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 0.8f, 1.1f);
    }

    // ------------------------------------------------------------------
    // 数据包加载
    // ------------------------------------------------------------------

    /** {@code data/<ns>/talismans/*.json}；{@code /reload} 时整体替换，然后重新下发展示数据。 */
    public static final class Listener extends SimpleJsonResourceReloadListener {

        public Listener() {
            super(new Gson(), "talismans");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager,
                ProfilerFiller profiler) {
            Map<String, TalismanSpec> loaded = new HashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
                // 键是**完整 ID**：不同 namespace 的同名护符必须各占一条（§3.6）
                String id = entry.getKey().toString();
                try {
                    TalismanSpec spec = TalismanSpec.parse(id, entry.getValue().getAsJsonObject());
                    loaded.put(id, spec);
                } catch (RuntimeException error) {
                    EldenCombat.LOGGER.error("[talismans] 解析失败 {}", entry.getKey(), error);
                }
            }
            specs = Map.copyOf(loaded);
            EldenCombat.LOGGER.info("[talismans] 护符数据载入 {} 件", specs.size());
            broadcastDisplay();
        }
    }
}
