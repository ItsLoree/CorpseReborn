package org.golde.bukkit.corpsereborn.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.golde.bukkit.corpsereborn.Main;

public class PlayerJoinListener implements Listener {
    private final Main plugin;
    public PlayerJoinListener(Main plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Invia i pacchetti dei cadaveri al giocatore appena entrato
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getCorpseManager().resendCorpsesToPlayer(event.getPlayer());
        }, 20L);
    }
}
