package com.dsh.eldencombat.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端方向负载的公共跳板。
 *
 * <p>存在的理由（踩过的坑，2026-10-11 实机验收发现）：
 * <ol>
 *   <li>NeoForge 的通道协商要求**发送方也知道包存在**，客户端方向的包必须**两端都注册**；
 *       只用 {@code if (dist == CLIENT)} 守卫注册，服务端发送时会抛
 *       {@code UnsupportedOperationException: Payload ... may not be sent to the client!}，
 *       并且该异常发生在 {@code PlayerLoggedInEvent} 里，会把玩家直接踢下线。</li>
 *   <li>但处理器又不能在公共注册处直接引用客户端类：{@code ClientCombat} 引用了
 *       {@code net.minecraft.client.Minecraft}，在专用服务器上加载会 NoClassDefFoundError。</li>
 * </ol>
 * 所以处理器统一写在这里，运行期再做一次 dist 判断。JVM 的符号引用是**首次执行时才解析**，
 * 因此专用服务器永远不会加载 {@code com.dsh.eldencombat.client.*}。
 */
public final class ClientPayloadTrampoline {

    private ClientPayloadTrampoline() {
    }

    public static void handleGraceMenuState(GraceMenuStatePayload payload, IPayloadContext context) {
        if(FMLEnvironment.dist==Dist.CLIENT)
            context.enqueueWork(()->com.dsh.eldencombat.client.grace.GraceScreenState.accept(payload));
    }

    /**
     * §3.5 护符展示数据。客户端类 {@code TalismanClientData} 只依赖纯逻辑的 {@code TalismanSpec}，
     * 专用服务器上加载它没有风险——但仍保持"公共跳板 + 运行期判 dist"的统一写法。
     */
    public static void handleTalismanData(TalismanDataPayload payload, IPayloadContext context) {
        if(FMLEnvironment.dist==Dist.CLIENT)
            context.enqueueWork(()->com.dsh.eldencombat.client.TalismanClientData.replace(payload.entries()));
    }

    public static void handleGraceNodes(GraceNodesPayload payload, IPayloadContext context) {
        // 服务端只在赐福适配器装载后才下发节点，所以这里不需要原型开关；
        // 但客户端地图插件引用 JourneyMap 类型，缺它时绝不能碰 GraceMapPlugin（符号引用是首次执行才解析）。
        if(FMLEnvironment.dist==Dist.CLIENT&&com.dsh.eldencombat.server.grace.GraceDependencies.mapPresent())
            context.enqueueWork(()->com.dsh.eldencombat.client.grace.GraceMapPlugin.replace(payload));
    }

    public static void handleCombatState(CombatStatePayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.dsh.eldencombat.client.ClientCombat.handleState(payload, context);
        }
    }

    public static void handleLockOn(LockOnPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.dsh.eldencombat.client.ClientCombat.handleLockOn(payload, context);
        }
    }

    public static void handleFp(FpSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.dsh.eldencombat.client.ClientSkills.handleFp(payload, context);
        }
    }

    public static void handleSkillCast(SkillCastPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.dsh.eldencombat.client.SkillPoseAnimator.handle(payload, context);
        }
    }
}
