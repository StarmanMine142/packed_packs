package io.github.fishstiz.packed_packs.gui.components.events;

import io.github.fishstiz.packed_packs.gui.components.pack.PackList;
import io.github.fishstiz.packed_packs.util.pack.FolderPack;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class FolderOpenEvent extends PackListEvent {
    private final FolderPack opened;

    public FolderOpenEvent(PackList target, FolderPack opened) {
        super(target);
        this.opened = Objects.requireNonNull(opened);
    }

    public @NotNull FolderPack opened() {
        return this.opened;
    }

    @Override
    public boolean modifiesTarget() {
        return false;
    }
}
