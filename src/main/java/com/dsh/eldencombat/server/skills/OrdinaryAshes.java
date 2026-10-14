package com.dsh.eldencombat.server.skills;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.core.AshWeaponGroups;
import com.dsh.eldencombat.core.WeaponDefinitions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 把 {@link OrdinaryAshCatalog} 的实际支持列表注册进 {@link AshBindings}（common setup 时一次）。
 *
 * <p>三件事必须同时成立才会进目录：
 * <ol>
 *   <li>id 是合法的完整 namespaced id，且与 spec 的 id 完全一致（{@code implemented()} 就是这么判的）；</li>
 *   <li>武器白名单展开后**非空**，且每一个都在物品注册表与武器定义表里真实存在
 *       （挂在死 id 上的白名单等于"永远不可选"，属于装成功其实是坑）；</li>
 *   <li>运行时不是 {@code RemembranceRuntime}（不然就是拿追忆战技冒充普通战灰）。</li>
 * </ol>
 * 任何一条不成立就**不注册**并打错误日志——宁可目录里没有它，也不要出现"能选但永远失败"的条目。
 */
public final class OrdinaryAshes {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private OrdinaryAshes() {
    }

    public static void registerAll() {
        if (!REGISTERED.compareAndSet(false, true)) return;
        OrdinaryAshRuntime runtime = new OrdinaryAshRuntime();
        int registered = 0;
        for (OrdinaryAshCatalog.Entry entry : OrdinaryAshCatalog.entries()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.id());
            if (id == null || !id.toString().equals(entry.spec().id())) {
                EldenCombat.LOGGER.error("[ash] 跳过 {}：id 非法或与 spec.id 不一致（{}）", entry.id(), entry.spec().id());
                continue;
            }
            Set<ResourceLocation> items = itemsFor(entry.groupKey());
            if (items.isEmpty()) {
                EldenCombat.LOGGER.error("[ash] 跳过 {}：武器白名单展开为空，拒绝注册", entry.id());
                continue;
            }
            String groupKey = entry.groupKey();
            AshBindings.register(new AshBindings.Definition(id, entry.spec(), runtime, items,
                    stack -> compatible(groupKey, stack)));
            registered++;
            EldenCombat.LOGGER.info("ASH_CATALOG_ENTRY: {} 「{}」 武器分组={} 武器数={} FP={} 段数={}",
                    entry.id(), entry.label(), groupKey, items.size(), entry.spec().fpCost(), entry.spec().maxStages());
        }
        EldenCombat.LOGGER.info("ASH_CATALOG_REGISTERED: 实际支持普通战灰 {} 件：{}", registered,
                OrdinaryAshCatalog.entries().stream().map(OrdinaryAshCatalog.Entry::id).collect(Collectors.joining(", ")));
    }

    /** 分组 → 真实存在的武器物品 id（剔除未注册的物品与没有武器定义的路径）。 */
    private static Set<ResourceLocation> itemsFor(String groupKey) {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        for (String raw : AshWeaponGroups.itemIds(groupKey, EldenCombat.MODID)) {
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id == null || BuiltInRegistries.ITEM.getOptional(id).isEmpty()) continue;
            if (WeaponDefinitions.of(id.getPath()) == null) continue;
            result.add(id);
        }
        return result;
    }

    /**
     * 兼容性判定。物品在注册时已按分组筛过，这里再按"当前拿着的这把武器的定义"核一遍：
     * 武器定义可以被数据包改（{@code WeaponDefinitions} 支持覆盖），不能只信注册时的那份快照。
     */
    private static boolean compatible(String groupKey, ItemStack stack) {
        var definition = com.dsh.eldencombat.item.WeaponStacks.definition(stack);
        return definition != null && AshWeaponGroups.contains(groupKey, definition.id());
    }
}
