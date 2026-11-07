package ar.com.tresyetres.trabajos;

import org.bukkit.Material;
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

    /** Una mision de entregar items. estado: 0 activa, 1 cobrada, 2 perdida (por abandonar el trabajo). */
    static final class Mision {
        static final int ACTIVA = 0, COMPLETA = 1, PERDIDA = 2;
        final Material item;
        int meta;
        int entregado;
        final Rareza rareza;
        int estado;

        Mision(Material item, int meta, Rareza rareza) { this.item = item; this.meta = meta; this.rareza = rareza; }

        void guardar(ConfigurationSection s) {
            s.set("item", item.name());
            s.set("meta", meta);
            s.set("entregado", entregado);
            s.set("rareza", rareza.name());
            s.set("estado", estado);
        }

        static Mision cargar(ConfigurationSection s) {
            if (s == null) return null;
            Material m = Material.matchMaterial(s.getString("item", ""));
            if (m == null) return null;
            Rareza r;
            try { r = Rareza.valueOf(s.getString("rareza", "COMUN")); } catch (IllegalArgumentException e) { r = Rareza.COMUN; }
            Mision x = new Mision(m, Math.max(1, s.getInt("meta", 1)), r);
            x.entregado = s.getInt("entregado", 0);
            x.estado = s.getInt("estado", 0);
            return x;
        }
    }

    static final class Progreso {
        int nivel = 1;
        double xp = 0;
        int prestigio = 0;
        // misiones: la diaria (a la hora del reroll) y la semanal (lunes 0:00) se generan al mirarlas
        long dia;
        Mision aleatoria;
        final Mision[] opciones = new Mision[4];
        Mision elegida;
        boolean elegidaPerdida;
        long semana;
        Mision semanal;
        int racha;
        long rachaDia;
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
    double mx, my, mz;
    float myaw, mpitch;
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
                g.dia = c.getLong("dia", 0L);
                g.semana = c.getLong("semana", 0L);
                g.racha = c.getInt("racha", 0);
                g.rachaDia = c.getLong("racha-dia", 0L);
                g.elegidaPerdida = c.getBoolean("elegida-perdida", false);
                g.aleatoria = Mision.cargar(c.getConfigurationSection("aleatoria"));
                g.elegida = Mision.cargar(c.getConfigurationSection("elegida"));
                g.semanal = Mision.cargar(c.getConfigurationSection("semanal"));
                for (int i = 0; i < 4; i++) g.opciones[i] = Mision.cargar(c.getConfigurationSection("opcion" + i));
            }
        }
    }

    /** El perfil como texto YAML (se escribe a disco aparte, sin frenar el servidor). */
    String aTexto() {
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
            Progreso g = e.getValue();
            y.set(base + ".dia", g.dia);
            y.set(base + ".semana", g.semana);
            y.set(base + ".racha", g.racha);
            y.set(base + ".racha-dia", g.rachaDia);
            y.set(base + ".elegida-perdida", g.elegidaPerdida);
            if (g.aleatoria != null) g.aleatoria.guardar(y.createSection(base + ".aleatoria"));
            if (g.elegida != null) g.elegida.guardar(y.createSection(base + ".elegida"));
            if (g.semanal != null) g.semanal.guardar(y.createSection(base + ".semanal"));
            for (int i = 0; i < 4; i++) if (g.opciones[i] != null) g.opciones[i].guardar(y.createSection(base + ".opcion" + i));
        }
        return y.saveToString();
    }
}
