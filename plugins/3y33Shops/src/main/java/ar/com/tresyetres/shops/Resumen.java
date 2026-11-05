package ar.com.tresyetres.shops;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cuando un dueño entra al server le cuenta lo que pasó en sus tiendas mientras no estaba: cuánto cobró, cuánto gastó y qué se movió más.
 * Respeta el botón de avisos de cada tienda.
 */
final class Resumen implements Listener {
    private static final long MINIMO_MS = 60_000L;       // si estuvo afuera menos de un minuto no vale la pena

    private final ShopsPlugin pl;

    Resumen(ShopsPlugin pl) { this.pl = pl; }

    @EventHandler
    public void alSalir(PlayerQuitEvent e) { pl.almacen().guardarVisto(e.getPlayer().getUniqueId(), System.currentTimeMillis()); }

    @EventHandler
    public void alEntrar(PlayerJoinEvent e) {
        final UUID u = e.getPlayer().getUniqueId();
        final long desde = pl.almacen().visto(u);
        final long ahora = System.currentTimeMillis();
        pl.almacen().guardarVisto(u, ahora);          // si el server se cae, la proxima vez no repite lo que ya vio
        if (desde == 0L || ahora - desde < MINIMO_MS) return;
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            Player p = Bukkit.getPlayer(u);
            if (p == null) return;
            Map<Long, Tienda> mias = new HashMap<>();
            for (Tienda t : pl.almacen().todas()) if (t.dueno.equals(u) && t.avisos) mias.put(t.id, t);
            if (mias.isEmpty()) return;
            pl.almacen().movimientos(mias.keySet(), desde, lista -> mostrar(u, mias, lista, ahora - desde));
        }, 80L);
    }

    private void mostrar(UUID u, Map<Long, Tienda> mias, List<Almacen.Movimiento> lista, long afuera) {
        Player p = Bukkit.getPlayer(u);
        if (p == null || lista.isEmpty()) return;
        long cobrado = 0, gastado = 0;
        int itemsVendidos = 0, itemsComprados = 0, opsVendidas = 0, opsCompradas = 0;
        Map<Long, long[]> porTienda = new HashMap<>();          // id -> {cantidad movida, plata movida}
        for (Almacen.Movimiento m : lista) {
            boolean compraron = "COMPRA".equals(m.tipo());      // un jugador le compró a la tienda: el dueño cobra
            if (compraron) { cobrado += m.total(); itemsVendidos += m.cantidad(); opsVendidas++; }
            else { gastado += m.total(); itemsComprados += m.cantidad(); opsCompradas++; }
            long[] a = porTienda.computeIfAbsent(m.tienda(), k -> new long[2]);
            a[0] += m.cantidad(); a[1] += m.total();
        }
        p.sendMessage(Component.text("Mientras no estabas (" + hace(afuera) + ") en tus tiendas:", NamedTextColor.RED, TextDecoration.BOLD));
        if (opsVendidas > 0) p.sendMessage(linea("Te compraron " + Msg.numero(itemsVendidos) + " ítems en " + Msg.numero(opsVendidas) + (opsVendidas == 1 ? " operación" : " operaciones") + ": cobraste " + Msg.dinero(cobrado) + ".", NamedTextColor.GREEN));
        if (opsCompradas > 0) p.sendMessage(linea("Te vendieron " + Msg.numero(itemsComprados) + " ítems en " + Msg.numero(opsCompradas) + (opsCompradas == 1 ? " operación" : " operaciones") + ": gastaste " + Msg.dinero(gastado) + ".", NamedTextColor.YELLOW));
        List<Map.Entry<Long, long[]>> orden = new ArrayList<>(porTienda.entrySet());
        orden.sort((x, y) -> Long.compare(y.getValue()[1], x.getValue()[1]));
        StringBuilder top = new StringBuilder();
        for (int i = 0; i < Math.min(3, orden.size()); i++) {
            Tienda t = mias.get(orden.get(i).getKey());
            if (t == null) continue;
            top.append(top.length() == 0 ? "" : ", ").append(VentanaTienda.nombreItem(t)).append(" (").append(Msg.numero(orden.get(i).getValue()[0])).append(")");
        }
        if (top.length() > 0) p.sendMessage(linea("Lo que más se movió: " + top + ".", NamedTextColor.GRAY));
        p.sendMessage(linea("Mirá el detalle en los ajustes de cada tienda > historial.", NamedTextColor.DARK_GRAY));
    }

    private static Component linea(String texto, NamedTextColor color) { return Component.text("• " + texto, color).decoration(TextDecoration.ITALIC, false); }

    private static String hace(long ms) {
        long min = ms / 60_000L;
        if (min < 60) return "hace " + Math.max(1, min) + " min";
        long h = min / 60;
        if (h < 48) return "hace " + h + " h" + (min % 60 >= 1 ? " " + (min % 60) + " min" : "");
        return "hace " + (h / 24) + " días";
    }
}
