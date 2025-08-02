package io.github.fishstiz.packed_packs.transform.interfaces;

public interface NestedPack {
    default boolean packed_packs$nestedPack() {
        return false;
    }

    default void packed_packs$setNestedPack(boolean nested) {
    }
}
