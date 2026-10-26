package ar.com.tresyetres.tierras;

import com.ghostchu.quickshop.api.event.management.ShopCreateEvent;
import com.ghostchu.quickshop.api.shop.Shop;
import net.kyori.adventure.text.Component;
import org.ayosynk.landClaimPlugin.models.ClaimProfile;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

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
}
