package org.golde.bukkit.corpsereborn.nms;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;
import com.comphenix.protocol.wrappers.EnumWrappers.*;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.golde.bukkit.corpsereborn.ConfigData;
import org.golde.bukkit.corpsereborn.Lang;
import org.golde.bukkit.corpsereborn.Main;
import org.golde.bukkit.corpsereborn.CorpseAPI.events.CorpseRemoveEvent;
import org.golde.bukkit.corpsereborn.CorpseAPI.events.CorpseSpawnEvent;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CorpseManager - by Griffer
 * Usa ProtocolLib per spawnare fake player con EntityPose.SLEEPING
 * identico al plugin originale, ma compatibile con Paper 1.21.
 */
public class CorpseManager {

    private final Main plugin;
    private final ProtocolManager protocolManager;
    private final List<CorpseData> corpses = new ArrayList<>();
    private File saveFile;

    // Contatore entity ID (deve essere unico per ogni fake entity)
    private static final AtomicInteger entityIdCounter = new AtomicInteger(2000000);

    public CorpseManager(Main plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        this.saveFile = new File(plugin.getDataFolder(), "corpses.yml");
    }

    public CorpseData spawnCorpse(Player player, String overrideName, Location location,
                                   Inventory inventory, int facing) {
        ConfigData cfg = plugin.getConfigData();
        String playerName = (overrideName != null && !overrideName.isEmpty()) ? overrideName : player.getName();
        String playerUUID = player.getUniqueId().toString();

        Location spawnLoc = findGroundLocation(location);
        CorpseData data = new CorpseData(playerName, playerUUID, spawnLoc, inventory, 0, cfg.getCorpseTime());

        CorpseSpawnEvent event = new CorpseSpawnEvent(data);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return null;

        // Assegna entity ID fake unico
        data.setEntityId(entityIdCounter.incrementAndGet());

        // Copia GameProfile (skin) del giocatore
        WrappedGameProfile profile = WrappedGameProfile.fromPlayer(player)
                .withName(playerName)
                .withId(UUID.randomUUID());
        data.setGameProfile(profile);

        // Spawna ArmorStand invisibile come hitbox per click
        spawnHitbox(data);

        // Invia pacchetti a tutti i giocatori online
        for (Player online : Bukkit.getOnlinePlayers()) {
            sendSpawnPackets(online, data, player);
        }

        // Timer despawn
        if (cfg.getCorpseTime() > 0) {
            int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin,
                    () -> removeCorpse(data),
                    (long) cfg.getCorpseTime() * 20L);
            data.setDespawnTaskId(taskId);
        }

        corpses.add(data);
        return data;
    }

    /**
     * Invia tutti i pacchetti per mostrare il fake player disteso (sleeping pose).
     */
    public void sendSpawnPackets(Player viewer, CorpseData data, Player deadPlayer) {
        if (Main.whoCanNotSeeCorpses.contains(viewer.getName())) return;

        int entityId = data.getEntityId();
        Location loc = data.getLocation();
        WrappedGameProfile profile = data.getGameProfile();

        try {
            // 1. Aggiungi alla PlayerList (necessario per la skin)
            PacketContainer infoPacket = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO);
            infoPacket.getPlayerInfoActions().write(0,
                    EnumSet.of(EnumWrappers.PlayerInfoAction.ADD_PLAYER,
                               EnumWrappers.PlayerInfoAction.UPDATE_LISTED));
            PlayerInfoData infoData = new PlayerInfoData(
                    profile.getUUID(),
                    0,
                    false,
                    EnumWrappers.NativeGameMode.SURVIVAL,
                    profile,
                    null,
                    null
            );
            infoPacket.getPlayerInfoDataLists().write(1, List.of(infoData));
            protocolManager.sendServerPacket(viewer, infoPacket);

            // 2. Aspetta 2 tick poi spawna
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    // Spawn named entity
                    PacketContainer spawnPacket = protocolManager.createPacket(PacketType.Play.Server.NAMED_ENTITY_SPAWN);
                    spawnPacket.getIntegers().write(0, entityId);
                    spawnPacket.getUUIDs().write(0, profile.getUUID());
                    spawnPacket.getDoubles().write(0, loc.getX());
                    spawnPacket.getDoubles().write(1, loc.getY());
                    spawnPacket.getDoubles().write(2, loc.getZ());
                    spawnPacket.getBytes().write(0, (byte)(loc.getYaw() * 256.0F / 360.0F));
                    spawnPacket.getBytes().write(1, (byte) 0);
                    protocolManager.sendServerPacket(viewer, spawnPacket);

                    // Metadata - EntityPose.SLEEPING + skin layers 0x7F
                    PacketContainer metaPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
                    metaPacket.getIntegers().write(0, entityId);

                    List<WrappedDataValue> metadata = new ArrayList<>();
                    // Index 6 = EntityPose (SLEEPING = 9)
                    metadata.add(new WrappedDataValue(6,
                            WrappedDataSerializer.fromHandle(
                                    WrappedDataSerializer.getHandleForClass(EnumWrappers.EntityPose.class)),
                            EnumWrappers.EntityPose.SLEEPING));
                    // Index 17 = skin layers (byte 0x7F = tutte le parti visibili)
                    metadata.add(new WrappedDataValue(17,
                            WrappedDataSerializer.fromHandle(
                                    WrappedDataSerializer.getHandleForClass(Byte.class)),
                            (byte) 0x7F));
                    metaPacket.getDataValueCollectionModifier().write(0, metadata);
                    protocolManager.sendServerPacket(viewer, metaPacket);

                    // Sleeping position (bed location)
                    Location bedLoc = getBedLocation(loc);
                    PacketContainer sleepPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
                    sleepPacket.getIntegers().write(0, entityId);
                    List<WrappedDataValue> sleepMeta = new ArrayList<>();
                    // Index 14 = sleeping position (Optional BlockPos)
                    sleepMeta.add(new WrappedDataValue(14,
                            WrappedDataSerializer.fromHandle(
                                    WrappedDataSerializer.getHandleForClass(Optional.class)),
                            Optional.of(new BlockPosition(bedLoc.getBlockX(),
                                    bedLoc.getBlockY(), bedLoc.getBlockZ()))));
                    sleepPacket.getDataValueCollectionModifier().write(0, sleepMeta);
                    protocolManager.sendServerPacket(viewer, sleepPacket);

                    // Fake bed block
                    viewer.sendBlockChange(bedLoc, Material.WHITE_BED.createBlockData());

                    // Equipaggiamento
                    if (plugin.getConfigData().shouldRenderArmor() && deadPlayer != null) {
                        sendEquipmentPackets(viewer, data, deadPlayer);
                    }

                    // Rimuovi dalla tablist dopo 40 tick
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        try {
                            PacketContainer removeInfo = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
                            removeInfo.getLists().write(0, List.of(profile.getUUID()));
                            protocolManager.sendServerPacket(viewer, removeInfo);
                        } catch (Exception ignored) {}
                    }, 40L);

                } catch (Exception e) {
                    plugin.getLogger().warning("[CorpseReborn] Errore spawn packets: " + e.getMessage());
                }
            }, 2L);

        } catch (Exception e) {
            plugin.getLogger().warning("[CorpseReborn] Errore info packet: " + e.getMessage());
        }
    }

    private void sendEquipmentPackets(Player viewer, CorpseData data, Player deadPlayer) {
        try {
            PacketContainer equipPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
            equipPacket.getIntegers().write(0, data.getEntityId());

            List<com.comphenix.protocol.wrappers.Pair<EnumWrappers.ItemSlot, ItemStack>> equipment = new ArrayList<>();
            var inv = deadPlayer.getInventory();
            if (inv.getHelmet() != null)     equipment.add(new com.comphenix.protocol.wrappers.Pair<>(EnumWrappers.ItemSlot.HEAD, inv.getHelmet()));
            if (inv.getChestplate() != null) equipment.add(new com.comphenix.protocol.wrappers.Pair<>(EnumWrappers.ItemSlot.CHEST, inv.getChestplate()));
            if (inv.getLeggings() != null)   equipment.add(new com.comphenix.protocol.wrappers.Pair<>(EnumWrappers.ItemSlot.LEGS, inv.getLeggings()));
            if (inv.getBoots() != null)      equipment.add(new com.comphenix.protocol.wrappers.Pair<>(EnumWrappers.ItemSlot.FEET, inv.getBoots()));

            if (!equipment.isEmpty()) {
                equipPacket.getSlotStackPairLists().write(0, equipment);
                protocolManager.sendServerPacket(viewer, equipPacket);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[CorpseReborn] Errore equipment: " + e.getMessage());
        }
    }

    /**
     * Hitbox invisibile per il click destro (looting).
     */
    private void spawnHitbox(CorpseData data) {
        Location loc = data.getLocation();
        ArmorStand hitbox = loc.getWorld().spawn(loc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSmall(false);
            stand.setArms(false);
            stand.setBasePlate(false);
            stand.setCanPickupItems(false);
            if (plugin.getConfigData().shouldShowNametag()) {
                stand.setCustomName(Lang.color("&7✦ &f" + data.getPlayerName() + " &7✦"));
                stand.setCustomNameVisible(true);
            }
        });
        data.setBodyStand(hitbox);
        data.setHeadStand(hitbox);
    }

    /**
     * Calcola la posizione del bed fake (2 blocchi sotto il giocatore).
     */
    private Location getBedLocation(Location loc) {
        return loc.clone().subtract(0, 2, 0);
    }

    public void removeCorpse(CorpseData data) {
        if (data == null) return;
        if (data.getDespawnTaskId() != -1) Bukkit.getScheduler().cancelTask(data.getDespawnTaskId());

        CorpseRemoveEvent event = new CorpseRemoveEvent(data);
        Bukkit.getPluginManager().callEvent(event);

        // Rimuovi fake player per tutti
        if (data.getEntityId() > 0) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                try {
                    PacketContainer destroyPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
                    destroyPacket.getIntLists().write(0, List.of(data.getEntityId()));
                    protocolManager.sendServerPacket(online, destroyPacket);

                    // Ripristina il bed block
                    Location bedLoc = getBedLocation(data.getLocation());
                    online.sendBlockChange(bedLoc, bedLoc.getBlock().getBlockData());
                } catch (Exception ignored) {}
            }
        }

        if (data.getBodyStand() != null && !data.getBodyStand().isDead()) data.getBodyStand().remove();
        if (data.getHeadStand() != null && !data.getHeadStand().isDead()
                && !data.getHeadStand().equals(data.getBodyStand())) data.getHeadStand().remove();

        corpses.remove(data);
    }

    public int removeCorpsesInRadius(Location center, double radius) {
        List<CorpseData> toRemove = new ArrayList<>();
        for (CorpseData d : corpses) {
            if (d.getLocation().getWorld().equals(center.getWorld())
                    && d.getLocation().distance(center) <= radius) toRemove.add(d);
        }
        toRemove.forEach(this::removeCorpse);
        return toRemove.size();
    }

    public void removeAllCorpses() { new ArrayList<>(corpses).forEach(this::removeCorpse); }

    public CorpseData getCorpseByEntity(ArmorStand stand) {
        for (CorpseData data : corpses) {
            if (stand.equals(data.getBodyStand()) || stand.equals(data.getHeadStand())) return data;
        }
        return null;
    }

    public List<CorpseData> getAllCorpses() { return Collections.unmodifiableList(corpses); }

    public void resendCorpsesToPlayer(Player player) {
        for (CorpseData data : corpses) {
            sendSpawnPackets(player, data, null);
        }
    }

    private Location findGroundLocation(Location loc) {
        Location result = loc.clone();
        World world = result.getWorld();
        if (world == null) return result;
        for (int i = 0; i < 5; i++) {
            Location below = result.clone().add(0, -1, 0);
            if (below.getBlock().getType().isSolid()) break;
            result = below;
        }
        return result;
    }

    // ── SAVE / LOAD ──

    public void saveCorpses() {
        if (!plugin.getConfigData().shouldSaveCorpses()) return;
        YamlConfiguration yml = new YamlConfiguration();
        int index = 0;
        for (CorpseData data : corpses) {
            if (data.getLocation() == null || data.getLocation().getWorld() == null) continue;
            String p = "corpse." + index + ".";
            yml.set(p + "player-name",   data.getPlayerName());
            yml.set(p + "player-uuid",   data.getPlayerUUID());
            yml.set(p + "world",         data.getLocation().getWorld().getName());
            yml.set(p + "x",             data.getLocation().getX());
            yml.set(p + "y",             data.getLocation().getY());
            yml.set(p + "z",             data.getLocation().getZ());
            yml.set(p + "yaw",           (double) data.getLocation().getYaw());
            yml.set(p + "pitch",         (double) data.getLocation().getPitch());
            yml.set(p + "spawn-time",    data.getSpawnTime());
            yml.set(p + "corpse-time",   data.getCorpseTime());
            yml.set(p + "selected-slot", data.getSelectedSlot());
            if (data.getInventory() != null) {
                for (int slot = 0; slot < data.getInventory().getSize(); slot++) {
                    ItemStack item = data.getInventory().getItem(slot);
                    if (item != null) yml.set(p + "inventory." + slot, item);
                }
            }
            index++;
        }
        yml.set("count", index);
        try { yml.save(saveFile); } catch (Exception e) {
            plugin.getLogger().severe("Impossibile salvare cadaveri: " + e.getMessage());
        }
    }

    public void loadCorpses() {
        if (!plugin.getConfigData().shouldSaveCorpses()) return;
        if (!saveFile.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(saveFile);
        int count = yml.getInt("count", 0);
        int loaded = 0;
        for (int index = 0; index < count; index++) {
            String p = "corpse." + index + ".";
            String playerName = yml.getString(p + "player-name");
            String playerUUID = yml.getString(p + "player-uuid");
            World world = Bukkit.getWorld(yml.getString(p + "world", ""));
            if (world == null) continue;
            Location loc = new Location(world,
                    yml.getDouble(p + "x"), yml.getDouble(p + "y"), yml.getDouble(p + "z"),
                    (float) yml.getDouble(p + "yaw"), (float) yml.getDouble(p + "pitch"));
            long spawnTime   = yml.getLong(p + "spawn-time");
            int corpseTime   = yml.getInt(p + "corpse-time", plugin.getConfigData().getCorpseTime());
            int selectedSlot = yml.getInt(p + "selected-slot", 0);
            if (corpseTime > 0 && (System.currentTimeMillis() - spawnTime) / 1000 >= corpseTime) continue;
            Inventory inv = Bukkit.createInventory(null, 54, playerName + "'s Corpse");
            if (yml.contains(p + "inventory")) {
                for (String slotKey : yml.getConfigurationSection(p + "inventory").getKeys(false)) {
                    try {
                        int slot = Integer.parseInt(slotKey);
                        ItemStack item = yml.getItemStack(p + "inventory." + slotKey);
                        if (item != null) inv.setItem(slot, item);
                    } catch (NumberFormatException ignored) {}
                }
            }
            CorpseData data = new CorpseData(playerName, playerUUID, loc, inv, selectedSlot, corpseTime);
            data.setEntityId(entityIdCounter.incrementAndGet());
            WrappedGameProfile profile = new WrappedGameProfile(UUID.randomUUID(), playerName);
            data.setGameProfile(profile);
            spawnHitbox(data);
            for (Player online : Bukkit.getOnlinePlayers()) {
                sendSpawnPackets(online, data, null);
            }
            if (corpseTime > 0) {
                long elapsed   = (System.currentTimeMillis() - spawnTime) / 1000;
                long remaining = Math.max(1, corpseTime - elapsed);
                int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin,
                        () -> removeCorpse(data), remaining * 20L);
                data.setDespawnTaskId(taskId);
            }
            corpses.add(data);
            loaded++;
        }
        plugin.getLogger().info("Caricati " + loaded + " cadaveri salvati.");
        saveFile.delete();
    }
}
