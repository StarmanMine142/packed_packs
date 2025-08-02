package io.github.fishstiz.packed_packs.gui.components.pack;

import com.google.common.collect.ImmutableList;
import io.github.fishstiz.fidgetz.gui.components.FidgetzButton;
import io.github.fishstiz.fidgetz.gui.renderables.sprites.Sprite;
import io.github.fishstiz.fidgetz.gui.shapes.Size;
import io.github.fishstiz.fidgetz.util.DrawUtil;
import io.github.fishstiz.packed_packs.gui.components.events.*;
import io.github.fishstiz.packed_packs.util.ResourceUtil;
import io.github.fishstiz.packed_packs.util.constants.Theme;
import io.github.fishstiz.packed_packs.util.pack.FolderPack;
import io.github.fishstiz.packed_packs.util.pack.PackAssets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.packs.repository.Pack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FolderPackList extends CurrentPackList {
    private static final Sprite CLOSE_SPRITE = new Sprite(ResourceUtil.getIcon("cross"), Size.of16());
    private static final int HEADER_HEIGHT = 16;
    private final FidgetzButton<Void> closeButton;
    private final int spacing;
    private PackList frame;
    private FolderPack folderPack;
    private Sprite sprite;

    public FolderPackList(PackAssets packAssets, PackListEventListener listener, int spacing) {
        super(packAssets, listener);
        this.spacing = spacing;
        this.closeButton = FidgetzButton.<Void>builder()
                .setOnPress(() -> this.sendEvent(new FolderCloseEvent(this)))
                .makeSquare(CLOSE_SPRITE.width)
                .build();
    }

    @Override
    protected @NotNull Entry createEntry(Pack pack, int index) {
        return new SubPackEntry(pack, index);
    }

    @Override
    public boolean isTransferable(Pack pack) {
        return false;
    }

    @Override
    public void renderDroppableZone(GuiGraphics guiGraphics, PackList source, ImmutableList<Pack> payload, Pack trigger, int mouseX, int mouseY, float partialTick) {
        if (source == this) {
            super.renderDroppableZone(guiGraphics, source, payload, trigger, mouseX, mouseY, partialTick);
        }
    }

    private void updateBounds() {
        if (this.frame != null) {
            int frameX = this.frame.getX();
            int frameY = this.frame.getY();
            int frameWidth = this.frame.getWidth();
            int frameHeight = this.frame.getHeight();

            int left = frameX + this.spacing;
            int top = frameY + this.spacing;
            int right = (frameX + frameWidth) - this.spacing;
            int bottom = (frameY + frameHeight) - this.spacing;

            this.closeButton.setPosition(left, top);
            this.setPosition(left, top + HEADER_HEIGHT + this.spacing);
            this.setWidth(right - left);
            this.setHeight(bottom - this.getY());
        }
    }

    private void renderFolderInfo(GuiGraphics guiGraphics, float partialTick) {
        if (this.frame != null && this.sprite != null && this.folderPack != null) {
            int frameX = this.frame.getX();
            int left = frameX + this.spacing;
            int top = this.frame.getY() + this.spacing;
            int right = (frameX + this.frame.getWidth()) - this.spacing;

            this.closeButton.setPosition(left, top);
            this.sprite.renderClamped(guiGraphics, left, top, CLOSE_SPRITE.width, CLOSE_SPRITE.height, partialTick);
            if (this.closeButton.isHovered()) {
                PackListBase.Entry.OVERLAY.render(guiGraphics, left, top, CLOSE_SPRITE.width, CLOSE_SPRITE.height);
                CLOSE_SPRITE.renderClamped(guiGraphics, left, top, CLOSE_SPRITE.width, CLOSE_SPRITE.height, partialTick);
            }

            var font = Minecraft.getInstance().font;
            int startX = left + CLOSE_SPRITE.width + this.spacing;
            int endY = top + HEADER_HEIGHT;

            DrawUtil.renderScrollingStringLeftAlign(guiGraphics, font, this.folderPack.getTitle(), startX, top, right, endY, Theme.GRAY_800.getARGB(), false);
        }
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.updateBounds();
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
        this.renderFolderInfo(guiGraphics, partialTick);
    }

    public void setFrame(PackList frame) {
        this.frame = frame;
        this.updateBounds();
    }

    public void setFolderPack(FolderPack folderPack) {
        this.folderPack = folderPack;

        if (folderPack != null) {
            this.packAssets.getOrLoadIcon(this.folderPack, icon -> this.sprite = new Sprite(icon, Size.of16()));
        }
    }

    public @Nullable FolderPack getFolderPack() {
        return this.folderPack;
    }

    public FidgetzButton<Void> getCloseButton() {
        return this.closeButton;
    }

    protected class SubPackEntry extends Entry {
        protected SubPackEntry(Pack pack, int index) {
            super(pack, index);
        }

        @Override
        public boolean isTransferable() {
            return false;
        }
    }
}
