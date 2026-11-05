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
        final List<UUID> lineas = new ArrayList<>();
        final List<Component> textos = new ArrayList<>();
    }

    private final ShopsPlugin pl;
    private final NamespacedKey clave;
    private final Map<Long, Pieza> piezas = new HashMap<>();
    private BukkitTask tarea;

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

    private void ciclo() {
        try {
            double aparece = Math.pow(pl.getConfig().getDouble("holograma.rango-aparicion", 24.0), 2);
            double desaparece = Math.pow(pl.getConfig().getDouble("holograma.rango-desaparicion", 40.0), 2);
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

    private static Quaternionf giro(String rot) {
        Quaternionf q = new Quaternionf();
        switch (rot == null ? "SUR" : rot) {
            case "NORTE" -> q.rotateY((float) Math.toRadians(180));
            case "ESTE" -> q.rotateY((float) Math.toRadians(90));
            case "OESTE" -> q.rotateY((float) Math.toRadians(-90));
            case "ARRIBA" -> q.rotateX((float) Math.toRadians(-90));
            case "ABAJO" -> q.rotateX((float) Math.toRadians(90));
            default -> { }
        }
        return q;
    }

    private void poner(Tienda t, World w, Location bloque) {
        org.bukkit.inventory.Inventory inv = Operaciones.inventario(t);
        if (inv == null) return;
        int stock = Operaciones.contar(inv, t.item);
        double h = pl.getConfig().getDouble("holograma.altura-item", 1.3);
        Location base = bloque.clone().add(0, h, 0);
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
                d.setBrightness(new Display.Brightness(15, 15));
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                d.setViewRange(0.6f);
                d.getPersistentDataContainer().set(clave, PersistentDataType.LONG, t.id);
            });
            p.visor = nuevo.getUniqueId();
            ev = nuevo;
        }
        if (ev instanceof ItemDisplay idp) {
            if (idp.getLocation().distanceSquared(destino) > 0.0004) idp.teleport(destino);
            if (!idp.getItemStack().isSimilar(uno)) idp.setItemStack(uno);
            idp.setTransformation(new Transformation(new Vector3f(), giro(t.rotacion), new Vector3f(escala, escala, escala), new Quaternionf()));
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
        for (Pieza p : piezas.values()) {
            Entity drop = p.item == null ? null : Bukkit.getEntity(p.item);
            Entity visor = p.visor == null ? null : Bukkit.getEntity(p.visor);
            for (Player jug : Bukkit.getOnlinePlayers()) {
                for (UUID u : p.lineas) {
                    Entity t = Bukkit.getEntity(u);
                    if (t == null || !t.isValid()) continue;
                    boolean cerca = jug.getWorld().equals(t.getWorld()) && jug.getLocation().distanceSquared(t.getLocation()) <= r2;
                    mostrar(jug, t, cerca);
                }
                boolean bedrock = esBedrock(jug);
                mostrar(jug, drop, bedrock);
                mostrar(jug, visor, !bedrock);
            }
        }
    }

    private void mostrar(Player jugador, Entity e, boolean debe) {
        if (e == null || !e.isValid()) return;
        boolean ve = jugador.canSee(e);
        if (debe && !ve) jugador.showEntity(pl, e);
        else if (!debe && ve) jugador.hideEntity(pl, e);
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
