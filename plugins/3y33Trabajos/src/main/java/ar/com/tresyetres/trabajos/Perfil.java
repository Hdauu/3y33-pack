package ar.com.tresyetres.trabajos;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Todo lo de un jugador: trabajo activo, progreso de cada trabajo y estado temporal (momentum, topes). */
final class Perfil {
    static final int NIVEL_MAX = 100;

    static final class Progreso {
        int nivel = 1;
        double xp = 0;
        int prestigio = 0;
    }

    final UUID id;
    String nombre;
    Trabajo activo;
    /** Hasta cuando no puede tomar un trabajo (penalizacion por abandonar). */
    long libreHasta;
    boolean tuvoTrabajo;
    /** Plata ganada que todavia no llego a un centavo. */
    double pendiente;
    final Map<Trabajo, Progreso> prog = new EnumMap<>(Trabajo.class);

    // temporal, no se guarda
    double momentum = 1.0;
    long momentumT;
    long ultimoMov = System.currentTimeMillis();
    long ultimoPagoTiempo;
    int accionesVentana;
    private final Map<String, long[]> topes = new HashMap<>();

    Perfil(UUID id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }

    Progreso de(Trabajo t) { return prog.computeIfAbsent(t, k -> new Progreso()); }

    /** true si ya llego al tope por hora de esa clave; si no, cuenta una mas. */
    boolean tope(String clave, int max, long ahora) {
        long[] v = topes.computeIfAbsent(clave, k -> new long[]{ahora, 0});
        if (ahora - v[0] > 3_600_000L) { v[0] = ahora; v[1] = 0; }
        if (v[1] >= max) return true;
        v[1]++;
        return false;
    }

    void cargar(File f) {
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        String a = y.getString("activo");
        if (a != null) { try { activo = Trabajo.valueOf(a); } catch (IllegalArgumentException ignorada) { } }
        libreHasta = y.getLong("libre-hasta", 0L);
        tuvoTrabajo = y.getBoolean("tuvo-trabajo", false);
        pendiente = y.getDouble("pendiente", 0.0);
        ConfigurationSection s = y.getConfigurationSection("progreso");
        if (s != null) {
            for (Trabajo t : Trabajo.values()) {
                ConfigurationSection c = s.getConfigurationSection(t.name());
                if (c == null) continue;
                Progreso g = de(t);
                g.nivel = Math.max(1, Math.min(NIVEL_MAX, c.getInt("nivel", 1)));
                g.xp = c.getDouble("xp", 0);
                g.prestigio = Math.max(0, Math.min(3, c.getInt("prestigio", 0)));
            }
        }
    }

    void guardar(File f) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("nombre", nombre);
        y.set("activo", activo == null ? null : activo.name());
        y.set("libre-hasta", libreHasta);
        y.set("tuvo-trabajo", tuvoTrabajo);
        y.set("pendiente", pendiente);
        for (Map.Entry<Trabajo, Progreso> e : prog.entrySet()) {
            String base = "progreso." + e.getKey().name();
            y.set(base + ".nivel", e.getValue().nivel);
            y.set(base + ".xp", e.getValue().xp);
            y.set(base + ".prestigio", e.getValue().prestigio);
        }
        try { y.save(f); } catch (IOException ex) { System.err.println("[3y33Trabajos] No pude guardar " + f + ": " + ex); }
    }
}
