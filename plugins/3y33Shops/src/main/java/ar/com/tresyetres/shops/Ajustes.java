package ar.com.tresyetres.shops;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

/** Ajustes de la tienda: bloqueos, precios, modo de ingresos, administradores, historial, umbral, rotacion y mensaje del holograma. */
final class Ajustes implements Listener {
    static final List<String> ROTACIONES = List.of("ARRIBA", "NORTE", "ESTE", "SUR", "OESTE", "ABAJO");
    static final int MAX_MENSAJE = 100, MAX_ADMINS = 10;

    private enum Pantalla { AJUSTES, ADMINS, HISTORIAL }

    private static final class Titular implements InventoryHolder {
        final long tienda;
        final Pantalla pantalla;
        Titular(long tienda, Pantalla pantalla) { this.tienda = tienda; this.pantalla = pantalla; }
        @Override public Inventory getInventory() { return null; }
    }

    private record Copia(boolean bloqCompra, boolean bloqVenta, String modo, int bajoStock, String rotacion, String mensaje, Set<UUID> admins) { }

    // slots de la pantalla de ajustes
    static final int A_BLOQ_COMPRA = 10, A_BLOQ_VENTA = 11, A_PRECIO_COMPRA = 12, A_PRECIO_VENTA = 13, A_MODO = 14, A_ADMINS = 15, A_HISTORIAL = 16;
    static final int A_COPIAR = 19, A_PEGAR = 20, A_UMBRAL = 21, A_ROTACION = 22, A_MENSAJE = 23, A_VOLVER = 31;

    private final ShopsPlugin pl;
    private final Map<UUID, Copia> portapapeles = new HashMap<>();
    private final Map<UUID, Long> ultimoClic = new HashMap<>();

    Ajustes(ShopsPlugin pl) { this.pl = pl; }

    // ---------- helpers de items ----------

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

    private static Inventory ventana(long tienda, Pantalla pantalla, int filas, String titulo) {
        Inventory inv = Bukkit.createInventory(new Titular(tienda, pantalla), filas * 9, Component.text(titulo, NamedTextColor.DARK_RED, TextDecoration.BOLD));
        ItemStack relleno = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, relleno);
        return inv;
    }

    private static boolean esDueno(Player p, Tienda t) { return p.getUniqueId().equals(t.dueno) || p.hasPermission("tresyetres.staff"); }

    private static String nombre(UUID u) {
        String s = Bukkit.getOfflinePlayer(u).getName();
        return s == null ? u.toString().substring(0, 8) : s;
    }

    // ---------- pantalla principal ----------

    void abrir(Player p, Tienda t) {
        if (!t.administra(p)) { Msg.error(p, "Solo el dueño o los administradores pueden ver los ajustes."); return; }
        Inventory inv = ventana(t.id, Pantalla.AJUSTES, 4, "Ajustes de la tienda");
        llenarAjustes(inv, p, t);
        p.openInventory(inv);
    }

    private void llenarAjustes(Inventory inv, Player p, Tienda t) {
        boolean dueno = esDueno(p, t);
        inv.setItem(A_BLOQ_COMPRA, t.compra < 0
                ? item(Material.GRAY_CONCRETE, n("Compras desactivadas", NamedTextColor.GRAY), List.of(l("No hay precio de compra.", NamedTextColor.DARK_GRAY)))
                : t.bloqCompra
                ? item(Material.RED_CONCRETE, n("Compras bloqueadas", NamedTextColor.RED), List.of(l("Nadie puede comprar en esta tienda.", NamedTextColor.GRAY), Component.empty(), l("Clic: desbloquear.", NamedTextColor.YELLOW)))
                : item(Material.LIME_CONCRETE, n("Compras habilitadas", NamedTextColor.GREEN), List.of(l("Los jugadores pueden comprarte.", NamedTextColor.GRAY), Component.empty(), l("Clic: bloquear.", NamedTextColor.YELLOW))));
        inv.setItem(A_BLOQ_VENTA, t.venta < 0
                ? item(Material.GRAY_CONCRETE, n("Ventas desactivadas", NamedTextColor.GRAY), List.of(l("No hay precio de venta.", NamedTextColor.DARK_GRAY)))
                : t.bloqVenta
                ? item(Material.RED_CONCRETE, n("Ventas bloqueadas", NamedTextColor.RED), List.of(l("Nadie puede venderle a esta tienda.", NamedTextColor.GRAY), Component.empty(), l("Clic: desbloquear.", NamedTextColor.YELLOW)))
                : item(Material.LIME_CONCRETE, n("Ventas habilitadas", NamedTextColor.GREEN), List.of(l("Los jugadores pueden venderte.", NamedTextColor.GRAY), Component.empty(), l("Clic: bloquear.", NamedTextColor.YELLOW))));

        inv.setItem(A_PRECIO_COMPRA, item(Material.GOLD_INGOT, n("Precio de compra", NamedTextColor.GOLD), List.of(
                l("Lo que paga el jugador por cada unidad.", NamedTextColor.GRAY),
                l("Actual: ", NamedTextColor.GRAY).append(Component.text(t.compra < 0 ? "desactivado" : Msg.dinero(t.compra), NamedTextColor.WHITE)),
                Component.empty(), l("Clic izquierdo: cambiar.", NamedTextColor.YELLOW), l("Clic derecho: desactivar.", NamedTextColor.YELLOW))));
        inv.setItem(A_PRECIO_VENTA, item(Material.IRON_INGOT, n("Precio de venta", NamedTextColor.GOLD), List.of(
                l("Lo que recibe el jugador por cada unidad.", NamedTextColor.GRAY),
                l("Actual: ", NamedTextColor.GRAY).append(Component.text(t.venta < 0 ? "desactivado" : Msg.dinero(t.venta), NamedTextColor.WHITE)),
                Component.empty(), l("Clic izquierdo: cambiar.", NamedTextColor.YELLOW), l("Clic derecho: desactivar.", NamedTextColor.YELLOW))));

        boolean comp = "COMPARTIDO".equals(t.modoIngreso);
        List<Component> lm = new ArrayList<>();
        lm.add(l("Quién cobra lo que ganan las ventas.", NamedTextColor.GRAY));
        lm.add(Component.empty());
        lm.add(l((comp ? "  " : "▶ ") + "Dueño", comp ? NamedTextColor.GRAY : NamedTextColor.GREEN));
        lm.add(l((comp ? "▶ " : "  ") + "Compartido entre admins", comp ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        lm.add(l("  Tierra (próximamente)", NamedTextColor.DARK_GRAY));
        lm.add(Component.empty());
        lm.add(dueno ? l("Clic: cambiar modo.", NamedTextColor.YELLOW) : l("Solo el dueño puede cambiarlo.", NamedTextColor.RED));
        inv.setItem(A_MODO, item(Material.EMERALD, n("Modo de ingresos", NamedTextColor.GREEN), lm));

        inv.setItem(A_ADMINS, item(Material.PLAYER_HEAD, n("Administradores", NamedTextColor.AQUA), List.of(
                l("Pueden reponer, retirar y cambiar ajustes.", NamedTextColor.GRAY),
                l("Hay: ", NamedTextColor.GRAY).append(Component.text(String.valueOf(t.admins.size()), NamedTextColor.WHITE)),
                Component.empty(), dueno ? l("Clic: ver y administrar.", NamedTextColor.YELLOW) : l("Solo el dueño puede cambiarlos.", NamedTextColor.RED))));
        inv.setItem(A_HISTORIAL, item(Material.BOOK, n("Últimas transacciones", NamedTextColor.AQUA), List.of(
                l("Las últimas compras y ventas de esta tienda.", NamedTextColor.GRAY), Component.empty(), l("Clic: ver.", NamedTextColor.YELLOW))));

        inv.setItem(A_COPIAR, item(Material.WRITABLE_BOOK, n("Copiar ajustes", NamedTextColor.WHITE), List.of(
                l("Guarda los ajustes de esta tienda", NamedTextColor.GRAY), l("(sin precios) para pegarlos en otra.", NamedTextColor.GRAY), Component.empty(), l("Clic: copiar.", NamedTextColor.YELLOW))));
        boolean hayCopia = portapapeles.containsKey(p.getUniqueId());
        inv.setItem(A_PEGAR, item(Material.PAPER, n("Pegar ajustes", hayCopia ? NamedTextColor.WHITE : NamedTextColor.GRAY), List.of(
                l(hayCopia ? "Aplica los ajustes copiados a esta tienda." : "Primero copiá los ajustes de otra tienda.", NamedTextColor.GRAY), Component.empty(),
                hayCopia ? l("Clic: pegar.", NamedTextColor.YELLOW) : l("No hay nada copiado.", NamedTextColor.DARK_GRAY))));
        inv.setItem(A_UMBRAL, item(Material.HOPPER, n("Umbral de bajo stock", NamedTextColor.GOLD), List.of(
                l("Te avisa cuando quedan pocas unidades.", NamedTextColor.GRAY),
                l("Actual: ", NamedTextColor.GRAY).append(Component.text(Msg.numero(t.bajoStock), NamedTextColor.WHITE)),
                Component.empty(), l("Clic: cambiar.", NamedTextColor.YELLOW))));

        List<Component> lr = new ArrayList<>();
        lr.add(l("Hacia dónde mira el ítem del holograma.", NamedTextColor.GRAY));
        lr.add(Component.empty());
        for (String r : ROTACIONES) {
            boolean act = r.equals(t.rotacion);
            lr.add(l((act ? "▶ " : "  ") + capitalizar(r), act ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        }
        lr.add(Component.empty());
        lr.add(l("Clic izquierdo: siguiente.", NamedTextColor.YELLOW));
        lr.add(l("Clic derecho: anterior.", NamedTextColor.YELLOW));
        inv.setItem(A_ROTACION, item(Material.COMPASS, n("Rotación del holograma", NamedTextColor.AQUA), lr));

        inv.setItem(A_MENSAJE, item(Material.OAK_SIGN, n("Mensaje del holograma", NamedTextColor.AQUA), List.of(
                l("Texto que aparece arriba de la tienda.", NamedTextColor.GRAY),
                l("Actual: ", NamedTextColor.GRAY).append(Component.text(t.mensaje.isBlank() ? "(ninguno)" : t.mensaje, NamedTextColor.WHITE)),
                Component.empty(), l("Clic izquierdo: cambiar (hasta " + MAX_MENSAJE + " letras).", NamedTextColor.YELLOW), l("Clic derecho: borrar.", NamedTextColor.YELLOW))));

        inv.setItem(A_VOLVER, item(Material.ARROW, n("Volver", NamedTextColor.RED), List.of(l("Volver a la tienda.", NamedTextColor.GRAY))));
    }

    private static String capitalizar(String s) { return s.charAt(0) + s.substring(1).toLowerCase(); }

    // ---------- administradores ----------

    private void abrirAdmins(Player p, Tienda t) {
        Inventory inv = ventana(t.id, Pantalla.ADMINS, 3, "Administradores");
        int i = 0;
        for (UUID u : t.admins) {
            if (i >= 18) break;
            ItemStack cabeza = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) cabeza.getItemMeta();
            OfflinePlayer op = Bukkit.getOfflinePlayer(u);
            try { sm.setOwningPlayer(op); } catch (Throwable ignored) { }
            sm.displayName(n(nombre(u), NamedTextColor.WHITE));
            sm.lore(List.of(l("Clic: quitar administrador.", NamedTextColor.YELLOW)));
            cabeza.setItemMeta(sm);
            inv.setItem(i++, cabeza);
        }
        if (t.admins.isEmpty()) inv.setItem(4, item(Material.BARRIER, n("Sin administradores", NamedTextColor.GRAY), List.of(l("Agregá a alguien con el botón de abajo.", NamedTextColor.DARK_GRAY))));
        inv.setItem(22, item(Material.LIME_DYE, n("Agregar administrador", NamedTextColor.GREEN), List.of(
                l("Escribís su nombre en el chat.", NamedTextColor.GRAY), l("Máximo " + MAX_ADMINS + ".", NamedTextColor.GRAY), Component.empty(), l("Clic: agregar.", NamedTextColor.YELLOW))));
        inv.setItem(26, item(Material.ARROW, n("Volver a ajustes", NamedTextColor.RED), List.of()));
        p.openInventory(inv);
    }

    private void agregarAdmin(Player p, Tienda t, String nombreJugador) {
        if (t.admins.size() >= MAX_ADMINS) { Msg.error(p, "Ya hay " + MAX_ADMINS + " administradores."); abrirAdmins(p, t); return; }
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(nombreJugador);
        if (op == null || (!op.hasPlayedBefore() && !op.isOnline())) { Msg.error(p, "No conozco a ningún jugador llamado " + nombreJugador + ". Tiene que haber entrado al server."); abrirAdmins(p, t); return; }
        if (op.getUniqueId().equals(t.dueno)) { Msg.error(p, "Ese es el dueño de la tienda."); abrirAdmins(p, t); return; }
        if (!t.admins.add(op.getUniqueId())) { Msg.error(p, op.getName() + " ya es administrador."); abrirAdmins(p, t); return; }
        pl.almacen().guardar(t);
        Msg.ok(p, op.getName() + " ahora es administrador de la tienda.");
        abrirAdmins(p, t);
    }

    // ---------- historial ----------

    private static final SimpleDateFormat FECHA = new SimpleDateFormat("dd/MM HH:mm");
    static { FECHA.setTimeZone(TimeZone.getTimeZone("America/Argentina/Buenos_Aires")); }

    private void abrirHistorial(Player p, Tienda t) {
        pl.almacen().ultimas(t.id, 21, lista -> {
            if (!p.isOnline()) return;
            Inventory inv = ventana(t.id, Pantalla.HISTORIAL, 4, "Últimas transacciones");
            if (lista.isEmpty()) inv.setItem(13, item(Material.BARRIER, n("Todavía no hubo operaciones", NamedTextColor.GRAY), List.of()));
            int i = 0;
            for (Almacen.Registro r : lista) {
                boolean compro = r.tipo().equals("COMPRA");
                String fecha;
                synchronized (FECHA) { fecha = FECHA.format(new Date(r.cuando())); }
                inv.setItem(i++, item(compro ? Material.LIME_DYE : Material.ORANGE_DYE,
                        n((compro ? "Compró " : "Vendió ") + Msg.numero(r.cantidad()) + "x", compro ? NamedTextColor.GREEN : NamedTextColor.GOLD),
                        List.of(l("Jugador: ", NamedTextColor.GRAY).append(Component.text(nombre(r.jugador()), NamedTextColor.WHITE)),
                                l((compro ? "Pagó: " : "Cobró: "), NamedTextColor.GRAY).append(Component.text(Msg.dinero(r.total()), NamedTextColor.WHITE)),
                                l(fecha, NamedTextColor.DARK_GRAY))));
            }
            inv.setItem(31, item(Material.ARROW, n("Volver a ajustes", NamedTextColor.RED), List.of()));
            p.openInventory(inv);
        });
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

        Tienda t = pl.almacen().porId(h.tienda);
        if (t == null) { p.closeInventory(); Msg.error(p, "Esta tienda ya no existe."); return; }
        if (!t.administra(p)) { p.closeInventory(); return; }
        int slot = e.getSlot();
        switch (h.pantalla) {
            case AJUSTES -> clicAjustes(p, t, slot, e.isRightClick(), top);
            case ADMINS -> {
                if (slot == 26) { abrir(p, t); return; }
                if (!esDueno(p, t)) return;
                if (slot == 22) { pl.entrada().pedir(p, "Escribí en el chat el nombre del nuevo administrador", txt -> agregarAdmin(p, t, txt)); return; }
                ItemStack it = e.getCurrentItem();
                if (slot < 18 && it != null && it.getType() == Material.PLAYER_HEAD) {
                    List<UUID> orden = new ArrayList<>(t.admins);
                    if (slot < orden.size()) {
                        UUID quitar = orden.get(slot);
                        t.admins.remove(quitar);
                        pl.almacen().guardar(t);
                        Msg.ok(p, nombre(quitar) + " ya no es administrador.");
                        abrirAdmins(p, t);
                    }
                }
            }
            case HISTORIAL -> { if (slot == 31) abrir(p, t); }
        }
    }

    private void clicAjustes(Player p, Tienda t, int slot, boolean derecho, Inventory inv) {
        double maxPesos = pl.getConfig().getDouble("precio-maximo", 1_000_000);
        switch (slot) {
            case A_BLOQ_COMPRA -> { if (t.compra >= 0) { t.bloqCompra = !t.bloqCompra; cambio(p, t, inv); } }
            case A_BLOQ_VENTA -> { if (t.venta >= 0) { t.bloqVenta = !t.bloqVenta; cambio(p, t, inv); } }
            case A_PRECIO_COMPRA, A_PRECIO_VENTA -> {
                final boolean esCompra = slot == A_PRECIO_COMPRA;
                if (derecho) {
                    if ((esCompra ? t.venta : t.compra) < 0) { Msg.error(p, "Al menos una de las dos operaciones tiene que estar activa."); return; }
                    if (esCompra) t.compra = -1; else t.venta = -1;
                    cambio(p, t, inv);
                    return;
                }
                pl.entrada().pedir(p, "Escribí el nuevo precio de " + (esCompra ? "compra" : "venta") + " por unidad (ej: 10 o 2,5)", txt -> {
                    long c = Msg.precio(txt, maxPesos);
                    if (c == -2 || c == -1) { Msg.error(p, "Tiene que ser un número (hasta 2 decimales)."); abrir(p, t); return; }
                    if (c == -3) { Msg.error(p, "El precio máximo es " + Msg.dinero((long) (maxPesos * 100)) + "."); abrir(p, t); return; }
                    if (esCompra) t.compra = c; else t.venta = c;
                    pl.almacen().guardar(t);
                    Msg.ok(p, "Precio de " + (esCompra ? "compra" : "venta") + " actualizado a " + Msg.dinero(c) + ".");
                    abrir(p, t);
                });
            }
            case A_MODO -> {
                if (!esDueno(p, t)) { Msg.error(p, "Solo el dueño puede cambiar el modo de ingresos."); return; }
                t.modoIngreso = "COMPARTIDO".equals(t.modoIngreso) ? "DUENO" : "COMPARTIDO";
                cambio(p, t, inv);
            }
            case A_ADMINS -> {
                if (!esDueno(p, t)) { Msg.error(p, "Solo el dueño puede cambiar los administradores."); return; }
                abrirAdmins(p, t);
            }
            case A_HISTORIAL -> abrirHistorial(p, t);
            case A_COPIAR -> {
                portapapeles.put(p.getUniqueId(), new Copia(t.bloqCompra, t.bloqVenta, t.modoIngreso, t.bajoStock, t.rotacion, t.mensaje, new HashSet<>(t.admins)));
                Msg.ok(p, "Copiaste los ajustes. Abrí otra tienda y usá Pegar ajustes.");
                llenarAjustes(inv, p, t);
            }
            case A_PEGAR -> {
                Copia c = portapapeles.get(p.getUniqueId());
                if (c == null) { Msg.error(p, "No copiaste nada todavía."); return; }
                t.bloqCompra = c.bloqCompra(); t.bloqVenta = c.bloqVenta();
                t.bajoStock = c.bajoStock(); t.rotacion = c.rotacion(); t.mensaje = c.mensaje();
                if (esDueno(p, t)) {
                    t.modoIngreso = c.modo();
                    t.admins.clear();
                    for (UUID u : c.admins()) if (!u.equals(t.dueno)) t.admins.add(u);
                }
                cambio(p, t, inv);
                Msg.ok(p, "Pegaste los ajustes en esta tienda.");
            }
            case A_UMBRAL -> pl.entrada().pedir(p, "Escribí el nuevo umbral de bajo stock (número de unidades)", txt -> {
                try {
                    int v = Integer.parseInt(txt.replace(".", ""));
                    if (v < 0 || v > 100000) throw new NumberFormatException();
                    t.bajoStock = v;
                    pl.almacen().guardar(t);
                    Msg.ok(p, "Umbral de bajo stock: " + Msg.numero(v) + ".");
                } catch (NumberFormatException ex) { Msg.error(p, "Tiene que ser un número entre 0 y 100.000."); }
                abrir(p, t);
            });
            case A_ROTACION -> {
                int i = Math.max(0, ROTACIONES.indexOf(t.rotacion));
                i = (i + (derecho ? ROTACIONES.size() - 1 : 1)) % ROTACIONES.size();
                t.rotacion = ROTACIONES.get(i);
                cambio(p, t, inv);
            }
            case A_MENSAJE -> {
                if (derecho) { t.mensaje = ""; cambio(p, t, inv); return; }
                pl.entrada().pedir(p, "Escribí el mensaje del holograma (hasta " + MAX_MENSAJE + " letras)", txt -> {
                    if (txt.length() > MAX_MENSAJE) { Msg.error(p, "Es muy largo: máximo " + MAX_MENSAJE + " letras."); abrir(p, t); return; }
                    t.mensaje = txt;
                    pl.almacen().guardar(t);
                    Msg.ok(p, "Mensaje del holograma actualizado.");
                    abrir(p, t);
                });
            }
            case A_VOLVER -> pl.ventana().abrir(p, t);
            default -> { }
        }
    }

    private void cambio(Player p, Tienda t, Inventory inv) {
        pl.almacen().guardar(t);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.3f);
        llenarAjustes(inv, p, t);
    }

    @EventHandler
    public void alArrastrar(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Titular) e.setCancelled(true);
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent e) {
        portapapeles.remove(e.getPlayer().getUniqueId());
        ultimoClic.remove(e.getPlayer().getUniqueId());
    }
}
