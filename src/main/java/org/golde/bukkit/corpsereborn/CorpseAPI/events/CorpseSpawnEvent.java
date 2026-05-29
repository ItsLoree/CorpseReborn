package org.golde.bukkit.corpsereborn.CorpseAPI.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.golde.bukkit.corpsereborn.nms.CorpseData;

/**
 * Called when a corpse is about to be spawned.
 * Can be cancelled to prevent the corpse from spawning.
 */
public class CorpseSpawnEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private final CorpseData corpseData;

    public CorpseSpawnEvent(CorpseData corpseData) {
        this.corpseData = corpseData;
    }

    public CorpseData getCorpseData() { return corpseData; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
