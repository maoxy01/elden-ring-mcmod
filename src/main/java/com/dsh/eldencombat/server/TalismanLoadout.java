package com.dsh.eldencombat.server;

import com.dsh.eldencombat.core.TalismanIds;
import com.dsh.eldencombat.core.TalismanRules;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 护符容器（玩家附件）：**存完整 {@link ItemStack}**，不是一个 id 字符串。
 *
 * <p>§3.2 的核心修复：旧版只存 {@code getKey().getPath()}，于是附魔、自定义名、质变等
 * 物品组件在装备时被丢掉。现在放进去的就是原样那一件，卸下时退回的还是同一件。
 *
 * <p>槽位数由 {@link TalismanRules#activeSlots()} 决定，但**容器只增不减地容纳已存在的物品**：
 * 解析存档时按 {@code max(存档长度, 当前槽数)} 对齐，绝不因为改配置把物品吃掉。
 * 配置变小后多出来的槽由 {@link Talismans#refresh} 退回背包并记日志。
 */
public final class TalismanLoadout {

    /** 落盘/读档：可选 ItemStack 列表（空槽用空栈表示）。 */
    public static final Codec<TalismanLoadout> CODEC =
            ItemStack.OPTIONAL_CODEC.listOf().xmap(TalismanLoadout::new, TalismanLoadout::raw);

    private final List<ItemStack> slots;

    /** 新玩家 / 没数据：按当前配置开槽。 */
    public TalismanLoadout() {
        this(List.of());
    }

    public TalismanLoadout(List<ItemStack> stored) {
        int size = Math.max(TalismanRules.current().activeSlots(), stored == null ? 0 : stored.size());
        List<ItemStack> copy = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ItemStack stack = stored != null && i < stored.size() ? stored.get(i) : ItemStack.EMPTY;
            copy.add(stack == null ? ItemStack.EMPTY : stack);
        }
        this.slots = copy;
    }

    private List<ItemStack> raw() {
        return Collections.unmodifiableList(new ArrayList<>(slots));
    }

    public int size() {
        return slots.size();
    }

    public ItemStack get(int slot) {
        if (slot < 0 || slot >= slots.size()) {
            return ItemStack.EMPTY;
        }
        return slots.get(slot);
    }

    public void set(int slot, ItemStack stack) {
        if (slot < 0 || slot >= slots.size()) {
            throw new IndexOutOfBoundsException("护符槽 " + slot + " 不存在（共 " + slots.size() + " 格）");
        }
        slots.set(slot, stack == null ? ItemStack.EMPTY : stack);
    }

    /** 全部格子的只读视图（含空格）。 */
    public List<ItemStack> stacks() {
        return raw();
    }

    /** 已装备件数（非空槽）。 */
    public int equippedCount() {
        int count = 0;
        for (ItemStack stack : slots) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public boolean isEmpty() {
        return equippedCount() == 0;
    }

    /** 第一个空格；没有则 -1。 */
    public int firstEmpty() {
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /** 命中同一件护符的槽位（按物品判等，不按组件）；没有则 -1。 */
    public int indexOf(ItemStack other) {
        if (other == null || other.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < slots.size(); i++) {
            if (!slots.get(i).isEmpty() && slots.get(i).getItem() == other.getItem()) {
                return i;
            }
        }
        return -1;
    }

    /** 从槽位取出（取出后该格为空）。 */
    public ItemStack take(int slot) {
        ItemStack stack = get(slot);
        if (!stack.isEmpty()) {
            slots.set(slot, ItemStack.EMPTY);
        }
        return stack;
    }

    public void clear() {
        for (int i = 0; i < slots.size(); i++) {
            slots.set(i, ItemStack.EMPTY);
        }
    }

    /**
     * 把容器对齐到 {@code limit} 格：不足补空格，超出时**只删空尾巴**。
     *
     * <p>容器自己绝不销毁物品：超出部分必须先由调用方 {@link #take(int)} 取走
     * （{@code Talismans.reconcile} 会退回背包），否则 {@code limit} 之外的格子会被保留，
     * 返回的实际格数会大于 {@code limit}——这是有意的安全网，宁可多留一格，也不静默吃掉物品。
     *
     * @return 实际格数
     */
    public int resize(int limit) {
        int target = Math.max(1, limit);
        while (slots.size() < target) {
            slots.add(ItemStack.EMPTY);
        }
        while (slots.size() > target && slots.get(slots.size() - 1).isEmpty()) {
            slots.remove(slots.size() - 1);
        }
        return slots.size();
    }

    /** 已装备护符的完整 ID（规范化；无效物品仍按其注册名给出，便于日志与拒绝原因）。 */
    public List<String> ids() {
        List<String> ids = new ArrayList<>();
        for (ItemStack stack : slots) {
            if (!stack.isEmpty()) {
                ids.add(idOf(stack));
            }
        }
        return ids;
    }

    /** 物品的护符 ID = 完整注册名（{@code namespace:path}）。 */
    public static String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return TalismanIds.normalize(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    @Override
    public String toString() {
        return "TalismanLoadout" + slots.stream().map(s -> s.isEmpty() ? "-" : idOf(s)).toList();
    }
}
