package com.dsh.eldencombat.command;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.core.CombatConfig;
import com.dsh.eldencombat.core.CombatState;
import com.dsh.eldencombat.entity.MargitEntity;
import com.dsh.eldencombat.registry.ModAttachments;
import com.dsh.eldencombat.registry.ModEntities;
import com.dsh.eldencombat.registry.ModItems;
import com.dsh.eldencombat.testing.SelfTest;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 调试命令（限作弊 / 管理员）：
 * /eldencombat testkit | summon margit | reset | reload | selftest | status
 *   | attribute | weapon | ash | skills
 * 命令不扫描删除其他实体，只处理该玩家登记的测试 Boss 与测试资源。
 *
 * <p>{@code /eldencombat ash grant|revoke} 是**管理/验收面**，不是正式奖励来源
 * （正式来源接现有探索/奖励能力，见 {@code AshUnlocks}）。用它做完验收**不能**声称玩法闭环通过。
 */
@EventBusSubscriber(modid = EldenCombat.MODID)
public final class EldenCombatCommand {

    private static final String OWNER_KEY = "eldencombat_test_owner";

    private EldenCombatCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("eldencombat")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("testkit").executes(EldenCombatCommand::testkit))
                .then(Commands.literal("summon")
                        .then(Commands.literal("margit").executes(EldenCombatCommand::summonMargit)))
                .then(Commands.literal("reset").executes(EldenCombatCommand::reset))
                .then(Commands.literal("reload").executes(EldenCombatCommand::reload))
                .then(Commands.literal("selftest").executes(EldenCombatCommand::selfTest))
                .then(Commands.literal("status").executes(EldenCombatCommand::status))
                .then(Commands.literal("attribute")
                        .executes(EldenCombatCommand::showAttributes)
                        .then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .then(Commands.argument("points", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-99, 99))
                                        .executes(EldenCombatCommand::setAttribute))))
                .then(Commands.literal("weapon")
                        .executes(EldenCombatCommand::showWeapon)
                        .then(Commands.literal("level")
                                .then(Commands.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 10))
                                        .executes(EldenCombatCommand::setWeaponLevel)))
                        .then(Commands.literal("affinity")
                                .then(Commands.argument("affinity", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(EldenCombatCommand::setWeaponAffinity))))
                .then(Commands.literal("ash")
                        .executes(EldenCombatCommand::showAsh)
                        .then(Commands.literal("grant")
                                .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                        .executes(EldenCombatCommand::grantAsh)))
                        .then(Commands.literal("revoke")
                                .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                        .executes(EldenCombatCommand::revokeAsh))))
                .then(SkillTestCommand.node()));
    }

    /** /eldencombat attribute：查看五项属性 */
    private static int showAttributes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        context.getSource().sendSuccess(() -> Component.literal(
                com.dsh.eldencombat.server.PlayerAttributes.describe(player)), false);
        return Command.SINGLE_SUCCESS;
    }

    /** /eldencombat attribute <kind> <points>：加点（±99，夹在 0..99） */
    private static int setAttribute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String raw = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "kind");
        int delta = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "points");
        com.dsh.eldencombat.core.AttributeKind kind = com.dsh.eldencombat.core.AttributeKind.byKey(raw);
        if (kind == null) {
            context.getSource().sendFailure(Component.literal("未知属性：" + raw
                    + "（可用：strength / dexterity / intelligence / faith / arcane）"));
            return 0;
        }
        int now = com.dsh.eldencombat.server.PlayerAttributes.add(player, kind, delta);
        context.getSource().sendSuccess(() -> Component.literal(kind.label() + " 现在是 " + now
                + "（" + com.dsh.eldencombat.server.PlayerAttributes.describe(player) + "）"), false);
        return Command.SINGLE_SUCCESS;
    }

    /** /eldencombat weapon：手上武器的成长状态 + 实际伤害倍率 */
    private static int showWeapon(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        String text = com.dsh.eldencombat.item.WeaponStacks.describe(held)
                + "（含属性补正 ×" + String.format("%.2f", com.dsh.eldencombat.item.WeaponStacks.damageMultiplier(
                held, com.dsh.eldencombat.server.PlayerAttributes.effective(player))) + "）";
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return Command.SINGLE_SUCCESS;
    }

    /** /eldencombat weapon level <n>：直接设置手上武器的强化等级（验收用，等价于锻造台结果） */
    private static int setWeaponLevel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int level = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "level");
        ItemStack held = player.getMainHandItem();
        com.dsh.eldencombat.core.WeaponDefinition definition =
                com.dsh.eldencombat.item.WeaponStacks.definition(held);
        if (definition == null) {
            context.getSource().sendFailure(Component.literal("主手不是本模组的武器"));
            return 0;
        }
        com.dsh.eldencombat.core.WeaponState state = com.dsh.eldencombat.item.WeaponStacks.state(held);
        com.dsh.eldencombat.item.WeaponStacks.setState(held,
                new com.dsh.eldencombat.core.WeaponState(level, state.affinity(), state.artLocked()));
        context.getSource().sendSuccess(() -> Component.literal(
                com.dsh.eldencombat.item.WeaponStacks.describe(held)), false);
        return Command.SINGLE_SUCCESS;
    }

    /** /eldencombat weapon affinity <name>：直接设置质变（验收用；战灰锁定会被拒绝） */
    private static int setWeaponAffinity(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String raw = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "affinity");
        ItemStack held = player.getMainHandItem();
        com.dsh.eldencombat.core.WeaponDefinition definition =
                com.dsh.eldencombat.item.WeaponStacks.definition(held);
        if (definition == null) {
            context.getSource().sendFailure(Component.literal("主手不是本模组的武器"));
            return 0;
        }
        com.dsh.eldencombat.core.Affinity affinity = com.dsh.eldencombat.core.Affinity.byName(raw);
        com.dsh.eldencombat.core.WeaponState state = com.dsh.eldencombat.item.WeaponStacks.state(held);
        if (affinity.isNone() && !"none".equalsIgnoreCase(raw)) {
            context.getSource().sendFailure(Component.literal("未知质变：" + raw
                    + "（可用：heavy / keen / quality / magic / fire / sacred / blood / occult / none）"));
            return 0;
        }
        if (!affinity.isNone() && state.artLocked()) {
            context.getSource().sendFailure(Component.literal("该武器战灰锁定（追忆武器），不能改质变"));
            return 0;
        }
        com.dsh.eldencombat.item.WeaponStacks.setState(held, state.withAffinity(affinity));
        context.getSource().sendSuccess(() -> Component.literal(
                com.dsh.eldencombat.item.WeaponStacks.describe(held)), false);
        return Command.SINGLE_SUCCESS;
    }

    /** /eldencombat ash：查看战灰解锁（服务端存档）与当前目录规模 */
    private static int showAsh(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<String> owned = com.dsh.eldencombat.server.skills.AshUnlocks.list(player);
        var catalog = com.dsh.eldencombat.server.skills.AshWorkshop.catalog(player);
        context.getSource().sendSuccess(() -> Component.literal("已解锁战灰 " + owned.size() + " 件"
                + (owned.isEmpty() ? "（无）" : "：" + String.join(", ", owned))), false);
        context.getSource().sendSuccess(() -> Component.literal("战灰目录 " + catalog.size() + " 项"
                + (catalog.isEmpty() ? "（普通战灰目录尚未注册，未实现项不可选不可绑定）" : "")), false);
        return Command.SINGLE_SUCCESS;
    }

    /** /eldencombat ash grant &lt;id&gt;：幂等授予。管理/验收面，不是正式奖励来源。 */
    private static int grantAsh(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String raw = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "id");
        try {
            boolean added = com.dsh.eldencombat.server.skills.AshUnlocks.grant(player, raw);
            context.getSource().sendSuccess(() -> Component.literal(added
                    ? "已解锁战灰：" + raw + "（管理命令，非正式奖励来源）"
                    : "这件战灰此前已解锁（幂等，无变化）：" + raw), false);
            return Command.SINGLE_SUCCESS;
        } catch (IllegalArgumentException invalid) {
            context.getSource().sendFailure(Component.literal(invalid.getMessage()));
            return 0;
        }
    }

    private static int revokeAsh(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String raw = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "id");
        boolean removed = com.dsh.eldencombat.server.skills.AshUnlocks.revoke(player, raw);
        context.getSource().sendSuccess(() -> Component.literal(removed
                ? "已撤销战灰解锁：" + raw
                : "这件战灰本来就不在解锁列表里（无变化）：" + raw), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int testkit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        CombatConfig config = EldenCombat.config();

        player.addItem(new ItemStack(ModItems.STRAIGHT_SWORD.get()));
        player.addItem(new ItemStack(ModItems.GREAT_SWORD.get()));
        player.addItem(new ItemStack(ModItems.SHIELD.get()));
        player.addItem(new ItemStack(ModItems.TEST_FLASK.get(), config.drink.charges));

        com.dsh.eldencombat.server.PlayerAppearance.slim(player); // 触发一次外观缓存初始化，避免首帧空指针

        source.sendSuccess(() -> Component.literal("已发放粗块直剑 / 大剑 / 盾牌 / 药瓶 x"
                + config.drink.charges), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int summonMargit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        Vec3 look = player.getLookAngle();

        for (double distance = 5.0; distance >= 2.0; distance -= 0.5) {
            double x = player.getX() + look.x * distance;
            double z = player.getZ() + look.z * distance;
            for (int dy = 2; dy >= -3; dy--) {
                double y = player.getY() + dy;
                MargitEntity boss = ModEntities.MARGIT.get().create(level);
                if (boss == null) {
                    continue;
                }
                boss.moveTo(x, y, z, player.getYRot() + 180.0f, 0.0f);
                BlockPos ground = BlockPos.containing(x, y - 0.1, z);
                boolean standable = level.noCollision(boss) && level.getBlockState(ground).blocksMotion();
                if (!standable) {
                    continue;
                }
                boss.getPersistentData().putUUID(OWNER_KEY, player.getUUID());
                level.addFreshEntity(boss);
                source.sendSuccess(() -> Component.literal("已在玩家前方生成玛尔基特（登记为该玩家的测试 Boss）"), true);
                return Command.SINGLE_SUCCESS;
            }
        }
        source.sendFailure(Component.literal("前方没有可站立的空地，请换个位置再试"));
        return 0;
    }

    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        CombatConfig config = EldenCombat.config();
        UUID owner = player.getUUID();

        List<MargitEntity> owned = new ArrayList<>();
        for (ServerLevel level : source.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof MargitEntity margit
                        && margit.getPersistentData().hasUUID(OWNER_KEY)
                        && owner.equals(margit.getPersistentData().getUUID(OWNER_KEY))) {
                    owned.add(margit);
                }
            }
        }
        for (MargitEntity margit : owned) {
            margit.discard();
        }

        var state = player.getData(ModAttachments.COMBAT_STATE);
        state.ensureInit(EldenCombat.config());
        state.clearAction();
        state.guarding = false;

        source.sendSuccess(() -> Component.literal("已清理该玩家的测试 Boss " + owned.size()
                + " 个，并重置自身战斗动作（不补生命值与药瓶）"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        CombatConfig config = EldenCombat.reloadConfig();
        context.getSource().sendSuccess(() -> Component.literal("战斗配置已重新载入: "
                + EldenCombat.configPath() + "（Boss 与药瓶配置；玩家动作与原版一致）"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int selfTest(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<String> results = SelfTest.runAll(EldenCombat.config());
        long failures = SelfTest.failures(results);
        for (String line : results) {
            EldenCombat.LOGGER.info("[selftest] {}", line);
        }
        results.stream().filter(line -> line.startsWith("FAIL")).limit(10)
                .forEach(line -> source.sendFailure(Component.literal(line)));
        source.sendSuccess(() -> Component.literal("自检完成：" + results.size() + " 项，失败 " + failures
                + " 项（明细见日志）"), false);
        return failures == 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var state = player.getData(ModAttachments.COMBAT_STATE);
        state.ensureInit(EldenCombat.config());
        var cast = com.dsh.eldencombat.server.skills.SkillDriver.cast(player);
        String text = "引擎=纯原版 战斗模式=" + state.combatMode
                + " 精力=" + String.format("%.1f / %.1f", state.stamina, state.maxStamina(EldenCombat.config()))
                + " 战技=" + (cast.isCasting() ? cast.skillId() + " 段" + cast.stageIndex() + " " + cast.stage() : "无")
                + " 游戏模式=" + player.gameMode.getGameModeForPlayer()
                + " 喝药=" + player.isUsingItem();
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        for(var boss:player.serverLevel().getEntitiesOfClass(MargitEntity.class,player.getBoundingBox().inflate(24))) {
            String detail="Boss 距离="+String.format("%.2f",player.distanceTo(boss))+" 动作="+boss.actionClip()
                +" 起手中="+boss.isActionRunning()+" 目标="+(boss.getTarget()==null?"无":boss.getTarget().getName().getString());
            context.getSource().sendSuccess(()->Component.literal(detail),false);
        }
        return Command.SINGLE_SUCCESS;
    }
}
