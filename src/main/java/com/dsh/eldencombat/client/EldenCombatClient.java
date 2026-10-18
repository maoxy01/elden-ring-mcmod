package com.dsh.eldencombat.client;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.registry.ModEntities;
import com.dsh.eldencombat.registry.ModItems;
import com.dsh.eldencombat.registry.ModParticles;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端注册：可改绑 KeyMapping（默认键位见交接说明）与自定义实体/BEWLR 渲染器。
 *
 * <p>纯原版方案，不再注册任何 Epic Fight 渲染链。玩家模型完全交给原版
 * {@code PlayerModel}/{@code PlayerRenderer}：普攻就是原版挥剑动作，战技动作由
 * {@link SkillPoseAnimator} 在自己的 mixin 里只改原版六个关节。
 */
@EventBusSubscriber(modid = EldenCombat.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class EldenCombatClient {

    public static final String CATEGORY = "key.categories.eldencombat";

    public static final KeyMapping TOGGLE_COMBAT = key("toggle_combat", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R);
    public static final KeyMapping HEAVY = key("heavy_attack", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G);
    public static final KeyMapping ROLL = key("roll", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT);
    public static final KeyMapping LOCK_ON = key("lock_on", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
    public static final KeyMapping PARRY = key("parry", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X);
    public static final KeyMapping DRINK = key("drink", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V);
    /** 战技：单键释放武器专属战技，追击窗口内再按一次进入下一段。 */
    public static final KeyMapping SKILL = key("skill", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C);

    private EldenCombatClient() {
    }

    /** Native state predicates; ready resource models supply their corresponding overrides. */
    @SubscribeEvent
    public static void setupItemStates(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(()->{
            for(var entry:ModItems.ALL_WEAPONS){
                var item=entry.get();
                var reference=item instanceof net.minecraft.world.item.BowItem?net.minecraft.world.item.Items.BOW:
                        item instanceof net.minecraft.world.item.CrossbowItem?net.minecraft.world.item.Items.CROSSBOW:
                        item instanceof net.minecraft.world.item.ShieldItem?net.minecraft.world.item.Items.SHIELD:null;
                if(reference==null)continue;
                for(String name:new String[]{"pull","pulling","charged","firework","blocking"}){
                    var key=net.minecraft.resources.ResourceLocation.withDefaultNamespace(name);
                    var nativeProperty=net.minecraft.client.renderer.item.ItemProperties.getProperty(new net.minecraft.world.item.ItemStack(reference),key);
                    if(nativeProperty!=null)net.minecraft.client.renderer.item.ItemProperties.register(item,key,
                            (stack,level,entity,seed)->nativeProperty.call(stack,level,entity,seed));
                }
            }
            // Provider-namespace visual properties, registered on our own items only for the states the
            // route contract marks as actually reachable. blocking/throwing stay evidence-only.
            com.dsh.eldencombat.client.equipment2d.EquipmentRouteSnapshot
                    .installAliasRegistrar(com.dsh.eldencombat.client.equipment2d.EquipmentPredicates::register);
        });
    }

    /**
     * 赐福客户端夹具的注册口：**只在显式测试模式**下调用。
     * 夹具类含 Waystones/JourneyMap 类型，不能用 {@code @EventBusSubscriber} 自动注册
     * （自动订阅器反射方法签名时会解析这些类型，缺依赖的专用服务器会起不来）。
     */
    @SubscribeEvent
    public static void setupGracePrototypeHarness(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        if (!Boolean.getBoolean("eldencombat.gracePrototype")) return;
        event.enqueueWork(() -> com.dsh.eldencombat.testing.GraceClientPrototypeHarness.install());
    }

    /** 普通战灰夹具的注册口：**只在显式测试模式**下调用（理由同上）。 */
    @SubscribeEvent
    public static void setupAshSmokeHarness(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        if (!Boolean.getBoolean("eldencombat.ashSmoke")) return;
        event.enqueueWork(() -> com.dsh.eldencombat.testing.AshSmokeHarness.install());
    }

    private static KeyMapping key(String name, InputConstants.Type type, int code) {
        return new KeyMapping("key.eldencombat." + name, KeyConflictContext.IN_GAME, type, code, CATEGORY);
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        // 七条映射必须全部注册：未注册的 KeyMapping 不会进入 options.keyMappings，
        // consumeClick()/isDown() 永远为假（这里曾经只注册了 DRINK，导致重击/翻滚/弹反/锁定全部失效）。
        event.register(TOGGLE_COMBAT);
        event.register(HEAVY);
        if (!com.dsh.eldencombat.compat.CombatRollCompat.present()) event.register(ROLL);
        event.register(LOCK_ON);
        event.register(PARRY);
        event.register(DRINK);
        event.register(SKILL);
    }

    /** 赐福菜单的界面注册（0 槽位服务菜单，见 {@code GraceScreen}）。 */
    @SubscribeEvent
    public static void onRegisterScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(com.dsh.eldencombat.registry.ModMenus.GRACE.get(),
                com.dsh.eldencombat.client.grace.GraceScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.MARGIT.get(), MargitRenderer::new);
        event.registerEntityRenderer(ModEntities.GOLDEN_WAVE.get(), com.dsh.eldencombat.client.effects.GoldenWaveRenderer::new);
        event.registerEntityRenderer(ModEntities.SKILL_EFFECT.get(), com.dsh.eldencombat.client.effects.SkillEffectRenderer::new);
    }

    /**
     * F3+T / 资源包切换时准备网格，再原子替换客户端缓存；不是服务端 /reload。
     *
     * <p>同时刷新战技姿态数据与武器手持校正（都来自资源包，改了不用重编译）。
     */
    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new PresentationRoutes());
        event.registerReloadListener(com.dsh.eldencombat.client.equipment2d.EquipmentRouteSnapshot.create());
        event.registerReloadListener(new net.minecraft.server.packs.resources.SimplePreparableReloadListener<com.dsh.eldencombat.effects.FxPalette.Data>() {
            @Override protected com.dsh.eldencombat.effects.FxPalette.Data prepare(net.minecraft.server.packs.resources.ResourceManager manager,
                    net.minecraft.util.profiling.ProfilerFiller profiler) {
                return com.dsh.eldencombat.client.effects.FxResources.prepare(manager);
            }
            @Override protected void apply(com.dsh.eldencombat.effects.FxPalette.Data data,
                    net.minecraft.server.packs.resources.ResourceManager manager, net.minecraft.util.profiling.ProfilerFiller profiler) {
                com.dsh.eldencombat.effects.FxPalette.apply(data);
            }
        });
        event.registerReloadListener(new net.minecraft.server.packs.resources.SimplePreparableReloadListener<java.util.Map<String, WeaponAssets.Entry>>() {
            @Override
            protected java.util.Map<String, WeaponAssets.Entry> prepare(net.minecraft.server.packs.resources.ResourceManager manager,
                    net.minecraft.util.profiling.ProfilerFiller profiler) {
                return WeaponAssets.prepare(manager);
            }

            @Override
            protected void apply(java.util.Map<String, WeaponAssets.Entry> prepared, net.minecraft.server.packs.resources.ResourceManager manager,
                    net.minecraft.util.profiling.ProfilerFiller profiler) {
                WeaponAssets.apply(prepared);
            }
        });
        event.registerReloadListener(new net.minecraft.server.packs.resources.SimplePreparableReloadListener<com.dsh.eldencombat.core.SkillPoseData.Data>() {
            @Override
            protected com.dsh.eldencombat.core.SkillPoseData.Data prepare(net.minecraft.server.packs.resources.ResourceManager manager,
                    net.minecraft.util.profiling.ProfilerFiller profiler) {
                return SkillPoseAnimator.prepare(manager);
            }

            @Override
            protected void apply(com.dsh.eldencombat.core.SkillPoseData.Data prepared, net.minecraft.server.packs.resources.ResourceManager manager,
                    net.minecraft.util.profiling.ProfilerFiller profiler) {
                SkillPoseAnimator.apply(prepared);
            }
        });
        event.registerReloadListener(new net.minecraft.server.packs.resources.SimplePreparableReloadListener<java.util.Map<String, WeaponCorrections.Entry>>() {
            @Override
            protected java.util.Map<String, WeaponCorrections.Entry> prepare(net.minecraft.server.packs.resources.ResourceManager manager,
                    net.minecraft.util.profiling.ProfilerFiller profiler) {
                return WeaponCorrections.prepare(manager);
            }

            @Override
            protected void apply(java.util.Map<String, WeaponCorrections.Entry> prepared, net.minecraft.server.packs.resources.ResourceManager manager,
                    net.minecraft.util.profiling.ProfilerFiller profiler) {
                WeaponCorrections.apply(prepared);
            }
        });
    }

    @SubscribeEvent
    public static void itemExtensions(RegisterClientExtensionsEvent event) {
        IClientItemExtensions extensions = new IClientItemExtensions() {
            private EquipmentRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new EquipmentRenderer();
                return renderer;
            }
        };
        Item[] items = new Item[ModItems.ALL_WEAPONS.size() + 1];
        for (int i = 0; i < ModItems.ALL_WEAPONS.size(); i++) items[i] = ModItems.ALL_WEAPONS.get(i).get();
        items[ModItems.ALL_WEAPONS.size()] = ModItems.TEST_FLASK.get();
        // Extension is a fallback only: generated/handheld models never invoke BEWLR.
        // 8 件追忆 + 33 件类型 + 药瓶的旧模型回退：
        // 物品模型 parent 是 builtin/entity，原版 ItemRenderer 对 GUI/地面/展示框/手持
        // 一律调用 renderByItem，所以这一条路径同时覆盖第一人称与第三人称。
        event.registerItem(extensions, items);
    }

    /**
     * Register the equipment 2D state models as additional item models.
     *
     * <p>Provider states are registered only when their required resources exist. This registers
     * runtime standalone baked keys, not shipped wrappers with unconditional external parents.
     */
    @SubscribeEvent
    public static void onRegisterAdditionalModels(
            net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
        // The IDs are read straight from routes/2d.json through the resource manager, so this cannot
        // depend on whether the reload listener has already populated its table.
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        ids.addAll(com.dsh.eldencombat.client.equipment2d.EquipmentRouteSnapshot.ownModelIds());
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft != null && minecraft.getResourceManager() != null) {
            ids.addAll(com.dsh.eldencombat.client.equipment2d.EquipmentRouteSnapshot
                    .additionalModelIds(minecraft.getResourceManager()));
        }
        for (String id : ids) {
            event.register(com.dsh.eldencombat.client.equipment2d.EquipmentRouteSnapshot
                    .standaloneModel(net.minecraft.resources.ResourceLocation.parse(id)));
        }
    }

    /** Hand the fully baked table to the route renderer once baking is complete. */
    @SubscribeEvent
    public static void onBakingCompleted(net.neoforged.neoforge.client.event.ModelEvent.BakingCompleted event) {
        com.dsh.eldencombat.client.equipment2d.EquipmentRouteRenderer.installBakedModels(event.getModels());
        com.dsh.eldencombat.client.equipment2d.EquipmentRouteSnapshot.reportCapabilities();
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SPARK.get(), sprites -> (type, level, x, y, z, dx, dy, dz) -> CombatParticle.create(level, sprites, x, y, z, dx, dy, dz, 0));
        event.registerSpriteSet(ModParticles.DUST.get(), sprites -> (type, level, x, y, z, dx, dy, dz) -> CombatParticle.create(level, sprites, x, y, z, dx, dy, dz, 1));
        event.registerSpriteSet(ModParticles.HEAL.get(), sprites -> (type, level, x, y, z, dx, dy, dz) -> CombatParticle.create(level, sprites, x, y, z, dx, dy, dz, 2));
    }
}
