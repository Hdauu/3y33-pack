package ar.com.tresyetres.trabajos;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

import java.text.Normalizer;
import java.util.Locale;

/** Los cinco trabajos. El color es el de su carta en el pack. */
enum Trabajo {
    MINERO("minero", "Minero", 0x5496FF, Material.IRON_PICKAXE, "Extraé recursos valiosos de las profundidades."),
    LENADOR("lenador", "Leñador", 0xDE8E34, Material.IRON_AXE, "Talá árboles y cosechá la madera de los bosques."),
    GRANJERO("granjero", "Granjero", 0x84CC44, Material.IRON_HOE, "Cultivá, criá y alimentá al mundo."),
    CAZADOR("cazador", "Cazador", 0xEC464E, Material.IRON_SWORD, "Cazá criaturas hostiles y llevate la gloria."),
    PESCADOR("pescador", "Pescador", 0x30BEE6, Material.FISHING_ROD, "Echá la caña y sacá tesoros del agua.");

    final String id;
    final String nombre;
    final TextColor color;
    final Material icono;
    final String descripcion;

    Trabajo(String id, String nombre, int rgb, Material icono, String descripcion) {
        this.id = id;
        this.nombre = nombre;
        this.color = TextColor.color(rgb);
        this.icono = icono;
        this.descripcion = descripcion;
    }

    static Trabajo de(String s) {
        if (s == null) return null;
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        for (Trabajo t : values()) if (t.id.equals(n)) return t;
        return null;
    }
}
