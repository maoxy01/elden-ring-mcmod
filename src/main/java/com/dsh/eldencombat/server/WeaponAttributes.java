package com.dsh.eldencombat.server;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.core.ActionSpec;
import com.dsh.eldencombat.core.HitWindow;
import com.dsh.eldencombat.core.WeaponProfile;
import com.dsh.eldencombat.core.WeaponProfiles;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * §5 把武器数值表落到**真实效果**上的那一层。
 *
 * <p>三件事：
 * <ol>
 *   <li><b>reach</b> → 原版属性 {@code Attributes.ENTITY_INTERACTION_RANGE}（基准 3.0）。
 *       用瞬时修饰符而不是物品静态属性，好处是数据包热改立刻生效，
 *       而且不用为了"每件武器一个属性包"去动 41 个物品的注册；</li>
 *   <li><b>攻速</b> → 原版属性 {@code Attributes.ATTACK_SPEED}，按重量公式给一个相对倍率
 *       （先摘掉自己的修饰符再读"原版 + 物品"的值，避免自己乘自己）；</li>
 *   <li><b>耐力消耗 / 削韧</b> → 由 {@link #applyProfile(ActionSpec, WeaponProfile)} 写进动作规格，
 *       调用方（ServerCombat）只负责把规格交给状态机。</li>
 * </ol>
 *
 * <p>只改主手武器；换手、换武器、登录、死亡都会重新应用（每 tick 做一次"当前主手 id 是否变化"的廉价比对）。
 */
public final class WeaponAttributes {

    private static final ResourceLocation REACH_ID =
            ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID, "weapon_reach");
    private static final ResourceLocation SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID, "weapon_weight_speed");

    /** 上一次应用过的武器 id：用来避免每 tick 反复写属性（会刷同步包） */
    private static final Map<UUID, String> APPLIED = new HashMap<>();

    private WeaponAttributes() {
    }

    /** 主手武器的数值档；没有对应条目（例如空手、原版物品）返回 null。 */
    public static WeaponProfile profile(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.isEmpty()) {
            return null;
        }
        return WeaponProfiles.of(BuiltInRegistries.ITEM.getKey(main.getItem()).toString());
    }

    /** 每 tick 调用：主手武器变化时刷新属性；退出时清理记录。 */
    public static void tick(ServerPlayer player) {
        WeaponProfile profile = profile(player);
        String current = profile == null ? "" : profile.id();
        String previous = APPLIED.get(player.getUUID());
        if (java.util.Objects.equals(previous, current)) {
            return;
        }
        APPLIED.put(player.getUUID(), current);
        if (profile == null) {
            clear(player);
        } else {
            apply(player, profile);
        }
    }

    public static void forget(UUID playerId) {
        APPLIED.remove(playerId);
    }

    /** 服务端停止：清空所有记录 */
    public static void forgetAll() {
        APPLIED.clear();
    }

    /** 清空本模组加的所有武器修饰符（换回无档位物品 / 玩家退出时）。 */
    public static void clear(ServerPlayer player) {
        removeModifier(player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE), REACH_ID);
        removeModifier(player.getAttribute(Attributes.ATTACK_SPEED), SPEED_ID);
    }

    private static void apply(ServerPlayer player, WeaponProfile profile) {
        AttributeInstance reach = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (reach != null) {
            if (Math.abs(profile.reachBonus()) < 1.0e-4) {
                reach.removeModifier(REACH_ID);
            } else {
                reach.addOrUpdateTransientModifier(new AttributeModifier(
                        REACH_ID, profile.reachBonus(), AttributeModifier.Operation.ADD_VALUE));
            }
        }
        AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (speed != null) {
            // 先摘掉自己的修饰符，读到的就是"原版 + 物品自带"的攻速，再按重量给相对倍率
            speed.removeModifier(SPEED_ID);
            double base = speed.getValue();
            double factor = profile.attackSpeedMultiplier() - 1.0;
            if (base > 0 && Math.abs(factor) > 1.0e-4) {
                speed.addOrUpdateTransientModifier(new AttributeModifier(
                        SPEED_ID, base * factor, AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    private static void removeModifier(AttributeInstance instance, ResourceLocation id) {
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    /**
     * 把武器档位写进动作规格：耐力消耗乘档位系数、命中窗口的削韧换成该武器的值。
     *
     * <p>实际计算在 {@link WeaponProfile#applyTo(ActionSpec)}（core，纯逻辑、可单测），
     * 这里只是"没有档位就原样返回"的空值保护。
     */
    public static ActionSpec applyProfile(ActionSpec spec, WeaponProfile profile) {
        return profile == null ? spec : profile.applyTo(spec);
    }

    /** 诊断文本（{@code /eldencombat status}）：当前主手武器的档位 */
    public static String describe(ServerPlayer player) {
        WeaponProfile profile = profile(player);
        return profile == null ? "无武器档位" : profile.describe();
    }
}
