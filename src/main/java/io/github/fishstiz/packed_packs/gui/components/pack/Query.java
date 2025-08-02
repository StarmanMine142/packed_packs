package io.github.fishstiz.packed_packs.gui.components.pack;

import io.github.fishstiz.fidgetz.gui.components.CyclicButton;
import io.github.fishstiz.fidgetz.gui.renderables.sprites.ButtonSprites;
import io.github.fishstiz.fidgetz.gui.renderables.sprites.Sprite;
import io.github.fishstiz.fidgetz.gui.shapes.Size;
import io.github.fishstiz.packed_packs.util.ResourceUtil;
import io.github.fishstiz.packed_packs.util.pack.FolderPack;
import io.github.fishstiz.packed_packs.util.pack.PackUtil;
import net.fabricmc.fabric.impl.resource.loader.BuiltinModResourcePackSource;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class Query {
    private final Path packDir;
    private boolean hideIncompatible = false;
    private SortOption sort;
    private String search;

    Query(Path packDir) {
        this.packDir = packDir;
    }

    Query(Path packDir, boolean hideIncompatible, SortOption sort, String search) {
        this(packDir);

        this.hideIncompatible = hideIncompatible;
        this.sort = sort;
        this.search = search;
    }

    boolean setHideIncompatible(boolean hideIncompatible) {
        boolean updated = this.hideIncompatible != hideIncompatible;
        this.hideIncompatible = hideIncompatible;
        return updated;
    }

    boolean setSort(SortOption sort) {
        boolean updated = !Objects.equals(this.sort, sort);
        this.sort = sort;
        return updated;
    }

    boolean setSearch(String search) {
        boolean updated = !Objects.equals(this.search, search);
        this.search = search;
        return updated;
    }

    boolean update(boolean incompatibleHidden, SortOption sort, String search) {
        boolean updated = this.setHideIncompatible(incompatibleHidden);
        updated |= this.setSort(sort);
        updated |= this.setSearch(search);
        return updated;
    }

    boolean update(Query query) {
        return this.update(query.hideIncompatible, query.sort, query.search);
    }

    void apply(final List<Pack> packs) {
        Objects.requireNonNull(packs);

        if (this.hideIncompatible) {
            packs.removeIf(pack -> !pack.getCompatibility().isCompatible());
        }
        if (this.search != null && !this.search.isEmpty()) {
            packs.removeIf(pack -> !normalizeTitle(pack.getTitle().getString()).toLowerCase().contains(this.search.toLowerCase()));
        }
        if (this.sort != null) {
            packs.sort(this.sort.createComparator(this.packDir));
        }
    }

    boolean isQuerying() {
        return this.hideIncompatible || (this.search != null && !this.search.isEmpty()) || this.sort != null;
    }

    public Query copy() {
        return new Query(this.packDir, this.hideIncompatible, this.sort, this.search);
    }

    public boolean isHideIncompatible() {
        return this.hideIncompatible;
    }

    public SortOption getSort() {
        return this.sort;
    }

    public String getSearch() {
        return this.search;
    }

    public enum SortOption implements CyclicButton.SpriteOption {
        VANILLA("sort.vanilla", "sort_vanilla", (first, second) -> {
            boolean builtInFirst = isBuiltIn(first.getPackSource());
            boolean builtInSecond = isBuiltIn(second.getPackSource());
            if (builtInFirst != builtInSecond) return builtInFirst ? 1 : -1;
            return first.getTitle().getString().compareTo(second.getTitle().getString());
        }),
        A_Z("sort.a_z", "sort_a_z", Comparator.comparing(
                pack -> normalizeTitle(pack.getTitle().getString()),
                String.CASE_INSENSITIVE_ORDER
        )),
        Z_A("sort.z_a", "sort_z_a", A_Z.createComparator(null).reversed()),
        RECENT("sort.recent", "sort_recent", ComparatorFactory.createDynamic((Path directory) ->
                Comparator.<Pack>comparingLong(pack -> PackUtil.getLastUpdatedEpochMs(directory, pack)).reversed()
        )),
        OLDEST("sort.oldest", "sort_oldest", ComparatorFactory.createDynamic((Path directory) ->
                RECENT.createComparator(directory).reversed()
        ));

        private final Component component;
        private final Tooltip tooltip;
        private final ButtonSprites sprites;
        private final ComparatorFactory<?> comparatorFactory;
        private Comparator<Pack> cachedComparator;

        <T> SortOption(String key, String icon, ComparatorFactory<T> comparatorFactory) {
            this.component = ResourceUtil.getText(key);
            this.tooltip = Tooltip.create(this.component);
            this.sprites = ButtonSprites.of(new Sprite(ResourceUtil.getIcon(icon), Size.of16()));
            this.comparatorFactory = comparatorFactory;
        }

        SortOption(String key, String icon, Comparator<Pack> comparator) {
            this(key, icon, arg -> comparator);
        }

        @Override
        public @NotNull Component text() {
            return this.component;
        }

        public <T> @NotNull Comparator<Pack> createComparator(T arg) {
            if (this.cachedComparator != null) {
                return this.cachedComparator;
            }

            @SuppressWarnings("unchecked")
            ComparatorFactory<T> factory = (ComparatorFactory<T>) this.comparatorFactory;
            Comparator<Pack> comparator = ComparatorFactory.folderFirst(factory.createComparator(arg));

            if (!factory.isDynamic()) {
                this.cachedComparator = comparator;
            }

            return comparator;
        }

        @Override
        public @Nullable Tooltip tooltip() {
            return this.tooltip;
        }

        @Override
        public @Nullable ButtonSprites sprites() {
            return this.sprites;
        }

        @FunctionalInterface
        private interface ComparatorFactory<T> {
            Comparator<Pack> createComparator(T arg);

            default boolean isDynamic() {
                return false;
            }

            static Comparator<Pack> folderFirst(Comparator<Pack> base) {
                return Comparator
                        .comparing((Pack pack) -> !(pack instanceof FolderPack))
                        .thenComparing(base);
            }

            static <T> ComparatorFactory<T> createDynamic(ComparatorFactory<T> factory) {
                return new ComparatorFactory<>() {
                    @Override
                    public Comparator<Pack> createComparator(T arg) {
                        return factory.createComparator(arg);
                    }

                    @Override
                    public boolean isDynamic() {
                        return true;
                    }
                };
            }
        }
    }

    private static boolean isBuiltIn(PackSource packSource) {
        //noinspection UnstableApiUsage
        return packSource == PackSource.BUILT_IN || packSource instanceof BuiltinModResourcePackSource;
    }

    private static String normalizeTitle(String title) {
        return title
                .replaceAll("§.", "") // remove formatting
                .trim();
    }
}
