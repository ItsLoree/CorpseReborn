package org.golde.bukkit.corpsereborn.cmds;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
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

        if (args.length > 0) {
            switch (args[0].toLowerCase()) {

                case "reload":
                    if (!sender.hasPermission("corpses.reload")) { sender.sendMessage(Lang.get("no-permission")); return true; }
                    plugin.reload();
                    sender.sendMessage(Lang.prefix("config-reloaded"));
                    return true;

                case "list":
                    if (!sender.hasPermission("corpses.reload")) { sender.sendMessage(Lang.get("no-permission")); return true; }
                    sendAdminPanel(sender);
                    return true;

                case "timer":
                    if (!sender.hasPermission("corpses.reload")) { sender.sendMessage(Lang.get("no-permission")); return true; }
                    if (args.length < 2) {
                        // Mostra valore attuale
                        int cur = plugin.getConfigData().getCorpseTime();
                        String curStr = cur == -1 ? "&a∞ &7(permanente)" : "&e" + cur + "s";
                        sender.sendMessage(Lang.color("&8[&6⚰&8] &7Timer attuale&8: " + curStr));
                        sender.sendMessage(Lang.color("&8[&6⚰&8] &7Uso&8: &e/cr timer <secondi> &8| &e/cr timer -1 &7per permanente"));
                        return true;
                    }
                    try {
                        int seconds = Integer.parseInt(args[1]);
                        if (seconds < -1) seconds = -1;
                        plugin.getConfigData().setCorpseTime(seconds);
                        String timeStr = seconds == -1 ? "∞ (permanente)" : seconds + "s";
                        sender.sendMessage(Lang.prefix("timer-set", "%time%", timeStr));
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Lang.prefix("invalid-number"));
                    }
                    return true;

                case "despawnlooted":
                    if (!sender.hasPermission("corpses.reload")) { sender.sendMessage(Lang.get("no-permission")); return true; }
                    if (args.length < 2) {
                        // Mostra valore attuale
                        boolean cur = plugin.getConfigData().shouldDespawnOnLooted();
                        sender.sendMessage(Lang.color("&8[&6⚰&8] &7Despawn se lootato&8: " + (cur ? "&aSI" : "&cNO")));
                        sender.sendMessage(Lang.color("&8[&6⚰&8] &7Uso&8: &e/cr despawnlooted <si/no>"));
                        return true;
                    }
                    String val = args[1].toLowerCase();
                    if (!val.equals("true") && !val.equals("false") && !val.equals("si") && !val.equals("no")
                            && !val.equals("yes") && !val.equals("1") && !val.equals("0")) {
                        sender.sendMessage(Lang.prefix("invalid-boolean"));
                        return true;
                    }
                    boolean enabled = val.equals("true") || val.equals("si") || val.equals("yes") || val.equals("1");
                    plugin.getConfigData().setDespawnOnLooted(enabled);
                    sender.sendMessage(Lang.prefix("despawn-looted-set", "%value%", enabled ? "SI ✔" : "NO ✘"));
                    return true;
            }
        }

        sendAdminPanel(sender);
        return true;
    }

    private void sendAdminPanel(CommandSender sender) {
        List<CorpseData> corpses = plugin.getCorpseManager().getAllCorpses();
        String v = plugin.getDescription().getVersion();

        int timer = plugin.getConfigData().getCorpseTime();
        boolean dol = plugin.getConfigData().shouldDespawnOnLooted();
        String timerStr  = timer == -1 ? "&a∞ &7permanente" : "&e" + timer + "s";
        String dolStr    = dol ? "&a✔ SI" : "&c✘ NO";

        s(sender, "");
        s(sender, "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        s(sender, "  &6⚰ &lCorpseReborn &8v" + v + "  &7by &fGriffer");
        s(sender, "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        s(sender, "");
        s(sender, "  &7⚙ &lImpostazioni");
        s(sender, "  &8│ &7Timer despawn      &8» " + timerStr);
        s(sender, "  &8│ &7Despawn se lootato &8» " + dolStr);
        s(sender, "  &8│ &7Cadaveri attivi    &8» &e" + corpses.size());
        s(sender, "");

        if (!corpses.isEmpty()) {
            s(sender, "  &7☠ &lCadaveri attivi");
            s(sender, "  &8┌─────────────────────────────────────");
            int shown = 0;
            for (CorpseData data : corpses) {
                if (shown >= 8) {
                    s(sender, "  &8│ &7... e altri &e" + (corpses.size() - 8) + " &7cadaveri");
                    break;
                }
                String world = data.getLocation().getWorld() != null ? data.getLocation().getWorld().getName() : "?";
                int x = data.getLocation().getBlockX();
                int y = data.getLocation().getBlockY();
                int z = data.getLocation().getBlockZ();
                String timeStr;
                if (data.getCorpseTime() <= 0) {
                    timeStr = "&a∞";
                } else {
                    long elapsed = (System.currentTimeMillis() - data.getSpawnTime()) / 1000;
                    int left = (int) Math.max(0, data.getCorpseTime() - elapsed);
                    timeStr = left > 0 ? "&e" + left + "s" : "&c0s";
                }
                s(sender, "  &8│ &f" + data.getPlayerName()
                        + " &8┃ &7" + world
                        + " &8[" + x + "&8," + y + "&8," + z + "&8]"
                        + " &8┃ " + timeStr);
                shown++;
            }
            s(sender, "  &8└─────────────────────────────────────");
            s(sender, "");
        }

        s(sender, "  &7📋 &lComandi");
        s(sender, "  &8┌─────────────────────────────────────");
        s(sender, "  &8│ &e/cr                      &8» &7Questo pannello");
        s(sender, "  &8│ &e/cr reload               &8» &7Ricarica configurazione");
        s(sender, "  &8│ &e/cr timer &8[&7sec&8]         &8» &7Mostra/imposta timer");
        s(sender, "  &8│ &e/cr despawnlooted &8[&7si/no&8]&8» &7Despawn se lootato");
        s(sender, "  &8│ &e/cr list                 &8» &7Lista cadaveri");
        s(sender, "  &8│ &e/spawncorpse &8[&7player&8]   &8» &7Spawna cadavere");
        s(sender, "  &8│ &e/removecorpse &8[&7raggio&8]  &8» &7Rimuovi cadaveri");
        s(sender, "  &8│ &e/togglecorpse             &8» &7Attiva/disattiva vista");
        s(sender, "  &8└─────────────────────────────────────");
        s(sender, "");
    }

    private void s(CommandSender sender, String msg) {
        sender.sendMessage(Lang.color(msg));
    }
}
