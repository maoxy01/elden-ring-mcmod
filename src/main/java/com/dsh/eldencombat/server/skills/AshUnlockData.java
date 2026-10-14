package com.dsh.eldencombat.server.skills;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.core.AshUnlockSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 战灰解锁的服务端持久化：**一头存档、一份权威**。
 *
 * <p>放服务端 {@link SavedData}（而不是玩家实体附件）的理由：解锁是**账号级进度**，
 * 不该随实体销毁/克隆丢失，也不该被"死亡掉落"之类的规则影响；
 * 按 UUID 直接查询/撤销也更简单。值一律是规范化后的完整 namespaced ID（见 {@link AshUnlockSet}）。
 *
 * <p>死亡与重登语义：key 是玩家 UUID、存在世界存档里，所以死亡不丢、重登即恢复；
 * 不需要额外的 copyOnDeath 处理，也就没有"靠死亡克隆刷战灰"的口子。
 */
public final class AshUnlockData extends SavedData {

    public static final String FILE_ID = "eldencombat_ash_unlocks";

    private final Map<UUID,AshUnlockSet> players = new HashMap<>();

    public static AshUnlockData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new Factory<>(AshUnlockData::new, AshUnlockData::load, null), FILE_ID);
    }

    private static AshUnlockData load(CompoundTag tag, HolderLookup.Provider provider) {
        AshUnlockData data = new AshUnlockData();
        ListTag entries = tag.getList("players", 10);
        int dropped = 0;
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.hasUUID("uuid")) {
                dropped++;
                continue;
            }
            ListTag ashes = entry.getList("ashes", 8);
            List<String> raw = new ArrayList<>(ashes.size());
            for (int j = 0; j < ashes.size(); j++) raw.add(ashes.getString(j));
            AshUnlockSet.LoadResult result = AshUnlockSet.load(raw);
            dropped += result.dropped();
            if (result.set().size() > 0) data.players.put(entry.getUUID("uuid"), result.set());
        }
        if (dropped > 0) {
            EldenCombat.LOGGER.warn("[ash] 战灰解锁存档里有 {} 条无法识别的记录，已丢弃（其余正常载入）", dropped);
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag entries = new ListTag();
        for (Map.Entry<UUID,AshUnlockSet> player : players.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", player.getKey());
            ListTag ashes = new ListTag();
            for (String id : player.getValue().ids()) ashes.add(StringTag.valueOf(id));
            entry.put("ashes", ashes);
            entries.add(entry);
        }
        tag.put("players", entries);
        return tag;
    }

    public boolean contains(UUID player, String ashId) {
        AshUnlockSet set = players.get(player);
        return set != null && set.owns(ashId);
    }

    /** 幂等授予；返回是否新增（新增才标脏）。 */
    public boolean grant(UUID player, String ashId) {
        AshUnlockSet set = players.computeIfAbsent(player, key -> new AshUnlockSet());
        if (!set.grant(ashId)) return false;
        setDirty();
        return true;
    }

    public boolean revoke(UUID player, String ashId) {
        AshUnlockSet set = players.get(player);
        if (set == null || !set.revoke(ashId)) return false;
        if (set.size() == 0) players.remove(player);
        setDirty();
        return true;
    }

    public List<String> list(UUID player) {
        AshUnlockSet set = players.get(player);
        return set == null ? List.of() : set.ids();
    }
}
