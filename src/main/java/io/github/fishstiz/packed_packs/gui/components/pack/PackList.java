package io.github.fishstiz.packed_packs.gui.components.pack;

import com.google.common.collect.ImmutableList;
import io.github.fishstiz.packed_packs.gui.history.Restorable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.server.packs.repository.Pack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static io.github.fishstiz.packed_packs.util.lang.ObjectsUtil.testNullable;

public interface PackList extends ContainerEventHandler, LayoutElement, Restorable<PackList.Snapshot> {
    void add(Pack pack);

    void addAll(List<Pack> packs);

    boolean move(Pack pack, int index);

    boolean moveAll(List<Pack> packs, int index);

    void remove(Pack pack);

    void removeAll(List<Pack> packs);

    void unselect(Pack pack);

    void select(Pack pack);

    void selectAll(List<Pack> packs);

    void selectExclusive(Pack pack);

    void selectRange(Pack pack);

    void clearSelection();

    void drop(PackList source, ImmutableList<Pack> selection, Pack trigger, double mouseX, double mouseY);

    void renderDroppableZone(GuiGraphics guiGraphics, PackList source, ImmutableList<Pack> payload, Pack trigger, int mouseX, int mouseY, float partialTick);

    @Nullable Entry getSelected();

    @Nullable Entry getEntry(Pack pack);

    default boolean isTransferable(Pack pack) {
        return testNullable(this.getEntry(pack), Entry::isTransferable);
    }

    @NotNull ImmutableList<Pack> copyPacks();

    @NotNull ImmutableList<Pack> copySelection();

    @NotNull Query copyQuery();

    @Override
    default @NotNull ScreenRectangle getRectangle() {
        return LayoutElement.super.getRectangle();
    }

    default @NotNull Snapshot captureState() {
        return new Snapshot(this, this.copyPacks(), this.copySelection(), this.copyQuery());
    }

    interface Entry extends GuiEventListener {
        boolean isTransferable();

        Pack getPack();
    }

    record Snapshot(
            PackList target,
            ImmutableList<Pack> packs,
            ImmutableList<Pack> selection,
            Query query
    ) implements Restorable.Snapshot<Snapshot> {
        public Snapshot validate(List<Pack> validPacks) {
            List<Pack> validated = new ArrayList<>(this.packs);
            validated.retainAll(validPacks);
            return new Snapshot(this.target, ImmutableList.copyOf(validated), this.selection, this.query);
        }
    }
}
