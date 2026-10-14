package com.dsh.eldencombat.command;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.server.skills.PlayerFpState;
import com.dsh.eldencombat.server.skills.RemembranceSkillSpec;
import com.dsh.eldencombat.server.skills.RemembranceWeaponRules;
import com.dsh.eldencombat.server.skills.SkillCasting;
import com.dsh.eldencombat.server.skills.SkillCastState;
import com.dsh.eldencombat.server.skills.SkillDriver;
import com.dsh.eldencombat.server.skills.SkillServerEvents;
import com.dsh.eldencombat.server.skills.SkillTransactions;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 追忆战技的**权限隔离**测试入口（父命令 {@code /eldencombat} 已要求权限等级 2，
 * 本类每个执行体再自查一次，避免以后有人把子节点挂到别的根上导致越权）。
 *
 * <pre>
 * /eldencombat fp get              查看当前 FP / 冷却 / 上次施放
 * /eldencombat fp set &lt;value&gt;     直接设置 FP（仅测试用；战斗逻辑仍然只走原子扣费）
 * /eldencombat fp max              补满 FP
 * /eldencombat fp skill status     主手武器绑定、施放状态、FP 一览
 * /eldencombat fp skill cast &lt;id&gt; 按 id 施放（与按战技键完全同一条服务端路径）
 * /eldencombat fp skill specs      打印八个战技的全部参数
 * </pre>
 *
 * <p>纯原版方案下不再有"技能注册表 / 专属动画注册"可查：技能由参数表的
 * {@code itemId} 绑定到武器，动作是客户端的 {@code skill_poses/*.json}。
 *
 * <p>注意：本命令只影响命令执行者自己的 FP 与施放，不批量改其他玩家，也不生成永久效果。
 */
public final class SkillTestCommand {

    /** 与父命令一致的权限门槛 */
    private static final int PERMISSION = 2;

    private SkillTestCommand() {
    }

    /** 挂到 {@code /eldencombat} 下的子节点 */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("fp")
                .then(Commands.literal("get").executes(SkillTestCommand::fpGet))
                .then(Commands.literal("set")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(0.0f))
                                .executes(SkillTestCommand::fpSet)))
                .then(Commands.literal("max")
                        .executes(SkillTestCommand::fpMax))
                .then(Commands.literal("skill")
                        .then(Commands.literal("status").executes(SkillTestCommand::skillStatus))
                        .then(Commands.literal("cast")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(SkillTestCommand::skillCast)))
                        .then(Commands.literal("specs").executes(SkillTestCommand::skillSpecs)));
    }

    private static boolean deny(CommandSourceStack source) {
        if (source.hasPermission(PERMISSION)) {
            return false;
        }
        source.sendFailure(Component.literal("追忆战技测试入口需要权限等级 " + PERMISSION + "（权限隔离）"));
        EldenCombat.LOGGER.warn("[skills] 权限不足的战技测试命令被拒绝: {}", source.getTextName());
        return true;
    }

    private static int fpGet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (deny(context.getSource())) {
            return 0;
        }
        ServerPlayer player = context.getSource().getPlayerOrException();
        PlayerFpState state = SkillServerEvents.fp(player);
        context.getSource().sendSuccess(() -> Component.literal(state.describe()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int fpSet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (deny(context.getSource())) {
            return 0;
        }
        ServerPlayer player = context.getSource().getPlayerOrException();
        float value = FloatArgumentType.getFloat(context, "value");
        PlayerFpState state = SkillServerEvents.fp(player);
        state.setFp(value);
        SkillServerEvents.sync(player);
        context.getSource().sendSuccess(() -> Component.literal("FP 已设为 " + state.describe()), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int fpMax(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (deny(context.getSource())) {
            return 0;
        }
        ServerPlayer player = context.getSource().getPlayerOrException();
        PlayerFpState state = SkillServerEvents.fp(player);
        state.setFp(state.maxFp());
        SkillServerEvents.sync(player);
        context.getSource().sendSuccess(() -> Component.literal("FP 已补满 " + state.describe()), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int skillStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (deny(context.getSource())) {
            return 0;
        }
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        RemembranceSkillSpec bound = SkillCasting.specForMainHand(player);
        SkillCastState cast = SkillDriver.cast(player);
        List<String> lines = new java.util.ArrayList<>();
        lines.add("主手=" + held.getItem() + "（" + held.getCount() + "）");
        lines.add(bound == null
                ? "武器战技=无（换上追忆武器后按战技键释放）"
                : "武器战技=" + bound.id() + " FP=" + bound.fpCost()
                        + (bound.hasFollowup() ? " 追击 FP=" + bound.costForStage(1)
                                + " 窗口=" + bound.followupWindowTicks() + "t 最多 " + bound.maxStages() + " 段" : ""));
        lines.add("绑定校验=" + (bound != null && RemembranceWeaponRules.matches(player, bound)));
        lines.add("施放中=" + cast.isCasting() + " 技能=" + cast.skillId() + " 段=" + cast.stage()
                + " cast#" + cast.castId() + " 已命中=" + cast.hitCount());
        lines.add("FP=" + SkillServerEvents.fp(player).describe());
        lines.add("战技键=默认 C（可在按键设置里改绑）");
        lines.forEach(line -> context.getSource().sendSuccess(() -> Component.literal(line), false));
        return Command.SINGLE_SUCCESS;
    }

    private static int skillSpecs(CommandContext<CommandSourceStack> context) {
        if (deny(context.getSource())) {
            return 0;
        }
        for (RemembranceSkillSpec spec : SkillDriver.specs().values()) {
            context.getSource().sendSuccess(() -> Component.literal(spec.describe()), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * 按 id 施放：与客户端按战技键**同一条服务端路径**（{@link SkillCasting}），
     * 只跳过"主手必须是绑定武器"这一步，方便逐技能验收。
     */
    private static int skillCast(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (deny(context.getSource())) {
            return 0;
        }
        ServerPlayer player = context.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(context, "id");
        RemembranceSkillSpec spec = SkillDriver.spec(id);
        if (spec == null) {
            context.getSource().sendFailure(Component.literal("未知战技 id: " + id
                    + "（可用：" + String.join(", ", SkillDriver.specs().keySet()) + "）"));
            return 0;
        }
        long tick = player.level().getGameTime();
        SkillTransactions.Result result = SkillCasting.requestById(player, id, tick);
        PlayerFpState state = SkillServerEvents.fp(player);
        SkillCastState cast = SkillDriver.cast(player);
        EldenCombat.LOGGER.info("[skills-test] cast {} result={} tick={} FP={} cast#{} stage={}",
                id, result, tick, state.fp(), cast.castId(), cast.stage());
        context.getSource().sendSuccess(() -> Component.literal("施放 " + id + " 结果=" + result
                + " " + state.describe() + " 段=" + cast.stage()), true);
        return result == SkillTransactions.Result.REJECTED ? 0 : Command.SINGLE_SUCCESS;
    }
}
