package org.golde.bukkit.corpsereborn.listeners;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.golde.bukkit.corpsereborn.Lang;
import org.golde.bukkit.corpsereborn.Main;
import org.golde.bukkit.corpsereborn.nms.CorpseData;

public class InventoryClickListener implements Listener {

    private final Main plugin;

    public InventoryClickListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand stand)) return;

        Player player = event.getPlayer();
        CorpseData data = plugin.getCorpseManager().getCorpseByEntity(stand);
        if (data == null) return;

        event.setCancelled(true);

        if (Main.whoCanNotSeeCorpses.contains(player.getName())) return;
        if (!plugin.getConfigData().hasLootingInventory()) return;

        if (data.getInventory() == null || data.isInventoryEmpty()) {
            // Inventario già vuoto - non mostrare messaggio, non fare nulla
            return;
        }

        player.openInventory(data.getInventory());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        for (var data : plugin.getCorpseManager().getAllCorpses()) {
            if (data.getInventory() != null && data.getInventory().equals(event.getInventory())) {
                if (data.isInventoryEmpty() && plugin.getConfigData().shouldDespawnOnLooted()) {
                    plugin.getCorpseManager().removeCorpse(data);
                }
                break;
            }
        }
    }
}
