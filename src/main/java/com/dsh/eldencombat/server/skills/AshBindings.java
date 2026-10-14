package com.dsh.eldencombat.server.skills;

import com.dsh.eldencombat.item.WeaponStacks;
import com.dsh.eldencombat.registry.ModDataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.Predicate;

/** Server integration entry for future menus/recipes. No client-selected skill packet. */
public final class AshBindings {
    public enum Result { APPLIED, BUSY, LOCKED, INVALID_ID, UNKNOWN_SKILL, UNIMPLEMENTED, UNSUPPORTED_ITEM, INCOMPATIBLE, ALREADY_BOUND, NOT_BOUND }
    public record Definition(ResourceLocation id, RemembranceSkillSpec spec, SkillDriver.Runtime runtime,
                             Set<ResourceLocation> items, Predicate<ItemStack> compatible) {
        public Definition { items=Set.copyOf(items); Objects.requireNonNull(id); Objects.requireNonNull(compatible); }
        public boolean implemented(){return spec!=null&&id.toString().equals(spec.id())&&runtime!=null&&!(runtime instanceof RemembranceRuntime);}
    }
    private static final Map<ResourceLocation,Definition> definitions=new LinkedHashMap<>();
    private AshBindings(){}
    /** Immutable catalog view for server menus; mutation still goes through register/update. */
    public static Map<ResourceLocation,Definition> definitions(){return Map.copyOf(definitions);}
    public static void register(Definition definition){
        if(definitions.putIfAbsent(definition.id(),definition)!=null)throw new IllegalArgumentException("Duplicate ash: "+definition.id());
        if(definition.implemented())SkillDriver.register(definition.id().toString(),definition.runtime());
    }
    public static RemembranceSkillSpec spec(String id){
        var key=ResourceLocation.tryParse(id);var definition=definitions.get(key);
        return definition!=null&&definition.implemented()?definition.spec():null;
    }
    public static ResourceLocation read(ItemStack stack){return stack.get(ModDataComponents.ASH_BINDING.get());}
    public static RemembranceSkillSpec resolve(ItemStack stack){
        var itemId=BuiltInRegistries.ITEM.getKey(stack.getItem());
        for(var fixed:RemembranceSkillSpec.defaultsById().values())
            if(itemId.equals(ResourceLocation.fromNamespaceAndPath("eldencombat",fixed.itemId())))return SkillDriver.spec(fixed.id());
        var definition=definitions.get(read(stack));
        return definition!=null&&check(stack,definition)==null?definition.spec():null;
    }
    private static Result check(ItemStack stack,Definition definition){
        if(!definition.implemented())return Result.UNIMPLEMENTED;
        if(stack.isEmpty()||!definition.items().contains(BuiltInRegistries.ITEM.getKey(stack.getItem())))return Result.UNSUPPORTED_ITEM;
        return definition.compatible().test(stack)?null:Result.INCOMPATIBLE;
    }
    public static Result bind(ServerPlayer player,String id){return update(player,id,false);}
    public static Result replace(ServerPlayer player,String id){return update(player,id,true);}
    private static Result editable(ServerPlayer player){
        if(SkillDriver.cast(player).isCasting()||player.getData(com.dsh.eldencombat.registry.ModAttachments.COMBAT_STATE).isBusy())return Result.BUSY;
        var stack=player.getMainHandItem();var definition=WeaponStacks.definition(stack);
        if(definition==null&&definitions.values().stream().noneMatch(value->value.items().contains(BuiltInRegistries.ITEM.getKey(stack.getItem()))))return Result.UNSUPPORTED_ITEM;
        var raw=stack.get(ModDataComponents.WEAPON_STATE.get());
        if((definition!=null&&(definition.remembrance()||definition.lockedArt()))||(raw!=null&&raw.artLocked()))return Result.LOCKED;
        return null;
    }
    private static Result update(ServerPlayer player,String id,boolean replace){
        if(!player.server.isSameThread())throw new IllegalStateException("Ash binding requires server thread");
        var rejection=editable(player);if(rejection!=null)return rejection;
        if(id==null||!id.contains(":"))return Result.INVALID_ID;
        var key=ResourceLocation.tryParse(id);if(key==null)return Result.INVALID_ID;
        var definition=definitions.get(key);if(definition==null)return Result.UNKNOWN_SKILL;
        var stack=player.getMainHandItem();rejection=check(stack,definition);if(rejection!=null)return rejection;
        if(!replace&&read(stack)!=null)return Result.ALREADY_BOUND;
        stack.set(ModDataComponents.ASH_BINDING.get(),key);player.containerMenu.broadcastChanges();return Result.APPLIED;
    }
    /** 菜单预览用：主手当前能否编辑战灰（null = 可以）。与 bind/replace/unbind 走同一套检查，不改状态。 */
    public static Result editability(ServerPlayer player){
        if(!player.server.isSameThread())throw new IllegalStateException("Ash binding requires server thread");
        return editable(player);
    }
    /**
     * 菜单兼容性预览：**当前主手**能不能用这件战灰（null = 可以）。与 bind/replace 走同一套检查，
     * 但只读、不改任何状态——界面上的"能不能装"必须和服务端真正提交时的判定同源，
     * 否则就会出现"显示可装、点了被拒"。
     */
    public static Result checkHeld(ServerPlayer player, ResourceLocation ashId){
        if(!player.server.isSameThread())throw new IllegalStateException("Ash binding requires server thread");
        if(ashId==null)return Result.INVALID_ID;
        var definition=definitions.get(ashId);
        if(definition==null)return Result.UNKNOWN_SKILL;
        return check(player.getMainHandItem(),definition);
    }
    public static Result unbind(ServerPlayer player){
        if(!player.server.isSameThread())throw new IllegalStateException("Ash binding requires server thread");
        var rejection=editable(player);if(rejection!=null)return rejection;
        var stack=player.getMainHandItem();if(read(stack)==null)return Result.NOT_BOUND;
        stack.remove(ModDataComponents.ASH_BINDING.get());player.containerMenu.broadcastChanges();return Result.APPLIED;
    }
}
