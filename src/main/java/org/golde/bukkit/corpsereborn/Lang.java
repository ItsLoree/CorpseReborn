package org.golde.bukkit.corpsereborn;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Lang {

    private static YamlConfiguration lang;
    private static String prefix;

    public static void load(Main plugin) {
        String language = plugin.getConfig().getString("language", "it");
        String fileName = "lang_" + language + ".yml";

        File langFile = new File(plugin.getDataFolder(), fileName);
        if (!langFile.exists()) {
            plugin.saveResource(fileName, false);
        }

        lang = YamlConfiguration.loadConfiguration(langFile);

        // Fallback to jar default
        InputStream defStream = plugin.getResource(fileName);
        if (defStream != null) {
            lang.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defStream)));
        }

        prefix = color(lang.getString("prefix", "&8[&6CorpseReborn&8] &r"));
    }

    public static String get(String key) {
        if (lang == null) return key;
        return color(lang.getString(key, key));
    }

    public static String get(String key, String... replacements) {
        String msg = get(key);
        for (int i = 0; i < replacements.length - 1; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1]);
        }
        return msg;
    }

    public static String prefix(String key, String... replacements) {
        return prefix + get(key, replacements);
    }

    public static String color(String text) {
        return text.replace("&", "\u00a7");
    }

    public static String getPrefix() {
        return prefix;
    }
}
