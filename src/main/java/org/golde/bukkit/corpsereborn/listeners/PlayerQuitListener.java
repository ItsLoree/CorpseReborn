package org.golde.bukkit.corpsereborn.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.golde.bukkit.corpsereborn.Main;

public class PlayerQuitListener implements Listener {
    private final Main plugin;
    public PlayerQuitListener(Main plugin) { this.plugin = plugin; }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Nothing special needed on quit
    }
}
