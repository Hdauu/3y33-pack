package ar.com.tresyetres.trabajos;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Placeholders de PlaceholderAPI: %trabajos_insignia% (MiniMessage con la insignia del chat y un espacio, o vacio). */
final class TrabajosExpansion extends PlaceholderExpansion {
    private final TrabajosPlugin pl;

    TrabajosExpansion(TrabajosPlugin pl) { this.pl = pl; }

    @Override public String getIdentifier() { return "trabajos"; }
    @Override public String getAuthor() { return "3y33"; }
    @Override public String getVersion() { return pl.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer off, String params) {
        if (off == null) return "";
        Player p = off.getPlayer();
        if (p == null) return "";
        if (params.equalsIgnoreCase("insignia")) return pl.insignias().mini(pl.perfiles().de(p));
        return null;
    }
}
