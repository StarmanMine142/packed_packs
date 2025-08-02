package io.github.fishstiz.packed_packs.gui.components.pack;

import com.google.common.collect.ImmutableList;
import io.github.fishstiz.fidgetz.gui.components.AbstractDynamicList;
import io.github.fishstiz.fidgetz.gui.components.ContainerEventHandlerPatch;
import io.github.fishstiz.fidgetz.gui.components.FidgetzButton;
import io.github.fishstiz.fidgetz.gui.renderables.ColoredRect;
import io.github.fishstiz.fidgetz.gui.renderables.sprites.Sprite;
import io.github.fishstiz.fidgetz.gui.shapes.Size;
import io.github.fishstiz.fidgetz.util.GuiUtil;
import io.github.fishstiz.packed_packs.compat.ModAdditions;
import io.github.fishstiz.packed_packs.gui.components.events.PackListEventListener;
import io.github.fishstiz.packed_packs.util.ResourceUtil;
import io.github.fishstiz.packed_packs.util.constants.Theme;
import io.github.fishstiz.packed_packs.gui.components.events.*;
import io.github.fishstiz.packed_packs.util.pack.FolderPack;
import io.github.fishstiz.packed_packs.util.pack.PackAssets;
import net.minecraft.Util;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.server.packs.repository.Pack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;

import static com.google.common.primitives.Ints.contains;
import static io.github.fishstiz.fidgetz.util.GuiUtil.playClickSound;
import static io.github.fishstiz.packed_packs.util.InputUtil.*;
import static io.github.fishstiz.packed_packs.util.lang.IntsUtil.hasGap;
import static io.github.fishstiz.packed_packs.util.lang.ObjectsUtil.*;

public abstract class PackListBase<T extends PackListBase<T>.Entry> extends AbstractDynamicList<T> implements PackList {
    protected static final int OFFSET_Y = 2;
    protected static final int ITEM_HEIGHT = 32;
    protected static final int ROW_GAP = 3;
    protected final PackAssets packAssets;
    protected final List<Pack> packs = new ArrayList<>();
    private final List<Pack> queried = new ArrayList<>();
    private final List<Pack> selection = new ArrayList<>();
    private final PackListEventListener listener;
    private final Query query;

    protected PackListBase(PackAssets packAssets, PackListEventListener listener) {
        super(ITEM_HEIGHT, DEFAULT_SCROLLBAR_OFFSET, OFFSET_Y, ROW_GAP);

        this.query = new Query(packAssets.getDirectory());
        this.packAssets = packAssets;
        this.listener = listener;
        this.queryPacks();
    }

    protected abstract @NotNull T createEntry(Pack pack, int index);

    public @Nullable T getEntry(@Nullable Pack pack) {
        if (pack == null) return null;
        for (T entry : this.children()) {
            if (entry.pack == pack) return entry;
        }
        return null;
    }

    protected void refreshEntries() {
        this.clearEntries();
        for (int i = 0; i < this.queried.size(); i++) {
            this.addEntry(this.createEntry(this.queried.get(i), i));
        }
        T focused = this.getFocused();
        if (focused != null && !this.queried.contains(focused.pack)) {
            this.setFocused(null);
        }
        this.clampScrollAmount();
    }

    public boolean isQueried() {
        return this.query.isQuerying();
    }

    protected void queryPacks() {
        this.queried.clear();
        this.queried.addAll(this.packs);
        this.query.apply(this.queried);
        this.selection.retainAll(this.queried);
        this.refreshEntries();
    }

    public void scrollToTop() {
        this.setScrollAmount(0);
    }

    public void reload(Collection<Pack> packs) {
        this.packs.clear();

        for (Pack pack : packs) {
            if (pack != null && !this.packs.contains(pack)) {
                this.packs.add(pack);
            }
        }

        this.setFocused(null);
        this.clearSelection();
        this.queryPacks();
        this.scrollToTop();
    }

    @Override
    public @NotNull ImmutableList<Pack> copyPacks() {
        return ImmutableList.copyOf(this.packs);
    }

    @Override
    public @NotNull ImmutableList<Pack> copySelection() {
        return ImmutableList.copyOf(this.selection);
    }

    @Override
    public @NotNull Query copyQuery() {
        return this.query.copy();
    }

    protected List<Pack> orderSelection(List<Pack> selection) {
        List<Pack> sortedSelection = new ArrayList<>(selection);
        sortedSelection.retainAll(this.queried);
        sortedSelection.sort(Comparator.comparingInt(this.queried::indexOf));
        return sortedSelection;
    }

    public ImmutableList<Pack> getOrderedSelection() {
        return ImmutableList.copyOf(this.orderSelection(this.selection));
    }

    protected int[] getIndicesFromSelection(List<Pack> selection) {
        int[] selectionIndices = new int[selection.size()];
        for (int i = 0; i < selection.size(); i++) {
            int index = this.queried.indexOf(selection.get(i));
            selectionIndices[i] = index;
        }
        return selectionIndices;
    }

    protected int[] getSelectionIndices() {
        return this.getIndicesFromSelection(this.selection);
    }

    @Override
    public void clearSelection() {
        this.selection.clear();
    }

    private void refresh() {
        this.clearSelection();
        this.queryPacks();
        this.scrollToTop();
    }

    public void sort(Query.SortOption sort) {
        if (this.query.setSort(sort)) {
            this.refresh();
        }
    }

    public void hideIncompatible(boolean hideIncompatible) {
        if (this.query.setHideIncompatible(hideIncompatible)) {
            this.clearSelection();
            this.queryPacks();
        }
    }

    public void search(@NotNull String search) {
        if (this.query.setSearch(search)) {
            this.refresh();
        }
    }

    private void addPack(Pack pack) {
        if (pack != null && !this.packs.contains(pack)) {
            int index = 0;
            for (Pack p : this.packs) {
                if (!p.isFixedPosition()) break;
                index++;
            }
            this.packs.add(index, pack);
        }
    }

    @Override
    public void add(Pack pack) {
        this.addPack(pack);
        this.queryPacks();
    }

    @Override
    public void addAll(List<Pack> packs) {
        for (Pack pack : packs) {
            this.addPack(pack);
        }
        this.queryPacks();
    }

    @Override
    public boolean move(Pack pack, int to) {
        if (pack.isFixedPosition()) return false;

        int from = this.packs.indexOf(pack);
        if (from == -1 || to < 0 || to >= this.packs.size() || from == to) {
            return false;
        }

        this.packs.remove(from);
        this.packs.add(to, pack);
        this.queryPacks();
        this.setFocused(this.getEntry(pack));
        return true;
    }

    @Override
    public boolean moveAll(List<Pack> selection, int to) {
        if (selection == null || selection.isEmpty() || to < 0 || to > this.packs.size()) {
            return false;
        }
        if (!new HashSet<>(this.packs).containsAll(selection)) {
            return false;
        }

        int index = to;
        for (Pack pack : selection) {
            int from = this.packs.indexOf(pack);
            if (from < to) index--;
        }

        this.packs.removeAll(selection);
        this.packs.addAll(index, selection);
        this.queryPacks();

        return true;
    }

    private void removePack(Pack pack) {
        if (this.packs.remove(pack) && this.selection.remove(pack)) {
            this.setFocused(null);
        }
    }

    @Override
    public void remove(Pack pack) {
        this.removePack(pack);
        this.queryPacks();
    }

    @Override
    public void removeAll(List<Pack> packs) {
        for (Pack pack : packs) {
            this.removePack(pack);
        }
        this.queryPacks();
    }

    public @Nullable Pack getLastSelected() {
        return !this.selection.isEmpty() ? this.selection.getLast() : null;
    }

    @Override
    public @Nullable T getSelected() {
        return this.getLastSelected() != null ? this.getEntry(this.getLastSelected()) : super.getSelected();
    }

    public boolean isSelected(Pack pack) {
        return this.selection.contains(pack);
    }

    public void scrollToLastSelected() {
        Optional.ofNullable(this.getEntry(this.getLastSelected())).ifPresent(this::ensureVisible);
    }

    @Override
    public void unselect(Pack pack) {
        this.selection.remove(pack);

        T entry = this.getEntry(pack);
        if (entry == this.getFocused()) {
            this.setFocused(null);
        }
        if (entry == this.getSelected()) {
            this.setSelected(null);
        }
    }

    @Override
    public void select(Pack pack) {
        if (pack != null && this.queried.contains(pack)) {
            this.selection.remove(pack);
            this.selection.add(pack);
            T entry = this.getEntry(pack);
            this.setFocused(entry);
            this.setSelected(entry);
        }
    }

    @Override
    public void selectAll(List<Pack> packs) {
        for (Pack pack : packs) {
            this.select(pack);
        }
    }

    @Override
    public void selectExclusive(Pack pack) {
        this.clearSelection();
        this.select(pack);
    }

    public void selectToggle(Pack pack) {
        if (this.isSelected(pack)) {
            this.unselect(pack);
        } else {
            this.select(pack);
        }
    }

    @Override
    public void selectRange(Pack pack) {
        Pack selectionStart = this.getLastSelected();
        int lastSelectedIndex = this.queried.indexOf(selectionStart);
        int selectedPackIndex = this.queried.indexOf(pack);
        int[] selectionIndices = this.getSelectionIndices();
        Arrays.sort(selectionIndices);

        if (!(contains(selectionIndices, -1) || hasGap(selectionIndices, true)) && selectionIndices.length > 0) {
            if (selectionIndices[0] == lastSelectedIndex) {
                selectionStart = this.queried.get(selectionIndices[selectionIndices.length - 1]);
            } else if (selectionIndices[selectionIndices.length - 1] == lastSelectedIndex) {
                selectionStart = this.queried.get(selectionIndices[0]);
            }
        }

        int startIndex = this.queried.indexOf(selectionStart);
        if (selectedPackIndex != -1 && startIndex != -1) {
            this.clearSelection();
            for (int i = Math.min(selectedPackIndex, startIndex); i <= Math.max(selectedPackIndex, startIndex); i++) {
                Pack selected = this.queried.get(i);
                if (selected != pack) this.select(selected);
            }
        }

        this.select(pack);
    }

    public void transferAll() {
        List<Pack> payload = new ArrayList<>();

        for (int i = this.queried.size() - 1; i >= 0; i--) {
            Pack pack = this.queried.get(i);
            if (this.isTransferable(pack)) {
                payload.add(pack);
            }
        }

        if (!payload.isEmpty()) {
            this.sendEvent(new RequestTransferEvent(this, payload, this.getLastSelected()));
        }
    }

    protected void sendEvent(PackListEvent event) {
        this.listener.onEvent(event);
    }

    protected abstract @Nullable List<Pack> handleDrop(PackList source, ImmutableList<Pack> selection, Pack trigger, double mouseX, double mouseY);

    @Override
    public final void drop(PackList source, ImmutableList<Pack> selection, Pack trigger, double mouseX, double mouseY) {
        List<Pack> dropped = this.handleDrop(source, selection, trigger, mouseX, mouseY);
        if (dropped != null && !dropped.isEmpty()) {
            if (source != this) {
                this.sendEvent(new DropEvent(source, this, dropped));
            } else {
                this.sendEvent(new MoveEvent(this, dropped, trigger));
            }
        }
    }

    private void openFolder(FolderPack folderPack) {
        this.sendEvent(new FolderOpenEvent(this, folderPack));
    }

    private @Nullable ComponentPath handleArrowNavigation(FocusNavigationEvent.ArrowNavigation arrowNavigation) {
        T entry = switch (arrowNavigation.direction()) {
            case UP -> this.getPreviousEntry();
            case DOWN -> this.getNextEntry();
            default -> null;
        };
        if (entry != null) {
            if (isRangeModifierActive()) {
                this.selectRange(entry.pack);
            } else {
                this.selectExclusive(entry.pack);
            }
            this.sendEvent(new SelectionEvent(this));
            this.ensureVisible(entry);
            return ComponentPath.path(entry, this);
        }
        this.setFocused(null);
        return null;
    }

    @Override
    public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent event) {
        if (!this.isFocused()) {
            Pack lastSelected = this.getLastSelected();
            T entry = null;
            if (lastSelected != null) {
                entry = this.getEntry(lastSelected);
            } else if (!this.children().isEmpty()) {
                entry = this.getFirstElement();
            }
            if (entry != null) {
                this.select(entry.pack);
                this.ensureVisible(entry);
                return ComponentPath.path(entry, this);
            }
        } else if (event instanceof FocusNavigationEvent.ArrowNavigation arrowNavigation) {
            return this.handleArrowNavigation(arrowNavigation);
        } else {
            this.setFocused(null);
        }
        return null;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isTransfer(keyCode, modifiers)) {
            Entry entry = this.getEntry(this.getLastSelected());
            if (entry != null && entry.transfer()) {
                playClickSound();
            }
            return entry != null;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return this.isValidClickButton(button) && super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderListItems(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderListItems(guiGraphics, mouseX, mouseY, partialTick);

        Entry focused = this.getFocused();
        if (focused != null && focused.isFocused()) {
            int outlineTop = focused.getY() - Entry.BACKGROUND_OFFSET * 2;
            int outlineHeight = focused.getHeight() + Entry.BACKGROUND_OFFSET * 4;
            guiGraphics.renderOutline(focused.getX(), outlineTop, focused.getWidth(), outlineHeight, Theme.WHITE.getARGB());
        }
    }

    @Override
    public void replaceState(@NotNull Snapshot snapshot) {
        this.packs.clear();

        for (Pack pack : snapshot.packs()) {
            if (pack != null && !this.packs.contains(pack)) {
                this.packs.add(pack);
            }
        }

        this.query.update(snapshot.query());
        this.queryPacks();

        this.clearSelection();
        for (Pack selected : snapshot.selection()) {
            this.select(selected);
        }
    }

    public abstract class Entry extends AbstractDynamicList<T>.Entry implements PackList.Entry, ContainerEventHandlerPatch {
        private static final double DRAG_THRESHOLD = 1.0;
        private static final int DOUBLE_CLICK_DELTA_MS = 200;
        private static final Sprite FOLDER_BUTTON_SPRITE = new Sprite(ResourceUtil.getIcon("hamburger"), Size.of16());
        protected static final int SPACING = 2;
        protected static final int BACKGROUND_OFFSET = 1;
        protected static final ColoredRect OVERLAY = new ColoredRect(Theme.WHITE.withAlpha(0.25F));
        protected static final ColoredRect SELECTED_OVERLAY = new ColoredRect(Theme.BLUE_500.withAlpha(0.25F));
        protected final List<GuiEventListener> children = new ArrayList<>();
        protected final List<Renderable> renderables = new ArrayList<>();
        protected final List<Renderable> topRenderables = new ArrayList<>();
        protected final List<NarratableEntry> narratables = new ArrayList<>();
        protected final Pack pack;
        private final PackWidget packWidget;
        private FidgetzButton<Void> folderWidget;
        private long lastClickTime = 0;
        private MouseSelectionState mouseSelectionState = MouseSelectionState.INACTIVE;

        protected Entry(Pack pack, int index) {
            super(index);

            this.pack = pack;
            this.packWidget = this.addRenderableWidget(new PackWidget(
                    this.pack,
                    PackListBase.this.packAssets,
                    this.getX(),
                    PackListBase.this.getRowTop(this.index),
                    this.getWidth(),
                    PackListBase.this.itemHeight,
                    SPACING
            ));

            if (this.pack instanceof FolderPack folderPack) {
                this.folderWidget = this.addTopRenderableOnly(this.prependWidget(
                        FidgetzButton.<Void>builder()
                                .setTooltip(Tooltip.create(ResourceUtil.getText("folder.open")))
                                .setHeight(this.getHeight() / 3)
                                .makeSquare()
                                .setSpriteOnly(FOLDER_BUTTON_SPRITE)
                                .setOnPress(btn -> {
                                    btn.setFocused(false);
                                    PackListBase.this.openFolder(folderPack);
                                })
                                .build()
                ));
            }

            ModAdditions.addToEntry(PackListBase.this.packAssets.isResourcePacks(), this);
        }

        public <U extends GuiEventListener & Renderable> U addRenderableWidget(U widget) {
            this.children.add(widget);
            this.renderables.add(widget);
            if (widget instanceof NarratableEntry narratable) this.narratables.add(narratable);
            return widget;
        }

        public <U extends GuiEventListener> U prependWidget(U widget) {
            this.children.addFirst(widget);
            if (widget instanceof NarratableEntry narratable) this.narratables.add(narratable);
            return widget;
        }

        public <U extends Renderable> U addTopRenderableOnly(U renderable) {
            this.topRenderables.add(renderable);
            return renderable;
        }

        /**
         * @deprecated z plane removed in GUI
         */
        @Deprecated(since = "mc1.21.6")
        public <U extends GuiEventListener & Renderable> U prependRenderableWidget(U widget) {
            this.children.addFirst(widget);
            this.renderables.addFirst(widget);
            if (widget instanceof NarratableEntry narratable) this.narratables.addFirst(narratable);
            return widget;
        }

        @Override
        public Pack getPack() {
            return this.pack;
        }

        public boolean isSelected() {
            return PackListBase.this.selection.contains(this.pack);
        }

        public boolean isSelectedLast() {
            return PackListBase.this.getLastSelected() == this.pack;
        }

        private boolean sendSelection() {
            List<Pack> payload = new ArrayList<>();

            for (Pack selected : PackListBase.this.getOrderedSelection().reversed()) {
                if (PackListBase.this.isTransferable(selected)) {
                    payload.add(selected);
                }
            }

            if (!payload.isEmpty()) {
                Pack trigger = this.isTransferable() ? this.pack : null;
                PackListBase.this.sendEvent(new RequestTransferEvent(PackListBase.this, payload, trigger));
                return true;
            }

            return false;
        }

        public boolean transfer() {
            if (!this.isSelected() && this.isTransferable()) {
                PackListBase.this.sendEvent(new RequestTransferEvent(PackListBase.this, this.pack));
                return true;
            }

            return this.sendSelection();
        }

        private boolean handleDoubleClick() {
            long currentTime = Util.getMillis();
            if (this.isTransferable() && this.isSelectedLast() && currentTime - lastClickTime <= DOUBLE_CLICK_DELTA_MS) {
                PackListBase.this.sendEvent(new RequestTransferEvent(PackListBase.this, this.pack));
                return true;
            }
            lastClickTime = currentTime;
            return false;
        }

        private void fireClickEvent(BiConsumer<PackListBase<T>, Pack> selector, MouseSelectionState state) {
            this.mouseSelectionState = state;
            selector.accept(PackListBase.this, this.pack);
            PackListBase.this.sendEvent(new SelectionEvent(PackListBase.this));
        }

        private boolean exceedsDragThreshold(double dragX, double dragY) {
            return Math.hypot(dragX, dragY) > DRAG_THRESHOLD;
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return GuiUtil.containsPoint(
                    this.getX(),
                    this.getY() - BACKGROUND_OFFSET,
                    this.getWidth(),
                    this.getHeight() + BACKGROUND_OFFSET * 2,
                    mouseX, mouseY
            );
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (ContainerEventHandlerPatch.super.mouseClicked(mouseX, mouseY, button)) {
                return false;
            }
            if (isLeftClick(button) && this.isMouseOver(mouseX, mouseY)) {
                if (!isRangeModifierActive() && !isSelectModifierActive() && this.handleDoubleClick()) {
                    return true;
                }
                if (isRangeModifierActive()) {
                    this.fireClickEvent(PackListBase::selectRange, MouseSelectionState.SELECTING_MANY);
                } else if (isSelectModifierActive()) {
                    this.fireClickEvent(PackListBase::selectToggle, MouseSelectionState.SELECTING_MANY);
                } else if (!this.isSelected()) {
                    this.fireClickEvent(PackListBase::selectExclusive, MouseSelectionState.SELECTING_ONE);
                } else if (this.isSelected() && !this.isSelectedLast()) {
                    this.fireClickEvent(PackListBase::select, MouseSelectionState.SELECTING_ONE);
                } else {
                    this.mouseSelectionState = MouseSelectionState.SELECTING_ONE;
                }
                return true;
            }
            this.mouseSelectionState = MouseSelectionState.INACTIVE;
            return false;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (this.isSelectedLast()
                && this.isMouseOver(mouseX, mouseY)
                && this.mouseSelectionState == MouseSelectionState.SELECTING_ONE
                && PackListBase.this.selection.size() > 1) {
                this.fireClickEvent(PackListBase::selectExclusive, MouseSelectionState.INACTIVE);
                return true;
            }
            this.mouseSelectionState = MouseSelectionState.INACTIVE;
            return false;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (!this.isMouseOver(mouseX, mouseY)) {
                this.mouseSelectionState = MouseSelectionState.INACTIVE;
                return false;
            }
            if (this.isSelected()
                && !this.pack.isFixedPosition()
                && this.mouseSelectionState == MouseSelectionState.SELECTING_ONE
                && this.exceedsDragThreshold(dragX, dragY)) {
                PackListBase.this.sendEvent(new DragEvent(
                        PackListBase.this,
                        PackListBase.this.getOrderedSelection().reversed(),
                        this.pack,
                        this.packWidget.getSprite()
                ));
                return true;
            }
            return false;
        }

        @Override
        public void renderBack(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isMouseOver, float partialTick) {
            if (!this.pack.getCompatibility().isCompatible() && !PackListBase.this.packAssets.getConfig().isIncompatibleWarningsHidden()) {
                int backgroundTop = this.getY() - BACKGROUND_OFFSET;
                int backgroundLeft = this.getX() + BACKGROUND_OFFSET;
                int backgroundBottom = backgroundTop + this.getHeight() + BACKGROUND_OFFSET * 2;
                int backgroundRight = backgroundLeft + this.getWidth() - BACKGROUND_OFFSET * 2;
                guiGraphics.fill(backgroundLeft, backgroundTop, backgroundRight, backgroundBottom, Theme.RED_900.getARGB());
            }
        }

        protected abstract void renderForeground(GuiGraphics guiGraphics, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick);

        private void renderSelection(GuiGraphics guiGraphics, int top, int left, int width, int height) {
            if (this.isSelected()) {
                int overlayTop = this.getY() - BACKGROUND_OFFSET;
                int overlayLeft = this.getX() + BACKGROUND_OFFSET;
                int overlayWidth = this.getWidth() - BACKGROUND_OFFSET * 2;
                int overlayHeight = this.getHeight() + BACKGROUND_OFFSET * 2;
                pick(isSelectedLast(), OVERLAY, SELECTED_OVERLAY).render(guiGraphics, overlayLeft, overlayTop, overlayWidth, overlayHeight);
            }
            if (this.isSelected() || this.isFocused()) {
                int outlineTop = top - BACKGROUND_OFFSET * 2;
                int outlineHeight = height + BACKGROUND_OFFSET * 4;

                if (!this.isFocused()) {
                    guiGraphics.renderOutline(left, outlineTop, width, outlineHeight, Theme.BLUE_500.getARGB());
                }
            }
        }

        protected void renderTop(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            if (this.folderWidget != null) {
                this.folderWidget.setPosition(this.packWidget.getContentLeft(), this.getBottom() - this.folderWidget.getHeight());
            }

            for (Renderable renderable : this.topRenderables) {
                renderable.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }

        private void renderWidget(GuiGraphics guiGraphics, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            this.packWidget.setPosition(left, top);
            this.packWidget.setWidth(width);

            for (Renderable renderable : this.renderables) {
                renderable.render(guiGraphics, mouseX, mouseY, partialTick);
            }

            this.renderSelection(guiGraphics, top, left, width, height);
            this.renderForeground(guiGraphics, top, left, width, height, mouseX, mouseY, hovering, partialTick);
            this.renderTop(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Override
        public final void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            this.renderWidget(guiGraphics, top, left, width, height, mouseX, mouseY, hovering, partialTick);
        }

        @Override
        public @NotNull List<? extends GuiEventListener> children() {
            return this.children;
        }

        @Override
        public @NotNull List<? extends NarratableEntry> narratables() {
            return this.narratables;
        }

        private enum MouseSelectionState {
            INACTIVE,
            SELECTING_ONE,
            SELECTING_MANY
        }
    }
}
