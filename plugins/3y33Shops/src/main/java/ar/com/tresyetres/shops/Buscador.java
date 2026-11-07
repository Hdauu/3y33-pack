package ar.com.tresyetres.shops;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /tienda buscar [item]: lista las tierras con tiendas (o con ese item) y, al hacer clic, te lleva al spawn de la tierra. */
final class Buscador implements Listener {
    private static final int POR_PAGINA = 45;
    private static final int S_ANTERIOR = 45, S_VOLVER = 48, S_INFO = 49, S_SIGUIENTE = 53;

    record Grupo(String tierra, List<Tienda> tiendas, UUID dueno) { }

    private static final class Titular implements InventoryHolder {
        final String filtro;
        final int pagina;
        final List<Grupo> grupos;
        Titular(String filtro, int pagina, List<Grupo> grupos) { this.filtro = filtro; this.pagina = pagina; this.grupos = grupos; }
        @Override public Inventory getInventory() { return null; }
    }

    private final ShopsPlugin pl;
    private final Map<UUID, Long> ultimoViaje = new HashMap<>();
    private final Map<UUID, Long> ultimoClic = new HashMap<>();

    Buscador(ShopsPlugin pl) { this.pl = pl; }

    // ---------- armar la lista ----------

    private static String norm(String s) { return s.toLowerCase(Locale.ROOT).replace('_', ' ').trim(); }

    private static boolean coincide(Tienda t, String filtro) {
        if (filtro == null || filtro.isBlank()) return true;
        String f = norm(filtro);
        if (norm(t.item.getType().name()).contains(f)) return true;
        ItemMeta im = t.item.getItemMeta();
        if (im != null && im.hasDisplayName() && im.displayName() != null) {
            String nombre = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(im.displayName());
            return norm(nombre).contains(f);
        }
        return false;
    }

    private List<Grupo> armar(String filtro) {
        Map<String, List<Tienda>> por = new LinkedHashMap<>();
        for (Tienda t : pl.almacen().todas()) {
            if (t.tierra.isBlank() || !coincide(t, filtro)) continue;
            if (!(t.puedeComprar() || t.puedeVender()) || pl.integridad().motivo(t) != null) continue;   // solo tiendas en regla
            por.computeIfAbsent(t.tierra.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(t);
        }
        List<Grupo> out = new ArrayList<>();
        for (List<Tienda> lista : por.values()) {
            Tienda primera = lista.get(0);
            UUID dueno = primera.dueno;
            if (primera.mundoObj() != null) {
                LandsPuente.Terreno ter = pl.lands().en(primera.ubicacion(), null);
                if (ter != null && ter.dueno() != null) dueno = ter.dueno();
            }
            out.add(new Grupo(primera.tierra, lista, dueno));
        }
        boolean hayFiltro = filtro != null && !filtro.isBlank();
        if (hayFiltro) out.sort(Comparator.comparingLong(Buscador::precioMasBajo).thenComparing(g -> g.tierra().toLowerCase(Locale.ROOT)));
        else out.sort(Comparator.comparingInt((Grupo g) -> -g.tiendas().size()).thenComparing(g -> g.tierra().toLowerCase(Locale.ROOT)));
        return out;
    }

    private static long precioMasBajo(Grupo g) {
        long min = Long.MAX_VALUE;
        for (Tienda t : g.tiendas()) if (t.puedeComprar()) min = Math.min(min, t.compra);
        return min;
    }

    /** Stock sin cargar chunks: -1 si no se puede saber ahora. */
    private static int stockSeguro(Tienda t) {
        World w = t.mundoObj();
        if (w == null || !w.isChunkLoaded(t.x >> 4, t.z >> 4)) return -1;
        Inventory inv = Operaciones.inventario(t);
        return inv == null ? -1 : Operaciones.contar(inv, t.item);
    }

    // ---------- ventana ----------

    void abrir(Player p, String filtro, int pagina) {
        List<Grupo> grupos = armar(filtro);
        boolean hayFiltro = filtro != null && !filtro.isBlank();
        if (grupos.isEmpty()) {
            Msg.info(p, hayFiltro ? "No encontré tiendas de \"" + filtro + "\"." : "Todavía no hay tiendas en el servidor.");
            return;
        }
        int paginas = (grupos.size() + POR_PAGINA - 1) / POR_PAGINA;
        pagina = Math.max(0, Math.min(pagina, paginas - 1));
        String titulo = hayFiltro ? "Buscar: " + filtro : "Tiendas del servidor";
        if (titulo.length() > 30) titulo = titulo.substring(0, 30);
        Inventory inv = Bukkit.createInventory(new Titular(filtro == null ? "" : filtro, pagina, grupos), 54, Component.text(titulo, NamedTextColor.DARK_RED, TextDecoration.BOLD));
        ItemStack relleno = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 45; i < 54; i++) inv.setItem(i, relleno);
        int desde = pagina * POR_PAGINA;
        for (int i = 0; i < POR_PAGINA && desde + i < grupos.size(); i++) inv.setItem(i, tarjeta(grupos.get(desde + i), hayFiltro ? filtro : null));
        inv.setItem(S_INFO, item(Material.BOOK, n("Página " + (pagina + 1) + " de " + paginas, NamedTextColor.GOLD), List.of(
                l(Msg.numero(grupos.size()) + " tierras con tiendas.", NamedTextColor.GRAY),
                l("Clic en una tierra para ir hasta ahí.", NamedTextColor.GRAY),
                l("Tip: /tienda buscar <ítem>", NamedTextColor.DARK_GRAY))));
        inv.setItem(S_VOLVER, pl.banner().modelo(p, item(Material.ARROW, n("Volver", NamedTextColor.RED), List.of(l("Volver al menú de tu tienda.", NamedTextColor.GRAY))), "flecha_volver"));
        if (pagina > 0) inv.setItem(S_ANTERIOR, pl.banner().modelo(p, item(Material.ARROW, n("Anterior", NamedTextColor.RED), List.of()), "flecha_izq"));
        if (pagina < paginas - 1) inv.setItem(S_SIGUIENTE, pl.banner().modelo(p, item(Material.ARROW, n("Siguiente", NamedTextColor.RED), List.of()), "flecha_der"));
        p.openInventory(inv);
    }

    private static Component n(String texto, NamedTextColor color) { return Component.text(texto, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false); }
    private static Component l(String texto, NamedTextColor color) { return Component.text(texto, color).decoration(TextDecoration.ITALIC, false); }

    private static ItemStack item(Material m, Component nombre, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.displayName(nombre);
        if (!lore.isEmpty()) im.lore(lore);
        it.setItemMeta(im);
        return it;
    }

    private ItemStack tarjeta(Grupo g, String filtro) {
        Almacen.Perfil perfil = pl.almacen().perfil(g.tierra());
        String titulo = perfil.titulo().isBlank() ? g.tierra() : perfil.titulo();
        OfflinePlayer dueno = g.dueno() == null ? null : Bukkit.getOfflinePlayer(g.dueno());
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) it.getItemMeta();
        pl.cabezas().aplicar(sm, g.dueno());
        sm.displayName(n(titulo, NamedTextColor.RED));
        List<Component> lore = new ArrayList<>();
        if (!perfil.titulo().isBlank()) lore.add(l("Tierra: ", NamedTextColor.GRAY).append(Component.text(g.tierra(), NamedTextColor.WHITE)));
        if (dueno != null && dueno.getName() != null) lore.add(l("Dueño: ", NamedTextColor.GRAY).append(Component.text(dueno.getName(), NamedTextColor.WHITE)));
        lore.add(l("Tiendas: ", NamedTextColor.GRAY).append(Component.text(Msg.numero(g.tiendas().size()), NamedTextColor.WHITE)));
        for (String linea : partir(perfil.descripcion(), 34, 3)) lore.add(l(linea, NamedTextColor.DARK_GRAY));
        if (filtro != null) {
            lore.add(Component.empty());
            int mostradas = 0;
            for (Tienda t : g.tiendas()) {
                if (mostradas >= 4) { lore.add(l("... y " + (g.tiendas().size() - 4) + " más", NamedTextColor.DARK_GRAY)); break; }
                Component linea = l("• ", NamedTextColor.DARK_GRAY).append(t.item.effectiveName().color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
                if (t.puedeComprar()) linea = linea.append(l("  Comprar " + Msg.dinero(t.compra), NamedTextColor.GREEN));
                if (t.puedeVender()) linea = linea.append(l("  Vender " + Msg.dinero(t.venta), NamedTextColor.RED));
                int stock = stockSeguro(t);
                if (stock == 0) linea = linea.append(l("  (vacía)", NamedTextColor.RED));
                else if (stock > 0) linea = linea.append(l("  (stock " + Msg.numero(stock) + ")", NamedTextColor.GRAY));
                lore.add(linea);
                mostradas++;
            }
        }
        lore.add(Component.empty());
        lore.add(l("Clic: ir a la tierra.", NamedTextColor.YELLOW));
        sm.lore(lore);
        sm.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES, org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(sm);
        return it;
    }

    /** Parte un texto en lineas de hasta {@code ancho} letras (maximo {@code maxLineas}). */
    static List<String> partir(String texto, int ancho, int maxLineas) {
        List<String> out = new ArrayList<>();
        if (texto == null || texto.isBlank()) return out;
        StringBuilder linea = new StringBuilder();
        for (String palabra : texto.trim().split("\\s+")) {
            if (linea.length() > 0 && linea.length() + 1 + palabra.length() > ancho) {
                out.add(linea.toString());
                linea.setLength(0);
                if (out.size() >= maxLineas) { out.set(maxLineas - 1, out.get(maxLineas - 1) + "..."); return out; }
            }
            if (linea.length() > 0) linea.append(' ');
            linea.append(palabra);
        }
        if (linea.length() > 0) out.add(linea.toString());
        return out;
    }

    // ---------- clics ----------

    @EventHandler(priority = EventPriority.HIGH)
    public void alClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Titular h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != top) return;
        long ahora = System.currentTimeMillis();
        if (ahora - ultimoClic.getOrDefault(p.getUniqueId(), 0L) < pl.getConfig().getLong("pausa-clics-ms", 150)) return;
        ultimoClic.put(p.getUniqueId(), ahora);
        int slot = e.getSlot();
        if (slot == S_VOLVER) {
            p.closeInventory();
            Bukkit.getScheduler().runTask(pl, () -> { if (p.isOnline()) p.performCommand("mitienda"); });
            return;
        }
        if (slot == S_ANTERIOR && h.pagina > 0) { abrir(p, h.filtro, h.pagina - 1); return; }
        if (slot == S_SIGUIENTE) { abrir(p, h.filtro, h.pagina + 1); return; }
        if (slot >= POR_PAGINA) return;
        int idx = h.pagina * POR_PAGINA + slot;
        if (idx >= h.grupos.size()) return;
        viajar(p, h.grupos.get(idx), h.filtro);
    }

    private void viajar(Player p, Grupo g, String filtro) {
        long espera = pl.getConfig().getLong("buscar-espera-segundos", 10) * 1000L;
        long restante = ultimoViaje.getOrDefault(p.getUniqueId(), 0L) + espera - System.currentTimeMillis();
        if (restante > 0 && !p.hasPermission("tresyetres.staff")) { Msg.error(p, "Esperá " + ((restante + 999) / 1000) + " segundos para volver a viajar."); return; }
        // la tienda a la que vamos: con un ítem buscado, la mas barata de comprar; si no, la primera que siga existiendo
        Tienda t = null;
        boolean hayFiltro = filtro != null && !filtro.isBlank();
        for (Tienda x : g.tiendas()) {
            if (pl.almacen().porId(x.id) == null) continue;
            if (t == null) t = x;
            else if (hayFiltro && x.puedeComprar() && (!t.puedeComprar() || x.compra < t.compra)) t = x;
        }
        if (t == null || t.mundoObj() == null) { Msg.error(p, "Esa tierra ya no tiene tiendas."); p.closeInventory(); return; }
        // 1) punto de la tierra, 2) spawn de Lands, 3) al lado de la tienda
        if (pl.integridad().motivo(t) != null) { Msg.error(p, "Esa tienda ya no está disponible."); p.closeInventory(); return; }
        Location destino = pl.puntos().ubicacion(Puntos.claveTierra(g.tierra()));
        if (destino != null && !pl.integridad().puntoValido(destino, g.tierra())) destino = null;
        if (destino == null) destino = pl.lands().spawn(t.ubicacion());
        final boolean conPunto = destino != null;
        if (destino == null) destino = cercaDe(t);
        final long costo = p.hasPermission("tresyetres.staff") ? 0 : (long) (pl.getConfig().getDouble("buscar-costo", 0) * 100);
        if (costo > 0) {
            if (!pl.economia().tiene(p, costo)) { Msg.error(p, "Viajar cuesta " + Msg.dinero(costo) + " y no te alcanza."); return; }
            if (!pl.economia().retirar(p, costo)) { Msg.error(p, "No pude cobrarte el viaje."); return; }
        }
        ultimoViaje.put(p.getUniqueId(), System.currentTimeMillis());
        final String nombre = g.tierra();
        p.closeInventory();
        p.teleportAsync(destino).thenAccept(ok -> Bukkit.getScheduler().runTask(pl, () -> {
            if (!p.isOnline()) return;
            if (ok) {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.2f);
                if (costo > 0) Msg.info(p, "El viaje costó " + Msg.dinero(costo) + ".");
                Msg.ok(p, conPunto ? "Te llevé a la tierra " + nombre + "." : "La tierra " + nombre + " no tiene punto de viaje ni spawn: te dejé al lado de una de sus tiendas.");
            } else {
                if (costo > 0) pl.economia().depositar(p, costo);
                Msg.error(p, "No pude teletransportarte.");
            }
        }));
    }

    /** Un lugar parado al lado de la tienda (cuando la tierra no tiene spawn). */
    private static Location cercaDe(Tienda t) {
        World w = t.mundoObj();
        int[][] lados = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
        for (int[] d : lados) {
            for (int dy = -1; dy <= 2; dy++) {
                Block pies = w.getBlockAt(t.x + d[0], t.y + dy, t.z + d[1]);
                Block cabeza = pies.getRelative(0, 1, 0);
                Block suelo = pies.getRelative(0, -1, 0);
                if (pies.isPassable() && cabeza.isPassable() && suelo.getType().isSolid()) return pies.getLocation().add(0.5, 0, 0.5);
            }
        }
        return new Location(w, t.x + 0.5, t.y + 1, t.z + 0.5);
    }

    @EventHandler
    public void alArrastrar(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Titular) e.setCancelled(true);
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent e) {
        ultimoViaje.remove(e.getPlayer().getUniqueId());
        ultimoClic.remove(e.getPlayer().getUniqueId());
    }

    /** Nombres de ítems con tienda, para completar con Tab. */
    List<String> itemsConTienda() {
        java.util.Set<String> set = new java.util.TreeSet<>();
        for (Tienda t : pl.almacen().todas()) set.add(t.item.getType().name().toLowerCase(Locale.ROOT));
        return new ArrayList<>(set);
    }
}
