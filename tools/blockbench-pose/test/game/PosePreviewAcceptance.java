package com.dsh.eldencombat.testing;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.client.SkillPoseAnimator;
import com.dsh.eldencombat.client.equipment2d.EquipmentRouteSnapshot;
import com.dsh.eldencombat.client.equipment2d.ItemThickness;
import com.dsh.eldencombat.core.SkillPoseData;
import com.dsh.eldencombat.server.skills.RemembranceSkillSpec;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import java.nio.file.*;
import java.util.*;

/** Extra source set for one isolated acceptance run; never added to the shipped mod sources/JAR. */
@EventBusSubscriber(modid="eldencombat", value=Dist.CLIENT)
public class PosePreviewAcceptance {
    private static final List<Map<String,Object>> rows=new ArrayList<>();
    private static List<RemembranceSkillSpec> specs;
    private static int age, last=-1, startup;
    private static String shot;
    private static Path evidence;
    private static void active(Minecraft mc,String skill,int stage,int index,double tick) throws Exception {
        var field=SkillPoseAnimator.class.getDeclaredField("CASTING");field.setAccessible(true);
        var map=(Map<Integer,Object>)field.get(null);
        var type=Class.forName("com.dsh.eldencombat.client.SkillPoseAnimator$Active");
        var constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);
        map.put(mc.player.getId(),constructor.newInstance(skill,1,stage,index,mc.player.tickCount-tick));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null) {if(++startup>2400){EldenCombat.LOGGER.error("POSE_PREVIEW_FAIL startup");mc.stop();}return;}
        try {
            if(specs==null) {
                evidence=Path.of(System.getProperty("eldencombat.poseEvidence"));Files.createDirectories(evidence);
                specs=new ArrayList<>(RemembranceSkillSpec.load().values());specs.sort(Comparator.comparing(RemembranceSkillSpec::id));
                SkillPoseAnimator.apply(SkillPoseAnimator.prepare(mc.getResourceManager()));
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);mc.options.fov().set(50);mc.setScreen(null);
            }
            int index=age/15;
            if(index>=specs.size()) {
                Files.writeString(evidence.resolve("game-live.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("passed",true,"samples",rows,"sourceMotionProfile",false)));
                EldenCombat.LOGGER.info("POSE_PREVIEW_COMPLETE skills={} samples={}",specs.size(),rows.size());mc.stop();return;
            }
            var spec=specs.get(index);
            mc.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("eldencombat",spec.itemId()))));
            mc.player.setYRot(0);mc.player.setXRot(0);mc.player.yBodyRot=mc.player.yBodyRotO=0;mc.player.yHeadRot=mc.player.yHeadRotO=0;
            active(mc,spec.id(),2,1,2);
            if(age%15==10) shot=spec.id()+"-active-2tick.png";
            if(last!=index) {
                last=index;
                var model=new net.minecraft.client.model.PlayerModel<net.minecraft.world.entity.player.Player>(mc.getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER),false);
                SkillPoseData.Skill skill;
                try(var reader=mc.getResourceManager().getResourceOrThrow(ResourceLocation.fromNamespaceAndPath("eldencombat","skill_poses/"+spec.id()+".json")).openAsReader()) {
                    skill=SkillPoseData.parse(com.google.gson.JsonParser.parseReader(reader).getAsJsonObject());
                }
                var parts=Map.of("head",model.head,"body",model.body,"rightArm",model.rightArm,"leftArm",model.leftArm,"rightLeg",model.rightLeg,"leftLeg",model.leftLeg);
                for(int stage:new int[]{1,2,4}) for(int segment:new int[]{0,1,2}) for(double time:new double[]{0,2,4.5}) {
                    active(mc,spec.id(),stage,segment,time);
                    for(var part:parts.values())part.xRot=part.yRot=part.zRot=0;
                    if(!SkillPoseAnimator.applyPose(model,mc.player,mc.player.tickCount))throw new IllegalStateException("Pose not applied");
                    var rotations=new LinkedHashMap<String,float[]>();
                    for(var entry:parts.entrySet()) {
                        var p=entry.getValue();var track=skill.forStage(stage,segment).parts().get(entry.getKey());var expected=track==null?new float[3]:track.sample(time,new float[3]);
                        float[] actual={p.xRot,p.yRot,p.zRot};for(int axis=0;axis<3;axis++)if(Math.abs(actual[axis]-expected[axis])>1e-6)throw new IllegalStateException("Live pose mismatch");
                        rotations.put(entry.getKey(),actual);
                    }
                    for(boolean left:new boolean[]{false,true}) {
                        var pose=new PoseStack();model.translateToHand(left?HumanoidArm.LEFT:HumanoidArm.RIGHT,pose);
                        pose.mulPose(Axis.XP.rotationDegrees(-90));pose.mulPose(Axis.YP.rotationDegrees(180));pose.translate((left?-1:1)/16.0,.125,-.625);
                        var context=left?ItemDisplayContext.THIRD_PERSON_LEFT_HAND:ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
                        var entry=EquipmentRouteSnapshot.data().get("eldencombat:"+spec.itemId());
                        var baked=mc.getModelManager().getModel(EquipmentRouteSnapshot.standaloneModel(entry.ownStates().get(entry.baseState())));
                        var depth=ItemThickness.depth(baked,mc.player.getMainHandItem(),false);
                        if(depth==null)throw new IllegalStateException("Missing real baked weapon geometry");
                        float target=ItemThickness.target(pose,context);entry.display().resolve(context).apply(left,pose);pose.translate(-.5,-.5,-.5);ItemThickness.normalize(pose,depth,target);
                        rows.add(Map.of("id",spec.id(),"stage",SkillPoseData.stageName(stage),"index",segment,"tick",time,"hand",left?"left":"right","rotations",rotations,"itemToModel",pose.last().pose().get(new float[16]),"referenceThickness",ItemThickness.referenceScale(context),"bakedDepth",List.of(depth.min(),depth.max())));
                    }
                }
                active(mc,spec.id(),2,1,2);
            }
            age++;
        } catch(Exception error) {EldenCombat.LOGGER.error("POSE_PREVIEW_FAIL",error);mc.stop();}
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if(shot==null)return;var mc=Minecraft.getInstance();String name=shot;shot=null;
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{});
    }
    @SubscribeEvent public static void beforeFrame(RenderFrameEvent.Pre event) {
        var mc=Minecraft.getInstance();if(specs==null||mc.player==null||last<0||last>=specs.size())return;
        try {active(mc,specs.get(last).id(),2,1,2-mc.getTimer().getGameTimeDeltaPartialTick(false));}
        catch(Exception error){EldenCombat.LOGGER.error("POSE_PREVIEW_FAIL freeze",error);mc.stop();}
    }
}
