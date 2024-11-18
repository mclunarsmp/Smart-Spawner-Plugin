package me.nighter.smartSpawner.managers;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.utils.SpawnerData;
import me.nighter.smartSpawner.utils.VirtualInventory;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerLootGenerator {
    private final SmartSpawner plugin;
    private final Random random;
    private final Map<String, EntityLootConfig> entityLootConfigs;
    private final ConfigManager configManager;

    public SpawnerLootGenerator(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.random = new Random();
        this.entityLootConfigs = new ConcurrentHashMap<>();
        loadConfigurations();
    }

    // Cache for entity configurations
    private static class EntityLootConfig {
        final int experience;
        final List<LootItem> possibleItems;

        EntityLootConfig(int experience, List<LootItem> items) {
            this.experience = experience;
            this.possibleItems = items;
        }
    }

    // Cache structure for item configuration
    private static class LootItem {
        final Material material;
        final int minAmount;
        final int maxAmount;
        final double chance;
        final Integer minDurability;
        final Integer maxDurability;
        final String potionEffectType;
        final Integer potionDuration;
        final Integer potionAmplifier;

        LootItem(Material material, int minAmount, int maxAmount, double chance,
                 Integer minDurability, Integer maxDurability, String potionEffectType,
                 Integer potionDuration, Integer potionAmplifier) {
            this.material = material;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
            this.chance = chance;
            this.minDurability = minDurability;
            this.maxDurability = maxDurability;
            this.potionEffectType = potionEffectType;
            this.potionDuration = potionDuration;
            this.potionAmplifier = potionAmplifier;
        }

        // Constructor cũ để đảm bảo tương thích ngược
        LootItem(Material material, int minAmount, int maxAmount, double chance,
                 Integer minDurability, Integer maxDurability) {
            this(material, minAmount, maxAmount, chance, minDurability, maxDurability,
                    null, null, null);
        }

        public ItemStack createItemStack(Random random) {
            ItemStack item = new ItemStack(material, 1); // Amount sẽ được set sau
            if (minDurability != null && maxDurability != null) {
                ItemMeta meta = item.getItemMeta();
                if (meta instanceof Damageable) {
                    int durability = random.nextInt(maxDurability - minDurability + 1) + minDurability;
                    ((Damageable) meta).setDamage(durability);
                    item.setItemMeta(meta);
                }
            }

            // Xử lý Tipped Arrow
            if (material == Material.TIPPED_ARROW && potionEffectType != null) {
                PotionMeta meta = (PotionMeta) item.getItemMeta();
                if (meta != null) {
                    PotionEffectType effectType = PotionEffectType.getByName(potionEffectType);
                    if (effectType != null && potionDuration != null && potionAmplifier != null) {
                        PotionEffect effect = new PotionEffect(
                                effectType,
                                potionDuration,
                                potionAmplifier,
                                true,  // ambient
                                true,  // particles
                                true   // icon
                        );
                        meta.addCustomEffect(effect, true);
                    }
                    // Format thời gian theo kiểu Minecraft
                    String duration = formatMinecraftDuration(potionDuration);
                    String effectName = formatEffectName(effectType.getName());
                    String level = potionAmplifier > 0 ? " " + toRomanNumeral(potionAmplifier + 1) : "";

                    // Set custom lore
                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.RED + effectName + level + " (" + duration + ")");
                    meta.setLore(lore);
                    meta.setDisplayName("Arrow of " + effectName);
                    item.setItemMeta(meta);
                }
            }

            return item;
        }

        private String formatMinecraftDuration(int ticks) {
            // Minecraft thường hiển thị thời gian dưới dạng mm:ss
            int totalSeconds = ticks / 20;
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            return String.format("%02d:%02d", minutes, seconds);
        }

        private String formatEffectName(String name) {
            String formatted = name.substring(0, 1).toUpperCase() + name.toLowerCase().substring(1);
            return formatted.replace("_", " ");
        }

        private String toRomanNumeral(int number) {
            String[] romanNumerals = {"I", "II", "III", "IV", "V"};
            return " " + (number > 0 && number <= romanNumerals.length ? romanNumerals[number - 1] : String.valueOf(number));
        }
    }

    private void loadConfigurations() {
        ConfigurationSection mobDropSection = configManager.getLootConfig().getConfigurationSection("per_mob_drop");
        if (mobDropSection == null) {
            configManager.debug("No mob drop section found in config");
            return;
        }

        for (String entityName : mobDropSection.getKeys(false)) {
            ConfigurationSection entitySection = mobDropSection.getConfigurationSection(entityName);
            if (entitySection == null) continue;

            configManager.debug("Loading loot configuration for entity: " + entityName);

            int experience = entitySection.getInt("experience", 0);
            List<LootItem> items = new ArrayList<>();

            ConfigurationSection lootSection = entitySection.getConfigurationSection("loot");
            if (lootSection != null) {
                for (String itemKey : lootSection.getKeys(false)) {
                    ConfigurationSection itemSection = lootSection.getConfigurationSection(itemKey);
                    if (itemSection == null) continue;

                    try {
                        Material material = Material.valueOf(itemKey.toUpperCase());
                        String[] amounts = itemSection.getString("amount", "1-1").split("-");
                        int minAmount = Integer.parseInt(amounts[0]);
                        int maxAmount = Integer.parseInt(amounts[1]);
                        double chance = itemSection.getDouble("chance", 100.0);

                        Integer minDurability = null;
                        Integer maxDurability = null;
                        if (itemSection.contains("durability")) {
                            String[] durabilities = itemSection.getString("durability").split("-");
                            minDurability = Integer.parseInt(durabilities[0]);
                            maxDurability = Integer.parseInt(durabilities[1]);
                        }

                        // Xử lý thông tin potion nếu là Tipped Arrow
                        String potionEffectType = null;
                        Integer potionDuration = null;
                        Integer potionAmplifier = null;

                        if (material == Material.TIPPED_ARROW && itemSection.contains("potion_effect")) {
                            ConfigurationSection potionSection = itemSection.getConfigurationSection("potion_effect");
                            if (potionSection != null) {
                                potionEffectType = potionSection.getString("type");
                                potionDuration = potionSection.getInt("duration", 200);
                                potionAmplifier = potionSection.getInt("amplifier", 0);
                            }
                        }

                        items.add(new LootItem(material, minAmount, maxAmount, chance,
                                minDurability, maxDurability, potionEffectType,
                                potionDuration, potionAmplifier));

                        configManager.debug("Loaded item config: " + itemKey +
                                " (Amount: " + minAmount + "-" + maxAmount +
                                ", Chance: " + chance + "%)");

                    } catch (IllegalArgumentException e) {
                        configManager.debug("Error loading item config: " + entityName + " -> " + itemKey +
                                " Error: " + e.getMessage());
                    }
                }
            }

            entityLootConfigs.put(entityName.toLowerCase(), new EntityLootConfig(experience, items));
            configManager.debug("Completed loading " + items.size() + " items for " + entityName);
        }
    }

    public LootResult generateLoot(EntityType entityType, int minMobs, int maxMobs, SpawnerData spawner) {
        String entityName = entityType.name().toLowerCase();
        EntityLootConfig config = entityLootConfigs.get(entityName);

        if (config == null) {
            configManager.debug("No loot config found for entity type: " + entityName);
            return new LootResult(new ArrayList<>(), 0);
        }

        int mobCount = random.nextInt(maxMobs - minMobs + 1) + minMobs;
        List<ItemStack> totalLoot = new ArrayList<>();
        int totalExperience = config.experience * mobCount;

        // Phân nhóm items thành 2 loại
        List<LootItem> guaranteedItems = new ArrayList<>();    // Items với chance 100%
        List<LootItem> randomItems = new ArrayList<>();        // Items với chance < 100%

        // Phân loại items
        for (LootItem item : config.possibleItems) {
            if (item.chance >= 100) {
                guaranteedItems.add(item);
            } else {
                randomItems.add(item);
            }
        }

        // Xử lý guaranteed items một lần cho tất cả mobs
        for (LootItem lootItem : guaranteedItems) {
            if (!spawner.isAllowEquipmentItems() && lootItem.minDurability != null && lootItem.maxDurability != null) {
                continue;
            }

            for (int j = 0; j < mobCount; j++) {
                int amount = random.nextInt(lootItem.maxAmount - lootItem.minAmount + 1) + lootItem.minAmount;
                if (amount > 0) {
                    // Sử dụng phương thức createItemStack của chính LootItem
                    ItemStack item = lootItem.createItemStack(random);
                    if (item != null) {
                        item.setAmount(amount);
                        totalLoot.add(item);
                    }
                }
            }
        }

        final int BATCH_SIZE = configManager.getBatchSize();
        for (int mobStart = 0; mobStart < mobCount; mobStart += BATCH_SIZE) {
            int currentBatchSize = Math.min(BATCH_SIZE, mobCount - mobStart);

            // Tạo random values cho batch này
            double[] batchRandomValues = new double[randomItems.size()];
            for (int i = 0; i < batchRandomValues.length; i++) {
                batchRandomValues[i] = random.nextDouble() * 100;
            }

            // Xử lý từng item trong batch
            for (int i = 0; i < randomItems.size(); i++) {
                LootItem lootItem = randomItems.get(i);

                if (!spawner.isAllowEquipmentItems() && lootItem.minDurability != null && lootItem.maxDurability != null) {
                    continue;
                }

                // Dùng random value cho batch này
                if (batchRandomValues[i] <= lootItem.chance) {
                    for (int j = 0; j < currentBatchSize; j++) {
                        int amount = random.nextInt(lootItem.maxAmount - lootItem.minAmount + 1) + lootItem.minAmount;
                        if (amount > 0) {
                            // Sử dụng phương thức createItemStack của chính LootItem
                            ItemStack item = lootItem.createItemStack(random);
                            if (item != null) {
                                item.setAmount(amount);
                                totalLoot.add(item);
                            }
                        }
                    }
                }
            }
        }

        return new LootResult(totalLoot, totalExperience);
    }

//    private ItemStack createItemStack(LootItem lootItem, int amount) {
//        ItemStack item = new ItemStack(lootItem.material, amount);
//
//        if (lootItem.minDurability != null && lootItem.maxDurability != null) {
//            ItemMeta meta = item.getItemMeta();
//            if (meta instanceof Damageable) {
//                int durability = random.nextInt(lootItem.maxDurability - lootItem.minDurability + 1) + lootItem.minDurability;
//                ((Damageable) meta).setDamage(durability);
//                item.setItemMeta(meta);
//            }
//        }
//
//        return item;
//    }

    public void addLootToSpawner(SpawnerData spawner, LootResult lootResult) {
//        configManager.debug("Adding loot to spawner " + spawner.getSpawnerId());
        VirtualInventory virtualInv = spawner.getVirtualInventory();
        Map<Integer, ItemStack> inventoryMap = virtualInv.getAllItems();

        int addedItems = 0;
        int skippedItems = 0;

        // Process items in batches
        for (ItemStack newItem : lootResult.getItems()) {
            boolean added = false;

            // Try to merge with existing stacks first
            for (Map.Entry<Integer, ItemStack> entry : inventoryMap.entrySet()) {
                ItemStack existingItem = entry.getValue();
                if (existingItem != null && existingItem.isSimilar(newItem) &&
                        existingItem.getAmount() < existingItem.getMaxStackSize()) {

                    int spaceLeft = existingItem.getMaxStackSize() - existingItem.getAmount();
                    int amountToAdd = Math.min(spaceLeft, newItem.getAmount());

                    existingItem.setAmount(existingItem.getAmount() + amountToAdd);
                    virtualInv.setItem(entry.getKey(), existingItem);

                    if (amountToAdd >= newItem.getAmount()) {
                        added = true;
                        addedItems++;
                        break;
                    } else {
                        newItem.setAmount(newItem.getAmount() - amountToAdd);
                    }
                }
            }

            // If not fully added, find empty slot
            if (!added) {
                boolean foundSlot = false;
                for (int i = 0; i < virtualInv.getSize(); i++) {
                    if (!inventoryMap.containsKey(i)) {
                        virtualInv.setItem(i, newItem);
                        inventoryMap.put(i, newItem);
                        addedItems++;
                        foundSlot = true;
                        break;
                    }
                }
                if (!foundSlot) {
                    skippedItems++;
                }
            }
        }

        // Update experience
        int oldExp = spawner.getSpawnerExp();
        int newExp = Math.min(oldExp + lootResult.getExperience(), spawner.getMaxStoredExp());
        spawner.setSpawnerExp(newExp);

        configManager.debug("Loot addition complete: " +
                "Added items: " + addedItems +
                ", Skipped items: " + skippedItems +
                ", XP change: " + oldExp + " -> " + newExp);
    }

    public void reloadConfigurations() {
        configManager.debug("Reloading loot configurations...");
        entityLootConfigs.clear();
        loadConfigurations();
        configManager.debug("Loot configurations reloaded successfully");
    }
}
