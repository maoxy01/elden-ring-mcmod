package com.dsh.eldencombat.registry;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.core.WeaponState;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 数据组件注册（§2.7）。
 *
 * <p>用 Data Component 而不是手写 NBT：组件带 codec 与网络同步，存档、配方预览、
 * 客户端 tooltip 都走同一条路径，不需要自己处理"客户端看不到等级"这类问题。
 *
 * <p>武器物品**不设置默认组件值**：读取时统一用 {@code getOrDefault(WEAPON_STATE, 定义.fresh())}。
 * 这样避免"物品注册早于组件注册"的时序坑，而且未锻造过的武器不写多余数据。
 */
public final class ModDataComponents {

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(EldenCombat.MODID);

    /** Separate optional component preserves the old weapon_state schema verbatim. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.resources.ResourceLocation>> ASH_BINDING =
            DATA_COMPONENTS.registerComponentType("ash_binding", builder -> builder
                    .persistent(net.minecraft.resources.ResourceLocation.CODEC)
                    .networkSynchronized(net.minecraft.resources.ResourceLocation.STREAM_CODEC));

    /** 武器成长状态：强化等级 / 质变 / 战灰锁定 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WeaponState>> WEAPON_STATE =
            DATA_COMPONENTS.registerComponentType("weapon_state", builder -> builder
                    .persistent(WeaponStateCodec.CODEC)
                    .networkSynchronized(WeaponStateCodec.STREAM_CODEC));

    private ModDataComponents() {
    }
}
