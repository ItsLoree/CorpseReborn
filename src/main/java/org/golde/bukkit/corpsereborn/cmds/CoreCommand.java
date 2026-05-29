package org.golde.bukkit.corpsereborn.cmds;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.golde.bukkit.corpsereborn.Lang;
import org.golde.bukkit.corpsereborn.Main;
import org.golde.bukkit.corpsereborn.nms.CorpseData;

import java.util.List;

public class CoreCommand implements CommandExecutor {

    private final Main plugin;

    public CoreCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("corpses.reload")) {
                sender.sendMessage(Lang.get("no-permission"));
                return true;
            }
            plugin.reload();
            sender.sendMessage(Lang.get("config-reloaded"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
            if (!sender.hasPermission("corpses.reload")) {
                sender.sendMessage(Lang.get("no-permission"));
                return true;
            }
            sendAdminPanel(sender);
            return true;
        }

        // Default: mostra il pannello admin
        sendAdminPanel(sender);
        return true;
    }

    private void sendAdminPanel(CommandSender sender) {
        List<CorpseData> corpses = plugin.getCorpseManager().getAllCorpses();
        String v = plugin.getDescription().getVersion();

        sender.sendMessage(Lang.color(""));
        sender.sendMessage(Lang.color("&8&m                                        "));
        sender.sendMessage(Lang.color("  &6⚰ &lCorpseReborn &8v" + v + "  &7by &fGriffer"));
        sender.sendMessage(Lang.color("&8&m                                        "));
        sender.sendMessage(Lang.color("  &7Cadaveri attivi&8: &e" + corpses.size()));

        if (!corpses.isEmpty()) {
            sender.sendMessage(Lang.color("  &8┌─────────────────────────────────────"));
            int shown = 0;
            for (CorpseData data : corpses) {
                if (shown >= 10) {
                    sender.sendMessage(Lang.color("  &8│ &7... e altri &e" + (corpses.size() - 10) + " &7cadaveri"));
                    break;
                }
                String world = data.getLocation().getWorld() != null ? data.getLocation().getWorld().getName() : "?";
                int x = data.getLocation().getBlockX();
                int y = data.getLocation().getBlockY();
                int z = data.getLocation().getBlockZ();
                int timeLeft = -1;
                if (data.getCorpseTime() > 0) {
                    long elapsed = (System.currentTimeMillis() - data.getSpawnTime()) / 1000;
                    timeLeft = (int) Math.max(0, data.getCorpseTime() - elapsed);
                }
                String timeStr = timeLeft == -1 ? "&a∞" : "&e" + timeLeft + "s";
                sender.sendMessage(Lang.color("  &8│ &f" + data.getPlayerName()
                        + " &8| &7" + world + " &8[&7" + x + "&8,&7" + y + "&8,&7" + z + "&8]"
                        + " &8| " + timeStr));
                shown++;
            }
            sender.sendMessage(Lang.color("  &8└─────────────────────────────────────"));
        }

        sender.sendMessage(Lang.color("  &8┌─────────────────────────────────────"));
        sender.sendMessage(Lang.color("  &8│ &e/cr reload       &8- &7Ricarica config"));
        sender.sendMessage(Lang.color("  &8│ &e/cr list         &8- &7Lista cadaveri"));
        sender.sendMessage(Lang.color("  &8│ &e/spawncorpse     &8- &7Spawna cadavere"));
        sender.sendMessage(Lang.color("  &8│ &e/removecorpse    &8- &7Rimuovi cadaveri"));
        sender.sendMessage(Lang.color("  &8│ &e/togglecorpse    &8- &7Attiva/disattiva"));
        sender.sendMessage(Lang.color("  &8└─────────────────────────────────────"));
        sender.sendMessage(Lang.color(""));
    }
}
