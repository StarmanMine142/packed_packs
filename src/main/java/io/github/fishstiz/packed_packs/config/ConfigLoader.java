package io.github.fishstiz.packed_packs.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.fishstiz.packed_packs.PackedPacks;

import java.io.*;

public class ConfigLoader {
    private static final Gson GSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();

    private ConfigLoader() {
    }

    public static <T extends Serializable> T load(InputStream inputStream, Class<T> spec) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(inputStream)) {
            return GSON.fromJson(reader, spec);
        }
    }

    public static Config load(File file) {
        Config config = new Config();

        try (FileReader reader = new FileReader(file)) {
            config = GSON.fromJson(reader, Config.class);
        } catch (FileNotFoundException e) {
            PackedPacks.LOGGER.info("[packed_packs] Creating config at '{}'...", file.getPath());
            save(config, file);
        } catch (IOException e) {
            PackedPacks.LOGGER.error("[packed_packs] Failed to load config at '{}'.", file.getPath());
        }

        config.file = file;
        return config;
    }

    public static <T extends Serializable> void save(T config, File file) {
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            PackedPacks.LOGGER.info("[packed_packs] Failed to save config at '{}'.", file.getPath());
        }
    }
}
