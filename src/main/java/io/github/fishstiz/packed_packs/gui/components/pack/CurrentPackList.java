package io.github.fishstiz.packed_packs.gui.components.pack;

import com.google.common.collect.ImmutableList;
import io.github.fishstiz.fidgetz.gui.renderables.ColoredRect;
import io.github.fishstiz.fidgetz.gui.renderables.GradientRect;
import io.github.fishstiz.fidgetz.gui.renderables.sprites.Sprite;
import io.github.fishstiz.fidgetz.util.GuiUtil;
import io.github.fishstiz.packed_packs.gui.components.events.PackListEventListener;
import io.github.fishstiz.packed_packs.util.constants.Theme;
import io.github.fishstiz.packed_packs.gui.components.events.MoveEvent;
import io.github.fishstiz.packed_packs.util.pack.PackAssets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.packs.repository.Pack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

import static com.mojang.blaze3d.platform.InputConstants.*;
import static io.github.fishstiz.fidgetz.util.GuiUtil.playClickSound;
import static io.github.fishstiz.packed_packs.util.InputUtil.isLeftClick;
import static io.github.fishstiz.packed_packs.util.InputUtil.isMoveModifierActive;
import static io.github.fishstiz.packed_packs.util.lang.IntsUtil.hasGap;
import static io.github.fishstiz.packed_packs.util.lang.ObjectsUtil.pick;
import static io.github.fishstiz.packed_packs.util.ResourceUtil.getVanillaSprite;

public class CurrentPackList extends PackListBase<CurrentPackList.Entry> {
    private static final Sprite UNSELECT_HIGHLIGHTED_SPRITE = Sprite.of32(getVanillaSprite("transferable_list/unselect_highlighted"));
    private static final Sprite UNSELECT_SPRITE = Sprite.of32(getVanillaSprite("transferable_list/unselect"));
    private static final Sprite MOVE_UP_HIGHLIGHTED_SPRITE = Sprite.of32(getVanillaSprite("transferable_list/move_up_highlighted"));
    private static final Sprite MOVE_UP_SPRITE = Sprite.of32(getVanillaSprite("transferable_list/move_up"));
    private static final Sprite MOVE_DOWN_HIGHLIGHTED_SPRITE = Sprite.of32(getVanillaSprite("transferable_list/move_down_highlighted"));
    private static final Sprite MOVE_DOWN_SPRITE = Sprite.of32(getVanillaSprite("transferable_list/move_down"));
    private static final Theme DROP_THEME = Theme.GREEN_500;
    private static final ColoredRect DROP_INDEX = new ColoredRect(DROP_THEME.getARGB());
    private static final GradientRect SCROLL_UP = GradientRect.fromTop(DROP_THEME.withAlpha(0.75f), DROP_THEME.withAlpha(0));
    private static final GradientRect SCROLL_DOWN = SCROLL_UP.flip();
    private static final int DROP_INDEX_PADDING = 2;
    private static final double SCROLL_STEP = 10;
    private boolean scrolling;

    public CurrentPackList(PackAssets packAssets, PackListEventListener listener) {
        super(packAssets, listener);
    }

    @Override
    protected @NotNull Entry createEntry(Pack pack, int index) {
        return new Entry(pack, index);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Entry entry = this.getEntry(this.getLastSelected());
        if (entry == null || !isMoveModifierActive()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == KEY_DOWN) {
            if (entry.moveDown()) playClickSound();
            return true;
        } else if (keyCode == KEY_UP) {
            if (entry.moveUp()) playClickSound();
            return true;
        }
        return false;
    }

    private void scrollStep(MoveDirection direction, float partialTick) {
        double scrollAmount = this.scrollAmount();
        if (direction.isUp()) {
            scrollAmount -= SCROLL_STEP * partialTick;
        } else if (direction.isDown()) {
            scrollAmount += SCROLL_STEP * partialTick;
        }

        this.scrolling = true;
        this.setClampedScrollAmount(scrollAmount);
    }

    private int getDropIndex(double mouseY) {
        if (this.children().isEmpty()) return -1;

        int index = this.getRowIndex(mouseY);
        if (index == -1) return -1;

        Entry entry = this.getEntry(index);
        int centerY = entry.getY() + (entry.getHeight() / 2);

        if (mouseY >= centerY) {
            int next = index + 1;
            return next < this.children().size() ? next : -1;
        }
        return index;
    }

    private boolean isMouserOverSelection(List<Pack> selection, double mouseX, double mouseY) {
        for (Pack selected : selection) {
            Entry entry = this.getEntry(selected);
            if (entry != null && entry.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    public void insertDrop(Pack pack, int index) {
        if (pack != null) {
            int previous = this.packs.indexOf(pack);
            if (previous != -1 && previous < index) {
                index--;
            }
            this.packs.remove(pack);
            this.packs.add(Math.clamp(index, 0, this.packs.size()), pack);
            this.queryPacks();
        }
    }

    private boolean canDrop(PackList source, ImmutableList<Pack> payload, Pack trigger, double mouseX, double mouseY) {
        if (this.scrolling || this.isQueried() || payload.isEmpty() || (source != this && source instanceof FolderPackList)) {
            return false;
        }

        int dropIndex = this.getDropIndex(mouseY);
        if (dropIndex > -1 && this.getEntry(dropIndex).isFixed()) {
            return false;
        }

        if (source != this) {
            return source.isTransferable(trigger);
        }

        if (trigger.isFixedPosition() || this.isMouserOverSelection(payload, mouseX, mouseY)) {
            return false;
        }

        int[] indices = this.getIndicesFromSelection(payload);

        if (indices.length == 0) {
            return false;
        }

        if (hasGap(indices)) {
            return true;
        }

        Arrays.sort(indices);
        int lastSelectionIndex = indices[indices.length - 1];

        if (!this.packs.isEmpty()) {
            int lastPackIndex = this.packs.indexOf(this.packs.getLast());
            if (dropIndex == -1 && lastPackIndex == lastSelectionIndex) {
                return false;
            }
        }

        return dropIndex != indices[0] && dropIndex - 1 != lastSelectionIndex;
    }

    @Override
    protected @Nullable List<Pack> handleDrop(PackList source, ImmutableList<Pack> payload, Pack trigger, double mouseX, double mouseY) {
        if (!this.canDrop(source, payload, trigger, mouseX, mouseY)) return null;

        int dropIndex = this.getDropIndex(mouseY);
        if (dropIndex == -1) {
            dropIndex = this.children().size();
        }

        if (source == this) {
            List<Pack> movable = new ArrayList<>(payload);
            movable.removeIf(Pack::isFixedPosition);
            return this.moveAll(this.orderSelection(movable), dropIndex) ? payload : null;
        }

        this.clearSelection();
        List<Pack> dropped = new ArrayList<>();
        for (Pack selected : payload) {
            if (source.isTransferable(selected)) {
                dropped.add(selected);
                this.insertDrop(selected, dropIndex);
                this.select(selected);
            }
        }
        source.removeAll(dropped);
        this.select(trigger);

        return dropped;
    }

    private void renderDropIndex(GuiGraphics guiGraphics, int mouseY, int x, int width) {
        int dropIndex = this.getDropIndex(mouseY);
        int rowTop = this.getRowTop(dropIndex != -1 ? dropIndex : this.children().size());
        int indexY = rowTop - this.rowGap - DROP_INDEX_PADDING;

        guiGraphics.enableScissor(this.getX(), this.getY(), this.getRight(), this.getBottom());
        DROP_INDEX.render(guiGraphics, x, indexY, width, rowTop - indexY + DROP_INDEX_PADDING);
        guiGraphics.disableScissor();
    }

    @Override
    public void renderDroppableZone(GuiGraphics guiGraphics, PackList source, ImmutableList<Pack> payload, Pack trigger, int mouseX, int mouseY, float partialTick) {
        if (this.isQueried() || (source != this && source instanceof FolderPackList)) return;

        int x = this.getX();
        int y = this.getY();
        int width = this.scrollbarVisible() ? this.getWidth() - this.scrollbarOffset : this.getWidth();
        int height = this.getHeight();
        int bottom = this.getBottom();

        if (this.isMouseOver(mouseX, mouseY)) {
            double scrollAmount = this.scrollAmount();

            int scrollDownY = bottom - this.itemHeight;
            if (scrollAmount < this.maxScrollAmount() && mouseY >= scrollDownY) {
                SCROLL_DOWN.render(guiGraphics, x, scrollDownY, width, this.itemHeight);
                this.scrollStep(MoveDirection.DOWN, partialTick);
            } else if (scrollAmount > 0 && mouseY <= y + this.itemHeight) {
                SCROLL_UP.render(guiGraphics, x, y, width, this.itemHeight);
                this.scrollStep(MoveDirection.UP, partialTick);
            } else {
                this.scrolling = false;
            }

            if (this.canDrop(source, payload, trigger, mouseX, mouseY)) {
                this.renderDropIndex(guiGraphics, mouseY, x, width);
            }
        }

        guiGraphics.renderOutline(x, y, width, height, DROP_THEME.getARGB());
    }

    public class Entry extends PackListBase<Entry>.Entry {
        protected Entry(Pack pack, int index) {
            super(pack, index);
        }

        @Override
        public boolean isTransferable() {
            return !this.pack.isRequired();
        }

        private int getDownIndex() {
            for (int i = this.index + 1; i < CurrentPackList.this.packs.size(); i++) {
                if (!CurrentPackList.this.packs.get(i).isFixedPosition()) return i;
            }
            return -1;
        }

        private int getUpIndex() {
            for (int i = this.index - 1; i >= 0; i--) {
                if (!CurrentPackList.this.packs.get(i).isFixedPosition()) return i;
            }
            return -1;
        }

        private boolean isFixed() {
            return CurrentPackList.this.isQueried() || this.pack.isFixedPosition();
        }

        public boolean canMoveDown() {
            if (this.isFixed()) return false;

            int size = CurrentPackList.this.packs.size();

            if (this.isSelected()) {
                List<Pack> selection = CurrentPackList.this.getOrderedSelection().reversed();
                if (!selection.isEmpty()) {
                    Entry entry = CurrentPackList.this.getEntry(selection.getFirst());
                    return entry != null && entry.getIndex() < size && entry.getDownIndex() > -1;
                }
            }

            return this.index >= 0 && this.index < size && this.getDownIndex() > -1;
        }

        public boolean canMoveUp() {
            if (this.isFixed()) return false;

            if (this.isSelected()) {
                List<Pack> selection = CurrentPackList.this.getOrderedSelection();
                if (!selection.isEmpty()) {
                    Entry entry = CurrentPackList.this.getEntry(selection.getFirst());
                    return entry != null && entry.getIndex() > 0 && entry.getUpIndex() > -1;
                }
            }

            return this.index > 0 && this.getUpIndex() > -1;
        }

        public boolean isMouseOverRemove(double mouseX, double mouseY) {
            return this.isTransferable() && GuiUtil.containsPoint(
                    this.getX() + SPACING,
                    this.getY(),
                    UNSELECT_SPRITE.width / 2,
                    UNSELECT_SPRITE.height,
                    mouseX,
                    mouseY
            );
        }

        public boolean isMouseOverUp(double mouseX, double mouseY) {
            return this.canMoveUp() && GuiUtil.containsPoint(
                    this.getX() + SPACING + MOVE_UP_SPRITE.width / 2,
                    this.getY(),
                    MOVE_UP_SPRITE.width / 2,
                    MOVE_UP_SPRITE.height / 2,
                    mouseX,
                    mouseY
            );
        }

        public boolean isMouseOverDown(double mouseX, double mouseY) {
            return this.canMoveDown() && GuiUtil.containsPoint(
                    this.getX() + SPACING + MOVE_DOWN_SPRITE.width / 2,
                    this.getY() + MOVE_DOWN_SPRITE.height / 2,
                    MOVE_DOWN_SPRITE.width / 2,
                    MOVE_DOWN_SPRITE.height / 2,
                    mouseX,
                    mouseY
            );
        }

        private @Nullable List<Pack> moveSelection(List<Pack> selection, ToIntFunction<Entry> indexGetter) {
            List<Pack> moved = new ArrayList<>();

            Pack lastSelected = CurrentPackList.this.getLastSelected();
            for (int i = 0; i < selection.size(); i++) {
                Entry entry = CurrentPackList.this.getEntry(selection.get(i));
                if (entry != null && CurrentPackList.this.move(selection.get(i), indexGetter.applyAsInt(entry))) {
                    moved.add(selection.get(i));
                } else if (i == 0) {
                    return null;
                }
            }
            CurrentPackList.this.select(lastSelected);

            return !moved.isEmpty() ? moved : null;
        }

        private void sendMoveEvent(List<Pack> moved) {
            CurrentPackList.this.sendEvent(new MoveEvent(CurrentPackList.this, moved, this.pack));
        }

        private boolean moveDirection(MoveDirection direction) {
            if ((direction.isUp() && !this.canMoveUp()) || (direction.isDown() && !this.canMoveDown())) {
                return false;
            }

            if (!this.isSelected()) {
                int targetIndex = direction.isUp() ? this.getUpIndex() : this.getDownIndex();
                if (CurrentPackList.this.move(this.pack, targetIndex)) {
                    CurrentPackList.this.selectExclusive(this.pack);
                    this.sendMoveEvent(List.of(this.pack));
                    return true;
                }
                return false;
            }

            List<Pack> moved = direction.isUp()
                    ? moveSelection(CurrentPackList.this.getOrderedSelection(), Entry::getUpIndex)
                    : moveSelection(CurrentPackList.this.getOrderedSelection().reversed(), Entry::getDownIndex);

            if (moved != null && !moved.isEmpty()) {
                this.sendMoveEvent(moved);
                return true;
            }

            return false;
        }

        public boolean moveUp() {
            return this.moveDirection(MoveDirection.UP);
        }

        public boolean moveDown() {
            return this.moveDirection(MoveDirection.DOWN);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (isLeftClick(button)) {
                if (this.isMouseOverRemove(mouseX, mouseY)) {
                    return this.consumeClick(CurrentPackList.Entry::transfer);
                } else if (this.isMouseOverUp(mouseX, mouseY)) {
                    return this.consumeClick(CurrentPackList.Entry::moveUp);
                } else if (this.isMouseOverDown(mouseX, mouseY)) {
                    return this.consumeClick(CurrentPackList.Entry::moveDown);
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        private boolean consumeClick(Consumer<CurrentPackList.Entry> action) {
            playClickSound();
            action.accept(this);
            return false;
        }

        @Override
        protected void renderForeground(GuiGraphics guiGraphics, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            if (!hovering && !this.isSelectedLast()) return;

            int x = left + SPACING;
            OVERLAY.render(guiGraphics, x, top, UNSELECT_SPRITE.width, UNSELECT_SPRITE.height);
            if (this.isTransferable()) {
                pick(!this.isMouseOverRemove(mouseX, mouseY), UNSELECT_SPRITE, UNSELECT_HIGHLIGHTED_SPRITE).render(guiGraphics, x, top);
            }
            if (this.canMoveUp()) {
                pick(!this.isMouseOverUp(mouseX, mouseY), MOVE_UP_SPRITE, MOVE_UP_HIGHLIGHTED_SPRITE).render(guiGraphics, x, top);
            }
            if (this.canMoveDown()) {
                pick(!this.isMouseOverDown(mouseX, mouseY), MOVE_DOWN_SPRITE, MOVE_DOWN_HIGHLIGHTED_SPRITE).render(guiGraphics, x, top);
            }
        }
    }

    private enum MoveDirection {
        UP, DOWN;

        public boolean isUp() {
            return this == MoveDirection.UP;
        }

        public boolean isDown() {
            return this == MoveDirection.DOWN;
        }
    }
}
