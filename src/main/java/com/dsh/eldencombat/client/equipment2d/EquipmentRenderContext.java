package com.dsh.eldencombat.client.equipment2d;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;

/**
 * Carries the <em>real</em> render caller down to the low-level {@code ItemRenderer.render} entry.
 *
 * <p>Contract §10.1/§10.4: the eight-argument {@code render} has no holder parameter, so the actual
 * holder, hand, level and seed must come from the real upstream call — never from
 * {@code Minecraft.player}, which would make every third-person observer draw the local player's bow
 * string. Every vanilla item render funnels through {@code ItemRenderer.renderStatic(LivingEntity, …)}
 * ({@code ItemInHandRenderer.renderItem} for both first and third person), so that overload is the
 * bridge: it is entered with the true holder and left again on return.
 *
 * <p>Two properties make this safe rather than a leaked static:
 * <ul>
 *   <li>The frame is a stack, so nested renders (an item drawn while drawing another item) restore in
 *       order instead of clobbering one another.</li>
 *   <li>Reading is <em>identity checked</em>: {@link #current} only hands back the top frame when it
 *       was pushed for this exact stack object and this exact display context. A frame that somehow
 *       survived an aborted render therefore cannot leak another item's usage state into a later
 *       draw; it reads as "no holder", which zeroes every active state.</li>
 *   <li>The depth is capped, so an aborted render can never grow the stack without bound.</li>
 * </ul>
 */
public final class EquipmentRenderContext {

    /** One in-flight item render, as reported by the real caller. */
    public record Frame(LivingEntity holder, InteractionHand hand, ItemStack stack,
                        ItemDisplayContext context, Level level, int seed) {
    }

    private static final int MAX_DEPTH = 64;
    private static volatile int depthCapHits;

    private static final ThreadLocal<ArrayDeque<Frame>> FRAMES =
            ThreadLocal.withInitial(ArrayDeque::new);

    private EquipmentRenderContext() {
    }

    /** Called from the upstream overload that has the real holder. */
    public static void enter(LivingEntity holder, ItemStack stack, ItemDisplayContext context,
                             Level level, int seed) {
        if (stack == null || stack.isEmpty() || context == null) {
            return;
        }
        ArrayDeque<Frame> frames = FRAMES.get();
        if (frames.size() >= MAX_DEPTH) {
            // Only reachable if a render aborted without unwinding; the frame is not required for a
            // correct draw, so dropping it keeps the stack bounded and every state zeroed.
            depthCapHits++;
            frames.clear();
        }
        frames.push(new Frame(holder, handOf(context), stack, context, level, seed));
    }

    public static void exit() {
        ArrayDeque<Frame> frames = FRAMES.get();
        if (!frames.isEmpty()) {
            frames.pop();
        }
        if (frames.isEmpty()) {
            FRAMES.remove();
        }
    }

    /**
     * The frame that belongs to this exact stack and context, or {@code null}.
     *
     * <p>Reference equality on the stack is deliberate: a value comparison would reintroduce the
     * cross-item leak this check exists to prevent.
     */
    public static Frame current(ItemStack stack, ItemDisplayContext context) {
        ArrayDeque<Frame> frames = FRAMES.get();
        Frame frame = frames.peek();
        if (frame == null || frame.stack() != stack || frame.context() != context) {
            return null;
        }
        return frame;
    }

    /** Depth of the current thread's frame stack; used by the tests and the capability report. */
    public static int depth() {
        return FRAMES.get().size();
    }

    public static int depthCapHits() {
        return depthCapHits;
    }

    /** Left/right hand is encoded by the display context, which is what the caller actually passes. */
    private static InteractionHand handOf(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
    }
}
