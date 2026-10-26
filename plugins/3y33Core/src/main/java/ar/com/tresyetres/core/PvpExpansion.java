package ar.com.tresyetres.core;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** %3y33_pvp% = espada roja si tiene el PvP activado, verde si no (para el TAB). */
final class PvpExpansion extends PlaceholderExpansion {
    private final Core core;
    private final Pvp pvp;

    PvpExpansion(Core core, Pvp pvp) { this.core = core; this.pvp = pvp; }

    @Override public String getIdentifier() { return "3y33"; }
    @Override public String getAuthor() { return "3y33"; }
    @Override public String getVersion() { return core.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer off, String params) {
        if (!(off instanceof Player p)) return "";
        if (params.equalsIgnoreCase("pvp")) return pvp.tienePvp(p) ? "&c⚔" : "&a⚔";
        if (params.equalsIgnoreCase("pvp_estado")) return pvp.tienePvp(p) ? "activado" : "desactivado";
        return null;
    }
}
