package dev.miningplus.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigReferenceWriter {
    private static final int CURRENT_CONFIG_VERSION = 17;
    private static final String[] SECTION_CONFIG_FILES = {
            "sections/progression.yml",
            "sections/mining.yml",
            "sections/abilities-events.yml",
            "sections/artifacts.yml",
            "sections/economy.yml",
            "sections/journal.yml",
            "sections/commissions.yml",
            "sections/shop.yml",
            "sections/interface.yml"
    };

    private ConfigReferenceWriter() {
    }

    public static void saveDefaultAndReferenceIfNeeded(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        saveDefaultSectionFiles(plugin);
        plugin.reloadConfig();

        int installedVersion = plugin.getConfig().getInt("config-version", 0);
        if (installedVersion >= CURRENT_CONFIG_VERSION) {
            return;
        }

        Path referencePath = plugin.getDataFolder().toPath().resolve("config-new.yml");
        try (InputStream inputStream = plugin.getResource("config.yml")) {
            if (inputStream == null) {
                plugin.getLogger().warning("Could not find bundled config.yml for update reference.");
                return;
            }
            byte[] bundledConfig = inputStream.readAllBytes();
            if (Files.exists(referencePath) && Arrays.equals(Files.readAllBytes(referencePath), bundledConfig)) {
                plugin.getLogger().warning("Your config.yml is outdated or missing config-version.");
                plugin.getLogger().warning("config-new.yml already exists with the latest reference config.");
                return;
            }
            Files.createDirectories(referencePath.getParent());
            Files.write(referencePath, bundledConfig);
            plugin.getLogger().warning("Your config.yml is outdated or missing config-version.");
            plugin.getLogger().warning("A new reference config was saved to config-new.yml.");
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save config-new.yml: " + e.getMessage());
        }
    }

    private static void saveDefaultSectionFiles(JavaPlugin plugin) {
        for (String resourcePath : SECTION_CONFIG_FILES) {
            Path destination = plugin.getDataFolder().toPath().resolve(resourcePath);
            if (Files.exists(destination)) {
                continue;
            }
            try {
                plugin.saveResource(resourcePath, false);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Could not find bundled config section '" + resourcePath + "'.");
            }
        }
    }
}
