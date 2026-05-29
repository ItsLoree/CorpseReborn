package org.golde.bukkit.corpsereborn;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import java.util.ArrayList;
import java.util.List;

public class ConfigData {

    private final Main plugin;
    private final FileConfiguration config;

    public ConfigData(Main plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    public int getCorpseTime() {
        return config.getInt("corpse-time", 300);
    }

    public boolean shouldSaveCorpses() {
        return config.getBoolean("save-corpses", true);
    }

    public boolean hasLootingInventory() {
        return config.getBoolean("looting-inventory", true);
    }

    public String getGuiName(String playerName) {
        return Lang.get("gui-name", "%player%", playerName);
    }

    public String getFinishLootingMessage(String playerName) {
        return Lang.get("finish-looting", "%player%", playerName);
    }

    public boolean shouldShowNametag() {
        return config.getBoolean("show-nametag", true);
    }

    public boolean shouldRenderArmor() {
        return config.getBoolean("render-armor", true);
    }

    public boolean shouldCheckForUpdates() {
        return config.getBoolean("check-for-updates", true);
    }

    public List<DamageCause> getDamageCausesThatDontCauseACorpse() {
        List<DamageCause> causes = new ArrayList<>();
        List<String> causeNames = config.getStringList("no-corpse-on-death-causes");
        for (String name : causeNames) {
            try {
                causes.add(DamageCause.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Unknown damage cause in config: " + name);
            }
        }
        return causes;
    }

    public List<String> getDisabledWorlds() {
        return config.getStringList("disabled-worlds");
    }

    public boolean shouldDespawnOnLooted() {
        return config.getBoolean("despawn-on-looted", false);
    }

    public void setCorpseTime(int seconds) {
        plugin.getConfig().set("corpse-time", seconds);
        plugin.saveConfig();
    }

    public void setDespawnOnLooted(boolean value) {
        plugin.getConfig().set("despawn-on-looted", value);
        plugin.saveConfig();
    }
}
