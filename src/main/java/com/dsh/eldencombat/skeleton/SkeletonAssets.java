package com.dsh.eldencombat.skeleton;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 模型 / 动作数据加载器（客户端渲染与服务端骨骼采样共用同一份数据）。 */
public final class SkeletonAssets {

    private final ModelData margit;
    private final ModelData spectralDagger;
    private final ModelData spectralHammer;
    private final Map<String, AnimationClip> clips;

    private SkeletonAssets(ModelData margit, ModelData spectralDagger, ModelData spectralHammer, Map<String, AnimationClip> clips) {
        this.margit = margit;
        this.spectralDagger = spectralDagger;
        this.spectralHammer = spectralHammer;
        this.clips = Map.copyOf(clips);
    }

    public ModelData margit() {
        return margit;
    }

    public ModelData spectralDagger() {
        return spectralDagger;
    }

    public ModelData spectralHammer() {
        return spectralHammer;
    }

    public ModelData accessory(String item) {
        return switch (item) {
            case "spectral_dagger" -> spectralDagger;
            case "spectral_hammer" -> spectralHammer;
            default -> null;
        };
    }

    public AnimationClip clip(String id) {
        AnimationClip clip = clips.get(id);
        if (clip == null) {
            throw new IllegalArgumentException("缺少动作: " + id);
        }
        return clip;
    }

    public boolean hasClip(String id) {
        return clips.containsKey(id);
    }

    public Map<String, AnimationClip> clips() {
        return clips;
    }

    /** 资源包路径（assets/eldencombat/...） */
    public static final String MODEL_MARGIT = "/assets/eldencombat/skeletons/entity/margit.model.json";
    public static final String MODEL_DAGGER = "/assets/eldencombat/skeletons/entity/spectral_dagger.model.json";
    public static final String MODEL_HAMMER = "/assets/eldencombat/skeletons/entity/spectral_hammer.model.json";
    public static final String ANIMATIONS = "/assets/eldencombat/animations/margit.animations.json";

    public static SkeletonAssets load() throws IOException {
        try (InputStream margitStream = open(MODEL_MARGIT);
             InputStream daggerStream = open(MODEL_DAGGER);
             InputStream hammerStream = open(MODEL_HAMMER);
             InputStream animationStream = open(ANIMATIONS)) {
            return new SkeletonAssets(
                    parseModel(margitStream),
                    parseModel(daggerStream),
                    parseModel(hammerStream),
                    parseAnimations(animationStream));
        }
    }

    /** 从磁盘目录加载（游戏内测试与离线校验用） */
    public static SkeletonAssets loadFrom(Path margitModel, Path daggerModel, Path hammerModel, Path animations) throws IOException {
        try (Reader margitReader = Files.newBufferedReader(margitModel, StandardCharsets.UTF_8);
             Reader daggerReader = Files.newBufferedReader(daggerModel, StandardCharsets.UTF_8);
             Reader hammerReader = Files.newBufferedReader(hammerModel, StandardCharsets.UTF_8);
             Reader animationReader = Files.newBufferedReader(animations, StandardCharsets.UTF_8)) {
            return new SkeletonAssets(
                    ModelData.parse(JsonParser.parseReader(margitReader).getAsJsonObject()),
                    ModelData.parse(JsonParser.parseReader(daggerReader).getAsJsonObject()),
                    ModelData.parse(JsonParser.parseReader(hammerReader).getAsJsonObject()),
                    parseAnimations(animationReader));
        }
    }

    private static InputStream open(String resource) throws IOException {
        InputStream stream = SkeletonAssets.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new IOException("资源缺失: " + resource + "（确认 art/margit 资产已随构建复制进 assets）");
        }
        return stream;
    }

    private static ModelData parseModel(InputStream stream) {
        return ModelData.parse(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
    }

    public static Map<String, AnimationClip> parseAnimations(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        JsonArray array = root.getAsJsonArray("clips");
        Map<String, AnimationClip> clips = new LinkedHashMap<>();
        List<AnimationClip> ordered = new ArrayList<>();
        for (JsonElement element : array) {
            AnimationClip clip = AnimationClip.parse(element.getAsJsonObject());
            clips.put(clip.id(), clip);
            ordered.add(clip);
        }
        return clips;
    }

    public static Map<String, AnimationClip> parseAnimations(InputStream stream) {
        return parseAnimations(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
