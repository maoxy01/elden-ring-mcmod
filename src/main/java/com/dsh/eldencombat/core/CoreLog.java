package com.dsh.eldencombat.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** core 包（纯规则层）使用的日志入口，避免核心逻辑直接依赖模组主类。 */
public final class CoreLog {
    public static final Logger LOGGER = LoggerFactory.getLogger("EldenCombat/Core");

    private CoreLog() {
    }
}
