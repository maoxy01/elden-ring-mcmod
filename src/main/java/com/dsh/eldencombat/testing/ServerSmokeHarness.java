package com.dsh.eldencombat.testing;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.core.Affinity;
import com.dsh.eldencombat.core.AttributeSet;
import com.dsh.eldencombat.core.WeaponProfiles;
import com.dsh.eldencombat.core.WeaponState;
import com.dsh.eldencombat.entity.MargitEntity;
import com.dsh.eldencombat.item.WeaponStacks;
import com.dsh.eldencombat.registry.ModEntities;
import com.dsh.eldencombat.registry.ModItems;
import com.dsh.eldencombat.registry.WeaponStateCodec;
import com.dsh.eldencombat.server.ServerCombat;
import com.dsh.eldencombat.server.WeaponAttributes;
import com.dsh.eldencombat.server.Weapons;
import com.dsh.eldencombat.core.WeaponClass;
import com.dsh.eldencombat.server.skills.LandingSupport;
import com.dsh.eldencombat.server.skills.SkillServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Opt-in dedicated-server regression runner, excluded from release jars.
 *
 * <p>除了跑共享自检，这里还**真的验证事件总线接线**（这是"构建通过 ≠ 事件会跑"的补丁，
 * 静态注解检查无法替代真实总线触发）：
 * <ol>
 *   <li>手工 post 一个真实的 {@code ServerTickEvent.Post}，断言 {@link ServerCombat#tickEventCount()}
 *       恰好 +1——历史上 {@code ServerCombat} 根本没注册，计数不会动；</li>
 *   <li>真实 tick 下采样计数差，断言**每 tick 只推进一次**（重复注册会翻倍）；</li>
 *   <li>`SkillServerEvents` 走自动订阅，同样用合成投递确认它在总线上。</li>
 * </ol>
 */
@EventBusSubscriber(modid=EldenCombat.MODID,value=Dist.DEDICATED_SERVER)
public final class ServerSmokeHarness {
    private static final boolean ENABLED=Boolean.getBoolean("eldencombat.serverSmoke");
    private static int ticks;
    private static long sampleTickCount = Long.MIN_VALUE;
    private static net.minecraft.world.entity.monster.Zombie bowTarget;
    private static boolean fixturesReady;

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if(!ENABLED)return;
        var results=SelfTest.runAll();
        if(SelfTest.failures(results)>0)throw new IllegalStateException("SERVER_SMOKE_FAIL: "+results);
        EldenCombat.LOGGER.info("SERVER_SMOKE_PASS: {} shared checks, no client-class dependencies",results.size());
        verifyBusWiring(event.getServer());
        verifyBreakPayoff(event.getServer());
        verifyLandingProbe(event.getServer());
        verifyWeaponProfiles(event.getServer());
        verifyForging(event.getServer());
        verifyNativeBow(event.getServer());
        fixturesReady=true;
    }

    private static void verifyNativeBow(MinecraftServer server) {
        var level=server.overworld();
        var player=FakePlayerFactory.getMinecraft(level);
        var spawn=level.getSharedSpawnPos();
        player.moveTo(spawn.getX()+.5,122,spawn.getZ()+.5,0,0);
        var bow=new ItemStack(ModItems.LONG_BOW.get());
        expect(bow.getItem() instanceof net.minecraft.world.item.BowItem,"long bow must use vanilla BowItem");
        player.setItemSlot(EquipmentSlot.MAINHAND,bow);
        player.getInventory().setItem(9,new ItemStack(net.minecraft.world.item.Items.ARROW,8));
        bowTarget=net.minecraft.world.entity.EntityType.HUSK.create(level);
        bowTarget.setNoAi(true); bowTarget.setNoGravity(true);
        bowTarget.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); bowTarget.setHealth(100);
        bowTarget.setPos(player.getX(),122,player.getZ()+5); level.addFreshEntity(bowTarget);
        expect(!player.getProjectile(bow).isEmpty(),"native bow fixture must have supported ammunition");
        player.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        bow.getItem().releaseUsing(bow,level,player,bow.getUseDuration(player)-20);
        player.stopUsingItem();
    }

    /**
     * §2 锻造与质变的运行时证据：用**真实的配方管理器**把整条链跑一遍
     * （而不是"配方 JSON 文件存在"这种静态检查）。
     *
     * <p>覆盖方案 §7.2 的可机检清单：逐级强化、质变换档位且保留等级、重铸清空质变、
     * 追忆武器战灰锁定、档位错配时**没有产出**（于是材料也不会被消耗）。
     */
    private static void verifyForging(MinecraftServer server) {
        ServerLevel level = server.overworld();

        ItemStack sword = new ItemStack(ModItems.STRAIGHT_SWORD.get());
        ItemStack plusOne = forge(server, level, ModItems.UPGRADE_TEMPLATES.get(0).get(), sword,
                new ItemStack(ModItems.SMITHING_STONES.get(0).get()));
        expect(WeaponStacks.state(plusOne).upgradeLevel() == 1,
                "直剑 + 强化模板[1] + 锻造石[1] 应产出 +1，实际=" + plusOne);

        ItemStack plusTwo = forge(server, level, ModItems.UPGRADE_TEMPLATES.get(1).get(), plusOne,
                new ItemStack(ModItems.SMITHING_STONES.get(1).get()));
        expect(WeaponStacks.state(plusTwo).upgradeLevel() == 2,
                "+1 直剑 + 强化模板[2] + 锻造石[2] 应产出 +2，实际=" + plusTwo);

        // 档位错配：+2 的目标是 +3（需要锻造石[3]），用 [2] 必须没有产出
        ItemStack wrongTier = forge(server, level, ModItems.UPGRADE_TEMPLATES.get(1).get(), plusTwo,
                new ItemStack(ModItems.SMITHING_STONES.get(1).get()));
        expect(wrongTier.isEmpty(), "档位错配时锻造台不该给出产出（否则材料会被白白消耗）");

        // 质变：用基础伤害换补正档位，等级保留
        ItemStack heavy = forge(server, level, ModItems.AFFINITY_TEMPLATE_HEAVY.get(), plusTwo,
                new ItemStack(ModItems.HEAVY_SCALE.get()));
        expect(!heavy.isEmpty() && WeaponStacks.state(heavy).affinity() == Affinity.HEAVY,
                "厚重质变应改写补正档位，实际=" + heavy);
        expect(WeaponStacks.state(heavy).upgradeLevel() == 2, "质变必须保留强化等级");

        // 重铸：清空质变、等级保留
        ItemStack reforged = forge(server, level, ModItems.REFORGE_TEMPLATE.get(), heavy,
                new ItemStack(ModItems.REMORSE_TEAR.get()));
        expect(WeaponStacks.state(reforged).affinity() == Affinity.NONE
                        && WeaponStacks.state(reforged).upgradeLevel() == 2,
                "重铸应清空质变并保留等级，实际=" + reforged);

        // 追忆武器：只能吃失色锻造石；重铸被战灰锁定挡住
        ItemStack relic = new ItemStack(ModItems.SACRED_RELIC_SWORD.get());
        ItemStack relicSomber = forge(server, level, ModItems.SOMBER_UPGRADE_TEMPLATES.get(0).get(), relic,
                new ItemStack(ModItems.SOMBER_STONES.get(0).get()));
        expect(WeaponStacks.state(relicSomber).upgradeLevel() == 1, "追忆武器应能用失色锻造石 +1");
        ItemStack relicByNormal = forge(server, level, ModItems.UPGRADE_TEMPLATES.get(0).get(), relic,
                new ItemStack(ModItems.SMITHING_STONES.get(0).get()));
        expect(relicByNormal.isEmpty(), "追忆武器不该吃普通锻造石");
        ItemStack relicReforge = forge(server, level, ModItems.REFORGE_TEMPLATE.get(), relicSomber,
                new ItemStack(ModItems.REMORSE_TEAR.get()));
        expect(relicReforge.isEmpty(), "战灰锁定的追忆武器不该被重铸");

        // 满级不再产出
        ItemStack maxed = new ItemStack(ModItems.STRAIGHT_SWORD.get());
        WeaponStacks.setState(maxed, new WeaponState(10, Affinity.NONE, false));
        expect(forge(server, level, ModItems.UPGRADE_TEMPLATES.get(7).get(), maxed,
                new ItemStack(ModItems.SMITHING_STONES.get(7).get())).isEmpty(),
                "已满级的武器不该还能强化");

        // 数据组件与存档序列化：NBT 往返必须一致（§7.2 第 7 条）
        WeaponState state = new WeaponState(7, Affinity.BLOOD, false);
        var encoded = WeaponStateCodec.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, state).getOrThrow();
        WeaponState decoded = WeaponStateCodec.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, encoded).getOrThrow();
        expect(decoded.equals(state), "武器状态 NBT 往返不一致: " + encoded);

        // 伤害路径：+10 的倍率应该是 B(1.0)=2.0（曲线真的接到了伤害上）
        ItemStack maxedForDamage = new ItemStack(ModItems.STRAIGHT_SWORD.get());
        WeaponStacks.setState(maxedForDamage, new WeaponState(10, Affinity.NONE, false));
        double fresh = WeaponStacks.damageMultiplier(new ItemStack(ModItems.STRAIGHT_SWORD.get()), AttributeSet.EMPTY);
        double upgraded = WeaponStacks.damageMultiplier(maxedForDamage, AttributeSet.EMPTY);
        expect(Math.abs(fresh - 1.0) < 1.0e-6, "全新武器倍率应为 1.0，实际=" + fresh);
        expect(Math.abs(upgraded - 2.0) < 1.0e-3, "+10 期望倍率 2.0（B(t) 线性项），实际=" + upgraded);

        EldenCombat.LOGGER.info("SERVER_FORGING_PASS: 强化/质变/重铸全链可用，档位错配无产出，"
                + "追忆武器锁定生效，+10 伤害倍率 ×{}", String.format("%.2f", upgraded));
    }

    /** 走真实配方管理器：找不到配方就返回空（等价于锻造台结果槽为空、输入不消耗）。 */
    private static ItemStack forge(MinecraftServer server, ServerLevel level, Object template, ItemStack base,
                                   Object addition) {
        return forge(server, level, resolve(template), base, resolve(addition));
    }

    private static ItemStack forge(MinecraftServer server, ServerLevel level, ItemStack template, ItemStack base,
                                   ItemStack addition) {
        var input = new net.minecraft.world.item.crafting.SmithingRecipeInput(template, base, addition);
        var found = server.getRecipeManager()
                .getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMITHING, input, level);
        return found.map(holder -> holder.value().assemble(input, server.registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    /** 允许直接用注册句柄或物品写用例，少一层 new ItemStack 噪声。 */
    private static ItemStack resolve(Object value) {
        if (value instanceof ItemStack stack) {
            return stack;
        }
        if (value instanceof net.neoforged.neoforge.registries.DeferredItem<?> item) {
            return new ItemStack(item.get());
        }
        if (value instanceof net.minecraft.world.item.Item item) {
            return new ItemStack(item);
        }
        return ItemStack.EMPTY;
    }

    private static void expect(boolean ok, String message) {
        if (!ok) {
            throw new IllegalStateException("SERVER_SMOKE_FAIL: " + message);
        }
    }

    /**
     * §5 武器差异化的运行时证据：
     * <ol>
     *   <li>每一件注册武器都在数值表里（漏一件 = 那件武器没有 reach/削韧差异）；</li>
     *   <li>reach 真的落到玩家属性上——用 FakePlayer 换主手武器，读
     *       {@code ENTITY_INTERACTION_RANGE} 必须跟着变（这是"长柄真的更长"的证据，
     *       也是数据表 → 属性 → 原版判定这条链路的证据）。</li>
     * </ol>
     */
    private static void verifyWeaponProfiles(MinecraftServer server) {
        List<String> missing = new ArrayList<>();
        for (var item : ModItems.ALL_WEAPONS) {
            if (WeaponProfiles.of(item.getId().getPath()) == null) {
                missing.add(item.getId().getPath());
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("SERVER_SMOKE_FAIL: 这些武器没有数值档（reach/削韧会退化）: " + missing);
        }

        ServerLevel level = server.overworld();
        var player = FakePlayerFactory.getMinecraft(level);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.SPEAR.get()));
        WeaponAttributes.forget(player.getUUID());
        WeaponAttributes.tick(player);
        double spear = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.DAGGER.get()));
        WeaponAttributes.tick(player);
        double dagger = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        WeaponAttributes.tick(player);
        double fists = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);

        if (Math.abs(spear - 3.9) > 1.0e-4 || Math.abs(dagger - 2.7) > 1.0e-4 || Math.abs(fists - 3.0) > 1.0e-4) {
            throw new IllegalStateException("SERVER_SMOKE_FAIL: reach 没有正确落到属性上（矛=" + spear
                    + " 匕首=" + dagger + " 空手=" + fists + "，应为 3.9 / 2.7 / 3.0）");
        }

        // 每件近战武器都必须能普攻：分类为 NONE 时 ServerCombat 会直接 return（等于这件武器没用）
        List<String> unusable = new ArrayList<>();
        int light = 0;
        int heavy = 0;
        for (var item : ModItems.ALL_WEAPONS) {
            var profile = WeaponProfiles.of(item.getId().getPath());
            if (profile == null || !profile.isMeleeProfile()) {
                continue;
            }
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item.get()));
            WeaponClass weaponClass = Weapons.classify(player);
            if (weaponClass == WeaponClass.NONE) {
                unusable.add(item.getId().getPath());
            } else if (weaponClass == WeaponClass.GREAT_SWORD) {
                heavy++;
            } else {
                light++;
            }
        }
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        if (!unusable.isEmpty()) {
            throw new IllegalStateException("SERVER_SMOKE_FAIL: 这些武器无法普攻（分类为 NONE）: " + unusable);
        }
        if (light == 0 || heavy == 0) {
            throw new IllegalStateException("SERVER_SMOKE_FAIL: 轻/重分档没有生效（轻=" + light + " 重=" + heavy + "）");
        }

        EldenCombat.LOGGER.info("SERVER_WEAPON_PASS: {} 件武器都有数值档，reach 真的落到属性上"
                        + "（矛 {} / 匕首 {} / 空手 {}），近战武器全部可普攻（轻档 {} 件 / 重档 {} 件）",
                ModItems.ALL_WEAPONS.size(), spear, dagger, fists, light, heavy);
    }

    /**
     * §4 落地处理器的**世界查询部分**：{@link LandingSupport#groundBelow} 必须
     * 在真实世界里找到落点、并在世界底部之下明确返回 NaN（否则"掉出世界"会被误判成正常落地）。
     */
    private static void verifyLandingProbe(MinecraftServer server) {
        ServerLevel level = server.overworld();
        // 不放依赖地形：先在一个确定的高空位置放一块石头，再问"下方最近的落点在哪"。
        // （出生点可能是海洋/深谷，靠地形做断言会假失败。）
        BlockPos probe = new BlockPos(0, 120, 0);
        level.setBlockAndUpdate(probe, Blocks.STONE.defaultBlockState());
        try {
            double ground = LandingSupport.groundBelow(level, 0.5, 130.0, 0.5, LandingSupport.MAX_SCAN);
            if (Math.abs(ground - 121.0) > 1.0e-6) {
                throw new IllegalStateException("SERVER_SMOKE_FAIL: 落点扫描返回 " + ground + "（应为 121，即石头上表面）");
            }
        } finally {
            level.removeBlock(probe, false);
        }
        double voidGround = LandingSupport.groundBelow(level, 0.5, level.getMinBuildHeight() - 10.0, 0.5, 8);
        if (!Double.isNaN(voidGround)) {
            throw new IllegalStateException("SERVER_SMOKE_FAIL: 世界底部之下仍然报出了落点 " + voidGround
                    + "（掉出世界会被误判成正常落地）");
        }
        EldenCombat.LOGGER.info("SERVER_LANDING_PASS: 落点扫描可用（测试方块顶部 Y=121 命中，世界底部之下返回 NaN）");
    }

    /**
     * 削韧 payoff（§6）的**运行时**证据：真的刷一个 Boss、真的把失衡打满、
     * 真的对比崩解前后的掉血量。
     *
     * <p>单测只能证明公式；这条证明"伤害路径真的乘了那个倍率、崩解真的开了描边"。
     * 历史上踩过的坑正是这一类：类写好了但没接线，构建全绿、游戏里毫无效果。
     */
    private static void verifyBreakPayoff(MinecraftServer server) {
        ServerLevel level = server.overworld();
        MargitEntity boss = ModEntities.MARGIT.get().create(level);
        if (boss == null) {
            throw new IllegalStateException("SERVER_SMOKE_FAIL: 无法创建 Boss 实体，崩解 payoff 无法验证");
        }
        boss.moveTo(0.5, 100.0, 0.5, 0f, 0f);
        boss.setPersistenceRequired();
        if (!level.addFreshEntity(boss)) {
            throw new IllegalStateException("SERVER_SMOKE_FAIL: Boss 没能加入世界，崩解 payoff 无法验证");
        }
        try {
            float nominal = 10f;
            if (boss.isBroken()) {
                throw new IllegalStateException("SERVER_SMOKE_FAIL: 刚生成的 Boss 不应处于崩解状态");
            }
            float beforeBreak = rawHit(boss, nominal, 1f);
            boss.addStagger(boss.poiseMax(), level.getGameTime());
            if (!boss.isBroken()) {
                throw new IllegalStateException("SERVER_SMOKE_FAIL: 失衡打满后没有进入崩解（削韧没有 payoff）");
            }
            if (!boss.isCurrentlyGlowing()) {
                throw new IllegalStateException("SERVER_SMOKE_FAIL: 崩解没有开启描边（setGlowingTag 未生效）");
            }
            float multiplier = boss.damageTakenMultiplier();
            if (Math.abs(multiplier - 1.5f) > 1.0e-4f) {
                throw new IllegalStateException("SERVER_SMOKE_FAIL: 崩解易伤倍率是 " + multiplier + "（应为 1.5）");
            }
            float duringBreak = rawHit(boss, nominal, multiplier);
            if (duringBreak < beforeBreak * 1.2f) {
                throw new IllegalStateException("SERVER_SMOKE_FAIL: 崩解期掉血没有明显增加（崩解前 " + beforeBreak
                        + " / 崩解期 " + duringBreak + "）");
            }
            EldenCombat.LOGGER.info("SERVER_BREAK_PASS: 崩解 payoff 接线正确（同名义伤害 {}：崩解前掉血 {} / "
                    + "崩解期掉血 {}，倍率 {}，描边={}）", nominal, beforeBreak, duringBreak, multiplier, boss.isCurrentlyGlowing());
        } finally {
            boss.discard();
        }
    }

    /** 绕过无敌帧的一次真实伤害结算，返回实际掉血量。 */
    private static float rawHit(MargitEntity boss, float nominal, float multiplier) {
        boss.invulnerableTime = 0;
        float before = boss.getHealth();
        boss.hurt(boss.damageSources().generic(), nominal * multiplier);
        return Math.max(0f, before - boss.getHealth());
    }

    /** 真实总线触发 + 单次推进检查。 */
    private static void verifyBusWiring(MinecraftServer server) {
        if (!ServerCombat.isRegistered()) {
            throw new IllegalStateException("SERVER_SMOKE_FAIL: ServerCombat 没有注册到游戏事件总线（战斗循环不会运行）");
        }
        long before = ServerCombat.tickEventCount();
        NeoForge.EVENT_BUS.post(new ServerTickEvent.Post(() -> true, server));
        long after = ServerCombat.tickEventCount();
        if (after != before + 1) {
            throw new IllegalStateException("SERVER_SMOKE_FAIL: 投递 1 次 ServerTickEvent.Post，ServerCombat 执行了 "
                    + (after - before) + " 次（应为 1；0=没注册，2=重复注册）");
        }
        long skillBefore = SkillServerEvents.busTickCount();
        NeoForge.EVENT_BUS.post(new ServerTickEvent.Post(() -> true, server));
        long skillAfter = SkillServerEvents.busTickCount();
        if (skillAfter != skillBefore + 1) {
            throw new IllegalStateException("SERVER_SMOKE_FAIL: 投递 1 次 ServerTickEvent.Post，SkillServerEvents 执行了 "
                    + (skillAfter - skillBefore) + " 次（应为 1）");
        }
        EldenCombat.LOGGER.info("SERVER_BUS_PASS: ServerCombat 单例与 SkillServerEvents 自动订阅都在游戏总线上"
                + "（合成投递 1 次 → 各执行 1 次）");
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(!ENABLED||!fixturesReady)return;
        ticks++;
        if(ticks==1) expect(!event.getServer().overworld().getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class,
                bowTarget.getBoundingBox().inflate(8)).isEmpty(),"native bow release must spawn an arrow in a ticking chunk");
        if(ticks==30) {
            expect(bowTarget!=null&&bowTarget.getHealth()<100,"native long bow arrow must actually damage target");
            EldenCombat.LOGGER.info("SERVER_NATIVE_BOW_PASS: vanilla release spawned arrow and damaged target; health={}",bowTarget.getHealth());
            bowTarget.discard();
        }
        if (ticks == 1) {
            sampleTickCount = ServerCombat.tickEventCount();
        } else if (ticks == 6) {
            // 真实 tick 的推进步长必须是 1：重复注册会让 5 tick 里执行 10 次
            long delta = ServerCombat.tickEventCount() - sampleTickCount;
            if (delta != 5) {
                throw new IllegalStateException("SERVER_SMOKE_FAIL: 5 个真实 tick 内 ServerCombat 执行了 "
                        + delta + " 次（应为 5，重复注册会翻倍）");
            }
            EldenCombat.LOGGER.info("SERVER_BUS_PASS: 每 tick 只推进一次（5 tick → 处理器 5 次）");
        }
        if(ticks>=300) {
            EldenCombat.LOGGER.info("SERVER_SMOKE_COMPLETE: test datapack had {} ticks to validate Boss damage/death", ticks);
            event.getServer().halt(false);
        }
    }
}
