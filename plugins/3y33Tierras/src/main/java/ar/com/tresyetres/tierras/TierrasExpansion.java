package ar.com.tresyetres.tierras;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.ayosynk.landClaimPlugin.models.ClaimProfile;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

final class TierrasExpansion extends PlaceholderExpansion {

    private final Tierras plugin;

    TierrasExpansion(Tierras plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "tierras";
    }

    @Override
    public String getAuthor() {
        return "3y33";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer off, String params) {
        if (off == null) return "";
        var api = plugin.api();
        switch (params.toLowerCase()) {
            case "chunks_usados":
                return String.valueOf(api.getTotalChunksByOwner(off.getUniqueId()));
            case "chunks_limite":
                return String.valueOf(off instanceof Player p ? api.getClaimLimit(p) : api.getClaimLimit(off.getUniqueId()));
            case "chunks_libres": {
                int lim = off instanceof Player p ? api.getClaimLimit(p) : api.getClaimLimit(off.getUniqueId());
                return String.valueOf(Math.max(0, lim - api.getTotalChunksByOwner(off.getUniqueId())));
            }
            case "comprados":
                return String.valueOf(plugin.comprados(off.getUniqueId()));
            case "precio_chunk":
                return plugin.plata(plugin.precioSiguiente(off.getUniqueId()));
            case "terrenos":
                return String.valueOf(plugin.terrenosDe(off.getUniqueId()));
            case "terrenos_max":
                return off instanceof Player p ? String.valueOf(plugin.maxTerrenos(p)) : "?";
            default:
                break;
        }
        if (!(off instanceof Player p)) return "";
        ClaimProfile aca = api.getClaimAt(p.getLocation());
        switch (params.toLowerCase()) {
            case "aca_nombre":
                return aca == null ? "Zona libre" : aca.getName();
            case "aca_monstruos":
                return aca == null ? "-" : (plugin.monstruos(aca) ? "Sí" : "No");
            case "aca_animales":
                return aca == null ? "-" : (plugin.animales(aca) ? "Sí" : "No");
            default:
                return null;
        }
    }
}
