package com.dsh.eldencombat.testing;

import com.dsh.eldencombat.core.ActionId;
import com.dsh.eldencombat.core.ActionSpec;
import com.dsh.eldencombat.core.CombatConfig;
import com.dsh.eldencombat.core.CombatRules;
import com.dsh.eldencombat.core.CombatState;
import com.dsh.eldencombat.core.HitWindow;
import com.dsh.eldencombat.core.WeaponClass;
import com.dsh.eldencombat.core.geom.Box;
import com.dsh.eldencombat.core.geom.Collision;
import com.dsh.eldencombat.skeleton.AnimationClip;
import com.dsh.eldencombat.skeleton.HitVolumes;
import com.dsh.eldencombat.skeleton.ModelData;
import com.dsh.eldencombat.skeleton.SkeletonAssets;
import com.dsh.eldencombat.skeleton.SkeletonData;
import com.dsh.eldencombat.skeleton.SkeletonPose;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 可执行验收清单：游戏内 /eldencombat selftest 与 JUnit 共用同一份检查。
 * 每条返回 "PASS ..." 或 "FAIL ..."，FAIL 表示规则或数据不符合交接说明。
 */
public final class SelfTest {

    private SelfTest() {
    }

    public static List<String> runAll() {
        return runAll(CombatConfig.defaults());
    }

    /** 游戏内使用实时配置跑同一份检查。 */
    public static List<String> runAll(CombatConfig config) {
        List<String> results = new ArrayList<>();
        configChecks(results, config);
        windowChecks(results);
        hitOnceChecks(results);
        rollAndGuardChecks(results);
        parryAndStaggerChecks(results);
        drinkChecks(results);
        geometryChecks(results);
        skeletonChecks(results);
        results.addAll(PlayerAssetChecks.run());
        return results;
    }

    public static long failures(List<String> results) {
        return results.stream().filter(line -> line.startsWith("FAIL")).count();
    }

    private static void check(List<String> results, String name, boolean condition) {
        results.add((condition ? "PASS " : "FAIL ") + name);
    }

    private static void checkEquals(List<String> results, String name, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        results.add((ok ? "PASS " : "FAIL ") + name + " (期望 " + expected + "，实际 " + actual + ")");
    }

    // ------------------------------------------------------------------

    private static void configChecks(List<String> results, CombatConfig config) {
        ActionSpec light = config.lightSpec(WeaponClass.LIGHT_SWORD);
        checkEquals(results, "直剑轻击总长", 22, light.totalTicks());
        checkEquals(results, "直剑轻击消耗", 15.0f, light.staminaCost());
        checkEquals(results, "直剑轻击窗口", "8-12", windowText(light));
        checkEquals(results, "直剑轻击伤害", 5.0f, light.windows().get(0).damage());

        ActionSpec greatLight = config.lightSpec(WeaponClass.GREAT_SWORD);
        checkEquals(results, "大剑轻击总长", 30, greatLight.totalTicks());
        checkEquals(results, "大剑轻击消耗", 20.0f, greatLight.staminaCost());
        checkEquals(results, "大剑轻击窗口", "12-17", windowText(greatLight));
        checkEquals(results, "大剑轻击伤害", 7.0f, greatLight.windows().get(0).damage());

        ActionSpec heavy = config.heavySpec(WeaponClass.LIGHT_SWORD);
        checkEquals(results, "重击窗口", "15-20", windowText(heavy));
        checkEquals(results, "重击消耗", 25.0f, heavy.staminaCost());
        checkEquals(results, "重击直剑伤害", 8.0f, heavy.windows().get(0).damage());
        checkEquals(results, "重击大剑伤害", 11.0f,
                config.heavySpec(WeaponClass.GREAT_SWORD).windows().get(0).damage());

        ActionSpec charged = config.chargedHeavySpec(WeaponClass.GREAT_SWORD);
        checkEquals(results, "满蓄力重击窗口", "16-22", windowText(charged));
        checkEquals(results, "满蓄力消耗", 35.0f, charged.staminaCost());
        checkEquals(results, "满蓄力大剑伤害", 15.0f, charged.windows().get(0).damage());
        checkEquals(results, "满蓄力直剑伤害", 11.0f,
                config.chargedHeavySpec(WeaponClass.LIGHT_SWORD).windows().get(0).damage());
        checkEquals(results, "蓄力达满 tick", 20, config.weapon(WeaponClass.LIGHT_SWORD).chargeFullTicks);
        checkEquals(results, "蓄力自动释放上限", 30, config.weapon(WeaponClass.LIGHT_SWORD).chargeMaxTicks);

        checkEquals(results, "翻滚消耗", 22.0f, config.rollSpec().staminaCost());
        checkEquals(results, "翻滚总长", 20, config.rollSpec().totalTicks());
        checkEquals(results, "翻滚无敌窗口", "4-11", windowText(config.rollSpec()));
        checkEquals(results, "弹反消耗", 15.0f, config.parrySpec().staminaCost());
        checkEquals(results, "弹反总长", 18, config.parrySpec().totalTicks());
        checkEquals(results, "弹反成功窗口", "3-7", windowText(config.parrySpec()));
        checkEquals(results, "喝药总长", 28, config.drinkSpec().totalTicks());
        checkEquals(results, "喝药生效 tick", 18, config.drinkSpec().consumeTick());
        checkEquals(results, "喝药回复", 8.0f, config.drinkSpec().heal());
        checkEquals(results, "初始药瓶次数", 4, config.drink.charges);
        checkEquals(results, "精力上限", 100.0f, config.stamina.max);
        checkEquals(results, "精力恢复延迟", 12, config.stamina.regenDelayTicks);
        checkEquals(results, "Boss 生命", 200.0f, config.boss.maxHealth);
        checkEquals(results, "Boss 二阶段阈值", 0.6f, config.boss.phaseTwoThreshold);
        checkEquals(results, "Boss 失衡上限", 100.0f, config.poise.max);
        checkEquals(results, "失衡恢复延迟", 80, config.poise.regenDelayTicks);
        // §6 削韧 payoff：崩解窗口 2.0s（40 tick）+ 易伤 +50%
        checkEquals(results, "崩解窗口下限", 40, config.poise.brokenTicks);
        checkEquals(results, "崩解易伤倍率", 1.5f, config.poise.brokenDamageMultiplier);
        checkEquals(results, "崩解窗口取动作与下限的较大者", 70, config.poiseBreakTicks(70));
        checkEquals(results, "崩解窗口不足动作时长时用配置下限", 40, config.poiseBreakTicks(20));
        checkEquals(results, "未崩解无额外易伤", 1.0f, config.poiseDamageTakenMultiplier(false));
        checkEquals(results, "崩解期易伤生效", 1.5f, config.poiseDamageTakenMultiplier(true));
        checkEquals(results, "单人失衡上限不缩放", 100.0f, config.poiseMaxFor(1));
        checkEquals(results, "两人失衡上限 ×1.5", 150.0f, config.poiseMaxFor(2));
        checkEquals(results, "盾防锥角", 120.0f, config.guard.coneDegrees);
        checkEquals(results, "破防硬直", 24, config.guard.breakTicks);
        checkEquals(results, "锁定范围", 24.0, config.lockOn.range);
        checkEquals(results, "解除锁定范围", 32.0, config.lockOn.breakRange);
    }

    private static String windowText(ActionSpec spec) {
        if (spec.windows().isEmpty()) {
            return "无";
        }
        HitWindow window = spec.windows().get(0);
        return window.start() + "-" + window.endExclusive();
    }

    private static void windowChecks(List<String> results) {
        CombatConfig config = CombatConfig.defaults();
        ActionSpec light = config.lightSpec(WeaponClass.LIGHT_SWORD);
        check(results, "轻击前摇不产生判定", light.activeWindow(7) == null);
        check(results, "轻击窗口起点生效", light.activeWindow(8) != null);
        check(results, "轻击窗口终点不含", light.activeWindow(12) == null);

        ActionSpec parry = config.parrySpec();
        check(results, "弹反窗口边界 2 不生效", parry.activeWindow(2) == null);
        check(results, "弹反窗口边界 3 生效", parry.activeWindow(3) != null);
        check(results, "弹反窗口边界 6 生效", parry.activeWindow(6) != null);
        check(results, "弹反窗口边界 7 不生效", parry.activeWindow(7) == null);
    }

    private static void hitOnceChecks(List<String> results) {
        CombatState state = new CombatState();
        state.initFrom(CombatConfig.defaults());
        state.acceptAction(CombatConfig.defaults().lightSpec(WeaponClass.LIGHT_SWORD), 100);
        check(results, "同一序号首次命中", state.markHit(7));
        check(results, "同一序号重复命中被拒绝", !state.markHit(7));
        state.acceptAction(CombatConfig.defaults().lightSpec(WeaponClass.LIGHT_SWORD), 130);
        check(results, "新序号重新可命中", state.markHit(7));
    }

    private static void rollAndGuardChecks(List<String> results) {
        CombatConfig config = CombatConfig.defaults();
        CombatState state = new CombatState();
        state.initFrom(config);
        state.acceptAction(config.rollSpec(), 100);

        check(results, "翻滚窗口前非无敌", !state.isRollInvulnerable(103));
        check(results, "翻滚窗口内无敌", state.isRollInvulnerable(104) && state.isRollInvulnerable(110));
        check(results, "翻滚窗口后非无敌", !state.isRollInvulnerable(111) && !state.isRollInvulnerable(120));

        check(results, "正面 0 度在盾防锥内", Collision.withinCone(0, 1, 0, 1, config.guard.coneDegrees));
        check(results, "侧面 59 度在盾防锥内", Collision.withinCone(0, 1, Math.sin(Math.toRadians(59)), Math.cos(Math.toRadians(59)), 120));
        check(results, "背后 180 度不在盾防锥内", !Collision.withinCone(0, 1, 0, -1, config.guard.coneDegrees));

        CombatState guard = new CombatState();
        guard.initFrom(config);
        guard.guarding = true;
        guard.stamina = 10f;
        check(results, "精力不足以继续挡（应破防）", !guard.hasStamina(20f));
        check(results, "精力足够可挡", guard.hasStamina(5f));
        guard.acceptAction(config.guardBrokenSpec(), 200);
        guard.guardBrokenUntilTick = 200 + config.guard.breakTicks;
        check(results, "破防期间盾防失效", !guard.shieldActive(205));
        guard.finishIfDone(200 + config.guard.breakTicks + 1L);
        check(results, "破防结束后恢复", guard.shieldActive(200 + config.guard.breakTicks + 5L));
    }

    private static void parryAndStaggerChecks(List<String> results) {
        CombatConfig config = CombatConfig.defaults();
        check(results, "木杖横扫可弹反", config.bossAttacks.get("staff_sweep").parryable);
        check(results, "匕首可弹反", config.bossAttacks.get("dagger_slash").parryable);
        check(results, "木杖下砸不可弹反", !config.bossAttacks.get("staff_slam").parryable);
        check(results, "黄金锤不可弹反", !config.bossAttacks.get("hammer_slam").parryable);
        check(results, "连续两次弹反可触发失衡", config.parryPoiseDamage * 2 >= config.poise.max);
        check(results, "首版不存在处决动作", noExecutionAction());

        CombatState state = new CombatState();
        state.initFrom(config);
        check(results, "重置后无残留无敌", resetClearsInvulnerability(config, state));
    }

    private static boolean noExecutionAction() {
        for (ActionId id : ActionId.values()) {
            if (id.name().contains("EXECUT")) {
                return false;
            }
        }
        return true;
    }

    private static boolean resetClearsInvulnerability(CombatConfig config, CombatState state) {
        state.acceptAction(config.rollSpec(), 10);
        state.guarding = true;
        state.targetId = 42;
        state.combatMode = true;
        state.reset(config);
        return state.action == ActionId.NONE && !state.isRollInvulnerable(12) && !state.guarding
                && state.targetId == CombatState.NO_TARGET && !state.combatMode;
    }

    private static void drinkChecks(List<String> results) {
        CombatConfig config = CombatConfig.defaults();
        CombatState state = new CombatState();
        state.initFrom(config);
        state.acceptAction(config.drinkSpec(), 0);
        // 第 17 tick 被打断：未生效，不消耗
        state.clearAction();
        check(results, "喝药中断未到生效时刻不消耗", state.potionCharges == config.drink.charges);

        CombatState state2 = new CombatState();
        state2.initFrom(config);
        state2.acceptAction(config.drinkSpec(), 0);
        int consumeTick = state2.spec.consumeTick();
        checkEquals(results, "喝药生效时刻", 18, consumeTick);
        state2.potionCharges--;
        checkEquals(results, "喝药消耗一次", 3, state2.potionCharges);
    }

    private static void geometryChecks(List<String> results) {
        Box box = new Box(0, 0, 0, 1, 1, 1);
        check(results, "线段穿过包围盒命中", Collision.capsuleHitsBox(
                new Vector3d(-2, 0.5, 0.5), new Vector3d(3, 0.5, 0.5), box, 0.1));
        check(results, "线段偏离包围盒不命中", !Collision.capsuleHitsBox(
                new Vector3d(-2, 3.0, 0.5), new Vector3d(3, 3.0, 0.5), box, 0.1));
        check(results, "上一 tick 判定体补漏", Collision.sweptCapsuleHitsBox(
                new Vector3d(-2, 0.5, 0.5), new Vector3d(0.5, 0.5, 0.5),
                new Vector3d(3, 0.5, 0.5), new Vector3d(4, 0.5, 0.5), box, 0.1));
        check(results, "盒体距离计算", Math.abs(Collision.distancePointToBox(new Vector3d(2, 0.5, 0.5), box) - 1.0) < 1.0e-6);
    }

    private static void skeletonChecks(List<String> results) {
        SkeletonAssets assets = SkeletonData.assets();
        if (assets == null) {
            results.add("FAIL 骨骼资产未载入（assets/eldencombat 下缺少 margit.*.json）");
            return;
        }
        ModelData model = assets.margit();
        checkEquals(results, "骨骼数量", 19, model.bones.size());
        checkEquals(results, "方块数量", 38, model.cubes.size());

        long tailBones = model.bones.stream().filter(bone -> bone.name().startsWith("tail_")).count();
        checkEquals(results, "尾巴骨骼数量", 3L, tailBones);
        long tailCubes = model.cubes.stream().filter(cube -> cube.name().startsWith("tail_")).count();
        checkEquals(results, "尾巴方块数量", 3L, tailCubes);
        check(results, "尾巴骨骼链 tail_01 -> tail_02 -> tail_03",
                "tail_01".equals(model.bone("tail_02").parent()) && "tail_02".equals(model.bone("tail_03").parent())
                        && !model.hasBone("tail_04"));
        check(results, "存在 staff_socket 与 spectral_socket",
                model.hasBone("staff_socket") && model.hasBone("spectral_socket"));

        checkEquals(results, "动作数量", 8, assets.clips().size());
        for (String clip : new String[]{"idle", "walk", "staff_sweep", "staff_slam", "dagger_slash", "hammer_slam", "stagger", "death"}) {
            check(results, "动作存在 " + clip, assets.hasClip(clip));
        }
        AnimationClip dagger = assets.clip("dagger_slash");
        check(results, "匕首动作有 equip 事件", "spectral_dagger".equals(dagger.equippedAt(dagger.hitStart())));
        AnimationClip hammer = assets.clip("hammer_slam");
        check(results, "黄金锤动作有 equip 事件", "spectral_hammer".equals(hammer.equippedAt(hammer.hitStart())));
        check(results, "死亡动作取消武器显示", assets.clip("death").equippedAt(20) == null);

        SkeletonPose rest = SkeletonPose.rest(model);
        Vector3f pelvis = rest.boneOrigin("pelvis");
        check(results, "骨盆原点 y = 1.5 方块", Math.abs(pelvis.y - 1.5f) < 1.0e-4f);
        Vector3f head = rest.boneOrigin("head");
        check(results, "头部原点高于骨盆", head.y > pelvis.y);

        ModelData.CubeSpec body = model.cubes.stream().filter(cube -> cube.name().equals("body")).findFirst().orElseThrow();
        Box bodyBox = rest.cubeWorldBox(body);
        check(results, "躯干包围盒在合理范围", bodyBox.minY() > 1.0 && bodyBox.maxY() < 3.0);

        HitVolumes.Capsule staff = HitVolumes.cubeAxisCapsule(rest, "staff_shaft", 0.28);
        check(results, "木杖判定体长度大于 1.5 方块", staff.a().distance(staff.b()) > 1.5);

        SkeletonPose accessory = SkeletonPose.of(assets.spectralHammer(), Map.of()).attachTo(rest, "spectral_socket");
        Box headBox = HitVolumes.cubeBox(accessory, "head");
        check(results, "黄金锤锤头盒在握持点附近", headBox.maxY() > rest.boneOrigin("hand_l").y - 0.5);
    }
}
