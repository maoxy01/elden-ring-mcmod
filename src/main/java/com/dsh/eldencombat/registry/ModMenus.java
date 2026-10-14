package com.dsh.eldencombat.registry;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.server.grace.GraceMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 菜单类型注册。
 *
 * <p>赐福菜单是**0 槽位的服务菜单**：不搬运物品，只把"你在哪个赐福、点了什么"交给服务端重新校验，
 * 所以额外数据里只有赐福 UUID 与名字，且客户端读到的那份只用于展示——
 * 服务端侧的那份由 {@code MenuProvider} 在打开时捕获。
 */
public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, EldenCombat.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<GraceMenu>> GRACE = MENUS.register("grace",
            () -> IMenuTypeExtension.create((windowId, inventory, buffer) ->
                    new GraceMenu(windowId, inventory, buffer.readUUID(), buffer.readUtf(128))));

    private ModMenus() {
    }
}
