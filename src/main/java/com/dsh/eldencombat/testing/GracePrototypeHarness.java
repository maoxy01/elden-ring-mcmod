package com.dsh.eldencombat.testing;

import com.dsh.eldencombat.EldenCombat;
import net.blay09.mods.waystones.api.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 隔离的依赖与真实节点启动检查；不是地图点击验收。
 *
 * <p>**不用 {@code @EventBusSubscriber}**：FML 的 {@code AutomaticEventSubscriber} 会枚举被注解类的
 * 方法签名，而这个类里有含 Waystones 类型的签名，未装 Waystones 的专用服务器会在
 * {@code AutomaticEventSubscriber.inject} 处 {@code NoClassDefFoundError} 起不来。
 * 所以改为「显式测试模式 + 依赖齐全」时由 {@link #install()} 手动注册——
 * 只在事件方法内部检查布尔值是不够的，反射扫签名发生在方法体执行之前。
 */
public final class GracePrototypeHarness {
    private static boolean registered;
    private static boolean ready;
    private static int ticks;

    private GracePrototypeHarness() {}

    /** 由 {@code EldenCombat.onCommonSetup} 在 DEDICATED_SERVER + 显式测试模式 + 依赖齐全时调用。 */
    public static synchronized void install() {
        if (registered) return;
        if (!Boolean.getBoolean("eldencombat.gracePrototype")) return;
        var missing = com.dsh.eldencombat.server.grace.GraceDependencies.missing();
        if (!missing.isEmpty()) {
            EldenCombat.LOGGER.warn("GRACE_PROTO_SKIPPED: 缺少依赖 {}，专用服务器夹具不注册", missing);
            return;
        }
        registered = true;
        NeoForge.EVENT_BUS.register(new GracePrototypeHarness());
        EldenCombat.LOGGER.info("GRACE_PROTO_HARNESS_REGISTERED: dedicated-server fixture active");
    }

    @SubscribeEvent public void started(ServerStartedEvent event) {
        var server = event.getServer();
        for (String id : new String[]{"waystones", "balm", "journeymap", "commonnetworking"}) {
            if (!ModList.get().isLoaded(id)) throw new IllegalStateException("GRACE_PROTO_FAIL: missing " + id);
            EldenCombat.LOGGER.info("GRACE_DEPENDENCY_PASS: {} {}", id, ModList.get().getModContainerById(id).orElseThrow().getModInfo().getVersion());
        }
        var level = server.overworld();
        for (int x = -4; x <= 24; x++) for (int z = -4; z <= 4; z++) {
            level.setBlockAndUpdate(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState());
            for (int y = 81; y <= 84; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        }
        for (int index = 0; index < 2; index++) {
            var pos = new BlockPos(index * 20, 81, 0);
            // 显式分支而不是 orElseGet(lambda)：lambda 的合成方法签名会把 Waystones 类型带进
            // 反射可见的方法表里，夹具虽然已不靠自动订阅注册，也没必要留这个雷。
            var existing = WaystonesAPI.getWaystoneAt(level, pos);
            Waystone node;
            if (existing.isPresent()) node = existing.get();
            else node = WaystonesAPI.placeWaystone(level, pos, WaystoneStyles.DEFAULT).orElseThrow();
            if (!(node instanceof MutableWaystone mutable)) throw new IllegalStateException("GRACE_PROTO_FAIL: node not mutable");
            mutable.setName(Component.literal("Grace prototype " + (index + 1)));
            com.dsh.eldencombat.server.grace.GraceRegistry.get(server).register(node.getWaystoneUid());
            EldenCombat.LOGGER.info("GRACE_NODE_PASS: uid={} name={} pos={} valid={}", node.getWaystoneUid(), node.getName().getString(), node.getPos(), node.isValid());
        }
        server.saveEverything(false, true, true);
        ready = true;
    }

    @SubscribeEvent public void tick(ServerTickEvent.Post event) {
        if (!ready) return;
        if (++ticks == 40) {
            EldenCombat.LOGGER.info("GRACE_PROTO_SERVER_COMPLETE: two real nodes saved; no player/map interaction asserted");
            event.getServer().halt(false);
        }
    }
}
