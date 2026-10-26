package ar.com.tresyetres.tierras;

import org.ayosynk.landClaimPlugin.LandClaimPlugin;
import org.ayosynk.landClaimPlugin.api.event.ClaimCreateEvent;
import org.ayosynk.landClaimPlugin.api.event.ClaimDeleteEvent;
import org.ayosynk.landClaimPlugin.models.ChunkPosition;
import org.ayosynk.landClaimPlugin.models.ClaimProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Un chunk que no toca ningún chunk de tu terreno activo se convierte en un terreno nuevo, no se suma al viejo. */
final class TerrenosAparte implements Listener {
    private final Tierras pl;
    private final Set<UUID> enProceso = new HashSet<>();

    TerrenosAparte(Tierras pl) { this.pl = pl; }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void alReclamar(ClaimCreateEvent e) {
        ClaimProfile perfil = e.getProfile();
        UUID id = e.getCreatorId();
        if (perfil == null || id == null || !perfil.isOwner(id) || enProceso.contains(id)) return;
        Player p = Bukkit.getPlayer(id);
        if (p == null) return;
        ChunkPosition nuevo = e.getChunk();
        if (nuevo == null || perfil.getOwnedChunks().isEmpty()) return;
        for (ChunkPosition c : perfil.getOwnedChunks()) {
            if (c.world().equals(nuevo.world()) && (nuevo.isAdjacentTo(c, true) || nuevo.equals(c))) return;
        }
        // no toca ninguno de sus chunks: terreno aparte
        e.setCancelled(true);
        if (pl.terrenosDe(id) >= pl.maxTerrenos(p)) {
            pl.msg(p, "&cEse chunk está separado de tu terreno y ya tenés el máximo de terrenos (&f" + pl.maxTerrenos(p)
                    + "&c). Reclamalo pegado a uno de tus terrenos.");
            return;
        }
        String base = p.getName();
        int n = pl.terrenosDe(id) + 1;
        String nombre = base + " " + n;
        while (pl.api().getClaimByName(nombre) != null) nombre = base + " " + (++n);
        final String elegido = nombre;
        enProceso.add(id);
        Bukkit.getScheduler().runTask(pl, () -> {
            p.performCommand("claim create " + elegido);
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                p.performCommand("claim");
                pl.msg(p, "&7Ese chunk está separado de tus otros terrenos, así que armé uno nuevo: &f" + elegido + "&7.");
                Bukkit.getScheduler().runTaskLater(pl, () -> enProceso.remove(id), 20L);
            }, 3L);
        });
    }

    /** Si te quedás sin ningún chunk en un terreno, el terreno se borra y deja de aparecer en /terrenos. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void alLiberar(ClaimDeleteEvent e) {
        ClaimProfile perfil = e.getProfile();
        if (perfil == null) return;
        final UUID idPerfil = perfil.getProfileId();
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            var mgr = LandClaimPlugin.getInstance().getClaimManager();
            ClaimProfile vivo = mgr.getProfileById(idPerfil);
            if (vivo != null && vivo.getOwnedChunks().isEmpty() && !enProceso.contains(vivo.getOwnerId())) {
                mgr.deleteProfile(idPerfil, ClaimDeleteEvent.DeleteReason.SYSTEM);
            }
        }, 5L);
    }
}
