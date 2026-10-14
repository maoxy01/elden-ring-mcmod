package com.dsh.eldencombat.testing;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.server.skills.*;
import com.dsh.eldencombat.entity.skill.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameRules;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Explicit isolated visual integration fixture; never runs in ordinary clients or servers. */
@EventBusSubscriber(modid=EldenCombat.MODID,value=Dist.CLIENT)
public final class VfxIntegrationHarness {
    private static final boolean ENABLED=Boolean.getBoolean("eldencombat.vfxSmoke");
    private static final String[] IDS={"wave_of_gold","gold_breaker","destined_death","takers_flames","waterfowl_dance","starcaller_cry","bloodboon_ritual","cursed_blood_slice"};
    private static int ticks, waiting;
    private static String capture;
    private static ArmorStand camera;
    private VfxIntegrationHarness() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!ENABLED) return;
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null) {
            if(++waiting>2400) { EldenCombat.LOGGER.error("VFX_SMOKE_FAIL startup timeout"); mc.stop(); }
            return;
        }
        mc.options.pauseOnLostFocus=false;
        mc.options.hideGui=true;
        mc.options.fov().set(70);
        int segment=ticks/120, frame=ticks%120;
        if(segment>=18) {
            EldenCombat.LOGGER.info("VFX_SMOKE_COMPLETE: eight skills day first-person/night orbit plus wave obstacles and forced missing-art outline; screenshots are fixture evidence, not manual acceptance");
            mc.stop();return;
        }
        int skill=segment<16?segment%8:segment==16?0:4;
        boolean orbit=segment>=8;
        String id=IDS[skill];
        System.setProperty("eldencombat.vfxSmokeMissing",String.valueOf(segment==17));
        if(frame==0) {
            var uuid=mc.player.getUUID();
            mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);if(p==null)return;
                var level=p.serverLevel();
                SkillDriver.cancel(p,p.level().getGameTime(),"visual fixture reset");
                for(var e:level.getEntities().getAll()) if(e instanceof SkillEffectEntity||e instanceof GoldenWaveEntity)e.discard();
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.server);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,p.server);
                level.setDayTime(orbit?18000:6000);
                for(int x=-12;x<=12;x++)for(int z=-12;z<=24;z++) {
                    level.setBlockAndUpdate(new BlockPos(x,80,z),Blocks.STONE.defaultBlockState());
                    for(int y=81;y<=87;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                    if(segment==16&&x>0&&z==5)for(int y=81;y<84;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState());
                }
                p.connection.teleport(0,81,0,0,15);p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                p.setHealth(p.getMaxHealth());
                var spec=SkillDriver.spec(id);
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("eldencombat",spec.itemId()))));
                var fp=SkillServerEvents.fp(p);fp.setFp(fp.maxFp());fp.clearCooldown(id);
                EldenCombat.LOGGER.info("VFX_SMOKE_SCENE {} {} orbit={}",segment,id,orbit);
            });
        }
        if(frame==10||frame>10&&frame<90) {
            var uuid=mc.player.getUUID();
            mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);if(p==null)return;
                var cast=SkillDriver.cast(p);long now=p.level().getGameTime();
                if(frame==10||cast.followupOpen(now)) {
                    var result=SkillCasting.request(p,now);
                    EldenCombat.LOGGER.info("VFX_SMOKE_INPUT {} frame={} result={}",id,frame,result);
                }
            });
        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        if(orbit) {
            if(camera==null)camera=new ArmorStand(mc.level,0,84,8);
            double angle=Math.toRadians(frame*.6);
            camera.setPos(Math.sin(angle)*8,85,Math.cos(angle)*8);
            camera.setYRot(180+(float)Math.toDegrees(angle));camera.setXRot(22);
            camera.xo=camera.getX();camera.yo=camera.getY();camera.zo=camera.getZ();
            camera.yRotO=camera.getYRot();camera.xRotO=camera.getXRot();mc.setCameraEntity(camera);
        } else mc.setCameraEntity(mc.player);
        if(frame>=12&&frame<=96&&frame%4==0) capture=String.format("vfx-%02d-%s-%03d.png",segment,id,frame);
        if(frame==115) {
            long count=java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false)
                    .filter(e->e instanceof SkillEffectEntity||e instanceof GoldenWaveEntity).count();
            EldenCombat.LOGGER.info("VFX_SMOKE_CLEANUP {} entities={}",id,count);
        }
        ticks++;
    }
    @SubscribeEvent public static void capture(RenderFrameEvent.Post event) {
        if(!ENABLED||capture==null)return;
        var mc=Minecraft.getInstance();var name=capture;capture=null;
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{});
    }
}
