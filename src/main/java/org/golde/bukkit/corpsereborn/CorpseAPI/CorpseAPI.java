package org.golde.bukkit.corpsereborn.CorpseAPI;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.golde.bukkit.corpsereborn.Main;
import org.golde.bukkit.corpsereborn.nms.CorpseData;

import java.util.List;

/**
 * Public API for CorpseReborn.
 * Other plugins can use this to interact with corpses.
 *
 * Example usage:
 *   CorpseAPI api = CorpseAPI.getInstance();
 *   api.spawnCorpse(player, player.getLocation(), null);
 */
public class CorpseAPI {

    private static CorpseAPI instance;
    private final Main plugin;

    private CorpseAPI(Main plugin) {
        this.plugin = plugin;
    }

    public static CorpseAPI getInstance() {
        if (instance == null) {
            instance = new CorpseAPI(Main.getPlugin());
        }
        return instance;
    }

    /**
     * Spawns a corpse at the given location.
     * @param player    The player to base the corpse on (skin, name)
     * @param location  Where to spawn the corpse
     * @param inventory The loot inventory (null for empty)
     * @return The CorpseData, or null if cancelled
     */
    public CorpseData spawnCorpse(Player player, Location location, Inventory inventory) {
        return plugin.getCorpseManager().spawnCorpse(player, null, location, inventory, 0);
    }

    /**
     * Removes a corpse.
     */
    public void removeCorpse(CorpseData data) {
        plugin.getCorpseManager().removeCorpse(data);
    }

    /**
     * Gets all active corpses.
     */
    public List<CorpseData> getAllCorpses() {
        return plugin.getCorpseManager().getAllCorpses();
    }
}
