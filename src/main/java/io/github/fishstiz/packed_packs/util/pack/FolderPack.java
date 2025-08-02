package io.github.fishstiz.packed_packs.util.pack;

import io.github.fishstiz.packed_packs.transform.interfaces.NestedPack;
import io.github.fishstiz.packed_packs.util.ResourceUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.flag.FeatureFlagSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public final class FolderPack extends Pack implements NestedPack {
    public static final Component FOLDER_DESCRIPTION = ResourceUtil.getText("folder");
    public static final PackSource FOLDER_SOURCE = PackSource.create(
            name -> Component.translatable(
                            "pack.nameAndSource",
                            name,
                            ResourceUtil.getModName().withStyle(ChatFormatting.YELLOW)
                    )
                    .withStyle(ChatFormatting.GRAY),
            false
    );
    public static final PackSelectionConfig FOLDER_SELECTION_CONFIG = new PackSelectionConfig(false, Pack.Position.TOP, false);
    public static final Metadata FOLDER_METADATA = new Metadata(FOLDER_DESCRIPTION, PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), Collections.emptyList());

    private FolderPack(PackLocationInfo packLocationInfo, Path parent) {
        super(packLocationInfo, new FolderResourcesSupplier(parent), FOLDER_METADATA, FOLDER_SELECTION_CONFIG);
    }

    public FolderPack(String id, String name, Path parent) {
        this(new PackLocationInfo(id, Component.literal(name), FOLDER_SOURCE, Optional.empty()), parent);
    }

    @Override
    public final boolean packed_packs$nestedPack() {
        return false;
    }

    public record FolderResources(PackLocationInfo location, Path parent) implements PackResources {
        @Override
        public @Nullable IoSupplier<InputStream> getRootResource(String... elements) {
            if (elements.length > 0 && elements[0].equals(PackAssets.ICON_FILENAME)) {
                return () -> Files.newInputStream(PackUtil.getPath(this.parent, this.location.id()).resolve(PackAssets.ICON_FILENAME));
            }

            return null;
        }

        @Override
        public @Nullable IoSupplier<InputStream> getResource(PackType packType, ResourceLocation location) {
            return null;
        }

        @Override
        public void listResources(PackType packType, String namespace, String path, ResourceOutput resourceOutput) {
            // no-op
        }

        @Override
        public @NotNull Set<String> getNamespaces(PackType type) {
            return Collections.emptySet();
        }

        @Override
        public @Nullable <T> T getMetadataSection(MetadataSectionType<T> type) {
            return null;
        }

        @Override
        public void close() {
            // no-op
        }
    }

    public record FolderResourcesSupplier(Path parent) implements ResourcesSupplier {
        @Override
        public @NotNull FolderResources openPrimary(PackLocationInfo location) {
            return new FolderResources(location, this.parent);
        }

        @Override
        public @NotNull FolderResources openFull(PackLocationInfo location, Metadata metadata) {
            return this.openPrimary(location);
        }
    }
}
