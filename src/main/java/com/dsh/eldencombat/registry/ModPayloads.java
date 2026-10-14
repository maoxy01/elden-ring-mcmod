package com.dsh.eldencombat.registry;

import com.dsh.eldencombat.network.ClientPayloadTrampoline;
import com.dsh.eldencombat.network.CombatIntentPayload;
import com.dsh.eldencombat.network.CombatStatePayload;
import com.dsh.eldencombat.network.FpSyncPayload;
import com.dsh.eldencombat.network.LockOnPayload;
import com.dsh.eldencombat.network.SkillCastPayload;
import com.dsh.eldencombat.server.ServerCombat;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络负载注册。
 *
 * <p>**客户端方向的包一律两端都注册**：NeoForge 的通道协商要求发送方也知道包存在，
 * 只在客户端注册会让服务端 {@code PacketDistributor.sendToPlayer} 抛
 * "may not be sent to the client"，而该异常发生在登录事件里会直接把玩家踢下线。
 *
 * <p>客户端专属类的引用全部放在 {@link ClientPayloadTrampoline} 里，靠 JVM 的惰性符号解析
 * 保证专用服务器不会加载 {@code com.dsh.eldencombat.client.*}。
 */
public final class ModPayloads {

    private ModPayloads() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("6");
        registrar.playToServer(CombatIntentPayload.TYPE, CombatIntentPayload.STREAM_CODEC, ServerCombat::handleIntent);
        registrar.playToClient(CombatStatePayload.TYPE, CombatStatePayload.STREAM_CODEC,
                ClientPayloadTrampoline::handleCombatState);
        registrar.playToClient(LockOnPayload.TYPE, LockOnPayload.STREAM_CODEC,
                ClientPayloadTrampoline::handleLockOn);
        registrar.playToClient(FpSyncPayload.TYPE, FpSyncPayload.STREAM_CODEC,
                ClientPayloadTrampoline::handleFp);
        registrar.playToClient(SkillCastPayload.TYPE, SkillCastPayload.STREAM_CODEC,
                ClientPayloadTrampoline::handleSkillCast);
        // 赐福旅行请求：这里不做原型开关判断——后端没装载时 GraceOperations.travel 会明确回
        // "赐福系统尚未启用"，而不是静默丢弃请求。
        registrar.playToServer(com.dsh.eldencombat.network.GraceTravelPayload.TYPE,
                com.dsh.eldencombat.network.GraceTravelPayload.STREAM_CODEC,(payload,context)->{
                    if(context.player() instanceof net.minecraft.server.level.ServerPlayer player)
                        context.enqueueWork(()->com.dsh.eldencombat.server.grace.GraceOperations.travel(player,payload.uid(),payload.sequence()));
                });
        registrar.playToClient(com.dsh.eldencombat.network.GraceNodesPayload.TYPE,
                com.dsh.eldencombat.network.GraceNodesPayload.STREAM_CODEC,ClientPayloadTrampoline::handleGraceNodes);
        // §3.5 护符展示数据：客户端 tooltip 只用这份服务端同步过来的数据（不参与结算）。
        registrar.playToClient(com.dsh.eldencombat.network.TalismanDataPayload.TYPE,
                com.dsh.eldencombat.network.TalismanDataPayload.STREAM_CODEC,ClientPayloadTrampoline::handleTalismanData);
        // 赐福菜单：客户端只发「操作 + 选择 ID + 序号」；权威值由服务端现取（GraceMenuActions）。
        registrar.playToClient(com.dsh.eldencombat.network.GraceMenuStatePayload.TYPE,
                com.dsh.eldencombat.network.GraceMenuStatePayload.STREAM_CODEC,ClientPayloadTrampoline::handleGraceMenuState);
        registrar.playToServer(com.dsh.eldencombat.network.GraceMenuActionPayload.TYPE,
                com.dsh.eldencombat.network.GraceMenuActionPayload.STREAM_CODEC,(payload,context)->{
                    // 后端没装载就直接丢弃：不能让这一行把含 Waystones 类型的服务端类加载进来。
                    if(!com.dsh.eldencombat.server.grace.GraceOperations.available())return;
                    if(context.player() instanceof net.minecraft.server.level.ServerPlayer player)
                        context.enqueueWork(()->com.dsh.eldencombat.server.grace.GraceMenuActions.apply(player,
                                payload.containerId(),payload.action(),payload.target(),payload.ashId(),payload.sequence(),
                                payload.heldToken()));
                });
    }
}
