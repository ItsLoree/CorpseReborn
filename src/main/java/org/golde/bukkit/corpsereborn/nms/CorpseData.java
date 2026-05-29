package org.golde.bukkit.corpsereborn.nms;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

/**
 * Holds all data for a spawned corpse.
 * In Paper 1.21 we use ArmorStand + NPC skin via PlayerProfile API
 * instead of raw NMS packets.
 */
public class CorpseData {

    private final UUID corpseUUID;        // Unique ID for this corpse
    private String playerName;            // Name of the player who died
    private String playerUUID;            // UUID of the dead player (for skin)
    private Location location;            // Where the corpse is
    private Inventory inventory;          // Loot in the corpse
    private int selectedSlot;             // Held item slot at time of death
    private long spawnTime;               // When the corpse was spawned (ms)
    private int corpseTime;               // How long (seconds) before despawn; -1 = permanent

    // The actual in-world entities representing the corpse
    private ArmorStand bodyStand;         // The main invisible armor stand (interaction point)
    private ArmorStand headStand;         // Displays the player skull

    // Scheduler task ID for auto-despawn
    private int despawnTaskId = -1;

    public CorpseData(String playerName, String playerUUID, Location location,
                      Inventory inventory, int selectedSlot, int corpseTime) {
        this.corpseUUID = UUID.randomUUID();
        this.playerName = playerName;
        this.playerUUID = playerUUID;
        this.location = location;
        this.inventory = inventory;
        this.selectedSlot = selectedSlot;
        this.spawnTime = System.currentTimeMillis();
        this.corpseTime = corpseTime;
    }

    // --- Getters & Setters ---

    public UUID getCorpseUUID() { return corpseUUID; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public String getPlayerUUID() { return playerUUID; }

    public Location getLocation() { return location; }

    public Inventory getInventory() { return inventory; }

    public int getSelectedSlot() { return selectedSlot; }
    public void setSelectedSlot(int selectedSlot) { this.selectedSlot = selectedSlot; }

    public long getSpawnTime() { return spawnTime; }

    public int getCorpseTime() { return corpseTime; }

    public ArmorStand getBodyStand() { return bodyStand; }
    public void setBodyStand(ArmorStand bodyStand) { this.bodyStand = bodyStand; }

    public ArmorStand getHeadStand() { return headStand; }
    public void setHeadStand(ArmorStand headStand) { this.headStand = headStand; }

    public int getDespawnTaskId() { return despawnTaskId; }
    public void setDespawnTaskId(int despawnTaskId) { this.despawnTaskId = despawnTaskId; }

    public boolean isInventoryEmpty() {
        if (inventory == null) return true;
        for (var item : inventory.getContents()) {
            if (item != null) return false;
        }
        return true;
    }
}
