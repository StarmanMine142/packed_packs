package io.github.fishstiz.packed_packs.gui.components.events;

import io.github.fishstiz.packed_packs.gui.components.pack.FolderPackList;
import io.github.fishstiz.packed_packs.util.pack.FolderPack;
import org.jetbrains.annotations.Nullable;

public final class FolderCloseEvent extends PackListEvent {
    private final FolderPack folderPack;

    public FolderCloseEvent(FolderPackList target) {
        super(target);
        this.folderPack = target.getFolderPack();
    }

    public @Nullable FolderPack folderPack() {
        return this.folderPack;
    }

    @Override
    public boolean modifiesTarget() {
        return false;
    }
}
