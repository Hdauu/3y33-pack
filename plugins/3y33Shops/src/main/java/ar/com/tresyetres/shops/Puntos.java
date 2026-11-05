package ar.com.tresyetres.shops;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Punto de viaje de una tierra (uno solo, compartido por todas sus tiendas): adonde te lleva /tienda buscar. Se arma en dos pasos: elegir y confirmar parado en el lugar. */
final class Puntos implements Listener {
    private record Pendiente(String clave, String tierra, boolean deTierra, long tiendaId, long vence) { }

    private final ShopsPlugin pl;
    private final Map<UUID, Pendiente> pendientes = new HashMap<>();

    Puntos(ShopsPlugin pl) { this.pl = pl; }

    static String claveTierra(String tierra) { return "T:" + tierra.toLowerCase(Locale.ROOT); }

    boolean esDuenoDeTierra(Player p, LandsPuente.Terreno ter) {
        return p.hasPermission("tresyetres.staff") || (ter != null && p.getUniqueId().equals(ter.dueno()));
    }

    // ---------- paso 1: elegir ----------

    void iniciarTierra(Player p, Tienda t) {
        LandsPuente.Terreno ter = pl.lands().en(t.ubicacion(), p.getUniqueId());
        if (!esDuenoDeTierra(p, ter)) { Msg.error(p, "Solo el dueño de la tierra puede cambiar el punto de toda la tierra."); return; }
        pendientes.put(p.getUniqueId(), new Pendiente(claveTierra(t.tierra), t.tierra, true, 0, System.currentTimeMillis() + 300_000));
        p.closeInventory();
        Msg.info(p, "Andá al lugar donde querés que lleguen los jugadores a " + t.tierra + " y escribí /tienda punto confirmar (o /tienda punto cancelar).");
    }

    // ---------- paso 2: confirmar parado en el lugar ----------

    void confirmar(Player p) {
        Pendiente pend = pendientes.remove(p.getUniqueId());
        if (pend == null || System.currentTimeMillis() > pend.vence()) { Msg.error(p, "No tenés ningún punto pendiente. Empezalo desde los ajustes de la tienda o el perfil de la tierra."); return; }
        Location loc = p.getLocation();
        LandsPuente.Terreno ter = pl.lands().en(loc, p.getUniqueId());
        if (ter == null || !ter.nombre().equalsIgnoreCase(pend.tierra())) { Msg.error(p, "Ese lugar no está dentro de la tierra " + pend.tierra() + ". Probá de nuevo."); return; }
        if (!esDuenoDeTierra(p, ter)) { Msg.error(p, "Solo el dueño de la tierra puede cambiar este punto."); return; }
        String problema = problemaDelLugar(loc);
        if (problema != null) { Msg.error(p, problema); return; }
        pl.almacen().guardarPunto(pend.clave(), loc);
        p.spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 1, 0), 12, 0.4, 0.5, 0.4, 0);
        Msg.ok(p, "Punto de viaje guardado para la tierra " + pend.tierra() + ": todas sus tiendas llevan acá.");
    }

    void cancelar(Player p) {
        if (pendientes.remove(p.getUniqueId()) != null) Msg.info(p, "Cancelado.");
        else Msg.info(p, "No tenías nada pendiente.");
    }

    /** Borra el punto de viaje de la tierra donde estás parado. */
    void borrar(Player p) {
        LandsPuente.Terreno ter = pl.lands().en(p.getLocation(), p.getUniqueId());
        if (ter == null) { Msg.error(p, "Tenés que estar parado dentro de la tierra."); return; }
        borrarDeTierra(p, ter.nombre(), ter);
    }

    void borrarDeTierra(Player p, String tierra, LandsPuente.Terreno ter) {
        if (!esDuenoDeTierra(p, ter)) { Msg.error(p, "Solo el dueño de la tierra puede borrar este punto."); return; }
        if (pl.almacen().punto(claveTierra(tierra)) == null) { Msg.info(p, "Esa tierra no tiene punto de viaje."); return; }
        pl.almacen().borrarPunto(claveTierra(tierra));
        Msg.ok(p, "Borraste el punto de viaje de la tierra " + tierra + ".");
    }

    /** null si el lugar sirve para recibir jugadores; si no, el motivo. */
    private static String problemaDelLugar(Location l) {
        Block pies = l.getBlock();
        Block cabeza = pies.getRelative(0, 1, 0);
        Block suelo = pies.getRelative(0, -1, 0);
        if (pies.isLiquid() || cabeza.isLiquid() || suelo.isLiquid()) return "Ahí hay agua o lava. Elegí un lugar seco.";
        if (!pies.isPassable() || !cabeza.isPassable()) return "Ahí no hay lugar para parar: tiene que haber 2 bloques libres.";
        if (!suelo.getType().isSolid()) return "Ahí no hay suelo firme abajo.";
        return null;
    }

    // ---------- lectura ----------

    /** El punto guardado con esa clave, ya como ubicacion (null si no hay o el mundo no esta cargado). */
    Location ubicacion(String clave) {
        Almacen.Punto pt = pl.almacen().punto(clave);
        if (pt == null) return null;
        org.bukkit.World w = org.bukkit.Bukkit.getWorld(pt.mundo());
        return w == null ? null : new Location(w, pt.x(), pt.y(), pt.z(), pt.yaw(), pt.pitch());
    }

    String texto(String clave) {
        Almacen.Punto pt = pl.almacen().punto(clave);
        return pt == null ? null : Math.round(pt.x()) + ", " + Math.round(pt.y()) + ", " + Math.round(pt.z());
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent e) { pendientes.remove(e.getPlayer().getUniqueId()); }
}
