package com.dsh.eldencombat.registry;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.crafting.WeaponForgingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 配方序列化器注册（§2.1）。
 *
 * <p>配方类型仍然落在 {@code minecraft:smithing} 上（{@link WeaponForgingRecipe} 实现了
 * {@code SmithingRecipe}），所以**原版锻造台、原版菜单、JEI 的锻造分类都能直接用**，
 * 不需要自己写 GUI 或容器同步。
 */
public final class ModRecipes {

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, EldenCombat.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<WeaponForgingRecipe>> WEAPON_FORGING =
            RECIPE_SERIALIZERS.register("weapon_forging", WeaponForgingRecipe.Serializer::new);

    private ModRecipes() {
    }
}
