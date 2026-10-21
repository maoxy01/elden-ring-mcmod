import com.dsh.eldencombat.core.SkillPoseData;
import com.dsh.eldencombat.server.skills.RemembranceSkillSpec;
import com.dsh.eldencombat.server.skills.SkillCastState;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Runs the project's actual Java parser, sampler and cast state; writes only JSON to stdout. */
public class PoseOracle {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        Gson gson = new Gson();
        JsonObject output = new JsonObject();
        JsonArray samples = new JsonArray(), flows = new JsonArray();
        JsonArray specs = JsonParser.parseString(Files.readString(root.resolve("src/main/resources/data/eldencombat/dsh_skills/remembrance_parameters.json"))).getAsJsonArray();
        for (JsonElement value : specs) {
            RemembranceSkillSpec spec = gson.fromJson(value, RemembranceSkillSpec.class);
            var skill = SkillPoseData.parse(JsonParser.parseString(Files.readString(root.resolve("src/main/resources/assets/eldencombat/skill_poses/"+spec.id()+".json"))).getAsJsonObject());
            for (int stage=0; stage<5; stage++) for (int index=0; index<4; index++) for (double tick : new double[]{-1,0,.3,2,5,10,99}) {
                JsonObject row = new JsonObject();
                row.addProperty("id",spec.id()); row.addProperty("stage",SkillPoseData.stageName(stage)); row.addProperty("index",index); row.addProperty("tick",tick);
                JsonObject parts = new JsonObject();
                for (String joint : SkillPoseData.JOINTS) {
                    var track = skill.forStage(stage,index).parts().get(joint);
                    float[] sampled = track==null?new float[3]:track.sample(Math.max(0,tick),new float[3]);
                    parts.add(joint,gson.toJsonTree(sampled));
                }
                row.add("parts",parts); samples.add(row);
            }
            for (boolean followup : new boolean[]{false,true}) {
                var cast=new SkillCastState(); cast.begin(spec.id(),0);
                for (int tick=0;tick<1000;tick++) {
                    int elapsed=cast.stageTicks(tick);
                    switch(cast.stage()) {
                        case WINDUP -> {if(elapsed>=spec.windupForStage(cast.stageIndex()))cast.enterStage(SkillCastState.Stage.ACTIVE,tick);}
                        case ACTIVE -> {if(elapsed>=spec.activeForStage(cast.stageIndex())) {if(spec.hasFollowup()&&cast.stageIndex()<spec.maxStages())cast.openFollowupWindow(tick,spec.followupWindowTicks());else cast.enterRecovery(tick);}}
                        case FOLLOWUP_WINDOW -> {if(cast.followupExpired(tick))cast.enterRecovery(tick);}
                        case RECOVERY -> {if(elapsed>=spec.recoveryTicks())cast.finish();}
                    }
                    if(followup&&cast.followupOpen(tick))cast.queueFollowup(tick,spec.maxStages());
                    if(!cast.isCasting())break;
                    JsonObject row=new JsonObject(); row.addProperty("id",spec.id());row.addProperty("followup",followup);row.addProperty("tick",tick);row.addProperty("stage",cast.stage().name());row.addProperty("index",cast.stageIndex());row.addProperty("elapsed",cast.stageTicks(tick));flows.add(row);
                }
            }
        }
        output.add("samples",samples);output.add("flows",flows);System.out.print(gson.toJson(output));
    }
}
