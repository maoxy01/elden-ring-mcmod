package com.dsh.eldencombat.testing;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.compat.CombatRollCompat;
import com.dsh.eldencombat.core.*;
import com.dsh.eldencombat.network.CombatIntentPayload;
import com.dsh.eldencombat.registry.*;
import com.dsh.eldencombat.server.*;
import com.dsh.eldencombat.server.skills.*;
import net.combat_roll.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Opt-in real-client/server integration, never included in release jars. */
@EventBusSubscriber(modid=EldenCombat.MODID)
public final class PackIntegrationHarness {
    private static int ticks, playerTicks, phase, phaseTicks;
    private static boolean sawRoll, finished, prepared;
    private static float stamina, rollFp;
    private static Vec3 rollStart;
    private static ServerPlayer tester;
    static void check(boolean ok,String message) {
        if(!ok) throw new IllegalStateException("PACK_INTEGRATION_FAIL: "+message);
        EldenCombat.LOGGER.info("PACK_CHECK_PASS: {}",message);
    }
    @SubscribeEvent public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        if(!Boolean.getBoolean("eldencombat.packIntegration"))return;
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("packcheck")
                .then(net.minecraft.commands.Commands.literal("ready").executes(context->{
                    var p=context.getSource().getPlayerOrException();
                    if(!prepared || tester!=p || phase!=0)return 0;
                    phase=1;phaseTicks=0;
                    var combat=p.getData(ModAttachments.COMBAT_STATE);
                    combat.clearAction();combat.guarding=false;combat.combatMode=false;
                    SkillDriver.cancel(p,p.level().getGameTime(),"integration prepare roll");
                    p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                    p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,ItemStack.EMPTY);
                    p.getFoodData().setFoodLevel(20);p.resetAttackStrengthTicker();
                    stamina=combat.stamina;rollFp=SkillServerEvents.fp(p).fp();rollStart=p.position();ServerCombat.syncNow(p);
                    return 1;
                })));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(!Boolean.getBoolean("eldencombat.packIntegration") || finished ||
                (!event.getServer().isDedicatedServer() && !Boolean.getBoolean("eldencombat.packSingle")))return;
        ticks++;
        var server=event.getServer();
        if(server.getPlayerList().getPlayers().isEmpty()) {
            if(tester!=null){finished=true;EldenCombat.LOGGER.error("PACK_INTEGRATION_FAIL: client disconnected before completion");server.halt(false);return;}
            if(ticks>4800){finished=true;server.halt(false);throw new IllegalStateException("PACK_INTEGRATION_FAIL: client timeout");}
            return;
        }
        tester=server.getPlayerList().getPlayers().getFirst();
        playerTicks++;
        try {
            if(playerTicks==20) {prepare(tester);prepared=true;}
            if(phase==0)return;
            phaseTicks++;
            if(!sawRoll && CombatRollCompat.rolling(tester)) {
                sawRoll=true;
                check(tester.getData(ModAttachments.COMBAT_STATE).stamina>=stamina,"roll start has no legacy stamina charge before regeneration");
                check(SkillServerEvents.fp(tester).fp()==rollFp,"roll start does not spend project FP");
                check(tester.getData(ModAttachments.COMBAT_STATE).action!=ActionId.ROLL,"roll start has no legacy action");
                tester.setInvulnerable(false);
                float health=tester.getHealth();
                tester.hurt(tester.damageSources().generic(),1);
                check(tester.getHealth()<health,"default zero iframe roll receives real damage");
            }
            if(phaseTicks==200) {
                check(sawRoll,"upstream client input reached server admission");
                check(rollStart.distanceTo(tester.position())>.5,"upstream roll moved real player");
                check(tester.getData(ModAttachments.COMBAT_STATE).action!=ActionId.ROLL,"no legacy ROLL state");
                check(tester.getData(ModAttachments.COMBAT_STATE).stamina>=stamina,"new roll does not charge old stamina");
                rejectDuringSkill(tester);
            }
            if(phaseTicks==260) {
                finished=true;
                EldenCombat.LOGGER.info("PACK_INTEGRATION_SERVER_COMPLETE: real client connected; input ownership, backpack serialization, native roll, rejection passed");
                server.halt(false);
            }
        } catch(Throwable error) {
            finished=true; EldenCombat.LOGGER.error("PACK_INTEGRATION_FAIL",error);server.halt(false);
        }
    }
    private static void prepare(ServerPlayer p) throws Exception {
        if(Boolean.getBoolean("eldencombat.packRestart")) {
            int found=0;
            for(int i=0;i<p.getInventory().getContainerSize();i++) {
                var saved=p.getInventory().getItem(i);
                var name=saved.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
                if(name==null || !name.getString().equals("PackPersistence"))continue;
                found++;
                var slots=net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper.fromStack(saved).getInventoryHandler();
                check(slots.getStackInSlot(0).is(Items.APPLE) && slots.getStackInSlot(0).getCount()==17,"backpack world storage and player inventory survive real restart");
                check(slots.extractItem(0,64,false).getCount()==17 && slots.extractItem(0,64,false).isEmpty(),"restarted backpack cannot duplicate stored items");
                p.getInventory().setItem(i,ItemStack.EMPTY);
            }
            check(found==1,"exactly one persisted backpack found after restart");
        }
        var level=p.serverLevel();
        BlockPos at=new BlockPos(level.getSharedSpawnPos().getX(),140,level.getSharedSpawnPos().getZ());
        for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)level.setBlockAndUpdate(at.offset(x,-1,z),Blocks.STONE.defaultBlockState());
        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)for(int y=0;y<3;y++)level.setBlockAndUpdate(at.offset(x,y,z),Blocks.AIR.defaultBlockState());
        p.connection.teleport(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.setInvulnerable(true);p.getFoodData().setFoodLevel(20);
        var foreign=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("simplyswords:iron_longsword")));
        check(!foreign.isEmpty(),"Simply Swords item registered");
        check(!Weapons.controlsMelee(foreign),"foreign weapon native input ownership");
        check(Weapons.controlsMelee(new ItemStack(ModItems.STRAIGHT_SWORD.get())),"own weapon controlled input ownership");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,foreign);
        p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));
        check(!Weapons.controlsGuard(p),"foreign weapon and offhand shield preserve vanilla use");
        var state=p.getData(ModAttachments.COMBAT_STATE); state.clearAction();state.combatMode=true;
        var apply=ServerCombat.class.getDeclaredMethod("apply",ServerPlayer.class,CombatIntentPayload.class);apply.setAccessible(true);
        float before=state.stamina;
        apply.invoke(ServerCombat.INSTANCE,p,new CombatIntentPayload(100000,IntentType.LIGHT.ordinal(),-1,0,0));
        check(state.action==ActionId.NONE && before==state.stamina,"forged foreign LIGHT has no custom attack/payment");
        apply.invoke(ServerCombat.INSTANCE,p,new CombatIntentPayload(100001,IntentType.ROLL.ordinal(),-1,0,0));
        check(state.action==ActionId.NONE && before==state.stamina,"legacy ROLL rejected before effects");
        var sequences=ServerCombat.class.getDeclaredField("lastSequence");sequences.setAccessible(true);
        ((java.util.Map<?,?>)sequences.get(ServerCombat.INSTANCE)).remove(p.getUUID());
        backpack(p);
        ServerCombat.syncNow(p);
    }
    private static void backpack(ServerPlayer p) {
        ItemStack pack=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("sophisticatedbackpacks:backpack")));
        var slots=net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper.fromStack(pack).getInventoryHandler();
        check(slots!=null && slots.getSlots()>0,"backpack exposes real inventory capability");
        check(slots.insertItem(0,new ItemStack(Items.APPLE,17),false).isEmpty(),"backpack inserts real items");
        var tag=pack.save(p.registryAccess());
        ItemStack restored=ItemStack.parse(p.registryAccess(),tag).orElseThrow();
        var restoredSlots=net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper.fromStack(restored).getInventoryHandler();
        check(restoredSlots!=null && restoredSlots.getStackInSlot(0).getCount()==17,"backpack contents survive item serialization");
        check(restoredSlots.extractItem(0,64,false).getCount()==17 && restoredSlots.extractItem(0,64,false).isEmpty(),"backpack extraction does not duplicate");
        check(restoredSlots.insertItem(0,new ItemStack(Items.APPLE,17),false).isEmpty(),"backpack seeded for real save and restart");
        pack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("PackPersistence"));
        p.getInventory().add(pack);
    }
    private static void rejectDuringSkill(ServerPlayer p) {
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(ModItems.SACRED_RELIC_SWORD.get()));
        p.getData(ModAttachments.COMBAT_STATE).clearAction();
        p.getData(ModAttachments.COMBAT_STATE).combatMode=true;
        SkillServerEvents.fp(p).refillOnRespawn();
        check(SkillCasting.request(p,p.level().getGameTime())!=SkillTransactions.Result.REJECTED,"real skill starts for roll rejection test");
        int cast=SkillDriver.cast(p).castId(); float fp=SkillServerEvents.fp(p).fp();
        var packet=new Packets.RollPublish(p.getId(),null,new Vec3(1,0,0));
        ServerNetwork.handleRollPublish(packet,p.server,p);
        check(SkillDriver.cast(p).isCasting() && SkillDriver.cast(p).castId()==cast,"actual upstream packet cannot cancel skill windup");
        check(SkillServerEvents.fp(p).fp()==fp && !CombatRollCompat.rolling(p),"rejected upstream packet has no second payment/roll");
    }
}
