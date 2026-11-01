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
}
