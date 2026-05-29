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
        // Nothing extra needed - armor stands are persistent entities
    }
}
