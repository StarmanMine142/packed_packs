package io.github.fishstiz.packed_packs.transform.mixin.folders;

import io.github.fishstiz.packed_packs.transform.interfaces.NestedPack;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Pack.class)
public abstract class PackMixin implements NestedPack {
    @Unique
    private boolean packed_packs$nested = false;

    @Override
    public boolean packed_packs$nestedPack() {
        return this.packed_packs$nested;
    }

    @Override
    public void packed_packs$setNestedPack(boolean nested) {
        this.packed_packs$nested = nested;
    }
}
