package com.dsh.eldencombat;

import com.dsh.eldencombat.testing.SelfTest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规则与数据验收：与游戏内 /eldencombat selftest 共用同一份检查清单。
 */
class SelfTestTest {

    @Test
    void allChecksPass() {
        List<String> results = SelfTest.runAll();
        List<String> failures = results.stream().filter(line -> line.startsWith("FAIL")).toList();
        assertTrue(results.size() > 40, "自检项过少，检查清单可能被裁剪：" + results.size());
        assertEquals(List.of(), failures, "存在未通过的检查项");
    }
}
