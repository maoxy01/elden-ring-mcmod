package com.dsh.eldencombat.server.grace;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.registry.ModAttachments;
import com.dsh.eldencombat.server.skills.SkillDriver;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.waystones.api.*;
import net.blay09.mods.waystones.api.event.WaystoneTeleportEvent;
import net.blay09.mods.waystones.api.requirement.WarpRequirement;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import com.dsh.eldencombat.network.GraceNodesPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Waystones/Balm 适配层：所有原生旅行入口的薄策略。
 *
 * <p>只在依赖齐备时由 {@link GraceBootstrap} 装载；本类引用 Waystones 类型，
 * 缺依赖时**不能被加载**（判断逻辑放在 {@link GraceDependencies}）。
 */
public final class GraceService {
    private static final Map<UUID,Long> combatUntil=new HashMap<>();
    private static final Map<UUID,Integer> lastSequence=new HashMap<>();
    private static final Map<UUID,WaystoneTeleportContext> pending=new HashMap<>();
    /** pending 的登记时刻；超时或目标节点消失就清理，不能让玩家被"旅行请求正在处理"永久卡住。 */
    private static final Map<UUID,Long> pendingSince=new HashMap<>();
    private static final Map<UUID,Long> cooldownUntil=new HashMap<>();
    private static boolean registered;
    /** 设置永远从 {@link GraceSettings} 现取，/eldencombat reload 后无需重启。 */
    private static GraceSettings settings(){return GraceSettings.current();}
    private GraceService() {}
    public static void register() {
        if(registered)return; registered=true;
        GraceOperations.install(new GraceOperations.Backend() {
            @Override public String rejectLocalUse(ServerPlayer player, UUID uid) {
                var node = WaystonesAPI.getWaystone(player.server, uid);
                return node.isEmpty() ? "赐福已失效" : GraceRest.rejectLocalUse(player, node.get(), node.get().getPos());
            }
            @Override public String rest(ServerPlayer player, UUID uid) {
                var node = WaystonesAPI.getWaystone(player.server, uid);
                return node.isEmpty() ? "赐福已失效" : GraceRest.rest(player, node.get(), node.get().getPos());
            }
            @Override public void travel(ServerPlayer player, UUID uid, int sequence) {
                requestTravel(player, uid, sequence);
            }
        });
        NeoForge.EVENT_BUS.register(new GraceService());
        Balm.getEvents().onEvent(net.blay09.mods.waystones.api.event.WaystoneActivatedEvent.class, event -> {
            if (event.getPlayer() instanceof ServerPlayer player) {
                GraceDiscovery.admit(player, event.getWaystone());
                syncNodes(player);
            }
        });
        Balm.getEvents().onEvent(net.blay09.mods.waystones.api.event.WaystoneRemovedEvent.class, event -> {
            var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null && server.isSameThread())
                GraceRegistry.get(server).unregister(event.getWaystone().getWaystoneUid());
        });
        EldenCombat.LOGGER.info("GRACE_ADAPTER_READY: 赐福适配器已装载（Waystones+Balm）；荒野默认策略，原生 Pre 与 requirements 双重门禁");
        Balm.getEvents().onEvent(WaystoneTeleportEvent.Pre.class,event->{
            var context=event.getContext();
            if (context.getTargetWaystone() != null && context.getEntity() instanceof ServerPlayer player)
                GraceDiscovery.admit(player, context.getTargetWaystone());
            if(context.getTargetWaystone()!=null&&context.getEntity() instanceof ServerPlayer player&&GraceRegistry.get(player.server).contains(context.getTargetWaystone().getWaystoneUid())) {
                String rejection=reject(player,context.getTargetWaystone());
                if(rejection!=null||(pending.containsKey(player.getUUID())&&pending.get(player.getUUID())!=context)) {
                    event.setCanceled(true);feedback(player,rejection==null?"旅行请求正在处理":rejection);
                    return;
                }
                pending.put(player.getUUID(),context);
                pendingSince.put(player.getUUID(),(long)player.server.getTickCount());
                if(!(context.getRequirements() instanceof GraceRequirement))
                    event.setRequirements(new GraceRequirement(context,event.getRequirements()));
            }
        });
        Balm.getEvents().onEvent(WaystoneTeleportEvent.Complete.class,event->{
            var context=event.getContext();
            if(context.getEntity() instanceof ServerPlayer player&&pending.remove(player.getUUID(),context)){
                pendingSince.remove(player.getUUID());
                EldenCombat.LOGGER.info("GRACE_TRAVEL_COMPLETE: player={} op={} uid={} success={} pos={} xp={} error={}",
                        player.getUUID(),player.hasPermissions(2),context.getTargetWaystone().getWaystoneUid(),
                        event.getPrimaryResult().filter(EntityTeleportResult::isSuccessful).isPresent(),player.position(),player.totalExperience,event.getTeleportError());
                if(event.getTeleportError().isEmpty()&&event.getPrimaryResult().filter(EntityTeleportResult::isSuccessful).isPresent())
                    cooldownUntil.put(player.getUUID(),(long)player.server.getTickCount()+settings().cooldownTicks);
            }
        });
    }
    /**
     * 诊断出口：当前挂着几张 pending 票据。
     *
     * <p>用途只有两个：夹具断言「票据确实挂上/确实被清理」，以及线上排障时确认玩家不是被一张
     * 永不失效的票据卡住。**玩法逻辑不读它**——清理规则始终在 {@link #sweepPending} 里。
     */
    public static int pendingCount(){return pending.size();}
    /** 诊断出口：这名玩家当前是否挂着一张 pending 票据。 */
    public static boolean hasPending(ServerPlayer player){return pending.containsKey(player.getUUID());}
    /**
     * 夹具脚手架（**生产路径从不调用**）：挂一张 pending 票据但不真正发起传送，
     * 用来单独验证 {@link #sweepPending} 的清理分支（真实旅行请求正常会在异步完成时立刻清票，
     * 夹具抓不到"挂着的票据"这个中间态）。见 {@code docs/combat-evidence/grace-pending-20261013.md}。
     *
     * @param ageTicks 这张票据"已经挂了多久"（tick）；0 = 刚挂上
     * @return 票据是否挂上（落点不安全等原生条件不满足时返回 false，夹具据此报错而不是静默跳过）
     */
    public static boolean debugHoldPending(ServerPlayer player,Waystone node,int ageTicks) {
        var context=WaystonesAPI.createDefaultTeleportContext(player,node,value->{});
        var accepted=context.left().orElse(null);
        if(accepted==null)return false;
        pending.put(player.getUUID(),accepted);
        pendingSince.put(player.getUUID(),(long)player.server.getTickCount()-ageTicks);
        return true;
    }
    public static String rejectActivity(ServerPlayer player) {
        long now=player.server.getTickCount();
        if(!player.isAlive()||player.hasDisconnected())return "玩家不可旅行";
        if(SkillDriver.cast(player).isCasting()||player.getData(ModAttachments.COMBAT_STATE).isBusy()
                ||now<combatUntil.getOrDefault(player.getUUID(),0L))return "战斗期间不能旅行";
        return null;
    }
    public static String reject(ServerPlayer player,Waystone node) {
        String rejection=rejectActivity(player);
        if(rejection==null)rejection=GraceAccess.rejection(player,node);
        if(rejection!=null)return rejection;
        if(player.server.getTickCount()<cooldownUntil.getOrDefault(player.getUUID(),0L))return "赐福旅行冷却中";
        var level=player.server.getLevel(node.getDimension());
        if(level==null||!level.getWorldBorder().isWithinBounds(node.getPos()))return "目标维度或边界无效";
        if(settings().requireNearby&&WaystonesAPI.getActivatedWaystones(player).stream().noneMatch(
                source->GraceRegistry.get(player.server).contains(source.getWaystoneUid())&&source.getDimension()==player.level().dimension()
                        &&player.distanceToSqr(Vec3.atCenterOf(source.getPos()))<=36))return "服务器要求在赐福旁出发";
        // The native async pipeline loads only the destination chunks. Do not synchronously load all map nodes.
        if(!level.hasChunkAt(node.getPos()))return null;
        var destination=WaystonesAPI.resolveDefaultDestination(level,node);
        if(destination.isEmpty())return "没有合法落点";
        Vec3 pos=destination.get().location();
        var feet=BlockPos.containing(pos);
        if(!level.getFluidState(feet).isEmpty()||!level.getFluidState(feet.above()).isEmpty()
                ||level.getBlockState(feet.below()).getCollisionShape(level,feet.below()).isEmpty()
                ||!level.noCollision(player,AABB.ofSize(pos.add(0,player.getBbHeight()/2,0),player.getBbWidth(),player.getBbHeight(),player.getBbWidth())))
            return "落点不安全";
        return null;
    }
    public static void requestTravel(ServerPlayer player,UUID uid,int sequence) {
        EldenCombat.LOGGER.info("GRACE_TRAVEL_REQUEST: player={} op={} uid={} sequence={} from={} xp={}",player.getUUID(),player.hasPermissions(2),uid,sequence,player.position(),player.totalExperience);
        int previous=lastSequence.getOrDefault(player.getUUID(),-1);
        if(sequence<=previous)return;
        lastSequence.put(player.getUUID(),sequence);
        if(pending.containsKey(player.getUUID())){feedback(player,"旅行请求正在处理");return;}
        var node=WaystonesAPI.getWaystone(player.server,uid);
        if(node.isEmpty()){feedback(player,"未知赐福");return;}
        String rejection=reject(player,node.get());
        if(rejection!=null){feedback(player,rejection);return;}
        var context=WaystonesAPI.createDefaultTeleportContext(player,node.get(),value->{});
        if(context.right().isPresent()){feedback(player,"原生旅行条件未满足："+context.right().get());return;}
        var accepted=context.left().orElseThrow();
        pending.put(player.getUUID(),accepted);
        pendingSince.put(player.getUUID(),(long)player.server.getTickCount());
        WaystonesAPI.tryTeleportAsync(accepted).whenComplete((result,error)->player.server.execute(()->{
            pending.remove(player.getUUID(),accepted);
            pendingSince.remove(player.getUUID());
            // 玩家离线后不得施加任何效果：连接回调只做反馈，不改玩家状态。
            if(!player.hasDisconnected())feedback(player,error!=null?"旅行失败":result.right().isPresent()?"旅行被拒绝："+result.right().get():"已到达赐福");
        }));
    }
    /**
     * 超时或节点已删除/失效的 pending 一律清掉：它是"处理中"的授权，不是永久票据。
     * 先收集再删，不在 removeIf 的判定里改同一个 map（否则迭代器 modCount 冲突）。
     */
    private static void sweepPending(MinecraftServer server,long now){
        if(pendingSince.isEmpty()&&pending.isEmpty())return;
        List<UUID> expired=new ArrayList<>();
        for(var entry:pendingSince.entrySet())
            if(now-entry.getValue()>settings().pendingTimeoutTicks)expired.add(entry.getKey());
        for(UUID id:expired){
            Long since=pendingSince.remove(id);pending.remove(id);
            // 这里必须打**真实存活时长**：以前直接把 now（服务端 tick 计数）写成 age，
            // 日志里会看到"age=400ticks"却不知道上限是 1200，读证据时极易误判（2026-10-13 修）。
            EldenCombat.LOGGER.info("GRACE_PENDING_EXPIRED: player={} age={}ticks limit={}ticks",
                    id,since==null?0L:now-since,settings().pendingTimeoutTicks);
        }
        // 目标节点在旅行途中被删除/私有化：立刻作废，别等超时。
        List<UUID> invalid=new ArrayList<>();
        for(var entry:pending.entrySet()){
            var target=entry.getValue().getTargetWaystone();
            if(target==null||WaystonesAPI.getWaystone(server,target.getWaystoneUid()).isEmpty())invalid.add(entry.getKey());
        }
        for(UUID id:invalid){
            pending.remove(id);pendingSince.remove(id);
            EldenCombat.LOGGER.info("GRACE_PENDING_INVALIDATED: player={}",id);
        }
    }
    @SubscribeEvent public void damage(LivingIncomingDamageEvent event) {
        long duration=settings().combatTicks;
        if(event.getEntity() instanceof ServerPlayer player)combatUntil.put(player.getUUID(),player.server.getTickCount()+duration);
        if(event.getSource().getEntity() instanceof ServerPlayer player)combatUntil.put(player.getUUID(),player.server.getTickCount()+duration);
    }
    /**
     * 赐福方块右键 = 打开赐福菜单（休息 / 旅行 / 战灰装配都在菜单里）。
     *
     * <p>四个条件都满足才抢下这次交互：
     * ①是本项目的赐福节点；②玩家**已经发现**它（未发现的交给 Waystones 自己的激活流程，
     * 别把发现入口堵死）；③不是潜行点击（留给原生交互）；④当前访问权限校验通过。
     * 不满足就不抢，让原生行为照旧，并把原因直接告诉玩家。
     *
     * <p>抢下时必须同时 DENY 掉 useBlock/useItem：否则 Waystones 的原生界面会在事件之后
     * 打开并把我们的菜单覆盖掉。菜单本身不授予任何权限——每次按钮执行都会重新校验。
     */
    @SubscribeEvent public void openGraceMenu(PlayerInteractEvent.RightClickBlock event) {
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        if(event.isCanceled()||player.isShiftKeyDown())return;
        var node=WaystonesAPI.getWaystoneAt(player.serverLevel(),event.getPos());
        node.ifPresent(value -> GraceDiscovery.admit(player, value));
        if(node.isEmpty()||!GraceRegistry.get(player.server).contains(node.get().getWaystoneUid()))return;
        if(!WaystonesAPI.isWaystoneActivated(player,node.get()))return;
        var access=GraceOperations.localAccess(player,node.get().getWaystoneUid());
        if(!access.accepted()){feedback(player,access.reason());return;}
        event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
        event.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);
        event.setCanceled(true);
        GraceMenuActions.open(player,node.get().getWaystoneUid(),node.get().getName().getString());
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        var id=event.getEntity().getUUID();
        combatUntil.remove(id);lastSequence.remove(id);pending.remove(id);pendingSince.remove(id);cooldownUntil.remove(id);
    }
    @SubscribeEvent public void dimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        // 切维度后旧的"处理中"授权不再代表任何进行中的旅行；留着会卡住下一次请求。
        // 但旅行本身就是跨维度：只有"去的不是目标维度"才作废，否则会抢在 Complete 前把票据吃掉，
        // 冷却与 GRACE_TRAVEL_COMPLETE 日志都会丢。
        var id=event.getEntity().getUUID();
        var context=pending.get(id);
        if(context==null)return;
        var target=context.getTargetWaystone();
        if(target!=null&&event.getTo().equals(target.getDimension()))return;
        pending.remove(id);pendingSince.remove(id);
        EldenCombat.LOGGER.info("GRACE_PENDING_DIMENSION_INVALIDATED: player={} from={} to={} targetDim={}",id,event.getFrom().location(),event.getTo().location(),target==null?"<unknown>":target.getDimension().location());
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event){combatUntil.clear();lastSequence.clear();pending.clear();pendingSince.clear();cooldownUntil.clear();}
    @SubscribeEvent public void snapshot(ServerTickEvent.Post event){
        long now=event.getServer().getTickCount();
        if(now%settings().syncTicks==0){
            sweepPending(event.getServer(),now);
            for(var player:event.getServer().getPlayerList().getPlayers())syncNodes(player);
        }
        // 菜单刷新比节点快照密：玩家换手/换槽后，界面上的"手持"与可点状态必须尽快跟上。
        if(now%GraceMenuActions.REFRESH_TICKS==0){
            for(var player:event.getServer().getPlayerList().getPlayers())GraceMenuActions.refresh(player);
        }
    }
    public static void syncNodes(ServerPlayer player){
        // 地图下发与菜单列表共用 GraceAccess.authorizedNodes 这一份授权判定。
        PacketDistributor.sendToPlayer(player,new GraceNodesPayload(GraceAccess.authorizedNodes(player,settings().snapshotLimit)));
    }
    /** Decorates the native costs. Waystones rechecks canAfford after chunk preparation and before consume. */
    private record GraceRequirement(WaystoneTeleportContext context,WarpRequirement nativeRequirement) implements WarpRequirement {
        @Override public boolean canAfford(Player player){
            if(!(player instanceof ServerPlayer serverPlayer))return false;
            var current=WaystonesAPI.getWaystone(serverPlayer.server,context.getTargetWaystone().getWaystoneUid());
            String rejection=current.isEmpty()?"赐福已失效":reject(serverPlayer,current.get());
            if(rejection!=null){feedback(serverPlayer,rejection);return false;}
            return nativeRequirement.canAfford(player);
        }
        @Override public void consume(Player player){nativeRequirement.consume(player);}
        @Override public void rollback(Player player){nativeRequirement.rollback(player);}
        @Override public void appendHoverText(Player player,List<Component> text){nativeRequirement.appendHoverText(player,text);}
    }
    private static void feedback(ServerPlayer player,String message){player.displayClientMessage(Component.literal("[赐福] "+message),true);}
}
