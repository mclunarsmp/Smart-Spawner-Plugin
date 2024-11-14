package me.nighter.smartSpawner.managers;

import me.nighter.smartSpawner.SmartSpawner;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigManager {
    private static ConfigManager instance;
    private final SmartSpawner plugin;
    private FileConfiguration config;
    private FileConfiguration lootConfig;
    private File configFile;
    private File lootConfigFile;
    private boolean debug;

    // Cache cho các giá trị config thường xuyên sử dụng
    private Map<String, Object> configCache;

    public ConfigManager(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configCache = new ConcurrentHashMap<>();
        loadConfigs();
    }

    private void loadConfigs() {
        loadMainConfig();
        loadLootConfig();
        initializeCache();
    }

    private void loadMainConfig() {
        plugin.saveDefaultConfig();
        config = plugin.getConfig();
        configFile = new File(plugin.getDataFolder(), "config.yml");

        // Thêm các cấu hình mặc định cho update checker nếu chưa có
        if (!config.contains("update-checker")) {
            config.set("update-checker.enabled", true);
            config.set("update-checker.check-interval", 24); // hours
            config.set("update-checker.notify-ops", true);
            config.set("update-checker.notify-on-join", true);
            saveMainConfig();
        }

        debug = config.getBoolean("settings.debug", false);
    }

    public void loadLootConfig() {
        lootConfigFile = new File(plugin.getDataFolder(), "mob_drops.yml");
        if (!lootConfigFile.exists()) {
            debug("Creating default mob_drops.yml");
            plugin.saveResource("mob_drops.yml", false);
        }

        lootConfig = YamlConfiguration.loadConfiguration(lootConfigFile);
        mergeLootDefaults();
    }

    private void mergeLootDefaults() {
        try (InputStream defaultLootConfigStream = plugin.getResource("mob_drops.yml")) {
            if (defaultLootConfigStream != null) {
                YamlConfiguration defaultLootConfig = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultLootConfigStream, StandardCharsets.UTF_8)
                );

                for (String key : defaultLootConfig.getKeys(true)) {
                    if (!lootConfig.contains(key)) {
                        debug("Adding missing default config value: " + key);
                        lootConfig.set(key, defaultLootConfig.get(key));
                    }
                }
                saveLootConfig();
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Error loading default loot config: " + e.getMessage());
        }
    }

    private void initializeCache() {
        configCache.clear();

        // Cache spawner settings
        configCache.put("spawner.default-entity", config.getString("spawner.default-entity"));
        configCache.put("spawner.max-stored-exp", config.getInt("spawner.max-stored-exp"));
        configCache.put("spawner.allow-exp-mending", config.getBoolean("spawner.allow-exp-mending"));
        configCache.put("spawner.min-mobs", config.getInt("spawner.min-mobs"));
        configCache.put("spawner.max-mobs", config.getInt("spawner.max-mobs"));
        configCache.put("spawner.range", config.getInt("spawner.range"));
        configCache.put("spawner.allow-toggle-equipment-items", config.getBoolean("spawner.allow-toggle-equipment-items"));
        configCache.put("spawner.delay", config.getInt("spawner.delay"));
        configCache.put("spawner.max-stack-size", config.getInt("spawner.max-stack-size"));
        configCache.put("spawner.allow-grief", config.getBoolean("spawner.allow-grief"));

        // Cache break settings
        configCache.put("spawner-break.enabled", config.getBoolean("spawner-break.enabled"));
        configCache.put("spawner-break.durability-loss", config.getInt("spawner-break.durability-loss-per-spawner"));
        configCache.put("spawner-break.silk-touch.required", config.getBoolean("spawner-break.silk-touch.required"));
        configCache.put("spawner-break.silk-touch.level", config.getInt("spawner-break.silk-touch.level"));
        configCache.put("spawner-break.drop-stack.amount", config.getInt("spawner-break.drop-stack.amount"));

        // Cache performance settings
        configCache.put("performance.batch-size", config.getInt("performance.batch-size"));

        // Cache general settings
        configCache.put("settings.language", config.getString("settings.language"));
        configCache.put("settings.debug", config.getBoolean("settings.debug"));
        configCache.put("settings.save-interval", config.getInt("settings.save-interval"));
    }

    public void reloadConfigs() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        lootConfig = YamlConfiguration.loadConfiguration(lootConfigFile);
        mergeLootDefaults();
        initializeCache();
        debug = config.getBoolean("settings.debug", false);
    }

    public void saveConfigs() {
        saveMainConfig();
        saveLootConfig();
        debug("Configurations saved successfully");
    }

    private void saveMainConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save main config: " + e.getMessage());
        }
    }

    private void saveLootConfig() {
        try {
            lootConfig.save(lootConfigFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save loot config: " + e.getMessage());
        }
    }

    public void debug(String message) {
        if (debug) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }

    public FileConfiguration getMainConfig() {
        return config;
    }

    public FileConfiguration getLootConfig() {
        return lootConfig;
    }

    private void setDefaultIfNotExists(String path, Object defaultValue) {
        if (!config.contains(path)) {
            config.set(path, defaultValue);
            plugin.saveConfig();
        }
    }

    public boolean isUpdateCheckerEnabled() {
        return (boolean) configCache.computeIfAbsent("update-checker.enabled", key -> {
            boolean defaultValue = true;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    public int getUpdateCheckInterval() {
        return (int) configCache.computeIfAbsent("update-checker.check-interval", key -> {
            int defaultValue = 24;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public boolean shouldNotifyOps() {
        return (boolean) configCache.computeIfAbsent("update-checker.notify-ops", key -> {
            boolean defaultValue = true;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    public boolean shouldNotifyOnJoin() {
        return (boolean) configCache.computeIfAbsent("update-checker.notify-on-join", key -> {
            boolean defaultValue = true;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    public int getSpawnerRange() {
        return (int) configCache.computeIfAbsent("spawner.range", key -> {
            int defaultValue = 16;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public int getBatchSize() {
        return (int) configCache.computeIfAbsent("spawner.batch-size", key -> {
            int defaultValue = 5;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public boolean isAllowExpMending() {
        return (boolean) configCache.computeIfAbsent("spawner.allow-exp-mending", key -> {
            boolean defaultValue = true;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    public boolean isAllowGrief() {
        return (boolean) configCache.computeIfAbsent("spawner.allow-grief", key -> {
            boolean defaultValue = false;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    public boolean isAllowToggleEquipmentItems() {
        return (boolean) configCache.computeIfAbsent("spawner.allow-toggle-equipment-items", key -> {
            boolean defaultValue = false;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    public EntityType getDefaultEntityType() {
        return (EntityType) configCache.computeIfAbsent("spawner.default-entity", key -> {
            String defaultValue = "PIG";
            setDefaultIfNotExists(key, defaultValue);
            String entityName = config.getString(key, defaultValue).toUpperCase();
            try {
                return EntityType.valueOf(entityName);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid default entity type: " + entityName);
                return EntityType.PIG;
            }
        });
    }

    public int getMaxStackSize() {
        return (int) configCache.computeIfAbsent("spawner.max-stack-size", key -> {
            int defaultValue = 100;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public boolean isSpawnerBreakEnabled() {
        return (boolean) configCache.computeIfAbsent("spawner-break.enabled", key -> {
            boolean defaultValue = true;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    @SuppressWarnings("unchecked")
    public List<String> getRequiredTools() {
        return (List<String>) configCache.computeIfAbsent("spawner-break.required-tools", key -> {
            List<String> defaultValue = Arrays.asList(
                    "STONE_PICKAXE",
                    "IRON_PICKAXE",
                    "GOLDEN_PICKAXE",
                    "DIAMOND_PICKAXE",
                    "NETHERITE_PICKAXE"
            );
            setDefaultIfNotExists(key, defaultValue);
            return config.getStringList(key);
        });
    }

    public boolean isSilkTouchRequired() {
        return (boolean) configCache.computeIfAbsent("spawner-break.silk-touch.required", key -> {
            boolean defaultValue = false;
            setDefaultIfNotExists(key, defaultValue);
            return config.getBoolean(key, defaultValue);
        });
    }

    public int getSilkTouchLevel() {
        return (int) configCache.computeIfAbsent("spawner-break.silk-touch.level", key -> {
            int defaultValue = 1;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public int getDropStackAmount() {
        return (int) configCache.computeIfAbsent("spawner-break.drop-stack.amount", key -> {
            int defaultValue = 64;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public int getDurabilityLossPerSpawner() {
        return (int) configCache.computeIfAbsent("spawner-break.durability-loss-per-spawner", key -> {
            int defaultValue = 1;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }

    public int getSaveInterval() {
        return (int) configCache.computeIfAbsent("settings.save-interval", key -> {
            int defaultValue = 6000;
            setDefaultIfNotExists(key, defaultValue);
            return config.getInt(key, defaultValue);
        });
    }
}