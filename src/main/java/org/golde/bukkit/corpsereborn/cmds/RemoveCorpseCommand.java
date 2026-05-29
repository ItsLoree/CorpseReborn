package org.golde.bukkit.corpsereborn.cmds;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.golde.bukkit.corpsereborn.Lang;
import org.golde.bukkit.corpsereborn.Main;

public class RemoveCorpseCommand implements CommandExecutor {
    private final Main plugin;
    public RemoveCorpseCommand(Main plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("corpses.remove")) { sender.sendMessage(Lang.get("no-permission")); return true; }
        if (!(sender instanceof Player player)) { sender.sendMessage(Lang.color("&cSolo in gioco.")); return true; }

        double radius = 10.0;
        if (args.length > 0) {
            try { radius = Double.parseDouble(args[0]); }
            catch (NumberFormatException e) { sender.sendMessage(Lang.color("&cRaggio non valido: " + args[0])); return true; }
        }

        int removed = plugin.getCorpseManager().removeCorpsesInRadius(player.getLocation(), radius);
        sender.sendMessage(Lang.get("removed-corpses", "%count%", String.valueOf(removed), "%radius%", String.valueOf(radius)));
        return true;
    }
}
