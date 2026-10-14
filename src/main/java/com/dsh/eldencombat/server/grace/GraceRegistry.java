package com.dsh.eldencombat.server.grace;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Only grace identity is stored here; Waystones owns locations, names and discovery. */
public final class GraceRegistry extends SavedData {
    private final Set<UUID> nodes=new HashSet<>();
    public static GraceRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(GraceRegistry::new,GraceRegistry::load,null),"eldencombat_grace");
    }
    private static GraceRegistry load(CompoundTag tag,HolderLookup.Provider provider) {
        var result=new GraceRegistry();
        for(var entry:tag.getList("nodes",10)) {
            var value=(CompoundTag)entry;
            if(value.hasUUID("uid"))result.nodes.add(value.getUUID("uid"));
        }
        return result;
    }
    public void register(UUID uid) { if(nodes.add(uid))setDirty(); }
    public void unregister(UUID uid) { if(nodes.remove(uid))setDirty(); }
    public boolean contains(UUID uid) { return nodes.contains(uid); }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider provider) {
        var list=new ListTag();
        for(var uid:nodes){var entry=new CompoundTag();entry.putUUID("uid",uid);list.add(entry);}
        tag.put("nodes",list);return tag;
    }
}
