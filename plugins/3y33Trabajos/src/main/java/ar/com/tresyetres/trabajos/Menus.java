package ar.com.tresyetres.trabajos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Ventanas de /trabajos: principal, un trabajo, sus acciones y la confirmacion de abandonar. */
final class Menus implements Listener {
    private static final TextColor ROJO = NamedTextColor.DARK_RED;
    private static final TextColor GRIS = NamedTextColor.GRAY;
    private static final TextColor BLANCO = NamedTextColor.WHITE;

    private final TrabajosPlugin pl;

    Menus(TrabajosPlugin pl) { this.pl = pl; }

    @EventHandler
    public void alClic(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Ventana v)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        v.clic(e);
    }

    @EventHandler
    public void alArrastrar(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Ventana) e.setCancelled(true);
    }

    private static Component titulo(String s) { return Component.text(s, ROJO, TextDecoration.BOLD); }

    private static Component linea(String s, TextColor c) { return Iconos.txt(s, c); }

    private void sonido(Player p) { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.3f); }

    // ---------- principal ----------

    void principal(Player p) {
        Perfil f = pl.perfiles().de(p);
        Ventana v = new Ventana(4, titulo("Trabajos"));
        SkullMeta sm;
        ItemStack cabeza = new ItemStack(Material.PLAYER_HEAD);
        sm = (SkullMeta) cabeza.getItemMeta();
        sm.setOwningPlayer(p);
        sm.displayName(Iconos.txtNegrita(p.getName(), BLANCO));
        List<Component> lc = new ArrayList<>();
        lc.add(linea(f.activo == null ? "Sin trabajo" : "Trabajo activo: " + f.activo.nombre, f.activo == null ? NamedTextColor.RED : f.activo.color));
        long ahora = System.currentTimeMillis();
        if (f.activo == null && ahora < f.libreHasta) lc.add(linea("Podés tomar otro en " + Msg.tiempo(f.libreHasta - ahora), NamedTextColor.RED));
        lc.add(Component.empty());
        lc.add(linea("Clic derecho en un trabajo para tomarlo.", GRIS));
        lc.add(linea("Clic izquierdo para ver sus detalles.", GRIS));
        sm.lore(lc);
        cabeza.setItemMeta(sm);
        v.poner(4, cabeza);

        int[] slots = {11, 12, 13, 14, 15};
        Trabajo[] ts = Trabajo.values();
        for (int i = 0; i < ts.length; i++) {
            Trabajo t = ts[i];
            v.poner(slots[i], cartaDe(p, f, t), e -> {
                if (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT) {
                    if (pl.servicio().unirse(p, t)) principal(p);
                } else {
                    sonido(p);
                    trabajo(p, t);
                }
            });
        }
        v.poner(31, Iconos.item(Material.CLOCK, Iconos.txtNegrita("Rarezas de hoy", NamedTextColor.GOLD), List.of(
                linea("Cada acción tiene una rareza que cambia", GRIS),
                linea("todos los días y suma al multiplicador.", GRIS),
                Component.empty(),
                linea("Se renuevan en " + Msg.tiempo(pl.rarezas().msRestantes()), NamedTextColor.YELLOW))));
        v.abrir(p);
    }

    private ItemStack cartaDe(Player p, Perfil f, Trabajo t) {
        Perfil.Progreso g = f.de(t);
        boolean activo = f.activo == t;
        Component nombre = Iconos.txtNegrita(t.nombre + (activo ? "  (activo)" : ""), t.color);
        List<Component> lore = new ArrayList<>();
        lore.add(linea(t.descripcion, GRIS));
        lore.add(Component.empty());
        lore.add(barraNivel(t, g));
        lore.add(linea("Multiplicador personal: x" + Msg.num(pl.pago().personal(g), 2), NamedTextColor.YELLOW));
        if (g.prestigio > 0) lore.add(linea("Prestigio " + g.prestigio, NamedTextColor.GOLD));
        lore.add(Component.empty());
        long ahora = System.currentTimeMillis();
        if (activo) lore.add(linea("Es tu trabajo ahora.", NamedTextColor.GREEN));
        else if (f.activo != null) lore.add(linea("Ya tenés otro trabajo.", NamedTextColor.RED));
        else if (ahora < f.libreHasta) lore.add(linea("Disponible en " + Msg.tiempo(f.libreHasta - ahora), NamedTextColor.RED));
        else lore.add(linea("Clic derecho: tomar este trabajo", NamedTextColor.GREEN));
        lore.add(linea("Clic izquierdo: ver detalles", GRIS));
        return pl.iconos().carta(p, t, g.prestigio, activo, nombre, lore);
    }

    private Component barraNivel(Trabajo t, Perfil.Progreso g) {
        if (g.nivel >= Perfil.NIVEL_MAX) return linea("Nivel 100 (máximo)", NamedTextColor.GOLD);
        double frac = g.xp / Pago.xpNecesaria(g.nivel);
        int lleno = (int) Math.round(frac * 10);
        Component barra = Iconos.txt("Nivel " + g.nivel + "  ", BLANCO);
        for (int i = 0; i < 10; i++) barra = barra.append(Iconos.txt("■", i < lleno ? t.color : NamedTextColor.DARK_GRAY));
        return barra.append(Iconos.txt(" " + Msg.num(frac * 100, 0) + "%", GRIS));
    }

    // ---------- un trabajo ----------

    void trabajo(Player p, Trabajo t) {
        Perfil f = pl.perfiles().de(p);
        Perfil.Progreso g = f.de(t);
        boolean activo = f.activo == t;
        Ventana v = new Ventana(4, Component.text(t.nombre, ROJO, TextDecoration.BOLD));
        v.poner(13, cartaDe(p, f, t));

        v.poner(20, Iconos.item(Material.BOOK, Iconos.txtNegrita("Acciones del trabajo", NamedTextColor.GREEN), List.of(
                linea("Todo lo que paga en " + t.nombre + ", con su rareza de hoy.", GRIS),
                Component.empty(),
                linea("Acciones: " + pl.catalogo().de(t).size(), NamedTextColor.YELLOW),
                linea("Rarezas nuevas en " + Msg.tiempo(pl.rarezas().msRestantes()), NamedTextColor.YELLOW),
                Component.empty(),
                linea("Clic para ver", NamedTextColor.GREEN))), e -> { sonido(p); acciones(p, t, 0); });

        v.poner(22, Iconos.item(Material.WRITABLE_BOOK, Iconos.txtNegrita("Misiones", NamedTextColor.GOLD), List.of(
                linea("Diarias, semanales y globales de " + t.nombre + ".", GRIS),
                Component.empty(),
                linea("Muy pronto.", NamedTextColor.RED))));

        v.poner(24, Iconos.item(Material.CHEST, Iconos.txtNegrita("Recompensas", NamedTextColor.LIGHT_PURPLE), List.of(
                linea("Premios por nivel, hasta el 100.", GRIS),
                Component.empty(),
                linea("Muy pronto.", NamedTextColor.RED))));

        v.poner(29, Iconos.item(Material.ARROW, Iconos.txtNegrita("Volver", GRIS), null), e -> { sonido(p); principal(p); });

        if (activo) {
            v.poner(33, Iconos.item(Material.RED_DYE, Iconos.txtNegrita("Abandonar el trabajo", NamedTextColor.RED), List.of(
                    linea("Te quedás sin trabajo " + pl.getConfig().getInt("abandonar-horas", 13) + " horas.", GRIS),
                    linea("Las misiones sin cobrar se pierden.", GRIS),
                    Component.empty(),
                    linea("Clic para abandonar", NamedTextColor.RED))), e -> { sonido(p); confirmarAbandono(p, t); });
        } else {
            v.poner(33, Iconos.item(Material.LIME_DYE, Iconos.txtNegrita("Tomar este trabajo", NamedTextColor.GREEN), List.of(
                    linea("Nivel " + g.nivel + " · x" + Msg.num(pl.pago().personal(g), 2), GRIS),
                    Component.empty(),
                    linea("Clic para tomarlo", NamedTextColor.GREEN))), e -> {
                if (pl.servicio().unirse(p, t)) trabajo(p, t);
            });
        }
        v.abrir(p);
    }

    // ---------- acciones ----------

    private static final int[] SLOTS_ACCIONES = slotsAcciones();

    private static int[] slotsAcciones() {
        int[] s = new int[28];
        int k = 0;
        for (int fila = 1; fila <= 4; fila++) for (int col = 1; col <= 7; col++) s[k++] = fila * 9 + col;
        return s;
    }

    void acciones(Player p, Trabajo t, int pagina) {
        Perfil f = pl.perfiles().de(p);
        Perfil.Progreso g = f.de(t);
        double personal = pl.pago().personal(g);
        List<Accion> todas = new ArrayList<>(pl.catalogo().de(t));
        todas.sort(Comparator.comparingDouble((Accion a) -> a.pago() * (personal + pl.rarezas().de(a).bono())).reversed());
        int porPagina = SLOTS_ACCIONES.length;
        int paginas = Math.max(1, (todas.size() + porPagina - 1) / porPagina);
        int pag = Math.max(0, Math.min(pagina, paginas - 1));
        Ventana v = new Ventana(6, Component.text("Acciones · " + t.nombre, ROJO, TextDecoration.BOLD));
        for (int i = 0; i < porPagina; i++) {
            int idx = pag * porPagina + i;
            if (idx >= todas.size()) break;
            v.poner(SLOTS_ACCIONES[i], itemAccion(t, todas.get(idx), personal));
        }
        v.poner(45, Iconos.item(Material.ARROW, Iconos.txtNegrita("Volver", GRIS), null), e -> { sonido(p); trabajo(p, t); });
        if (pag > 0) v.poner(48, Iconos.item(Material.SPECTRAL_ARROW, Iconos.txtNegrita("Página anterior", NamedTextColor.YELLOW), null), e -> { sonido(p); acciones(p, t, pag - 1); });
        v.poner(49, Iconos.item(Material.CLOCK, Iconos.txtNegrita("Rarezas de hoy", NamedTextColor.GOLD), List.of(
                linea("Se renuevan en " + Msg.tiempo(pl.rarezas().msRestantes()), NamedTextColor.YELLOW),
                linea("Página " + (pag + 1) + " de " + paginas, GRIS))));
        if (pag < paginas - 1) v.poner(50, Iconos.item(Material.SPECTRAL_ARROW, Iconos.txtNegrita("Página siguiente", NamedTextColor.YELLOW), null), e -> { sonido(p); acciones(p, t, pag + 1); });
        v.abrir(p);
    }

    private ItemStack itemAccion(Trabajo t, Accion a, double personal) {
        Rareza r = pl.rarezas().de(a);
        double factor = personal + r.bono();
        List<Component> lore = new ArrayList<>();
        lore.add(linea("Rareza: " + r.nombre + " (x" + Msg.num(r.mult, 1).replace(",0", "") + ")", r.color));
        lore.add(Component.empty());
        lore.add(linea("Pago base: " + Msg.dinero(a.pago()), GRIS));
        lore.add(linea("Tu pago ahora: " + Msg.dinero(a.pago() * factor), NamedTextColor.GREEN));
        lore.add(linea("XP: " + Msg.num(a.xp() * (1 + r.bono()), 2), NamedTextColor.AQUA));
        lore.add(linea("Multiplicador: x" + Msg.num(factor, 2) + " (personal + rareza)", GRIS));
        ItemStack it = Iconos.item(a.icono(), a.nombre().color(r.color).decoration(TextDecoration.ITALIC, false), lore);
        return it;
    }

    // ---------- confirmar ----------

    void confirmarAbandono(Player p, Trabajo t) {
        long horas = pl.getConfig().getLong("abandonar-horas", 13);
        Ventana v = new Ventana(3, Component.text("¿Abandonar " + t.nombre + "?", ROJO, TextDecoration.BOLD));
        v.poner(13, Iconos.item(Material.PAPER, Iconos.txtNegrita("¿Seguro?", NamedTextColor.RED), List.of(
                linea("Te quedás " + horas + " horas sin trabajo:", GRIS),
                linea("no cobrás por acciones ni por tiempo", GRIS),
                linea("y no podés tomar otro hasta que pasen.", GRIS),
                Component.empty(),
                linea("Las misiones sin cobrar se pierden.", NamedTextColor.RED),
                linea("Tu nivel y tu progreso se guardan.", NamedTextColor.GREEN))));
        v.poner(11, Iconos.item(Material.LIME_CONCRETE, Iconos.txtNegrita("Sí, abandonar", NamedTextColor.GREEN), null), e -> {
            pl.servicio().abandonar(p);
            p.closeInventory();
            Bukkit.getScheduler().runTask(pl, () -> principal(p));
        });
        v.poner(15, Iconos.item(Material.RED_CONCRETE, Iconos.txtNegrita("No, quedarme", NamedTextColor.RED), null), e -> { sonido(p); trabajo(p, t); });
        v.abrir(p);
    }
}
