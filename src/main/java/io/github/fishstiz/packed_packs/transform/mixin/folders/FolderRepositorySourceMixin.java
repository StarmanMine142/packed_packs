package io.github.fishstiz.packed_packs.transform.mixin.folders;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import io.github.fishstiz.packed_packs.PackedPacks;
import io.github.fishstiz.packed_packs.transform.interfaces.NestedPack;
import io.github.fishstiz.packed_packs.util.pack.PackUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackDetector;
import net.minecraft.world.level.validation.DirectoryValidator;
import net.minecraft.world.level.validation.ForbiddenSymlinkInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Mixin(FolderRepositorySource.class)
public abstract class FolderRepositorySourceMixin {
    /**
     * Tracks whether we're already inside a nested pack discovery call. Used to limit traversal depth to one level.
     */
    @Unique
    private static final ThreadLocal<Boolean> IS_DISCOVERING_CHILD = ThreadLocal.withInitial(() -> false);

    @Shadow
    @Final
    private Path folder;

    @ModifyArg(method = "method_45272", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/packs/repository/Pack;readMetaAndCreate(Lnet/minecraft/server/packs/PackLocationInfo;Lnet/minecraft/server/packs/repository/Pack$ResourcesSupplier;Lnet/minecraft/server/packs/PackType;Lnet/minecraft/server/packs/PackSelectionConfig;)Lnet/minecraft/server/packs/repository/Pack;"
    ))
    private PackLocationInfo addDirInNestedPackId(PackLocationInfo location, @Local(argsOnly = true) Path path, @Share("nested") LocalBooleanRef nestedRef) {
        try {
            Path parent = path.getParent();
            if (!Files.isSameFile(parent, this.folder) && Files.isSameFile(parent.getParent(), this.folder)) {
                nestedRef.set(true);

                String folderName = nameFromPath(parent) + PackUtil.DIRECTORY_DELIMITER;
                String id = PackUtil.FILE_PREFIX + folderName + nameFromPath(path);

                return new PackLocationInfo(
                        id,
                        Component.literal(folderName).append(location.title()),
                        location.source(),
                        location.knownPackInfo()
                );
            }
        } catch (IOException ignore) {
        }
        return location;
    }

    @ModifyArg(method = "method_45272", at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"))
    private Object bindDirToNestedPack(Object arg, @Local(argsOnly = true) Path path, @Share("nested") LocalBooleanRef nestedRef) {
        if (arg instanceof NestedPack pack) {
            pack.packed_packs$setNestedPack(nestedRef.get());
        }
        return arg;
    }

    @Inject(method = "loadPacks", at = @At("RETURN"))
    private void resetChildDiscoveryFlag(Consumer<Pack> consumer, CallbackInfo ci) {
        IS_DISCOVERING_CHILD.remove();
    }

    @WrapOperation(method = "discoverPacks", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/packs/repository/FolderRepositorySource$FolderPackDetector;detectPackResources(Ljava/nio/file/Path;Ljava/util/List;)Ljava/lang/Object;"
    ))
    private static Object discoverNestedPacks(
            @Coerce PackDetector<Pack.ResourcesSupplier> instance,
            Path path,
            List<ForbiddenSymlinkInfo> list,
            Operation<Object> original,
            @Local(argsOnly = true) DirectoryValidator validator,
            @Local(argsOnly = true) BiConsumer<Path, Pack.ResourcesSupplier> output
    ) {
        if (Files.isDirectory(path) && !PackUtil.hasMcmeta(path)) {
            boolean isRoot = !IS_DISCOVERING_CHILD.get();
            try {
                if (isRoot) {
                    IS_DISCOVERING_CHILD.set(true);
                    discoverPacks(path, validator, output);
                }
            } catch (IOException e) {
                PackedPacks.LOGGER.warn("[packed_packs] Failed to list packs in {}", path, e);
            } finally {
                if (isRoot) {
                    IS_DISCOVERING_CHILD.remove();
                }
            }
        }

        return original.call(instance, path, list);
    }

    @Shadow
    public static void discoverPacks(Path folder, DirectoryValidator validator, BiConsumer<Path, Pack.ResourcesSupplier> output) throws IOException {
        throw new AssertionError();
    }

    @Shadow
    private static String nameFromPath(Path path) {
        throw new AssertionError();
    }
}
