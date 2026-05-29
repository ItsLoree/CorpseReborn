package org.golde.bukkit.corpsereborn.CorpseAPI.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.golde.bukkit.corpsereborn.nms.CorpseData;

/**
 * Called when a player right-clicks a corpse.
 */
public class CorpseClickEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private final CorpseData corpseData;
    private final Player player;

    public CorpseClickEvent(CorpseData corpseData, Player player) {
        this.corpseData = corpseData;
        this.player = player;
    }

    public CorpseData getCorpseData() { return corpseData; }
    public Player getPlayer() { return player; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
