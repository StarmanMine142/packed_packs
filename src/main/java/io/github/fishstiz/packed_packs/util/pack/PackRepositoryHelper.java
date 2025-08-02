package io.github.fishstiz.packed_packs.util.pack;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.github.fishstiz.packed_packs.config.Folder;
import io.github.fishstiz.packed_packs.transform.interfaces.NestedPack;
import io.github.fishstiz.packed_packs.transform.mixin.PackSelectionModelAccessor;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class PackRepositoryHelper implements PackAssets {
    private final Map<String, ResourceLocation> cachedIcons = new Object2ObjectOpenHashMap<>();
    private final Map<String, Pack> availablePacks = new Object2ObjectLinkedOpenHashMap<>();
    private final Map<String, List<Pack>> folderPacks = new Object2ObjectOpenHashMap<>();
    private final Map<String, CompletableFuture<Folder>> folderConfigs = new Object2ObjectOpenHashMap<>();
    private final PackRepository repository;
    private final Path packDir;
    private final PackSelectionModel model;
    private final boolean resourcePacks;
    private Map<String, ResourceLocation> staleIcons;

    public PackRepositoryHelper(PackRepository repository, Path packDir) {
        this.repository = repository;
        this.packDir = packDir;

        // Fabric API workaround
        this.model = new PackSelectionModel(PackRepositoryHelper::_update, PackRepositoryHelper::_getIcon, this.repository, PackRepositoryHelper::_apply);

        this.resourcePacks = this.repository == Minecraft.getInstance().getResourcePackRepository();

        this.regenerateAvailablePacks();
    }

    public PackRepository getRepository() {
        return this.repository;
    }

    private List<Pack> getSelectedPacks() {
        return ((PackSelectionModelAccessor) this.model).getSelectedPacks();
    }

    private List<Pack> getUnselectedPacks() {
        return ((PackSelectionModelAccessor) this.model).getUnselectedPacks();
    }

    public ImmutableList<Pack> getPacks() {
        return ImmutableList.copyOf(this.availablePacks.values());
    }

    public PackGroup getPacksByRequirement() {
        List<Pack> required = new ArrayList<>();
        List<Pack> optional = new ArrayList<>();

        for (Pack pack : this.availablePacks.values()) {
            if (pack.isRequired()) {
                pack.getDefaultPosition().insert(required, pack, Pack::selectionConfig, true);
            } else {
                optional.add(pack);
            }
        }

        return PackGroup.of(required, optional);
    }

    public PackGroup getPacksBySelected() {
        return this.validateAndGroupPacks(this.getUnselectedPacks(), this.getSelectedPacks());
    }

    /**
     * @param unselected ungrouped list of unselected packs
     * @param selected   ungrouped list of selected packs
     * @return validated and grouped list of packs
     */
    public PackGroup validateAndGroupPacks(List<Pack> unselected, List<Pack> selected) {
        return validatePacks(this.groupByFolders(unselected), this.groupByFolders(selected));
    }

    /**
     * @param unselected grouped list of unselected packs
     * @param selected   grouped list of selected packs
     * @return validated and grouped list of packs
     */
    public PackGroup validatePacks(List<Pack> unselected, List<Pack> selected) {
        Set<Pack> seen = new ObjectOpenHashSet<>();
        Set<Pack> validPacks = new ObjectOpenHashSet<>(this.availablePacks.values());
        List<Pack> validSelected = new ArrayList<>(selected.size());
        List<Pack> validUnselected = new ArrayList<>(unselected.size());

        for (Pack pack : selected) {
            if (validPacks.contains(pack) && seen.add(pack)) {
                validSelected.add(pack);
            }
        }
        for (Pack pack : unselected) {
            if (validPacks.contains(pack) && seen.add(pack)) {
                validUnselected.add(pack);
            }
        }
        for (Pack pack : validPacks) {
            if (seen.add(pack)) {
                if (pack.isRequired()) {
                    pack.getDefaultPosition().insert(validSelected, pack, Pack::selectionConfig, true);
                } else {
                    validUnselected.add(pack);
                }
            }
        }
        return PackGroup.of(validSelected, validUnselected);
    }

    /**
     * @param folderPack  the folder pack
     * @param nestedPacks the nested packs that define the preferred order
     * @return a validated and ordered list of all packs under the folder pack
     */
    public List<Pack> validateAndOrderNestedPacks(FolderPack folderPack, List<Pack> nestedPacks) {
        List<Pack> validAll = this.folderPacks.get(folderPack.getId());
        Set<Pack> validSet = new ObjectOpenHashSet<>(validAll);
        Set<Pack> seen = new ObjectOpenHashSet<>();
        List<Pack> result = new ArrayList<>();

        for (Pack pack : nestedPacks) {
            if (validSet.contains(pack) && seen.add(pack)) {
                result.add(pack);
            }
        }
        for (Pack pack : validAll) {
            if (seen.add(pack)) {
                result.add(pack);
            }
        }
        this.folderPacks.put(folderPack.getId(), result);
        return result;
    }

    public List<Pack> validateAndOrderNestedPackIds(FolderPack folderPack, List<String> nestedPacks) {
        return this.validateAndOrderNestedPacks(folderPack, this.getPacksById(nestedPacks, this.folderPacks.get(folderPack.getId())));
    }

    public List<Pack> getPacksById(List<String> packIds, Map<String, Pack> source) {
        List<Pack> packs = new ArrayList<>();
        for (String id : packIds) {
            Pack pack = source.get(id);
            if (pack != null) {
                packs.add(pack);
            }
        }
        return packs;
    }

    public List<Pack> getPacksById(List<String> packIds, List<Pack> source) {
        Map<String, Pack> sourceMap = new Object2ObjectOpenHashMap<>();
        for (Pack pack : source) {
            sourceMap.put(pack.getId(), pack);
        }
        return this.getPacksById(packIds, sourceMap);
    }


    public List<Pack> getPacksById(List<String> packIds) {
        return this.getPacksById(packIds, this.availablePacks);
    }

    /**
     * @param packs ungrouped collection of packs
     */
    private void populateAvailablePacks(Collection<Pack> packs) {
        for (Pack pack : packs) {
            if (((NestedPack) pack).packed_packs$nestedPack()) {
                String folderName = PackUtil.getSubdirectoryName(pack);
                String folderId = PackUtil.FILE_PREFIX + folderName;
                if (!this.availablePacks.containsKey(folderId)) {
                    FolderPack folderPack = new FolderPack(folderId, folderName, this.packDir);
                    this.folderConfigs.put(folderId, folderPack.loadConfig());
                    this.availablePacks.put(folderId, folderPack);
                }
                this.folderPacks.computeIfAbsent(folderId, id -> new ArrayList<>()).add(pack);
            } else {
                this.availablePacks.put(pack.getId(), pack);
            }
        }
    }

    private void regenerateAvailablePacks() {
        this.availablePacks.clear();
        this.folderPacks.clear();
        this.folderConfigs.clear();
        this.populateAvailablePacks(this.getSelectedPacks());
        this.populateAvailablePacks(this.getUnselectedPacks());
    }

    public void refresh() {
        ((PackSelectionModelAccessor) this.model).packed_packs$reset();
        this.model.findNewPacks();
        this.regenerateAvailablePacks();
    }

    /**
     * @param selected grouped list of selected packs
     */
    public void selectPacks(List<Pack> selected) {
        List<Pack> flattened = this.flattenPacks(selected);
        this.repository.setSelected(Lists.reverse(flattened).stream().map(Pack::getId).collect(ImmutableList.toImmutableList()));
    }

    /**
     * @param groupedPacks grouped list of packs
     * @return flattened list of packs
     */
    public List<Pack> flattenPacks(List<Pack> groupedPacks) {
        if (this.folderPacks.isEmpty()) return groupedPacks;

        List<Pack> flattened = new ArrayList<>(groupedPacks);
        for (int i = flattened.size() - 1; i >= 0; i--) {
            if (flattened.get(i) instanceof FolderPack folderPack) {
                flattened.remove(i);
                List<Pack> nested = this.getNestedPacks(folderPack);
                if (nested != null) {
                    for (Pack pack : Lists.reverse(nested)) {
                        flattened.add(i, pack);
                    }
                }
            }
        }
        return flattened;
    }

    /**
     * @param flatPacks ungrouped list of packs
     * @return grouped list of packs
     */
    public List<Pack> groupByFolders(List<Pack> flatPacks) {
        if (this.folderPacks.isEmpty()) return flatPacks;

        Set<String> seenFolders = new ObjectOpenHashSet<>();
        Set<String> nestedPackIds = new ObjectOpenHashSet<>();
        List<Pack> grouped = new ArrayList<>();

        for (Pack pack : flatPacks) {
            for (Map.Entry<String, List<Pack>> entry : this.folderPacks.entrySet()) {
                if (entry.getValue().contains(pack)) {
                    String folderId = entry.getKey();
                    if (seenFolders.add(folderId)) {
                        grouped.add(this.availablePacks.get(folderId));
                    }
                    nestedPackIds.add(pack.getId());
                    break;
                }
            }
            if (!nestedPackIds.contains(pack.getId())) {
                grouped.add(pack);
            }
        }
        return grouped;
    }

    public void openDirectory() {
        Util.getPlatform().openPath(this.packDir);
    }

    @Override
    public void getOrLoadIcon(Pack pack, Consumer<ResourceLocation> iconCallback) {
        if (this.staleIcons != null) {
            ResourceLocation staleIcon = this.staleIcons.get(pack.getId());
            if (staleIcon != null) {
                iconCallback.accept(staleIcon);
            }
        }

        ResourceLocation cachedIcon = this.cachedIcons.get(pack.getId());
        if (cachedIcon != null) {
            iconCallback.accept(cachedIcon);
        } else {
            PackAssets.loadPackIcon(pack).thenAcceptAsync(location -> {
                this.cachedIcons.put(pack.getId(), location);
                iconCallback.accept(location);
            }, Minecraft.getInstance());
        }
    }

    public void clearIconCache() {
        this.staleIcons = new Object2ObjectOpenHashMap<>(this.cachedIcons);
        this.cachedIcons.clear();
    }

    @Override
    public boolean isResourcePacks() {
        return this.resourcePacks;
    }

    @Override
    public Path getDirectory() {
        return this.packDir;
    }

    public @Nullable Folder getFolderConfig(FolderPack folderPack) {
        CompletableFuture<Folder> future = this.folderConfigs.get(folderPack.getId());
        return future != null ? future.join() : null;
    }

    @Override
    public List<Pack> getNestedPacks(FolderPack folderPack) {
        return this.validateAndOrderNestedPackIds(folderPack, Objects.requireNonNull(this.getFolderConfig(folderPack)).getPackIds());
    }

    public record PackGroup(ImmutableList<Pack> selected, ImmutableList<Pack> unselected) {
        private static PackGroup of(List<Pack> selected, List<Pack> unselected) {
            return new PackGroup(ImmutableList.copyOf(selected), ImmutableList.copyOf(unselected));
        }
    }

    private static ResourceLocation _getIcon(Pack pack) {
        return DEFAULT_ICON; // placeholder
    }

    private static void _update() {
        // placeholder
    }

    private static void _apply(PackRepository repository) {
        // placeholder
    }
}
