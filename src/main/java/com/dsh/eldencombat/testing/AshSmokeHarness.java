package com.dsh.eldencombat.testing;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 普通战灰（§2.2）的引擎夹具：真实客户端 + 集成服务器。
 *
 * <p>**明确标注：这是引擎夹具，不是真人操作**。它验证的是"目录/绑定校验/运行时结算"这些
 * 服务端语义，走的是与按键路径**同一个**入口（{@code SkillCasting.request}，
 * {@code ServerCombat} 的 SKILL 分支就是调它）。
 *
 * <p>刻意不做的事：不用 {@code /give}、不提权、不解锁（§2.1 的奖励来源没接，所以拥有权
 * 这条链路由赐福菜单夹具单独验证）。施放键的真实按键路径由 {@code -PclientSmoke} 覆盖。
 */
public final class AshSmokeHarness {

    private static final String ASH = "eldencombat:stamp_upward_cut";
    private static final String WRONG_ASH = "eldencombat:heavy_thrust";
    /** 等"施放真的结束"最多用多少个**客户端** tick（服务端 tick 可能落后，见 step>45 的轮询注释）。 */
    private static final int ASH_CAST_WAIT = 200;

    private static boolean registered;
    private static int tick;
    private static boolean castEndPending = true;
    private static Zombie target;
    private static net.minecraft.world.item.ItemStack probeWeapon;
    private static int rewardUnlockCount;
    private static float healthBefore;
    private static float fpBefore;
    private static float fpCost;

    private AshSmokeHarness() {}

    public static synchronized void install() {
        if (registered) return;
        if (!Boolean.getBoolean("eldencombat.ashSmoke")) return;
        registered = true;
        NeoForge.EVENT_BUS.register(new AshSmokeHarness());
        EldenCombat.LOGGER.info("ASH_SMOKE_REGISTERED: client fixture active");
    }

    @SubscribeEvent public void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        var server = mc.getSingleplayerServer();
        int step = ++tick;
        if (step > 600) {
            EldenCombat.LOGGER.error("ASH_SMOKE_FAIL: timed out at step {}", step);
            mc.execute(mc::stop);
            return;
        }
        if (step == 1) server.execute(() -> guard(mc, () -> {
            var p = server.getPlayerList().getPlayers().getFirst();
            p.setGameMode(GameType.SURVIVAL);
            p.getInventory().clearContent();
            // §2.5 探针武器：带自定义名 + 强化 +2 + 质变 HEAVY，绑定战灰后这些都必须原样保留
            probeWeapon = new ItemStack(ModItems.GREAT_SWORD.get());
            probeWeapon.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    net.minecraft.network.chat.Component.literal("验收用大剑"));
            com.dsh.eldencombat.item.WeaponStacks.setState(probeWeapon,
                    new com.dsh.eldencombat.core.WeaponState(2, com.dsh.eldencombat.core.Affinity.HEAVY, false));
            p.setItemSlot(EquipmentSlot.MAINHAND, probeWeapon);
            // 先铺一块石头平台：否则玩家与目标会一起下落，判定距离每 tick 都在变（第一版夹具就栽在这）。
            var level = server.overworld();
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 5; z++) {
                    level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, 80, z),
                            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                    for (int y = 81; y <= 84; y++) {
                        level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x, y, z),
                                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    }
                }
            }
            p.connection.teleport(0, 81, 0, 0, 0);
            com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).setFp(100);
            com.dsh.eldencombat.server.skills.SkillServerEvents.sync(p);

            // 目录：非空、全部 implemented（未实现项不得进目录）
            var catalog = com.dsh.eldencombat.server.skills.AshWorkshop.catalog(p);
            require(!catalog.isEmpty(), "普通战灰目录不能为空（实际支持列表）");
            require(catalog.stream().allMatch(com.dsh.eldencombat.server.skills.AshWorkshop.Entry::implemented),
                    "目录里不允许出现未实现的占位条目");
            require(catalog.stream().anyMatch(e -> e.id().toString().equals(ASH)), "目录缺少 " + ASH);

            // 绑定校验：类型不匹配 / 不存在 / id 非法 都必须被拒
            require(bind(p, WRONG_ASH) == com.dsh.eldencombat.server.skills.AshBindings.Result.UNSUPPORTED_ITEM,
                    "大剑不该能绑刺击系战灰（" + WRONG_ASH + "）");
            require(bind(p, "eldencombat:no_such_ash") == com.dsh.eldencombat.server.skills.AshBindings.Result.UNKNOWN_SKILL,
                    "目录外的战灰必须被拒");
            require(bind(p, "bare_path") == com.dsh.eldencombat.server.skills.AshBindings.Result.INVALID_ID,
                    "裸路径 id 必须被拒");
            // §2.5：追忆武器的战技不允许被覆盖
            p.setItemSlot(EquipmentSlot.MAINHAND,
                    new ItemStack(com.dsh.eldencombat.registry.ModItems.SACRED_RELIC_SWORD.get()));
            require(bind(p, ASH) == com.dsh.eldencombat.server.skills.AshBindings.Result.LOCKED,
                    "追忆武器必须拒绝覆盖战技（应返回 LOCKED）");
            p.setItemSlot(EquipmentSlot.MAINHAND, probeWeapon);
            EldenCombat.LOGGER.info("ASH_SMOKE_LOCKED_PASS: 追忆武器拒绝覆盖战技");
            require(bind(p, ASH) == com.dsh.eldencombat.server.skills.AshBindings.Result.APPLIED,
                    "同类型武器应能绑定 " + ASH);
            var held = p.getMainHandItem();
            var resolved = com.dsh.eldencombat.server.skills.AshBindings.resolve(held);
            require(resolved != null && resolved.id().equals(ASH), "绑定后主手应解析出该战灰");
            // §2.5：绑定不得动武器的强化/质变/其它组件
            var weaponState = com.dsh.eldencombat.item.WeaponStacks.state(held);
            require(weaponState.upgradeLevel() == 2
                            && weaponState.affinity() == com.dsh.eldencombat.core.Affinity.HEAVY,
                    "绑定战灰不能改强化等级与质变（实际 +" + weaponState.upgradeLevel() + "/" + weaponState.affinity() + "）");
            require(held.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME) != null,
                    "绑定战灰不能丢原有物品组件（自定义名）");
            require(probeWeapon.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME) != null,
                    "组件必须写在同一个物品栈上（没有偷偷换成新栈）");
            EldenCombat.LOGGER.info("ASH_SMOKE_COMPONENT_PASS: 绑定后 强化+2 / 质变HEAVY / 自定义名 全部保留");
            fpCost = resolved.fpCost();

            target = net.minecraft.world.entity.EntityType.ZOMBIE.create(server.overworld());
            require(target != null, "无法创建测试目标");
            target.setNoAi(true);
            target.setPersistenceRequired();
            target.moveTo(0.5, 81, 2.5, 0, 0);
            server.overworld().addFreshEntity(target);
            target.setDeltaMovement(0, 0, 0);
            // 目标血量刻意给足：单段普通战灰大约 19~20 点伤害，用 20 血的僵尸做"不该秒杀"断言
            // 是在刀尖上跑（2026-10-13 实跑：同一份代码一次秒杀、一次 0 伤害）。给到 200 血后
            // "造成伤害但没秒杀"才是稳定可判的。
            var maxHealth = target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            require(maxHealth != null, "测试目标缺少最大生命属性");
            maxHealth.setBaseValue(200.0);
            target.setHealth(200f);
            healthBefore = target.getHealth();
            EldenCombat.LOGGER.info("ASH_SMOKE_SETUP: catalog={} ash={} fpCost={} 目标血量={}",
                    catalog.size(), ASH, fpCost, healthBefore);
        }));
        if (step == 5) server.execute(() -> guard(mc, () -> {
            var p = server.getPlayerList().getPlayers().getFirst();
            require(com.dsh.eldencombat.server.skills.SkillCasting.specForMainHand(p).id().equals(ASH),
                    "按键路径解析出的战灰应为主手绑定的那件");
            fpBefore = com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).fp();
            var result = com.dsh.eldencombat.server.skills.SkillCasting.request(p, p.level().getGameTime());
            require(result == com.dsh.eldencombat.server.skills.SkillTransactions.Result.STARTED,
                    "单段普通战灰应返回 STARTED，实际 " + result);
            var cast = com.dsh.eldencombat.server.skills.SkillDriver.cast(p);
            require(cast.isCasting() && cast.skillId().equals(ASH), "施放状态应指向该战灰");
            // §2.6 口径：每段实际扣费必须正好等于该段费用，不多不少
            float after = com.dsh.eldencombat.server.skills.SkillServerEvents.fp(p).fp();
            require(Math.abs((fpBefore - after) - fpCost) < 0.0001f,
                    "首段扣费应正好是 " + fpCost + "，实际 " + (fpBefore - after));
            EldenCombat.LOGGER.info("ASH_SMOKE_CAST_PASS: {} 起手成功，FP {}\u2192{}（扣 {}）",
                    ASH, Math.round(fpBefore), Math.round(after), Math.round(fpBefore - after));
        }));
        if (step == 45) server.execute(() -> guard(mc, () -> {
            var p = server.getPlayerList().getPlayers().getFirst();
            require(target != null && target.isAlive(), "测试目标应仍然存活（单段一击不该秒杀）");
            require(target.getHealth() < healthBefore,
                    "普通战灰运行时没有造成伤害（" + healthBefore + " -> " + target.getHealth()
                            + "；玩家 " + p.position() + " 朝向 " + p.getYRot()
                            + "，目标 " + target.position() + "）");
        }));
        // "施放结束"与"这一段真的打中了"都**不钉死在固定 step 上**：本夹具的 step 走**客户端 tick**
        // （ClientTickEvent.Post 驱动），而施放与判定由**服务端 tick** 推进。第 1 步铺 315 块石头会让
        // 服务端落后十几个 tick，于是 step 45 会比"判定落地"先到——2026-10-13 实跑就因此误报过
        // "施放应已结束"、也误报过"没有造成伤害"（同一份代码重跑结果不同，证实是夹具时钟耦合）。
        // 改成轮询：收招时就断言本段伤害与卸除，最多等 ASH_CAST_WAIT 个客户端 tick。
        if (step > 45) server.execute(() -> guard(mc, () -> {
            if (!castEndPending) return;
            var p = server.getPlayerList().getPlayers().getFirst();
            if (com.dsh.eldencombat.server.skills.SkillDriver.cast(p).isCasting()) {
                require(step - 45 < ASH_CAST_WAIT,
                        "施放迟迟不结束（服务端 tick 持续落后，已等 " + (step - 45) + " 个客户端 tick）");
                return;
            }
            castEndPending = false;
            require(target != null && target.isAlive(), "单段一击不该秒杀测试目标（200 血）");
            require(target.getHealth() < healthBefore,
                    "普通战灰运行时没有造成伤害（" + healthBefore + " -> " + target.getHealth()
                            + "；玩家 " + p.position() + " 朝向 " + p.getYRot()
                            + "，目标 " + target.position() + "）");
            EldenCombat.LOGGER.info("ASH_SMOKE_HIT_PASS: {} 单段判定造成 {} 点伤害（{} -> {}）",
                    ASH, healthBefore - target.getHealth(), healthBefore, target.getHealth());
            require(com.dsh.eldencombat.server.skills.AshBindings.unbind(p)
                            == com.dsh.eldencombat.server.skills.AshBindings.Result.APPLIED,
                    "已绑定的武器应能卸除战灰");
            require(com.dsh.eldencombat.server.skills.AshBindings.resolve(p.getMainHandItem()) == null,
                    "卸除后不应再解析出战灰");
            require(com.dsh.eldencombat.server.skills.SkillCasting.specForMainHand(p) == null,
                    "卸除后按键路径不应再找到战灰");
            EldenCombat.LOGGER.info("ASH_SMOKE_UNBIND_PASS: 卸除后主手不再解析出战灰");
        }));
        if (step == 60) server.execute(() -> guard(mc, () -> {
            // §2.1 玩法内来源：击杀配置表里的实体 → 解锁（这是唯一不靠管理命令的解锁路径）
            var p = server.getPlayerList().getPlayers().getFirst();
            var reward = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("eldencombat", "stamp_upward_cut");
            require(!com.dsh.eldencombat.server.skills.AshUnlocks.owns(p, reward),
                    "击杀前不该已经解锁（否则这条断言没意义）");
            killBoss(p);
            require(com.dsh.eldencombat.server.skills.AshUnlocks.owns(p, reward),
                    "击杀玛尔基特后应解锁 " + reward + "（玩法内来源）");
            rewardUnlockCount = com.dsh.eldencombat.server.skills.AshUnlocks.list(p).size();
            EldenCombat.LOGGER.info("ASH_REWARD_PASS: 击杀玛尔基特解锁 {}，当前共解锁 {} 件", reward, rewardUnlockCount);
        }));
        if (step == 80) server.execute(() -> guard(mc, () -> {
            // 幂等：再杀一次不得重复解锁（奖励来源可以安全重试）
            var p = server.getPlayerList().getPlayers().getFirst();
            killBoss(p);
            int now = com.dsh.eldencombat.server.skills.AshUnlocks.list(p).size();
            require(now == rewardUnlockCount, "重复击杀不得重复解锁（" + rewardUnlockCount + " -> " + now + "）");
            EldenCombat.LOGGER.info("ASH_REWARD_IDEMPOTENT_PASS: 第二次击杀没有新增解锁（仍 {} 件）", now);
        }));
        if (step == 140) {
            if (castEndPending) {
                EldenCombat.LOGGER.error("ASH_SMOKE_FAIL: 收招/卸除相位没在 {} 步内跑完（服务端 tick 落后过多）", step);
            } else {
                EldenCombat.LOGGER.info("ASH_SMOKE_COMPLETE: 目录/绑定校验/单段运行时/扣费/卸除 全部通过（引擎夹具）");
            }
            mc.execute(mc::stop);
        }
    }

    /** 生成一只玛尔基特并以"玩家击杀"的方式打死它（触发 LivingDeathEvent → 奖励来源）。 */
    private static void killBoss(net.minecraft.server.level.ServerPlayer player) {
        var boss = com.dsh.eldencombat.registry.ModEntities.MARGIT.get().create(player.serverLevel());
        require(boss != null, "无法创建玛尔基特");
        boss.moveTo(player.getX(), player.getY(), player.getZ() + 4, 0, 0);
        boss.setNoAi(true);
        player.serverLevel().addFreshEntity(boss);
        boss.invulnerableTime = 0;
        boss.hurt(player.damageSources().playerAttack(player), 10000f);
        require(!boss.isAlive(), "玛尔基特应已被击杀");
    }

    private static com.dsh.eldencombat.server.skills.AshBindings.Result bind(
            net.minecraft.server.level.ServerPlayer player, String ashId) {
        return com.dsh.eldencombat.server.skills.AshBindings.bind(player, ashId);
    }

    private static void guard(Minecraft mc, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException failure) {
            EldenCombat.LOGGER.error("ASH_SMOKE_FAIL", failure);
            mc.execute(mc::stop);
        }
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException("ASH_SMOKE_FAIL: " + message);
    }
}
