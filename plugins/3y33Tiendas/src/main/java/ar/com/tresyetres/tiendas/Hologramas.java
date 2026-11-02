package ar.com.tresyetres.tiendas;

import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.event.management.ShopDeleteEvent;
import com.ghostchu.quickshop.api.event.management.ShopLoadEvent;
import com.ghostchu.quickshop.api.event.management.ShopUnloadEvent;
import com.ghostchu.quickshop.api.shop.Shop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Holograma propio de cada tienda: el item flotante y el texto (nombre + precio) forman UNA sola pieza que se mueve junta.
 * Usa entidades comunes (item sin recoger + armor stands invisibles con nombre), que Geyser traduce bien para Bedrock.
 * Los carteles propios de QuickShop (display-items y text-display) tienen que estar apagados en su config.
 */
final class Hologramas implements Listener {
    private static final double TOPE_LADOS = 3.0, TOPE_ABAJO = -0.5, TOPE_ARRIBA = 3.0;

    /** Entidades de una tienda. */
    private static final class Pieza {
        UUID item, nombre, precio;
        String textoNombre = "", textoPrecio = "";
    }

    private final TiendasPlugin pl;
    private final NamespacedKey clave;
    private final Map<String, Pieza> piezas = new HashMap<>();
    private final File archivo;
    private final YamlConfiguration datos;

    Hologramas(TiendasPlugin pl) {
        this.pl = pl;
        this.clave = new NamespacedKey(pl, "holo");
        this.archivo = new File(pl.getDataFolder(), "holos.yml");
        this.datos = YamlConfiguration.loadConfiguration(archivo);
    }

    // ---------- datos por tienda ----------

    static String llave(Location l) {
        return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    Vector desplazamiento(Location l) {
        String k = "o." + llave(l).replace('.', '_');
        return new Vector(datos.getDouble(k + ".x"), datos.getDouble(k + ".y"), datos.getDouble(k + ".z"));
    }

    private void guardarDesplazamiento(Location l, Vector v) {
        String k = "o." + llave(l).replace('.', '_');
        if (Math.abs(v.getX()) < 1e-6 && Math.abs(v.getY()) < 1e-6 && Math.abs(v.getZ()) < 1e-6) datos.set(k, null);
        else { datos.set(k + ".x", v.getX()); datos.set(k + ".y", v.getY()); datos.set(k + ".z", v.getZ()); }
        try { datos.save(archivo); } catch (IOException e) { pl.getLogger().warning("No pude guardar holos.yml: " + e); }
    }

    /** Mueve el holograma de una tienda. Devuelve false si se pasaría del tope. */
    boolean mover(Shop<?, ?> shop, double dx, double dy, double dz) {
        Location l = shop.bukkitLocation();
        Vector v = desplazamiento(l).add(new Vector(dx, dy, dz));
        v.setX(Math.max(-TOPE_LADOS, Math.min(TOPE_LADOS, v.getX())));
        v.setZ(Math.max(-TOPE_LADOS, Math.min(TOPE_LADOS, v.getZ())));
        v.setY(Math.max(TOPE_ABAJO, Math.min(TOPE_ARRIBA, v.getY())));
        guardarDesplazamiento(l, v);
        sincronizar();
        return true;
    }

    void reiniciar(Shop<?, ?> shop) {
        guardarDesplazamiento(shop.bukkitLocation(), new Vector());
        sincronizar();
    }

    // ---------- sincronizacion ----------

    @SuppressWarnings({"unchecked", "rawtypes"})
    void sincronizar() {
        Set<String> vivas = new java.util.HashSet<>();
        for (Shop s : (Set<Shop>) (Set) QuickShopAPI.getInstance().getShopManager().getLoadedShops()) {
            try {
                Location l = s.bukkitLocation();
                if (l == null || l.getWorld() == null || !s.isValid()) continue;
                if (!l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) continue;
                ItemStack it = s.getItem();
                if (it == null) continue;
                String k = llave(l);
                vivas.add(k);
                poner(s, k, l, it);
            } catch (Throwable t) {
                pl.getLogger().warning("Holograma de tienda: " + t);
            }
        }
        for (Iterator<Map.Entry<String, Pieza>> i = piezas.entrySet().iterator(); i.hasNext(); ) {
            Map.Entry<String, Pieza> e = i.next();
            if (!vivas.contains(e.getKey())) { quitar(e.getValue()); i.remove(); }
        }
    }

    private void poner(Shop<?, ?> s, String k, Location bloque, ItemStack tienda) {
        World w = bloque.getWorld();
        Vector o = desplazamiento(bloque);
        double h = pl.getConfig().getDouble("altura-item", 1.3);
        Location base = new Location(w, bloque.getBlockX() + 0.5 + o.getX(), bloque.getBlockY() + h + o.getY(), bloque.getBlockZ() + 0.5 + o.getZ());
        Location locNombre = base.clone().add(0, pl.getConfig().getDouble("altura-nombre", 0.4), 0);
        Location locPrecio = base.clone().add(0, pl.getConfig().getDouble("altura-precio", 0.1), 0);

        Pieza p = piezas.computeIfAbsent(k, x -> new Pieza());

        // item flotante
        Entity ei = p.item == null ? null : Bukkit.getEntity(p.item);
        if (ei == null || !ei.isValid()) {
            ItemStack uno = tienda.clone();
            uno.setAmount(1);
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
                i.getPersistentDataContainer().set(clave, PersistentDataType.STRING, k);
            });
            p.item = nuevo.getUniqueId();
        } else {
            if (ei.getLocation().distanceSquared(base) > 0.0004) { ei.teleport(base); ei.setVelocity(new Vector()); }
            if (ei instanceof Item it && !it.getItemStack().isSimilar(tienda)) {
                ItemStack uno = tienda.clone();
                uno.setAmount(1);
                it.setItemStack(uno);
            }
        }

        // texto: nombre y precio
        Component nombre = tienda.effectiveName().colorIfAbsent(NamedTextColor.LIGHT_PURPLE)
                .color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false);
        Component precio = precioTexto(s, tienda);
        String tn = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(nombre);
        String tp = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(precio);
        p.nombre = linea(p.nombre, locNombre, nombre, p.textoNombre.equals(tn), k);
        p.precio = linea(p.precio, locPrecio, precio, p.textoPrecio.equals(tp), k);
        p.textoNombre = tn;
        p.textoPrecio = tp;
    }

    private UUID linea(UUID actual, Location donde, Component texto, boolean igual, String k) {
        Entity e = actual == null ? null : Bukkit.getEntity(actual);
        if (e instanceof ArmorStand a && a.isValid()) {
            if (a.getLocation().distanceSquared(donde) > 0.0004) a.teleport(donde);
            if (!igual) a.customName(texto);
            return actual;
        }
        ArmorStand nuevo = donde.getWorld().spawn(donde, ArmorStand.class, a -> {
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
            a.getPersistentDataContainer().set(clave, PersistentDataType.STRING, k);
        });
        return nuevo.getUniqueId();
    }

    private Component precioTexto(Shop<?, ?> s, ItemStack tienda) {
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "AR"));
        nf.setMaximumFractionDigits(2);
        nf.setMinimumFractionDigits(0);
        String precio = "$" + nf.format(s.getPrice());
        int cant = Math.max(1, tienda.getAmount());
        String sufijo = cant == 1 ? " c/u" : " x" + cant;
        boolean compra = false;
        try { compra = s.shopType().isBuying(); } catch (Throwable ignored) { }
        Component c = Component.text((compra ? "Compro " : "") + precio + sufijo)
                .color(compra ? NamedTextColor.GOLD : NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true);
        return c;
    }

    private void quitar(Pieza p) {
        for (UUID u : new UUID[]{p.item, p.nombre, p.precio}) {
            if (u == null) continue;
            Entity e = Bukkit.getEntity(u);
            if (e != null) e.remove();
        }
    }

    void quitarTodo() {
        for (Pieza p : piezas.values()) quitar(p);
        piezas.clear();
    }

    /** Borra entidades de holograma que hayan quedado sueltas (por ejemplo tras un reinicio brusco). */
    void limpiarHuerfanas() {
        java.util.Set<UUID> conocidas = new java.util.HashSet<>();
        for (Pieza p : piezas.values()) { if (p.item != null) conocidas.add(p.item); if (p.nombre != null) conocidas.add(p.nombre); if (p.precio != null) conocidas.add(p.precio); }
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getPersistentDataContainer().has(clave, PersistentDataType.STRING) && !conocidas.contains(e.getUniqueId())) e.remove();
            }
        }
    }

    /** El texto solo lo ve quien esta cerca (si no se amontonan los carteles de todas las tiendas, incluso a traves de paredes). */
    void visibilidad() {
        double r2 = Math.pow(pl.getConfig().getDouble("rango-texto", 6.0), 2);
        for (Pieza p : piezas.values()) {
            Entity[] textos = {p.nombre == null ? null : Bukkit.getEntity(p.nombre), p.precio == null ? null : Bukkit.getEntity(p.precio)};
            for (Entity t : textos) {
                if (t == null || !t.isValid()) continue;
                for (Player pl2 : Bukkit.getOnlinePlayers()) {
                    boolean cerca = pl2.getWorld().equals(t.getWorld()) && pl2.getLocation().distanceSquared(t.getLocation()) <= r2;
                    boolean ve = pl2.canSee(t);
                    if (cerca && !ve) pl2.showEntity(pl, t);
                    else if (!cerca && ve) pl2.hideEntity(pl, t);
                }
            }
        }
    }

    // ---------- eventos de QuickShop ----------

    private void luego() { Bukkit.getScheduler().runTask(pl, this::sincronizar); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alCargar(ShopLoadEvent e) { luego(); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alDescargar(ShopUnloadEvent e) { luego(); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alBorrar(ShopDeleteEvent e) { luego(); }

    List<String> nombresAcciones() { return new ArrayList<>(List.of("izquierda", "derecha", "adelante", "atras", "subir", "bajar", "reiniciar")); }
}
