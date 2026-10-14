package com.dsh.eldencombat.testing;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.client.ClientCombat;
import com.dsh.eldencombat.client.ClientSkills;
import com.dsh.eldencombat.client.EldenCombatClient;
import com.dsh.eldencombat.core.ActionId;
import com.dsh.eldencombat.core.ActionSpec;
import com.dsh.eldencombat.core.CombatRules;
import com.dsh.eldencombat.core.WeaponClass;
import com.dsh.eldencombat.core.IntentType;
import com.dsh.eldencombat.network.CombatIntentPayload;
import com.dsh.eldencombat.registry.ModAttachments;
import com.dsh.eldencombat.registry.ModEntities;
import com.dsh.eldencombat.registry.ModItems;
import com.dsh.eldencombat.server.PlayerAppearance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;
import java.util.function.Consumer;
import com.dsh.eldencombat.skeleton.PlayerAssets;
import com.dsh.eldencombat.server.HitResolver;
import com.dsh.eldencombat.server.skills.SkillDriver;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;

/** Opt-in visual fixtures in an isolated development save; excluded from release jars. */
@EventBusSubscriber(modid=EldenCombat.MODID,value=Dist.CLIENT)
public final class ClientSmokeHarness {
    private static final boolean ENABLED=Boolean.getBoolean("eldencombat.clientSmoke");
    private static final boolean RELOAD_CHECK=Boolean.getBoolean("eldencombat.persistenceReload");
    private static final boolean GRIP_CHECK=Boolean.getBoolean("eldencombat.gripSmoke");
    private static boolean saveQueued;
    private record Fixture(String name,ActionId action,boolean great,boolean armor,boolean left,boolean first,int frame,
                           java.util.function.Supplier<net.minecraft.world.item.Item> item,boolean ignoreCorrections,boolean offHand) {
        Fixture(String name,ActionId action,boolean great,boolean armor,boolean left,boolean first,int frame,
                java.util.function.Supplier<net.minecraft.world.item.Item> item,boolean ignoreCorrections) {
            this(name,action,great,armor,left,first,frame,item,ignoreCorrections,false);
        }
        Fixture(String name,ActionId action,boolean great,boolean armor,boolean left,boolean first,int frame) {
            this(name,action,great,armor,left,first,frame,null,false);
        }
        Fixture(String name,ActionId action,boolean great,boolean armor,boolean left,boolean first,int frame,
                java.util.function.Supplier<net.minecraft.world.item.Item> item) {
            this(name,action,great,armor,left,first,frame,item,false);
        }
        /**
         * 实际要拿在手上的物品。
         *
         * <p>刻意用 {@link java.util.function.Supplier}：这些 Fixture 是**静态字段**，在类加载期构造，
         * 而那时 DeferredRegister 还没填，直接写 {@code ModItems.X.get()} 会抛
         * {@code ExceptionInInitializerError}，进而让整个模组"Failed to register automatic subscribers"
         * 起不来（踩过）。
         */
        net.minecraft.world.item.Item heldItem() {
            return item != null ? item.get() : (great?ModItems.GREAT_SWORD.get():ModItems.STRAIGHT_SWORD.get());
        }
    }
    private static final Fixture[] FULL_FIXTURES={
        new Fixture("idle",ActionId.NONE,false,false,false,false,0),
        new Fixture("armor-guard",ActionId.GUARD,false,true,false,false,0),
        new Fixture("light",ActionId.LIGHT_ATTACK,false,false,false,false,10),
        new Fixture("charged",ActionId.CHARGED_HEAVY,false,false,false,false,18),
        new Fixture("roll",ActionId.ROLL,false,false,false,false,7),
        new Fixture("parry",ActionId.PARRY,false,false,false,false,5),
        new Fixture("drink",ActionId.DRINK,false,false,false,false,19),
        new Fixture("great-sword",ActionId.NONE,true,false,false,false,0),
        new Fixture("great-left",ActionId.LIGHT_ATTACK,true,true,true,false,14),
        new Fixture("first-sword",ActionId.NONE,false,false,false,true,0),
        new Fixture("first-great",ActionId.NONE,true,false,false,true,0),
        new Fixture("first-drink",ActionId.DRINK,false,false,false,true,19),
        new Fixture("vanilla-fallback",ActionId.NONE,false,true,false,false,0),
        // 【握持对照】同机位、同帧：原版钻石剑 vs 我们的精细直剑。
        // 这两张截图是"剑型武器握持对不对"的唯一硬证据——不看截图只调矩阵等于闭眼改渲染。
        // 第三人称收 HUD（不挡剑），第一人称不收（原版 F1 会连手臂一起藏）。
        new Fixture("ref-vanilla-sword",ActionId.NONE,false,false,false,false,0,
                () -> net.minecraft.world.item.Items.DIAMOND_SWORD),
        new Fixture("ref-our-sword",ActionId.NONE,false,false,false,false,0),
        new Fixture("ref-vanilla-sword-first",ActionId.NONE,false,false,false,true,0,
                () -> net.minecraft.world.item.Items.DIAMOND_SWORD),
        new Fixture("ref-our-sword-first",ActionId.NONE,false,false,false,true,0),
        // 追忆剑（长得多）也来一张：长度归一化与握把位置不能只顾短剑
        new Fixture("ref-remembrance-sword",ActionId.NONE,false,false,false,false,0,
                () -> com.dsh.eldencombat.registry.ModItems.SACRED_RELIC_SWORD.get()),
        // 巨剑类对照（无 item_skins 校正）
        new Fixture("ref-great-sword",ActionId.NONE,true,false,false,false,0,
                () -> com.dsh.eldencombat.registry.ModItems.GREAT_SWORD.get()),
        // 【yaw 回归守卫】同一把追忆剑、同一机位：上一张走 item_skins 校正表，这一张渲染时临时置空。
        // 2026-10-12 实测：追忆剑那 8 件的 `rotation [0,90,0]` 是在"缺 display 块"那套坏管线下打的
        // 补丁，在新握法下会把武器转成侧向（截图里几乎看不见）。数据已清零，
        // 所以这两张**必须一模一样**——将来谁再给它加偏航，这里就会分叉。
        new Fixture("ref-remembrance-noyaw",ActionId.NONE,false,false,false,false,0,
                () -> com.dsh.eldencombat.registry.ModItems.SACRED_RELIC_SWORD.get(),true),
        new Fixture("ref-scale-dagger",ActionId.NONE,false,false,false,false,0,()->ModItems.DAGGER.get()),
        new Fixture("ref-scale-dagger-left",ActionId.NONE,false,false,true,false,0,()->ModItems.DAGGER.get()),
        new Fixture("ref-scale-colossal",ActionId.NONE,false,false,false,false,0,()->ModItems.COLOSSAL_SWORD.get()),
        new Fixture("ref-scale-spear",ActionId.NONE,false,false,false,false,0,()->ModItems.SPEAR.get()),
        new Fixture("ref-scale-shield",ActionId.NONE,false,false,false,false,0,()->ModItems.SHIELD.get()),
        new Fixture("ref-scale-bow",ActionId.NONE,false,false,false,false,0,()->ModItems.LONG_BOW.get()),
        new Fixture("ref-scale-crossbow-left",ActionId.NONE,false,false,true,false,0,()->ModItems.CROSSBOW.get()),
        new Fixture("ref-scale-dagger-first",ActionId.NONE,false,false,false,true,0,()->ModItems.DAGGER.get()),
        new Fixture("ref-scale-colossal-first-left",ActionId.NONE,false,false,true,true,0,()->ModItems.COLOSSAL_SWORD.get()),
        new Fixture("ref-scale-spear-first",ActionId.NONE,false,false,false,true,0,()->ModItems.SPEAR.get()),
        new Fixture("ref-scale-shield-first",ActionId.NONE,false,false,false,true,0,()->ModItems.SHIELD.get()),
        new Fixture("ref-scale-bow-first",ActionId.NONE,false,false,false,true,0,()->ModItems.LONG_BOW.get()),
        new Fixture("ref-scale-crossbow-first-left",ActionId.NONE,false,false,true,true,0,()->ModItems.CROSSBOW.get())
    };
    private static final Fixture[] FIXTURES=GRIP_CHECK?gripFixtures():FULL_FIXTURES;

    private static Fixture[] gripFixtures() {
        var fixtures=new java.util.ArrayList<Fixture>();
        var items=java.util.List.<java.util.function.Supplier<net.minecraft.world.item.Item>>of(
                ()->Items.DIAMOND_SWORD,()->ModItems.STRAIGHT_SWORD.get(),()->ModItems.GREAT_SWORD.get(),()->ModItems.COLOSSAL_SWORD.get(),
                ()->ModItems.SPEAR.get(),()->ModItems.SHIELD.get(),()->ModItems.LONG_BOW.get());
        String[] names={"diamond","straight","great","colossal","spear","shield","bow"};
        for(int i=0;i<names.length;i++) for(boolean first:new boolean[]{false,true}) for(boolean left:new boolean[]{false,true}) {
            fixtures.add(new Fixture("ref-grip-"+names[i]+(first?"-first":"-third")+(left?"-left":"-right"),
                    ActionId.NONE,false,false,left,first,0,items.get(i),false,false));
            if(i>0) fixtures.add(new Fixture("ref-grip-"+names[i]+(first?"-first":"-third")+(left?"-off-right":"-off-left"),
                    ActionId.NONE,false,false,left,first,0,items.get(i),false,true));
        }
        for(int i=1;i<=3;i++) for(boolean first:new boolean[]{false,true}) for(var action:new ActionId[]{ActionId.LIGHT_ATTACK,ActionId.GUARD})
            fixtures.add(new Fixture("ref-grip-"+names[i]+(first?"-first":"-third")+"-"+action.name().toLowerCase(),
                    action,false,false,false,first,action==ActionId.LIGHT_ATTACK?10:0,items.get(i),false,false));
        for(int draw:new int[]{0,10,20}) fixtures.add(new Fixture("ref-bow-draw-"+draw,ActionId.NONE,false,false,false,false,
                draw,()->ModItems.LONG_BOW.get(),false,false));
        return fixtures.toArray(Fixture[]::new);
    }
    private static int ticks, startupTicks;
    /** 网络相位自己的 tick 计数：有按键待落实时会**冻结**，保证后续断言不会被按键延迟带偏 */
    private static int networkTick;
    private static volatile boolean initialized;
    /** 重启相位：等客户端把登录期间下发的展示数据处理完再断言。 */
    private static int reloadClientTicks;
    private static boolean reloadClientChecked;
    private static volatile int stage=-1;
    private static String screenshot;
    private static volatile boolean networkPassed;
    private static volatile boolean networkFailed;
    private static volatile String networkFailure="";
    /** yaw A/B：进入"关掉校正"的 fixture 前保存的原始校正表，离开时还原。 */
    private static java.util.Map<String,com.dsh.eldencombat.client.WeaponCorrections.Entry> savedCorrections;
    private static Zombie hitTarget;
    private static net.minecraft.world.entity.Entity gripCamera;
    /** 削韧 payoff 端到端检查用的 Boss（服务端 id，客户端按同一个 id 取它自己的镜像实体）。 */
    private static volatile int breakBossId = -1;
    /** §4 落地安全网的基线计数（进入高空战技用例前采样）。 */
    private static volatile int landingBase;
    /** §2.1 战灰解锁的探针 ID：验证"幂等授予 / 死亡保留 / 关服重启保留"。 */
    private static final net.minecraft.resources.ResourceLocation ASH_PROBE =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("eldencombat","smoke_probe_ash");

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!ENABLED)return;
        var mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null || mc.getSingleplayerServer()==null) {
            if(++startupTicks>2400) { EldenCombat.LOGGER.error("CLIENT_SMOKE_FAIL: startup timeout"); mc.stop(); }
            return;
        }
        mc.options.pauseOnLostFocus=false;
        if(GRIP_CHECK) mc.options.fov().set(stage>=0&&stage<FIXTURES.length&&FIXTURES[stage].first()?70:40);
        if(networkFailed) throw new IllegalStateException("CLIENT_SMOKE_FAIL: "+networkFailure);
        if(RELOAD_CHECK) {
            if(!initialized) {
                initialized=true;
                reloadClientTicks=0;
            }
            // 登录/重启后的客户端展示数据由 enqueueWork 排队处理，先等它落实再断言（也避免抢在关服之前）。
            if(++reloadClientTicks<40) return;
            if(!reloadClientChecked) {
                reloadClientChecked=true;
                require(com.dsh.eldencombat.client.TalismanClientData.size()>=4,
                        "client received the talisman display payload on login after restart");
                require("战士壶碎片".equals(com.dsh.eldencombat.client.TalismanClientData
                        .spec("eldencombat:warrior_jar_shard").displayName()),
                        "restart display data matches the datapack (tooltip no longer says '未同步')");
                EldenCombat.LOGGER.info("TALISMAN_RESTART_DISPLAY_PASS: 重启后客户端收到服务端护符展示数据 {} 件",
                        com.dsh.eldencombat.client.TalismanClientData.size());
                onServer(mc,p->{
                    require(com.dsh.eldencombat.server.PlayerAttributes.of(p).equals(new com.dsh.eldencombat.core.AttributeSet(7,3,0,0,0)),
                            "attributes survived actual world restart");
                    var weapon=com.dsh.eldencombat.item.WeaponStacks.state(p.getInventory().getItem(0));
                    require(weapon.upgradeLevel()==2&&weapon.affinity()==com.dsh.eldencombat.core.Affinity.HEAVY,
                            "weapon upgrade and affinity survived actual world restart");
                    require(com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).fp()==37,
                            "FP survived actual world restart without login refill");
                    require(com.dsh.eldencombat.server.skills.AshUnlocks.owns(p,ASH_PROBE),
                            "ash unlock survived actual world restart");
                    EldenCombat.LOGGER.info("ASH_UNLOCK_RESTART_PASS: 战灰解锁在真实关服/重启后保留");
                    // §3.4 护符：真实 ItemStack（含组件）落盘保留，登录时重新应用数值
                    var talisman=com.dsh.eldencombat.server.Talismans.loadout(p).get(0);
                    require(talisman.is(ModItems.WARRIOR_JAR_SHARD.get()),
                            "talisman ItemStack survived actual world restart");
                    require("烟测护符·死亡保留".equals(talisman.getOrDefault(
                            net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                            net.minecraft.network.chat.Component.empty()).getString()),
                            "talisman item components survived actual world restart");
                    require(Math.abs(com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).maxFp()-100f)<1.0e-4f,
                            "talisman max-FP bonus re-applied on login (no FP talisman equipped)");
                    require(Math.abs(com.dsh.eldencombat.server.Talismans.damageMultiplier(p)-1.08f)<1.0e-4f,
                            "talisman value outlet recomputed after restart");
                    EldenCombat.LOGGER.info("TALISMAN_RESTART_PASS: 护符 ItemStack（含自定义名组件）在真实关服/重启后保留，登录时重新应用数值");
                    require(!SkillDriver.cast(p).isCasting(),"reload has no transient cast");
                    EldenCombat.LOGGER.info("CLIENT_PERSISTENCE_COMPLETE: actual save/shutdown/restart passed");
                    mc.execute(mc::stop);
                });
            }
            return;
        }
        if(!initialized) {
            if(stage!=-1)return;
            if(GRIP_CHECK) {
                var expected=Boolean.getBoolean("eldencombat.smokeSlim")?net.minecraft.client.resources.PlayerSkin.Model.SLIM:net.minecraft.client.resources.PlayerSkin.Model.WIDE;
                require(mc.player.getSkin().model()==expected,"grip fixture uses actual "+expected+" player skin");
                EldenCombat.LOGGER.info("CLIENT_GRIP_RUNTIME: skin={} uuid={} FOV=40; development classes/resources, not installed release JAR",
                        mc.player.getSkin().model(),mc.player.getUUID());
            }
            stage=-2;
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();
                var level=server.overworld();
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false,server);
                level.setDayTime(6000);
                for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)level.setBlockAndUpdate(new BlockPos(x,80,z),Blocks.STONE.defaultBlockState());
                var player=server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.CREATIVE);
                player.teleportTo(0,81,0);
                if(!GRIP_CHECK) {
                    var boss=ModEntities.MARGIT.get().create(level);
                    boss.setPos(2.5,81,0); boss.setNoAi(true); boss.setPersistenceRequired(); level.addFreshEntity(boss);
                }
                var checks=SelfTest.runAll();
                if(SelfTest.failures(checks)!=0)throw new IllegalStateException("Smoke selftest failed: "+checks);
                EldenCombat.LOGGER.info("CLIENT_SMOKE: {} shared checks passed",checks.size());
                stage=-1;
                if(Boolean.getBoolean("eldencombat.functionalSmoke")) {
                    stage=FIXTURES.length; ticks=FIXTURES.length*40;
                }
                initialized=true;
            });
            return;
        }
        int next=ticks/40;
        if(next>=FIXTURES.length) {
            if(ticks==FIXTURES.length*40)EldenCombat.LOGGER.info("CLIENT_SMOKE_PASS: all {} visual fixtures rendered",FIXTURES.length);
            if(GRIP_CHECK) { mc.stop(); return; }
            handlePresses(mc);
            // 有按键还没落实（例如窗口失焦，ClientCombat 会正确地忽略按键）时冻结网络相位：
            // 后面的断言都假定"前一步已经发生"，冻结比放大时间间隔可靠。
            if (pendingPresses()) { ticks++; return; }
            networkChecks(mc,networkTick++);
            ticks++; return;
        }
        if(next!=stage) {
            stage=next;
            var fixture=FIXTURES[next];
            mc.options.setCameraType(fixture.first?CameraType.FIRST_PERSON:CameraType.THIRD_PERSON_FRONT);
            // 握持对照图必须收起 HUD：血条/耐力条/物品名正好压在握着剑的那块画面上，看不见剑。
            // 但**第一人称不能收**：原版 GameRenderer:947 里 `!options.hideGui` 是第一人称手臂的
            // 渲染条件之一（F1 连手一起藏），收了就是一张空画面。第一人称的手在右下角，
            // HUD 不挡那里，所以照常显示 HUD。
            mc.options.hideGui=fixture.name().startsWith("ref-")&&!fixture.first;
            applyCorrectionMode(fixture);
            mc.player.setYRot(0); mc.player.setXRot(0);
        }
        if(GRIP_CHECK) {
            var fixture=FIXTURES[stage];
            if(fixture.first()) mc.setCameraEntity(mc.player);
            else {
                if(gripCamera==null||gripCamera.level()!=mc.level) gripCamera=EntityType.ARMOR_STAND.create(mc.level);
                boolean targetLeft=fixture.offHand()?!fixture.left():fixture.left();
                gripCamera.moveTo(mc.player.getX(),mc.player.getY()+mc.player.getEyeHeight()-gripCamera.getEyeHeight(),
                        mc.player.getZ(),targetLeft?45:-45,0);
                ((net.minecraft.world.entity.LivingEntity)gripCamera).setYHeadRot(targetLeft?45:-45);
                ((net.minecraft.world.entity.LivingEntity)gripCamera).yHeadRotO=targetLeft?45:-45;
                gripCamera.setOldPosAndRot();
                mc.setCameraEntity(gripCamera);
            }
        }
        if(ticks%40==20) {
            require(mc.player.getY()>=80.5,"fixture height retained: "+FIXTURES[stage].name);
            var renderer=mc.getEntityRenderDispatcher().getRenderer(mc.player);
            // 纯原版方案：玩家必须用原版 PlayerRenderer + 原版 PlayerModel（不再替换模型/加关节）
            if(!(renderer instanceof PlayerRenderer playerRenderer) || !(playerRenderer.getModel() instanceof net.minecraft.client.model.PlayerModel))
                throw new IllegalStateException("Player renderer is not vanilla PlayerRenderer/PlayerModel");
            if(stage!=FIXTURES.length-1 && !ClientCombat.combatMode())throw new IllegalStateException("No server combat snapshot at fixture "+stage);
            screenshot=String.format("%02d-%s.png",stage,FIXTURES[stage].name);
        }
        if(GRIP_CHECK&&ticks%40==18&&FIXTURES[stage].action()==ActionId.LIGHT_ATTACK)
            mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        ticks++;
    }
    private static void onServer(Minecraft mc,Consumer<ServerPlayer> task) {
        mc.getSingleplayerServer().execute(()->{
            try { task.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst()); }
            catch(RuntimeException | Error failure) {
                networkFailure=failure.toString(); networkFailed=true; throw failure;
            }
        });
    }
    private static void require(boolean ok,String name) {
        if(!ok) { networkFailure=name; networkFailed=true; throw new IllegalStateException("CLIENT_NETWORK_FAIL: "+name); }
        EldenCombat.LOGGER.info("CLIENT_NETWORK_PASS: {}",name);
    }

    /** 背包里某件物品的数量（§3 护符"物品守恒"断言用：退回背包 vs 被销毁）。 */
    private static int countItem(ServerPlayer player,net.minecraft.world.item.Item item) {
        int count=0;
        for(int i=0;i<player.getInventory().getContainerSize();i++) {
            var stack=player.getInventory().getItem(i);
            if(!stack.isEmpty()&&stack.is(item))count+=stack.getCount();
        }
        return count;
    }

    /**
     * {@code item_skins} 姿态校正的临时开关（只给 yaw A/B 用）。
     *
     * <p>不改美术文件：进入带 {@code ignoreCorrections} 的 fixture 时把有效校正表换成空表，
     * 离开时换回来。渲染每帧都会重新读这张表，所以切换立刻生效。
     */
    private static void applyCorrectionMode(Fixture fixture) {
        if(fixture.ignoreCorrections()) {
            if(savedCorrections==null) {
                savedCorrections=com.dsh.eldencombat.client.WeaponCorrections.all();
                com.dsh.eldencombat.client.WeaponCorrections.apply(java.util.Map.of());
            }
        } else if(savedCorrections!=null) {
            com.dsh.eldencombat.client.WeaponCorrections.apply(savedCorrections);
            savedCorrections=null;
        }
    }

    // ------------------------------------------------------------------
    // 真实按键：窗口失焦时 ClientCombat 会（正确地）忽略按键，
    // 所以自动化测试不能假定"点一次就一定生效"，而要轮询重试 + 冻结后续相位。
    // ------------------------------------------------------------------
    private static final class Press {
        final net.minecraft.client.KeyMapping key;
        final java.util.function.BooleanSupplier satisfied;
        final String name;
        final int startTick;
        int lastClickTick;
        boolean clicked;
        boolean done;
        Press(net.minecraft.client.KeyMapping key, java.util.function.BooleanSupplier satisfied, String name, int startTick) {
            this.key = key; this.satisfied = satisfied; this.name = name; this.startTick = startTick;
        }
    }

    private static final java.util.List<Press> PRESSES = new java.util.ArrayList<>();
    /** 等待窗口回到前台的耐心上限（tick）：20 秒 */
    private static final int PRESS_TIMEOUT_TICKS = 400;
    /** 同一次按键两次尝试之间的间隔 */
    private static final int PRESS_RETRY_TICKS = 10;
    /** 只在第一次覆盖"窗口激活"状态时打一行日志 */
    private static boolean windowOverrideLogged;

    private static void pressKey(net.minecraft.client.KeyMapping key, java.util.function.BooleanSupplier satisfied,
                                 String name, int tick) {
        PRESSES.add(new Press(key, satisfied, name, tick));
    }

    private static boolean pendingPresses() {
        for (Press press : PRESSES) {
            if (!press.done) return true;
        }
        return false;
    }

    private static void handlePresses(Minecraft mc) {
        for (Press press : PRESSES) {
            if (press.done) continue;
            // 必须先真的按下去过一次，再看条件：否则"条件本来就成立"会变成假通过
            if (press.clicked && press.satisfied.getAsBoolean()) {
                press.done = true;
                require(true, press.name);
                continue;
            }
            if (ticks - press.startTick > PRESS_TIMEOUT_TICKS) {
                networkFailed = true;
                throw new IllegalStateException("CLIENT_NETWORK_FAIL: " + press.name
                        + "（等了 " + PRESS_TIMEOUT_TICKS + " tick）");
            }
            // 自动测试可能在后台上跑：`ClientCombat` 在窗口失焦时**正确地**忽略按键，
            // 这会把"按键没生效"混进断言里。这里只覆盖这一个与测试目标无关的条件
            // （我们测的是"按键 → 游戏总线 → 发一次意图"，不是窗口焦点策略）。
            if (!mc.isWindowActive()) {
                mc.setWindowActive(true);
                if (!windowOverrideLogged) {
                    windowOverrideLogged = true;
                    EldenCombat.LOGGER.info("CLIENT_SMOKE: 游戏窗口在后台上运行，测试期间强制视为激活以驱动真实按键");
                }
            }
            // 注意别用 "ticks - Integer.MIN_VALUE" 这种写法判断"还没按过"：会整型溢出成负数，
            // 结果是**一次都不按**（这条踩过：表现了 400 tick 超时，看起来像窗口失焦）。
            if (press.clicked && ticks - press.lastClickTick < PRESS_RETRY_TICKS) continue;
            net.minecraft.client.KeyMapping.click(press.key.getKey());
            press.lastClickTick = ticks;
            press.clicked = true;
        }
    }
    private static void networkChecks(Minecraft mc,int tick) {
        switch(tick) {
            case 0 -> {
                stage=FIXTURES.length;
                onServer(mc,p->{
                    p.getData(ModAttachments.COMBAT_STATE).reset(EldenCombat.config());
                    p.setMainArm(HumanoidArm.RIGHT);
                    p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(ModItems.STRAIGHT_SWORD.get()));
                    p.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(ModItems.SHIELD.get()));
                });
            }
            // 【总线接线回归】这里**不**直接调 ClientCombat.send，而是模拟真实按键：
            // 按键 -> ClientCombat.onClientTick(游戏总线) -> 发一次意图 -> 服务端翻转状态 ->
            // 服务端快照回来 -> 客户端镜像翻转。所以这条断言同时证明：
            // ① ClientCombat 真的挂在客户端事件总线上；② 按键路径通；③ 一次按键只发一次。
            // （镜像翻转是"服务端也处理了"的证据：镜像只由服务端快照驱动。）
            case 8 -> pressKey(EldenCombatClient.TOGGLE_COMBAT,ClientCombat::combatMode,
                    "combat toggle snapshot via real key press",ticks);
            case 12 -> ClientCombat.send(IntentType.LIGHT,-1,0,0);
            case 16 -> {
                onServer(mc,p->{
                    var s=p.getData(ModAttachments.COMBAT_STATE);
                    require(s.action==ActionId.LIGHT_ATTACK && s.actionWeapon==WeaponClass.LIGHT_SWORD,"bound light attack");
                    var blade=PlayerAssets.get().sampledBlade(s.actionWeapon,s.actionSlim,s.actionLeftHanded,
                        PlayerAssets.clipId(s.action,false,s.actionSequence,0,false),10);
                    var point=HitResolver.toWorld(p,p.getYRot(),new org.joml.Vector3d(blade.from()).lerp(blade.to(),0.5));
                    hitTarget=EntityType.HUSK.create(p.serverLevel());
                    hitTarget.setNoAi(true); hitTarget.setNoGravity(true); hitTarget.setPersistenceRequired();
                    hitTarget.setPos(point.x,point.y-0.9,point.z); p.serverLevel().addFreshEntity(hitTarget);
                });
                PacketDistributor.sendToServer(CombatIntentPayload.of(1,IntentType.CANCEL,-1,0,0));
            }
            case 20 -> {
                onServer(mc,p->require(p.getData(ModAttachments.COMBAT_STATE).action==ActionId.LIGHT_ATTACK,"stale cancel rejected"));
            }
            case 24 -> {
                onServer(mc,p->{require(hitTarget!=null&&hitTarget.getHealth()<20&&hitTarget.getHealth()>=15,"real blade hits target once"); hitTarget.discard();});
                ClientCombat.send(IntentType.CANCEL,-1,0,0);
            }
            case 28 -> {
                onServer(mc,p->require(p.getData(ModAttachments.COMBAT_STATE).action==ActionId.NONE,"fresh cancel accepted"));
                ClientCombat.send(IntentType.GUARD_START,-1,0,0);
            }
            case 32 -> {
                onServer(mc,p->require(p.getData(ModAttachments.COMBAT_STATE).guarding,"guard accepted"));
                ClientCombat.send(IntentType.GUARD_STOP,-1,0,0);
            }
            case 36 -> {
                onServer(mc,p->{require(!p.getData(ModAttachments.COMBAT_STATE).guarding,"guard released"); p.setHealth(12);});
                ClientCombat.send(IntentType.DRINK,-1,0,0);
            }
            case 45 -> onServer(mc,p->{var s=p.getData(ModAttachments.COMBAT_STATE); require(s.potionCharges==4&&p.getHealth()==12,"drink before consume tick");});
            case 58 -> onServer(mc,p->{var s=p.getData(ModAttachments.COMBAT_STATE); require(s.potionCharges==3&&p.getHealth()==20,"drink heals once after consume tick");});
            case 68 -> ClientCombat.send(IntentType.HEAVY_START,-1,0,0);
            case 90 -> ClientCombat.send(IntentType.HEAVY_RELEASE,-1,0,0);
            case 94 -> onServer(mc,p->require(p.getData(ModAttachments.COMBAT_STATE).action==ActionId.CHARGED_HEAVY,"full charge release"));
            case 98 -> ClientCombat.send(IntentType.CANCEL,-1,0,0);
            case 104 -> ClientCombat.send(IntentType.ROLL,-1,1,0);
            case 113 -> onServer(mc,p->require(p.getData(ModAttachments.COMBAT_STATE).action==ActionId.ROLL&&p.getX()>0.5&&p.getY()>=80.5,"roll moves through server physics and preserves height"));
            case 128 -> {
                require(mc.player.getY()>=80.5,"client roll height retained");
                onServer(mc,p->require(p.getData(ModAttachments.COMBAT_STATE).action==ActionId.NONE&&!p.getData(ModAttachments.COMBAT_STATE).isRollInvulnerable(p.level().getGameTime())&&p.getY()>=80.5,"roll clears invulnerability and retains height"));
            }
            case 132 -> ClientCombat.send(IntentType.HEAVY_START,-1,0,0);
            case 135 -> onServer(mc,p->p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(ModItems.GREAT_SWORD.get())));
            case 140 -> onServer(mc,p->{require(p.getData(ModAttachments.COMBAT_STATE).action==ActionId.NONE,"changing weapon cancels charge"); networkPassed=true;});
            case 155 -> onServer(mc,p->{
                for(int y=81;y<=83;y++)for(int z=-2;z<=2;z++)p.serverLevel().setBlockAndUpdate(new BlockPos(2,y,z),Blocks.STONE.defaultBlockState());
                p.connection.teleport(0,81,0,0,0);
            });
            case 163 -> ClientCombat.send(IntentType.ROLL,-1,1,0);
            case 178 -> onServer(mc,p->require(p.getX()>0.5&&p.getX()<=1.701&&p.getY()>=80.5,"roll stops at wall without tunneling"));
            case 188 -> onServer(mc,p->p.connection.teleport(0,85,0,0,0));
            case 192 -> ClientCombat.send(IntentType.ROLL,-1,0,-1);
            case 201 -> {
                require(mc.player.getY()<84&&mc.player.getY()>=80.5,"air roll preserves client falling");
                onServer(mc,p->require(p.getY()<84&&p.getY()>=80.5,"air roll preserves server falling"));
            }
            // 【战技端到端】同样走真实按键：换上绑定的追忆武器 -> 按 C -> 服务端起手 ->
            // 阶段推进（客户端镜像跟着变）-> 收招结束。这条链路覆盖"能释放战技"这一点。
            // 时序注意：192 tick 起手的翻滚要 20 tick 才结束（约 213），战技必须等它走完，
            // 否则 CombatRules.canAccept 会（正确地）拒绝——这正是"施放期间不接受新动作"的规则。
            case 216 -> onServer(mc,p->{
                p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(ModItems.SACRED_RELIC_SWORD.get()));
                p.connection.teleport(0,81,0,0,0);
                for(int x=-6;x<=6;x++)for(int z=0;z<=18;z++)
                    p.serverLevel().setBlockAndUpdate(new BlockPos(x,80,z),Blocks.STONE.defaultBlockState());
                hitTarget=EntityType.HUSK.create(p.serverLevel());
                hitTarget.setNoAi(true); hitTarget.setNoGravity(true); hitTarget.setPersistenceRequired();
                hitTarget.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);
                hitTarget.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);
                hitTarget.setPos(0,81,6); p.serverLevel().addFreshEntity(hitTarget);
            });
            case 218 -> pressKey(EldenCombatClient.SKILL,ClientSkills::casting,
                    "skill cast accepted from real key press (client mirror follows server cast)",ticks);
            case 224 -> {
                onServer(mc,p->{
                    var cast=SkillDriver.cast(p);
                    require(cast.isCasting()&&cast.skillId().equals("wave_of_gold"),
                        "skill cast accepted from real key press: "+cast.describe());
                });
                require(ClientSkills.activeSkillId().equals("wave_of_gold")&&ClientSkills.casting(),
                    "client mirror sees the cast (skill cast payload arrived)");
            }
            case 231 -> onServer(mc,p->{
                var waves=p.serverLevel().getEntitiesOfClass(com.dsh.eldencombat.entity.skill.GoldenWaveEntity.class,
                    p.getBoundingBox().inflate(20),w->w.owner()==p);
                require(waves.size()==1&&!waves.getFirst().isFlame()&&waves.getFirst().travelDistance()>0,
                    "real key spawned exactly one non-flame travelling golden wave");
            });
            case 245 -> onServer(mc,p->{
                require(!SkillDriver.cast(p).isCasting(),"skill timeline finished and returned to idle");
                require(hitTarget!=null&&Math.abs(hitTarget.getHealth()-6f)<.01f,
                    "golden wave dealt exactly one 14 damage hit, health="+hitTarget.getHealth());
                hitTarget.discard();
            });
            // 【削韧 payoff 端到端】服务端刷一个干净位置的 Boss → 失衡打满 → 客户端断言
            // "崩解"状态与描边标志真的同步过来了（HUD 失衡条爆闪读的就是这两个字段）。
            case 248 -> onServer(mc,p->{
                var level=p.serverLevel();
                var boss=com.dsh.eldencombat.registry.ModEntities.MARGIT.get().create(level);
                boss.moveTo(16.5,90,0.5,0,0); boss.setNoAi(true); boss.setPersistenceRequired();
                level.addFreshEntity(boss);
                breakBossId=boss.getId();
            });
            case 252 -> onServer(mc,p->{
                var boss=(com.dsh.eldencombat.entity.MargitEntity)p.serverLevel().getEntity(breakBossId);
                require(boss!=null&&!boss.isBroken(),"fresh boss is not broken");
                boss.addStagger(boss.poiseMax(),p.level().getGameTime());
                require(boss.isBroken()&&boss.isCurrentlyGlowing(),"server entered break state with outline");
            });
            case 258 -> {
                var entity=mc.level==null?null:mc.level.getEntity(breakBossId);
                require(entity instanceof com.dsh.eldencombat.entity.MargitEntity,"break boss is tracked by the client");
                var boss=(com.dsh.eldencombat.entity.MargitEntity)entity;
                require(boss.isBroken(),"client mirror sees break state (DATA_BROKEN synced)");
                // 注意用 isCurrentlyGlowing()：它客户端读的是**同步的共享标志位 6**，
                // 而 hasGlowingTag() 读的是不同步的服务端字段（客户端恒为 false，会误判成"描边没同步"）。
                require(boss.isCurrentlyGlowing(),"client mirror sees break outline (shared flag 6 synced)");
            }
            case 264 -> {
                // 【§4 落地兜底端到端】把玩家扔到高空，用会跃起的追忆武器（黄金碎击）放战技：
                // 施放在落地前就结束了 -> 落地判定交给"安全网"，它必须观察到落地并收工
                // （而不是把人留在半空或掉进虚空）。
                onServer(mc,p->{
                    p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(ModItems.MARIKAS_HAMMER.get()));
                    p.connection.teleport(0,170,0,0,0);
                    landingBase=com.dsh.eldencombat.server.skills.RemembranceRuntime.safetyResolutions();
                });
            }
            case 268 -> pressKey(EldenCombatClient.SKILL,ClientSkills::casting,
                    "jump skill cast accepted while airborne (client mirror follows server cast)",ticks);
            case 274 -> onServer(mc,p->require(SkillDriver.cast(p).isCasting()&&SkillDriver.cast(p).skillId().equals("gold_breaker"),
                    "jump skill server-side cast is gold_breaker: "+SkillDriver.cast(p).describe()));
            case 330 -> onServer(mc,p->{
                int resolved=com.dsh.eldencombat.server.skills.RemembranceRuntime.safetyResolutions()-landingBase;
                require(resolved>=1,"landing safety net observed the landing after the cast ended ("
                        +com.dsh.eldencombat.server.skills.RemembranceRuntime.lastSafetyOutcome()+")");
                require(p.onGround(),"player is back on solid ground, not stuck in the air or in the void");
            });
            case 334 -> onServer(mc,p->{
                p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(ModItems.SACRED_RELIC_SWORD.get()));
                p.connection.teleport(0,81,0,0,0);
                com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).setFp(0);
            });
            case 338 -> ClientCombat.send(IntentType.SKILL,-1,0,0);
            case 352 -> onServer(mc,p->{
                require(!SkillDriver.cast(p).isCasting()&&com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).fp()==0,
                    "insufficient FP request rejected without charge");
                require(p.serverLevel().getEntitiesOfClass(com.dsh.eldencombat.entity.skill.GoldenWaveEntity.class,
                    p.getBoundingBox().inflate(20),w->w.owner()==p).isEmpty(),"rejected request spawned no wave");
                com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).setFp(100);
                for(int x=-6;x<=6;x++)for(int y=81;y<=84;y++)
                    p.serverLevel().setBlockAndUpdate(new BlockPos(x,y,3),Blocks.STONE.defaultBlockState());
                hitTarget=EntityType.HUSK.create(p.serverLevel());
                hitTarget.setNoAi(true); hitTarget.setNoGravity(true); hitTarget.setPersistenceRequired();
                hitTarget.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);
                hitTarget.setPos(0,81,6); p.serverLevel().addFreshEntity(hitTarget);
            });
            case 356 -> pressKey(EldenCombatClient.SKILL,ClientSkills::casting,"wall test real key accepted",ticks);
            case 398 -> onServer(mc,p->{
                require(hitTarget.getHealth()==20,"golden wave did not damage target behind wall");
                hitTarget.discard();
            });
            case 404 -> onServer(mc,p->{
                for(int x=-6;x<=6;x++)for(int y=81;y<=84;y++)
                    p.serverLevel().setBlockAndUpdate(new BlockPos(x,y,3),Blocks.AIR.defaultBlockState());
                p.connection.teleport(0,81,0,0,0);
                p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(ModItems.STARSCOURGE_GREATSWORD.get()));
                com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).setFp(100);
                hitTarget=EntityType.HUSK.create(p.serverLevel());
                hitTarget.setNoAi(true); hitTarget.setNoGravity(true); hitTarget.setPersistenceRequired();
                hitTarget.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);
                hitTarget.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
                hitTarget.setHealth(100); hitTarget.setPos(0,81,2); p.serverLevel().addFreshEntity(hitTarget);
            });
            case 408 -> pressKey(EldenCombatClient.SKILL,ClientSkills::casting,"starcaller initial key accepted",ticks);
            case 425 -> onServer(mc,p->require(Math.abs(hitTarget.getHealth()-90)<.01,
                    "starcaller first stage dealt 10 damage"));
            case 426 -> pressKey(EldenCombatClient.SKILL,()->ClientSkills.stageIndex()>=2,
                    "starcaller followup key reached second ACTIVE",ticks);
            case 459 -> onServer(mc,p->{
                require(Math.abs(hitTarget.getHealth()-65)<.01,"starcaller followup landing dealt 25 damage");
                require(com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).fp()==70,"starcaller charged two inputs once");
                hitTarget.discard();
            });
            case 464 -> onServer(mc,p->{
                p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(ModItems.SACRED_RELIC_SWORD.get()));
                com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).setFp(100);
            });
            case 468 -> pressKey(EldenCombatClient.SKILL,ClientSkills::casting,"cleanup test key accepted",ticks);
            case 473 -> ClientCombat.send(IntentType.CANCEL,-1,0,0);
            case 476 -> onServer(mc,p->require(SkillDriver.cast(p).isCasting(),"manual cancel rejected during windup"));
            case 481 -> onServer(mc,p->{
                require(!p.serverLevel().getEntitiesOfClass(com.dsh.eldencombat.entity.skill.GoldenWaveEntity.class,
                    p.getBoundingBox().inflate(20),w->w.owner()==p).isEmpty(),"cleanup fixture emitted wave");
                p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(ModItems.MARIKAS_HAMMER.get()));
            });
            case 486 -> onServer(mc,p->{
                require(!SkillDriver.cast(p).isCasting(),"weapon switch forced cancellation");
                require(p.serverLevel().getEntitiesOfClass(com.dsh.eldencombat.entity.skill.GoldenWaveEntity.class,
                    p.getBoundingBox().inflate(20),w->w.owner()==p).isEmpty(),"weapon cancellation removed emitted wave");
                require(com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).fp()==80,"cancellation retained one FP charge");
            });
            // Boundary fixture isolates the packet rule at exact RECOVERY ticks; not a full hammer cast.
            case 490 -> onServer(mc,p->{
                SkillDriver.begin(p,"gold_breaker",p.level().getGameTime());
                SkillDriver.cast(p).enterRecovery(p.level().getGameTime());
            });
            case 491 -> ClientCombat.send(IntentType.ROLL,-1,1,0);
            case 492 -> onServer(mc,p->require(SkillDriver.cast(p).isCasting(),"hard recovery rejected roll"));
            case 493 -> ClientCombat.send(IntentType.ROLL,-1,1,0);
            case 496 -> onServer(mc,p->{
                require(!SkillDriver.cast(p).isCasting()&&p.getData(ModAttachments.COMBAT_STATE).action==ActionId.ROLL,
                    "recovery tail connected to roll through network");
                require(!p.isNoGravity(),"cancel restored ordinary gravity");
            });
            default -> { if(tick>=520) remainingSkillChecks(mc,tick-520); }
        }
    }

    private static void remainingSkillChecks(Minecraft mc,int elapsed) {
        String[] ids={"gold_breaker","destined_death","takers_flames","waterfowl_dance","bloodboon_ritual","cursed_blood_slice"};
        int index=elapsed/110, frame=elapsed%110;
        if(index>=ids.length) { environmentChecks(mc,elapsed-ids.length*110); return; }
        String id=ids[index];
        if(frame==0) onServer(mc,p->{
            var item=switch(id) {
                case "gold_breaker" -> ModItems.MARIKAS_HAMMER.get();
                case "destined_death" -> ModItems.MALIKETHS_BLACK_BLADE.get();
                case "takers_flames" -> ModItems.BLASPHEMOUS_BLADE.get();
                case "waterfowl_dance" -> ModItems.HAND_OF_MALENIA.get();
                case "bloodboon_ritual" -> ModItems.MOHGWYNS_SACRED_SPEAR.get();
                default -> ModItems.MORGOTTS_CURSED_SWORD.get();
            };
            p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(item));
            p.connection.teleport(0,81,0,0,0); p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).setFp(100);
            hitTarget=EntityType.HUSK.create(p.serverLevel());
            hitTarget.setNoAi(true); hitTarget.setNoGravity(true); hitTarget.setPersistenceRequired();
            hitTarget.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);
            hitTarget.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
            hitTarget.setHealth(100); hitTarget.setPos(0,81,2); p.serverLevel().addFreshEntity(hitTarget);
        });
        if(frame==6) pressKey(EldenCombatClient.SKILL,ClientSkills::casting,id+" real key accepted",ticks);
        if(frame==100) onServer(mc,p->{
            require(hitTarget.getHealth()<100,id+" caused actual target damage, health="+hitTarget.getHealth());
            require(!SkillDriver.cast(p).isCasting(),id+" returned to idle");
            require(com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).fp()==100-SkillDriver.spec(id).fpCost(),
                    id+" charged initial input once");
            hitTarget.discard();
        });
    }

    private static void environmentChecks(Minecraft mc,int elapsed) {
        String[] names={"low ceiling","step","liquid","cliff"};
        int index=elapsed/110,frame=elapsed%110;
        if(index>=names.length) { lifecycleChecks(mc,elapsed-names.length*110); return; }
        if(frame==0) onServer(mc,p->{
            int x=40+index*20;
            for(int dx=-7;dx<=7;dx++) for(int z=-7;z<=7;z++) {
                p.serverLevel().setBlockAndUpdate(new BlockPos(x+dx,80,z),Blocks.STONE.defaultBlockState());
                for(int y=81;y<=86;y++) p.serverLevel().setBlockAndUpdate(new BlockPos(x+dx,y,z),Blocks.AIR.defaultBlockState());
                if(index==0) p.serverLevel().setBlockAndUpdate(new BlockPos(x+dx,83,z),Blocks.STONE.defaultBlockState());
                if(index==2) for(int y=81;y<=82;y++) p.serverLevel().setBlockAndUpdate(new BlockPos(x+dx,y,z),Blocks.WATER.defaultBlockState());
            }
            if(index==1) for(int dx=-2;dx<=2;dx++) p.serverLevel().setBlockAndUpdate(new BlockPos(x+dx,81,1),Blocks.STONE_SLAB.defaultBlockState());
            if(index==3) for(int dx=-1;dx<=1;dx++) for(int z=-1;z<=1;z++)
                p.serverLevel().setBlockAndUpdate(new BlockPos(x+dx,120,z),Blocks.STONE.defaultBlockState());
            p.connection.teleport(x,index==3?121:81,0,0,0); p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(ModItems.MARIKAS_HAMMER.get()));
            com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).setFp(100);
            hitTarget=EntityType.HUSK.create(p.serverLevel());
            hitTarget.setNoAi(true); hitTarget.setNoGravity(true); hitTarget.setPersistenceRequired();
            hitTarget.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
            hitTarget.setHealth(100); hitTarget.setPos(x,81,2); p.serverLevel().addFreshEntity(hitTarget);
        });
        if(frame==6) pressKey(EldenCombatClient.SKILL,ClientSkills::casting,"gold breaker environment "+names[index],ticks);
        if(frame==100) onServer(mc,p->{
            require(!SkillDriver.cast(p).isCasting()&&!p.isNoGravity(),names[index]+" ended with ordinary gravity");
            if(index==2) require(hitTarget.getHealth()==100,"liquid landing caused no impact damage");
            else require(p.onGround(),names[index]+" returned to solid ground");
            hitTarget.discard();
        });
    }

    private static void lifecycleChecks(Minecraft mc,int frame) {
        if(frame==0) onServer(mc,p->{
            com.dsh.eldencombat.server.PlayerAttributes.set(p,new com.dsh.eldencombat.core.AttributeSet(7,3,0,0,0));
            var weapon=new ItemStack(ModItems.STRAIGHT_SWORD.get());
            com.dsh.eldencombat.item.WeaponStacks.setState(weapon,
                    new com.dsh.eldencombat.core.WeaponState(2,com.dsh.eldencombat.core.Affinity.HEAVY,false));
            p.getInventory().setItem(8,weapon);
            p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(ModItems.SACRED_RELIC_SWORD.get()));
            p.connection.teleport(0,81,0,0,0);
            p.getData(ModAttachments.COMBAT_STATE).combatMode=true;
            com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).setFp(100);
            p.serverLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true,p.server);
            // §2.1 战灰解锁：死亡前授予一次，并当场验证"重复授予是空操作"（奖励来源重试不会翻倍）
            require(com.dsh.eldencombat.server.skills.AshUnlocks.grant(p,ASH_PROBE.toString()),
                    "ash unlock granted before death");
            require(!com.dsh.eldencombat.server.skills.AshUnlocks.grant(p,ASH_PROBE.toString()),
                    "ash unlock grant is idempotent (re-grant is a no-op)");
        });
        if(frame==2) pressKey(EldenCombatClient.SKILL,ClientSkills::casting,"death cleanup cast accepted",ticks);
        if(frame==18) onServer(mc,p->{
            require(!p.serverLevel().getEntitiesOfClass(com.dsh.eldencombat.entity.skill.GoldenWaveEntity.class,
                    p.getBoundingBox().inflate(20),w->w.owner()==p).isEmpty(),"death cleanup fixture emitted wave");
            p.setGameMode(GameType.SURVIVAL);
            p.hurt(p.damageSources().genericKill(),10000);
            require(!p.isAlive(),"lifecycle fixture really died");
        });
        if(frame==24) mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(
                net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        if(frame==40) onServer(mc,p->{
            require(p.isAlive(),"actual death respawn completed");
            require(com.dsh.eldencombat.server.PlayerAttributes.of(p).strength()==7,
                    "attribute attachment inherited on actual death");
            require(com.dsh.eldencombat.server.skills.AshUnlocks.owns(p,ASH_PROBE),
                    "ash unlock survived actual death/respawn");
            EldenCombat.LOGGER.info("ASH_UNLOCK_DEATH_PASS: 战灰解锁在真实死亡/重生后保留（服务端存档为权威）");
            require(com.dsh.eldencombat.item.WeaponStacks.state(p.getInventory().getItem(8)).upgradeLevel()==2,
                    "weapon component retained with keepInventory on death");
            require(com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).fp()==100,"actual respawn refilled FP");
            require(!SkillDriver.cast(p).isCasting(),"respawn has no transient cast");
            require(p.server.overworld().getEntitiesOfClass(com.dsh.eldencombat.entity.skill.GoldenWaveEntity.class,
                    new net.minecraft.world.phys.AABB(-20,79,-20,20,90,20)).isEmpty(),"death removed emitted wave");
            p.setGameMode(GameType.CREATIVE);
            com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).setFp(37);
            SkillDriver.begin(p,"gold_breaker",p.level().getGameTime());
            // §3.4「切维度」：带一件改数值上限的护符跨维度，验证容器与倍率跟着玩家走（后面 56/57 帧断言）
            require(com.dsh.eldencombat.server.Talismans.equip(p,
                    new ItemStack(ModItems.RADAGON_SORESEAL.get()))
                    ==com.dsh.eldencombat.server.Talismans.Result.EQUIPPED,
                    "FP talisman equipped before the dimension transfer");
            require(Math.abs(com.dsh.eldencombat.server.Talismans.maxFpBonus(p)-30f)<1.0e-4f
                            &&Math.abs(com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).maxFp()-130f)<1.0e-4f,
                    "talisman FP bonus applied before the dimension transfer");
            p.teleportTo(p.server.getLevel(net.minecraft.world.level.Level.NETHER),0,90,0,0,0);
        });
        if(frame==56) onServer(mc,p->{
            require(p.level().dimension()==net.minecraft.world.level.Level.NETHER,"actual dimension transfer completed");
            require(!SkillDriver.cast(p).isCasting()&&!p.isNoGravity(),"dimension transfer cleared cast and gravity");
            require(com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).fp()==37,"dimension transfer did not refill FP");
            // §3.4「切维度」前半程：护符容器与它的上限加成一起到了另一个维度，且没有借切维度补 FP
            require(com.dsh.eldencombat.server.Talismans.loadout(p).get(0).is(ModItems.RADAGON_SORESEAL.get()),
                    "talisman container survived the cross-dimension transfer");
            require(Math.abs(com.dsh.eldencombat.server.Talismans.maxFpBonus(p)-30f)<1.0e-4f
                            &&Math.abs(com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).maxFp()-130f)<1.0e-4f,
                    "talisman FP bonus still applied inside the other dimension");
            p.teleportTo(p.server.overworld(),0,81,0,0,0);
        });
        // ---- §3 护符闭环：迁移 / 组件保留 / 聚合出口 / 多槽配置 / 上限与刷 FP / 死亡保留 / 客户端同步 ----
        if(frame==58) onServer(mc,p->{
            // 测试前置（不是玩法来源）：配置复位到默认单槽 + 清空护符栏，让本相位与上一次运行无关。
            new com.dsh.eldencombat.core.TalismanRules().save(EldenCombat.talismanConfigPath());
            EldenCombat.reloadConfig();
            require(com.dsh.eldencombat.core.TalismanRules.current().activeSlots()==1,
                    "talisman default slot count is 1 (existing behaviour)");
            var loadout=com.dsh.eldencombat.server.Talismans.loadout(p);
            // 测试前置：上一相位留下的护符先**正常卸下**（退回背包）再清空，避免夹具自己销毁物品；
            // 背包满时卸下会被拒（TalismanEquipment.unequip 返回 INVENTORY_FULL），那时 clear() 丢掉的
            // 也只是夹具自己造的这件，不是玩家资产。
            if(!loadout.get(0).isEmpty())com.dsh.eldencombat.server.TalismanEquipment.unequip(p,0);
            loadout.clear();
            com.dsh.eldencombat.server.Talismans.refresh(p);
            require(com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).fp()==37,
                    "talisman refresh never refills FP");
            // §3.2 旧数据迁移：旧存档里写的是**裸路径**
            p.setData(ModAttachments.TALISMAN,"warrior_jar_shard");
            require(com.dsh.eldencombat.server.Talismans.migrateLegacy(p),"legacy bare-path record migrated once");
            require(loadout.get(0).is(ModItems.WARRIOR_JAR_SHARD.get()),"migrated slot holds the real item");
            require(!com.dsh.eldencombat.server.Talismans.migrateLegacy(p),"legacy migration is idempotent");
            require(p.getData(ModAttachments.TALISMAN).isEmpty(),"legacy record cleared after migration");
            EldenCombat.LOGGER.info("TALISMAN_MIGRATE_PASS: 旧裸路径 warrior_jar_shard 一次性迁移成真实 ItemStack，重复调用不重复发放");
            // 未知物品：记录必须留着，不能被悄悄清掉
            loadout.clear();
            p.setData(ModAttachments.TALISMAN,"not_a_real_talisman_at_all");
            require(!com.dsh.eldencombat.server.Talismans.migrateLegacy(p),"unknown legacy item is not migrated");
            require("not_a_real_talisman_at_all".equals(p.getData(ModAttachments.TALISMAN)),
                    "unknown legacy record is preserved, never silently cleared");
            require(loadout.equippedCount()==0,"unknown legacy item granted nothing");
            p.setData(ModAttachments.TALISMAN,"");
            EldenCombat.LOGGER.info("TALISMAN_MIGRATE_UNKNOWN_PASS: 未知物品的旧记录被保留（不静默清掉），也没有凭空发放");
            loadout.clear();
            com.dsh.eldencombat.server.Talismans.refresh(p);
        });
        if(frame==60) onServer(mc,p->{
            // 真实右键路径：手持这件真实物品，走 TalismanItem.use 调用的同一个服务端入口
            var crafted=new ItemStack(ModItems.WARRIOR_JAR_SHARD.get());
            crafted.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    net.minecraft.network.chat.Component.literal("烟测护符·组件保留"));
            crafted.set(net.minecraft.core.component.DataComponents.DAMAGE,7);
            p.setItemSlot(EquipmentSlot.MAINHAND,crafted);
            require(com.dsh.eldencombat.server.TalismanEquipment.equipFromHand(p,
                    net.minecraft.world.InteractionHand.MAIN_HAND)==com.dsh.eldencombat.server.TalismanEquipment.Status.EQUIPPED,
                    "real right-click equip path equipped the talisman");
            var stored=com.dsh.eldencombat.server.Talismans.loadout(p).get(0);
            require(stored.is(ModItems.WARRIOR_JAR_SHARD.get()),"slot 0 holds the talisman item");
            require(stored.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME)!=null
                    &&"烟测护符·组件保留".contentEquals(stored.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString()),
                    "stored ItemStack keeps the custom name component");
            require(stored.getOrDefault(net.minecraft.core.component.DataComponents.DAMAGE,0)==7,
                    "stored ItemStack keeps the damage component");
            require(p.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).isEmpty(),"equipping consumed exactly the held item");
            // §3.3 数值出口必须是"读容器聚合"的结果
            require(Math.abs(com.dsh.eldencombat.server.Talismans.damageMultiplier(p)-1.08f)<1.0e-4f,
                    "damage outlet reads the container aggregate (x1.08)");
            require(Math.abs(com.dsh.eldencombat.server.Talismans.staminaRegenMultiplier(p)-1.20f)<1.0e-4f,
                    "stamina outlet reads the container aggregate (x1.20)");
            require(Math.abs(com.dsh.eldencombat.server.Talismans.maxFpBonus(p))<1.0e-4f,
                    "warrior jar shard has no FP bonus");
            EldenCombat.LOGGER.info("TALISMAN_EQUIP_PASS: 真实装备 → 槽0 保留 customName/DAMAGE 组件；出口 伤害×1.08 精力×1.20");
        });
        if(frame==62) onServer(mc,p->{
            // 槽数 1→3：写配置 + 走 /eldencombat reload 的同一条路径（读配置 + refreshAll）
            var rules=new com.dsh.eldencombat.core.TalismanRules();
            rules.slots=3;
            rules.save(EldenCombat.talismanConfigPath());
            EldenCombat.reloadConfig();
            require(com.dsh.eldencombat.core.TalismanRules.current().activeSlots()==3,"slot count reloaded from config");
            var loadout=com.dsh.eldencombat.server.Talismans.loadout(p);
            require(loadout.size()==3,"container grew to the configured slot count");
            require(loadout.get(0).is(ModItems.WARRIOR_JAR_SHARD.get()),"reload kept the existing talisman in its slot");
            var second=new ItemStack(ModItems.DRAGONCREST_SHIELD_TALISMAN.get());
            p.setItemSlot(EquipmentSlot.MAINHAND,second);
            require(com.dsh.eldencombat.server.TalismanEquipment.equipFromHand(p,
                    net.minecraft.world.InteractionHand.MAIN_HAND)==com.dsh.eldencombat.server.TalismanEquipment.Status.EQUIPPED,
                    "second talisman equipped into slot 2");
            require(Math.abs(com.dsh.eldencombat.server.Talismans.damageTakenMultiplier(p)-0.82f)<1.0e-4f,
                    "aggregate damage taken x0.82 (18% reduction from slot 2)");
            require(Math.abs(com.dsh.eldencombat.server.Talismans.healBonus(p)-1.0f)<1.0e-4f,
                    "aggregate heal +1.0 from slot 2");
            require(Math.abs(com.dsh.eldencombat.server.Talismans.damageMultiplier(p)-1.08f)<1.0e-4f,
                    "slot 1 damage bonus still aggregated alongside slot 2");
            // 重复同款：有空格时按集中规则拒绝（不是"静默换掉"）
            var duplicate=new ItemStack(ModItems.WARRIOR_JAR_SHARD.get());
            p.setItemSlot(EquipmentSlot.MAINHAND,duplicate);
            require(com.dsh.eldencombat.server.TalismanEquipment.equipFromHand(p,
                    net.minecraft.world.InteractionHand.MAIN_HAND)==com.dsh.eldencombat.server.TalismanEquipment.Status.REJECTED,
                    "duplicate talisman rejected by the centralized rule");
            require(Math.abs(com.dsh.eldencombat.server.Talismans.damageMultiplier(p)-1.08f)<1.0e-4f
                            &&loadout.get(0).is(ModItems.WARRIOR_JAR_SHARD.get())
                            &&loadout.get(1).is(ModItems.DRAGONCREST_SHIELD_TALISMAN.get()),
                    "rejected duplicate changed nothing");
            // 第三件：多槽是"先求和再夹紧"，不是逐件各算各的
            var third=new ItemStack(ModItems.GREEN_TURTLE_TALISMAN.get());
            p.setItemSlot(EquipmentSlot.MAINHAND,third);
            require(com.dsh.eldencombat.server.TalismanEquipment.equipFromHand(p,
                    net.minecraft.world.InteractionHand.MAIN_HAND)==com.dsh.eldencombat.server.TalismanEquipment.Status.EQUIPPED,
                    "third talisman equipped into slot 3");
            require(Math.abs(com.dsh.eldencombat.server.Talismans.staminaRegenMultiplier(p)-1.50f)<1.0e-4f,
                    "aggregate sums slot 1 + slot 3 stamina regen (0.20+0.30 -> x1.50)");
            require(loadout.firstEmpty()<0,"all three configured slots are full");
            // 栏满时右键同一件 = 卸下（保留旧的切换手感，也是"只能右键"时的唯一卸下途径）
            p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(ModItems.WARRIOR_JAR_SHARD.get()));
            require(com.dsh.eldencombat.server.TalismanEquipment.equipFromHand(p,
                    net.minecraft.world.InteractionHand.MAIN_HAND)==com.dsh.eldencombat.server.TalismanEquipment.Status.UNEQUIPPED,
                    "full bar + right-clicking an equipped talisman toggles it off");
            require(loadout.get(0).isEmpty(),"toggled-off slot is empty");
            require(Math.abs(com.dsh.eldencombat.server.Talismans.damageMultiplier(p)-1.0f)<1.0e-4f,
                    "toggled-off talisman left the aggregate");
            require(countItem(p,ModItems.WARRIOR_JAR_SHARD.get())>0,"toggled-off talisman returned to the inventory");
            var back=new ItemStack(ModItems.WARRIOR_JAR_SHARD.get());
            p.setItemSlot(EquipmentSlot.MAINHAND,back);
            require(com.dsh.eldencombat.server.TalismanEquipment.replace(p,0,
                    net.minecraft.world.InteractionHand.MAIN_HAND)==com.dsh.eldencombat.server.TalismanEquipment.Status.EQUIPPED,
                    "talisman put back into slot 1 for the later phases");
            EldenCombat.LOGGER.info("TALISMAN_MULTISLOT_PASS: 槽数 1→3 经配置 reload 生效；三件聚合 受伤×0.82 治疗+1.0 精力×1.50；重复同款被拒；栏满右键=卸下");
        });
        if(frame==64) onServer(mc,p->{
            // 槽数 3→1：槽外物品必须退回背包，绝不销毁
            int shieldBefore=countItem(p,ModItems.DRAGONCREST_SHIELD_TALISMAN.get());
            int turtleBefore=countItem(p,ModItems.GREEN_TURTLE_TALISMAN.get());
            new com.dsh.eldencombat.core.TalismanRules().save(EldenCombat.talismanConfigPath());
            EldenCombat.reloadConfig();
            require(com.dsh.eldencombat.core.TalismanRules.current().activeSlots()==1,"slot count reloaded back to 1");
            require(com.dsh.eldencombat.server.Talismans.loadout(p).size()==1,"container shrank to the configured slot count");
            require(countItem(p,ModItems.DRAGONCREST_SHIELD_TALISMAN.get())>shieldBefore
                            &&countItem(p,ModItems.GREEN_TURTLE_TALISMAN.get())>turtleBefore,
                    "both overflow talismans were returned to the inventory, not destroyed");
            require(com.dsh.eldencombat.server.Talismans.loadout(p).get(0).is(ModItems.WARRIOR_JAR_SHARD.get()),
                    "slot 1 kept its talisman through the slot-count change");
            require(Math.abs(com.dsh.eldencombat.server.Talismans.staminaRegenMultiplier(p)-1.20f)<1.0e-4f,
                    "only slot 1 remains in the aggregate (regen x1.20)");
            require(Math.abs(com.dsh.eldencombat.server.Talismans.healBonus(p))<1.0e-4f
                            &&Math.abs(com.dsh.eldencombat.server.Talismans.damageTakenMultiplier(p)-1.0f)<1.0e-4f,
                    "returned talismans no longer contribute to the aggregate");
            EldenCombat.LOGGER.info("TALISMAN_SLOT_SHRINK_PASS: 槽数 3→1 时槽外两件护符都退回背包（物品守恒，未销毁）");
        });
        if(frame==66) onServer(mc,p->{
            // §3.4 FP 上限：装上 +30，换下回落；反复换装不能补 FP
            var seal=new ItemStack(ModItems.RADAGON_SORESEAL.get());
            p.setItemSlot(EquipmentSlot.MAINHAND,seal);
            require(com.dsh.eldencombat.server.TalismanEquipment.replace(p,0,
                    net.minecraft.world.InteractionHand.MAIN_HAND)==com.dsh.eldencombat.server.TalismanEquipment.Status.EQUIPPED,
                    "replace entry equipped the FP talisman into slot 1");
            var fp=com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p);
            require(Math.abs(fp.bonusMaxFp()-30f)<1.0e-4f,"FP bonus +30 applied from the container");
            require(Math.abs(fp.maxFp()-130f)<1.0e-4f,"max FP is 100+30");
            require(fp.fp()==37,"equipping an FP talisman does not refill FP");
            require(countItem(p,ModItems.WARRIOR_JAR_SHARD.get())>0,"replaced talisman returned to the inventory");
            fp.setFp(100);
            var jar=new ItemStack(ModItems.WARRIOR_JAR_SHARD.get());
            p.setItemSlot(EquipmentSlot.MAINHAND,jar);
            require(com.dsh.eldencombat.server.TalismanEquipment.replace(p,0,
                    net.minecraft.world.InteractionHand.MAIN_HAND)==com.dsh.eldencombat.server.TalismanEquipment.Status.EQUIPPED,
                    "swap back to the non-FP talisman");
            require(fp.fp()==100&&Math.abs(fp.maxFp()-100f)<1.0e-4f,
                    "FP stays at 100 after the max-FP bonus is removed (no clamping loss, no refill)");
            var seal2=new ItemStack(ModItems.RADAGON_SORESEAL.get());
            p.setItemSlot(EquipmentSlot.MAINHAND,seal2);
            com.dsh.eldencombat.server.TalismanEquipment.replace(p,0,net.minecraft.world.InteractionHand.MAIN_HAND);
            require(fp.fp()==100&&Math.abs(fp.maxFp()-130f)<1.0e-4f,
                    "re-equipping the FP talisman raises the cap without refilling current FP");
            EldenCombat.LOGGER.info("TALISMAN_FP_PASS: 上限 100→130→100 随容器变化；反复换装当前 FP 恒为 100（无法刷 FP）");
            // 死亡保留相位用带自定义名的护符
            var named=new ItemStack(ModItems.WARRIOR_JAR_SHARD.get());
            named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    net.minecraft.network.chat.Component.literal("烟测护符·死亡保留"));
            p.setItemSlot(EquipmentSlot.MAINHAND,named);
            com.dsh.eldencombat.server.TalismanEquipment.replace(p,0,net.minecraft.world.InteractionHand.MAIN_HAND);
            require(com.dsh.eldencombat.server.Talismans.loadout(p).get(0)
                    .get(net.minecraft.core.component.DataComponents.CUSTOM_NAME)!=null,"named talisman equipped for the death phase");
        });
        if(frame==57){
            // 跨维度后原版要求客户端回 AcceptTeleportation 才会关掉 isChangingDimension；
            // 若没关，ServerPlayer.hurt 会**无条件**返回 false（连 generic_kill 都免疫）。
            // 夹具在第 40/56 帧做过跨维度 teleportTo，这里同维度重发一次位置包，把原版握手续完。
            onServer(mc,p->{
                if(p.isChangingDimension())p.connection.teleport(p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot());
                // §3.4「切维度」后半程：回到主世界后容器与上限加成仍在，且没有补 FP
                require(com.dsh.eldencombat.server.Talismans.loadout(p).get(0).is(ModItems.RADAGON_SORESEAL.get())
                                &&Math.abs(com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).maxFp()-130f)<1.0e-4f,
                        "talisman container and FP bonus survived the return trip");
            });
            EldenCombat.LOGGER.info("TALISMAN_DIMENSION_HANDSHAKE: 重发位置包，客户端 screen={}",mc.screen);
        }
        if(frame==61) onServer(mc,p->{
            if(p.isChangingDimension()){
                EldenCombat.LOGGER.warn("TALISMAN_DIMENSION_HANDSHAKE: 客户端 4 tick 内未回 AcceptTeleportation，"
                        +"夹具显式调用原版 hasChangedDimension() 结束切换状态（这是**夹具脚手架**，不是玩法规则）");
                p.hasChangedDimension();
            }
            EldenCombat.LOGGER.info("TALISMAN_DIMENSION_HANDSHAKE: changingDimension={}",p.isChangingDimension());
        });
        if(frame>=68&&frame<=71) onServer(mc,p->{
            // 第二次真实死亡。可能被挡住的路径有三条（原版 ServerPlayer.hurt）：
            //   ①isInvulnerableTo()：isRemoved() / Entity.invulnerable / **isChangingDimension()**；
            //   ②spawnInvulnerableTime>0（重生后 60 tick，只有 generic_kill 这类 BYPASSES_INVULNERABILITY 才免疫）；
            //   ③LivingEntity 的 invulnerableTime>10 伤害冷却（夹具清 0，属**测试前置**不是玩法规则）。
            // 夹具因此在 68..71 每 tick 重试，并把判别状态打进日志，失败时必须能指出是哪条挡住。
            if(frame==68){
                var probe=p.damageSources().genericKill();
                EldenCombat.LOGGER.info("TALISMAN_DEATH_DIAG(before): changingDimension={} removed={} gameMode={} "
                                +"abilitiesInvulnerable={} health={} invulnerableTime={} invulnerableTo={}",
                        p.isChangingDimension(),p.isRemoved(),p.gameMode.getGameModeForPlayer(),
                        p.getAbilities().invulnerable,p.getHealth(),p.invulnerableTime,p.isInvulnerableTo(probe));
                p.setGameMode(GameType.SURVIVAL);
            }
            if(p.isAlive()){
                p.invulnerableTime=0;
                boolean hurt=p.hurt(p.damageSources().genericKill(),10000);
                EldenCombat.LOGGER.info("TALISMAN_DEATH_ATTEMPT: frame={} hurt={} alive={} health={} "
                                +"changingDimension={} invulnerableTo={}",
                        frame,hurt,p.isAlive(),p.getHealth(),p.isChangingDimension(),
                        p.isInvulnerableTo(p.damageSources().genericKill()));
            }
            if(frame==71) require(!p.isAlive(),"second death with a talisman in the bar");
        });
        if(frame==72) mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(
                net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        if(frame==74) onServer(mc,p->{
            // §3.5 反证：服务端手工下发一份"改过名"的展示数据
            PacketDistributor.sendToPlayer(p,new com.dsh.eldencombat.network.TalismanDataPayload(
                    java.util.List.of(new com.dsh.eldencombat.core.TalismanSpec("eldencombat:warrior_jar_shard",
                            "同步测试名","",30f,0.5f,0.25f,0f,3f))));
            require("战士壶碎片".equals(com.dsh.eldencombat.server.Talismans
                    .spec("eldencombat:warrior_jar_shard").displayName()),
                    "server-side static table still holds the datapack name");
        });
        if(frame==76) {
            var synced=com.dsh.eldencombat.client.TalismanClientData.spec("eldencombat:warrior_jar_shard");
            require("同步测试名".equals(synced.displayName()),
                    "client tooltip data comes from the server-synced payload, not from a client-side static table");
            require(!("同步测试名".equals(com.dsh.eldencombat.server.Talismans
                    .spec("eldencombat:warrior_jar_shard").displayName())),
                    "that sync did not rewrite the server-authoritative table");
            require(com.dsh.eldencombat.client.TalismanClientData.spec("othermod:warrior_jar_shard").isNone(),
                    "a same-named talisman from another namespace is not confused with ours");
            EldenCombat.LOGGER.info("TALISMAN_CLIENT_SYNC_PASS: 客户端展示数据来自服务端同步 payload（服务端权威表未被改写）");
        }
        if(frame==78) onServer(mc,p->{
            require(p.isAlive(),"second respawn completed");
            var stored=com.dsh.eldencombat.server.Talismans.loadout(p).get(0);
            require(stored.is(ModItems.WARRIOR_JAR_SHARD.get()),"talisman survived actual death/respawn");
            require(stored.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME)!=null
                    &&"烟测护符·死亡保留".contentEquals(stored.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString()),
                    "talisman custom-name component survived actual death/respawn");
            require(Math.abs(com.dsh.eldencombat.server.Talismans.damageMultiplier(p)-1.08f)<1.0e-4f,
                    "talisman outlet recomputed after respawn");
            EldenCombat.LOGGER.info("TALISMAN_DEATH_PASS: 护符与自定义名组件在真实死亡/重生后保留，数值出口重生后重新生效");
            com.dsh.eldencombat.server.Talismans.syncDisplay(p);
            p.setGameMode(GameType.CREATIVE);
        });
        if(frame==79) onServer(mc,p->{
            // §3.4 背包满：**拒绝**卸下（而不是把护符丢在地上），护符必须还在栏里、数量不翻倍
            var inventory=p.getInventory();
            java.util.List<ItemStack> saved=new java.util.ArrayList<>();
            for(int i=0;i<36;i++) { saved.add(inventory.getItem(i)); inventory.setItem(i,new ItemStack(Items.STONE,64)); }
            require(inventory.getFreeSlot()<0,"fixture really filled the inventory");
            require(com.dsh.eldencombat.server.TalismanEquipment.unequip(p,0)
                            ==com.dsh.eldencombat.server.TalismanEquipment.Status.INVENTORY_FULL,
                    "unequip is refused while the inventory is full");
            require(com.dsh.eldencombat.server.Talismans.loadout(p).get(0).is(ModItems.WARRIOR_JAR_SHARD.get()),
                    "refused unequip left the talisman in the talisman bar");
            require(countItem(p,ModItems.WARRIOR_JAR_SHARD.get())==0,"refused unequip did not duplicate the item");
            for(int i=0;i<36;i++) inventory.setItem(i,saved.get(i));
            require("烟测护符·死亡保留".equals(com.dsh.eldencombat.server.Talismans.loadout(p).get(0)
                            .getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                                    net.minecraft.network.chat.Component.empty()).getString()),
                    "talisman state is identical after the refused unequip");
            EldenCombat.LOGGER.info("TALISMAN_INVENTORY_FULL_PASS: 背包满时拒绝卸下（明确提示），护符留在栏里且未复制");
        });
        if(frame>=80) {
            if(!saveQueued) {
                saveQueued=true;
                require(networkPassed&&!networkFailed,"all network checks completed");
                onServer(mc,p->{
                    com.dsh.eldencombat.server.PlayerAttributes.set(p,new com.dsh.eldencombat.core.AttributeSet(7,3,0,0,0));
                    var weapon=new ItemStack(ModItems.STRAIGHT_SWORD.get());
                    com.dsh.eldencombat.item.WeaponStacks.setState(weapon,
                            new com.dsh.eldencombat.core.WeaponState(2,com.dsh.eldencombat.core.Affinity.HEAVY,false));
                    p.getInventory().setItem(0,weapon);
                    com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).setFp(37);
                    p.server.saveEverything(false,true,true);
                    EldenCombat.LOGGER.info("CLIENT_PERSISTENCE_SAVED: attributes, +2 heavy weapon, FP37, ash unlock, talisman(含组件); awaiting restart");
                    mc.execute(mc::stop);
                });
            }
            return;
        }
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Pre event) {
        if(!ENABLED||!initialized||stage<0||stage>=FIXTURES.length)return;
        var fixture=FIXTURES[stage];
        for(var player:event.getServer().getPlayerList().getPlayers()) {
            var state=player.getData(ModAttachments.COMBAT_STATE);
            var config=EldenCombat.config(); state.ensureInit(config);
            var weapon=fixture.great?WeaponClass.GREAT_SWORD:WeaponClass.LIGHT_SWORD;
            player.setMainArm(fixture.left?HumanoidArm.LEFT:HumanoidArm.RIGHT);
            player.setYRot(0); player.setYBodyRot(0); player.setXRot(0);
            player.setItemSlot(EquipmentSlot.MAINHAND,fixture.offHand()?ItemStack.EMPTY:new ItemStack(fixture.heldItem()));
            player.setItemSlot(EquipmentSlot.OFFHAND,fixture.offHand()?new ItemStack(fixture.heldItem())
                    : GRIP_CHECK&&fixture.action()!=ActionId.GUARD?ItemStack.EMPTY:new ItemStack(ModItems.SHIELD.get()));
            player.setItemSlot(EquipmentSlot.HEAD,fixture.armor?new ItemStack(Items.IRON_HELMET):ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.CHEST,fixture.armor?new ItemStack(Items.IRON_CHESTPLATE):ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.LEGS,fixture.armor?new ItemStack(Items.IRON_LEGGINGS):ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.FEET,fixture.armor?new ItemStack(Items.IRON_BOOTS):ItemStack.EMPTY);
            state.combatMode=stage!=FIXTURES.length-1;
            state.guarding=fixture.action==ActionId.GUARD;
            if(GRIP_CHECK) {
                if(fixture.name().startsWith("ref-bow-draw-")&&fixture.frame()>0&&ticks%40>=20-fixture.frame())
                    player.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
                else if(state.guarding) player.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
                else player.stopUsingItem();
            }
            ActionId action=state.guarding?ActionId.NONE:fixture.action;
            if(state.action!=action) {
                if(action==ActionId.NONE)state.clearAction();
                else state.acceptAction(CombatRules.specFor(config,weapon,action),player.level().getGameTime()-fixture.frame);
            }
            state.actionStartTick=player.level().getGameTime()-fixture.frame;
            state.actionWeapon=weapon;
            state.actionSlim=PlayerAppearance.slim(player);
            state.actionLeftHanded=fixture.left;
            state.actionItem=BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString();
            state.stamina=config.stamina.max;
            state.rollDirX=0; state.rollDirZ=-1;
            if(action==ActionId.ROLL)player.teleportTo(0,81,0);
        }
    }
    @SubscribeEvent public static void capture(RenderFrameEvent.Post event) {
        if(!ENABLED||screenshot==null)return;
        var mc=Minecraft.getInstance(); String name=screenshot; screenshot=null;
        if(GRIP_CHECK&&!FIXTURES[stage].first()) {
            try { exportGripProof(mc,name); }
            catch(Exception failure) { require(false,"grip proof export: "+failure); }
        }
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->EldenCombat.LOGGER.info("CLIENT_SMOKE_CAPTURE: {} {}",name,message.getString()));
    }

    /** Debug-only: use the live rendered arm cube and pose, then reproduce the actual item chain. */
    private static void exportGripProof(Minecraft mc,String name) throws Exception {
        var fixture=FIXTURES[stage];
        var player=mc.player;
        var stack=fixture.offHand()?player.getOffhandItem():player.getMainHandItem();
        var asset=com.dsh.eldencombat.client.WeaponAssets.entryFor(stack);
        if(asset==null || asset.proportion()==null) return; // vanilla diamond is the screenshot reference
        var renderer=(PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(player);
        boolean left=fixture.offHand()?!fixture.left():fixture.left();
        var hand=left?HumanoidArm.LEFT:HumanoidArm.RIGHT;
        var arm=left?renderer.getModel().leftArm:renderer.getModel().rightArm;
        float[][] cube={null};
        arm.visit(new com.mojang.blaze3d.vertex.PoseStack(),(pose,path,index,box)->{
            if(cube[0]==null) cube[0]=new float[]{box.minX,box.maxY-4,box.minZ,box.maxX,box.maxY,box.maxZ};
        });
        if(cube[0]==null) throw new IllegalStateException("No live arm cube");
        float partial=mc.getTimer().getGameTimeDeltaPartialTick(false);
        var position=player.getPosition(partial);
        var root=new com.mojang.blaze3d.vertex.PoseStack();
        root.translate(position.x,position.y,position.z);
        root.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180-net.minecraft.util.Mth.rotLerp(partial,player.yBodyRotO,player.yBodyRot)));
        root.scale(-.9375f,-.9375f,.9375f);
        root.translate(0,-1.501,0);
        var palm=new com.mojang.blaze3d.vertex.PoseStack();
        palm.mulPose(root.last().pose());
        arm.translateAndRotate(palm);
        var item=new com.mojang.blaze3d.vertex.PoseStack();
        item.mulPose(root.last().pose());
        renderer.getModel().translateToHand(hand,item);
        item.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90));
        item.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
        item.translate((left?-1:1)/16.0,.125,-.625);
        var context=left?net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND:net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        var display=mc.getItemRenderer().getModel(stack,mc.level,player,0).getTransforms().getTransform(context);
        display.apply(left,item);
        item.translate(-.5,-.5,-.5);
        var anchor=com.dsh.eldencombat.client.EquipmentRenderer.palmAnchor(stack,context,left);
        float gx=anchor.x, gy=anchor.y;
        item.translate(gx,gy,anchor.z);
        var id=BuiltInRegistries.ITEM.getKey(stack.getItem());
        com.dsh.eldencombat.client.WeaponCorrections.get(id.getPath()).apply(item);
        item.translate(-gx,-gy,-anchor.z);
        var profile=asset.proportion();
        float scale=profile.heldScale((float)(asset.bounds().maxY()-asset.bounds().minY()),false,display.scale.y);
        item.mulPose(com.dsh.eldencombat.core.WeaponDisplayTransforms.matrix(asset.bounds(),
                com.dsh.eldencombat.core.WeaponDisplayTransforms.View.THIRD_PERSON,left,scale,profile.gripX(),profile.gripY(),profile.gripZ(),anchor.x,anchor.y,anchor.z));
        var center=item.last().pose().transformPosition(new org.joml.Vector3f(profile.gripX(),profile.gripY(),profile.gripZ()));
        var local=new org.joml.Matrix4f(palm.last().pose()).invert().transformPosition(new org.joml.Vector3f(center)).mul(16);
        float[] box=cube[0];
        boolean inside=local.x>=box[0]&&local.x<=box[3]&&local.y>=box[1]&&local.y<=box[4]&&local.z>=box[2]&&local.z<=box[5];
        var palmCenter=palm.last().pose().transformPosition(new org.joml.Vector3f((box[0]+box[3])/32,(box[1]+box[4])/32,(box[2]+box[5])/32));
        var proof=new java.util.LinkedHashMap<String,Object>();
        proof.put("item",id.toString()); proof.put("skin",player.getSkin().model().name()); proof.put("arm",hand.name());
        proof.put("usingItem",player.isUsingItem()); proof.put("useTicks",player.getTicksUsingItem());
        proof.put("palmDefinition","distal 4 model pixels of the live vanilla arm cube; no new hand geometry");
        proof.put("palmLocalPixels",box); proof.put("palmToWorld",palm.last().pose().get(new float[16]));
        proof.put("itemToWorld",item.last().pose().get(new float[16]));
        proof.put("handleCenterWorld",new float[]{center.x,center.y,center.z});
        proof.put("handleCenterInPalmPixels",new float[]{local.x,local.y,local.z});
        proof.put("handleCenterInsidePalm",inside); proof.put("centerDistanceBlocks",center.distance(palmCenter));
        String regionPath=java.util.Set.of("spear","shield","long_bow").contains(id.getPath())?"utility_grip_regions.json":"sword_grip_regions.json";
        var resource=mc.getResourceManager().getResource(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID,"weapons/"+regionPath)).orElseThrow();
        try(var stream=resource.open()) {
            var regions=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for(var value:regions.getAsJsonArray("entries")) {
                var region=value.getAsJsonObject(); if(!region.get("id").getAsString().equals(id.toString())) continue;
                proof.put("region",region);
                if(!region.has("samples")) continue;
                var samples=region.getAsJsonArray("samples");
                var corners=new java.util.ArrayList<float[]>();
                for(var sample:samples) {
                    var s=sample.getAsJsonObject();
                    for(String x:new String[]{"minX","maxX"}) for(String z:new String[]{"minZ","maxZ"}) {
                        var world=item.last().pose().transformPosition(new org.joml.Vector3f(s.get(x).getAsFloat()/16,
                                s.get("y").getAsFloat()/16,s.get(z).getAsFloat()/16));
                        corners.add(new float[]{world.x,world.y,world.z});
                    }
                }
                proof.put("handleRegionWorldSamples",corners);
            }
        }
        var directory=mc.gameDirectory.toPath().resolve("grip-proof");
        java.nio.file.Files.createDirectories(directory);
        java.nio.file.Files.writeString(directory.resolve(name.replace(".png",".json")),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
        require(inside,"live handle center overlaps "+hand+" palm, distance="+center.distance(palmCenter));
    }
}


