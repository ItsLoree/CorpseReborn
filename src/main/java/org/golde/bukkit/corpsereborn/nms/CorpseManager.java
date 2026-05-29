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
import org.golde.bukkit.corpsereborn.CorpseAPI.events.CorpseSpawnEvent;
import org.golde.bukkit.corpsereborn.CorpseAPI.events.CorpseRemoveEvent;

import java.io.File;
import java.util.*;

/**
 * CorpseManager - by Griffer
 * Gestisce tutti i cadaveri usando Paper 1.21 API.
 * Usa ArmorStand con pose "sdraiato" per simulare un corpo a terra.
 */
public class CorpseManager {

    private final Main plugin;
    private final List<CorpseData> corpses = new ArrayList<>();
    private File saveFile;

    // Angoli per simulare il corpo disteso a terra
    // Il body stand è ruotato di 90° sull'asse X per "sdraiarsi"
    private static final EulerAngle BODY_ANGLE     = new EulerAngle(Math.toRadians(90), 0, 0);
    private static final EulerAngle HEAD_ANGLE      = new EulerAngle(Math.toRadians(0), 0, 0);
    private static final EulerAngle LEFT_ARM_ANGLE  = new EulerAngle(Math.toRadians(90), 0, Math.toRadians(-10));
    private static final EulerAngle RIGHT_ARM_ANGLE = new EulerAngle(Math.toRadians(90), 0, Math.toRadians(10));
    private static final EulerAngle LEFT_LEG_ANGLE  = new EulerAngle(Math.toRadians(90), 0, Math.toRadians(-5));
    private static final EulerAngle RIGHT_LEG_ANGLE = new EulerAngle(Math.toRadians(90), 0, Math.toRadians(5));

    public CorpseManager(Main plugin) {
        this.plugin = plugin;
        this.saveFile = new File(plugin.getDataFolder(), "corpses.yml");
    }

    public CorpseData spawnCorpse(Player player, String overrideName, Location location, Inventory inventory, int facing) {
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

    /**
     * Spawna il corpo disteso a terra usando UN armor stand grande con pose corrette.
     *
     * L'armor stand viene:
     * - Ruotato di 90° sull'asse X (si "sdraia")
     * - Posizionato leggermente sotto terra per sembrare disteso sul pavimento
     * - Con braccia e gambe con angoli naturali
     * - Con la testa del giocatore come helmet
     * - Con l'armatura del giocatore
     */
    private void spawnArmorStands(CorpseData data, Player player) {
        ConfigData cfg = plugin.getConfigData();
        Location loc = data.getLocation().clone();

        // Offset verso il basso per far sembrare il corpo a terra (non galleggiante)
        // -0.2 è il valore ottimale per Paper 1.21
        Location bodyLoc = loc.clone().add(0, -0.2, 0);

        ArmorStand body = loc.getWorld().spawn(bodyLoc, ArmorStand.class, stand -> {
            stand.setVisible(false);          // corpo invisibile, usiamo armor+skull
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSmall(false);
            stand.setArms(true);
            stand.setBasePlate(false);
            stand.setCanPickupItems(false);

            // === POSE CORPO DISTESO ===
            stand.setHeadPose(HEAD_ANGLE);
            stand.setBodyPose(BODY_ANGLE);
            stand.setLeftArmPose(LEFT_ARM_ANGLE);
            stand.setRightArmPose(RIGHT_ARM_ANGLE);
            stand.setLeftLegPose(LEFT_LEG_ANGLE);
            stand.setRightLegPose(RIGHT_LEG_ANGLE);

            // Ruota l'armor stand in base alla direzione in cui guardava il giocatore
            if (player != null) {
                stand.setRotation(player.getLocation().getYaw(), 0);
            }

            // === SKULL CON SKIN DEL GIOCATORE ===
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

            // === ARMATURA DEL GIOCATORE ===
            if (cfg.shouldRenderArmor() && player != null) {
                var inv = player.getInventory();
                if (inv.getChestplate() != null) stand.getEquipment().setChestplate(inv.getChestplate().clone());
                if (inv.getLeggings() != null)   stand.getEquipment().setLeggings(inv.getLeggings().clone());
                if (inv.getBoots() != null)       stand.getEquipment().setBoots(inv.getBoots().clone());
                // Nota: non mettiamo l'helmet del giocatore perché usiamo già il skull
            }

            // === NAMETAG ===
            if (cfg.shouldShowNametag()) {
                stand.setCustomName(Lang.color("&7✦ &f" + data.getPlayerName() + " &7✦"));
                stand.setCustomNameVisible(true);
            }
        });

        data.setBodyStand(body);
        data.setHeadStand(body); // stesso stand, non ne serve uno separato
    }

    public void removeCorpse(CorpseData data) {
        if (data == null) return;

        if (data.getDespawnTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(data.getDespawnTaskId());
        }

        CorpseRemoveEvent event = new CorpseRemoveEvent(data);
        Bukkit.getPluginManager().callEvent(event);

        if (data.getBodyStand() != null && !data.getBodyStand().isDead()) {
            data.getBodyStand().remove();
        }

        corpses.remove(data);
    }

    public int removeCorpsesInRadius(Location center, double radius) {
        List<CorpseData> toRemove = new ArrayList<>();
        for (CorpseData d : corpses) {
            if (d.getLocation().getWorld().equals(center.getWorld())) {
                if (d.getLocation().distance(center) <= radius) {
                    toRemove.add(d);
                }
            }
        }
        toRemove.forEach(this::removeCorpse);
        return toRemove.size();
    }

    public void removeAllCorpses() {
        new ArrayList<>(corpses).forEach(this::removeCorpse);
    }

    public CorpseData getCorpseByEntity(ArmorStand stand) {
        for (CorpseData data : corpses) {
            if (stand.equals(data.getBodyStand())) return data;
        }
        return null;
    }

    public List<CorpseData> getAllCorpses() {
        return Collections.unmodifiableList(corpses);
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

    // =========================================================
    // SAVE / LOAD
    // =========================================================

    public void saveCorpses() {
        if (!plugin.getConfigData().shouldSaveCorpses()) return;
        YamlConfiguration yml = new YamlConfiguration();
        int index = 0;
        for (CorpseData data : corpses) {
            if (data.getLocation() == null || data.getLocation().getWorld() == null) continue;
            String path = "corpse." + index + ".";
            yml.set(path + "player-name", data.getPlayerName());
            yml.set(path + "player-uuid", data.getPlayerUUID());
            yml.set(path + "world", data.getLocation().getWorld().getName());
            yml.set(path + "x", data.getLocation().getX());
            yml.set(path + "y", data.getLocation().getY());
            yml.set(path + "z", data.getLocation().getZ());
            yml.set(path + "yaw", (double) data.getLocation().getYaw());
            yml.set(path + "pitch", (double) data.getLocation().getPitch());
            yml.set(path + "spawn-time", data.getSpawnTime());
            yml.set(path + "corpse-time", data.getCorpseTime());
            yml.set(path + "selected-slot", data.getSelectedSlot());
            if (data.getInventory() != null) {
                for (int slot = 0; slot < data.getInventory().getSize(); slot++) {
                    ItemStack item = data.getInventory().getItem(slot);
                    if (item != null) yml.set(path + "inventory." + slot, item);
                }
            }
            index++;
        }
        yml.set("count", index);
        try { yml.save(saveFile); } catch (Exception e) {
            plugin.getLogger().severe("Impossibile salvare i cadaveri: " + e.getMessage());
        }
    }

    public void loadCorpses() {
        if (!plugin.getConfigData().shouldSaveCorpses()) return;
        if (!saveFile.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(saveFile);
        int count = yml.getInt("count", 0);
        int loaded = 0;
        for (int index = 0; index < count; index++) {
            String path = "corpse." + index + ".";
            String playerName = yml.getString(path + "player-name");
            String playerUUID = yml.getString(path + "player-uuid");
            String worldName  = yml.getString(path + "world");
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;
            double x = yml.getDouble(path + "x");
            double y = yml.getDouble(path + "y");
            double z = yml.getDouble(path + "z");
            float yaw = (float) yml.getDouble(path + "yaw");
            float pitch = (float) yml.getDouble(path + "pitch");
            long spawnTime = yml.getLong(path + "spawn-time");
            int corpseTime = yml.getInt(path + "corpse-time", plugin.getConfigData().getCorpseTime());
            int selectedSlot = yml.getInt(path + "selected-slot", 0);
            Location loc = new Location(world, x, y, z, yaw, pitch);
            Inventory inv = Bukkit.createInventory(null, 54, playerName + "'s Corpse");
            if (yml.contains(path + "inventory")) {
                for (String slotKey : yml.getConfigurationSection(path + "inventory").getKeys(false)) {
                    try {
                        int slot = Integer.parseInt(slotKey);
                        ItemStack item = yml.getItemStack(path + "inventory." + slotKey);
                        if (item != null) inv.setItem(slot, item);
                    } catch (NumberFormatException ignored) {}
                }
            }
            if (corpseTime > 0) {
                long elapsed = (System.currentTimeMillis() - spawnTime) / 1000;
                if (elapsed >= corpseTime) continue;
            }
            CorpseData data = new CorpseData(playerName, playerUUID, loc, inv, selectedSlot, corpseTime);
            spawnArmorStands(data, null);
            if (corpseTime > 0) {
                long elapsed = (System.currentTimeMillis() - spawnTime) / 1000;
                long remaining = Math.max(1, corpseTime - elapsed);
                int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> removeCorpse(data), remaining * 20L);
                data.setDespawnTaskId(taskId);
            }
            corpses.add(data);
            loaded++;
        }
        plugin.getLogger().info("Caricati " + loaded + " cadaveri salvati.");
        saveFile.delete();
    }
}
