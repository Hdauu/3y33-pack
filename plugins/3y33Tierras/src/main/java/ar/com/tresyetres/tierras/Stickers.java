package ar.com.tresyetres.tierras;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Material;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Stickers del pack 3y33: códigos tipo :corazon: que se dibujan como íconos pixel. */
final class Stickers {

    record Sticker(String nombre, String caracter, String fallback, Material icono) {}

    static final Map<String, Sticker> TODOS = new LinkedHashMap<>();
    private static final Pattern CODIGO = Pattern.compile(":([a-z]+):");

    private static final Map<String, Material> ICONOS = Map.ofEntries(
            Map.entry("corazon", Material.RED_DYE), Map.entry("estrella", Material.NETHER_STAR),
            Map.entry("espada", Material.IRON_SWORD), Map.entry("pico", Material.DIAMOND_PICKAXE),
            Map.entry("fuego", Material.BLAZE_POWDER), Map.entry("corona", Material.GOLDEN_HELMET),
            Map.entry("casa", Material.OAK_DOOR), Map.entry("flor", Material.PINK_TULIP),
            Map.entry("calavera", Material.SKELETON_SKULL), Map.entry("diamante", Material.DIAMOND),
            Map.entry("nota", Material.NOTE_BLOCK), Map.entry("hoja", Material.OAK_LEAVES),
            Map.entry("pez", Material.TROPICAL_FISH), Map.entry("luna", Material.CLOCK),
            Map.entry("sol", Material.SUNFLOWER), Map.entry("rayo", Material.LIGHTNING_ROD),
            Map.entry("trofeo", Material.TOTEM_OF_UNDYING), Map.entry("escudo", Material.SHIELD),
            Map.entry("gema", Material.AMETHYST_SHARD), Map.entry("manzana", Material.APPLE));

    static void cargar(Tierras pl) {
        TODOS.clear();
        try (var in = pl.getResource("stickers.json")) {
            if (in == null) return;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String n : o.keySet()) {
                JsonObject s = o.getAsJsonObject(n);
                TODOS.put(n, new Sticker(n, s.get("char").getAsString(), s.get("fallback").getAsString(),
                        ICONOS.getOrDefault(n, Material.PAPER)));
            }
        } catch (Exception e) {
            pl.getLogger().warning("No se pudieron cargar los stickers: " + e.getMessage());
        }
    }

    /** Un sticker listo para MiniMessage. */
    static String dibujar(Sticker s, boolean conPack, boolean grande) {
        if (!conPack) return "<white>" + s.fallback() + "</white>";
        return "<white><font:3y33:" + (grande ? "sticker_g" : "sticker") + ">" + s.caracter() + "</font></white>";
    }

    /** Escapa el texto del jugador y reemplaza los códigos :nombre: por stickers. */
    static String procesar(String texto, boolean conPack, boolean grande) {
        String esc = Menu.esc(texto);
        Matcher m = CODIGO.matcher(esc);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Sticker s = TODOS.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(s == null ? m.group() : dibujar(s, conPack, grande)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static int contar(String texto) {
        Matcher m = CODIGO.matcher(texto);
        int n = 0;
        while (m.find()) if (TODOS.containsKey(m.group(1))) n++;
        return n;
    }
}
