package io.github.fishstiz.packed_packs.util.pack;

import com.google.common.hash.Hashing;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.fishstiz.packed_packs.PackedPacks;
import io.github.fishstiz.packed_packs.config.Config;
import io.github.fishstiz.packed_packs.util.ResourceUtil;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface PackAssets {
    String ICON_FILENAME = "pack.png";
    ResourceLocation DEFAULT_FOLDER_ICON = ResourceUtil.getResource("textures/misc/unknown_folder.png");
    ResourceLocation DEFAULT_ICON = ResourceLocation.withDefaultNamespace("textures/misc/unknown_pack.png");

    void getOrLoadIcon(Pack pack, Consumer<ResourceLocation> iconCallback);

    boolean isResourcePacks();

    Path getDirectory();

    List<Pack> getNestedPacks(FolderPack subdirectory);

    default Config.Packs getConfig() {
        return this.isResourcePacks() ? PackedPacks.CONFIG.getResourcepacks() : PackedPacks.CONFIG.getDatapacks();
    }

    static ResourceLocation getDefaultIcon(Pack pack) {
        return pack instanceof FolderPack ? DEFAULT_FOLDER_ICON : DEFAULT_ICON;
    }

    /**
     * Copied from {@link PackSelectionScreen#loadPackIcon(TextureManager, Pack)}
     */
    @SuppressWarnings("all")
    static CompletableFuture<ResourceLocation> loadPackIcon(Pack pack) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ResourceLocation packIcon;
                try (PackResources packResources = pack.open()) {
                    IoSupplier<InputStream> iconIoSupplier = packResources.getRootResource(ICON_FILENAME);

                    if (iconIoSupplier == null) {
                        return getDefaultIcon(pack);
                    }

                    String id = pack.getId();
                    ResourceLocation resourceLocation = ResourceLocation.withDefaultNamespace(
                            "pack/" + Util.sanitizeName(id, ResourceLocation::validPathChar) + "/" + Hashing.sha1().hashUnencodedChars(id) + "/icon"
                    );
                    InputStream iconStream = iconIoSupplier.get();

                    try {
                        NativeImage nativeImage = NativeImage.read(iconStream);
                        TextureManager manager = Minecraft.getInstance().getTextureManager();
                        Minecraft.getInstance().execute(() -> manager.register(resourceLocation, new DynamicTexture(resourceLocation::toString, nativeImage)));
                        packIcon = resourceLocation;
                    } catch (Throwable e) {
                        if (iconStream != null) {
                            try {
                                iconStream.close();
                            } catch (Throwable e2) {
                                e.addSuppressed(e2);
                            }
                        }
                        throw e;
                    }
                    if (iconStream != null) {
                        iconStream.close();
                    }
                }
                return packIcon;
            } catch (Exception e) {
                if (!(e instanceof NoSuchFileException)) {
                    PackedPacks.LOGGER.warn("Failed to load icon from pack '{}'", pack.getId(), e);
                }
                return getDefaultIcon(pack);
            }
        });
    }
}
