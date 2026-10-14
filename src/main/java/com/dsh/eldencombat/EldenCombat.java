package com.dsh.eldencombat;

import com.dsh.eldencombat.core.CombatConfig;
import com.dsh.eldencombat.entity.MargitEntity;
import com.dsh.eldencombat.registry.ModAttachments;
import com.dsh.eldencombat.registry.ModDataComponents;
import com.dsh.eldencombat.registry.ModEntities;
import com.dsh.eldencombat.registry.ModItems;
import com.dsh.eldencombat.registry.ModMenus;
import com.dsh.eldencombat.registry.ModParticles;
import com.dsh.eldencombat.registry.ModPayloads;
import com.dsh.eldencombat.registry.ModRecipes;
import com.dsh.eldencombat.server.skills.SkillServerEvents;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Elden Combat 主类：只做注册与装配，规则在 core、服务端判定在 server、渲染在 client。
 *
 * <p>本模组**不依赖任何战斗模组**：玩家使用原版模型与原版普攻动作，战技只驱动原版关节。
 */
@Mod(EldenCombat.MODID)
public final class EldenCombat {

    public static final String MODID = "eldencombat";
    public static final Logger LOGGER = LoggerFactory.getLogger("EldenCombat");

    private static CombatConfig config;

    public EldenCombat(IEventBus modBus, ModContainer container) {
        ModItems.ITEMS.register(modBus);
        ModParticles.PARTICLES.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModAttachments.ATTACHMENTS.register(modBus);
        ModDataComponents.DATA_COMPONENTS.register(modBus);
        ModRecipes.RECIPE_SERIALIZERS.register(modBus);
        ModMenus.MENUS.register(modBus);

        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onAttributes);
        modBus.addListener(this::onCreativeTabs);
        modBus.addListener(ModPayloads::register);

        // 八个追忆战技都由同一个运行时驱动（内部按 spec.id 分派），在这里一次性注册。
        // 必须在任何施放请求之前完成，否则 SkillDriver 找不到 Runtime 会静默不生效。
        com.dsh.eldencombat.server.skills.SkillDriver.registerAll(
                new com.dsh.eldencombat.server.skills.RemembranceRuntime());

        // 服务端战斗循环（普攻窗口/翻滚/精力/受击打断/状态同步）是**实例**订阅者，
        // 不能靠 @EventBusSubscriber 自动注册，只能在这里显式挂到游戏总线上（内部带一次性防护）。
        com.dsh.eldencombat.server.ServerCombat.register();

        // SkillServerEvents 自带 @EventBusSubscriber，自动注册在游戏总线上，这里不要再手动注册（会重复触发）。
        LOGGER.info("[EldenCombat] 纯原版战斗扩展 loaded (MC 1.21.1 / NeoForge 21.1.219)");
    }

    public static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve("eldencombat-combat.json");
    }

    /** 赐福系统的唯一配置文件（休息恢复规则等集中在这里，见 {@code GraceSettings}）。 */
    public static Path graceConfigPath() {
        return FMLPaths.CONFIGDIR.get().resolve("eldencombat-grace.json");
    }

    /**
     * 护符规则的唯一配置文件（槽数/重复限制/倍率上下限，见 {@code TalismanRules}）。
     * 默认值 = 既有单槽行为。
     */
    public static Path talismanConfigPath() {
        return FMLPaths.CONFIGDIR.get().resolve("eldencombat-talisman.json");
    }

    /** 战斗配置（首轮调试默认值集中在这里，可热改后 /eldencombat reload）。 */
    public static CombatConfig config() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    public static CombatConfig reloadConfig() {
        config = CombatConfig.loadOrCreate(configPath());
        // 赐福规则跟战斗配置同一次 reload 读入。GraceSettings 不引用任何 Waystones 类型，
        // 所以缺依赖的服务器也能读这份配置（只是赐福接口依然不可用）。
        com.dsh.eldencombat.server.grace.GraceSettings.reload();
        // 护符规则（槽位/重复限制/上限）同样跟战斗配置一起读入：纯逻辑类，不引用任何可选依赖。
        com.dsh.eldencombat.core.TalismanRules.reload(talismanConfigPath());
        // 槽数/上限可能被改：让在线玩家重新对齐护符容器并重算数值（无服务端时是空操作）。
        com.dsh.eldencombat.server.Talismans.refreshAll();
        LOGGER.info("[EldenCombat] 战斗配置已载入: {}", configPath());
        return config;
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        // 用语句块而不是方法引用：reloadConfig() 有返回值，直接当 Runnable 传会与
        // enqueueWork(Supplier<T>) 重载产生歧义，javac 报"找不到合适的方法"。
        event.enqueueWork(() -> {
            reloadConfig();
            // 普通战灰目录：只注册"实际支持"的那几件（用独立运行时，不借追忆战技冒充）。
            com.dsh.eldencombat.server.skills.OrdinaryAshes.registerAll();
            // 战灰解锁的服务端权威存储：只在这里接线一次（AshWorkshop.Ownership）。
            // 不引用任何 Waystones 类型，所以缺依赖的服务器照样有正确语义（"尚未获得"而不是"存储未接入"）。
            com.dsh.eldencombat.server.skills.AshUnlocks.install();
            // 赐福的正式启动路径（不再是 -D 原型开关）：依赖齐备才装载 Waystones/Balm 适配器；
            // 缺依赖时赐福接口显示不可用，普通战斗功能完全不受影响（GraceBootstrap 内部兜住链路错误）。
            com.dsh.eldencombat.server.grace.GraceBootstrap.install();
            // 测试夹具**显式注册**：FML 的自动订阅器会枚举 @EventBusSubscriber 类的方法签名，
            // 夹具里含 Waystones 类型时，"未装 Waystones"的专用服务器会在 AutomaticEventSubscriber.inject
            // 处 NoClassDefFoundError 起不来。所以只在显式测试模式 + 依赖齐全时注册。
            if (Boolean.getBoolean("eldencombat.gracePrototype")
                    && net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.DEDICATED_SERVER) {
                com.dsh.eldencombat.testing.GracePrototypeHarness.install();
            }
        });
    }

    private void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.MARGIT.get(), MargitEntity.createAttributes().build());
    }

    private void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.MARGIT_SPAWN_EGG.get());
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.TEST_FLASK.get());
            for (var weapon : ModItems.ALL_WEAPONS) {
                event.accept(weapon.get());
            }
            for (var talisman : ModItems.TALISMANS) {
                event.accept(talisman.get());
            }
            for (var consumable : ModItems.CONSUMABLES) {
                event.accept(consumable.get());
            }
            // §2 锻造材料与模板
            for (var forging : ModItems.FORGING_ITEMS) {
                event.accept(forging.get());
            }
        }
    }
}
