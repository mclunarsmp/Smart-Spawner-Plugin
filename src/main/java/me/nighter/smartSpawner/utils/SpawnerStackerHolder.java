package me.nighter.smartSpawner.utils;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class SpawnerStackerHolder implements InventoryHolder {
    private final SpawnerData spawnerData;

    public SpawnerStackerHolder(SpawnerData spawnerData) {
        this.spawnerData = spawnerData;
    }

    @Override
    public Inventory getInventory() {
        return null; // Required by interface
    }

    public SpawnerData getSpawnerData() {
        return spawnerData;
    }

}