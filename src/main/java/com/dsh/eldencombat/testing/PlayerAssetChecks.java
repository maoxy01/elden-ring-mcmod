package com.dsh.eldencombat.testing;

import com.dsh.eldencombat.core.ActionId;
import com.dsh.eldencombat.core.WeaponClass;
import com.dsh.eldencombat.core.geom.Box;
import com.dsh.eldencombat.core.geom.Collision;
import com.dsh.eldencombat.skeleton.PlayerAssets;
import com.dsh.eldencombat.skeleton.ClipTiming;
import com.dsh.eldencombat.skeleton.SkinLayout;
import com.google.gson.JsonParser;
import com.dsh.eldencombat.skeleton.AnimationClip;
import org.joml.Vector3d;
import java.util.ArrayList;
import java.util.List;

/** Pure checks shared by JUnit and the in-game acceptance command. */
public final class PlayerAssetChecks {
    private PlayerAssetChecks() {}
    private static void check(List<String> results,String name,boolean ok) {
        results.add((ok?"PASS ":"FAIL ")+name);
    }
    public static List<String> run() {
        List<String> results=new ArrayList<>();
        var assets=PlayerAssets.get();
        for(boolean slim:new boolean[]{false,true}) {
            var model=assets.model(slim);
            check(results,"Player bone/cube count "+slim,model.bones.size()==17 && model.cubes.size()==10);
            boolean uvValid=true;
            for(var cube:model.cubes) for(int face=0;face<6;face++) for(boolean overlay:new boolean[]{false,true})
                for(float v:assets.skin(slim).uv(cube.name(),face,overlay))uvValid &= Float.isFinite(v)&&v>=0&&v<=1;
            check(results,"Skin and overlay UV bounds "+slim,uvValid);
            var front=assets.skin(slim).minecraftUv("head",5,false);
            var top=assets.skin(slim).minecraftUv("head",2,false);
            var side=assets.skin(slim).minecraftUv("head",0,false);
            check(results,"Native skin face orientation "+slim,front[0]>front[2] && top[1]>top[5]
                && Math.abs(side[2]-assets.skin(slim).uv("head",1,false)[0])<0.00001);
            var overlay=assets.skin(slim).minecraftUv("head",5,true);
            check(results,"Native overlay orientation "+slim,overlay[0]>overlay[2]);
            for(var weapon:new WeaponClass[]{WeaponClass.LIGHT_SWORD,WeaponClass.GREAT_SWORD}) {
                for(var action:ActionId.values()) {
                    String id=PlayerAssets.clipId(action,false,1,0,false);
                    check(results,"Player clip "+slim+" "+weapon+" "+id,assets.clip(weapon,slim,id)!=null);
                }
                for(String id:new String[]{"idle","walk","run","guard_idle","light_1","light_2","heavy_windup","heavy_release","charged_release","roll","parry","guard_hit","guard_break","drink","hit_stagger","death"}) {
                    var clip=assets.clip(weapon,slim,id);
                    boolean valid=true,mirrored=true,grip=true,rootStationary=true;
                    for(double tick=0;tick<=clip.lengthTicks();tick+=0.5) {
                        var right=assets.pose(weapon,slim,false,id,tick);
                        var left=assets.pose(weapon,slim,true,id,tick);
                        for(var bone:model.bones)valid &= right.boneWorld(bone.name()).isFinite() && left.boneWorld(bone.name()).isFinite();
                        var a=assets.sampledBlade(weapon,slim,false,id,tick);
                        var b=assets.sampledBlade(weapon,slim,true,id,tick);
                        valid &= Math.abs(a.from().distance(a.to())-assets.blade(weapon).from().distance(assets.blade(weapon).to()))<0.00001;
                        mirrored &= Math.abs(a.from().x+b.from().x)<0.00001 && Math.abs(a.from().y-b.from().y)<0.00001 && Math.abs(a.from().z-b.from().z)<0.00001
                            && Math.abs(a.to().x+b.to().x)<0.00001 && Math.abs(a.to().y-b.to().y)<0.00001 && Math.abs(a.to().z-b.to().z)<0.00001;
                        var root=right.boneOrigin("root"); rootStationary &= Math.abs(root.x)<0.00001 && Math.abs(root.z)<0.00001;
                        if(weapon==WeaponClass.GREAT_SWORD && !List.of("roll","parry","guard_idle","guard_hit","guard_break","drink","hit_stagger","death").contains(id)) {
                            var target=assets.socket(right,"sword_socket",false).transformPosition(new org.joml.Vector3f(0,-3/16f,0));
                            grip &= right.boneOrigin("hand_l").distance(target)<0.05;
                        }
                    }
                    check(results,"Finite bones/blade "+slim+" "+weapon+" "+id,valid);
                    check(results,"Mirrored hands "+slim+" "+weapon+" "+id,mirrored);
                    check(results,"No visual root X/Z displacement "+slim+" "+weapon+" "+id,rootStationary);
                    check(results,"Two-handed grip "+slim+" "+weapon+" "+id,grip);
                }
            }
        }
        var clip=assets.clip(WeaponClass.LIGHT_SWORD,false,"light_1");
        var head=assets.model(false).cubes.stream().filter(cube->cube.name().equals("head")).findFirst().orElseThrow();
        var armor=SkinLayout.armorUv(head,5);
        check(results,"Native armor orientation",armor[0]>armor[2]);
        check(results,"Configured hit-start retiming",ClipTiming.frame(clip,4,40,4,9,20)==clip.hitStart());
        check(results,"Configured hit-end retiming",ClipTiming.frame(clip,9,40,4,9,20)==clip.hitEnd());
        check(results,"Configured recovery retiming",ClipTiming.frame(clip,40,40,4,9,20)==clip.lengthTicks());
        var windup=assets.clip(WeaponClass.LIGHT_SWORD,false,"heavy_windup");
        check(results,"Full charge retiming",ClipTiming.frame(windup,40,90,-1,-1,40)==20);
        var eventClip=AnimationClip.parse(JsonParser.parseString("""
            {"id":"events","lengthTicks":10,"loop":true,"events":[
            {"tick":8,"type":"unequip"},{"tick":0,"type":"start"},{"tick":3,"type":"equip","item":"test"}],"tracks":{}}
            """).getAsJsonObject());
        check(results,"Unsorted equip events", "test".equals(eventClip.equippedAt(4)) && eventClip.equippedAt(8)==null);
        check(results,"Event zero fires once",eventClip.eventsBetween(-1,0).size()==1 && eventClip.eventsBetween(0,0).isEmpty());
        check(results,"Loop boundary event",eventClip.eventsBetween(9,10).size()==1 && eventClip.eventsBetween(10,11).isEmpty());
        check(results,"Skipped frame events",eventClip.eventsBetween(2,8).size()==2);
        Box target=new Box(-0.1,0,-0.1,0.1,1,0.1);
        check(results,"Fast blade crosses between endpoints",Collision.sweptCapsuleHitsBox(new Vector3d(-2,0.2,0),new Vector3d(-2,0.8,0),new Vector3d(2,0.2,0),new Vector3d(2,0.8,0),target,0.02));
        check(results,"Sweep outside target misses",!Collision.sweptCapsuleHitsBox(new Vector3d(-2,0.2,3),new Vector3d(-2,0.8,3),new Vector3d(2,0.2,3),new Vector3d(2,0.8,3),target,0.02));
        check(results,"No execution/jump attack IDs",java.util.Arrays.stream(ActionId.values()).noneMatch(id->id.name().contains("EXECUT")||id.name().contains("JUMP")));
        check(results,"Non-finite direction rejected",Collision.normalize2D(Double.NaN,1)[0]==0 && Collision.normalize2D(Double.POSITIVE_INFINITY,1)[1]==0);
        return results;
    }
}
