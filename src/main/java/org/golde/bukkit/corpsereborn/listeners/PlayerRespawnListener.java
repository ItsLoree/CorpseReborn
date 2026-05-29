package org.golde.bukkit.corpsereborn.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerRespawnEvent;
import org.golde.bukkit.corpsereborn.Main;

public class PlayerRespawnListener implements Listener {
    private final Main plugin;
    public PlayerRespawnListener(Main plugin) { this.plugin = plugin; }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        // Nothing special needed on respawn in Paper 1.21
        // Corpses are already persistent entities
    }
}
