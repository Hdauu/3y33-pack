package ar.com.tresyetres.core;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** %3y33_pvp% = espada roja si tiene el PvP activado, verde si no (para el TAB). */
final class PvpExpansion extends PlaceholderExpansion {
    private final Core core;
    private final Pvp pvp;
    private final Color color;

    PvpExpansion(Core core, Pvp pvp, Color color) { this.core = core; this.pvp = pvp; this.color = color; }

    @Override public String getIdentifier() { return "3y33"; }
    @Override public String getAuthor() { return "3y33"; }
    @Override public String getVersion() { return core.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer off, String params) {
        if (!(off instanceof Player p)) return "";
        if (params.equalsIgnoreCase("pvp")) return pvp.tienePvp(p) ? "&c⚔" : "&a⚔";
        // sin colores: el cartel (ModernTags) le pone el color. El que no corresponde devuelve un espacio de ancho cero.
        if (params.equalsIgnoreCase("pvp_rojo")) return pvp.tienePvp(p) ? "⚔" : "";
        if (params.equalsIgnoreCase("pvp_verde")) return pvp.tienePvp(p) ? "" : "⚔";
        if (params.equalsIgnoreCase("pvp_estado")) return pvp.tienePvp(p) ? "activado" : "desactivado";
        if (params.startsWith("n_")) return color.efectivo(p).equals(params.substring(2)) ? p.getName() : "";
        if (params.equalsIgnoreCase("color_a")) return color.colores(p)[0];
        if (params.equalsIgnoreCase("color_b")) return color.colores(p)[1];
        return null;
    }
}
