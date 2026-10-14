package com.dsh.eldencombat.server;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.core.ConsumableSpec;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 药物加成（消耗品）的服务端实现。
 *
 * <p>与药瓶（{@code HealingFlaskItem}，绑定在 {@code DRINK} 意图上）不同，
 * 这里的消耗品走**原版右键使用**：喝下后按数据文件给药水效果与治疗量，
 * 效果、等级、时长都写在数据包里，改数值不用重编译。
 *
 * <p>物品名必须与数据文件名一致：{@code eldencombat:flame_grant_me_strength}
 * 对应 {@code data/eldencombat/consumables/flame_grant_me_strength.json}。
 */
public final class Consumables {

    private static volatile Map<String, ConsumableSpec> specs = Map.of();

    private Consumables() {
    }

    public static Map<String, ConsumableSpec> all() {
        return specs;
    }

    public static ConsumableSpec spec(String id) {
        if (id == null || id.isEmpty()) {
            return ConsumableSpec.NONE;
        }
        return specs.getOrDefault(id, ConsumableSpec.NONE);
    }

    public static ConsumableSpec specFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return ConsumableSpec.NONE;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return EldenCombat.MODID.equals(key.getNamespace())
                ? spec(key.getPath()) : ConsumableSpec.NONE;
    }

    /** 喝下：治疗 + 逐条施加药水效果。未知效果只记日志，不影响其余效果。 */
    public static void consume(ServerPlayer player, ItemStack stack) {
        ConsumableSpec spec = specFor(stack);
        if (spec.isNone()) {
            return;
        }
        float heal = spec.heal();
        if (heal > 0) {
            // 护符的治疗加成也作用在药物上
            player.heal(heal + Talismans.healBonus(player));
        }
        for (ConsumableSpec.Effect effect : spec.effects()) {
            ResourceLocation id = ResourceLocation.tryParse(effect.effectId());
            Holder<MobEffect> holder = id == null ? null
                    : BuiltInRegistries.MOB_EFFECT.getHolder(id).orElse(null);
            if (holder == null) {
                EldenCombat.LOGGER.warn("[consumables] {} 的效果不存在：{}", spec.id(), effect.effectId());
                continue;
            }
            player.addEffect(new MobEffectInstance(holder, effect.durationTicks(), effect.amplifier()));
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.7f, 1.0f);
        EldenCombat.LOGGER.debug("[consumables] {} 使用了 {}", player.getGameProfile().getName(), spec.describe());
    }

    /** {@code data/<ns>/consumables/*.json}；{@code /reload} 时整体替换。 */
    public static final class Listener extends SimpleJsonResourceReloadListener {

        public Listener() {
            super(new Gson(), "consumables");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager,
                ProfilerFiller profiler) {
            Map<String, ConsumableSpec> loaded = new HashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
                try {
                    loaded.put(entry.getKey().getPath(),
                            ConsumableSpec.parse(entry.getKey().getPath(), entry.getValue().getAsJsonObject()));
                } catch (RuntimeException error) {
                    EldenCombat.LOGGER.error("[consumables] 解析失败 {}", entry.getKey(), error);
                }
            }
            specs = Map.copyOf(loaded);
            EldenCombat.LOGGER.info("[consumables] 药物数据载入 {} 种", specs.size());
        }
    }
}
