package org.golde.bukkit.corpsereborn.nms;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.EulerAngle;
import org.golde.bukkit.corpsereborn.ConfigData;
import org.golde.bukkit.corpsereborn.Lang;
import org.golde.bukkit.corpsereborn.Main;
import org.golde.bukkit.corpsereborn.CorpseAPI.events.CorpseRemoveEvent;
import org.golde.bukkit.corpsereborn.CorpseAPI.events.CorpseSpawnEvent;

import java.io.File;
import java.util.*;

/**
 * CorpseManager - by Griffer
 * Corpo disteso usando due ArmorStand sovrapposti.
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
                                   Inventory inventory, int facing) {
        ConfigData cfg = plugin.getConfigData();
        String playerName = (overrideName != null && !overrideName.isEmpty()) ? overrideName : player.getName();
        String playerUUID = player.getUniqueId().toString();

        Location spawnLoc = findGroundLocation(location);
        CorpseData data = new CorpseData(playerName, playerUUID, spawnLoc, inventory, 0, cfg.getCorpseTime());

        CorpseSpawnEvent event = new CorpseSpawnEvent(data);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return null;

        spawnArmorStands(data, player);

        if (cfg.getCorpseTime() > 0) {
            int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin,
                    () -> removeCorpse(data),
                    (long) cfg.getCorpseTime() * 20L);
            data.setDespawnTaskId(taskId);
        }

        corpses.add(data);
        return data;
    }

    private void spawnArmorStands(CorpseData data, Player player) {
        ConfigData cfg = plugin.getConfigData();
        Location loc = data.getLocation().clone();
        float yaw = player != null ? player.getLocation().getYaw() : loc.getYaw();

        // Body stand - disteso a terra
        Location bodyLoc = loc.clone().add(0, -0.8, 0);
        ArmorStand body = loc.getWorld().spawn(bodyLoc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSmall(false);
            stand.setArms(true);
            stand.setBasePlate(false);
            stand.setCanPickupItems(false);
            stand.setRotation(yaw, 0);

            // Pose corpo disteso
            stand.setBodyPose(new EulerAngle(Math.toRadians(90), 0, 0));
            stand.setLeftArmPose(new EulerAngle(Math.toRadians(90), 0, Math.toRadians(-30)));
            stand.setRightArmPose(new EulerAngle(Math.toRadians(90), 0, Math.toRadians(30)));
            stand.setLeftLegPose(new EulerAngle(Math.toRadians(90), 0, Math.toRadians(-5)));
            stand.setRightLegPose(new EulerAngle(Math.toRadians(90), 0, Math.toRadians(5)));

            // Armatura
            if (cfg.shouldRenderArmor() && player != null) {
                var inv = player.getInventory();
                if (inv.getChestplate() != null) stand.getEquipment().setChestplate(inv.getChestplate().clone());
                if (inv.getLeggings() != null)   stand.getEquipment().setLeggings(inv.getLeggings().clone());
                if (inv.getBoots() != null)       stand.getEquipment().setBoots(inv.getBoots().clone());
            }

            stand.setCustomNameVisible(false);
        });

        // Head stand - skull del giocatore posizionato alla testa
        double rad = Math.toRadians(yaw);
        Location headLoc = bodyLoc.clone().add(-Math.sin(rad) * 0.9, 0.25, Math.cos(rad) * 0.9);
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

            // Skull con skin del giocatore
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (player != null) {
                meta.setOwningPlayer(player);
            } else {
                try {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(data.getPlayerUUID()));
                    meta.setOwningPlayer(op);
                } catch (Exception ignored) {}
            }
            skull.setItemMeta(meta);
            stand.getEquipment().setHelmet(skull);
        });

        data.setBodyStand(body);
        data.setHeadStand(head);
    }

    public void removeCorpse(CorpseData data) {
        if (data == null) return;
        if (data.getDespawnTaskId() != -1) Bukkit.getScheduler().cancelTask(data.getDespawnTaskId());

        CorpseRemoveEvent event = new CorpseRemoveEvent(data);
        Bukkit.getPluginManager().callEvent(event);

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
        // Con ArmorStand non serve resend - sono entità reali
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
            CorpseData data = new CorpseData(playerName, yml.getString(p + "player-uuid", ""), loc, inv, selectedSlot, corpseTime);
            spawnArmorStands(data, null);
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
