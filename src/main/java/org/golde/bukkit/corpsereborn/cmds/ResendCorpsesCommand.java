package org.golde.bukkit.corpsereborn.cmds;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.golde.bukkit.corpsereborn.Lang;
import org.golde.bukkit.corpsereborn.Main;

public class ResendCorpsesCommand implements CommandExecutor {
    private final Main plugin;
    public ResendCorpsesCommand(Main plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("corpses.resend")) { sender.sendMessage(Lang.get("no-permission")); return true; }
        int count = plugin.getCorpseManager().getAllCorpses().size();
        sender.sendMessage(Lang.get("active-corpses", "%count%", String.valueOf(count)));
        return true;
    }
}
