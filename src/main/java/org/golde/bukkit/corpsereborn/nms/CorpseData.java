package org.golde.bukkit.corpsereborn.nms;

import com.mojang.authlib.GameProfile;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

public class CorpseData {

    private final UUID corpseUUID;
    private String playerName;
    private String playerUUID;
    private Location location;
    private Inventory inventory;
    private int selectedSlot;
    private long spawnTime;
    private int corpseTime;

    private ArmorStand bodyStand;
    private ArmorStand headStand;
    private int entityId;
    private GameProfile fakeProfile;
    private int despawnTaskId = -1;

    public CorpseData(String playerName, String playerUUID, Location location,
                      Inventory inventory, int selectedSlot, int corpseTime) {
        this.corpseUUID   = UUID.randomUUID();
        this.playerName   = playerName;
        this.playerUUID   = playerUUID;
        this.location     = location;
        this.inventory    = inventory;
        this.selectedSlot = selectedSlot;
        this.spawnTime    = System.currentTimeMillis();
        this.corpseTime   = corpseTime;
    }

    public UUID getCorpseUUID()    { return corpseUUID; }
    public String getPlayerName()  { return playerName; }
    public void setPlayerName(String n) { this.playerName = n; }
    public String getPlayerUUID()  { return playerUUID; }
    public Location getLocation()  { return location; }
    public Inventory getInventory(){ return inventory; }
    public int getSelectedSlot()   { return selectedSlot; }
    public void setSelectedSlot(int s) { this.selectedSlot = s; }
    public long getSpawnTime()     { return spawnTime; }
    public int getCorpseTime()     { return corpseTime; }

    public ArmorStand getBodyStand()  { return bodyStand; }
    public void setBodyStand(ArmorStand s) { this.bodyStand = s; }
    public ArmorStand getHeadStand()  { return headStand; }
    public void setHeadStand(ArmorStand s) { this.headStand = s; }

    public int getEntityId() { return entityId; }
    public void setEntityId(int id) { this.entityId = id; }
    public GameProfile getFakeProfile() { return fakeProfile; }
    public void setFakeProfile(GameProfile p) { this.fakeProfile = p; }

    public int getDespawnTaskId()  { return despawnTaskId; }
    public void setDespawnTaskId(int id) { this.despawnTaskId = id; }

    public boolean isInventoryEmpty() {
        if (inventory == null) return true;
        for (var item : inventory.getContents()) if (item != null) return false;
        return true;
    }
}
