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
        TODAS.put(id, new Plantilla(1.0f, List.of(titulo(a, b, nombre), "<gray>" + sub, "<dark_gray>" + accion)));
    }

    static {
        TODAS.put("bienvenida", new Plantilla(1.5f, List.of(
                titulo("#E03131", "#FF8787", "3y33 SMP"),
                "<gray>Survival vanilla · Java y Bedrock",
                " ",
                "<#FF8787>ds.3y33.com.ar")));
        seccion("terrenos", "#2F9E44", "#8CE99A", "Terrenos", "Protegé tu base", "Clic para abrir");
        seccion("misiones", "#F08C00", "#FFE066", "Misiones", "Ganá plata y rangos", "Clic para abrir");
        seccion("niveles", "#9C36B5", "#E599F7", "Niveles", "Mirá tu progreso", "Clic para abrir");
        seccion("reglas", "#1971C2", "#74C0FC", "Reglas", "Leelas antes de jugar", "Clic para abrir");
        seccion("explorar", "#E8590C", "#FFC078", "Explorar", "Viajá a un lugar nuevo", "Clic para viajar");
        seccion("discord", "#4263EB", "#91A7FF", "Discord", "ds.3y33.com.ar", "Sumate a la comunidad");
    }

    static List<String> nombres() { return new ArrayList<>(TODAS.keySet()); }

    /** Devuelve el nombre con el que quedó creado, o null si falló (el motivo ya se le avisó al jugador). */
    static String crear(Core core, Player p, String plantilla, String nombre) {
        Plantilla t = TODAS.get(plantilla.toLowerCase(Locale.ROOT));
        if (t == null) {
            core.aviso(p, "<red>Plantillas: <white>" + String.join(", ", TODAS.keySet()));
            return null;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("FancyHolograms")) {
            core.aviso(p, "<red>FancyHolograms no está activo.");
            return null;
        }
        try {
            var manager = FancyHologramsPlugin.get().getHologramManager();
            String base = (nombre == null || nombre.isBlank() ? plantilla : nombre).replace('.', '_');
            String final_ = base;
            int n = 2;
            while (manager.getHologram(final_).isPresent()) final_ = base + "_" + n++;
            Location l = p.getLocation().clone().add(0, 1.8, 0);
            TextHologramData d = new TextHologramData(final_, l);
            d.setText(new ArrayList<>(t.lineas));
            d.setBackground(Color.fromARGB(110, 0, 0, 0));
            d.setTextShadow(true);
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
            core.aviso(p, "<red>No se pudo crear el holograma. Mirá la consola.");
            return null;
        }
    }
}
