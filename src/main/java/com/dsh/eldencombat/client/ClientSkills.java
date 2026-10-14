package com.dsh.eldencombat.client;

import com.dsh.eldencombat.network.FpSyncPayload;
import com.dsh.eldencombat.server.skills.PlayerFpState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 FP 镜像：只用于 HUD 与表现，不参与任何结算。
 *
 * <p>刻意**不加** {@code @EventBusSubscriber}：本类的入口是 {@code ModPayloads} 注册的
 * payload 处理器，不是事件订阅者。给一个没有 {@code @SubscribeEvent} 方法的类加订阅者注解，
 * NeoForge 会在模组构造期直接抛 IllegalArgumentException 导致整个模组加载失败。
 */
public final class ClientSkills {

    private static float fp = PlayerFpState.DEFAULT_MAX_FP;
    private static float maxFp = PlayerFpState.DEFAULT_MAX_FP;
    private static String lastSkillId = "";
    private static String activeSkillId = "";
    private static int activeCastId;
    private static boolean casting;
    private static int stageIndex;

    private ClientSkills() {
    }

    public static void handleFp(FpSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            fp = payload.fp();
            maxFp = Math.max(1.0f, payload.maxFp());
            lastSkillId = payload.lastSkillId();
            activeSkillId = payload.activeSkillId();
            activeCastId = payload.activeCastId();
            casting = payload.casting();
            stageIndex = payload.stageIndex();
        });
    }

    public static float fp() {
        return fp;
    }

    public static float maxFp() {
        return maxFp;
    }

    public static float ratio() {
        return maxFp <= 0 ? 0f : Math.max(0f, Math.min(1f, fp / maxFp));
    }

    public static String lastSkillId() {
        return lastSkillId;
    }

    public static String activeSkillId() {
        return activeSkillId;
    }

    public static int activeCastId() {
        return activeCastId;
    }

    public static boolean casting() {
        return casting;
    }

    public static int stageIndex() { return stageIndex; }

    public static void resetLocal() {
        fp = maxFp;
        lastSkillId = "";
        activeSkillId = "";
        activeCastId = 0;
        casting = false;
        stageIndex = 0;
    }
}
