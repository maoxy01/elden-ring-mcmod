package com.dsh.eldencombat.crafting;

import com.dsh.eldencombat.core.Affinity;
import com.dsh.eldencombat.core.WeaponDefinition;
import com.dsh.eldencombat.core.WeaponState;
import com.dsh.eldencombat.item.WeaponStacks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;

/**
 * 武器锻造配方（《成长路径与手感收口方案》§2.1）：**不新增槽位、不新增方块**，
 * 用原版锻造台的三个槽位表达三种操作：
 *
 * <table>
 *   <tr><th>模板</th><th>槽位构成</th><th>产出</th></tr>
 *   <tr><td>强化模板 N</td><td>武器 + 锻造石</td><td>等级 +1</td></tr>
 *   <tr><td>质变模板 X</td><td>武器 + 质变素材</td><td>改写质变，等级保留</td></tr>
 *   <tr><td>重铸模板</td><td>武器 + 悔恨之泪</td><td>清空质变，等级保留</td></tr>
 * </table>
 *
 * <p>与 {@code SmithingTransformRecipe} 的区别只有一处，但很关键：
 * **产出是动态的**——它由"底座武器当前的成长状态"算出来，而不是 JSON 里写死的 ItemStack。
 * 所以一个档位只需要一条配方，而不是 41 件武器 × 10 档。
 *
 * <p>原子性来自原版锻造台语义：{@code matches} 不通过（等级已满 / 档位不对 / 战灰锁定）时结果槽为空，
 * 玩家拿不到产出，输入也就不会被消耗。
 */
public class WeaponForgingRecipe implements SmithingRecipe {

    public enum Operation {
        /** 强化：等级 +1 */
        UPGRADE,
        /** 质变：改写补正档位，等级保留 */
        AFFINITY,
        /** 重铸：清空质变，等级保留（战灰锁定时不生效） */
        REFORGE
    }

    /**
     * 普通锻造石档位 → 目标等级（方案 §2.6 的材料梯度）：
     * 石[1]–[4] 覆盖 +0→+5、石[5]–[6] 覆盖 +5→+8、石[7]–[8] 覆盖 +8→+10。
     */
    private static final int[] NORMAL_STONE_FOR_TARGET = {1, 2, 3, 4, 4, 5, 6, 6, 7, 8};

    private final net.minecraft.world.item.crafting.Ingredient template;
    private final net.minecraft.world.item.crafting.Ingredient base;
    private final net.minecraft.world.item.crafting.Ingredient addition;
    private final Operation operation;
    private final boolean somber;
    private final int tier;
    private final String affinity;

    public WeaponForgingRecipe(net.minecraft.world.item.crafting.Ingredient template,
                               net.minecraft.world.item.crafting.Ingredient base,
                               net.minecraft.world.item.crafting.Ingredient addition,
                               Operation operation, boolean somber, int tier, String affinity) {
        this.template = template;
        this.base = base;
        this.addition = addition;
        this.operation = operation;
        this.somber = somber;
        this.tier = Math.max(1, tier);
        this.affinity = affinity == null ? "NONE" : affinity;
    }

    public Operation operation() {
        return operation;
    }

    public boolean somber() {
        return somber;
    }

    public int tier() {
        return tier;
    }

    public String affinity() {
        return affinity;
    }

    public net.minecraft.world.item.crafting.Ingredient template() {
        return template;
    }

    public net.minecraft.world.item.crafting.Ingredient base() {
        return base;
    }

    public net.minecraft.world.item.crafting.Ingredient addition() {
        return addition;
    }

    /** 目标等级需要的普通锻造石档位（越界返回 -1）。 */
    public static int normalStoneForTargetLevel(int targetLevel) {
        if (targetLevel < 1 || targetLevel > NORMAL_STONE_FOR_TARGET.length) {
            return -1;
        }
        return NORMAL_STONE_FOR_TARGET[targetLevel - 1];
    }

    /** 该操作对这件武器是否合法（不合法 → 锻造台结果槽留空 → 输入不消耗）。 */
    public boolean legalFor(ItemStack baseStack) {
        WeaponDefinition definition = WeaponStacks.definition(baseStack);
        if (definition == null) {
            return false;
        }
        WeaponState state = WeaponStacks.state(baseStack);
        return switch (operation) {
            case UPGRADE -> definition.remembrance() == somber
                    && state.canUpgrade(definition)
                    && requiredTier(state.upgradeLevel() + 1) == tier;
            case AFFINITY -> !state.artLocked() && !Affinity.byName(affinity).isNone();
            case REFORGE -> !state.artLocked() && !state.affinity().isNone();
        };
    }

    private int requiredTier(int targetLevel) {
        return somber ? targetLevel : normalStoneForTargetLevel(targetLevel);
    }

    @Override
    public boolean matches(SmithingRecipeInput input, net.minecraft.world.level.Level level) {
        return template.test(input.template()) && base.test(input.base()) && addition.test(input.addition())
                && legalFor(input.base());
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input, net.minecraft.core.HolderLookup.Provider registries) {
        ItemStack baseStack = input.base();
        WeaponDefinition definition = WeaponStacks.definition(baseStack);
        if (definition == null) {
            return ItemStack.EMPTY;
        }
        WeaponState state = WeaponStacks.state(baseStack);
        WeaponState next = switch (operation) {
            case UPGRADE -> state.upgraded(definition);
            case AFFINITY -> state.withAffinity(Affinity.byName(affinity));
            case REFORGE -> state.reforged(definition);
        };
        ItemStack result = baseStack.copyWithCount(1);
        WeaponStacks.setState(result, next);
        return result;
    }

    /**
     * 动态配方没有固定产物：这里返回空，让 JEI/配方书展示"模板 + 武器 + 素材"。
     * 锻造台本身不依赖它（{@code SmithingMenu} 用 {@link #assemble} 现算结果）。
     */
    @Override
    public ItemStack getResultItem(net.minecraft.core.HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isTemplateIngredient(ItemStack stack) {
        return template.test(stack);
    }

    @Override
    public boolean isBaseIngredient(ItemStack stack) {
        return base.test(stack);
    }

    @Override
    public boolean isAdditionIngredient(ItemStack stack) {
        return addition.test(stack);
    }

    @Override
    public boolean isIncomplete() {
        return template.hasNoItems() || base.hasNoItems() || addition.hasNoItems();
    }

    @Override
    public net.minecraft.world.item.crafting.RecipeSerializer<?> getSerializer() {
        return com.dsh.eldencombat.registry.ModRecipes.WEAPON_FORGING.get();
    }

    /**
     * 序列化器：JSON 里写"模板 / 底座 / 素材 + 操作 + 档位"，
     * 产出永远由底座武器的成长状态现算，所以一个档位一条配方就够。
     */
    public static class Serializer implements net.minecraft.world.item.crafting.RecipeSerializer<WeaponForgingRecipe> {

        private static final com.mojang.serialization.MapCodec<WeaponForgingRecipe> CODEC =
                com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
                        net.minecraft.world.item.crafting.Ingredient.CODEC.fieldOf("template")
                                .forGetter(WeaponForgingRecipe::template),
                        net.minecraft.world.item.crafting.Ingredient.CODEC.fieldOf("base")
                                .forGetter(WeaponForgingRecipe::base),
                        net.minecraft.world.item.crafting.Ingredient.CODEC.fieldOf("addition")
                                .forGetter(WeaponForgingRecipe::addition),
                        com.mojang.serialization.Codec.STRING.optionalFieldOf("operation", "UPGRADE")
                                .forGetter(recipe -> recipe.operation().name()),
                        com.mojang.serialization.Codec.BOOL.optionalFieldOf("somber", false)
                                .forGetter(WeaponForgingRecipe::somber),
                        com.mojang.serialization.Codec.INT.optionalFieldOf("tier", 1)
                                .forGetter(WeaponForgingRecipe::tier),
                        com.mojang.serialization.Codec.STRING.optionalFieldOf("affinity", "NONE")
                                .forGetter(WeaponForgingRecipe::affinity)
                ).apply(instance, (template, base, addition, operation, somber, tier, affinity) ->
                        new WeaponForgingRecipe(template, base, addition, parseOperation(operation), somber, tier, affinity)));

        // StreamCodec.composite 最多到 6 个字段，这里 7 个字段，直接手写（与 SmithingTransformRecipe 同款做法）
        private static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf,
                WeaponForgingRecipe> STREAM_CODEC =
                net.minecraft.network.codec.StreamCodec.of(Serializer::toNetwork, Serializer::fromNetwork);

        private static WeaponForgingRecipe.Operation parseOperation(String name) {
            try {
                return WeaponForgingRecipe.Operation.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return WeaponForgingRecipe.Operation.UPGRADE;
            }
        }

        private static void toNetwork(net.minecraft.network.RegistryFriendlyByteBuf buffer, WeaponForgingRecipe recipe) {
            net.minecraft.world.item.crafting.Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.template());
            net.minecraft.world.item.crafting.Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.base());
            net.minecraft.world.item.crafting.Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.addition());
            buffer.writeUtf(recipe.operation().name());
            buffer.writeBoolean(recipe.somber());
            buffer.writeVarInt(recipe.tier());
            buffer.writeUtf(recipe.affinity());
        }

        private static WeaponForgingRecipe fromNetwork(net.minecraft.network.RegistryFriendlyByteBuf buffer) {
            var template = net.minecraft.world.item.crafting.Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            var base = net.minecraft.world.item.crafting.Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            var addition = net.minecraft.world.item.crafting.Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            String operation = buffer.readUtf();
            boolean somber = buffer.readBoolean();
            int tier = buffer.readVarInt();
            String affinity = buffer.readUtf();
            return new WeaponForgingRecipe(template, base, addition, parseOperation(operation), somber, tier, affinity);
        }

        @Override
        public com.mojang.serialization.MapCodec<WeaponForgingRecipe> codec() {
            return CODEC;
        }

        @Override
        public net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf,
                WeaponForgingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
