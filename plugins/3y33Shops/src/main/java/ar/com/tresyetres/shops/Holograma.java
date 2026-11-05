package ar.com.tresyetres.shops;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
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
        final List<UUID> lineas = new ArrayList<>();
        final List<Component> textos = new ArrayList<>();
    }

    private final ShopsPlugin pl;
    private final NamespacedKey clave;
    private final Map<Long, Pieza> piezas = new HashMap<>();
    private BukkitTask tarea;
    private int ciclos;
    private final Set<String> pendientes = new HashSet<>();

    Holograma(ShopsPlugin pl) {
        this.pl = pl;
        this.clave = new NamespacedKey(pl, "holo");
    }

    void iniciar() {
        limpiarHuerfanas();
        tarea = Bukkit.getScheduler().runTaskTimer(pl, this::ciclo, 40L, 20L);
    }

    void detener() {
        if (tarea != null) tarea.cancel();
        for (Pieza p : piezas.values()) quitarPieza(p);
        piezas.clear();
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

    /** Fase del vaiven suave del item: cambia cada 2 segundos. */
    private int fase() { return (ciclos / 2) % 2; }

    private void ciclo() {
        ciclos++;
        try {
            double aparece = Math.pow(pl.getConfig().getDouble("holograma.rango-aparicion", 14.0), 2);
            double desaparece = Math.pow(pl.getConfig().getDouble("holograma.rango-desaparicion", 24.0), 2);
            Set<Long> vivas = new HashSet<>();
            for (Tienda t : pl.almacen().todas()) {
                vivas.add(t.id);
                World w = t.mundoObj();
                if (w == null || !w.isChunkLoaded(t.x >> 4, t.z >> 4)) { quitar(t.id); continue; }
                Location c = centro(t, w);
                double min = Double.MAX_VALUE;
                for (Player p : w.getPlayers()) min = Math.min(min, p.getLocation().distanceSquared(c));
                if (min <= aparece) poner(t, w, c);
                else if (min > desaparece) quitar(t.id);
            }
            for (Long id : new ArrayList<>(piezas.keySet())) if (!vivas.contains(id)) quitar(id);
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

    /** Donde queda el holograma segun la posicion elegida: arriba del cofre, pegado a un costado, o abajo. */
    private Location anclaje(Tienda t, Location centro) {
        double h = pl.getConfig().getDouble("holograma.altura-item", 1.1);
        double margen = pl.getConfig().getDouble("holograma.distancia-lateral", 0.6);
        double altoLado = pl.getConfig().getDouble("holograma.altura-lateral", 0.15);
        double abajo = pl.getConfig().getDouble("holograma.altura-abajo", -1.7);
        // un cofre doble es mas ancho en su eje: el holograma tiene que quedar fuera del cofre entero
        double ex = t.doble && t.x != t.x2 ? (Math.abs(t.x - t.x2) + 1) / 2.0 : 0.5;
        double ez = t.doble && t.z != t.z2 ? (Math.abs(t.z - t.z2) + 1) / 2.0 : 0.5;
        String r = t.rotacion == null ? "ARRIBA" : t.rotacion;
        return switch (r) {
            case "NORTE" -> centro.clone().add(0, altoLado, -(ez + margen));
            case "SUR" -> centro.clone().add(0, altoLado, ez + margen);
            case "ESTE" -> centro.clone().add(ex + margen, altoLado, 0);
            case "OESTE" -> centro.clone().add(-(ex + margen), altoLado, 0);
            case "ABAJO" -> centro.clone().add(0, abajo, 0);
            default -> centro.clone().add(0, h, 0);
        };
    }

    private void poner(Tienda t, World w, Location bloque) {
        org.bukkit.inventory.Inventory inv = Operaciones.inventario(t);
        if (inv == null) return;
        int stock = Operaciones.contar(inv, t.item);
        Location base = anclaje(t, bloque);
        Pieza p = piezas.computeIfAbsent(t.id, x -> new Pieza());
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
                d.setInterpolationDuration(10);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0f, 0f, 0f), new Quaternionf()));
                d.getPersistentDataContainer().set(clave, PersistentDataType.LONG, t.id);
            });
            p.visor = nuevo.getUniqueId();
            p.listo = false;
            ev = nuevo;
            final UUID idv = nuevo.getUniqueId();
            // un instante despues crece hasta su tamaño: asi no aparece de golpe
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (Bukkit.getEntity(idv) instanceof ItemDisplay d2 && d2.isValid()) {
                    int f = fase();
                    d2.setTransformation(new Transformation(new Vector3f(0f, f == 0 ? -0.05f : 0.05f, 0f), new Quaternionf(), new Vector3f(escala, escala, escala), new Quaternionf()));
                    d2.setInterpolationDelay(0);
                    p.fase = f;
                    p.listo = true;
                }
            }, 3L);
        }
        if (ev instanceof ItemDisplay idp) {
            if (idp.getLocation().distanceSquared(destino) > 0.0004) idp.teleport(destino);
            if (!idp.getItemStack().isSimilar(uno)) idp.setItemStack(uno);
            int f = fase();
            if (p.listo && p.fase != f) {
                // vaiven suave: sube y baja unos centimetros, interpolado por el cliente
                idp.setInterpolationDuration(40);
                idp.setTransformation(new Transformation(new Vector3f(0f, f == 0 ? -0.05f : 0.05f, 0f), new Quaternionf(), new Vector3f(escala, escala, escala), new Quaternionf()));
                idp.setInterpolationDelay(0);
                p.fase = f;
            }
        }

        // texto, de abajo hacia arriba
        List<Component> textos = new ArrayList<>();
        textos.add(t.item.effectiveName().color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false));
        textos.add(precios(t));
        if (stock == 0 && t.puedeComprar()) textos.add(Component.text("Esta tienda está vacía!", NamedTextColor.RED, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        if (!t.mensaje.isBlank()) textos.add(LegacyComponentSerializer.legacyAmpersand().deserialize(t.mensaje).decoration(TextDecoration.ITALIC, false));

        double sep = pl.getConfig().getDouble("holograma.separacion-lineas", 0.26);
        double arranque = pl.getConfig().getDouble("holograma.altura-texto", 0.35);
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

    /** El texto solo lo ve quien esta cerca. El item: Java ve el ItemDisplay y Bedrock el item comun. */
    private void visibilidad() {
        double r2 = Math.pow(pl.getConfig().getDouble("holograma.rango-texto", 6.0), 2);
        double rItem2 = Math.pow(pl.getConfig().getDouble("holograma.rango-aparicion", 14.0), 2);
        for (Pieza p : piezas.values()) {
            Entity drop = p.item == null ? null : Bukkit.getEntity(p.item);
            Entity visor = p.visor == null ? null : Bukkit.getEntity(p.visor);
            for (Player jug : Bukkit.getOnlinePlayers()) {
                int idx = 0;
                for (UUID u : p.lineas) {
                    Entity t = Bukkit.getEntity(u);
                    if (t == null || !t.isValid()) { idx++; continue; }
                    boolean cerca = jug.getWorld().equals(t.getWorld()) && jug.getLocation().distanceSquared(t.getLocation()) <= r2;
                    boolean ve = jug.canSee(t);
                    if (cerca && !ve) {
                        // las lineas aparecen una tras otra, de abajo hacia arriba
                        String k = jug.getUniqueId() + ":" + u;
                        if (pendientes.add(k)) {
                            Bukkit.getScheduler().runTaskLater(pl, () -> {
                                pendientes.remove(k);
                                Entity e2 = Bukkit.getEntity(u);
                                if (e2 != null && e2.isValid() && jug.isOnline()) jug.showEntity(pl, e2);
                            }, 1L + idx * 3L);
                        }
                    } else if (!cerca && ve) {
                        jug.hideEntity(pl, t);
                    }
                    idx++;
                }
                boolean bedrock = esBedrock(jug);
                mostrarItem(jug, drop, bedrock, rItem2);
                mostrarItem(jug, visor, !bedrock, rItem2);
            }
        }
    }

    /** Muestra u oculta el item; al aparecerle a alguien cerca suelta unas chispitas para que no sea tan brusco. */
    private void mostrarItem(Player jugador, Entity e, boolean debe, double rChispas2) {
        if (e == null || !e.isValid()) return;
        boolean ve = jugador.canSee(e);
        if (debe && !ve) {
            jugador.showEntity(pl, e);
            if (jugador.getWorld().equals(e.getWorld()) && jugador.getLocation().distanceSquared(e.getLocation()) <= rChispas2) {
                jugador.spawnParticle(Particle.END_ROD, e.getLocation().add(0, 0.1, 0), 6, 0.18, 0.18, 0.18, 0.01);
            }
        } else if (!debe && ve) {
            jugador.hideEntity(pl, e);
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
