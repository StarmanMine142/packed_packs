package io.github.fishstiz.packed_packs.gui.components.pack;

import io.github.fishstiz.fidgetz.gui.components.FidgetzText;
import io.github.fishstiz.fidgetz.gui.renderables.sprites.Sprite;
import io.github.fishstiz.packed_packs.util.constants.Theme;
import io.github.fishstiz.packed_packs.util.pack.PackAssets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.server.packs.repository.Pack;

class PackWidget extends AbstractWidget {
    private static final int DESCRIPTION_LINES = 2;
    private final Pack pack;
    private final PackAssets packAssets;
    private final FidgetzText<?> title = FidgetzText.builder()
            .setHeight(Minecraft.getInstance().font.lineHeight)
            .setColor(ChatFormatting.WHITE.getColor())
            .setShadow(true)
            .alignLeft()
            .build();
    private MultiLineLabel description;
    private Sprite sprite;
    private final int spacing;
    private boolean lazyLoaded = false;

    PackWidget(Pack pack, PackAssets packAssets, int x, int y, int width, int height, int spacing) {
        super(x, y, width, height, pack.getTitle());

        this.pack = pack;
        this.packAssets = packAssets;
        this.title.setMessage(pack.getTitle());
        this.spacing = spacing;
        this.sprite = Sprite.of32(PackAssets.getDefaultIcon(pack));

        this.cacheDescription();
    }

    public Sprite getSprite() {
        return this.sprite;
    }

    private int getIconSize() {
        return this.getHeight();
    }

    private void cacheDescription() {
        this.description = MultiLineLabel.create(
                Minecraft.getInstance().font,
                this.title.getWidth(),
                DESCRIPTION_LINES,
                this.pack.getPackSource().decorate(this.pack.getDescription())
        );
    }

    @Override
    public void setWidth(int width) {
        super.setWidth(width);

        int bodyX = this.spacing * 2 + this.getX() + this.getIconSize();
        int bodyWidth = this.getRight() - this.spacing * 2 - bodyX;

        if (this.title.getWidth() != bodyWidth) {
            this.title.setX(bodyX);
            this.title.setWidth(bodyWidth);
            this.cacheDescription();
        }
    }

    protected void renderSprite(GuiGraphics guiGraphics, float partialTick) {
        if (!this.lazyLoaded) { // lazy loads icon as this is not called if not in view
            this.lazyLoaded = true;
            this.packAssets.getOrLoadIcon(this.pack, icon -> this.sprite = Sprite.of32(icon));
        }

        int x = this.getX() + this.spacing;
        int y = this.getY();
        int size = this.getIconSize();
        this.sprite.render(guiGraphics, x, y, size, size, partialTick);
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderSprite(guiGraphics, partialTick);

        int lineHeight = Minecraft.getInstance().font.lineHeight;
        int totalContentHeight = lineHeight + spacing + (lineHeight * DESCRIPTION_LINES);
        int startY = this.getY() + (this.getHeight() - totalContentHeight) / 2;

        this.title.setY(startY);
        this.title.renderWidget(guiGraphics, mouseX, mouseY, partialTick);

        this.description.renderLeftAligned(
                guiGraphics,
                this.title.getX(),
                startY + lineHeight + this.spacing,
                lineHeight,
                Theme.GRAY_500.getARGB()
        );
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        // unsupported
    }
}
