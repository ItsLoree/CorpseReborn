package org.golde.bukkit.corpsereborn.nms;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.Inventory;
import org.golde.bukkit.corpsereborn.ConfigData;
import org.golde.bukkit.corpsereborn.Lang;
import org.golde.bukkit.corpsereborn.Main;
import org.golde.bukkit.corpsereborn.CorpseAPI.events.CorpseRemoveEvent;
import org.golde.bukkit.corpsereborn.CorpseAPI.events.CorpseSpawnEvent;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CorpseManager - by Griffer
 * Usa NMS Paper 1.21 per spawnare fake player in posa SLEEPING
 * identico al plugin originale CorpseReborn 1.16.
 */
public class CorpseManager {

    private final Main plugin;
    private final List<CorpseData> corpses = new ArrayList<>();
    private File saveFile;

    public CorpseManager(Main plugin) {
        this.plugin = plugin;
        this.saveFile = new File(plugin.getDataFolder(), "corpses.yml");
    }

    public CorpseData spawnCorpse(org.bukkit.entity.Player player, String overrideName,
                                   Location location, Inventory inventory, int facing) {
        ConfigData cfg = plugin.getConfigData();
        String playerName = (overrideName != null && !overrideName.isEmpty()) ? overrideName : player.getName();
        String playerUUID = player.getUniqueId().toString();

        Location spawnLoc = findGroundLocation(location);
        CorpseData data = new CorpseData(playerName, playerUUID, spawnLoc, inventory, 0, cfg.getCorpseTime());

        CorpseSpawnEvent event = new CorpseSpawnEvent(data);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return null;

        // Crea il fake player NMS
        int entityId = getNextEntityId();
        data.setEntityId(entityId);

        // GameProfile con skin del giocatore reale
        GameProfile realProfile = ((CraftPlayer) player).getProfile();
        GameProfile fakeProfile = new GameProfile(UUID.randomUUID(), playerName);
        fakeProfile.getProperties().putAll(realProfile.getProperties());
        data.setFakeProfile(fakeProfile);

        // Spawna hitbox ArmorStand per click
        spawnHitbox(data, location);

        // Invia pacchetti a tutti
        for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
            sendSpawnPackets(online, data, player);
        }

        if (cfg.getCorpseTime() > 0) {
            int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin,
                    () -> removeCorpse(data),
                    (long) cfg.getCorpseTime() * 20L);
            data.setDespawnTaskId(taskId);
        }

        corpses.add(data);
        return data;
    }

    public void sendSpawnPackets(org.bukkit.entity.Player viewer, CorpseData data, org.bukkit.entity.Player deadPlayer) {
        if (Main.whoCanNotSeeCorpses.contains(viewer.getName())) return;

        GameProfile profile = data.getFakeProfile();
        if (profile == null) return;

        Location loc = data.getLocation();
        int entityId = data.getEntityId();
        ServerGamePacketListenerImpl conn = ((CraftPlayer) viewer).getHandle().connection;
        ServerLevel level = ((CraftWorld) loc.getWorld()).getHandle();

        try {
            // 1. Aggiungi alla player list
            ServerPlayer fakeServerPlayer = createFakeServerPlayer(profile, loc, level);
            fakeServerPlayer.setId(entityId);

            conn.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(fakeServerPlayer)));

            // 2. Aspetta 2 tick poi spawna
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    // Spawn packet
                    conn.send(new ClientboundAddEntityPacket(
                            entityId,
                            profile.getId(),
                            loc.getX(), loc.getY(), loc.getZ(),
                            loc.getYaw(), 0f,
                            EntityType.PLAYER,
                            0,
                            Vec3.ZERO,
                            loc.getYaw()
                    ));

                    // Metadata: SLEEPING pose + skin layers
                    List<SynchedEntityData.DataValue<?>> metaList = new ArrayList<>();
                    // Index 6 = Pose (SLEEPING)
                    metaList.add(SynchedEntityData.DataValue.create(
                            net.minecraft.world.entity.LivingEntity.DATA_POSE,
                            Pose.SLEEPING
                    ));
                    // Index 17 = skin layers (0x7F = tutto visibile)
                    metaList.add(SynchedEntityData.DataValue.create(
                            Player.DATA_PLAYER_MODE_CUSTOMISATION,
                            (byte) 0x7F
                    ));
                    conn.send(new ClientboundSetEntityDataPacket(entityId, metaList));

                    // Sleeping position (bed location)
                    Location bedLoc = getBedLocation(loc);
                    List<SynchedEntityData.DataValue<?>> sleepMeta = new ArrayList<>();
                    sleepMeta.add(SynchedEntityData.DataValue.create(
                            net.minecraft.world.entity.LivingEntity.DATA_SLEEPING_POS_ID,
                            Optional.of(new net.minecraft.core.BlockPos(
                                    bedLoc.getBlockX(), bedLoc.getBlockY(), bedLoc.getBlockZ()))
                    ));
                    conn.send(new ClientboundSetEntityDataPacket(entityId, sleepMeta));

                    // Fake bed block
                    BlockState bedState = Blocks.WHITE_BED.defaultBlockState()
                            .setValue(BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD);
                    conn.send(new ClientboundBlockUpdatePacket(
                            new net.minecraft.core.BlockPos(bedLoc.getBlockX(), bedLoc.getBlockY(), bedLoc.getBlockZ()),
                            bedState
                    ));

                    // Equipaggiamento
                    if (plugin.getConfigData().shouldRenderArmor() && deadPlayer != null) {
                        sendEquipment(conn, entityId, deadPlayer);
                    }

                    // Rimuovi dalla tablist dopo 40 tick
                    Bukkit.getScheduler().runTaskLater(plugin, () ->
                            conn.send(new ClientboundPlayerInfoRemovePacket(List.of(profile.getId()))), 40L);

                } catch (Exception e) {
                    plugin.getLogger().warning("[CorpseReborn] Errore spawn: " + e.getMessage());
                    e.printStackTrace();
                }
            }, 2L);

        } catch (Exception e) {
            plugin.getLogger().warning("[CorpseReborn] Errore info: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private ServerPlayer createFakeServerPlayer(GameProfile profile, Location loc, ServerLevel level) {
        ServerPlayer fake = new ServerPlayer(
                ((CraftServer) Bukkit.getServer()).getServer(),
                level,
                profile,
                net.minecraft.server.level.ClientInformation.createDefault()
        );
        fake.setPos(loc.getX(), loc.getY(), loc.getZ());
        fake.setYRot(loc.getYaw());
        fake.setXRot(0f);
        return fake;
    }

    private void sendEquipment(ServerGamePacketListenerImpl conn, int entityId, org.bukkit.entity.Player deadPlayer) {
        try {
            var inv = deadPlayer.getInventory();
            List<Pair<net.minecraft.world.entity.EquipmentSlot, ItemStack>> equipment = new ArrayList<>();
            if (inv.getHelmet() != null)     equipment.add(Pair.of(net.minecraft.world.entity.EquipmentSlot.HEAD,   CraftItemStack.asNMSCopy(inv.getHelmet())));
            if (inv.getChestplate() != null) equipment.add(Pair.of(net.minecraft.world.entity.EquipmentSlot.CHEST,  CraftItemStack.asNMSCopy(inv.getChestplate())));
            if (inv.getLeggings() != null)   equipment.add(Pair.of(net.minecraft.world.entity.EquipmentSlot.LEGS,   CraftItemStack.asNMSCopy(inv.getLeggings())));
            if (inv.getBoots() != null)      equipment.add(Pair.of(net.minecraft.world.entity.EquipmentSlot.FEET,   CraftItemStack.asNMSCopy(inv.getBoots())));
            if (!equipment.isEmpty()) conn.send(new ClientboundSetEquipmentPacket(entityId, equipment));
        } catch (Exception e) {
            plugin.getLogger().warning("[CorpseReborn] Errore equipment: " + e.getMessage());
        }
    }

    private void spawnHitbox(CorpseData data, Location loc) {
        ArmorStand hitbox = loc.getWorld().spawn(loc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSmall(false);
            stand.setArms(false);
            stand.setBasePlate(false);
            stand.setCanPickupItems(false);
            stand.setCustomNameVisible(false);
        });
        data.setBodyStand(hitbox);
        data.setHeadStand(hitbox);
    }

    private Location getBedLocation(Location loc) {
        return loc.clone().subtract(0, 2, 0);
    }

    private int getNextEntityId() {
        try {
            Field entityCount = net.minecraft.world.entity.Entity.class.getDeclaredField("ENTITY_COUNTER");
            entityCount.setAccessible(true);
            AtomicInteger counter = (AtomicInteger) entityCount.get(null);
            return counter.incrementAndGet();
        } catch (Exception e) {
            return new Random().nextInt(1000000) + 1000000;
        }
    }

    public void removeCorpse(CorpseData data) {
        if (data == null) return;
        if (data.getDespawnTaskId() != -1) Bukkit.getScheduler().cancelTask(data.getDespawnTaskId());

        CorpseRemoveEvent event = new CorpseRemoveEvent(data);
        Bukkit.getPluginManager().callEvent(event);

        // Rimuovi fake player per tutti
        if (data.getEntityId() > 0) {
            for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
                try {
                    ServerGamePacketListenerImpl conn = ((CraftPlayer) online).getHandle().connection;
                    conn.send(new ClientboundRemoveEntitiesPacket(data.getEntityId()));
                    // Ripristina bed block
                    Location bedLoc = getBedLocation(data.getLocation());
                    conn.send(new ClientboundBlockUpdatePacket(
                            new net.minecraft.core.BlockPos(bedLoc.getBlockX(), bedLoc.getBlockY(), bedLoc.getBlockZ()),
                            ((CraftWorld) bedLoc.getWorld()).getHandle().getBlockState(
                                    new net.minecraft.core.BlockPos(bedLoc.getBlockX(), bedLoc.getBlockY(), bedLoc.getBlockZ()))
                    ));
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

    public void resendCorpsesToPlayer(org.bukkit.entity.Player player) {
        for (CorpseData data : corpses) sendSpawnPackets(player, data, null);
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
                    org.bukkit.inventory.ItemStack item = data.getInventory().getItem(slot);
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
            World world = Bukkit.getWorld(yml.getString(p + "world", ""));
            if (world == null) continue;
            Location loc = new Location(world,
                    yml.getDouble(p + "x"), yml.getDouble(p + "y"), yml.getDouble(p + "z"),
                    (float) yml.getDouble(p + "yaw"), (float) yml.getDouble(p + "pitch"));
            long spawnTime   = yml.getLong(p + "spawn-time");
            int corpseTime   = yml.getInt(p + "corpse-time", plugin.getConfigData().getCorpseTime());
            int selectedSlot = yml.getInt(p + "selected-slot", 0);
            if (corpseTime > 0 && (System.currentTimeMillis() - spawnTime) / 1000 >= corpseTime) continue;
            org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, 54, playerName + "'s Corpse");
            if (yml.contains(p + "inventory")) {
                for (String slotKey : yml.getConfigurationSection(p + "inventory").getKeys(false)) {
                    try {
                        int slot = Integer.parseInt(slotKey);
                        org.bukkit.inventory.ItemStack item = yml.getItemStack(p + "inventory." + slotKey);
                        if (item != null) inv.setItem(slot, item);
                    } catch (NumberFormatException ignored) {}
                }
            }
            CorpseData data = new CorpseData(playerName, yml.getString(p + "player-uuid", ""), loc, inv, selectedSlot, corpseTime);
            data.setEntityId(getNextEntityId());
            GameProfile profile = new GameProfile(UUID.randomUUID(), playerName);
            data.setFakeProfile(profile);
            spawnHitbox(data, loc);
            for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) sendSpawnPackets(online, data, null);
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
