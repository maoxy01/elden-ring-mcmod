package com.dsh.eldencombat.client;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.core.SkillPoseData;
import com.dsh.eldencombat.network.SkillCastPayload;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 战技释放动作（客户端表现层）。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>只驱动原版六个关节</b>：{@code head / body / rightArm / leftArm / rightLeg / leftLeg}，
 *       不给玩家模型加任何自定义骨骼或关节；普攻仍然是原版挥剑动作；</li>
 *   <li><b>只覆盖列出来的关节</b>：姿态数据里没写的关节保持原版动画（走路、潜行都照旧），
 *       所以动作是"叠加"而不是"全盘接管"；</li>
 *   <li><b>服务端只发状态</b>：{@code {skillId, stage, stageIndex, stageTicks}}，客户端自己按 tick
 *       插值，不依赖每 tick 同步；</li>
 *   <li><b>数据驱动</b>：关键帧在 {@code assets/eldencombat/skill_poses/<skillId>.json}，
 *       解析/插值/阶段回退都在 {@link SkillPoseData}（纯逻辑，有单测），这里只做加载与施加。</li>
 * </ul>
 *
 * <p>注入点是 {@code PlayerModelPoseMixin}（{@code PlayerModel.setupAnim} 的 TAIL）。
 */
public final class SkillPoseAnimator {

    /** 资源目录：{@code assets/eldencombat/skill_poses/} */
    public static final String DIRECTORY = "skill_poses";

    private record Active(String skillId, int castId, int stage, int stageIndex, double startTick) {
    }

    private static final Map<Integer, Active> CASTING = new ConcurrentHashMap<>();
    private static volatile SkillPoseData.Data data = SkillPoseData.Data.EMPTY;
    private static final float[] SCRATCH = new float[3];

    private SkillPoseAnimator() {
    }

    // ------------------------------------------------------------------
    // 资源加载
    // ------------------------------------------------------------------

    public static SkillPoseData.Data prepare(ResourceManager manager) {
        Map<String, SkillPoseData.Skill> skills = new HashMap<>();
        Map<ResourceLocation, Resource> found = manager.listResources(DIRECTORY,
                path -> path.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            String path = entry.getKey().getPath();
            String id = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
            try (var reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
                SkillPoseData.Skill skill = SkillPoseData.parse(JsonParser.parseReader(reader).getAsJsonObject());
                if (skill != null) {
                    skills.put(id, skill);
                }
            } catch (IOException | RuntimeException error) {
                EldenCombat.LOGGER.error("[skills] 战技姿态解析失败 {}", entry.getKey(), error);
            }
        }
        return new SkillPoseData.Data(Map.copyOf(skills));
    }

    public static void apply(SkillPoseData.Data prepared) {
        data = prepared;
        CASTING.clear();
        EldenCombat.LOGGER.info("[skills] 战技姿态就绪：{} 个", prepared.skills().size());
    }

    // ------------------------------------------------------------------
    // 网络
    // ------------------------------------------------------------------

    public static void handle(SkillCastPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.skillId().isEmpty() || payload.stage() <= 0) {
                CASTING.remove(payload.entityId());
                return;
            }
            var level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            Entity entity = level.getEntity(payload.entityId());
            if (!(entity instanceof LivingEntity living)) {
                CASTING.remove(payload.entityId());
                return;
            }
            Active previous = CASTING.get(payload.entityId());
            if (previous != null && previous.castId() == payload.castId()
                    && previous.stage() == payload.stage() && previous.stageIndex() == payload.stageIndex()) {
                // 周期性心跳只用来纠偏，不重置起算点，否则动作每 5 tick 抖一次
                return;
            }
            if (previous != null && payload.castId() != 0 && previous.castId() > payload.castId()) {
                // 迟到的旧 cast 包（乱序 / 上一段残留）不能把正在播的新动作顶掉
                return;
            }
            CASTING.put(payload.entityId(), new Active(payload.skillId(), payload.castId(), payload.stage(),
                    payload.stageIndex(), living.tickCount - payload.stageTicks()));
        });
    }

    /**
     * 断开连接 / 换维度 / 重连时清空动作缓存。
     *
     * <p>实体 id 会被复用：不清的话，上一局某个实体的施放姿势会套到新会话里 id 相同的新实体身上，
     * 而且既不会自己消失（服务端不会再发那一局的结束包），也没有任何界面提示它已经过期。
     */
    public static void reset() {
        CASTING.clear();
    }

    // ------------------------------------------------------------------
    // 每帧应用
    // ------------------------------------------------------------------

    /**
     * 把当前施放阶段的姿势写到原版玩家模型上。
     *
     * @return 是否有任何关节被改动（调用方据此重新同步袖子/裤腿/帽子/外套）
     */
    public static boolean applyPose(HumanoidModel<?> model, LivingEntity entity, float ageInTicks) {
        Active active = CASTING.get(entity.getId());
        if (active == null) {
            return false;
        }
        SkillPoseData.Skill skill = data.skills().get(active.skillId());
        if (skill == null) {
            return false;
        }
        SkillPoseData.Stage stage = skill.forStage(active.stage(), active.stageIndex());
        if (stage.parts().isEmpty()) {
            return false;
        }
        double elapsed = ageInTicks - active.startTick();
        if (elapsed < 0) {
            elapsed = 0;
        }
        boolean touched = false;
        for (Map.Entry<String, SkillPoseData.Track> entry : stage.parts().entrySet()) {
            ModelPart part = part(model, entry.getKey());
            if (part == null) {
                continue;
            }
            float[] rotation = entry.getValue().sample(elapsed, SCRATCH);
            part.xRot = rotation[0];
            part.yRot = rotation[1];
            part.zRot = rotation[2];
            touched = true;
        }
        return touched;
    }

    /** 只认原版六个关节；名字写错一律当作"没有这个关节"，不抛异常。 */
    private static ModelPart part(HumanoidModel<?> model, String name) {
        return switch (name) {
            case "head" -> model.head;
            case "body" -> model.body;
            case "rightArm" -> model.rightArm;
            case "leftArm" -> model.leftArm;
            case "rightLeg" -> model.rightLeg;
            case "leftLeg" -> model.leftLeg;
            default -> null;
        };
    }

    /** 本地玩家当前是否在施放战技（HUD / 调试用）。 */
    public static boolean localCasting() {
        var player = Minecraft.getInstance().player;
        return player != null && CASTING.containsKey(player.getId());
    }

    public static boolean rollCancelable() {
        var player = Minecraft.getInstance().player;
        Active active = player == null ? null : CASTING.get(player.getId());
        if (active == null) return true;
        var spec = com.dsh.eldencombat.server.skills.SkillDriver.spec(active.skillId());
        if (active.stage() != 4 || spec == null) return false;
        int recovery = spec.recoveryTicks();
        int tail = Math.min(2, Math.max(0, recovery - 1));
        double elapsed = player.tickCount - active.startTick();
        return tail > 0 && elapsed >= recovery - tail && elapsed < recovery;
    }
}
