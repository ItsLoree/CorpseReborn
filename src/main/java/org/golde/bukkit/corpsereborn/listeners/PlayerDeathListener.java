package org.golde.bukkit.corpsereborn.listeners;

// Test deploy automatico v2 - by Griffer

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.golde.bukkit.corpsereborn.ConfigData;
import org.golde.bukkit.corpsereborn.Main;

import java.util.ArrayList;
import java.util.List;

public class PlayerDeathListener implements Listener {

    private final Main plugin;

    public PlayerDeathListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        ConfigData cfg = plugin.getConfigData();

        // Check if world is disabled
        if (cfg.getDisabledWorlds().contains(player.getWorld().getName())) return;

        // Check if damage cause should skip corpse
        if (player.getLastDamageCause() != null) {
            var cause = player.getLastDamageCause().getCause();
            if (cfg.getDamageCausesThatDontCauseACorpse().contains(cause)) return;
        }

        // === FIX DUPE ===
        // Prendiamo gli item dai drops dell'evento (quello che cadrebbe a terra)
        // e li mettiamo nel cadavere RIMUOVENDOLI dai drops
        // Così gli item NON cadono a terra E sono nel cadavere - nessun dupe!

        Inventory corpseInv = Bukkit.createInventory(null, 54, cfg.getGuiName(player.getName()));

        // Copia i drops nel cadavere
        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        int slot = 0;
        for (ItemStack item : drops) {
            if (item != null && slot < 54) {
                corpseInv.setItem(slot++, item.clone());
            }
        }

        // RIMUOVI tutti i drops dall'evento - così non cadono a terra
        event.getDrops().clear();

        // Cattura posizione e slot
        int selectedSlot = player.getInventory().getHeldItemSlot();
        var loc = player.getLocation().clone();

        // Spawna il cadavere al prossimo tick
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            var corpseData = plugin.getCorpseManager().spawnCorpse(player, null, loc, corpseInv, selectedSlot);
        }, 1L);
    }
}
