package ar.com.tresyetres.core;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.joml.Vector3f;

import java.util.*;

/** Hologramas prediseñados de 3y33 (degradados, fondo oscuro y mirando siempre al jugador). */
final class Plantillas {

    private record Plantilla(float escala, List<String> lineas) {}

    private static final Map<String, Plantilla> TODAS = new LinkedHashMap<>();

    private static String titulo(String a, String b, String texto) {
        return "<gradient:" + a + ":" + b + "><bold>" + texto + "</bold></gradient>";
    }

    private static void seccion(String id, String a, String b, String nombre, String sub, String accion) {
        TODAS.put(id, new Plantilla(1.0f, List.of(titulo(a, b, nombre), "<white>" + sub, "<white>" + accion)));
    }

    static {
        TODAS.put("bienvenida", new Plantilla(1.5f, List.of(
                titulo("#FF2020", "#FF6B6B", "3y33 SMP"),
                "<white>Survival vanilla · Java y Bedrock",
                " ",
                "<#FF4D4D>ds.3y33.com.ar")));
        seccion("terrenos", "#12B533", "#5CFF7A", "Terrenos", "Protegé tu base", "Clic para abrir");
        seccion("misiones", "#FF9500", "#FFD21F", "Misiones", "Ganá plata y rangos", "Clic para abrir");
        seccion("niveles", "#B52BE0", "#E86BFF", "Niveles", "Mirá tu progreso", "Clic para abrir");
        seccion("reglas", "#1E8CFF", "#4FD0FF", "Reglas", "Leelas antes de jugar", "Clic para abrir");
        seccion("explorar", "#FF5A00", "#FFA040", "Explorar", "Viajá a un lugar nuevo", "Clic para viajar");
        seccion("discord", "#4C5CFF", "#8A9BFF", "Discord", "Sumate a nuestro Discord", "Hacé clic acá");
    }

    static List<String> nombres() { return new ArrayList<>(TODAS.keySet()); }

    /** Devuelve el nombre con el que quedó creado, o null si falló (el motivo ya se le avisó al jugador). */
    static String crear(Core core, Player p, String plantilla, String nombre) {
        return crearEn(core, p, p.getLocation().clone().add(0, 1.8, 0), plantilla, nombre);
    }

    /** Igual que crear, pero en un lugar puntual (p puede ser null: se avisa por consola). */
    static String crearEn(Core core, Player p, Location donde, String plantilla, String nombre) {
        Plantilla t = TODAS.get(plantilla.toLowerCase(Locale.ROOT));
        if (t == null) {
            if (p != null) core.aviso(p, "<red>Plantillas: <white>" + String.join(", ", TODAS.keySet()));
            return null;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("FancyHolograms")) {
            if (p != null) core.aviso(p, "<red>FancyHolograms no está activo.");
            return null;
        }
        try {
            var manager = FancyHologramsPlugin.get().getHologramManager();
            String base = (nombre == null || nombre.isBlank() ? plantilla : nombre).replace('.', '_');
            String final_ = base;
            int n = 2;
            while (manager.getHologram(final_).isPresent()) final_ = base + "_" + n++;
            Location l = donde.clone();
            TextHologramData d = new TextHologramData(final_, l);
            d.setText(new ArrayList<>(t.lineas));
            d.setBackground(Color.fromARGB(51, 0, 0, 0));
            d.setTextShadow(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setBillboard(Display.Billboard.CENTER);
            d.setScale(new Vector3f(t.escala, t.escala, t.escala));
            Hologram h = manager.create(d);
            h.createHologram();
            for (Player online : Bukkit.getOnlinePlayers()) h.updateShownStateFor(online);
            manager.addHologram(h);
            manager.saveHolograms();
            return final_;
        } catch (Throwable e) {
            core.getLogger().warning("No pude crear el holograma: " + e);
            if (p != null) core.aviso(p, "<red>No se pudo crear el holograma. Mirá la consola.");
            return null;
        }
    }

    /** Deja los carteles de spawn_ y beta_ fijos (sin girar) y mirando al centro del arco donde se para la gente. extra = grados de correccion. */
    static int orientar(Core core, double extra) {
        int n = 0;
        try {
            var manager = FancyHologramsPlugin.get().getHologramManager();
            for (String pre : new String[]{"spawn_", "beta_"}) {
                List<Hologram> hs = new ArrayList<>();
                for (Hologram h : manager.getHolograms()) if (h.getData().getName().startsWith(pre)) hs.add(h);
                List<Hologram> arco = new ArrayList<>();
                for (Hologram h : hs) if (!h.getData().getName().endsWith("bienvenida")) arco.add(h);
                if (arco.size() < 3) continue;
                // circulo que pasa por los carteles del arco (minimos cuadrados): x2+z2+Dx+Ez+F=0
                double sxx = 0, sxz = 0, szz = 0, sx = 0, sz = 0, b1 = 0, b2 = 0, b3 = 0;
                int cnt = arco.size();
                for (Hologram h : arco) {
                    Location l = h.getData().getLocation();
                    double x = l.getX(), z = l.getZ(), r = -(x * x + z * z);
                    sxx += x * x; sxz += x * z; szz += z * z; sx += x; sz += z; b1 += x * r; b2 += z * r; b3 += r;
                }
                double[][] a = {{sxx, sxz, sx}, {sxz, szz, sz}, {sx, sz, cnt}};
                double[] b = {b1, b2, b3};
                double d = det(a);
                if (Math.abs(d) < 1e-9) continue;
                double dd = det(reem(a, b, 0)) / d, ee = det(reem(a, b, 1)) / d;
                double cx = -dd / 2, cz = -ee / 2;
                for (Hologram h : hs) {
                    Location l = h.getData().getLocation().clone();
                    double yaw = Math.toDegrees(Math.atan2(-(cx - l.getX()), cz - l.getZ())) + extra;
                    l.setYaw((float) yaw);
                    h.getData().setLocation(l);
                    if (h.getData() instanceof TextHologramData td) td.setBillboard(Display.Billboard.FIXED);
                    h.forceUpdate();
                    h.refreshForViewers();
                    n++;
                }
            }
            manager.saveHolograms();
        } catch (Throwable e) {
            core.getLogger().warning("No pude orientar los carteles: " + e);
        }
        return n;
    }

    private static double det(double[][] m) {
        return m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
                + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
    }

    private static double[][] reem(double[][] a, double[] b, int col) {
        double[][] r = new double[3][3];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) r[i][j] = j == col ? b[i] : a[i][j];
        return r;
    }
}
