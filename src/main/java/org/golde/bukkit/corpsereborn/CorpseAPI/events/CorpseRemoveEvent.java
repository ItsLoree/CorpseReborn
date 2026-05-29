package org.golde.bukkit.corpsereborn.CorpseAPI.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.golde.bukkit.corpsereborn.nms.CorpseData;

/**
 * Called when a corpse is removed from the world.
 */
public class CorpseRemoveEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final CorpseData corpseData;

    public CorpseRemoveEvent(CorpseData corpseData) {
        this.corpseData = corpseData;
    }

    public CorpseData getCorpseData() { return corpseData; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
