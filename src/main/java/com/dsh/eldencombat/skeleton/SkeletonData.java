package com.dsh.eldencombat.skeleton;

import com.dsh.eldencombat.EldenCombat;
import org.jetbrains.annotations.Nullable;

/**
 * 模型 / 动作数据的全局入口：客户端渲染与服务端骨骼采样共用同一份 JSON，
 * 加载失败时只记录一次错误，服务端 AI 退化为固定时长，不让游戏崩。
 */
public final class SkeletonData {

    private static SkeletonAssets assets;
    private static boolean loadFailed;

    private SkeletonData() {
    }

    @Nullable
    public static SkeletonAssets assets() {
        if (assets == null && !loadFailed) {
            try {
                assets = SkeletonAssets.load();
                EldenCombat.LOGGER.info("[EldenCombat] 已载入骨骼资产: {} 骨骼 / {} 方块 / {} 个动作",
                        assets.margit().bones.size(), assets.margit().cubes.size(), assets.clips().size());
            } catch (Exception exception) {
                loadFailed = true;
                EldenCombat.LOGGER.error("[EldenCombat] 载入骨骼资产失败，渲染与判定退化为回退值", exception);
            }
        }
        return assets;
    }

    /** 动作总长（缺失资产时返回回退值） */
    public static int clipLength(String clipId, int fallback) {
        SkeletonAssets loaded = assets();
        if (loaded == null || !loaded.hasClip(clipId)) {
            return fallback;
        }
        return loaded.clip(clipId).lengthTicks();
    }

    public static boolean isHitWindowOpen(String clipId, int tickInAction, int fallbackStart, int fallbackEnd) {
        SkeletonAssets loaded = assets();
        if (loaded == null || !loaded.hasClip(clipId)) {
            return tickInAction >= fallbackStart && tickInAction < fallbackEnd;
        }
        AnimationClip clip = loaded.clip(clipId);
        int start = clip.hitStart();
        int end = clip.hitEnd();
        if (start < 0 || end < 0) {
            return tickInAction >= fallbackStart && tickInAction < fallbackEnd;
        }
        return tickInAction >= start && tickInAction < end;
    }
}
