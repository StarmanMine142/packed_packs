package io.github.fishstiz.packed_packs.util.pack;

import io.github.fishstiz.packed_packs.config.ConfigLoader;
import io.github.fishstiz.packed_packs.config.Folder;
import io.github.fishstiz.packed_packs.transform.interfaces.NestedPack;
import io.github.fishstiz.packed_packs.util.ResourceUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.flag.FeatureFlagSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class FolderPack extends Pack implements NestedPack {
    public static final String FOLDER_CONFIG_FILENAME = "folder.json";
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
    public static final PackSelectionConfig FOLDER_SELECTION_CONFIG = new PackSelectionConfig(false, Position.TOP, false);
    public static final Metadata FOLDER_METADATA = new Metadata(FOLDER_DESCRIPTION, PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), Collections.emptyList());

    private FolderPack(PackLocationInfo packLocationInfo, Path parent) {
        super(packLocationInfo, new FolderResourcesSupplier(parent), FOLDER_METADATA, FOLDER_SELECTION_CONFIG);
    }

    public FolderPack(String id, String name, Path parent) {
        this(new PackLocationInfo(id, Component.literal(name), FOLDER_SOURCE, Optional.empty()), parent);
    }

    public CompletableFuture<Folder> loadConfig() {
        return CompletableFuture.supplyAsync(() -> {
            try (PackResources resources = this.open()) {
                var configIoSupplier = resources.getRootResource(FOLDER_CONFIG_FILENAME);
                if (configIoSupplier == null) {
                    throw new IOException();
                }
                return ConfigLoader.load(configIoSupplier.get(), Folder.class);
            } catch (IOException e) {
                return new Folder();
            }
        });
    }

    public void saveConfig(Folder folder) {
        if (folder != null) {
            try (PackResources resources = this.open()) {
                ConfigLoader.save(folder, ((FolderResources) resources).getRoot().resolve(FOLDER_CONFIG_FILENAME).toFile());
            }
        }
    }

    public record FolderResources(PackLocationInfo location, Path parent) implements PackResources {
        private Path getRoot() {
            return PackUtil.getPath(this.parent, this.location.id());
        }

        @Override
        public @Nullable IoSupplier<InputStream> getRootResource(String... elements) {
            if (elements.length > 0) {
                if (elements[0].equals(PackAssets.ICON_FILENAME)) {
                    return () -> Files.newInputStream(this.getRoot().resolve(PackAssets.ICON_FILENAME));
                } else if (elements[0].equals(FOLDER_CONFIG_FILENAME)) {
                    return () -> Files.newInputStream(this.getRoot().resolve(FOLDER_CONFIG_FILENAME));
                }
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
