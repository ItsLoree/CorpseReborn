package org.golde.bukkit.corpsereborn.cmds;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.golde.bukkit.corpsereborn.Lang;
import org.golde.bukkit.corpsereborn.Main;

public class ToggleCorpseCommand implements CommandExecutor {
    private final Main plugin;
    public ToggleCorpseCommand(Main plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("corpses.toggle")) { sender.sendMessage(Lang.get("no-permission")); return true; }
        if (!(sender instanceof Player player)) { sender.sendMessage(Lang.color("&cSolo in gioco.")); return true; }

        String name = player.getName();
        if (Main.whoCanNotSeeCorpses.contains(name)) {
            Main.whoCanNotSeeCorpses.remove(name);
            player.sendMessage(Lang.get("toggle-on"));
        } else {
            Main.whoCanNotSeeCorpses.add(name);
            player.sendMessage(Lang.get("toggle-off"));
        }
        return true;
    }
}
