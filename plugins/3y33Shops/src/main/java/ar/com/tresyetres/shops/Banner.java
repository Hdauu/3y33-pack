package ar.com.tresyetres.shops;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Fondo y banner de la ventana TIENDA: es un caracter de la fuente 3y33:tienda del resource pack que se dibuja como titulo.
 * Solo se usa con quien tiene Java y no rechazo el pack; los demas ven la ventana comun con su titulo de texto.
 * Config {@code ventana-banner}: staff (solo el staff, para probar), todos, no.
 */
final class Banner implements Listener {
    private final ShopsPlugin pl;
    private final Set<UUID> sinPack = new HashSet<>();

    Banner(ShopsPlugin pl) { this.pl = pl; }

    @EventHandler
    public void alEstadoDelPack(PlayerResourcePackStatusEvent e) {
        PlayerResourcePackStatusEvent.Status s = e.getStatus();
        pl.getLogger().info("Pack de recursos de " + e.getPlayer().getName() + ": " + s);
        switch (s) {
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> sinPack.add(e.getPlayer().getUniqueId());
            case SUCCESSFULLY_LOADED -> sinPack.remove(e.getPlayer().getUniqueId());
            default -> { }
        }
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent e) { sinPack.remove(e.getPlayer().getUniqueId()); }

    /** Si a este jugador hay que mostrarle la ventana con el banner. */
    boolean activo(Player p) {
        String modo = pl.getConfig().getString("ventana-banner", "staff").toLowerCase(java.util.Locale.ROOT);
        if (modo.equals("no") || Holograma.esBedrock(p)) return false;
        if (modo.equals("staff")) return p.hasPermission("tresyetres.staff");
        return !sinPack.contains(p.getUniqueId());
    }

    /** Fondos disponibles: cada uno es un caracter de la fuente 3y33:tienda del pack. */
    static final char TIENDA = (char) 0xE300, AJUSTES = (char) 0xE301;
    private static final char CORRER_8 = (char) 0xF808;

    /** El titulo: un solo caracter (corrido 8 a la izquierda para quedar pegado al borde) que dibuja todo el fondo. En blanco para que no lo tiña. */
    Component titulo(char fondo) {
        return Component.text("" + CORRER_8 + fondo).font(Key.key("3y33", "tienda")).color(NamedTextColor.WHITE);
    }
}
