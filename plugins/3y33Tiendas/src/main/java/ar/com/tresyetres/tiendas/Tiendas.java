package ar.com.tresyetres.tiendas;

import com.ghostchu.quickshop.api.event.Phase;
import com.ghostchu.quickshop.api.event.management.ShopCreateEvent;
import com.ghostchu.quickshop.api.shop.Shop;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Reglas de tiendas (QuickShop) con Lands: solo en el terreno de tu hermandad, nombradas como el terreno. */
final class Tiendas implements Listener {
    private static final Pattern RENOMBRAR = Pattern.compile("^/(?:[a-z]+:)?(?:qs|quickshop|shop|tienda|tiendas|chestshop|cshop)\\s+(?:name|nombre|setname)(?:\\s.*)?$");
    private final TiendasPlugin pl;
    private final LandsPuente lands;

    Tiendas(TiendasPlugin pl, LandsPuente lands) { this.pl = pl; this.lands = lands; }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void crear(ShopCreateEvent e) {
        Player p = e.user().getBukkitPlayer().orElse(null);
        if (p == null) return;
        if (e.phase() == Phase.POST) { nombrar(e); return; }
        if (p.hasPermission("tresyetres.staff")) return;
        if (!e.phase().cancellable()) return;
        LandsPuente.Terreno t = lands.en(e.location(), p.getUniqueId());
        String motivo = null;
        if (t == null) motivo = "Solo podés crear tiendas dentro del terreno de tu hermandad. Creá la tuya con /lands.";
        else if (!t.miembro()) motivo = "Solo podés crear tiendas en el terreno de tu propia hermandad.";
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
        LandsPuente.Terreno t = lands.en(e.location(), null);
        if (t == null) return;
        o.get().setShopName(t.nombre());
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
