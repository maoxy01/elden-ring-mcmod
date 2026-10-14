package com.dsh.eldencombat.server.grace;

import com.dsh.eldencombat.EldenCombat;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 赐福系统的**正式启动路径**（取代原来的 {@code -D eldencombat.gracePrototype} 原型开关）。
 *
 * <p>顺序：先问 {@link GraceDependencies} 依赖在不在（那里没有任何 Waystones 类型），
 * 齐备才触碰 {@link GraceService}（含 Waystones/Balm 类型的适配类）。
 * 缺依赖时只写一行日志，赐福接口保持"不可用"，普通战斗功能完全不受影响。
 *
 * <p>{@code catch (Throwable)} 是刻意的：链路上的 {@code NoClassDefFoundError} /
 * {@code LinkageError} 会让整模组起不来，而赐福是可选子系统，必须能降级成"不可用"。
 *
 * <p>本类自身也不引用 Waystones 类型（{@code GraceService} 只在符号引用里出现，
 * JVM 到真正执行那一行才解析）。
 */
public final class GraceBootstrap {

    private static final AtomicBoolean ATTEMPTED = new AtomicBoolean();
    private static volatile boolean installed;

    private GraceBootstrap() {
    }

    /** 幂等：重复调用不会重复安装后端。返回是否可用。 */
    public static boolean install() {
        if (!ATTEMPTED.compareAndSet(false, true)) {
            return installed;
        }
        List<String> missing = GraceDependencies.missing();
        if (!missing.isEmpty()) {
            EldenCombat.LOGGER.info("GRACE_UNAVAILABLE: 缺少依赖 {}；赐福接口显示不可用，普通战斗功能不受影响", missing);
            return false;
        }
        try {
            GraceService.register();
            installed = true;
        } catch (Throwable failure) {
            EldenCombat.LOGGER.error("GRACE_ADAPTER_FAILED: 赐福适配器装载失败，赐福接口显示不可用", failure);
        }
        return installed;
    }

    /** 适配器是否已装载（赐福功能是否可用）。 */
    public static boolean installed() {
        return installed;
    }
}
