package org.golde.bukkit.corpsereborn.nms;

import com.mojang.authlib.GameProfile;
import io.papermc.paper.entity.LookAnchor;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.SkullMeta;
import org.golde.bukkit.corpsereborn.ConfigData;
import org.golde.bukkit.corpsereborn.Lang;
import org.golde.bukkit.corpsereborn.Main;
import org.golde.bukkit.corpsereborn.CorpseAPI.events.CorpseRemoveEvent;
import org.golde.bukkit.corpsereborn.CorpseAPI.events.CorpseSpawnEvent;

import java.io.File;
import java.util.*;

/**
 * CorpseManager - by Griffer
 * Usa fake ServerPlayer packets per simulare un cadavere identico
 * al player model originale, disteso a terra (posa di morte).
 */
public class CorpseManager {

    private final Main plugin;
    private final List<CorpseData> corpses = new ArrayList<>();
    private File saveFile;

    public CorpseManager(Main plugin) {
        this.plugin = plugin;
        this.saveFile = new File(plugin.getDataFolder(), "corpses.yml");
    }

    public CorpseData spawnCorpse(Player player, String overrideName, Location location,
                                   org.bukkit.inventory.Inventory inventory, int facing) {
        ConfigData cfg = plugin.getConfigData();
        String playerName = (overrideName != null && !overrideName.isEmpty()) ? overrideName : player.getName();
        String playerUUID = player.getUniqueId().toString();

        Location spawnLoc = findGroundLocation(location);
        CorpseData data = new CorpseData(playerName, playerUUID, spawnLoc, inventory, 0, cfg.getCorpseTime());

        CorpseSpawnEvent event = new CorpseSpawnEvent(data);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return null;

        spawnFakePlayer(data, player);

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
     * Spawna un fake player usando NMS packets.
     * Il client vede un vero player model disteso (posa di morte = pitch 90°).
     */
    private void spawnFakePlayer(CorpseData data, Player realPlayer) {
        try {
            // Crea un GameProfile con UUID random ma stessa skin del giocatore morto
            GameProfile profile;
            if (realPlayer != null) {
                GameProfile original = ((CraftPlayer) realPlayer).getProfile();
                profile = new GameProfile(UUID.randomUUID(), data.getPlayerName());
                profile.getProperties().putAll(original.getProperties());
            } else {
                profile = new GameProfile(UUID.randomUUID(), data.getPlayerName());
            }

            Location loc = data.getLocation();
            ServerLevel level = ((CraftWorld) loc.getWorld()).getHandle();

            // Crea il ServerPlayer fake
            ServerPlayer fakePlayer = new ServerPlayer(
                    ((CraftServer) Bukkit.getServer()).getServer(),
                    level,
                    profile,
                    net.minecraft.server.level.ClientInformation.createDefault()
            );

            // Posizione e rotazione: pitch 90° = disteso a terra
            fakePlayer.setPos(loc.getX(), loc.getY() - 0.1, loc.getZ());
            fakePlayer.setYRot(loc.getYaw());
            fakePlayer.setXRot(90.0f); // MORTE = pitch 90°

            // Equipaggiamento dal giocatore reale
            if (realPlayer != null) {
                var inv = realPlayer.getInventory();
                if (inv.getHelmet() != null)
                    fakePlayer.setItemSlot(EquipmentSlot.HEAD,   CraftItemStack.asNMSCopy(inv.getHelmet()));
                if (inv.getChestplate() != null)
                    fakePlayer.setItemSlot(EquipmentSlot.CHEST,  CraftItemStack.asNMSCopy(inv.getChestplate()));
                if (inv.getLeggings() != null)
                    fakePlayer.setItemSlot(EquipmentSlot.LEGS,   CraftItemStack.asNMSCopy(inv.getLeggings()));
                if (inv.getBoots() != null)
                    fakePlayer.setItemSlot(EquipmentSlot.FEET,   CraftItemStack.asNMSCopy(inv.getBoots()));
                if (inv.getItemInMainHand() != null && inv.getItemInMainHand().getType() != Material.AIR)
                    fakePlayer.setItemSlot(EquipmentSlot.MAINHAND, CraftItemStack.asNMSCopy(inv.getItemInMainHand()));
            }

            // Mostra tutte le skin layers (cape, hat, ecc.)
            fakePlayer.getEntityData().set(
                    net.minecraft.world.entity.player.Player.DATA_PLAYER_MODE_CUSTOMISATION,
                    (byte) 0x7f
            );

            data.setFakePlayer(fakePlayer);
            data.setFakeProfile(profile);

            // Invia i pacchetti a tutti i giocatori online
            for (Player online : Bukkit.getOnlinePlayers()) {
                sendSpawnPackets(online, data);
            }

            // Spawna un ArmorStand invisibile come hitbox per il click
            spawnHitbox(data, loc);

        } catch (Exception e) {
            plugin.getLogger().severe("[CorpseReborn] Errore spawn fake player: " + e.getMessage());
            e.printStackTrace();
            // Fallback: usa armor stand
            spawnArmorStandFallback(data, realPlayer);
        }
    }

    /**
     * Invia i pacchetti NMS per far vedere il fake player a un giocatore.
     */
    public void sendSpawnPackets(Player viewer, CorpseData data) {
        if (data.getFakePlayer() == null) return;
        if (Main.whoCanNotSeeCorpses.contains(viewer.getName())) return;

        ServerPlayer fakePlayer = data.getFakePlayer();
        ServerGamePacketListenerImpl conn = ((CraftPlayer) viewer).getHandle().connection;

        // 1. Aggiungi alla player list (necessario per spawn)
        conn.send(new ClientboundPlayerInfoUpdatePacket(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                fakePlayer
        ));

        // 2. Spawn entity
        conn.send(new ClientboundAddEntityPacket(
                fakePlayer,
                fakePlayer.getId(),
                fakePlayer.blockPosition()
        ));

        // 3. Entity metadata (skin layers, ecc.)
        conn.send(new ClientboundSetEntityDataPacket(
                fakePlayer.getId(),
                fakePlayer.getEntityData().getNonDefaultValues() != null
                        ? fakePlayer.getEntityData().getNonDefaultValues()
                        : List.of()
        ));

        // 4. Equipaggiamento
        List<net.minecraft.core.Holder<net.minecraft.world.item.Item>> dummy = null;
        List<net.minecraft.world.entity.EquipmentSlotGroup> dummy2 = null;
        conn.send(new ClientboundSetEquipmentPacket(
                fakePlayer.getId(),
                Arrays.stream(EquipmentSlot.values())
                        .map(slot -> new com.mojang.datafixers.util.Pair<>(
                                slot, fakePlayer.getItemBySlot(slot)))
                        .filter(p -> !p.getSecond().isEmpty())
                        .collect(java.util.stream.Collectors.toList())
        ));

        // 5. Rimuovi dalla tablist dopo un tick (non vogliamo che appaia in tablist)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            conn.send(new ClientboundPlayerInfoRemovePacket(
                    List.of(fakePlayer.getUUID())
            ));
        }, 2L);

        // Nametag tramite ArmorStand separato
        if (plugin.getConfigData().shouldShowNametag() && data.getHitboxStand() != null) {
            data.getHitboxStand().setCustomName(Lang.color("&7✦ &f" + data.getPlayerName() + " &7✦"));
            data.getHitboxStand().setCustomNameVisible(true);
        }
    }

    /**
     * ArmorStand invisibile usato come hitbox per il click destro (looting).
     */
    private void spawnHitbox(CorpseData data, Location loc) {
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
        data.setHitboxStand(hitbox);
    }

    /**
     * Fallback con ArmorStand se NMS fallisce.
     */
    private void spawnArmorStandFallback(CorpseData data, Player player) {
        ConfigData cfg = plugin.getConfigData();
        Location loc = data.getLocation().clone();
        float yaw = player != null ? player.getLocation().getYaw() : loc.getYaw();

        Location bodyLoc = loc.clone().add(0, -0.75, 0);
        ArmorStand body = loc.getWorld().spawn(bodyLoc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSmall(false);
            stand.setArms(true);
            stand.setBasePlate(false);
            stand.setCanPickupItems(false);
            stand.setRotation(yaw, 0);
            stand.setBodyPose(new org.bukkit.util.EulerAngle(Math.toRadians(90), 0, 0));
            stand.setLeftArmPose(new org.bukkit.util.EulerAngle(Math.toRadians(90), 0, Math.toRadians(-30)));
            stand.setRightArmPose(new org.bukkit.util.EulerAngle(Math.toRadians(90), 0, Math.toRadians(30)));
            stand.setLeftLegPose(new org.bukkit.util.EulerAngle(Math.toRadians(90), 0, Math.toRadians(-5)));
            stand.setRightLegPose(new org.bukkit.util.EulerAngle(Math.toRadians(90), 0, Math.toRadians(5)));
            if (cfg.shouldRenderArmor() && player != null) {
                var inv = player.getInventory();
                if (inv.getChestplate() != null) stand.getEquipment().setChestplate(inv.getChestplate().clone());
                if (inv.getLeggings() != null)   stand.getEquipment().setLeggings(inv.getLeggings().clone());
                if (inv.getBoots() != null)       stand.getEquipment().setBoots(inv.getBoots().clone());
            }
            if (cfg.shouldShowNametag()) {
                stand.setCustomName(Lang.color("&7✦ &f" + data.getPlayerName() + " &7✦"));
                stand.setCustomNameVisible(true);
            }
        });

        // Head stand
        double rad = Math.toRadians(yaw);
        Location headLoc = bodyLoc.clone().add(-Math.sin(rad) * 0.9, 0.3, Math.cos(rad) * 0.9);
        ArmorStand head = loc.getWorld().spawn(headLoc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSmall(true);
            stand.setArms(false);
            stand.setBasePlate(false);
            stand.setCanPickupItems(false);
            stand.setCustomNameVisible(false);
            stand.setRotation(yaw, 0);
            org.bukkit.inventory.ItemStack skull = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (player != null) meta.setOwningPlayer(player);
            skull.setItemMeta(meta);
            stand.getEquipment().setHelmet(skull);
        });

        data.setBodyStand(body);
        data.setHeadStand(head);
        data.setHitboxStand(body);
    }

    public void removeCorpse(CorpseData data) {
        if (data == null) return;
        if (data.getDespawnTaskId() != -1) Bukkit.getScheduler().cancelTask(data.getDespawnTaskId());

        CorpseRemoveEvent event = new CorpseRemoveEvent(data);
        Bukkit.getPluginManager().callEvent(event);

        // Rimuovi fake player per tutti
        if (data.getFakePlayer() != null) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                ServerGamePacketListenerImpl conn = ((CraftPlayer) online).getHandle().connection;
                conn.send(new ClientboundRemoveEntitiesPacket(data.getFakePlayer().getId()));
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
            if (stand.equals(data.getBodyStand()) || stand.equals(data.getHeadStand())
                    || stand.equals(data.getHitboxStand())) return data;
        }
        return null;
    }

    public List<CorpseData> getAllCorpses() { return Collections.unmodifiableList(corpses); }

    // Invia i pacchetti spawn a un nuovo giocatore che entra
    public void resendCorpsesToPlayer(Player player) {
        for (CorpseData data : corpses) {
            if (data.getFakePlayer() != null) sendSpawnPackets(player, data);
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
            CorpseData data = new CorpseData(playerName, playerUUID, loc, inv, selectedSlot, corpseTime);
            spawnArmorStandFallback(data, null);
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
