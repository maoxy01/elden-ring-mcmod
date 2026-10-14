package com.dsh.eldencombat.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 首轮调试默认值的唯一集中处。首次运行写出 config/eldencombat-combat.json，
 * 热改后可 /eldencombat reload 重新读取。这些数字不声称精确还原原作数值。
 */
public final class CombatConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ---- 资源与全局规则 ----
    public Stamina stamina = new Stamina();
    public Guard guard = new Guard();
    public Poise poise = new Poise();
    public Boss boss = new Boss();
    public Ai ai = new Ai();
    public LockOn lockOn = new LockOn();
    public Drink drink = new Drink();
    public Roll roll = new Roll();
    public Parry parry = new Parry();
    /** 攻击后摇最后多少 tick 允许翻滚取消 */
    public int rollCancelLastTicks = 6;
    /** 连续弹反计数有效窗口（tick） */
    public int parryChainWindowTicks = 100;
    /** 触发 Boss 失衡所需的连续弹反次数 */
    public int parryChainRequired = 2;
    /** 弹反成功时给 Boss 的削韧 */
    public float parryPoiseDamage = 50f;
    /** 测试用剑的冲击力（击退） */
    public double attackKnockback = 0.35;

    public Map<String, Weapon> weapons = new LinkedHashMap<>();
    public Map<String, BossAttack> bossAttacks = new LinkedHashMap<>();

    public static final class Stamina {
        public float max = 100f;
        public int regenDelayTicks = 12;
        public float regenPerTick = 1f;
        /** 防御时恢复速率乘数 */
        public float guardRegenMultiplier = 0.5f;
    }

    public static final class Guard {
        /** 盾防覆盖的角度（以玩家朝向为中心的完整锥角） */
        public float coneDegrees = 120f;
        /** 精力耗尽后的破防硬直 */
        public int breakTicks = 24;
    }

    public static final class Poise {
        public float max = 100f;
        public int regenDelayTicks = 80;
        public float regenPerTick = 2.5f;
        /**
         * 失衡打满后的崩解窗口下限（tick）。2.0s = 40 tick；
         * 实际时长取 {@code max(硬直动作时长, 本值)}，见 {@link PoiseMath#breakTicks}。
         */
        public int brokenTicks = 40;
        /** 崩解期敌人受到的伤害倍率（§6：+50% 易伤） */
        public float brokenDamageMultiplier = 1.5f;
        /** 多人：每多 1 名玩家，失衡上限提高的比例（0.5 = 两人 ×1.5） */
        public float multiplayerScalePerExtraPlayer = 0.5f;
    }

    public static final class Boss {
        public float maxHealth = 200f;
        /** 血量低于该比例进入二阶段 */
        public float phaseTwoThreshold = 0.6f;
        public double collisionWidth = 1.35;
        public double collisionHeight = 3.1;
    }

    public static final class Ai {
        public double meleeRange = 2.1;
        public double slamRangeMin = 1.7;
        public double slamRangeMax = 2.6;
        public double attackConeDegrees = 90f;
        public int actionCooldownTicks = 10;
        /** 停步观察时间 */
        public int observeTicks = 12;
        /** 前摇前半段允许缓慢转向 */
        public float turnSlowDegreesPerTick = 6f;
        /** 同招最多连续次数 */
        public int maxSameMoveRepeat = 2;
        public double chaseSpeed = 0.28;
        /** 停步距离 */
        public double stopDistance = 1.6;
        /** 脱战距离 */
        public double loseTargetRange = 48.0;
        public Map<String, Map<String, Double>> phaseWeights = new LinkedHashMap<>();
    }

    public static final class LockOn {
        public double range = 24.0;
        public double breakRange = 32.0;
    }

    public static final class Drink {
        public int totalTicks = 28;
        public int consumeTick = 18;
        public float heal = 8f;
        public int charges = 4;
        public float staminaCost = 0f;
    }

    public static final class Roll {
        public float staminaCost = 22f;
        public int totalTicks = 20;
        public int iframeStart = 4;
        public int iframeEnd = 11;
        /** 位移速度（方块/tick）与生效区间 */
        public double speed = 0.45;
        public int moveStart = 2;
        public int moveEnd = 14;
    }

    public static final class Parry {
        public float staminaCost = 15f;
        public int totalTicks = 18;
        public int windowStart = 3;
        public int windowEnd = 7;
    }

    /** 一种武器的轻/重/满蓄力重击参数 */
    public static final class Weapon {
        public Attack light = new Attack();
        public Attack heavy = new Attack();
        public Attack chargedHeavy = new Attack();
        /** 蓄力达满所需 tick */
        public int chargeFullTicks = 20;
        /** 蓄满后最多再维持多少 tick 自动释放 */
        public int chargeMaxTicks = 30;
        /**
         * 玩家武器判定体参数。玩家模型与动作由 Codex 后续交付，
         * 首版用"手部到刀尖"的数值胶囊 + 横扫角来近似骨骼采样，接口与判定算法保持一致。
         */
        public double reach = 2.6;
        public double sweepDegrees = 120.0;
        public double bladeRadius = 0.18;
    }

    public static final class Attack {
        public float staminaCost;
        public int totalTicks;
        public int hitStart;
        public int hitEnd;
        public float damage;
        public float poiseDamage;
    }

    /** Boss 单招的伤害与判定体参数；命中窗口取自动作数据的 hit_start/hit_end 事件。 */
    public static final class BossAttack {
        public float damage;
        public float guardCost;
        public boolean parryable;
        /** 长轴胶囊半径（木杖 / 匕首） */
        public double capsuleRadius = 0.25;
        /** 黄金锤锤头扫掠判定盒半边长（x,y,z，单位方块） */
        public double[] hammerHead = {0.9, 0.7, 0.7};
        /** 判定所用的骨骼 */
        public String hitBone = "staff_socket";
        /** 该招是否用锤头盒判定（true=盒，false=骨骼长轴胶囊） */
        public boolean hammerBox = false;
        public double weightPhaseOne = 0;
        public double weightPhaseTwo = 0;
    }

    // ------------------------------------------------------------------
    // 规格构造
    // ------------------------------------------------------------------

    /** 当前在场玩家数下的失衡上限（多人缩放，见 {@link PoiseMath#maxForPlayers}）。 */
    public float poiseMaxFor(int players) {
        return PoiseMath.maxForPlayers(poise.max, players, poise.multiplayerScalePerExtraPlayer);
    }

    /** 崩解窗口时长（tick）：动作时长与配置下限取大。 */
    public int poiseBreakTicks(int animationTicks) {
        return PoiseMath.breakTicks(animationTicks, poise.brokenTicks);
    }

    /** 崩解期易伤倍率（未崩解为 1）。 */
    public float poiseDamageTakenMultiplier(boolean broken) {
        return PoiseMath.damageTakenMultiplier(broken, poise.brokenDamageMultiplier);
    }

    public Weapon weapon(WeaponClass weaponClass) {
        Weapon weapon = weapons.get(weaponClass.key());
        if (weapon == null) {
            throw new IllegalStateException("配置缺少武器定义: " + weaponClass.key());
        }
        return weapon;
    }

    public ActionSpec lightSpec(WeaponClass weaponClass) {
        return attackSpec(ActionId.LIGHT_ATTACK, weapon(weaponClass).light);
    }

    public ActionSpec heavySpec(WeaponClass weaponClass) {
        return attackSpec(ActionId.HEAVY_ATTACK, weapon(weaponClass).heavy);
    }

    public ActionSpec chargedHeavySpec(WeaponClass weaponClass) {
        return attackSpec(ActionId.CHARGED_HEAVY, weapon(weaponClass).chargedHeavy);
    }

    private ActionSpec attackSpec(ActionId id, Attack attack) {
        HitWindow window = new HitWindow(attack.hitStart, attack.hitEnd, attack.damage, attack.poiseDamage, 0f, false);
        int cancelFrom = Math.max(0, attack.totalTicks - rollCancelLastTicks);
        return new ActionSpec(id, attack.totalTicks, attack.staminaCost, List.of(window), cancelFrom, -1, 0f, 0);
    }

    public ActionSpec rollSpec() {
        HitWindow iframe = new HitWindow(roll.iframeStart, roll.iframeEnd, 0f, 0f, 0f, false);
        return new ActionSpec(ActionId.ROLL, roll.totalTicks, roll.staminaCost, List.of(iframe), -1, -1, 0f, 0);
    }

    public ActionSpec parrySpec() {
        HitWindow window = new HitWindow(parry.windowStart, parry.windowEnd, 0f, 0f, 0f, true);
        return new ActionSpec(ActionId.PARRY, parry.totalTicks, parry.staminaCost, List.of(window), -1, -1, 0f, 0);
    }

    public ActionSpec drinkSpec() {
        return new ActionSpec(ActionId.DRINK, drink.totalTicks, drink.staminaCost, List.of(),
                Math.max(0, drink.totalTicks - rollCancelLastTicks), drink.consumeTick, drink.heal, 0);
    }

    public ActionSpec guardBrokenSpec() {
        return ActionSpec.simple(ActionId.GUARD_BROKEN, guard.breakTicks, 0f);
    }

    public ActionSpec staggerSpec(int ticks) {
        return ActionSpec.simple(ActionId.STAGGER, Math.max(1, ticks), 0f);
    }

    // ------------------------------------------------------------------
    // 默认值
    // ------------------------------------------------------------------

    public static CombatConfig defaults() {
        CombatConfig config = new CombatConfig();

        Weapon lightSword = new Weapon();
        lightSword.light = attack(15f, 22, 8, 12, 5f, 8f);
        lightSword.heavy = attack(25f, 34, 15, 20, 8f, 22f);
        lightSword.chargedHeavy = attack(35f, 36, 16, 22, 11f, 30f);
        lightSword.chargeFullTicks = 20;
        lightSword.chargeMaxTicks = 30;
        lightSword.reach = 2.6;
        lightSword.sweepDegrees = 120.0;
        lightSword.bladeRadius = 0.18;
        config.weapons.put(WeaponClass.LIGHT_SWORD.key(), lightSword);

        Weapon greatSword = new Weapon();
        greatSword.light = attack(20f, 30, 12, 17, 7f, 10f);
        greatSword.heavy = attack(25f, 34, 15, 20, 11f, 28f);
        greatSword.chargedHeavy = attack(35f, 36, 16, 22, 15f, 34f);
        greatSword.chargeFullTicks = 20;
        greatSword.chargeMaxTicks = 30;
        greatSword.reach = 3.2;
        greatSword.sweepDegrees = 140.0;
        greatSword.bladeRadius = 0.24;
        config.weapons.put(WeaponClass.GREAT_SWORD.key(), greatSword);

        config.bossAttacks.put("staff_sweep", bossAttack(12f, 20f, true, "staff_socket", 0.28, false, 35, 25));
        config.bossAttacks.put("staff_slam", bossAttack(18f, 35f, false, "staff_socket", 0.34, false, 30, 25));
        config.bossAttacks.put("dagger_slash", bossAttack(8f, 12f, true, "spectral_socket", 0.2, false, 35, 35));
        config.bossAttacks.put("hammer_slam", bossAttack(22f, 45f, false, "spectral_socket", 0.4, true, 0, 30));

        Map<String, Double> phaseOne = new LinkedHashMap<>();
        phaseOne.put("staff_sweep", 35.0);
        phaseOne.put("staff_slam", 30.0);
        phaseOne.put("dagger_slash", 35.0);
        Map<String, Double> phaseTwo = new LinkedHashMap<>();
        phaseTwo.put("staff_sweep", 25.0);
        phaseTwo.put("staff_slam", 25.0);
        phaseTwo.put("dagger_slash", 20.0);
        phaseTwo.put("hammer_slam", 30.0);
        config.ai.phaseWeights.put("1", phaseOne);
        config.ai.phaseWeights.put("2", phaseTwo);

        return config;
    }

    private static Attack attack(float cost, int ticks, int start, int end, float damage, float poise) {
        Attack attack = new Attack();
        attack.staminaCost = cost;
        attack.totalTicks = ticks;
        attack.hitStart = start;
        attack.hitEnd = end;
        attack.damage = damage;
        attack.poiseDamage = poise;
        return attack;
    }

    private static BossAttack bossAttack(float damage, float guardCost, boolean parryable, String bone, double radius,
                                         boolean hammerBox, double weightOne, double weightTwo) {
        BossAttack attack = new BossAttack();
        attack.damage = damage;
        attack.guardCost = guardCost;
        attack.parryable = parryable;
        attack.hitBone = bone;
        attack.capsuleRadius = radius;
        attack.hammerBox = hammerBox;
        attack.weightPhaseOne = weightOne;
        attack.weightPhaseTwo = weightTwo;
        return attack;
    }

    // ------------------------------------------------------------------
    // 读写 / 修补
    // ------------------------------------------------------------------

    /** 把缺失或非法字段补成默认值，保证热改配置不会把状态机弄坏。 */
    public CombatConfig normalize() {
        CombatConfig defaults = defaults();
        if (stamina == null) stamina = defaults.stamina;
        if (guard == null) guard = defaults.guard;
        if (poise == null) poise = defaults.poise;
        if (boss == null) boss = defaults.boss;
        if (ai == null) ai = defaults.ai;
        if (lockOn == null) lockOn = defaults.lockOn;
        if (drink == null) drink = defaults.drink;
        if (roll == null) roll = defaults.roll;
        if (parry == null) parry = defaults.parry;
        if (weapons == null) weapons = new LinkedHashMap<>();
        if (bossAttacks == null) bossAttacks = new LinkedHashMap<>();

        for (Map.Entry<String, Weapon> entry : defaults.weapons.entrySet()) {
            Weapon current = weapons.get(entry.getKey());
            if (current == null) {
                weapons.put(entry.getKey(), entry.getValue());
                continue;
            }
            current.light = fixAttack(current.light, entry.getValue().light);
            current.heavy = fixAttack(current.heavy, entry.getValue().heavy);
            current.chargedHeavy = fixAttack(current.chargedHeavy, entry.getValue().chargedHeavy);
            if (current.chargeFullTicks <= 0) current.chargeFullTicks = entry.getValue().chargeFullTicks;
            if (current.chargeMaxTicks <= 0) current.chargeMaxTicks = entry.getValue().chargeMaxTicks;
            if (current.reach <= 0) current.reach = entry.getValue().reach;
            if (current.sweepDegrees <= 0) current.sweepDegrees = entry.getValue().sweepDegrees;
            if (current.bladeRadius <= 0) current.bladeRadius = entry.getValue().bladeRadius;
        }
        for (Map.Entry<String, BossAttack> entry : defaults.bossAttacks.entrySet()) {
            BossAttack current = bossAttacks.get(entry.getKey());
            if (current == null) {
                bossAttacks.put(entry.getKey(), entry.getValue());
                continue;
            }
            if (current.hitBone == null || current.hitBone.isBlank()) current.hitBone = entry.getValue().hitBone;
            if (current.capsuleRadius <= 0) current.capsuleRadius = entry.getValue().capsuleRadius;
            if (current.hammerHead == null || current.hammerHead.length != 3) current.hammerHead = entry.getValue().hammerHead;
        }

        if (stamina.max <= 0) stamina.max = defaults.stamina.max;
        if (stamina.regenPerTick <= 0) stamina.regenPerTick = defaults.stamina.regenPerTick;
        if (stamina.regenDelayTicks < 0) stamina.regenDelayTicks = defaults.stamina.regenDelayTicks;
        if (poise.max <= 0) poise.max = defaults.poise.max;
        if (poise.regenPerTick <= 0) poise.regenPerTick = defaults.poise.regenPerTick;
        if (poise.regenDelayTicks < 0) poise.regenDelayTicks = defaults.poise.regenDelayTicks;
        if (poise.brokenTicks < 0) poise.brokenTicks = defaults.poise.brokenTicks;
        if (poise.brokenDamageMultiplier < 1f) poise.brokenDamageMultiplier = defaults.poise.brokenDamageMultiplier;
        if (poise.multiplayerScalePerExtraPlayer < 0f) poise.multiplayerScalePerExtraPlayer = 0f;
        if (boss.maxHealth <= 0) boss.maxHealth = defaults.boss.maxHealth;
        if (guard.breakTicks <= 0) guard.breakTicks = defaults.guard.breakTicks;
        if (guard.coneDegrees <= 0 || guard.coneDegrees > 360) guard.coneDegrees = defaults.guard.coneDegrees;
        if (roll.totalTicks <= 0) roll.totalTicks = defaults.roll.totalTicks;
        if (roll.iframeEnd <= roll.iframeStart) {
            roll.iframeStart = defaults.roll.iframeStart;
            roll.iframeEnd = defaults.roll.iframeEnd;
        }
        if (roll.moveEnd <= roll.moveStart) {
            roll.moveStart = defaults.roll.moveStart;
            roll.moveEnd = defaults.roll.moveEnd;
        }
        if (roll.speed <= 0) roll.speed = defaults.roll.speed;
        if (parry.totalTicks <= 0) parry.totalTicks = defaults.parry.totalTicks;
        if (parry.windowEnd <= parry.windowStart) {
            parry.windowStart = defaults.parry.windowStart;
            parry.windowEnd = defaults.parry.windowEnd;
        }
        if (rollCancelLastTicks < 0) rollCancelLastTicks = 0;
        if (drink.totalTicks <= 0) drink.totalTicks = defaults.drink.totalTicks;
        if (drink.consumeTick <= 0 || drink.consumeTick > drink.totalTicks) drink.consumeTick = defaults.drink.consumeTick;
        if (drink.charges <= 0) drink.charges = defaults.drink.charges;
        if (ai.phaseWeights == null || ai.phaseWeights.isEmpty()) ai.phaseWeights = defaults.ai.phaseWeights;
        if (ai.actionCooldownTicks < 0) ai.actionCooldownTicks = defaults.ai.actionCooldownTicks;
        if (ai.maxSameMoveRepeat <= 0) ai.maxSameMoveRepeat = defaults.ai.maxSameMoveRepeat;
        if (parryChainRequired <= 0) parryChainRequired = defaults.parryChainRequired;
        if (parryChainWindowTicks <= 0) parryChainWindowTicks = defaults.parryChainWindowTicks;
        return this;
    }

    private static Attack fixAttack(Attack current, Attack fallback) {
        if (current == null) {
            return fallback;
        }
        if (current.totalTicks <= 0) current.totalTicks = fallback.totalTicks;
        if (current.damage <= 0) current.damage = fallback.damage;
        if (current.hitEnd <= current.hitStart || current.hitEnd > current.totalTicks) {
            current.hitStart = fallback.hitStart;
            current.hitEnd = fallback.hitEnd;
        }
        if (current.staminaCost < 0) current.staminaCost = fallback.staminaCost;
        if (current.poiseDamage < 0) current.poiseDamage = fallback.poiseDamage;
        return current;
    }

    public static CombatConfig loadOrCreate(Path file) {
        CombatConfig config = defaults();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                CombatConfig parsed = GSON.fromJson(reader, CombatConfig.class);
                if (parsed != null) {
                    config = parsed;
                }
            } catch (IOException | JsonSyntaxException exception) {
                com.dsh.eldencombat.core.CoreLog.LOGGER.error("读取战斗配置失败，改用内置默认值: " + file, exception);
                config = defaults();
            }
        }
        config.normalize();
        config.save(file);
        return config;
    }

    public void save(Path file) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException exception) {
            com.dsh.eldencombat.core.CoreLog.LOGGER.error("写入战斗配置失败: " + file, exception);
        }
    }
}
