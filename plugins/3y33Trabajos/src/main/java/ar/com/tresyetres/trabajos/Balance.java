package ar.com.tresyetres.trabajos;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Cuanta plata y XP crean los trabajos por hora activa (para balancear): se guarda por hora, las ultimas 72. */
final class Balance {
    static final int ACCIONES = 0, TIEMPO = 1, MISIONES = 2, GLOBAL = 3, XP = 4, MINUTOS = 5;
    private static final int HORAS = 72;

    private final TrabajosPlugin pl;
    private final File archivo;
    private final TreeMap<Long, Map<Trabajo, double[]>> horas = new TreeMap<>();
    private boolean sucio;

    Balance(TrabajosPlugin pl) {
        this.pl = pl;
        this.archivo = new File(pl.getDataFolder(), "balance.yml");
    }

    private double[] fila(Trabajo t) {
        long h = System.currentTimeMillis() / 3_600_000L;
        Map<Trabajo, double[]> m = horas.computeIfAbsent(h, k -> new EnumMap<>(Trabajo.class));
        while (horas.size() > HORAS) horas.pollFirstEntry();
        sucio = true;
        return m.computeIfAbsent(t, k -> new double[6]);
    }

    void plata(Trabajo t, int origen, double pesos) { if (pesos > 0) fila(t)[origen] += pesos; }

    void xp(Trabajo t, double xp) { if (xp > 0) fila(t)[XP] += xp; }

    /** Un minuto de trabajo activo (hizo una accion en el ultimo minuto). */
    void minuto(Trabajo t) { fila(t)[MINUTOS] += 1; }

    /** Suma de las ultimas n horas de ese trabajo: [acciones, tiempo, misiones, global, xp, minutos]. */
    double[] suma(Trabajo t, int ultimas) {
        double[] r = new double[6];
        long desde = System.currentTimeMillis() / 3_600_000L - ultimas + 1;
        for (Map.Entry<Long, Map<Trabajo, double[]>> e : horas.entrySet()) {
            if (e.getKey() < desde) continue;
            double[] v = e.getValue().get(t);
            if (v != null) for (int i = 0; i < 6; i++) r[i] += v[i];
        }
        return r;
    }

    void cargar() {
        if (!archivo.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(archivo);
        for (String k : y.getKeys(false)) {
            long h;
            try { h = Long.parseLong(k); } catch (NumberFormatException ignorada) { continue; }
            ConfigurationSection s = y.getConfigurationSection(k);
            if (s == null) continue;
            Map<Trabajo, double[]> m = new EnumMap<>(Trabajo.class);
            for (Trabajo t : Trabajo.values()) {
                List<Double> l = s.getDoubleList(t.id);
                if (l.size() < 6) continue;
                double[] v = new double[6];
                for (int i = 0; i < 6; i++) v[i] = l.get(i);
                m.put(t, v);
            }
            horas.put(h, m);
        }
    }

    void guardarSiHaceFalta() { if (sucio) guardar(); }

    void guardar() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<Long, Map<Trabajo, double[]>> e : horas.entrySet()) {
            for (Map.Entry<Trabajo, double[]> t : e.getValue().entrySet()) {
                List<Double> l = new ArrayList<>();
                for (double d : t.getValue()) l.add(d);
                y.set(e.getKey() + "." + t.getKey().id, l);
            }
        }
        try { y.save(archivo); sucio = false; } catch (IOException ex) { pl.getLogger().warning("No pude guardar balance.yml: " + ex); }
    }
}
