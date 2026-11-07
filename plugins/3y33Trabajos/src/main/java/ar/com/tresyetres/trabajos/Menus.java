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
import java.util.Map;

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

    /** Ícono con el nivel, el XP y de dónde sale el multiplicador. */
    private ItemStack itemNivel(Trabajo t, Perfil.Progreso g, Perfil f) {
        List<Component> l = new ArrayList<>();
        l.add(barraNivel(t, g));
        if (g.nivel < Perfil.NIVEL_MAX) {
            double need = Pago.xpNecesaria(t, g.nivel);
            l.add(Iconos.txt("XP: ", GRIS).append(Iconos.txt(Msg.num(g.xp, 0) + " / " + Msg.num(need, 0), NamedTextColor.AQUA)));
            l.add(linea("Faltan " + Msg.num(need - g.xp, 0) + " XP para el nivel " + (g.nivel + 1) + ".", GRIS));
        }
        l.add(Component.empty());
        l.add(Iconos.txtNegrita("Tu multiplicador: x" + Msg.num(pl.pago().personal(g), 2), NamedTextColor.YELLOW));
        l.add(linea("- Nivel: +" + Msg.num(Pago.bonoNivel(g.nivel) * 100, 1) + "%", GRIS));
        if (g.prestigio > 0) l.add(linea("- Prestigio: +" + Msg.num(25.0 * g.prestigio, 0) + "%", GRIS));
        l.add(linea("- Racha de misiones: +" + Msg.num(pl.misiones().bonoRacha(g) * 100, 0) + "%", GRIS));
        l.add(Component.empty());
        l.add(linea("El XP sale de las acciones del trabajo", GRIS));
        l.add(linea("y de todas las misiones (diarias, semanal y global).", GRIS));
        return Iconos.item(Material.EXPERIENCE_BOTTLE, Iconos.txtNegrita("Tu nivel · " + t.nombre, t.color), l);
    }

    private Component barraNivel(Trabajo t, Perfil.Progreso g) {
        if (g.nivel >= Perfil.NIVEL_MAX) return linea("Nivel " + Perfil.NIVEL_MAX + " (máximo)", NamedTextColor.GOLD);
        double frac = g.xp / Pago.xpNecesaria(t, g.nivel);
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

        int pendientes = g.pend.size();
        int guardados = 0;
        for (int n : g.boosts.values()) guardados += n;
        List<Component> lr = new ArrayList<>();
        lr.add(linea("Cada " + pl.recompensas().cada() + " niveles hay una recompensa para elegir:", GRIS));
        lr.add(linea("1 de 3 opciones (boosts o XP instantánea).", GRIS));
        lr.add(Component.empty());
        lr.add(pendientes > 0 ? Iconos.txtNegrita("¡Tenés " + pendientes + " para elegir!", NamedTextColor.GREEN) : linea("Sin recompensas pendientes.", NamedTextColor.YELLOW));
        lr.add(Component.empty());
        lr.add(linea("Clic para ver el camino", NamedTextColor.GREEN));
        ItemStack ir = Iconos.item(Material.CHEST, Iconos.txtNegrita("Recompensas", NamedTextColor.LIGHT_PURPLE), lr);
        if (pendientes > 0) { org.bukkit.inventory.meta.ItemMeta im = ir.getItemMeta(); im.setEnchantmentGlintOverride(true); ir.setItemMeta(im); }
        v.poner(28, ir, e -> { sonido(p); recompensas(p, t); });
        v.poner(25, Iconos.item(Material.EXPERIENCE_BOTTLE, Iconos.txtNegrita("Boosts", NamedTextColor.GOLD), List.of(
                linea("Los boosts que elegiste en el camino.", GRIS),
                linea("Activalos cuando quieras.", GRIS),
                Component.empty(),
                linea("Guardados: " + guardados, NamedTextColor.YELLOW),
                Component.empty(),
                linea("Clic para abrir", NamedTextColor.GREEN))), e -> { sonido(p); boosts(p, t); });

        v.poner(16, relojRarezas());
        v.poner(37, itemNivel(t, g, f));
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

    // ---------- recompensas por nivel: el camino ----------

    private static final int NODOS_POR_PAGINA = 15;

    void recompensas(Player p, Trabajo t) { recompensas(p, t, -1); }

    /** El camino de niveles: un nodo cada "cada-niveles" niveles, en zigzag. pagina -1 = la que tiene tu proximo nodo. */
    void recompensas(Player p, Trabajo t, int pagina) {
        Perfil f = pl.perfiles().de(p);
        Perfil.Progreso g = f.de(t);
        int cada = pl.recompensas().cada();
        int total = Perfil.NIVEL_MAX / cada;
        int paginas = (total + NODOS_POR_PAGINA - 1) / NODOS_POR_PAGINA;
        if (pagina < 0) {
            int sig = g.pend.isEmpty() ? Math.min(total, g.nivel / cada + 1) : g.pend.firstKey() / cada;
            pagina = (sig - 1) / NODOS_POR_PAGINA;
        }
        final int pag = Math.max(0, Math.min(paginas - 1, pagina));
        // con el pack, el fondo ya dibuja el caminito y las plaquitas de cada nodo: no hacen falta los tramos de vidrio
        boolean pack = pl.iconos().conPack(p);
        Ventana v = new Ventana(6, pl.iconos().titulo(p, Iconos.FONDO_CAMINO, "Camino · " + t.nombre));
        for (int k = 0; k < NODOS_POR_PAGINA; k++) {
            int idx = pag * NODOS_POR_PAGINA + k;
            if (idx >= total) break;
            int nivel = (idx + 1) * cada;
            int fila = (k / 5) * 2, j = k % 5;
            int col = (k / 5) % 2 == 0 ? 2 * j : 8 - 2 * j;
            nodo(v, fila * 9 + col, p, t, g, nivel);
            // el tramo que lleva a este nodo: hacia el costado dentro de la fila, o hacia abajo al empezar una fila nueva
            if (pack) continue;
            boolean recorrido = nivel <= g.nivel;
            ItemStack tramo = Iconos.item(recorrido ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), null);
            org.bukkit.inventory.meta.ItemMeta tm = tramo.getItemMeta(); tm.setHideTooltip(true); tramo.setItemMeta(tm);
            if (j > 0) v.poner(fila * 9 + ((k / 5) % 2 == 0 ? 2 * j - 1 : 9 - 2 * j), tramo);
            else if (k > 0) v.poner((fila - 1) * 9 + ((k / 5) % 2 == 0 ? 0 : 8), tramo);
        }
        v.poner(45, pl.iconos().flecha(p, false, Iconos.txtNegrita("Volver", GRIS), null), e -> { sonido(p); trabajo(p, t); });
        if (pag > 0) v.poner(47, pl.iconos().flecha(p, false, Iconos.txtNegrita("Página anterior", NamedTextColor.YELLOW), List.of(linea("Página " + (pag + 1) + " de " + paginas, GRIS))),
                e -> { sonido(p); recompensas(p, t, pag - 1); });
        v.poner(49, Iconos.item(Material.MAP, Iconos.txtNegrita("Camino de " + t.nombre, NamedTextColor.GOLD), List.of(
                linea("Una recompensa cada " + cada + " niveles.", GRIS),
                linea("Elegís 1 de 3 y las otras dos se pierden.", GRIS),
                linea("Las que no elijas te esperan acá.", GRIS),
                Component.empty(),
                linea("Nivel " + g.nivel + " · para elegir: " + g.pend.size(), NamedTextColor.YELLOW))));
        if (pag < paginas - 1) v.poner(51, pl.iconos().flecha(p, true, Iconos.txtNegrita("Página siguiente", NamedTextColor.YELLOW), List.of(linea("Página " + (pag + 1) + " de " + paginas, GRIS))),
                e -> { sonido(p); recompensas(p, t, pag + 1); });
        v.poner(53, Iconos.item(Material.EXPERIENCE_BOTTLE, Iconos.txtNegrita("Boosts", NamedTextColor.GOLD), List.of(linea("Clic para abrir", NamedTextColor.GREEN))), e -> { sonido(p); boosts(p, t); });
        v.abrir(p);
    }

    /** Un nodo del camino: ya elegido, esperando que elijas, alcanzado sin recompensa o todavia lejos. */
    private void nodo(Ventana v, int slot, Player p, Trabajo t, Perfil.Progreso g, int nivel) {
        String hecha = g.hechas.get(nivel);
        if (hecha != null) {
            List<Component> lore = new ArrayList<>();
            lore.add(linea("Nivel " + nivel + " · ya la elegiste", NamedTextColor.GREEN));
            lore.add(Component.empty());
            lore.addAll(pl.recompensas().detalle(hecha, t, g));
            v.poner(slot, pl.recompensas().icono(p, hecha, lore));
        } else if (g.pend.containsKey(nivel)) {
            ItemStack it = Iconos.item(Material.CHEST, Iconos.txtNegrita("Nivel " + nivel + " · ¡Elegí tu recompensa!", NamedTextColor.GREEN), List.of(
                    linea("Hay 3 opciones esperándote.", GRIS),
                    linea("Elegís 1 y las otras dos se pierden.", GRIS),
                    Component.empty(),
                    linea("Clic para elegir", NamedTextColor.GREEN)));
            org.bukkit.inventory.meta.ItemMeta im = it.getItemMeta();
            im.setEnchantmentGlintOverride(true);
            it.setItemMeta(im);
            v.poner(slot, it, e -> { sonido(p); elegirRecompensa(p, t, nivel); });
        } else if (nivel <= g.nivel) {
            v.poner(slot, Iconos.item(Material.IRON_BARS, Iconos.txtNegrita("Nivel " + nivel, GRIS), List.of(linea("Sin recompensa (lo alcanzaste antes del camino).", GRIS))));
        } else {
            v.poner(slot, Iconos.item(Material.GRAY_DYE, Iconos.txtNegrita("Nivel " + nivel, GRIS), List.of(
                    linea("Te faltan " + (nivel - g.nivel) + " nivel" + (nivel - g.nivel == 1 ? "" : "es") + ".", GRIS))));
        }
    }

    /** Elegir 1 de las 3 opciones de un nodo, en cartas. El primer clic marca y el segundo confirma. */
    void elegirRecompensa(Player p, Trabajo t, int nivel) {
        Perfil f = pl.perfiles().de(p);
        Perfil.Progreso g = f.de(t);
        String[] of = g.pend.get(nivel);
        if (of == null) { recompensas(p, t); return; }
        Ventana v = new Ventana(6, pl.iconos().titulo(p, Iconos.FONDO_MISIONES, "Recompensa · nivel " + nivel));
        int[] columnas = {1, 4, 7};
        int[][] zonas = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}};
        int marcada = pl.recompensas().seleccionada(p, nivel);
        v.poner(4, Iconos.item(Material.PAPER, Iconos.txtNegrita("Nivel " + nivel + " · elegí una", NamedTextColor.GREEN), List.of(
                linea("Clic para marcarla y otro clic para confirmar.", GRIS),
                linea("Las otras dos se pierden.", NamedTextColor.RED))));
        for (int i = 0; i < of.length; i++) {
            final int idx = i;
            String k = of[i];
            boolean sel = marcada == i;
            List<Component> lore = new ArrayList<>(pl.recompensas().detalle(k, t, g));
            lore.add(Component.empty());
            lore.add(sel ? Iconos.txtNegrita("¡Clic de nuevo para confirmar!", NamedTextColor.GREEN) : linea("Clic para marcarla", GRIS));
            ItemStack real = pl.recompensas().icono(p, k, lore);
            if (sel) { org.bukkit.inventory.meta.ItemMeta im = real.getItemMeta(); im.setEnchantmentGlintOverride(true); real.setItemMeta(im); }
            tarjeta(v, p, columnas[i], pl.recompensas().modeloCarta(k) + (sel ? "_sel" : ""), real, zonas[i], e -> {
                sonido(p);
                if (pl.recompensas().seleccionada(p, nivel) == idx) {
                    if (pl.recompensas().reclamar(p, t, nivel, idx)) recompensas(p, t, ((nivel / pl.recompensas().cada()) - 1) / NODOS_POR_PAGINA);
                } else {
                    pl.recompensas().seleccionar(p, nivel, idx);
                    elegirRecompensa(p, t, nivel);
                }
            });
        }
        v.poner(5 * 9, pl.iconos().flecha(p, false, Iconos.txtNegrita("Volver", GRIS), null), e -> { sonido(p); recompensas(p, t); });
        v.abrir(p);
    }

    // ---------- boosts: los guardados y los que estan andando ----------

    void boosts(Player p, Trabajo t) {
        Perfil f = pl.perfiles().de(p);
        Perfil.Progreso g = f.de(t);
        Ventana v = new Ventana(5, Component.text("Boosts · " + t.nombre, ROJO, TextDecoration.BOLD));
        v.poner(2, boostActivo(p, "XP", f.boostXp));
        v.poner(4, Iconos.item(Material.BOOK, Iconos.txtNegrita("Cómo andan los boosts", NamedTextColor.GOLD), List.of(
                linea("Pueden andar dos a la vez: uno de XP y uno de plata.", GRIS),
                linea("Dos del mismo tipo no se acumulan.", GRIS),
                linea("El Mega cuenta como los dos.", GRIS),
                linea("El tiempo corre mientras estás conectado", GRIS),
                linea("con el trabajo del boost puesto.", GRIS),
                Component.empty(),
                linea("Clic en uno guardado para usarlo.", NamedTextColor.GREEN))));
        v.poner(6, boostActivo(p, "plata", f.boostPlata));
        int n = 0;
        for (Map.Entry<String, Integer> e : g.boosts.entrySet()) {
            if (n >= 18) break;
            final String k = e.getKey();
            List<Component> lore = new ArrayList<>(pl.recompensas().detalle(k, t, g));
            lore.add(Component.empty());
            lore.add(linea("Tenés " + e.getValue() + ".", NamedTextColor.YELLOW));
            lore.add(Iconos.txtNegrita("Clic para usarlo", NamedTextColor.GREEN));
            ItemStack it = pl.recompensas().icono(p, k, lore);
            it.setAmount(Math.max(1, Math.min(64, e.getValue())));
            v.poner(18 + n, it, ev -> { sonido(p); if (pl.recompensas().activar(p, t, k)) boosts(p, t); });
            n++;
        }
        if (n == 0) v.poner(22, Iconos.item(Material.BARRIER, Iconos.txtNegrita("No tenés boosts guardados", NamedTextColor.GRAY), List.of(
                linea("Elegilos en el camino de recompensas.", GRIS))));
        v.poner(36, pl.iconos().flecha(p, false, Iconos.txtNegrita("Volver", GRIS), null), e -> { sonido(p); trabajo(p, t); });
        v.poner(40, Iconos.item(Material.CHEST, Iconos.txtNegrita("Camino de recompensas", NamedTextColor.LIGHT_PURPLE), List.of(linea("Clic para abrir", NamedTextColor.GREEN))), e -> { sonido(p); recompensas(p, t); });
        v.abrir(p);
    }

    private ItemStack boostActivo(Player p, String tipo, Recompensas.Activo a) {
        if (a == null || a.ms <= 0) {
            return Iconos.item(Material.GRAY_DYE, Iconos.txtNegrita("Sin boost de " + tipo + " activo", GRIS), List.of(linea("Activá uno de los que tenés guardados.", GRIS)));
        }
        return pl.recompensas().icono(p, a.clave, List.of(
                Iconos.txtNegrita("Boost de " + tipo + " activo", NamedTextColor.GREEN),
                linea("Quedan " + Msg.tiempo(a.ms), NamedTextColor.YELLOW),
                linea("Solo corre mientras tengas puesto " + a.trabajo.nombre + ".", GRIS)));
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
        lore.add(linea("Recompensa: " + Msg.dinero(pl.misiones().recompensa(tipo, m, g)) + " + " + Msg.num(pl.misiones().recompensaXp(t, tipo, m), 0) + " XP", NamedTextColor.GREEN));
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
            int minimo = pl.misiones().aporteMinimo(t);
            List<Component> lg = new ArrayList<>();
            Misiones.Rar rar = pl.misiones().rarezaDe(t);
            double parte = pl.misiones().parte(t, p.getUniqueId());
            boolean cobrable = pl.misiones().puedeCobrarGlobal(t, p);
            boolean cobrada = gl.completada && gl.cobraron.contains(p.getUniqueId());
            lg.add(Iconos.txt("Tipo de misión: ", GRIS).append(Iconos.txt("Global (" + t.nombre + ")", BLANCO)));
            lg.add(Iconos.txt("Rareza: ", GRIS).append(Iconos.txt(rar.nombre, rar.color)));
            lg.add(linea("Cambia en " + Msg.tiempo(pl.misiones().msHastaCiclo()), GRIS));
            lg.add(Component.empty());
            lg.add(Iconos.txtNegrita("Objetivo global", NamedTextColor.YELLOW));
            lg.add(pl.misiones().pedidoGlobal(t));
            lg.add(Iconos.txt("Progreso compartido: ", NamedTextColor.GOLD).append(Iconos.txt(Msg.num(Math.min(gl.total, gl.objetivo), 0) + "/" + Msg.num(gl.objetivo, 0)
                    + " (" + Msg.num(Math.min(100, gl.total * 100.0 / gl.objetivo), 1) + "%)", BLANCO)));
            lg.add(Iconos.txt("Tu aporte: ", NamedTextColor.GOLD).append(Iconos.txt(Msg.num(aporte, 0) + (parte > 0 ? " (" + Msg.num(parte * 100, 1) + "% del reparto)" : ""), BLANCO)));
            if (parte <= 0) lg.add(linea("Hay que entregar al menos " + Msg.num(minimo, 0) + " para cobrar.", GRIS));
            lg.add(Component.empty());
            lg.add(Iconos.txtNegrita("Recompensas", NamedTextColor.YELLOW));
            lg.add(Iconos.txt("- Fondo colaborador: ", NamedTextColor.GOLD).append(Iconos.txt("$" + Msg.num(pl.misiones().pozo(t), 0), NamedTextColor.YELLOW)));
            lg.add(Iconos.txt("- Fondo XP: ", NamedTextColor.GREEN).append(Iconos.txt(Msg.num(pl.misiones().pozoXp(t), 0) + " XP", NamedTextColor.GREEN)));
            lg.add(Iconos.txt("- Bono global: ", NamedTextColor.GOLD).append(Iconos.txt("+" + Math.round(rar.bono * 100) + "% XP + dinero por " + pl.misiones().bonoHoras() + " h", NamedTextColor.YELLOW)));
            lg.add(linea("Los fondos se reparten según el % que entregó cada uno (tope " + Math.round(pl.misiones().topeParte() * 100) + "% por persona).", GRIS));
            if (parte > 0) {
                lg.add(Iconos.txtNegrita("Tu recompensa estimada:", NamedTextColor.GREEN));
                lg.add(linea("- $" + Msg.num(pl.misiones().pagoGlobal(t, p.getUniqueId()), 0), NamedTextColor.GREEN));
                lg.add(linea("- " + Msg.num(pl.misiones().xpGlobal(t, p.getUniqueId()), 0) + " XP", NamedTextColor.GREEN));
            }
            lg.add(Component.empty());
            String estado = cobrada ? "Cobrada" : cobrable ? "Lista para cobrar" : gl.completada ? "Financada" : aporte > 0 ? "Aportando" : "No unido";
            NamedTextColor ce = cobrada || cobrable || gl.completada ? NamedTextColor.GREEN : aporte > 0 ? NamedTextColor.YELLOW : NamedTextColor.RED;
            lg.add(Iconos.txt("Estado: ", GRIS).append(Iconos.txt(estado, ce)));
            lg.add(Component.empty());
            if (cobrada) lg.add(Iconos.txtNegrita("¡Completada y cobrada!", NamedTextColor.GREEN));
            else if (cobrable) lg.add(Iconos.txtNegrita("Hacé clic para cobrar tu parte", NamedTextColor.GREEN));
            else if (gl.completada) lg.add(linea("Completada. Sin " + Msg.num(minimo, 0) + " entregados no cobrás.", NamedTextColor.RED));
            else if (f.activo != t) lg.add(linea("Tomá este trabajo para entregar.", NamedTextColor.RED));
            else if (!pl.misiones().enNpc(p, t)) lg.add(linea("Entregá en el NPC de " + t.nombre + ".", NamedTextColor.YELLOW));
            else {
                lg.add(Iconos.txtNegrita("Hacé clic para contribuir", NamedTextColor.GREEN));
                lg.add(linea("Clic derecho: solo lo que tenés en la mano", GRIS));
            }
            String claveG = cobrada ? "mision_global_x_completa" : "mision_global_" + t.id + (cobrable ? "_lista" : "_normal");
            tarjeta(v, p, 2, claveG, Iconos.item(Material.BEACON, Iconos.txtNegrita("Misión global", NamedTextColor.GREEN), lg), new int[]{1, 2, 3}, e -> {
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
        v.poner(5 * 9 + 7, pl.iconos().libro(p, Iconos.txtNegrita("Cómo funciona la misión global", NamedTextColor.GOLD), List.of(
                Iconos.txt("Todos los " + t.nombre + " del servidor", BLANCO),
                Iconos.txt("completan la misma misión juntos.", BLANCO),
                Component.empty(),
                Iconos.txtNegrita("1. ", NamedTextColor.YELLOW).append(Iconos.txt("Cada " + pl.misiones().cicloDias() + " días sale una misión nueva:", GRIS)),
                linea("   entregar una cantidad de un ítem.", GRIS),
                Iconos.txtNegrita("2. ", NamedTextColor.YELLOW).append(Iconos.txt("Entregás en el NPC de " + t.nombre + ".", GRIS)),
                Iconos.txtNegrita("3. ", NamedTextColor.YELLOW).append(Iconos.txt("Lo que entrega cada uno suma a la barra.", GRIS)),
                Iconos.txtNegrita("4. ", NamedTextColor.YELLOW).append(Iconos.txt("Al llegar al 100% se completa y se", GRIS)),
                linea("   activa el bono para todos.", GRIS),
                Iconos.txtNegrita("5. ", NamedTextColor.YELLOW).append(Iconos.txt("Cobrás plata y XP según el % que", GRIS)),
                linea("   entregaste sobre el total.", GRIS),
                Component.empty(),
                Iconos.txt("Más rara la misión, más paga y más bono.", NamedTextColor.GOLD),
                Iconos.txt("Nadie cobra más del " + Math.round(pl.misiones().topeParte() * 100) + "% del fondo.", NamedTextColor.GRAY),
                Iconos.txt("Si no se completa, cobrás una compensación baja.", NamedTextColor.RED),
                Iconos.txt("Si no cobraste, te llega sola al cambiar la misión.", NamedTextColor.GRAY))));
        v.poner(5 * 9 + 1, itemNivel(t, g, f));
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
            lore.add(linea("Recompensa: " + Msg.dinero(pl.misiones().recompensa(Misiones.ELEGIDA, m, g)) + " + " + Msg.num(pl.misiones().recompensaXp(t, Misiones.ELEGIDA, m), 0) + " XP", NamedTextColor.GREEN));
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
