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

    /** Trabajos de cada pagina (indices de Trabajo.values()), columna del casillero de la fila 3 de donde cuelga cada carta y columnas de su zona de clic. */
    private static final int[][] PAGINA = {{0, 1, 2}, {3, 4}};
    private static final int[][] COLUMNA_CARTA = {{1, 4, 7}, {2, 5}};
    private static final int[][][] ZONA = {{{0, 1, 2}, {3, 4, 5}, {6, 7, 8}}, {{1, 2, 3}, {4, 5, 6}}};

    void principal(Player p) { principal(p, 0); }

    void principal(Player p, int pagina) {
        final int pag = ((pagina % PAGINA.length) + PAGINA.length) % PAGINA.length;
        Perfil f = pl.perfiles().de(p);
        boolean pack = pl.iconos().conPack(p);
        Ventana v = new Ventana(6, pl.iconos().titulo(p, Iconos.FONDO_TRABAJOS, "Trabajos"));
        Trabajo[] ts = Trabajo.values();
        for (int k = 0; k < PAGINA[pag].length; k++) {
            Trabajo t = ts[PAGINA[pag][k]];
            ItemStack carta = cartaDe(p, f, t, false);
            java.util.function.Consumer<InventoryClickEvent> alClic = e -> {
                if (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT) {
                    if (pl.servicio().unirse(p, t)) principal(p, pag);
                } else {
                    sonido(p);
                    trabajo(p, t);
                }
            };
            int principal = 3 * 9 + COLUMNA_CARTA[pag][k];
            if (pack) {
                // la carta ocupa 3x4 casilleros: el item grande va en uno y el resto lleva un relleno invisible con el mismo texto
                ItemStack vacio = pl.iconos().relleno(carta);
                for (int fila = 1; fila <= 4; fila++)
                    for (int col : ZONA[pag][k]) {
                        int slot = fila * 9 + col;
                        if (slot != principal) v.poner(slot, vacio, alClic);
                    }
            }
            v.poner(principal, carta, alClic);
        }

        List<Component> lc = new ArrayList<>();
        lc.add(linea(f.activo == null ? "Sin trabajo" : "Trabajo activo: " + f.activo.nombre, f.activo == null ? NamedTextColor.RED : f.activo.color));
        long ahora = System.currentTimeMillis();
        if (f.activo == null && ahora < f.libreHasta) lc.add(linea("Podés tomar otro en " + Msg.tiempo(f.libreHasta - ahora), NamedTextColor.RED));
        lc.add(Component.empty());
        lc.add(linea("Clic derecho en una carta: tomar el trabajo.", GRIS));
        lc.add(linea("Clic izquierdo: ver sus detalles.", GRIS));
        v.poner(48, Iconos.item(Material.PAPER, Iconos.txtNegrita(p.getName(), BLANCO), lc));
        v.poner(50, relojRarezas());
        v.poner(45, pl.iconos().flecha(p, false, Iconos.txtNegrita("Página anterior", NamedTextColor.YELLOW), List.of(linea("Página " + (pag + 1) + " de " + PAGINA.length, GRIS))),
                e -> { sonido(p); principal(p, pag - 1); });
        v.poner(53, pl.iconos().flecha(p, true, Iconos.txtNegrita("Página siguiente", NamedTextColor.YELLOW), List.of(linea("Página " + (pag + 1) + " de " + PAGINA.length, GRIS))),
                e -> { sonido(p); principal(p, pag + 1); });
        v.abrir(p);
    }

    private ItemStack relojRarezas() {
        return Iconos.item(Material.CLOCK, Iconos.txtNegrita("Rarezas de hoy", NamedTextColor.GOLD), List.of(
                linea("Cada acción tiene una rareza que cambia", GRIS),
                linea("todos los días y suma al multiplicador.", GRIS),
                Component.empty(),
                linea("Se renuevan en " + Msg.tiempo(pl.rarezas().msRestantes()), NamedTextColor.YELLOW)));
    }

    private ItemStack cartaDe(Player p, Perfil f, Trabajo t, boolean detalle) {
        Perfil.Progreso g = f.de(t);
        boolean activo = f.activo == t;
        long ahoraMs = System.currentTimeMillis();
        Iconos.Estado estado = activo ? Iconos.Estado.ACTIVO : (f.activo != null || ahoraMs < f.libreHasta) ? Iconos.Estado.BLOQUEADO : Iconos.Estado.NORMAL;
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
        else if (!detalle) lore.add(linea("Clic derecho: tomar este trabajo", NamedTextColor.GREEN));
        if (!detalle) lore.add(linea("Clic izquierdo: ver detalles", GRIS));
        return pl.iconos().carta(p, t, g.prestigio, estado, detalle, nombre, lore);
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
        Ventana v = new Ventana(6, pl.iconos().titulo(p, Iconos.FONDO_TRABAJO, t.nombre));
        ItemStack carta = cartaDe(p, f, t, true);
        if (pl.iconos().conPack(p)) {
            ItemStack vacio = pl.iconos().relleno(carta);
            for (int fila = 1; fila <= 4; fila++)
                for (int col = 3; col <= 5; col++) v.poner(fila * 9 + col, vacio);
        }
        v.poner(3 * 9 + 4, carta);

        v.poner(10, Iconos.item(Material.BOOK, Iconos.txtNegrita("Acciones del trabajo", NamedTextColor.GREEN), List.of(
                linea("Todo lo que paga en " + t.nombre + ", con su rareza de hoy.", GRIS),
                Component.empty(),
                linea("Acciones: " + pl.catalogo().de(t).size(), NamedTextColor.YELLOW),
                linea("Rarezas nuevas en " + Msg.tiempo(pl.rarezas().msRestantes()), NamedTextColor.YELLOW),
                Component.empty(),
                linea("Clic para ver", NamedTextColor.GREEN))), e -> { sonido(p); acciones(p, t, 0); });

        v.poner(19, Iconos.item(Material.WRITABLE_BOOK, Iconos.txtNegrita("Misiones", NamedTextColor.GOLD), List.of(
                linea("Diarias, semanal y global de " + t.nombre + ".", GRIS),
                linea("Entregá items y cobrá mucho más que picando.", GRIS),
                Component.empty(),
                linea("Clic para ver", NamedTextColor.GREEN))), e -> { sonido(p); misiones(p, t); });

        v.poner(28, Iconos.item(Material.CHEST, Iconos.txtNegrita("Recompensas", NamedTextColor.LIGHT_PURPLE), List.of(
                linea("Premios por nivel, hasta el 100.", GRIS),
                Component.empty(),
                linea("Muy pronto.", NamedTextColor.RED))));

        v.poner(16, relojRarezas());
        v.poner(45, pl.iconos().flecha(p, false, Iconos.txtNegrita("Volver", GRIS), null), e -> { sonido(p); principal(p, java.util.Arrays.asList(Trabajo.values()).indexOf(t) < 3 ? 0 : 1); });

        if (activo) {
            v.poner(53, Iconos.item(Material.RED_DYE, Iconos.txtNegrita("Abandonar el trabajo", NamedTextColor.RED), List.of(
                    linea("Te quedás sin trabajo " + pl.getConfig().getInt("abandonar-horas", 13) + " horas.", GRIS),
                    linea("Las misiones sin cobrar se pierden.", GRIS),
                    Component.empty(),
                    linea("Clic para abandonar", NamedTextColor.RED))), e -> { sonido(p); confirmarAbandono(p, t); });
        } else {
            v.poner(53, Iconos.item(Material.LIME_DYE, Iconos.txtNegrita("Tomar este trabajo", NamedTextColor.GREEN), List.of(
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
        Ventana v = new Ventana(6, pl.iconos().titulo(p, Iconos.FONDO_ACCIONES, "Acciones · " + t.nombre));
        for (int i = 0; i < porPagina; i++) {
            int idx = pag * porPagina + i;
            if (idx >= todas.size()) break;
            v.poner(SLOTS_ACCIONES[i], itemAccion(t, todas.get(idx), personal));
        }
        v.poner(45, pl.iconos().flecha(p, false, Iconos.txtNegrita("Volver", GRIS), null), e -> { sonido(p); trabajo(p, t); });
        if (pag > 0) v.poner(48, pl.iconos().flecha(p, false, Iconos.txtNegrita("Página anterior", NamedTextColor.YELLOW), List.of(linea("Página " + pag + " de " + paginas, GRIS))), e -> { sonido(p); acciones(p, t, pag - 1); });
        v.poner(49, Iconos.item(Material.CLOCK, Iconos.txtNegrita("Rarezas de hoy", NamedTextColor.GOLD), List.of(
                linea("Se renuevan en " + Msg.tiempo(pl.rarezas().msRestantes()), NamedTextColor.YELLOW),
                linea("Página " + (pag + 1) + " de " + paginas, GRIS))));
        if (pag < paginas - 1) v.poner(50, pl.iconos().flecha(p, true, Iconos.txtNegrita("Página siguiente", NamedTextColor.YELLOW), List.of(linea("Página " + (pag + 2) + " de " + paginas, GRIS))), e -> { sonido(p); acciones(p, t, pag + 1); });
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

    // ---------- misiones ----------

    private static final String[] NOMBRE_TIPO = {"Diaria aleatoria", "Diaria elegida", "Semanal personal"};
    private static final String[] CLAVE_TIPO = {"diaria", "elegida", "semanal"};
    private static final int PAGINAS_MISIONES = 2;

    private Component barra(double frac, TextColor color) {
        int lleno = (int) Math.round(Math.max(0, Math.min(1, frac)) * 10);
        Component b = Component.empty();
        for (int i = 0; i < 10; i++) b = b.append(Iconos.txt("■", i < lleno ? color : NamedTextColor.DARK_GRAY));
        return b.append(Iconos.txt(" " + Msg.num(Math.max(0, Math.min(1, frac)) * 100, 0) + "%", GRIS));
    }

    private static String claveRareza(Rareza r) { return r.name().toLowerCase(java.util.Locale.ROOT); }

    /** Un item de mision: el material que piden, con su progreso y lo que pagan. */
    private ItemStack itemMision(Player p, Perfil f, Trabajo t, int tipo, Perfil.Mision m) {
        Perfil.Progreso g = f.de(t);
        List<Component> lore = new ArrayList<>();
        lore.add(linea("Rareza: " + m.rareza.nombre, m.rareza.color));
        lore.add(Component.empty());
        lore.add(Misiones.pedido(m));
        lore.add(barra((double) m.entregado / m.meta, m.rareza.color).append(Iconos.txt("  " + m.entregado + "/" + m.meta, BLANCO)));
        lore.add(Component.empty());
        double base = pl.misiones().base(tipo, m.rareza);
        lore.add(linea("Recompensa: " + Msg.dinero(pl.misiones().recompensa(tipo, m, g)), NamedTextColor.GREEN));
        lore.add(linea("Base " + Msg.dinero(base) + " × tu multiplicador x" + Msg.num(pl.pago().personal(g), 2), GRIS));
        lore.add(Component.empty());
        if (m.estado == Perfil.Mision.COMPLETA) lore.add(linea("¡Cobrada!", NamedTextColor.GREEN));
        else if (m.estado == Perfil.Mision.PERDIDA) lore.add(linea("Perdida: abandonaste el trabajo.", NamedTextColor.RED));
        else if (f.activo != t) lore.add(linea("Tomá este trabajo para entregarla.", NamedTextColor.RED));
        else if (!pl.misiones().enNpc(p, t)) lore.add(linea("Entregala en el NPC de " + t.nombre + ".", NamedTextColor.YELLOW));
        else {
            lore.add(linea("Clic: entregar lo que tengas", NamedTextColor.GREEN));
            lore.add(linea("(items sin nombre ni encantamientos)", GRIS));
        }
        Material mat = m.estado == Perfil.Mision.PERDIDA ? Material.BARRIER : m.item;
        return Iconos.item(mat, Iconos.txtNegrita(NOMBRE_TIPO[tipo], m.estado == Perfil.Mision.COMPLETA ? NamedTextColor.GREEN : m.rareza.color), lore);
    }

    /**
     * Pone una tarjeta de mision. Con el pack: la tarjeta grande (34x54, 2 casilleros de ancho y 3 de alto) cuelga del casillero de la fila 1
     * y el item de verdad va en el de la fila 2, encima de la ventanita del medio; el resto de la zona lleva relleno invisible para el clic.
     * Sin el pack, solo el item de verdad.
     */
    private void tarjeta(Ventana v, Player p, int col, String clave, ItemStack real, int[] zona, java.util.function.Consumer<InventoryClickEvent> alClic) {
        if (pl.iconos().conPack(p)) {
            ItemStack carta = pl.iconos().modelo(real.clone(), clave);
            ItemStack vacio = pl.iconos().relleno(carta);
            for (int fila = 1; fila <= 3; fila++)
                for (int c : zona) {
                    if (c == col && (fila == 1 || fila == 2)) continue;
                    v.poner(fila * 9 + c, vacio, alClic);
                }
            v.poner(1 * 9 + col, carta, alClic);
        }
        v.poner(2 * 9 + col, real, alClic);
    }

    void misiones(Player p, Trabajo t) { misiones(p, t, 0); }

    void misiones(Player p, Trabajo t, int pagina) {
        final int pag = ((pagina % PAGINAS_MISIONES) + PAGINAS_MISIONES) % PAGINAS_MISIONES;
        Perfil f = pl.perfiles().de(p);
        Perfil.Progreso g = f.de(t);
        pl.misiones().revisar(g, t);
        Ventana v = new Ventana(6, pl.iconos().titulo(p, Iconos.FONDO_MISIONES, "Misiones · " + t.nombre));

        if (pag == 0) {
            // diaria aleatoria (columna 1), diaria elegida (columna 4) y semanal personal (columna 7)
            Perfil.Mision a = g.aleatoria;
            tarjeta(v, p, 1, claveCarta(0, a), itemMision(p, f, t, Misiones.ALEATORIA, a), new int[]{0, 1, 2}, e -> {
                if (pl.misiones().entregar(p, t, Misiones.ALEATORIA, g.aleatoria)) misiones(p, t, pag);
            });

            if (g.elegida != null) {
                tarjeta(v, p, 4, claveCarta(1, g.elegida), itemMision(p, f, t, Misiones.ELEGIDA, g.elegida), new int[]{3, 4, 5}, e -> {
                    if (pl.misiones().entregar(p, t, Misiones.ELEGIDA, g.elegida)) misiones(p, t, pag);
                });
            } else if (g.elegidaPerdida) {
                tarjeta(v, p, 4, "mision_elegida_x_perdida", Iconos.item(Material.BARRIER, Iconos.txtNegrita("Diaria elegida", NamedTextColor.RED), List.of(
                        linea("Perdida: abandonaste el trabajo.", NamedTextColor.RED),
                        linea("Se puede volver a elegir en " + Msg.tiempo(pl.misiones().msHastaDia()), GRIS))), new int[]{3, 4, 5}, e -> { });
            } else {
                tarjeta(v, p, 4, "mision_elegir_x_normal", Iconos.item(Material.WRITABLE_BOOK, Iconos.txtNegrita("Diaria elegida", NamedTextColor.GOLD), List.of(
                        linea("Elegís 1 de 4 misiones, una por rareza.", GRIS),
                        linea("Cuanto más rara, más paga.", GRIS),
                        Component.empty(),
                        linea("Clic para elegir", NamedTextColor.GREEN))), new int[]{3, 4, 5}, e -> {
                    if (f.activo != t) { Msg.error(p, "Tomá el trabajo de " + t.nombre + " para elegir."); return; }
                    sonido(p);
                    elegir(p, t);
                });
            }

            tarjeta(v, p, 7, claveCarta(2, g.semanal), itemMision(p, f, t, Misiones.SEMANAL, g.semanal), new int[]{6, 7, 8}, e -> {
                if (pl.misiones().entregar(p, t, Misiones.SEMANAL, g.semanal)) misiones(p, t, pag);
            });
        } else {
            // global semanal (columna 2) y racha (columna 5)
            Misiones.Global gl = pl.misiones().global(t);
            int aporte = pl.misiones().aporteDe(t, p);
            int minimo = pl.misiones().aporteMinimo();
            List<Component> lg = new ArrayList<>();
            lg.add(linea("Tema: " + pl.misiones().nombreTema(t), NamedTextColor.GOLD));
            lg.add(linea("Todos los " + t.nombre + " entregan al mismo objetivo.", GRIS));
            lg.add(Component.empty());
            lg.add(barra(gl.total / gl.objetivo, t.color).append(Iconos.txt("  " + Msg.num(Math.min(gl.total, gl.objetivo), 0) + "/" + Msg.num(gl.objetivo, 0) + " puntos", BLANCO)));
            double parte = pl.misiones().parte(t, p.getUniqueId());
            lg.add(linea("Tu aporte: " + aporte + " puntos" + (parte > 0 ? " (" + Msg.num(parte * 100, 1) + "% del reparto)" : " (hacen falta " + minimo + " para cobrar)"), aporte >= minimo ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            if (parte > 0) lg.add(linea("Hoy te tocarían " + "$" + Msg.num(pl.misiones().pagoGlobal(t, p.getUniqueId()), 0), NamedTextColor.GREEN));
            lg.add(Component.empty());
            lg.add(linea("Al completarse:", GRIS));
            lg.add(linea(" · Pozo de " + "$" + Msg.num(pl.misiones().pozo(t), 0) + ", repartido según el % que aportó cada uno", NamedTextColor.GREEN));
            lg.add(linea(" · Todos los " + t.nombre + " tienen +20% de pago por 48 horas", NamedTextColor.GREEN));
            lg.add(Component.empty());
            boolean cobrable = pl.misiones().puedeCobrarGlobal(t, p);
            boolean cobrada = gl.completada && gl.cobraron.contains(p.getUniqueId());
            if (cobrada) lg.add(linea("¡Completada y cobrada!", NamedTextColor.GREEN));
            else if (cobrable) lg.add(linea("Clic para cobrar tu parte: " + "$" + Msg.num(pl.misiones().pagoGlobal(t, p.getUniqueId()), 0), NamedTextColor.GREEN));
            else if (gl.completada) lg.add(linea("Completada. Sin " + minimo + " puntos no cobrás.", NamedTextColor.RED));
            else if (f.activo != t) lg.add(linea("Tomá este trabajo para entregar.", NamedTextColor.RED));
            else if (!pl.misiones().enNpc(p, t)) lg.add(linea("Entregá en el NPC de " + t.nombre + ".", NamedTextColor.YELLOW));
            else {
                lg.add(linea("Clic: entregar todo lo que sume", NamedTextColor.GREEN));
                lg.add(linea("Clic derecho: solo lo que tenés en la mano", NamedTextColor.GREEN));
            }
            if (!gl.completada) lg.add(linea("Cambia de tema en " + Msg.tiempo(pl.misiones().msHastaCiclo()), GRIS));
            String claveG = cobrada ? "mision_global_x_completa" : "mision_global_" + t.id + (cobrable ? "_lista" : "_normal");
            tarjeta(v, p, 2, claveG, Iconos.item(Material.BEACON, Iconos.txtNegrita("Misión global semanal", cobrable ? NamedTextColor.GREEN : t.color), lg), new int[]{1, 2, 3}, e -> {
                if (pl.misiones().puedeCobrarGlobal(t, p)) {
                    if (pl.misiones().cobrarGlobal(p, t)) misiones(p, t, pag);
                    return;
                }
                if (pl.misiones().entregarGlobal(p, t, e.isRightClick()) > 0) misiones(p, t, pag);
            });

            int racha = pl.misiones().racha(g);
            List<Component> lr = new ArrayList<>();
            lr.add(linea("Días seguidos con las dos diarias hechas.", GRIS));
            lr.add(Component.empty());
            lr.add(linea("Racha: " + racha + (racha == 1 ? " día" : " días"), NamedTextColor.GOLD));
            lr.add(linea("Bono: +" + Msg.num(pl.misiones().bonoRacha(g) * 100, 0) + "% al multiplicador (tope +20%)", NamedTextColor.YELLOW));
            lr.add(Component.empty());
            lr.add(pl.misiones().diariasHechas(g) ? linea("Hoy ya sumaste tu día.", NamedTextColor.GREEN) : linea("Hoy te faltan diarias por cumplir.", NamedTextColor.RED));
            ItemStack ir = Iconos.item(Material.BLAZE_POWDER, Iconos.txtNegrita("Racha de misiones", NamedTextColor.GOLD), lr);
            ir.setAmount(Math.max(1, Math.min(64, racha)));
            tarjeta(v, p, 5, "mision_racha_x_normal", ir, new int[]{4, 5, 6}, e -> { });
        }

        List<Component> lt = new ArrayList<>();
        lt.add(linea("Diarias nuevas en " + Msg.tiempo(pl.misiones().msHastaDia()), NamedTextColor.YELLOW));
        lt.add(linea("Semanal: lunes 0:00 (en " + Msg.tiempo(pl.misiones().msHastaSemana()) + ")", NamedTextColor.YELLOW));
        lt.add(linea("Global nueva en " + Msg.tiempo(pl.misiones().msHastaCiclo()), NamedTextColor.YELLOW));
        double bono = pl.misiones().bonoGlobal(t);
        if (bono > 0) { lt.add(Component.empty()); lt.add(linea("Bono global activo: +" + Msg.num(bono * 100, 0) + "% de pago", NamedTextColor.GREEN)); }
        lt.add(Component.empty());
        lt.add(linea("Las misiones valen solo con el trabajo puesto:", GRIS));
        lt.add(linea("si lo abandonás, las que no cobraste se pierden.", GRIS));
        v.poner(5 * 9 + 4, Iconos.item(Material.CLOCK, Iconos.txtNegrita("Reinicios", NamedTextColor.GOLD), lt));
        if (pag == 1) {
            List<Component> la = new ArrayList<>();
            la.add(linea("Entregá estos items en el NPC (sin nombre", GRIS));
            la.add(linea("ni encantamientos). Los más raros valen más.", GRIS));
            la.add(Component.empty());
            List<java.util.Map.Entry<Material, Integer>> acep = pl.misiones().aceptados(t);
            int mostrar = Math.min(16, acep.size());
            for (int i = 0; i < mostrar; i++) {
                java.util.Map.Entry<Material, Integer> en = acep.get(i);
                la.add(Iconos.txt(en.getValue() + " pts  ", NamedTextColor.YELLOW)
                        .append(Component.translatable(en.getKey().translationKey()).color(BLANCO).decoration(TextDecoration.ITALIC, false)));
            }
            if (acep.size() > mostrar) la.add(linea("... y " + (acep.size() - mostrar) + " más", GRIS));
            v.poner(5 * 9 + 7, Iconos.item(Material.BOOK, Iconos.txtNegrita("Qué se entrega: " + pl.misiones().nombreTema(t), NamedTextColor.GOLD), la));
        }
        v.poner(5 * 9, pl.iconos().flecha(p, false, Iconos.txtNegrita("Volver", GRIS), null), e -> { sonido(p); trabajo(p, t); });
        v.poner(5 * 9 + 3, pl.iconos().flecha(p, false, Iconos.txtNegrita("Página anterior", NamedTextColor.YELLOW), List.of(linea("Página " + (pag + 1) + " de " + PAGINAS_MISIONES, GRIS))),
                e -> { sonido(p); misiones(p, t, pag - 1); });
        v.poner(5 * 9 + 5, pl.iconos().flecha(p, true, Iconos.txtNegrita("Página siguiente", NamedTextColor.YELLOW), List.of(linea("Página " + (pag + 1) + " de " + PAGINAS_MISIONES, GRIS))),
                e -> { sonido(p); misiones(p, t, pag + 1); });
        v.abrir(p);
    }

    /** Nombre del modelo de la tarjeta segun el tipo (0 aleatoria, 1 elegida, 2 semanal) y como va esa mision. */
    private static String claveCarta(int tipo, Perfil.Mision m) {
        if (m.estado == Perfil.Mision.COMPLETA) return "mision_" + CLAVE_TIPO[tipo] + "_x_completa";
        if (m.estado == Perfil.Mision.PERDIDA) return "mision_" + CLAVE_TIPO[tipo] + "_x_perdida";
        return "mision_" + CLAVE_TIPO[tipo] + "_" + claveRareza(m.rareza) + "_normal";
    }

    /** Elegir 1 de las 4 misiones del dia (una por rareza). */
    void elegir(Player p, Trabajo t) {
        Perfil f = pl.perfiles().de(p);
        Perfil.Progreso g = f.de(t);
        pl.misiones().revisar(g, t);
        Ventana v = new Ventana(6, pl.iconos().titulo(p, Iconos.FONDO_ELEGIR, "Elegí tu misión · " + t.nombre));
        int[] columnas = {1, 3, 5, 7};
        // las tarjetas vecinas se pisan un poco en las columnas del medio (2, 4 y 6): ahi no hay clic para no elegir la que no era
        int[][] zonas = {{0, 1}, {3}, {5}, {7, 8}};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            Perfil.Mision m = g.opciones[i];
            if (m == null) continue;
            List<Component> lore = new ArrayList<>();
            lore.add(linea("Rareza: " + m.rareza.nombre, m.rareza.color));
            lore.add(Component.empty());
            lore.add(Misiones.pedido(m));
            lore.add(Component.empty());
            lore.add(linea("Recompensa: " + Msg.dinero(pl.misiones().recompensa(Misiones.ELEGIDA, m, g)), NamedTextColor.GREEN));
            lore.add(Component.empty());
            lore.add(linea("Clic para elegirla (no se puede cambiar)", NamedTextColor.GREEN));
            ItemStack real = Iconos.item(m.item, Iconos.txtNegrita("Misión " + m.rareza.nombre.toLowerCase(java.util.Locale.ROOT), m.rareza.color), lore);
            tarjeta(v, p, columnas[i], "elegir_" + claveRareza(m.rareza), real, zonas[i], e -> {
                if (pl.misiones().elegir(p, t, idx)) {
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
                    misiones(p, t);
                }
            });
        }
        v.poner(5 * 9, pl.iconos().flecha(p, false, Iconos.txtNegrita("Volver", GRIS), null), e -> { sonido(p); misiones(p, t); });
        v.abrir(p);
    }

    // ---------- confirmar ----------

    void confirmarAbandono(Player p, Trabajo t) {
        long horas = pl.getConfig().getLong("abandonar-horas", 13);
        Ventana v = new Ventana(3, pl.iconos().titulo(p, Iconos.FONDO_CONFIRMAR, "¿Abandonar " + t.nombre + "?"));
        v.poner(13, Iconos.item(Material.PAPER, Iconos.txtNegrita("¿Seguro?", NamedTextColor.RED), List.of(
                linea("Te quedás " + horas + " horas sin trabajo:", GRIS),
                linea("no cobrás por acciones ni por tiempo", GRIS),
                linea("y no podés tomar otro hasta que pasen.", GRIS),
                Component.empty(),
                linea(pl.misiones().pendientes(pl.perfiles().de(p), t) > 0
                        ? "Vas a perder " + pl.misiones().pendientes(pl.perfiles().de(p), t) + " misión(es) sin cobrar."
                        : "Las misiones sin cobrar se pierden.", NamedTextColor.RED),
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
