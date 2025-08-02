package io.github.fishstiz.packed_packs.util.pack;

import io.github.fishstiz.packed_packs.PackedPacks;
import io.github.fishstiz.packed_packs.transform.interfaces.NestedPack;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackDetector;
import net.minecraft.world.level.validation.ForbiddenSymlinkInfo;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class PackUtil {
    public static final String FILE_PREFIX = "file/";
    public static final String FILE_PREFIX_REGEX = "^" + Pattern.quote(FILE_PREFIX);
    public static final String DIRECTORY_DELIMITER = "/";

    private PackUtil() {
    }

    public static boolean isFile(Pack pack) {
        return pack.getId().startsWith(FILE_PREFIX);
    }

    public static String getSubdirectoryName(Pack pack) {
        return StringUtils.substringBetween(pack.getId(), DIRECTORY_DELIMITER);
    }

    public static String getFileName(String packId) {
        return packId.replaceFirst(FILE_PREFIX_REGEX, "");
    }

    public static String getFileName(Pack pack) {
        return ((NestedPack) pack).packed_packs$nestedPack()
                ? pack.getId().replaceFirst(FILE_PREFIX_REGEX + ".*" + Pattern.quote(DIRECTORY_DELIMITER), "")
                : getFileName(pack.getId());
    }

    public static Path getPath(Path root, String packId) {
        return root.resolve(getFileName(packId));
    }

    public static Path getPath(Path root, Pack pack) {
        return ((NestedPack) pack).packed_packs$nestedPack()
                ? root.resolve(getSubdirectoryName(pack)).resolve(getFileName(pack))
                : getPath(root, getFileName(pack));
    }

    public static long getLastUpdatedEpochMs(Path root, Pack pack) {
        if (!isFile(pack)) {
            return -1;
        }

        try {
            Path path = getPath(root, pack);
            return Files.getLastModifiedTime(path).toInstant().toEpochMilli();
        } catch (IOException e) {
            PackedPacks.LOGGER.error("Failed to get age of pack '{}'", pack.getId());
            return -1;
        }
    }

    public static Stream<String> extractPackNames(Collection<Path> paths) {
        return paths.stream().map(Path::getFileName).map(Path::toString);
    }

    public static boolean hasMcmeta(Path path) {
        return Files.isRegularFile(path.resolve(PackResources.PACK_META));
    }

    public static PackDetector<Path> createPackDetector() {
        return new PackDetector<>(Minecraft.getInstance().directoryValidator()) {
            @Override
            protected Path createZipPack(Path path) {
                return path;
            }

            @Override
            protected Path createDirectoryPack(Path path) {
                return path;
            }
        };
    }

    public static PathValidationResults validatePaths(List<Path> packs, PackDetector<Path> packDetector) {
        List<Path> valid = new ArrayList<>(packs.size());
        Set<Path> rejected = new HashSet<>(packs);
        List<ForbiddenSymlinkInfo> symlinkWarnings = new ArrayList<>();

        for (Path path : packs) {
            try {
                Path detectedPack = packDetector.detectPackResources(path, symlinkWarnings);
                if (detectedPack == null) {
                    PackedPacks.LOGGER.warn("Path {} does not seem like pack", path);
                } else {
                    valid.add(detectedPack);
                    rejected.remove(detectedPack);
                }
            } catch (IOException e) {
                PackedPacks.LOGGER.warn("Failed to check {} for packs", path, e);
            }
        }
        return new PathValidationResults(valid, rejected, symlinkWarnings);
    }

    public record PathValidationResults(List<Path> valid, Set<Path> rejected,
                                        List<ForbiddenSymlinkInfo> symlinkWarnings) {
    }
}
