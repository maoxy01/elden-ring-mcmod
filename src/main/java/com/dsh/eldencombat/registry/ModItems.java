package com.dsh.eldencombat.registry;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.item.EldenWeaponItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 物品注册。
 *
 * <p>33 件精细武器的 ID 与 {@code art/weapons/catalog.json} 一一对应：物品 ID 即模型文件名，
 * {@code WeaponAssets} 直接按注册 ID 查 {@code assets/eldencombat/skeletons/items/<id>.model.json}，
 * 不需要额外的映射表。已有同 ID 的直剑 / 大剑 / 盾改为新的精细资产，不重复注册。
 */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EldenCombat.MODID);

    /** 玛尔基特刷怪蛋（普通世界不自然生成，只用刷怪蛋与调试命令） */
    public static final DeferredItem<Item> MARGIT_SPAWN_EGG = ITEMS.registerItem("margit_spawn_egg",
            properties -> new DeferredSpawnEggItem(ModEntities.MARGIT, 0x6A6250, 0x2E3524, properties),
            new Item.Properties());

    /** 测试药瓶：喝药次数记在战斗状态里，这里只是载体（首版不做赐福与真实药瓶）。 */
    public static final DeferredItem<com.dsh.eldencombat.item.HealingFlaskItem> TEST_FLASK = ITEMS.registerItem("test_flask",
            com.dsh.eldencombat.item.HealingFlaskItem::new, new Item.Properties().stacksTo(16));

    // ------------------------------------------------------------------
    // 护符（4 件）：右键装备到护符栏，同一时间只生效一件。
    // 数值在 data/eldencombat/talismans/<id>.json，服务端应用。
    // ------------------------------------------------------------------

    /** 战士壶碎片：伤害 +8%、精力恢复 +20% */
    public static final DeferredItem<com.dsh.eldencombat.item.TalismanItem> WARRIOR_JAR_SHARD =
            talisman("warrior_jar_shard");
    /** 拉达冈的烙印：FP 上限 +30、精力恢复 +10%，但受到的伤害 +10%（有代价） */
    public static final DeferredItem<com.dsh.eldencombat.item.TalismanItem> RADAGON_SORESEAL =
            talisman("radagon_soreseal");
    /** 绿龟护符：精力恢复 +30% */
    public static final DeferredItem<com.dsh.eldencombat.item.TalismanItem> GREEN_TURTLE_TALISMAN =
            talisman("green_turtle_talisman");
    /** 龙印护盾护符：受到伤害 -18% */
    public static final DeferredItem<com.dsh.eldencombat.item.TalismanItem> DRAGONCREST_SHIELD_TALISMAN =
            talisman("dragoncrest_shield_talisman");

    // ------------------------------------------------------------------
    // 药物（3 种）：右键饮用，按数据包施加药水效果。
    // ------------------------------------------------------------------

    /** 火焰赐予我力量：力量 II 60 秒 + 生命恢复 I 20 秒 */
    public static final DeferredItem<com.dsh.eldencombat.item.ConsumableItem> FLAME_GRANT_ME_STRENGTH =
            consumable("flame_grant_me_strength");
    /** 铁壶香药：抗性提升 III 20 秒 + 缓慢 II 20 秒（换来硬抗） */
    public static final DeferredItem<com.dsh.eldencombat.item.ConsumableItem> IRONJAR_AROMATIC =
            consumable("ironjar_aromatic");
    /** 星光碎片：生命恢复 I 30 秒 + 吸收 I 30 秒 */
    public static final DeferredItem<com.dsh.eldencombat.item.ConsumableItem> STARLIGHT_SHARD =
            consumable("starlight_shard");

    public static final List<DeferredItem<com.dsh.eldencombat.item.TalismanItem>> TALISMANS = List.of(
            WARRIOR_JAR_SHARD, RADAGON_SORESEAL, GREEN_TURTLE_TALISMAN, DRAGONCREST_SHIELD_TALISMAN);

    public static final List<DeferredItem<com.dsh.eldencombat.item.ConsumableItem>> CONSUMABLES = List.of(
            FLAME_GRANT_ME_STRENGTH, IRONJAR_AROMATIC, STARLIGHT_SHARD);

    // ------------------------------------------------------------------
    // 33 件精细武器
    // 数值只是让物品能拿在手里打出伤害的占位，真正的战斗参数由 Epic Fight 能力提供。
    // ------------------------------------------------------------------

    // 短兵 / 直刃
    public static final DeferredItem<Item> DAGGER = weapon("dagger", 3, -1.4f, 200);
    public static final DeferredItem<Item> STRAIGHT_SWORD = weapon("straight_sword", 4, -2.0f, 250);
    public static final DeferredItem<Item> GREAT_SWORD = weapon("great_sword", 6, -2.6f, 300);
    public static final DeferredItem<Item> COLOSSAL_SWORD = weapon("colossal_sword", 9, -3.0f, 400);
    public static final DeferredItem<Item> THRUSTING_SWORD = weapon("thrusting_sword", 4, -1.9f, 250);
    public static final DeferredItem<Item> HEAVY_THRUSTING_SWORD = weapon("heavy_thrusting_sword", 7, -2.8f, 320);
    public static final DeferredItem<Item> CURVED_SWORD = weapon("curved_sword", 4, -1.8f, 250);
    public static final DeferredItem<Item> CURVED_GREATSWORD = weapon("curved_greatsword", 7, -2.6f, 340);
    public static final DeferredItem<Item> KATANA = weapon("katana", 5, -2.0f, 260);
    public static final DeferredItem<Item> TWINBLADE = weapon("twinblade", 5, -2.0f, 300);

    // 斧 / 钝器
    public static final DeferredItem<Item> AXE = weapon("axe", 6, -2.8f, 300);
    public static final DeferredItem<Item> GREATAXE = weapon("greataxe", 8, -3.1f, 360);
    public static final DeferredItem<Item> HAMMER = weapon("hammer", 6, -3.0f, 320);
    public static final DeferredItem<Item> GREAT_HAMMER = weapon("great_hammer", 8, -3.2f, 380);
    public static final DeferredItem<Item> COLOSSAL_WEAPON = weapon("colossal_weapon", 10, -3.3f, 420);

    // 长柄 / 软兵
    public static final DeferredItem<Item> FLAIL = weapon("flail", 5, -2.2f, 300);
    public static final DeferredItem<Item> SPEAR = weapon("spear", 5, -2.2f, 300);
    public static final DeferredItem<Item> GREAT_SPEAR = weapon("great_spear", 7, -2.8f, 340);
    public static final DeferredItem<Item> HALBERD = weapon("halberd", 6, -2.6f, 340);
    public static final DeferredItem<Item> SCYTHE = weapon("scythe", 5, -2.4f, 300);
    public static final DeferredItem<Item> WHIP = weapon("whip", 4, -2.0f, 260);

    // 拳 / 爪
    public static final DeferredItem<Item> FIST = weapon("fist", 3, -1.2f, 200);
    public static final DeferredItem<Item> CLAW = weapon("claw", 3, -1.3f, 220);

    // 远程：复用原版装填与弹药逻辑
    public static final DeferredItem<Item> SHORT_BOW = bow("short_bow", 300);
    public static final DeferredItem<Item> LONG_BOW = bow("long_bow", 384);
    public static final DeferredItem<Item> GREAT_BOW = bow("great_bow", 450);
    public static final DeferredItem<Item> CROSSBOW = crossbow("crossbow");
    public static final DeferredItem<Item> BALLISTA = crossbow("ballista");

    // 法器
    public static final DeferredItem<Item> GLINTSTONE_STAFF = weapon("glintstone_staff", 2, -1.5f, 200);
    public static final DeferredItem<Item> SACRED_SEAL = weapon("sacred_seal", 2, -1.5f, 200);

    // 盾
    public static final DeferredItem<Item> SMALL_SHIELD = shield("small_shield", 280);
    public static final DeferredItem<Item> SHIELD = shield("shield", 336);
    public static final DeferredItem<Item> GREAT_SHIELD = shield("great_shield", 420);

    // ------------------------------------------------------------------
    // 8 件本体追忆武器（固定专属战技，replaceableArt=false）
    // 美术走 art/remembrance-game 的游戏减面版，ID 与高细节源版一致，不是再注册 8 件重复物品。
    // ------------------------------------------------------------------
    public static final DeferredItem<Item> SACRED_RELIC_SWORD = weapon("sacred_relic_sword", 7, -2.6f, 400);
    public static final DeferredItem<Item> MARIKAS_HAMMER = weapon("marikas_hammer", 7, -3.0f, 400);
    public static final DeferredItem<Item> MALIKETHS_BLACK_BLADE = weapon("malikeths_black_blade", 9, -3.0f, 420);
    public static final DeferredItem<Item> BLASPHEMOUS_BLADE = weapon("blasphemous_blade", 7, -2.6f, 400);
    public static final DeferredItem<Item> HAND_OF_MALENIA = weapon("hand_of_malenia", 5, -2.0f, 300);
    public static final DeferredItem<Item> STARSCOURGE_GREATSWORD = weapon("starscourge_greatsword", 9, -3.0f, 450);
    public static final DeferredItem<Item> MOHGWYNS_SACRED_SPEAR = weapon("mohgwyns_sacred_spear", 7, -2.8f, 380);
    public static final DeferredItem<Item> MORGOTTS_CURSED_SWORD = weapon("morgotts_cursed_sword", 7, -2.6f, 360);


    /** 33 件精细武器，顺序与 catalog.json 一致。 */
    public static final List<DeferredItem<Item>> WEAPONS = List.of(
            DAGGER, STRAIGHT_SWORD, GREAT_SWORD, COLOSSAL_SWORD, THRUSTING_SWORD, HEAVY_THRUSTING_SWORD,
            CURVED_SWORD, CURVED_GREATSWORD, KATANA, TWINBLADE,
            AXE, GREATAXE, HAMMER, GREAT_HAMMER, COLOSSAL_WEAPON,
            FLAIL, SPEAR, GREAT_SPEAR, HALBERD, SCYTHE, WHIP,
            FIST, CLAW,
            SHORT_BOW, LONG_BOW, GREAT_BOW, CROSSBOW, BALLISTA,
            GLINTSTONE_STAFF, SACRED_SEAL,
            SMALL_SHIELD, SHIELD, GREAT_SHIELD);

    /** 33 件的物品 ID（不带命名空间），与 catalog.json 后缀一致。 */
    public static final Set<String> WEAPON_IDS = new LinkedHashSet<>(WEAPONS.stream()
            .map(item -> item.getId().getPath()).toList());

    /** 8 件追忆武器，顺序与 remembrance-game/catalog.json 一致。 */
    public static final List<DeferredItem<Item>> REMEMBRANCE_WEAPONS = List.of(
            SACRED_RELIC_SWORD, MARIKAS_HAMMER, MALIKETHS_BLACK_BLADE, BLASPHEMOUS_BLADE,
            HAND_OF_MALENIA, STARSCOURGE_GREATSWORD, MOHGWYNS_SACRED_SPEAR, MORGOTTS_CURSED_SWORD);

    /** 追忆武器走 skeletons/items/remembrance/ 子目录与独立的 2048 图集。 */
    public static final Set<String> REMEMBRANCE_IDS = new LinkedHashSet<>(
            REMEMBRANCE_WEAPONS.stream().map(item -> item.getId().getPath()).toList());

    /** 全部目标武器（追忆优先 + 33 件类型），用于创造标签、BEWLR 与资源索引。 */
    public static final List<DeferredItem<Item>> ALL_WEAPONS = java.util.stream.Stream
            .concat(REMEMBRANCE_WEAPONS.stream(), WEAPONS.stream()).toList();

    // ------------------------------------------------------------------
    // §2 锻造材料与模板（纯物品 + 数据组件；不新增槽位、不新增方块）
    // 数值与配方规则在 core/WeaponDefinition 与 crafting/WeaponForgingRecipe，
    // 这里只注册载体。图标由 Codex 出图前用占位贴图。
    // ------------------------------------------------------------------

    /** 普通锻造石 [1]–[8]：强化 +0→+10 用 */
    public static final List<DeferredItem<Item>> SMITHING_STONES = stones("smithing_stone", 8);
    /** 失色锻造石 [1]–[5]：追忆武器 +0→+5 用 */
    public static final List<DeferredItem<Item>> SOMBER_STONES = stones("somber_smithing_stone", 5);
    /** 古龙岩锻造石：+10（最终）*/
    public static final DeferredItem<Item> ANCIENT_DRAGON_SMITHING_STONE = material("ancient_dragon_smithing_stone");
    /** 古龙岩失色锻造石：追忆 +5（最终）*/
    public static final DeferredItem<Item> ANCIENT_DRAGON_SOMBER_STONE = material("ancient_dragon_somber_stone");

    /** 强化模板 [1]–[8]：与锻造石档位一一对应（用法与下界合金升级模板一致） */
    public static final List<DeferredItem<Item>> UPGRADE_TEMPLATES = templates("upgrade_template", 8);
    /** 失色强化模板 [1]–[5] */
    public static final List<DeferredItem<Item>> SOMBER_UPGRADE_TEMPLATES = templates("somber_upgrade_template", 5);

    /** 8 种质变模板 */
    public static final DeferredItem<Item> AFFINITY_TEMPLATE_HEAVY = material("affinity_template_heavy");
    public static final DeferredItem<Item> AFFINITY_TEMPLATE_KEEN = material("affinity_template_keen");
    public static final DeferredItem<Item> AFFINITY_TEMPLATE_QUALITY = material("affinity_template_quality");
    public static final DeferredItem<Item> AFFINITY_TEMPLATE_MAGIC = material("affinity_template_magic");
    public static final DeferredItem<Item> AFFINITY_TEMPLATE_FIRE = material("affinity_template_fire");
    public static final DeferredItem<Item> AFFINITY_TEMPLATE_SACRED = material("affinity_template_sacred");
    public static final DeferredItem<Item> AFFINITY_TEMPLATE_BLOOD = material("affinity_template_blood");
    public static final DeferredItem<Item> AFFINITY_TEMPLATE_OCCULT = material("affinity_template_occult");
    /** 重铸模板：清空质变（追忆武器战灰锁定，用不了）*/
    public static final DeferredItem<Item> REFORGE_TEMPLATE = material("reforge_template");

    /** 8 种质变素材（原创名，对应方案 §2.4 的表）*/
    public static final DeferredItem<Item> HEAVY_SCALE = material("heavy_scale");
    public static final DeferredItem<Item> KEEN_SCALE = material("keen_scale");
    public static final DeferredItem<Item> QUALITY_SCALE = material("quality_scale");
    public static final DeferredItem<Item> GLINTSTONE_DUST = material("glintstone_dust");
    public static final DeferredItem<Item> FIRE_EMBER = material("fire_ember");
    public static final DeferredItem<Item> SACRED_CRYSTAL = material("sacred_crystal");
    public static final DeferredItem<Item> BLOOD_RESIN = material("blood_resin");
    public static final DeferredItem<Item> OCCULT_CRYSTAL = material("occult_crystal");

    /** 悔恨之泪：重铸消耗 */
    public static final DeferredItem<Item> REMORSE_TEAR = material("remorse_tear");
    /** 铃珠 [1]–[4]：交给商人后解锁该档及以下锻造石的无限购买 */
    public static final List<DeferredItem<Item>> BELL_BEARINGS = stones("bell_bearing", 4);

    /** 全部锻造相关物品（创造标签用） */
    public static final List<DeferredItem<Item>> FORGING_ITEMS = java.util.stream.Stream.of(
            SMITHING_STONES.stream(), SOMBER_STONES.stream(),
            java.util.stream.Stream.of(ANCIENT_DRAGON_SMITHING_STONE, ANCIENT_DRAGON_SOMBER_STONE),
            UPGRADE_TEMPLATES.stream(), SOMBER_UPGRADE_TEMPLATES.stream(),
            java.util.stream.Stream.of(AFFINITY_TEMPLATE_HEAVY, AFFINITY_TEMPLATE_KEEN, AFFINITY_TEMPLATE_QUALITY,
                    AFFINITY_TEMPLATE_MAGIC, AFFINITY_TEMPLATE_FIRE, AFFINITY_TEMPLATE_SACRED,
                    AFFINITY_TEMPLATE_BLOOD, AFFINITY_TEMPLATE_OCCULT, REFORGE_TEMPLATE),
            java.util.stream.Stream.of(HEAVY_SCALE, KEEN_SCALE, QUALITY_SCALE, GLINTSTONE_DUST, FIRE_EMBER,
                    SACRED_CRYSTAL, BLOOD_RESIN, OCCULT_CRYSTAL),
            java.util.stream.Stream.of(REMORSE_TEAR), BELL_BEARINGS.stream()
    ).flatMap(stream -> stream).toList();

    private ModItems() {
    }

    /** 普通材料：不堆叠上限特殊处理，可堆叠 64 */
    private static DeferredItem<Item> material(String id) {
        return ITEMS.registerItem(id, Item::new, new Item.Properties());
    }

    /** 带编号的档位物品（锻造石 / 模板 / 铃珠），编号从 1 开始 */
    private static List<DeferredItem<Item>> stones(String prefix, int count) {
        List<DeferredItem<Item>> list = new java.util.ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            list.add(material(prefix + "_" + i));
        }
        return List.copyOf(list);
    }

    private static List<DeferredItem<Item>> templates(String prefix, int count) {
        return stones(prefix, count);
    }

    private static DeferredItem<Item> weapon(String id, int damage, float speed, int durability) {
        return ITEMS.registerItem(id, EldenWeaponItem::new, new Item.Properties()
                .stacksTo(1)
                .durability(durability)
                .attributes(SwordItem.createAttributes(Tiers.IRON, damage, speed)));
    }

    /** 护符：不消耗、不堆叠；装备状态存在玩家附件里。 */
    private static DeferredItem<com.dsh.eldencombat.item.TalismanItem> talisman(String id) {
        return ITEMS.registerItem(id, com.dsh.eldencombat.item.TalismanItem::new, new Item.Properties());
    }

    /** 药物：可堆叠的消耗品，数值来自 data/eldencombat/consumables/<id>.json。 */
    private static DeferredItem<com.dsh.eldencombat.item.ConsumableItem> consumable(String id) {
        return ITEMS.registerItem(id, com.dsh.eldencombat.item.ConsumableItem::new, new Item.Properties());
    }

    private static DeferredItem<Item> bow(String id, int durability) {
        return ITEMS.registerItem(id, BowItem::new, new Item.Properties().stacksTo(1).durability(durability));
    }

    private static DeferredItem<Item> crossbow(String id) {
        return ITEMS.registerItem(id, CrossbowItem::new, new Item.Properties().stacksTo(1).durability(465));
    }

    private static DeferredItem<Item> shield(String id, int durability) {
        return ITEMS.registerItem(id, ShieldItem::new, new Item.Properties().stacksTo(1).durability(durability));
    }
}
