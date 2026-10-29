package ar.com.tresyetres.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;

/** Arma los hologramas de bienvenida alrededor del spawn de Essentials (se acomodan después con /editor). */
final class Spawn {
    private static String pref = "spawn_";

    private Spawn() { }

    static Location punto() {
        File f = new File(Bukkit.getPluginsFolder(), "Essentials/spawn.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        String w = y.getString("spawns.default.world-name");
        World mundo = w == null ? null : Bukkit.getWorld(w);
        if (mundo == null) mundo = Bukkit.getWorlds().get(0);
        if (!y.contains("spawns.default.x")) return mundo.getSpawnLocation();
        return new Location(mundo, y.getDouble("spawns.default.x"), y.getDouble("spawns.default.y"),
                y.getDouble("spawns.default.z"), (float) y.getDouble("spawns.default.yaw"), 0f);
    }

    /** Devuelve cuántos hologramas creó. */
    static int armar(Core core, Player quien) { return armar(core, quien, null); }

    /** mundo = null usa el spawn de Essentials; si no, el spawn de ese mundo (hologramas con prefijo del mundo). */
    static int armar(Core core, Player quien, World mundo) {
        Location sp = mundo == null ? punto() : mundo.getSpawnLocation();
        pref = mundo == null ? "spawn_" : "beta_";
        double yaw = Math.toRadians(sp.getYaw());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // adelante
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);  // derecha
        int creados = 0;
        // bienvenida grande al frente y el resto en arco
        creados += poner(core, quien, sp, fx, fz, rx, rz, 12, 0, 4.2, "bienvenida");
        List<String> arco = List.of("terrenos", "misiones", "niveles", "reglas", "explorar", "discord");
        double[] angulos = {-75, -45, -15, 15, 45, 75};
        for (int i = 0; i < arco.size(); i++) {
            double a = Math.toRadians(angulos[i]);
            double f = 8 * Math.cos(a), r = 8 * Math.sin(a);
            creados += poner(core, quien, sp, fx, fz, rx, rz, f, r, 2.6, arco.get(i));
        }
        return creados;
    }

    private static int poner(Core core, Player quien, Location sp, double fx, double fz, double rx, double rz,
                             double f, double r, double alto, String plantilla) {
        String nombre = pref + plantilla;
        try {
            var m = de.oliver.fancyholograms.api.FancyHologramsPlugin.get().getHologramManager();
            if (m.getHologram(nombre).isPresent()) return 0;
        } catch (Throwable e) {
            return 0;
        }
        Location l = sp.clone().add(fx * f + rx * r, alto, fz * f + rz * r);
        return Plantillas.crearEn(core, quien, l, plantilla, nombre) != null ? 1 : 0;
    }
}
