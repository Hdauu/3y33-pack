package ar.com.tresyetres.shops;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.BlockState;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Holograma de cada tienda, de abajo hacia arriba: item flotante, nombre, precios, "Esta tienda esta vacia!" y el mensaje del dueño.
 * Solo existe mientras hay jugadores cerca. Java ve el ItemDisplay (con la rotacion elegida) y Bedrock ve el item comun,
 * y el texto son armor stands invisibles, que Geyser traduce bien.
 */
final class Holograma implements Listener {
    private static final class Pieza {
        UUID item, visor;
        boolean listo;
        int fase;
        /** Para saber si un jugador la esta mirando: mundo, centro del cofre, radio de ese cofre y donde flota el item. */
        String mundo = "";
        double x, y, z, radio = 0.8, ix, iy, iz;
        final List<UUID> lineas = new ArrayList<>();
        final List<Component> textos = new ArrayList<>();
    }

    private final ShopsPlugin pl;
    private final NamespacedKey clave;
    private final Map<Long, Pieza> piezas = new HashMap<>();
    private BukkitTask tarea, tareaMirada;
    private int ciclos;
    /** Que tienda esta mirando cada jugador (el texto del holograma solo se le muestra de esa). */
    private final Map<UUID, Long> mirando = new HashMap<>();
    private final Set<String> pendientes = new HashSet<>();

    Holograma(ShopsPlugin pl) {
        this.pl = pl;
        this.clave = new NamespacedKey(pl, "holo");
    }

    void iniciar() {
        limpiarHuerfanas();
        tarea = Bukkit.getScheduler().runTaskTimer(pl, this::ciclo, 40L, 20L);
        tareaMirada = Bukkit.getScheduler().runTaskTimer(pl, this::mirada, 40L, 4L);
    }

    void detener() {
        if (tarea != null) tarea.cancel();
        if (tareaMirada != null) tareaMirada.cancel();
        for (Pieza p : piezas.values()) quitarPieza(p);
        piezas.clear();
        mirando.clear();
    }

    /** Borra todos los hologramas; el ciclo los vuelve a armar enseguida con la config y los datos actuales. */
    void reconstruir() {
        for (Pieza p : piezas.values()) quitarPieza(p);
        piezas.clear();
        limpiarHuerfanas();
    }

    /** Los de Bedrock (Floodgate) tienen UUID que empieza con ceros. */
    static boolean esBedrock(Player p) { return p.getUniqueId().getMostSignificantBits() == 0L; }

    // ---------- ciclo ----------

    private float vaiven() { return (float) pl.getConfig().getDouble("holograma.vaiven", 0.12); }

    /** Fase del vaiven suave del item: cambia cada 2 segundos. */
    private int fase() { return (ciclos / 2) % 2; }

    private void ciclo() {
        ciclos++;
        try {
            double aparece2 = Math.pow(pl.getConfig().getDouble("holograma.rango-aparicion", 14.0), 2);
            double desaparece = pl.getConfig().getDouble("holograma.rango-desaparicion", 24.0);
            double desaparece2 = desaparece * desaparece;
            int rc = (int) Math.ceil(desaparece / 16.0);
            // solo se miran las tiendas de los chunks que rodean a cada jugador, no todas las del server
            Map<Long, Double> distancia = new HashMap<>();
            Map<Long, Tienda> cerca = new HashMap<>();
            for (Player jug : Bukkit.getOnlinePlayers()) {
                Location pos = jug.getLocation();
                World w = pos.getWorld();
                String mundo = w.getName();
                int cx = pos.getBlockX() >> 4, cz = pos.getBlockZ() >> 4;
                for (int dx = -rc; dx <= rc; dx++) {
                    for (int dz = -rc; dz <= rc; dz++) {
                        for (Tienda t : pl.almacen().enChunk(mundo, cx + dx, cz + dz)) {
                            double tx = t.doble ? (t.x + t.x2) / 2.0 + 0.5 : t.x + 0.5, tz = t.doble ? (t.z + t.z2) / 2.0 + 0.5 : t.z + 0.5;
                            double ddx = pos.getX() - tx, ddy = pos.getY() - t.y, ddz = pos.getZ() - tz;
                            distancia.merge(t.id, ddx * ddx + ddy * ddy + ddz * ddz, Math::min);
                            cerca.put(t.id, t);
                        }
                    }
                }
            }
            for (Map.Entry<Long, Tienda> en : cerca.entrySet()) {
                Tienda t = en.getValue();
                World w = t.mundoObj();
                if (w == null || !w.isChunkLoaded(t.x >> 4, t.z >> 4)) { quitar(t.id); continue; }
                double min = distancia.get(t.id);
                if (min <= aparece2) poner(t, w, centro(t, w));
                else if (min > desaparece2) quitar(t.id);
            }
            for (Long id : new ArrayList<>(piezas.keySet())) if (!cerca.containsKey(id)) quitar(id);
            visibilidad();
        } catch (Throwable e) {
            pl.getLogger().warning("Holograma: " + e);
        }
    }

    private static Location centro(Tienda t, World w) {
        if (t.doble) return new Location(w, (t.x + t.x2) / 2.0 + 0.5, t.y, (t.z + t.z2) / 2.0 + 0.5);
        return new Location(w, t.x + 0.5, t.y, t.z + 0.5);
    }

    /** Vuelve a dibujar una tienda ya mostrada (cambio de ajustes, de stock, etc.). */
    void refrescar(Tienda t) {
        if (!piezas.containsKey(t.id)) return;
        World w = t.mundoObj();
        if (w == null) return;
        poner(t, w, centro(t, w));
    }

    void quitar(long id) {
        Pieza p = piezas.remove(id);
        if (p != null) quitarPieza(p);
    }

    // ---------- armado ----------

    /**
     * Donde queda el holograma segun la posicion elegida: arriba del cofre, pegado a un costado, o abajo.
     * Se calcula con el tamaño real del item para que quede casi tocando la tienda en todas las direcciones.
     */
    private Location anclaje(Tienda t, Location centro, int lineas) {
        double escala = pl.getConfig().getDouble("holograma.escala-item", 0.6);
        double mitad = (t.item.getType().isBlock() ? 0.25 : 0.5) * escala;      // medio ancho del item mostrado
        double hueco = pl.getConfig().getDouble("holograma.separacion-tienda", 0.06);
        double vaiven = pl.getConfig().getDouble("holograma.vaiven", 0.12);
        double altoLado = pl.getConfig().getDouble("holograma.altura-lateral", 0.15);
        double sep = pl.getConfig().getDouble("holograma.separacion-lineas", 0.26);
        double arranque = pl.getConfig().getDouble("holograma.altura-texto", 0.25);
        // un cofre doble es mas ancho en su eje: el holograma tiene que quedar fuera del cofre entero
        double ex = t.doble && t.x != t.x2 ? (Math.abs(t.x - t.x2) + 1) / 2.0 : 0.5;
        double ez = t.doble && t.z != t.z2 ? (Math.abs(t.z - t.z2) + 1) / 2.0 : 0.5;
        double lado = mitad + hueco;
        String r = t.rotacion == null ? "ARRIBA" : t.rotacion;
        return switch (r) {
            case "NORTE" -> centro.clone().add(0, altoLado, -(ez + lado));
            case "SUR" -> centro.clone().add(0, altoLado, ez + lado);
            case "ESTE" -> centro.clone().add(ex + lado, altoLado, 0);
            case "OESTE" -> centro.clone().add(-(ex + lado), altoLado, 0);
            case "ABAJO" -> {
                // el texto queda entre el item y el cofre: la linea de arriba termina justo debajo del cofre
                double techoTexto = arranque + (Math.max(1, lineas) - 1) * sep + 0.32;
                yield centro.clone().add(0, -hueco - techoTexto, 0);
            }
            default -> centro.clone().add(0, 1.0 + mitad + hueco + vaiven - 0.15 + pl.getConfig().getDouble("holograma.ajuste-arriba", -0.09), 0);   // el item (en base + 0.15) apenas toca la tapa al bajar; ajuste-arriba lo pega o lo despega del cofre
        };
    }

    private void poner(Tienda t, World w, Location bloque) {
        org.bukkit.inventory.Inventory inv = Operaciones.inventario(t);
        if (inv == null) return;
        int stock = Operaciones.contar(inv, t.item);
        // texto, de abajo hacia arriba
        List<Component> textos = new ArrayList<>();
        textos.add(t.item.effectiveName().color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false));
        boolean suspendida = pl.integridad().motivo(t) != null;
        textos.add(suspendida ? Component.text("Tienda suspendida", NamedTextColor.RED, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false) : precios(t));
        if (!suspendida && stock == 0 && t.puedeComprar()) textos.add(Component.text("Esta tienda está vacía!", NamedTextColor.RED, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        if (!t.mensaje.isBlank()) textos.add(LegacyComponentSerializer.legacyAmpersand().deserialize(t.mensaje).decoration(TextDecoration.ITALIC, false));

        Location base = anclaje(t, bloque, textos.size());
        Pieza p = piezas.computeIfAbsent(t.id, x -> new Pieza());
        p.mundo = w.getName();
        p.x = bloque.getX(); p.y = bloque.getY() + 0.5; p.z = bloque.getZ();
        p.radio = t.doble ? pl.getConfig().getDouble("holograma.mirada-radio-doble", 1.3) : pl.getConfig().getDouble("holograma.mirada-radio", 0.8);
        p.ix = base.getX(); p.iy = base.getY() + 0.3; p.iz = base.getZ();
        ItemStack uno = t.item.clone();
        uno.setAmount(1);

        // item para Bedrock: item comun sin recoger
        Entity ei = p.item == null ? null : Bukkit.getEntity(p.item);
        if (ei == null || !ei.isValid()) {
            Item nuevo = w.dropItem(base, uno, i -> {
                i.setPickupDelay(32767);
                i.setUnlimitedLifetime(true);
                i.setGravity(false);
                i.setVelocity(new Vector());
                i.setInvulnerable(true);
                i.setPersistent(false);
                i.setCanMobPickup(false);
                i.setCanPlayerPickup(false);
                i.setSilent(true);
                i.setVisibleByDefault(false);
                i.getPersistentDataContainer().set(clave, PersistentDataType.LONG, t.id);
            });
            p.item = nuevo.getUniqueId();
        } else {
            if (ei.getLocation().distanceSquared(base) > 0.0004) { ei.teleport(base); ei.setVelocity(new Vector()); }
            if (ei instanceof Item it && !it.getItemStack().isSimilar(uno)) it.setItemStack(uno);
        }

        // item para Java: ItemDisplay con la rotacion elegida
        float escala = (float) pl.getConfig().getDouble("holograma.escala-item", 0.6);
        Location destino = base.clone().add(0, 0.15, 0);
        Entity ev = p.visor == null ? null : Bukkit.getEntity(p.visor);
        if (ev == null || !ev.isValid()) {
            ItemDisplay nuevo = w.spawn(destino, ItemDisplay.class, d -> {
                d.setItemStack(uno);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setBillboard(Display.Billboard.VERTICAL);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                d.setViewRange(0.6f);
                d.setTeleportDuration(2);
                d.setInterpolationDuration(40);
                d.setTransformation(new Transformation(new Vector3f(0f, -vaiven(), 0f), new Quaternionf(), new Vector3f(escala, escala, escala), new Quaternionf()));
                d.getPersistentDataContainer().set(clave, PersistentDataType.LONG, t.id);
            });
            p.visor = nuevo.getUniqueId();
            p.fase = 0;
            p.listo = true;
            ev = nuevo;
        }
        if (ev instanceof ItemDisplay idp) {
            if (idp.getLocation().distanceSquared(destino) > 0.0004) idp.teleport(destino);
            if (!idp.getItemStack().isSimilar(uno)) idp.setItemStack(uno);
            int f = fase();
            if (p.listo && p.fase != f) {
                // vaiven suave: sube y baja unos centimetros, interpolado por el cliente
                idp.setInterpolationDuration(40);
                idp.setTransformation(new Transformation(new Vector3f(0f, f == 0 ? -vaiven() : vaiven(), 0f), new Quaternionf(), new Vector3f(escala, escala, escala), new Quaternionf()));
                idp.setInterpolationDelay(0);
                p.fase = f;
            }
        }

        double sep = pl.getConfig().getDouble("holograma.separacion-lineas", 0.26);
        double arranque = pl.getConfig().getDouble("holograma.altura-texto", 0.25);
        while (p.lineas.size() > textos.size()) {
            UUID u = p.lineas.remove(p.lineas.size() - 1);
            p.textos.remove(p.textos.size() - 1);
            Entity e = Bukkit.getEntity(u);
            if (e != null) e.remove();
        }
        for (int i = 0; i < textos.size(); i++) {
            Location donde = base.clone().add(0, arranque + i * sep, 0);
            Component texto = textos.get(i);
            Entity e = i < p.lineas.size() ? Bukkit.getEntity(p.lineas.get(i)) : null;
            if (e instanceof ArmorStand a && a.isValid()) {
                if (a.getLocation().distanceSquared(donde) > 0.0004) a.teleport(donde);
                if (!texto.equals(p.textos.get(i))) { a.customName(texto); p.textos.set(i, texto); }
                continue;
            }
            ArmorStand nuevo = w.spawn(donde, ArmorStand.class, a -> {
                a.setVisible(false);
                a.setMarker(true);
                a.setSmall(true);
                a.setGravity(false);
                a.setInvulnerable(true);
                a.setPersistent(false);
                a.setSilent(true);
                a.setBasePlate(false);
                a.setArms(false);
                a.setCanPickupItems(false);
                a.customName(texto);
                a.setCustomNameVisible(true);
                a.setVisibleByDefault(false);
                a.getPersistentDataContainer().set(clave, PersistentDataType.LONG, t.id);
            });
            if (i < p.lineas.size()) { p.lineas.set(i, nuevo.getUniqueId()); p.textos.set(i, texto); }
            else { p.lineas.add(nuevo.getUniqueId()); p.textos.add(texto); }
        }
    }

    private static Component precios(Tienda t) {
        Component c = Component.empty();
        boolean algo = false;
        if (t.puedeComprar()) { c = c.append(Component.text("Comprar: " + Msg.dinero(t.compra), NamedTextColor.GREEN)); algo = true; }
        if (t.puedeVender()) {
            if (algo) c = c.append(Component.text(" | ", NamedTextColor.DARK_GRAY));
            c = c.append(Component.text("Vender: " + Msg.dinero(t.venta), NamedTextColor.RED));
            algo = true;
        }
        if (!algo) c = Component.text("Tienda cerrada", NamedTextColor.GRAY);
        return c.decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false);
    }

    // ---------- visibilidad ----------

    /** El item: Java ve el ItemDisplay y Bedrock el item comun, desde lejos. El texto va aparte (mirada), solo para quien apunta a la tienda. */
    private void visibilidad() {
        if (piezas.isEmpty()) return;
        List<Player> jugs = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (jugs.isEmpty()) return;
        double rItem2 = Math.pow(pl.getConfig().getDouble("holograma.rango-aparicion", 14.0), 2);
        boolean[] bedrock = new boolean[jugs.size()];
        for (int j = 0; j < jugs.size(); j++) bedrock[j] = esBedrock(jugs.get(j));
        for (Pieza p : piezas.values()) {
            // las entidades de la pieza se buscan una sola vez, no una por jugador
            Entity drop = p.item == null ? null : Bukkit.getEntity(p.item);
            Entity visor = p.visor == null ? null : Bukkit.getEntity(p.visor);
            for (int j = 0; j < jugs.size(); j++) {
                Player jug = jugs.get(j);
                mostrarItem(jug, drop, bedrock[j], rItem2);
                mostrarItem(jug, visor, !bedrock[j], rItem2);
            }
        }
    }

    // ---------- mirada: el texto solo se ve en la tienda que apuntas ----------

    /** Cada 4 ticks: a cada jugador le muestra el texto de la tienda que mira (la mas centrada en su mira) y se lo saca al dejar de mirarla. */
    private void mirada() {
        try {
            if (piezas.isEmpty()) {
                if (!mirando.isEmpty()) { for (Map.Entry<UUID, Long> en : mirando.entrySet()) { Player j = Bukkit.getPlayer(en.getKey()); if (j != null) ocultarLineas(j, null); } mirando.clear(); }
                return;
            }
            double rango = pl.getConfig().getDouble("holograma.rango-texto", 6.0);
            double r2max = rango * rango;
            double margen = pl.getConfig().getDouble("holograma.mirada-margen", 1.4);
            for (Player jug : Bukkit.getOnlinePlayers()) {
                Location ojo = jug.getEyeLocation();
                Vector dir = ojo.getDirection();
                String mundo = ojo.getWorld().getName();
                Long actual = mirando.get(jug.getUniqueId());
                Long nuevo = null;
                // la que ya miraba se conserva mientras siga cerca de la mira (asi no parpadea en el borde)
                if (actual != null) {
                    Pieza pa = piezas.get(actual);
                    if (pa != null && puntaje(pa, mundo, ojo, dir, r2max, margen) >= 0) nuevo = actual;
                }
                if (nuevo == null) {
                    double mejor = Double.MAX_VALUE;
                    for (Map.Entry<Long, Pieza> en : piezas.entrySet()) {
                        double sc = puntaje(en.getValue(), mundo, ojo, dir, r2max, 1.0);
                        if (sc >= 0 && sc < mejor) { mejor = sc; nuevo = en.getKey(); }
                    }
                }
                if (actual != null && !actual.equals(nuevo)) ocultarLineas(jug, piezas.get(actual));
                if (nuevo == null) mirando.remove(jug.getUniqueId());
                else {
                    mirando.put(jug.getUniqueId(), nuevo);
                    mostrarLineas(jug, nuevo, piezas.get(nuevo));
                }
            }
            mirando.keySet().removeIf(u -> Bukkit.getPlayer(u) == null);
        } catch (Throwable e) {
            pl.getLogger().warning("Holograma (mirada): " + e);
        }
    }

    /** Que tan centrada esta la tienda en la mira (tangente al cuadrado del angulo), o -1 si no la apunta o esta lejos. */
    private static double puntaje(Pieza p, String mundo, Location ojo, Vector dir, double r2max, double margen) {
        if (!p.mundo.equals(mundo)) return -1;
        double a = punto(p.x, p.y, p.z, p.radio * margen, ojo, dir, r2max);
        double b = punto(p.ix, p.iy, p.iz, 0.6 * margen, ojo, dir, r2max);
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }

    private static double punto(double x, double y, double z, double radio, Location ojo, Vector dir, double r2max) {
        double vx = x - ojo.getX(), vy = y - ojo.getY(), vz = z - ojo.getZ();
        double dist2 = vx * vx + vy * vy + vz * vz;
        if (dist2 > r2max) return -1;
        double dot = vx * dir.getX() + vy * dir.getY() + vz * dir.getZ();
        if (dot <= 0.05) return -1;
        double perp2 = dist2 - dot * dot;
        if (perp2 > radio * radio) return -1;
        return perp2 / (dot * dot);
    }

    /** Muestra las lineas de la pieza que todavia no ve (una tras otra, de abajo hacia arriba), siempre que siga mirandola. */
    private void mostrarLineas(Player jug, long id, Pieza p) {
        if (p == null) return;
        for (int idx = 0; idx < p.lineas.size(); idx++) {
            final UUID u = p.lineas.get(idx);
            Entity e = Bukkit.getEntity(u);
            if (e == null || !e.isValid() || jug.canSee(e)) continue;
            String k = jug.getUniqueId() + ":" + u;
            if (pendientes.add(k)) {
                Bukkit.getScheduler().runTaskLater(pl, () -> {
                    pendientes.remove(k);
                    Entity e2 = Bukkit.getEntity(u);
                    Long m = mirando.get(jug.getUniqueId());
                    if (e2 != null && e2.isValid() && jug.isOnline() && m != null && m == id) aparecer(jug, e2);
                }, 1L + idx * 3L);
            }
        }
    }

    private void ocultarLineas(Player jug, Pieza p) {
        if (p == null) return;
        for (UUID u : p.lineas) {
            Entity e = Bukkit.getEntity(u);
            if (e != null && e.isValid() && jug.canSee(e)) jug.hideEntity(pl, e);
        }
    }

    private void mostrarItem(Player jugador, Entity e, boolean debe, double ignorado) {
        if (e == null || !e.isValid()) return;
        boolean ve = jugador.canSee(e);
        if (debe && !ve) aparecer(jugador, e);
        else if (!debe && ve) jugador.hideEntity(pl, e);
    }

    /**
     * Le muestra una pieza a un jugador deslizandola desde un poco mas abajo hasta su lugar (el mismo movimiento que se ve al
     * cambiar la posicion). Si otro jugador ya la esta viendo, la muestra directo para no moverla delante suyo.
     */
    private void aparecer(Player jug, Entity e) {
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.equals(jug) && o.canSee(e)) { jug.showEntity(pl, e); return; }
        }
        final double abajo = 0.6;
        final Location ini = e.getLocation().clone().add(0, -abajo, 0);
        e.teleport(ini);
        jug.showEntity(pl, e);
        for (int s = 1; s <= 4; s++) {
            final int paso = s;
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (e.isValid()) e.teleport(ini.clone().add(0, abajo * paso / 4.0, 0));
            }, s * 2L);
        }
    }

    // ---------- limpieza ----------

    private void quitarPieza(Pieza p) {
        List<UUID> todos = new ArrayList<>(p.lineas);
        if (p.item != null) todos.add(p.item);
        if (p.visor != null) todos.add(p.visor);
        for (UUID u : todos) {
            Entity e = Bukkit.getEntity(u);
            if (e != null) e.remove();
        }
    }

    /** Borra entidades de holograma que hayan quedado sueltas (por ejemplo tras un cierre brusco del server). */
    private void limpiarHuerfanas() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getPersistentDataContainer().has(clave, PersistentDataType.LONG)) e.remove();
            }
        }
    }

    // ---------- cuando se cierra el cofre de una tienda cambia el stock ----------

    @EventHandler(priority = EventPriority.MONITOR)
    public void alCerrar(InventoryCloseEvent e) {
        Tienda t = deInventario(e.getInventory());
        if (t != null) Bukkit.getScheduler().runTask(pl, () -> refrescar(t));
    }

    private Tienda deInventario(Inventory inv) {
        InventoryHolder h;
        try { h = inv.getHolder(false); } catch (Throwable x) { h = inv.getHolder(); }
        if (h instanceof DoubleChest dc) {
            Tienda a = deHolder(dc.getLeftSide(false));
            return a != null ? a : deHolder(dc.getRightSide(false));
        }
        return deHolder(h);
    }

    private Tienda deHolder(InventoryHolder h) {
        if (h instanceof BlockState bs) return pl.almacen().en(bs.getBlock());
        return null;
    }
}
