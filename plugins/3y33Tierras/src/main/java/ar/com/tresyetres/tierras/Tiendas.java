package ar.com.tresyetres.tierras;

import com.ghostchu.quickshop.api.event.management.ShopCreateEvent;
import com.ghostchu.quickshop.api.shop.Shop;
import net.kyori.adventure.text.Component;
import org.ayosynk.landClaimPlugin.models.ClaimProfile;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.ayosynk.landClaimPlugin.models.Warp;
import java.util.Map;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Reglas de tiendas (QuickShop): solo en terrenos propios, nombradas como el terreno. */
final class Tiendas implements Listener {
    private static final Pattern RENOMBRAR = Pattern.compile("^/(?:[a-z]+:)?(?:qs|quickshop|shop|tienda|tiendas|chestshop|cshop)\\s+(?:name|nombre|setname)(?:\\s.*)?$");
    private final Tierras pl;

    Tiendas(Tierras pl) { this.pl = pl; }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void crear(ShopCreateEvent e) {
        Player p = e.user().getBukkitPlayer().orElse(null);
        if (p == null) return;
        if (p.hasPermission("tresyetres.staff")) {
            if (e.phase() == com.ghostchu.quickshop.api.event.Phase.POST) nombrar(e);
            return;
        }
        if (e.phase() == com.ghostchu.quickshop.api.event.Phase.POST) { nombrar(e); return; }
        if (!e.phase().cancellable()) return;
        ClaimProfile c = pl.api().getClaimAt(e.location());
        String motivo = null;
        if (c == null) motivo = "Solo podés crear tiendas dentro de un terreno reclamado. Reclamá el tuyo con /tierra.";
        else if (!c.getOwnerId().equals(p.getUniqueId())) motivo = "Solo podés crear tiendas en terrenos que sean tuyos.";
        if (motivo == null) return;
        try {
            e.setCancelled(true, Component.text(motivo));
        } catch (IllegalStateException ex) {
            return;
        }
        pl.msg(p, "&c" + motivo);
    }

    private void nombrar(ShopCreateEvent e) {
        Optional<Shop> o = e.shop();
        if (o.isEmpty()) return;
        ClaimProfile c = pl.api().getClaimAt(e.location());
        if (c == null) return;
        o.get().setShopName(c.getName());
    }

    /** Cambiar el nombre de una tienda es un beneficio de rangos pagos. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void renombrar(PlayerCommandPreprocessEvent e) {
        String m = e.getMessage().toLowerCase(Locale.ROOT).trim();
        if (!RENOMBRAR.matcher(m).matches()) return;
        Player p = e.getPlayer();
        if (p.hasPermission("tresyetres.tienda.nombre") || p.hasPermission("tresyetres.staff")) return;
        e.setCancelled(true);
        pl.msg(p, "&cEl nombre de tu tienda es el de tu terreno. Cambiarlo es un beneficio de los rangos pagos.");
    }

    /** El catálogo no te lleva al cofre: te lleva al punto de teletransporte del terreno del dueño. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void catalogo(PlayerTeleportEvent e) {
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) return;
        Location to = e.getTo();
        if (to == null || to.getWorld() == null) return;
        Shop tienda = null;
        var mgr = com.ghostchu.quickshop.api.QuickShopAPI.getInstance().getShopManager();
        double mejor = 25.0; // hasta 5 bloques
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                var mapa = mgr.getShops(to.getWorld().getChunkAt((to.getBlockX() >> 4) + cx, (to.getBlockZ() >> 4) + cz));
                if (mapa == null) continue;
                for (Shop sh : mapa.values()) {
                    Location l = sh.bukkitLocation();
                    if (l == null || l.getWorld() == null || !l.getWorld().equals(to.getWorld())) continue;
                    double d = l.clone().add(0.5, 0.5, 0.5).distanceSquared(to);
                    if (d < mejor) { mejor = d; tienda = sh; }
                }
            }
        }
        pl.getLogger().info("[catalogo] " + e.getPlayer().getName() + " tp " + to.getBlockX() + "," + to.getBlockY() + "," + to.getBlockZ() + " tienda=" + (tienda != null));
        if (tienda == null) return;
        Player p = e.getPlayer();
        ClaimProfile c = pl.api().getClaimAt(tienda.bukkitLocation());
        Location punto = null;
        if (c != null) {
            Map<String, Warp> warps = pl.api().getWarps(c.getProfileId());
            if (warps != null && !warps.isEmpty()) {
                Warp w = warps.get("spawn");
                if (w == null) w = warps.values().iterator().next();
                punto = w.getLocation();
            }
        }
        pl.getLogger().info("[catalogo] terreno=" + (c == null ? "ninguno" : c.getName()) + " punto=" + punto);
        if (punto == null) {
            e.setCancelled(true);
            pl.msg(p, "&cEse terreno todavía no tiene un punto de teletransporte.");
            return;
        }
        e.setTo(punto);
        pl.msg(p, "&7Te llevo al punto de &f" + c.getName() + "&7.");
    }

    /** /tienda solo (sin nada más) abre el catálogo de tiendas. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void soloTienda(PlayerCommandPreprocessEvent e) {
        String m = e.getMessage().toLowerCase(Locale.ROOT).trim();
        if (m.equals("/tienda") || m.equals("/tiendas")) {
            e.setCancelled(true);
            e.getPlayer().performCommand("qs browse");
        }
    }
}
