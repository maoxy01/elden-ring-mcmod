package com.dsh.eldencombat.client.grace;

import com.dsh.eldencombat.network.GraceMenuActionPayload;
import com.dsh.eldencombat.network.GraceMenuStatePayload;
import com.dsh.eldencombat.server.grace.GraceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import com.dsh.eldencombat.client.PresentationRoutes;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * 赐福菜单界面：左侧旅行列表、右侧战灰装配、底部休息 / 卸除。
 *
 * <p>纯展示 + 发请求：权限、拥有权、FP、武器兼容性全部由服务端重新判定。
 * 界面上的"可点击"只是省一次必然失败的请求，**不是授权**。
 * 全部用原版控件（§共同边界：不处理美术资源）。
 */
public final class GraceScreen extends AbstractContainerScreen<GraceMenu> {

    private static final int ROWS = 6;
    private static final int ROW_H = 18;
    private static final int COL_W = 150;
    private static final int LEFT_X = 8;
    private static final int RIGHT_X = 172;
    private static final int ROWS_Y = 32;
    private static final int PAGE_Y = ROWS_Y + ROWS * ROW_H;
    private static final int BUTTON_Y = 184;

    private static final int WHITE = 0xFFE8E8E8;
    private static final int GRAY = 0xFF9A9A9A;
    private static final int AMBER = 0xFFFFD060;
    private static final int RED = 0xFFFF6A6A;

    private GraceMenuStatePayload state = GraceMenuStatePayload.empty();
    private boolean fromServer;
    private int nodePage;
    private int ashPage;
    private boolean dirty;

    public GraceScreen(GraceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 330;
        this.imageHeight = 210;
    }

    /** 服务端快照到达。比对赐福 UUID，别把另一个赐福的快照画到这张界面上。 */
    public void apply(GraceMenuStatePayload payload) {
        if (payload == null || !payload.graceId().equals(menu.graceId())) return;
        // 只有"控件构成"真的变了才重建控件：服务端每 10 tick 会推一次刷新，
        // 每次都重建的话鼠标悬停会被反复清掉，界面看起来像在抖。
        boolean structureChanged = !sameStructure(state, payload);
        this.state = payload;
        this.fromServer = true;
        if (structureChanged) this.dirty = true;
    }

    /** 决定要不要重建控件的部分（notice / FP 这些只影响文字的不算）。 */
    private static boolean sameStructure(GraceMenuStatePayload a, GraceMenuStatePayload b) {
        return a.nodes().equals(b.nodes()) && a.ashes().equals(b.ashes())
                && a.heldItem().equals(b.heldItem()) && a.heldRejection().equals(b.heldRejection())
                && a.boundAsh().equals(b.boundAsh());
    }

    @Override protected void init() {
        super.init();
        if (!fromServer) {
            var latest = GraceScreenState.latest();
            if (latest != null && latest.graceId().equals(menu.graceId())) {
                state = latest;
                fromServer = true;
            }
        }
        nodePage = Math.min(nodePage, pages(state.nodes().size()) - 1);
        ashPage = Math.min(ashPage, pages(state.ashes().size()) - 1);
        buildTravel();
        buildAsh();
        addRenderableWidget(Button.builder(Component.literal("休息（恢复资源）"), pressed -> rest())
                .bounds(leftPos + LEFT_X, topPos + BUTTON_Y, COL_W, 20).build());
        if (!state.boundAsh().isEmpty()) {
            var remove = addRenderableWidget(Button.builder(Component.literal("卸除战灰"), pressed -> removeAsh())
                    .bounds(leftPos + RIGHT_X, topPos + BUTTON_Y, COL_W, 20).build());
            remove.active = fromServer && state.heldRejection().isEmpty();
        }
    }

    private void buildTravel() {
        List<GraceMenuStatePayload.Node> nodes = state.nodes();
        for (int row = 0; row < ROWS; row++) {
            int index = nodePage * ROWS + row;
            if (index >= nodes.size()) break;
            var node = nodes.get(index);
            addRenderableWidget(Button.builder(Component.literal(shorten(node.name(), 14) + "  " + node.dimension().getPath()),
                            pressed -> GraceClientActions.send(menu.containerId, GraceMenuActionPayload.TRAVEL, node.uid(), "", state.heldToken()))
                    .bounds(leftPos + LEFT_X, topPos + ROWS_Y + row * ROW_H, COL_W, ROW_H - 2).build());
        }
        int pageCount = pages(nodes.size());
        if (pageCount > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), pressed -> turnNodePage(-1))
                    .bounds(leftPos + LEFT_X, topPos + PAGE_Y, 40, 16).build());
            addRenderableWidget(Button.builder(Component.literal(">"), pressed -> turnNodePage(1))
                    .bounds(leftPos + LEFT_X + 44, topPos + PAGE_Y, 40, 16).build());
        }
    }

    private void buildAsh() {
        List<GraceMenuStatePayload.Ash> ashes = state.ashes();
        for (int row = 0; row < ROWS; row++) {
            int index = ashPage * ROWS + row;
            if (index >= ashes.size()) break;
            var ash = ashes.get(index);
            var button = addRenderableWidget(new AshButton(leftPos + RIGHT_X, topPos + ROWS_Y + row * ROW_H,
                    ash, pressed -> GraceClientActions.send(menu.containerId, AshUi.action(state.boundAsh()),
                            null, ash.id(), state.heldToken())));
            button.active = fromServer && AshUi.canApply(ash, state.heldRejection());
            button.setTooltip(Tooltip.create(AshUi.label(ash).copy().append("\n" + ash.id())
                    .append(state.heldRejection().isEmpty() ? "" : "\n" + state.heldRejection())));
        }
        int pageCount = pages(ashes.size());
        if (pageCount > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), pressed -> turnAshPage(-1))
                    .bounds(leftPos + RIGHT_X, topPos + PAGE_Y, 40, 16).build());
            addRenderableWidget(Button.builder(Component.literal(">"), pressed -> turnAshPage(1))
                    .bounds(leftPos + RIGHT_X + 44, topPos + PAGE_Y, 40, 16).build());
        }
    }

    private void turnNodePage(int delta) {
        nodePage = Math.max(0, Math.min(pages(state.nodes().size()) - 1, nodePage + delta));
        dirty = true;
    }

    private void turnAshPage(int delta) {
        ashPage = Math.max(0, Math.min(pages(state.ashes().size()) - 1, ashPage + delta));
        dirty = true;
    }

    private void rest() {
        GraceClientActions.send(menu.containerId, GraceMenuActionPayload.REST, null, "", state.heldToken());
    }

    private void removeAsh() {
        GraceClientActions.send(menu.containerId, GraceMenuActionPayload.REMOVE, null, "", state.heldToken());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (dirty) {
            dirty = false;
            rebuildWidgets();
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos - 4, topPos - 4, leftPos + imageWidth + 4, topPos + imageHeight + 4, 0xD0101010);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0x50101010);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 不调 super：本菜单没有槽位，原版会画"物品栏"标签。
        graphics.drawString(font, getTitle(), LEFT_X, 6, WHITE, false);
        graphics.drawString(font, "旅行 · 已发现 " + state.nodes().size() + " 处", LEFT_X, 18, WHITE, false);
        graphics.drawString(font, "战灰装配", RIGHT_X, 18, WHITE, false);
        if (!state.notice().isEmpty()) {
            int available = Math.max(24, imageWidth - 24 - font.width(getTitle()));
            String notice = fit(font, state.notice(), available);
            graphics.drawString(font, notice, imageWidth - 8 - font.width(notice), 6, AMBER, false);
        }
        if (state.nodes().isEmpty()) {
            graphics.drawString(font, "还没有发现的赐福", LEFT_X, ROWS_Y + 4, GRAY, false);
        }
        if (state.ashes().isEmpty()) {
            graphics.drawString(font, "普通战灰目录尚未注册", RIGHT_X, ROWS_Y + 4, GRAY, false);
            graphics.drawString(font, "（未实现项不可选不可绑定）", RIGHT_X, ROWS_Y + 14, GRAY, false);
        }
        graphics.drawString(font, fit(font, "手持：" + state.heldItem(), COL_W), LEFT_X, 164, GRAY, false);
        graphics.drawString(font, "FP " + state.fp() + "/" + state.maxFp(), RIGHT_X, 164, 0xFF9FC3F0, false);
        if (!state.heldRejection().isEmpty()) {
            graphics.drawString(font, fit(font, state.heldRejection(), imageWidth - 16), LEFT_X, 174, RED, false);
        }
    }

    private static String fit(Font font, String text, int width) {
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("…")) + "…";
    }

    /** Keep vanilla focus/hover/disabled backgrounds and narration; only lay out icon + text. */
    private static final class AshButton extends Button {
        private final String icon;

        private AshButton(int x, int y, GraceMenuStatePayload.Ash ash, OnPress onPress) {
            super(x, y, COL_W, ROW_H - 2, AshUi.label(ash), onPress, DEFAULT_NARRATION);
            icon = ash.id();
        }

        @Override public void renderString(GuiGraphics graphics, Font font, int color) {
            float tint = active ? 1f : .45f;
            graphics.setColor(tint, tint, tint, alpha);
            try {
                PresentationRoutes.drawIcon(graphics, icon, getX() + 2, getY());
            } finally {
                graphics.setColor(1, 1, 1, 1);
            }
            graphics.drawString(font, fit(font, getMessage().getString(), getWidth() - 24),
                    getX() + 20, getY() + (getHeight() - font.lineHeight) / 2, color, false);
        }
    }

    private static int pages(int count) {
        return Math.max(1, (count + ROWS - 1) / ROWS);
    }

    private static String shorten(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
