package org.golde.bukkit.corpsereborn;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.golde.bukkit.corpsereborn.cmds.*;
import org.golde.bukkit.corpsereborn.listeners.*;
import org.golde.bukkit.corpsereborn.nms.CorpseManager;

import java.util.HashSet;
import java.util.Set;

public class Main extends JavaPlugin {

    private static Main plugin;
    private CorpseManager corpseManager;
    private ConfigData configData;

    public static Set<String> whoCanNotSeeCorpses = new HashSet<>();

    @Override
    public void onEnable() {
        plugin = this;

        saveDefaultConfig();
        saveResource("lang_it.yml", false);
        saveResource("lang_en.yml", false);

        this.configData = new ConfigData(this);
        Lang.load(this);

        this.corpseManager = new CorpseManager(this);

        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new InventoryClickListener(this), this);
        getServer().getPluginManager().registerEvents(new ChunkLoadListener(this), this);

        getCommand("spawncorpse").setExecutor(new SpawnCorpseCommand(this));
        getCommand("removecorpse").setExecutor(new RemoveCorpseCommand(this));
        getCommand("corpsereborn").setExecutor(new CoreCommand(this));
        getCommand("resendcorpses").setExecutor(new ResendCorpsesCommand(this));
        getCommand("togglecorpse").setExecutor(new ToggleCorpseCommand(this));

        Bukkit.getScheduler().runTaskLater(this, () -> corpseManager.loadCorpses(), 20L);

        getLogger().info("╔══════════════════════════════╗");
        getLogger().info("║    CorpseReborn v" + getDescription().getVersion() + "        ║");
        getLogger().info("║    Developed by Griffer       ║");
        getLogger().info("║    Paper 1.21 Edition         ║");
        getLogger().info("╚══════════════════════════════╝");
    }

    @Override
    public void onDisable() {
        if (corpseManager != null) {
            corpseManager.saveCorpses();
            corpseManager.removeAllCorpses();
        }
        getLogger().info("[CorpseReborn] Plugin disabilitato. Cadaveri salvati. | by Griffer");
    }

    public void reload() {
        reloadConfig();
        this.configData = new ConfigData(this);
        Lang.load(this);
        corpseManager.saveCorpses();
        corpseManager.removeAllCorpses();
        corpseManager.loadCorpses();
    }

    public static Main getPlugin() { return plugin; }
    public CorpseManager getCorpseManager() { return corpseManager; }
    public ConfigData getConfigData() { return configData; }
}
