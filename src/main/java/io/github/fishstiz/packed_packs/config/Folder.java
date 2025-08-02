package io.github.fishstiz.packed_packs.config;

import net.minecraft.server.packs.repository.Pack;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Folder implements Serializable {
    private List<String> packIds = new ArrayList<>();

    public void setPacks(List<Pack> packs) {
        List<String> ids = new ArrayList<>();
        for (Pack pack : packs) {
            if (pack != null) ids.add(pack.getId());
        }
        this.packIds = ids;
    }

    public List<String> getPackIds() {
        return List.copyOf(this.packIds);
    }
}
