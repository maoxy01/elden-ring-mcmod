package com.dsh.eldencombat.client.effects;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.effects.SkillEffectMesh;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 取证用的显式诊断层（NORMAL / PRIMARY_ONLY / DEBRIS_ONLY / CLEAN）。
 *
 * <p>它只做两件事：<b>（1）</b>按"本次取证的指定实例/指定路由"决定主贴片、碎块能否提交；
 * <b>（2）</b>统计真实提交与"被诊断抑制"的次数，供夹具门禁核对。
 *
 * <p>正常客户端里 {@link #active()} 恒为 false，所有判据直接返回 true、不产生任何日志或计数，
 * 也不引用任何从发布 JAR 排除的夹具类；异常/结束由调用方 {@link #reset()} 恢复。
 */
public final class VfxDiagnosticLayers {
    public enum Layer { NORMAL, PRIMARY_ONLY, DEBRIS_ONLY, CLEAN }

    private static Layer layer = Layer.NORMAL;
    private static boolean active;
    private static int ownerId = -1, waveId = -1, phase = -1;
    private static Set<SkillEffectMesh.Kind> kinds = Set.of();
    /** 目标实例 id：{@code wave/<id>} 与 {@code effect/<id>/<phase>} 里的实例编号，用来排除别的实例顶替目标。 */
    private static Set<Integer> entityIds = Set.of();
    private static final Set<String> NOTED = new HashSet<>();
    private static int spriteSubmissions, outlineSubmissions, debrisSubmissions, legacyCalls, suppressed;
    private static int spriteThisFrame, outlineThisFrame, debrisThisFrame;
    /**
     * 本帧作者美术帧号与该路由的总帧数（-1 表示本帧没有提交作者美术）。
     *
     * <p>为什么需要它：效果只在开头几帧被采样时，"美术有没有上屏"量到的是**淡入帧**，
     * 而玩家看到的是中段。取证时必须能证明"样本取自哪一帧"，否则 184 像素这种数字
     * 无法区分"美术太薄"和"只截到了第 0 帧"（见 reports 的采样窗口一节）。
     */
    private static int artFrameThisFrame = -1, artFramesThisFrame = -1;

    private VfxDiagnosticLayers() {}

    /**
     * 选定本取证组的层与目标。目标匹配同时看 kind、实例身份与 phase：
     * 波用 {@code wave/<实体id>}，普通效果用 {@code effect/<施法者id>/<phase>}。
     * 未给出任何选择条件时视同 NORMAL（不误伤生产语义）。
     */
    public static void select(Layer which, int ownerEntityId, int waveEntityId,
                              Collection<SkillEffectMesh.Kind> targets, int onlyPhase) {
        layer = which == null ? Layer.NORMAL : which;
        ownerId = ownerEntityId;
        waveId = waveEntityId;
        phase = onlyPhase;
        kinds = targets == null ? Set.of() : Set.copyOf(targets);
        active = layer != Layer.NORMAL && (ownerId >= 0 || waveId >= 0 || !kinds.isEmpty() || !entityIds.isEmpty());
        NOTED.clear();
        spriteSubmissions = outlineSubmissions = debrisSubmissions = legacyCalls = suppressed = 0;
        spriteThisFrame = outlineThisFrame = debrisThisFrame = 0;
        artFrameThisFrame = artFramesThisFrame = -1;
        SUBMITTED_FRAME.clear();
    }

    /**
     * 只更新"目标实例集合"，保留层与累计计数。客户端实体上的 {@code ownerEntityId()} 可能是 -1
     * （没有同步到施法者 id），因此目标实例一律用**实体编号**匹配：{@code wave/<id>} 与
     * {@code effect/<id>/<phase>}。空集合表示回退到 kind/owner 匹配。
     */
    public static void retarget(Collection<Integer> instanceIds) {
        entityIds = instanceIds == null || instanceIds.isEmpty() ? Set.of() : Set.copyOf(instanceIds);
        active = layer != Layer.NORMAL && (ownerId >= 0 || waveId >= 0 || !kinds.isEmpty() || !entityIds.isEmpty());
    }

    public static void reset() { select(Layer.NORMAL, -1, -1, List.of(), -1); }

    /**
     * 只切换层，保留目标选择、日志去重与累计计数：受控对照需要在相邻渲染帧之间
     * PRIMARY_ONLY ↔ CLEAN 来回切，不能因此清掉本段的 legacy/抑制计数。
     */
    public static void setLayer(Layer which) {
        layer = which == null ? Layer.NORMAL : which;
        active = layer != Layer.NORMAL && (ownerId >= 0 || waveId >= 0 || !kinds.isEmpty());
    }

    public static boolean active() { return active; }

    public static Layer layer() { return layer; }

    /** 该实体本帧是否允许提交任何 VFX；PRIMARY_ONLY/DEBRIS_ONLY 会整段排除非目标实例。 */
    public static boolean allowEntity(SkillEffectMesh.Kind kind, String identity, int entityPhase) {
        if (!active) return true;
        boolean target = isTarget(kind, identity, entityPhase);
        return switch (layer) {
            case NORMAL -> true;
            case PRIMARY_ONLY, DEBRIS_ONLY -> target;
            case CLEAN -> !target;      // 只压目标自身的 VFX，背景与其它实例照常
        };
    }

    /** 主贴片（作者贴片/合法轮廓）是否允许：DEBRIS_ONLY 与 CLEAN 一律不允许。 */
    public static boolean primaryAllowed(SkillEffectMesh.Kind kind, String identity, int entityPhase) {
        if (!active) return true;
        boolean target = isTarget(kind, identity, entityPhase);
        return switch (layer) {
            case NORMAL -> true;
            case PRIMARY_ONLY -> target;
            case DEBRIS_ONLY, CLEAN -> false;
        };
    }

    /** 独立碎块是否允许：PRIMARY_ONLY 与 CLEAN 一律不允许（碎块不能给主贴片代签）。 */
    public static boolean debrisAllowed(SkillEffectMesh.Kind kind, String identity, int entityPhase) {
        if (!active) return true;
        boolean target = isTarget(kind, identity, entityPhase);
        return switch (layer) {
            case NORMAL -> true;
            case DEBRIS_ONLY -> target;
            case PRIMARY_ONLY, CLEAN -> false;
        };
    }

    /** 目标判定：只在"本取证组的目标实例"上生效，避免别的实例顶替取证对象。 */
    private static boolean isTarget(SkillEffectMesh.Kind kind, String identity, int entityPhase) {
        // kinds 为空表示"不按 kind 过滤"（按实例身份选目标）；实例身份始终按渲染器的命名约定匹配。
        if (!kinds.isEmpty() && !kinds.contains(kind)) return false;
        if (phase >= 0 && entityPhase != phase) return false;
        if (identity == null) return ownerId < 0 && waveId < 0 && entityIds.isEmpty();
        int instance = instanceId(identity);
        if (!entityIds.isEmpty()) return instance >= 0 && entityIds.contains(instance);
        if (identity.startsWith("wave/")) return waveId < 0 || instance == waveId;
        if (ownerId < 0) return true;
        return identity.startsWith("effect/" + ownerId + "/");
    }

    /** 从 {@code wave/<id>} 或 {@code effect/<id>/<phase>} 取出实例编号；格式不符返回 -1。 */
    private static int instanceId(String identity) {
        int start = identity.indexOf('/');
        if (start < 0) return -1;
        int end = identity.indexOf('/', start + 1);
        String number = end < 0 ? identity.substring(start + 1) : identity.substring(start + 1, end);
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException error) {
            return -1;
        }
    }

    /**
     * 真实提交计数：**不受层是否启用影响**（NORMAL 组的帧也要计数），由 {@link #select}/{@link #reset}
     * 在取证段边界清零，供夹具核对"这一段到底提交了多少贴片/轮廓/碎块、有没有回落 legacy"。
     * 同时按帧累计，供夹具只在"目标这一帧真的提交了"时取主体样本。
     */
    public static void countSprite(int quads) { if (quads > 0) { spriteSubmissions += quads; spriteThisFrame += quads; } }
    public static void countOutline(int panels) { if (panels > 0) { outlineSubmissions += panels; outlineThisFrame += panels; } }
    public static void countDebris(int pieces) { if (pieces > 0) { debrisSubmissions += pieces; debrisThisFrame += pieces; } }
    public static void countLegacy() { legacyCalls++; }
    public static void countSuppressed() { suppressed++; }

    /** 每个渲染帧开头（AFTER_SKY，早于实体渲染）清空"本帧提交"计数。 */
    public static void beginFrame() {
        spriteThisFrame = outlineThisFrame = debrisThisFrame = 0;
        artFrameThisFrame = artFramesThisFrame = -1;
        SUBMITTED_FRAME.clear();
    }

    /**
     * 本帧**真实提交过**的实例身份（{@code effect/<id>/<phase>} / {@code wave/<id>}）。
     *
     * <p>与 {@link #submittedThisFrame()} 的区别：那个只回答"这一帧整体有没有提交"，
     * 无法回答"三次挥击里的第 2、3 次有没有画出来"——逐实例覆盖门禁需要后者。
     */
    private static final Set<String> SUBMITTED_FRAME = new java.util.LinkedHashSet<>();

    /** 渲染器在**真实提交**（主贴片或合法轮廓）后登记本帧的实例身份。 */
    public static void noteSubmit(String identity, int quads, int panels) {
        if (identity == null || quads + panels <= 0) return;
        SUBMITTED_FRAME.add(identity);
    }

    /**
     * 登记本帧作者美术走到第几帧（{@code frame} 为语义帧号，{@code count} 为该路由的总帧数）。
     * 只记录，不参与任何判定；夹具用它把"采样到哪一帧"写进证据。
     */
    public static void noteArtFrame(int frame, int count) {
        if (frame < 0 || count <= 0) return;
        if (frame >= artFrameThisFrame) { artFrameThisFrame = frame; artFramesThisFrame = count; }
    }

    /** 本帧作者美术帧号（{@code 帧号/总帧数}），没有提交时为 {@code -}。 */
    public static String artThisFrame() {
        return artFrameThisFrame < 0 ? "-" : artFrameThisFrame + "/" + artFramesThisFrame;
    }

    public static Set<String> submittedIdentitiesThisFrame() { return Set.copyOf(SUBMITTED_FRAME); }

    /** 本帧目标是否真的提交了主贴片或合法轮廓（不变量：CORE/缺资源轮廓也算真实提交）。 */
    public static boolean submittedThisFrame() { return spriteThisFrame + outlineThisFrame > 0; }

    /** 本帧目标是否真的提交了独立碎块。 */
    public static boolean debrisThisFrame() { return debrisThisFrame > 0; }

    /** 被诊断层整段排除的实例：每种身份只记一条，避免刷屏。 */
    public static void noteSkipped(String identity) {
        if (!active || identity == null || !NOTED.add(identity)) return;
        EldenCombat.LOGGER.info("VFX_LAYER_SKIP layer={} id={} (diagnostic layer excluded this entity)", layer, identity);
    }

    /** 供夹具门禁核对：{@code [spriteQuads, outlinePanels, debrisPieces, legacyCalls, suppressedRecords]}。 */
    public static int[] counts() {
        return new int[] { spriteSubmissions, outlineSubmissions, debrisSubmissions, legacyCalls, suppressed };
    }

    public static String summary() {
        int[] c = counts();
        return String.format(Locale.ROOT, "layer=%s active=%s sprite=%d outline=%d debris=%d legacy=%d suppressed=%d",
                layer, active, c[0], c[1], c[2], c[3], c[4]);
    }
}
