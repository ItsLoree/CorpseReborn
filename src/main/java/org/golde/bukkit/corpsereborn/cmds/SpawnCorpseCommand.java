package org.golde.bukkit.corpsereborn.cmds;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.golde.bukkit.corpsereborn.Lang;
import org.golde.bukkit.corpsereborn.Main;

public class SpawnCorpseCommand implements CommandExecutor {
    private final Main plugin;
    public SpawnCorpseCommand(Main plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("corpses.spawn")) { sender.sendMessage(Lang.get("no-permission")); return true; }
        if (!(sender instanceof Player player)) { sender.sendMessage(Lang.color("&cSolo in gioco.")); return true; }

        Player target = player;
        if (args.length > 0) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) { sender.sendMessage(Lang.get("player-not-found", "%player%", args[0])); return true; }
        }

        Inventory inv = Bukkit.createInventory(null, 54, plugin.getConfigData().getGuiName(target.getName()));
        plugin.getCorpseManager().spawnCorpse(target, null, player.getLocation(), inv, 0);
        sender.sendMessage(Lang.get("corpse-spawned-cmd", "%player%", target.getName()));
        return true;
    }
}
