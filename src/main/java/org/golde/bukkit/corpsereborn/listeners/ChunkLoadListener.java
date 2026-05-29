package org.golde.bukkit.corpsereborn.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.golde.bukkit.corpsereborn.Main;

public class ChunkLoadListener implements Listener {
    private final Main plugin;
    public ChunkLoadListener(Main plugin) { this.plugin = plugin; }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // Armor stand entities are automatically loaded with chunks in Paper 1.21
        // No special handling needed unlike the old NMS packet approach
    }
}
