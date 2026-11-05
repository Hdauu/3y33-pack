package ar.com.tresyetres.shops;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Control de que las tiendas y los puntos de viaje sigan siendo legitimos: una tienda solo vale mientras esta dentro de una
 * tierra donde su dueño sigue siendo miembro. Si no, se suspende (no se puede usar ni aparece en el buscador) hasta que se arregle,
 * y los puntos de viaje que ya no estan dentro de su tierra se borran. Asi nadie puede usar tiendas o puntos como teletransportes gratis.
 */
final class Integridad implements Listener {
    private static final int FALLAS_PARA_BORRAR = 3;

    private final ShopsPlugin pl;
    private final Map<String, Integer> fallas = new HashMap<>();

    /** Pasadas seguidas en que cada tienda salio mal: se suspende recien a la segunda, para no avisar por un dato que Lands todavia estaba cargando. */
    private final Map<Long, Integer> dudas = new HashMap<>();
    private final long inicio = System.currentTimeMillis();

    Integridad(ShopsPlugin pl) { this.pl = pl; }

    void iniciar() {
        long cada = pl.getConfig().getLong("integridad.revisar-cada-segundos", 120) * 20L;
        Bukkit.getScheduler().runTaskTimer(pl, () -> {
            try { revisarTodas(); } catch (Throwable e) { pl.getLogger().warning("Integridad: " + e); }
        }, 1800L, Math.max(200L, cada));
    }

    /** null si la tienda esta en regla; si no, el motivo de la suspension. No cambia nada. */
    String motivo(Tienda t) {
        if (!pl.lands().disponible()) return null;      // sin Lands no se puede decidir: no se suspende nada
        // justo despues de arrancar Lands todavia esta cargando las tierras: no se decide nada (se mantiene lo que ya habia)
        if (System.currentTimeMillis() - inicio < 90_000L) return t.suspension;
        if (t.mundoObj() == null) return "el mundo de la tienda no está disponible";
        LandsPuente.Terreno ter = pl.lands().enONoSe(t.ubicacion(), t.dueno);
        if (ter == LandsPuente.DESCONOCIDO) return t.suspension;     // Lands fallo al contestar: no se cambia el estado
        if (ter == null) return "ya no está dentro de ninguna tierra";
        if (!ter.miembro()) return "su dueño ya no es miembro de la tierra " + ter.nombre();
        return null;
    }

    /** Un punto de viaje es valido si sigue dentro de la tierra a la que pertenece. */
    boolean puntoValido(Location loc, String tierra) {
        if (loc == null || loc.getWorld() == null) return false;
        LandsPuente.Terreno ter = pl.lands().en(loc, null);
        return ter != null && ter.nombre().equalsIgnoreCase(tierra);
    }

    /** Revisa todas las tiendas y puntos. Devuelve {suspendidas, eliminadas, puntosBorrados}. */
    int[] revisarTodas() {
        int suspendidas = 0, eliminadas = 0, puntos = 0;
        if (!pl.lands().disponible()) return new int[]{0, 0, 0};
        for (Tienda t : pl.almacen().todas()) {
            World w = t.mundoObj();
            // el cofre ya no esta (solo se mira si el chunk esta cargado, para no cargar nada)
            if (w != null && w.isChunkLoaded(t.x >> 4, t.z >> 4) && Operaciones.inventario(t) == null) {
                pl.getLogger().info("Tienda " + t.id + " eliminada: el cofre ya no existe (" + t.mundo + " " + t.x + "," + t.y + "," + t.z + ")");
                pl.almacen().borrar(t);
                eliminadas++;
                continue;
            }
            if (w != null && w.isChunkLoaded(t.x >> 4, t.z >> 4) && !Operaciones.contenedorPermitido(t.bloque().getType())) {
                pl.getLogger().info("Tienda " + t.id + " eliminada: el contenedor " + t.bloque().getType() + " no esta permitido (" + t.mundo + " " + t.x + "," + t.y + "," + t.z + ")");
                Player dueno = Bukkit.getPlayer(t.dueno);
                if (dueno != null) Msg.error(dueno, "Se borró tu tienda de " + VentanaTienda.nombreItem(t) + " (" + t.x + ", " + t.y + ", " + t.z + "): las tiendas solo pueden estar en cofres o barriles.");
                pl.almacen().borrar(t);
                eliminadas++;
                continue;
            }
            String m = motivo(t);
            if (m == null) {
                LandsPuente.Terreno ter = pl.lands().en(t.ubicacion(), t.dueno);
                if (ter != null && !ter.nombre().equalsIgnoreCase(t.tierra)) {
                    // la tierra cambio de nombre: la tienda, el perfil y el punto de la tierra la siguen
                    pl.almacen().renombrarTierra(t.tierra, ter.nombre());
                    t.tierra = ter.nombre();
                    pl.almacen().guardar(t);
                }
            } else {
                suspendidas++;
            }
            boolean estabaSuspendida = t.suspension != null;
            if (m != null && !estabaSuspendida && dudas.merge(t.id, 1, Integer::sum) < 2) continue;   // primera vez que sale mal: se espera a la proxima pasada
            if (m == null || estabaSuspendida) dudas.remove(t.id);
            t.suspension = m;
            if (estabaSuspendida != (m != null)) {
                pl.getLogger().info("Tienda " + t.id + (m != null ? " suspendida: " + m : " rehabilitada") + " (" + t.mundo + " " + t.x + "," + t.y + "," + t.z + ")");
                cambioDeEstado(t, m);
            }
        }
        for (Map.Entry<String, Almacen.Punto> e : pl.almacen().todosPuntos()) {
            String clave = e.getKey();
            if (clave.startsWith("S:")) { pl.almacen().borrarPunto(clave); puntos++; continue; }   // puntos por tienda de versiones viejas
            String tierra = clave.substring(2);
            if (!hayTiendas(tierra)) { pl.almacen().borrarPunto(clave); puntos++; continue; }       // tierra sin tiendas: no queda punto suelto
            Location loc = pl.puntos().ubicacion(clave);
            if (loc == null) continue;      // mundo no cargado: no se decide
            if (puntoValido(loc, tierra)) { fallas.remove(clave); continue; }
            int n = fallas.merge(clave, 1, Integer::sum);
            if (n >= FALLAS_PARA_BORRAR) {
                pl.getLogger().info("Punto de viaje " + clave + " borrado: ya no esta dentro de la tierra " + tierra);
                pl.almacen().borrarPunto(clave);
                fallas.remove(clave);
                puntos++;
            }
        }
        return new int[]{suspendidas, eliminadas, puntos};
    }

    private boolean hayTiendas(String tierra) {
        for (Tienda t : pl.almacen().todas()) if (t.tierra.equalsIgnoreCase(tierra)) return true;
        return false;
    }

    private void cambioDeEstado(Tienda t, String motivo) {
        if (pl.holograma() != null) pl.holograma().refrescar(t);
        Player dueno = Bukkit.getPlayer(t.dueno);
        if (dueno == null) return;
        String item = VentanaTienda.nombreItem(t);
        if (motivo != null) Msg.error(dueno, "Tu tienda de " + item + " (" + t.x + ", " + t.y + ", " + t.z + ") quedó suspendida: " + motivo + ".");
        else Msg.ok(dueno, "Tu tienda de " + item + " (" + t.x + ", " + t.y + ", " + t.z + ") volvió a funcionar.");
    }

    /** Al entrar, el dueño se entera si tiene tiendas suspendidas. */
    @EventHandler
    public void alEntrar(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline()) return;
            List<Tienda> malas = new ArrayList<>();
            for (Tienda t : pl.almacen().todas()) if (t.dueno.equals(p.getUniqueId()) && motivo(t) != null) malas.add(t);
            if (malas.isEmpty()) return;
            Msg.error(p, "Tenés " + malas.size() + (malas.size() == 1 ? " tienda suspendida" : " tiendas suspendidas") + ". Nadie puede comprar ni vender ahí hasta que vuelvan a estar dentro de una tierra tuya.");
        }, 100L);
    }
}
