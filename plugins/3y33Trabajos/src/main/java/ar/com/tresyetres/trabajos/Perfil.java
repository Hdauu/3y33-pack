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
    static final int NIVEL_MAX = 130;

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
        /** Las 3 opciones que te ofrecieron al subir de nivel (null si no hay), y los boosts que guardaste: clave -> cantidad. */
        String[] oferta;
        final Map<String, Integer> boosts = new java.util.LinkedHashMap<>();
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
    long ultimaAccion;
    /** Boosts que se estan usando (el mega ocupa los dos). */
    Recompensas.Activo boostXp, boostPlata;
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
                java.util.List<String> of = c.getStringList("oferta");
                g.oferta = of.size() == 3 ? of.toArray(new String[0]) : null;
                ConfigurationSection bs = c.getConfigurationSection("boosts");
                if (bs != null) for (String k : bs.getKeys(false)) {
                    int n = bs.getInt(k, 0);
                    if (n > 0) g.boosts.put(k.replace('-', ':'), n);
                }
            }
        }
        boostXp = cargarActivo(y, "activos.xp");
        boostPlata = cargarActivo(y, "activos.plata");
        if (boostXp != null && boostXp.clave.startsWith("MEGA")) boostPlata = boostXp;
    }

    private static Recompensas.Activo cargarActivo(YamlConfiguration y, String base) {
        ConfigurationSection c = y.getConfigurationSection(base);
        if (c == null) return null;
        Trabajo t = Trabajo.de(c.getString("trabajo"));
        long ms = c.getLong("ms", 0L);
        String clave = c.getString("clave");
        if (t == null || ms <= 0 || clave == null) return null;
        Recompensas.Activo a = new Recompensas.Activo();
        a.clave = clave;
        a.trabajo = t;
        a.mult = c.getDouble("mult", 1.0);
        a.ms = ms;
        return a;
    }

    private static void guardarActivo(YamlConfiguration y, String base, Recompensas.Activo a) {
        if (a == null || a.ms <= 0) return;
        y.set(base + ".clave", a.clave);
        y.set(base + ".trabajo", a.trabajo.id);
        y.set(base + ".mult", a.mult);
        y.set(base + ".ms", a.ms);
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
            if (g.oferta != null) y.set(base + ".oferta", java.util.Arrays.asList(g.oferta));
            for (Map.Entry<String, Integer> b : g.boosts.entrySet()) y.set(base + ".boosts." + b.getKey().replace(':', '-'), b.getValue());
        }
        guardarActivo(y, "activos.xp", boostXp);
        if (boostPlata != boostXp) guardarActivo(y, "activos.plata", boostPlata);
        return y.saveToString();
    }
}
