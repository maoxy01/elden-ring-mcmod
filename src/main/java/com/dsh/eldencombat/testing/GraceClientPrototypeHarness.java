package com.dsh.eldencombat.testing;

import com.dsh.eldencombat.EldenCombat;
import net.blay09.mods.waystones.api.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.UUID;

/**
 * 真实客户端启动 / 开地图 / 赐福菜单检查；这里的"激活"与"点击"都是**明确标注过的引擎夹具**，
 * 不是鼠标验收（真人鼠标流程仍算未验收，见证据文件）。
 *
 * <p>**不用 {@code @EventBusSubscriber}**：理由见 {@code GracePrototypeHarness}——
 * 自动订阅器会在方法体执行之前反射枚举签名，依赖缺失时专用服务器会因此起不来。
 * 本类由 {@code EldenCombatClient}（仅客户端）在显式测试模式下调用 {@link #install()} 注册。
 */
public final class GraceClientPrototypeHarness {
    private static final boolean MENU=Boolean.getBoolean("eldencombat.graceMenu");
    private static boolean registered;
    private static int ticks;
    private static int menuTick;
    /** 收尾相位等服务端准备界面的上限（**客户端** tick；见 menuPhase 尾部的时钟解耦注释）。 */
    private static final int MENU_WAIT=120;
    private static boolean bindPressed;
    private static boolean bindAsserted;
    private static boolean duplicateChecked;
    private static boolean unbindPressed;
    private static boolean unbindChecked;
    private static boolean finished;
    /** 战斗窗口过后菜单有没有被服务端真的留住（服务端线程读写）。 */
    private static boolean menuReopened;
    private static boolean waitLogged;
    private static UUID hiddenUid;
    private static UUID menuNodeUid;
    private static String menuNodeName;
    /** 界面"当时展示的那把武器"的旧标记：换手后拿它提交必须被拒（§2.3）。 */
    private static String staleHeldToken;
    /** §1.4 pending 清理相位：观察真实票据 → 超时 → 目标节点被删 → 切维度（作废 / 保留两条分支）。 */
    private static boolean pendingObserved,pendingTimeoutCleared,pendingDeletedChecked,pendingDeletedCleared,pendingDimChecked;
    private static int pendingTimeoutSinceStep,pendingDeletedSinceStep;
    private static int realPendingSeen=-1;
    private static UUID tempNodeUid;
    private static volatile boolean ready,failed;
    private static boolean actualTravel,actualRest;
    private GraceClientPrototypeHarness() {}

    public static synchronized void install() {
        if(registered)return;
        if(!Boolean.getBoolean("eldencombat.gracePrototype"))return;
        if(!com.dsh.eldencombat.server.grace.GraceDependencies.present())return;
        registered=true;
        NeoForge.EVENT_BUS.register(new GraceClientPrototypeHarness());
        EldenCombat.LOGGER.info("GRACE_PROTO_CLIENT_REGISTERED: client fixture active");
    }

    @SubscribeEvent public void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(failed)throw new IllegalStateException("GRACE_PROTO_CLIENT_FAIL");
        if(mc.player==null||mc.getSingleplayerServer()==null)return;
        mc.options.pauseOnLostFocus=false;
        if(ticks++==0) mc.getSingleplayerServer().execute(()->{
            try {
                var server=mc.getSingleplayerServer(); var p=server.getPlayerList().getPlayers().getFirst();
                p.setGameMode(GameType.SURVIVAL); p.connection.teleport(Boolean.getBoolean("eldencombat.graceManual")?10:2,81,3,0,0);
                server.getPlayerList().deop(p.getGameProfile());
                if(p.hasPermissions(2))throw new IllegalStateException("GRACE_PROTO_CLIENT_FAIL: player is OP");
                for(int index=0;index<2;index++) {
                    var node=WaystonesAPI.getWaystoneAt(server.overworld(),new BlockPos(index*20,81,0)).orElseThrow();
                    if(Boolean.getBoolean("eldencombat.graceReload")){
                        if(!WaystonesAPI.isWaystoneActivated(p,node))throw new IllegalStateException("GRACE_PROTO_CLIENT_FAIL: discovery did not survive restart");
                        if(!com.dsh.eldencombat.server.grace.GraceRegistry.get(server).contains(node.getWaystoneUid()))throw new IllegalStateException("GRACE_PROTO_CLIENT_FAIL: registry did not survive restart");
                    }else {
                        com.dsh.eldencombat.server.grace.GraceRegistry.get(server).register(node.getWaystoneUid());
                        WaystonesAPI.activateWaystone(p,node);
                    }
                    if(!WaystonesAPI.isWaystoneActivated(p,node))throw new IllegalStateException("Node activation failed");
                    EldenCombat.LOGGER.info("GRACE_CLIENT_NODE_PASS: mode={} player={} op={} uid={} name={}",Boolean.getBoolean("eldencombat.graceReload")?"restart discovery check":"engine activation",p.getUUID(),p.hasPermissions(2),node.getWaystoneUid(),node.getName().getString());
                }
                if(Boolean.getBoolean("eldencombat.graceManual")){
                    com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).setFp(17);
                    com.dsh.eldencombat.server.skills.SkillServerEvents.sync(p);
                    p.giveExperiencePoints(100);
                    EldenCombat.LOGGER.info("GRACE_WILDERNESS_READY: fixture pos={} op=false fp=17 xp={}; actual mouse menu required",p.position(),p.totalExperience);
                }
                ready=true;
            } catch(RuntimeException failure) {failed=true;throw failure;}
        });
        if(MENU){
            // 夹具内的断言失败必须让客户端停下来（否则集成服务器崩了会挂住 gradle 不退出），
            // 同时把原因写进日志，靠 GRACE_MENU_FAIL / GRACE_MENU_COMPLETE 判定结果。
            if(ready) try { menuPhase(mc); } catch(RuntimeException failure) {
                EldenCombat.LOGGER.error("GRACE_MENU_FAIL",failure);
                mc.execute(mc::stop);
            }
            return;
        }
        if(ticks==80&&ready) {
            var key=java.util.Arrays.stream(mc.options.keyMappings).filter(value->value.getName().equals("key.journeymap.map_toggle_alt")).findFirst().orElseThrow();
            KeyMapping.click(key.getKey());
            EldenCombat.LOGGER.info("GRACE_MAP_KEY: registered map_toggle_alt click helper");
        }
        if(ticks==100&&mc.screen!=null&&mc.screen.getClass().getName().contains("AboutScreen")){
            var button=mc.screen.children().stream().filter(value->value instanceof net.minecraft.client.gui.components.AbstractButton)
                    .map(value->(net.minecraft.client.gui.components.AbstractButton)value).filter(value->value.getMessage().getString().equals("Close")).findFirst().orElseThrow();
            button.onPress();
            EldenCombat.LOGGER.info("GRACE_ABOUT_CLOSE: public Close button helper");
        }
        if(ticks==120) {
            if(mc.screen==null||!mc.screen.getClass().getName().toLowerCase().contains("fullscreen"))throw new IllegalStateException("GRACE_PROTO_CLIENT_FAIL: full map did not open: "+(mc.screen==null?"null":mc.screen.getClass().getName()));
            if(com.dsh.eldencombat.client.grace.GraceMapPlugin.markers().size()!=2)throw new IllegalStateException("GRACE_PROTO_CLIENT_FAIL: own markers missing");
            EldenCombat.LOGGER.info("GRACE_MAP_OPEN_PASS: {} via KeyMapping helper; not an actual mouse travel",mc.screen.getClass().getName());
            Screenshot.grab(mc.gameDirectory,"grace-map.png",mc.getMainRenderTarget(),message->EldenCombat.LOGGER.info("GRACE_MAP_CAPTURE: {}",message.getString()));
        }
        if(Boolean.getBoolean("eldencombat.graceManual")&&ticks>145&&ticks%20==0)mc.getSingleplayerServer().execute(()->{
            try{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                if(!actualTravel&&p.getX()>17){
                    actualTravel=true;
                    if(com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).fp()!=17)throw new IllegalStateException("Map travel incorrectly restored FP");
                    EldenCombat.LOGGER.info("GRACE_MOUSE_TRAVEL_OBSERVED: real connection pos={} op={} fp=17; input evidence logged separately",p.position(),p.hasPermissions(2));
                    p.connection.teleport(22,81,0.5,90,17);
                    mc.execute(()->mc.setScreen(null));
                }
                if(actualTravel&&!actualRest&&com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).fp()==com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).maxFp()){
                    actualRest=true;
                    EldenCombat.LOGGER.info("GRACE_MOUSE_REST_OBSERVED: FP restored after block interaction; input evidence logged separately");
                    p.connection.teleport(10,81,3,0,0);
                    com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).setFp(37);
                    com.dsh.eldencombat.server.skills.SkillServerEvents.sync(p);
                    mc.getSingleplayerServer().saveEverything(false,true,true);
                    EldenCombat.LOGGER.info("GRACE_PROTO_CLIENT_COMPLETE: physical mouse travel/rest; FP37 saved for next restart");
                    mc.execute(mc::stop);
                }
            }catch(RuntimeException failure){failed=true;throw failure;}
        });
        if(ticks==12000&&Boolean.getBoolean("eldencombat.graceManual"))throw new IllegalStateException("GRACE_PROTO_CLIENT_FAIL: manual travel/rest timed out");
        if(ticks==145&&!Boolean.getBoolean("eldencombat.graceManual")) mc.getSingleplayerServer().execute(()->{
            mc.getSingleplayerServer().saveEverything(false,true,true);
            EldenCombat.LOGGER.info("GRACE_PROTO_CLIENT_COMPLETE: map opened; real player discovery saved; travel not tested");
            mc.execute(mc::stop);
        });
    }

    /**
     * §1.3 赐福菜单夹具（**引擎夹具，不是鼠标验收**）。真实客户端 + 集成服务器，
     * 通过真实界面控件的 {@code onPress()} 触发真实网络包，逐项验证服务端校验：
     * 打开菜单 → 点"休息"生效 → 伪造/过期 containerId 被丢弃 → 未发现节点不许旅行 → 超距离休息被拒。
     */
    private static void menuPhase(Minecraft mc){
        var server=mc.getSingleplayerServer();
        int step=++menuTick;
        if(step>900){
            EldenCombat.LOGGER.error("GRACE_MENU_FAIL: timed out at step {}",step);
            mc.execute(mc::stop);
            return;
        }
        if(step==2)server.execute(()->guard(mc,()->{
            var p=server.getPlayerList().getPlayers().getFirst();
            if(!p.isAlive()){
                // 测试前置：上一次运行可能把这个世界留在"玩家 0 血、半空中"的状态上（夹具自己的超距离用例
                // 会把玩家扔到 x=200 的半空，摔伤会累积到存盘里）。这时**走原版重生路径**，
                // 客户端才会退出死亡界面；只 setHealth 治不好客户端的 DeathScreen。
                EldenCombat.LOGGER.warn("GRACE_MENU_RESET: 世界里的玩家是 0 血（上次运行遗留），走原版重生路径复位");
                p.connection.handleClientCommand(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(
                        net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                p=server.getPlayerList().getPlayers().getFirst();
            }
            // 测试前置（不是玩法来源）：本夹具复用同一份世界，上一次运行可能留下低血/着火/饥饿/摔落状态，
            // 让"菜单有没有打开"这类断言失真（2026-10-13 实跑出现过客户端开局就是 DeathScreen）。
            // 开局记录一次**进来时**的状态，再显式复位，这样下次真出问题能直接看到入口状态。
            EldenCombat.LOGGER.info("GRACE_MENU_RESET(before): health={}/{} food={} fire={} onGround={} pos={}",
                    p.getHealth(),p.getMaxHealth(),p.getFoodData().getFoodLevel(),p.isOnFire(),p.onGround(),p.position());
            p.setHealth(p.getMaxHealth());
            p.getFoodData().setFoodLevel(20);
            p.getFoodData().setSaturation(5f);
            p.clearFire();
            p.setAirSupply(p.getMaxAirSupply());
            p.fallDistance=0;
            var nodes=com.dsh.eldencombat.server.grace.GraceAccess.authorizedNodes(p,256);
            if(nodes.size()!=2)throw new IllegalStateException("GRACE_MENU_FAIL: authorized nodes="+nodes.size());
            // 第三个节点：注册成赐福但**不激活**（玩家未发现）→ 菜单不披露它，也不许旅行过去。
            var level=server.overworld();
            for(int y=80;y<=84;y++)level.setBlockAndUpdate(new BlockPos(40,y,0),
                    y==80?net.minecraft.world.level.block.Blocks.STONE.defaultBlockState():net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            var hidden=WaystonesAPI.placeWaystone(level,new BlockPos(40,81,0),WaystoneStyles.DEFAULT).orElseThrow();
            com.dsh.eldencombat.server.grace.GraceRegistry.get(server).register(hidden.getWaystoneUid());
            hiddenUid=hidden.getWaystoneUid();
            menuNodeUid=nodes.getFirst().uid();
            menuNodeName=nodes.getFirst().name();
            // 夹具自带前置：清掉上一次运行留在同一个世界里的解锁与背包，让断言不依赖历史状态。
            // （这是**测试前置**，不是玩法来源——本夹具真正要验的解锁来自 step 75 的击杀。）
            for(var entry:com.dsh.eldencombat.server.skills.OrdinaryAshCatalog.entries())
                com.dsh.eldencombat.server.skills.AshUnlocks.revoke(p,entry.id());
            p.getInventory().clearContent();
            p.getInventory().selected=0;
            if(!com.dsh.eldencombat.server.skills.AshUnlocks.list(p).isEmpty())
                throw new IllegalStateException("GRACE_MENU_FAIL: 前置清空失败，仍持有解锁");
            p.connection.teleport(0.5,81,1.5,0,0);
            com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).setFp(5);
            com.dsh.eldencombat.server.skills.SkillServerEvents.sync(p);
            // §1.5 前置：把"休息默认不恢复"的资源弄成非满值，用来证明 KEEP 规则真的按配置生效
            // （而不是硬编码"什么都补满"）。step 40 的休息断言会核对它们**没被补满**。
            var combatState=p.getData(com.dsh.eldencombat.registry.ModAttachments.COMBAT_STATE);
            combatState.stamina=1f;
            combatState.potionCharges=0;
            p.setHealth(7f);
            EldenCombat.LOGGER.info("GRACE_MENU_REST_PRECOND: health={}/{} stamina={} flasks={}（默认规则：FP=FULL，其余 KEEP）",
                    p.getHealth(),p.getMaxHealth(),combatState.stamina,combatState.potionCharges);
            com.dsh.eldencombat.server.grace.GraceMenuActions.open(p,nodes.getFirst().uid(),nodes.getFirst().name());
            EldenCombat.LOGGER.info("GRACE_MENU_OPEN_REQUEST: fp=5 authorized={} hidden={}",nodes.size(),hiddenUid);
        }));
        if(step==15){
            // 测试前置：上一次运行可能把这个世界留在"玩家死亡"状态上，客户端就停在死亡界面；
            // step 2 已在服务端把玩家复位成存活，这里清掉客户端残留的死亡界面（不是玩法路径）。
            if(mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen
                    &&mc.player!=null&&mc.player.isAlive()){
                EldenCombat.LOGGER.warn("GRACE_MENU_RESET: 清掉上次运行遗留的客户端死亡界面（服务端玩家已复位为存活）");
                mc.setScreen(null);
            }
        }
        if(step==20){
            if(!(mc.screen instanceof com.dsh.eldencombat.client.grace.GraceScreen))
                throw new IllegalStateException("GRACE_MENU_FAIL: screen="+(mc.screen==null?"null":mc.screen.getClass().getName()));
            var state=com.dsh.eldencombat.client.grace.GraceScreenState.latest();
            if(state.nodes().size()!=2)throw new IllegalStateException("GRACE_MENU_FAIL: menu disclosed "+state.nodes().size()+" nodes");
            if(state.ashes().size()!=com.dsh.eldencombat.server.skills.OrdinaryAshCatalog.entries().size())
                throw new IllegalStateException("GRACE_MENU_FAIL: menu ashes="+state.ashes().size()
                        +" catalog="+com.dsh.eldencombat.server.skills.OrdinaryAshCatalog.entries().size());
            EldenCombat.LOGGER.info("GRACE_MENU_SCREEN_PASS: nodes={} ashes={} fp={}/{} held={} rejection={}",
                    state.nodes().size(),state.ashes().size(),state.fp(),state.maxFp(),state.heldItem(),state.heldRejection());
            press(mc,"休息（恢复 FP）");
        }
        if(step==30){
            // 空手时战灰按钮应显示"未获得"（§2.1 没解锁），点它必须被拥有权校验挡住而不是静默装上。
            pressNth(mc,0,"未获得");
        }
        if(step==35){
            // 必须赶在 step 40 的"超距离"用例之前读：那一步会用"需要靠近赐福休息"覆盖 notice。
            var state=com.dsh.eldencombat.client.grace.GraceScreenState.latest();
            if(!state.notice().startsWith("尚未获得此战灰"))
                throw new IllegalStateException("GRACE_MENU_FAIL: unowned ash was not refused, notice="+state.notice());
            EldenCombat.LOGGER.info("GRACE_MENU_OWNERSHIP_PASS: 未解锁战灰被拒，notice={}",state.notice());
        }
        if(step==40)server.execute(()->guard(mc,()->{
            var p=server.getPlayerList().getPlayers().getFirst();
            var fp=com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p);
            if(fp.fp()!=fp.maxFp())throw new IllegalStateException("GRACE_MENU_FAIL: rest did not restore fp="+fp.fp());
            EldenCombat.LOGGER.info("GRACE_MENU_REST_PASS: fp={}/{}",Math.round(fp.fp()),Math.round(fp.maxFp()));
            // §1.5 规则集中在 config/eldencombat-grace.json：默认只有 FP=FULL，生命/精力/药瓶是 KEEP。
            // 断言"**没**被补满"而不是"等于某个精确值"——精力会自然回复、生命会自然再生，精确值会假失败。
            var restState=p.getData(com.dsh.eldencombat.registry.ModAttachments.COMBAT_STATE);
            if(p.getHealth()>=p.getMaxHealth())
                throw new IllegalStateException("GRACE_MENU_FAIL: rest refilled health although the rule is KEEP");
            if(restState.stamina>=EldenCombat.config().stamina.max)
                throw new IllegalStateException("GRACE_MENU_FAIL: rest refilled stamina although the rule is KEEP");
            if(restState.potionCharges!=0)
                throw new IllegalStateException("GRACE_MENU_FAIL: rest refilled flasks although the rule is KEEP");
            EldenCombat.LOGGER.info("GRACE_MENU_REST_KEEP_PASS: 休息只按配置补 FP；生命 {}/{}、精力 {}、药瓶 {} 都按 KEEP 保持未满",
                    p.getHealth(),p.getMaxHealth(),restState.stamina,restState.potionCharges);
            if(!(p.containerMenu instanceof com.dsh.eldencombat.server.grace.GraceMenu))
                throw new IllegalStateException("GRACE_MENU_FAIL: menu closed unexpectedly: "+p.containerMenu.getClass().getName());
            int menuId=p.containerMenu.containerId;
            // ① 伪造/过期 containerId：必须被丢弃（不认"打开过菜单"，也不认不在菜单里的请求）
            fp.setFp(5);
            com.dsh.eldencombat.server.grace.GraceMenuActions.apply(p,menuId+999,
                    com.dsh.eldencombat.network.GraceMenuActionPayload.REST,null,"",1,
                    com.dsh.eldencombat.server.grace.GraceMenuActions.heldToken(p));
            if(fp.fp()!=5f)throw new IllegalStateException("GRACE_MENU_FAIL: forged containerId accepted, fp="+fp.fp());
            EldenCombat.LOGGER.info("GRACE_MENU_FORGERY_REJECTED: stale containerId ignored, fp still 5");
            // ② 未发现（未授权披露）的节点：不许旅行
            com.dsh.eldencombat.server.grace.GraceMenuActions.apply(p,menuId,
                    com.dsh.eldencombat.network.GraceMenuActionPayload.TRAVEL,hiddenUid,"",2,
                    com.dsh.eldencombat.server.grace.GraceMenuActions.heldToken(p));
            if(p.getX()>17)throw new IllegalStateException("GRACE_MENU_FAIL: traveled to an unauthorized node");
            EldenCombat.LOGGER.info("GRACE_MENU_TRAVEL_REJECTED: undiscovered node refused, pos={}",p.position());
            // ③ 超出有效距离的休息：必须被拒（同一 tick 内调用，避免被"自动关菜单"抢先）
            fp.setFp(5);
            p.connection.teleport(200,81,0,0,0);
            com.dsh.eldencombat.server.grace.GraceMenuActions.apply(p,menuId,
                    com.dsh.eldencombat.network.GraceMenuActionPayload.REST,null,"",3,
                    com.dsh.eldencombat.server.grace.GraceMenuActions.heldToken(p));
            if(fp.fp()!=5f)throw new IllegalStateException("GRACE_MENU_FAIL: out-of-range rest applied, fp="+fp.fp());
            EldenCombat.LOGGER.info("GRACE_MENU_RANGE_REJECTED: out-of-range rest refused, fp still 5");
            p.connection.teleport(0.5,81,1.5,0,0);
            // 测试前置：超距离用例刚把玩家扔到半空，回来时清掉摔落距离并补满，避免这次跳跃
            // 变成"写进存档的摔伤"（2026-10-13 实跑就是靠它累积到 0 血，把下一次运行的开局搞坏）。
            p.fallDistance=0;
            p.setHealth(p.getMaxHealth());
            server.saveEverything(false,true,true);
            // 距离测试把玩家挪走后菜单已被 stillValid 关掉，这里重新打开，好继续测"旅行入口"的正常路径。
            com.dsh.eldencombat.server.grace.GraceMenuActions.open(p,menuNodeUid,menuNodeName);
        }));
        if(step==50){
            // 已授权节点的旅行按钮：只断言"授权通过并已提交"，不依赖 Waystones 异步落地（那属于框架既有的未验收项）。
            // 按序号点：按钮文案里的节点名会被截断（"Grace prototy…  overworld"），名字前缀匹配不可靠。
            pressNth(mc,1,"overworld");
        }
        if(step==70){
            var state=com.dsh.eldencombat.client.grace.GraceScreenState.latest();
            if(!state.notice().startsWith("已提交旅行请求"))
                throw new IllegalStateException("GRACE_MENU_FAIL: authorized travel not dispatched, notice="+state.notice());
            EldenCombat.LOGGER.info("GRACE_MENU_TRAVEL_DISPATCH_PASS: authorized node accepted, notice={}",state.notice());
        }
        if(step==75)server.execute(()->guard(mc,()->{
            // §2.3 成功路径：**用玩法内来源解锁**——击杀玛尔基特（AshRewardTable 里配置的那条），不用管理命令。
            // 槽 0 放大剑（斩劈系，与 stamp_upward_cut 兼容）、槽 1 放刀，后面靠切槽制造"换手"。
            var p=server.getPlayerList().getPlayers().getFirst();
            var boss=com.dsh.eldencombat.registry.ModEntities.MARGIT.get().create(p.serverLevel());
            if(boss==null)throw new IllegalStateException("GRACE_MENU_FAIL: 无法创建玛尔基特");
            boss.moveTo(p.getX(),p.getY(),p.getZ()+4,0,0);
            boss.setNoAi(true);
            p.serverLevel().addFreshEntity(boss);
            boss.invulnerableTime=0;
            boss.hurt(p.damageSources().playerAttack(p),10000f);
            if(boss.isAlive())throw new IllegalStateException("GRACE_MENU_FAIL: 玛尔基特没死");
            p.getInventory().setItem(0,new net.minecraft.world.item.ItemStack(com.dsh.eldencombat.registry.ModItems.GREAT_SWORD.get()));
            p.getInventory().setItem(1,new net.minecraft.world.item.ItemStack(com.dsh.eldencombat.registry.ModItems.KATANA.get()));
            p.getInventory().selected=0;
            staleHeldToken=com.dsh.eldencombat.server.grace.GraceMenuActions.heldToken(p);
            com.dsh.eldencombat.server.grace.GraceMenuActions.refresh(p);
            EldenCombat.LOGGER.info("GRACE_MENU_REWARD_UNLOCK: 击杀玛尔基特解锁 stamp_upward_cut 并换上大剑（玩法内来源，非管理命令）token={}",staleHeldToken);
        }));
        if(step==120)server.execute(()->guard(mc,()->{
            // 击杀 Boss 会让玩家进入 200 tick 的"战斗期间"；菜单的 stillValid 每 tick 复核战斗状态，
            // 因此**打开中的菜单会被服务端强制关掉**。这条规则就在战斗窗口内断言，
            // 而不是像以前那样等到 step 290 重开时"顺带"证明。
            var p=server.getPlayerList().getPlayers().getFirst();
            if(p.containerMenu instanceof com.dsh.eldencombat.server.grace.GraceMenu)
                throw new IllegalStateException("GRACE_MENU_FAIL: 战斗期间菜单本该已被关闭");
            EldenCombat.LOGGER.info("GRACE_MENU_COMBAT_AUTOCLOSE_PASS: 战斗期间服务端把菜单关了（stillValid 每 tick 复核战斗状态）");
        }));
        if(step>=290&&!bindPressed)server.execute(()->guard(mc,()->{
            // 战斗窗口在**服务端 tick** 上计时（combatUntil = serverTick + 200），而夹具的 step 走客户端
            // tick，两者不保证对齐；固定"290 重开一次"在服务端落后时会被 stillValid 立刻关掉，
            // 于是客户端从来没有菜单（2026-10-13 实跑就是这样：REOPEN 打了日志，按钮却一直没出现）。
            // 改成轮询重开，直到服务端真的留住它。
            var p=server.getPlayerList().getPlayers().getFirst();
            if(p.containerMenu instanceof com.dsh.eldencombat.server.grace.GraceMenu){
                if(!menuReopened){
                    menuReopened=true;
                    EldenCombat.LOGGER.info("GRACE_MENU_REOPEN_AFTER_COMBAT: 战斗窗口过后重开菜单（第 {} 步才留住）",step);
                }
                return;
            }
            com.dsh.eldencombat.server.grace.GraceMenuActions.open(p,menuNodeUid,menuNodeName);
            if(!(p.containerMenu instanceof com.dsh.eldencombat.server.grace.GraceMenu))waitOrFail(step,290,"战斗窗口结束后重开菜单");
        }));
        // 相位收尾（300 起）刻意**不钉死在固定 step 上**：夹具 step 走**客户端 tick**，而"重开菜单 /
        // 装配回执 / 卸除按钮"都由**服务端线程**准备；服务端落后时 step 300 会先到，于是误报
        // "matched buttons=0"（2026-10-13 实跑发生过，同一份代码重跑又通过）。
        // 这里改成"按钮出现就按、回执到达就断言"，每个等待上限 MENU_WAIT 个客户端 tick，超时照样失败。
        if(step>=300&&!bindPressed){
            if(!waitLogged){
                waitLogged=true;
                var st=com.dsh.eldencombat.client.grace.GraceScreenState.latest();
                EldenCombat.LOGGER.info("GRACE_MENU_WAIT: screen={} notice={} ashes={} nodes={} held={}",
                        mc.screen==null?"null":mc.screen.getClass().getSimpleName(),st.notice(),st.ashes().size(),
                        st.nodes().size(),st.heldItem());
            }
            if(hasButton(mc,"可装配")){ pressNth(mc,0,"可装配"); bindPressed=true; }
            else waitOrFail(step,300,"\"可装配\"按钮");
        }
        if(bindPressed&&!bindAsserted){
            var state=com.dsh.eldencombat.client.grace.GraceScreenState.latest();
            if(state.notice().startsWith("已装配战灰")){
                if(!state.notice().contains("stamp_upward_cut"))
                    throw new IllegalStateException("GRACE_MENU_FAIL: bound the wrong ash, notice="+state.notice());
                EldenCombat.LOGGER.info("GRACE_MENU_BIND_PASS: 玩法内解锁的战灰经菜单装配成功，notice={}",state.notice());
                bindAsserted=true;
            } else waitOrFail(step,300,"装配回执");
        }
        if(bindAsserted&&!duplicateChecked)server.execute(()->guard(mc,()->{
            var p=server.getPlayerList().getPlayers().getFirst();
            int menuId=p.containerMenu.containerId;
            // ① 重复点击：第二次点"装配"（不是替换）必须给出可读的空操作，不能又装一遍也不能静默失败
            com.dsh.eldencombat.server.grace.GraceMenuActions.apply(p,menuId,
                    com.dsh.eldencombat.network.GraceMenuActionPayload.BIND,null,"eldencombat:stamp_upward_cut",9,
                    com.dsh.eldencombat.server.grace.GraceMenuActions.heldToken(p));
            String duplicate=((com.dsh.eldencombat.server.grace.GraceMenu)p.containerMenu).notice();
            if(!duplicate.contains("已经绑定了战灰"))
                throw new IllegalStateException("GRACE_MENU_FAIL: duplicate bind not reported, notice="+duplicate);
            EldenCombat.LOGGER.info("GRACE_MENU_DUPLICATE_PASS: 重复装配被当作可读空操作，notice={}",duplicate);
            // ② 换手：拿"界面当时展示的那把"的旧 token 提交，必须被拒，且不能装到后来拿起的武器上
            p.getInventory().selected=1;
            com.dsh.eldencombat.server.grace.GraceMenuActions.apply(p,menuId,
                    com.dsh.eldencombat.network.GraceMenuActionPayload.BIND,null,"eldencombat:stamp_upward_cut",10,
                    staleHeldToken);
            String switched=((com.dsh.eldencombat.server.grace.GraceMenu)p.containerMenu).notice();
            if(!switched.contains("主手武器已变化"))
                throw new IllegalStateException("GRACE_MENU_FAIL: stale held weapon was accepted, notice="+switched);
            if(com.dsh.eldencombat.server.skills.AshBindings.read(p.getMainHandItem())!=null)
                throw new IllegalStateException("GRACE_MENU_FAIL: 战灰被装到了后来拿起的武器上");
            EldenCombat.LOGGER.info("GRACE_MENU_HELD_SWITCH_REJECTED: 换手后旧 token 被拒且未误改新武器，notice={}",switched);
            p.getInventory().selected=0;
            duplicateChecked=true;
        }));
        if(duplicateChecked&&!unbindPressed){
            if(hasButton(mc,"卸除战灰")){ press(mc,"卸除战灰"); unbindPressed=true; }
            else waitOrFail(step,300,"\"卸除战灰\"按钮");
        }
        if(unbindPressed&&!unbindChecked)server.execute(()->guard(mc,()->{
            var p=server.getPlayerList().getPlayers().getFirst();
            if(com.dsh.eldencombat.server.skills.AshBindings.read(p.getMainHandItem())!=null)
                throw new IllegalStateException("GRACE_MENU_FAIL: 卸除后仍绑着战灰");
            EldenCombat.LOGGER.info("GRACE_MENU_UNBIND_PASS: 菜单卸除成功，主手不再绑战灰");
            // ③ 进入战斗：战斗期间休息必须被拒（每次执行重新校验战斗状态）
            var fp=com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p);
            fp.setFp(5);
            p.invulnerableTime=0;
            p.hurt(p.damageSources().generic(),1f);
            com.dsh.eldencombat.server.grace.GraceMenuActions.apply(p,p.containerMenu.containerId,
                    com.dsh.eldencombat.network.GraceMenuActionPayload.REST,null,"",11,
                    com.dsh.eldencombat.server.grace.GraceMenuActions.heldToken(p));
            if(fp.fp()!=5f)throw new IllegalStateException("GRACE_MENU_FAIL: rest applied while in combat, fp="+fp.fp());
            EldenCombat.LOGGER.info("GRACE_MENU_COMBAT_REJECTED: 战斗期间休息被拒、fp 仍为 5");
            unbindChecked=true;
        }));
        // ── §1.4 pending 清理 ────────────────────────────────────────────────────────────────
        // 真实旅行请求正常会在异步完成时立刻清票，夹具抓不到"票据挂着"的中间态，所以这里用
        // GraceService.debugHoldPending 这个**明确标注的脚手架**挂票，再验三条清理分支。
        // 「掉线」「关服」两条在单玩家夹具里制造不出来（见证据文件的未验收清单）。
        // 夹具 step 走客户端 tick、清理走服务端 tick（syncTicks=40），所以每条都轮询等结果，不钉死步号。
        if(step>=350&&unbindChecked&&!pendingObserved)server.execute(()->guard(mc,()->{
            realPendingSeen=com.dsh.eldencombat.server.grace.GraceService.pendingCount();
            EldenCombat.LOGGER.info("GRACE_PENDING_OBSERVED: 真实旅行请求走完后仍挂着的 pending 票据数={}",realPendingSeen);
            pendingObserved=true;
            // ① 超时分支：挂一张"已经超时"的票据（目标节点仍然存在 → 只有超时判定能清掉它）
            var p=server.getPlayerList().getPlayers().getFirst();
            var node=WaystonesAPI.getWaystone(server,menuNodeUid)
                    .orElseThrow(()->new IllegalStateException("GRACE_MENU_FAIL: 目标赐福节点消失了"));
            int timeout=com.dsh.eldencombat.server.grace.GraceSettings.current().pendingTimeoutTicks;
            if(!com.dsh.eldencombat.server.grace.GraceService.debugHoldPending(p,node,timeout+2))
                throw new IllegalStateException("GRACE_MENU_FAIL: 超时用例没能挂上票据（原生条件不满足）");
            pendingTimeoutSinceStep=step;
            EldenCombat.LOGGER.info("GRACE_PENDING_TIMEOUT_ARMED: 挂一张 age={}tick 的票据（上限 {}），等 sweep 清理",timeout+2,timeout);
        }));
        if(pendingObserved&&!pendingTimeoutCleared)server.execute(()->guard(mc,()->{
            var p=server.getPlayerList().getPlayers().getFirst();
            if(com.dsh.eldencombat.server.grace.GraceService.hasPending(p)){ waitOrFail(step,pendingTimeoutSinceStep,"超时清理 pending 票据"); return; }
            EldenCombat.LOGGER.info("GRACE_PENDING_TIMEOUT_PASS: 超时票据被 sweep 清掉（目标节点仍在，只有超时判定能清它）");
            pendingTimeoutCleared=true;
            // ② 目标节点被删：夹具自己的临时赐福（(60,81,0)，**不注册**进 GraceRegistry），删掉方块后必须立刻作废票据
            var level=server.overworld();
            for(int y=80;y<=84;y++)level.setBlockAndUpdate(new BlockPos(60,y,0),
                    y==80?net.minecraft.world.level.block.Blocks.STONE.defaultBlockState():net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            var temp=WaystonesAPI.placeWaystone(level,new BlockPos(60,81,0),WaystoneStyles.DEFAULT).orElseThrow();
            if(!com.dsh.eldencombat.server.grace.GraceService.debugHoldPending(p,temp,0))
                throw new IllegalStateException("GRACE_MENU_FAIL: 删除用例没能挂上票据");
            level.removeBlock(new BlockPos(60,81,0),false);
            tempNodeUid=temp.getWaystoneUid();
            pendingDeletedSinceStep=step;
            pendingDeletedChecked=true;
            EldenCombat.LOGGER.info("GRACE_PENDING_DELETE_ARMED: 临时赐福 {} 的方块已删除，等 sweep 作废票据",tempNodeUid);
        }));
        if(pendingDeletedChecked&&!pendingDeletedCleared)server.execute(()->guard(mc,()->{
            var p=server.getPlayerList().getPlayers().getFirst();
            // 前提先成立：方块删掉之后 Waystones 必须查不到这条赐福（否则本用例验不到东西）。
            if(WaystonesAPI.getWaystone(server,tempNodeUid).isPresent()){
                waitOrFail(step,pendingDeletedSinceStep,"删除后的赐福从 Waystones 查询里消失");
                return;
            }
            if(com.dsh.eldencombat.server.grace.GraceService.hasPending(p)){ waitOrFail(step,pendingDeletedSinceStep,"作废已删除节点的票据"); return; }
            EldenCombat.LOGGER.info("GRACE_PENDING_DELETED_PASS: 目标赐福在旅行途中被删 → 票据立刻作废（不等超时）");
            pendingDeletedCleared=true;
            // ③ 切维度：`PlayerChangedDimensionEvent` 的两条分支（作废 / 保留）按事件级断言逐条核。
            //    这里**不真的切维度**（真切维度会把夹具拖进原版 isChangingDimension 无敌态，见 ClientSmokeHarness 的坑），
            //    只投递同一个事件对象来驱动处理器——所以日志里写明是事件级验证。
            var node=WaystonesAPI.getWaystone(server,menuNodeUid).orElseThrow();
            if(!com.dsh.eldencombat.server.grace.GraceService.debugHoldPending(p,node,0))
                throw new IllegalStateException("GRACE_MENU_FAIL: 切维度用例没能挂上票据");
            NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent(
                    p,net.minecraft.world.level.Level.OVERWORLD,net.minecraft.world.level.Level.NETHER));
            if(com.dsh.eldencombat.server.grace.GraceService.hasPending(p))
                throw new IllegalStateException("GRACE_MENU_FAIL: 切到非目标维度后票据还在");
            EldenCombat.LOGGER.info("GRACE_PENDING_DIMENSION_PASS: 切到非目标维度 → 票据作废（事件级验证，不是真切换维度）");
            if(!com.dsh.eldencombat.server.grace.GraceService.debugHoldPending(p,node,0))
                throw new IllegalStateException("GRACE_MENU_FAIL: 保留分支没能挂上票据");
            NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent(
                    p,net.minecraft.world.level.Level.NETHER,net.minecraft.world.level.Level.OVERWORLD));
            if(!com.dsh.eldencombat.server.grace.GraceService.hasPending(p))
                throw new IllegalStateException("GRACE_MENU_FAIL: 目标维度就是目的维度时票据被误清（会抢在旅行 Complete 之前吃掉票据）");
            EldenCombat.LOGGER.info("GRACE_PENDING_DIMENSION_KEEP_PASS: 目的维度=目标维度 → 票据保留（不抢在 Complete 前吃掉它）");
            pendingDimChecked=true;
        }));
        if(step>=350&&unbindChecked&&pendingDimChecked&&!finished){
            finished=true;
            server.execute(()->{
                var p=server.getPlayerList().getPlayers().getFirst();
                // 收尾（测试前置）：把玩家放回地面、补满、清摔落距离再存盘，
                // 否则下一次运行会带着"半空中 + 低血"开局，最后演变成开局就是 0 血。
                p.connection.teleport(0.5,81,1.5,0,0);
                p.fallDistance=0;
                p.setHealth(p.getMaxHealth());
                server.saveEverything(false,true,true);
                EldenCombat.LOGGER.info("GRACE_MENU_COMPLETE: engine fixture (programmatic button press); real mouse clicks still unverified");
                mc.execute(mc::stop);
            });
        }
    }

    /** 客户端界面上有没有文案包含 {@code label} 的原版按钮（用来等服务端把菜单准备好）。 */
    private static boolean hasButton(Minecraft mc,String label){
        if(mc.screen==null)return false;
        return mc.screen.children().stream()
                .filter(value->value instanceof net.minecraft.client.gui.components.AbstractButton)
                .map(value->(net.minecraft.client.gui.components.AbstractButton)value)
                .anyMatch(value->value.getMessage().getString().contains(label));
    }

    /** 服务端没在 {@code MENU_WAIT} 个客户端 tick 内把 {@code what} 准备好，就按失败处理（不放水）。 */
    private static void waitOrFail(int step,int since,String what){
        if(step-since>MENU_WAIT)
            throw new IllegalStateException("GRACE_MENU_FAIL: 等待 "+what+" 超过 "+MENU_WAIT
                    +" 个客户端 tick（服务端没有把菜单/回执准备好）");
    }

    /** 服务端线程上的夹具体：任何失败都写日志并让客户端停下，避免挂住 gradle。 */
    private static void guard(Minecraft mc,Runnable body){
        try { body.run(); } catch(RuntimeException failure) {
            EldenCombat.LOGGER.error("GRACE_MENU_FAIL",failure);
            mc.execute(mc::stop);
        }
    }

    /** 按序号点第 n 个"旅行"按钮（旅行按钮是唯一带维度后缀的那种）。夹具标记：不是鼠标点击。 */
    private static void pressNth(Minecraft mc,int index,String mustContain){
        if(mc.screen==null)throw new IllegalStateException("GRACE_MENU_FAIL: no screen for travel button "+index);
        var buttons=mc.screen.children().stream()
                .filter(value->value instanceof net.minecraft.client.gui.components.AbstractButton)
                .map(value->(net.minecraft.client.gui.components.AbstractButton)value)
                .filter(value->value.getMessage().getString().contains(mustContain))
                .toList();
        if(buttons.size()<=index)throw new IllegalStateException("GRACE_MENU_FAIL: matched buttons="+buttons.size()+" need index "+index);
        var button=buttons.get(index);
        button.onPress();
        EldenCombat.LOGGER.info("GRACE_MENU_BUTTON: pressed [{}] {}",index,button.getMessage().getString());
    }

    /** 用原版控件的公开回调触发真实按钮逻辑——夹具标记：不是鼠标点击。 */
    private static void press(Minecraft mc,String label){
        if(mc.screen==null)throw new IllegalStateException("GRACE_MENU_FAIL: no screen to press "+label);
        var button=mc.screen.children().stream()
                .filter(value->value instanceof net.minecraft.client.gui.components.AbstractButton)
                .map(value->(net.minecraft.client.gui.components.AbstractButton)value)
                .filter(value->value.getMessage().getString().equals(label)
                        ||value.getMessage().getString().startsWith(label))
                .findFirst().orElseThrow(()->new IllegalStateException("GRACE_MENU_FAIL: button not found: "+label));
        button.onPress();
        EldenCombat.LOGGER.info("GRACE_MENU_BUTTON: pressed {}",label);
    }
}
