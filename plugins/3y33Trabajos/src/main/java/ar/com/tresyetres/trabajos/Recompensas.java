package ar.com.tresyetres.trabajos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/** Lo que te dan al subir de nivel: 3 opciones (boost de XP, boost de plata o XP instantanea) con rareza; elegis una. */
final class Recompensas {
    static final class Rar {
        final String id, nombre;
        final double peso, mult, xpPct;
        final int minutos;
        final TextColor color;
        Rar(String id, String nombre, double peso, double mult, int minutos, double xpPct, TextColor color) {
            this.id = id; this.nombre = nombre; this.peso = peso; this.mult = mult; this.minutos = minutos; this.xpPct = xpPct; this.color = color;
        }
    }

    /** Un boost que se esta usando: clave ("XP:2"), de que trabajo es, multiplicador y milisegundos que le quedan. */
    static final class Activo {
        String clave;
        Trabajo trabajo;
        double mult;
        long ms;
    }

    private static final String[] IDS = {"comun", "rara", "epica", "legendaria", "mega"};
    private static final String[] NOMBRES = {"Común", "Rara", "Épica", "Legendaria", "Mega"};
    private static final int[] RGB = {0xBFBFBF, 0x55A8FF, 0xB455FF, 0xFFAA00, 0xFF4D4D};
    private static final double[] PESO = {65, 24, 8, 2.5, 0};
    private static final double[] MULT = {1.10, 1.25, 1.5, 2.0, 2.5};
    private static final int[] MINUTOS = {20, 30, 45, 60, 60};
    private static final double[] XP_PCT = {8, 15, 30, 60, 100};

    private final TrabajosPlugin pl;
    private final Random azar = new Random();
    private final List<Rar> rarezas = new ArrayList<>();
    private double probMega = 0.001;
    private int cada = 5;
    /** Opcion marcada (primer clic) de cada jugador: {nivel, opcion}; el segundo clic sobre la misma la confirma. */
    private final Map<UUID, int[]> seleccion = new HashMap<>();

    Recompensas(TrabajosPlugin pl) { this.pl = pl; }

    void cargar() {
        File f = new File(pl.getDataFolder(), "recompensas.yml");
        if (!f.exists()) pl.saveResource("recompensas.yml", false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        probMega = y.getDouble("probabilidad-mega", 0.001);
        cada = Math.max(1, y.getInt("cada-niveles", 5));
        rarezas.clear();
        for (int i = 0; i < IDS.length; i++) {
            ConfigurationSection c = y.getConfigurationSection("rarezas." + IDS[i]);
            if (c == null) {
                rarezas.add(new Rar(IDS[i], NOMBRES[i], PESO[i], MULT[i], MINUTOS[i], XP_PCT[i], TextColor.color(RGB[i])));
            } else {
                rarezas.add(new Rar(IDS[i], c.getString("nombre", NOMBRES[i]), c.getDouble("peso", PESO[i]), c.getDouble("mult", MULT[i]),
                        c.getInt("minutos", MINUTOS[i]), c.getDouble("xp-porcentaje", XP_PCT[i]), TextColor.color(RGB[i])));
            }
        }
    }

    // ---------- claves: "TIPO:rareza" ----------

    static String tipo(String k) { return k.substring(0, k.indexOf(':')); }

    private int idx(String k) {
        try { return Math.max(0, Math.min(rarezas.size() - 1, Integer.parseInt(k.substring(k.indexOf(':') + 1)))); } catch (NumberFormatException e) { return 0; }
    }

    /** Las 3 opciones de un nivel: boost de XP, boost de plata y XP instantanea (cada una con su rareza). */
    String[] sortear() { return new String[]{opcion("XP"), opcion("PLATA"), opcion("XPDIRECTA")}; }

    private String opcion(String tipo) {
        if (!tipo.equals("XPDIRECTA") && azar.nextDouble() < probMega) return "MEGA:4";
        double total = 0;
        for (int i = 0; i < 4; i++) total += rarezas.get(i).peso;
        double t = azar.nextDouble() * total;
        for (int i = 0; i < 4; i++) {
            t -= rarezas.get(i).peso;
            if (t < 0) return tipo + ":" + i;
        }
        return tipo + ":0";
    }

    Material material(String k) {
        return switch (tipo(k)) {
            case "XP" -> Material.EXPERIENCE_BOTTLE;
            case "PLATA" -> Material.GOLD_INGOT;
            case "XPDIRECTA" -> Material.EXPERIENCE_BOTTLE;
            default -> Material.NETHER_STAR;
        };
    }

    Component nombre(String k) {
        String base = switch (tipo(k)) {
            case "XP" -> "Boost de XP";
            case "PLATA" -> "Boost de plata";
            case "XPDIRECTA" -> "XP instantánea";
            default -> "Mega boost";
        };
        Rar r = rarezas.get(idx(k));
        return Iconos.txtNegrita(base + " · " + r.nombre, r.color);
    }

    List<Component> detalle(String k, Trabajo t, Perfil.Progreso g) {
        Rar r = rarezas.get(idx(k));
        long mas = Math.round((r.mult - 1) * 100);
        List<Component> l = new ArrayList<>();
        switch (tipo(k)) {
            case "XP" -> l.add(Iconos.txt("+" + mas + "% de XP durante " + r.minutos + " min.", NamedTextColor.AQUA));
            case "PLATA" -> l.add(Iconos.txt("+" + mas + "% de plata durante " + r.minutos + " min.", NamedTextColor.GREEN));
            case "XPDIRECTA" -> {
                double xp = Math.floor(r.xpPct / 100.0 * Pago.xpNecesaria(t, g.nivel));
                l.add(Iconos.txt("Te da " + Msg.num(xp, 0) + " XP ahora (" + Msg.num(r.xpPct, 0) + "% de tu nivel).", NamedTextColor.AQUA));
            }
            default -> {
                l.add(Iconos.txt("+" + mas + "% de XP y de plata a la vez", NamedTextColor.GOLD));
                l.add(Iconos.txt("durante " + r.minutos + " min.", NamedTextColor.GOLD));
            }
        }
        if (!tipo(k).equals("XPDIRECTA")) {
            l.add(Iconos.txt("Cuenta mientras estás conectado con el", NamedTextColor.GRAY));
            l.add(Iconos.txt("trabajo de " + t.nombre + " puesto.", NamedTextColor.GRAY));
        }
        return l;
    }

    // ---------- el camino: una recompensa cada "cada-niveles" niveles ----------

    int cada() { return cada; }

    /** Modelo del pack para el icono del boost (botella o monedas con flechas del color de la rareza). */
    String modeloIcono(String k) { return tipo(k).equals("MEGA") ? "boost_mega" : "boost_" + tipo(k).toLowerCase(Locale.ROOT) + "_" + rarezas.get(idx(k)).id; }

    /** Modelo del pack para la carta donde se ofrece. */
    String modeloCarta(String k) { return tipo(k).equals("MEGA") ? "recompensa_mega" : "recompensa_" + tipo(k).toLowerCase(Locale.ROOT) + "_" + rarezas.get(idx(k)).id; }

    /** El item de una recompensa: con el pack, la botella o las monedas con flechas; sin el pack, el item comun. */
    ItemStack icono(Player p, String k, List<Component> lore) {
        ItemStack it = Iconos.item(material(k), nombre(k), lore);
        if (pl.iconos().conPack(p)) pl.iconos().modelo(it, modeloIcono(k));
        return it;
    }

    int seleccionada(Player p, int nivel) {
        int[] s = seleccion.get(p.getUniqueId());
        return s != null && s[0] == nivel ? s[1] : -1;
    }

    void seleccionar(Player p, int nivel, int i) { seleccion.put(p.getUniqueId(), new int[]{nivel, i}); }

    /** Al subir de nivel: si toca nodo del camino, deja 3 opciones esperando (no se pierden aunque sigas subiendo) y avisa. */
    void alSubir(Player p, Trabajo t, Perfil.Progreso g) {
        if (g.nivel % cada != 0 || g.pend.containsKey(g.nivel) || g.hechas.containsKey(g.nivel)) return;
        g.pend.put(g.nivel, sortear());
        Msg.aviso(p, Component.text("¡Tenés una recompensa para elegir en " + t.nombre + " (nivel " + g.nivel + ")! ", NamedTextColor.GREEN)
                .append(Component.text("[Elegir]", NamedTextColor.YELLOW, TextDecoration.BOLD).clickEvent(ClickEvent.runCommand("/trabajos recompensas " + t.id + " " + g.nivel))));
    }

    /** Para el staff: deja pendientes los nodos que ya alcanzaste y no tienen recompensa. Devuelve cuantos agrego. */
    int alcanzar(Perfil.Progreso g) {
        int n = 0;
        for (int nv = cada; nv <= g.nivel; nv += cada) {
            if (g.pend.containsKey(nv) || g.hechas.containsKey(nv)) continue;
            g.pend.put(nv, sortear());
            n++;
        }
        return n;
    }

    boolean reclamar(Player p, Trabajo t, int nivel, int i) {
        Perfil f = pl.perfiles().de(p);
        Perfil.Progreso g = f.de(t);
        String[] of = g.pend.get(nivel);
        if (of == null || i < 0 || i >= of.length) { Msg.error(p, "Esa recompensa ya no está."); return false; }
        String k = of[i];
        g.pend.remove(nivel);
        g.hechas.put(nivel, k);
        seleccion.remove(p.getUniqueId());
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        if (tipo(k).equals("XPDIRECTA")) {
            double xp = Math.floor(rarezas.get(idx(k)).xpPct / 100.0 * Pago.xpNecesaria(t, g.nivel));
            Msg.aviso(p, Component.text("Elegiste ", NamedTextColor.GRAY).append(nombre(k)).append(Component.text("  +" + Msg.num(xp, 0) + " XP", NamedTextColor.AQUA)));
            pl.pago().subirXp(p, f, t, xp);
        } else {
            g.boosts.merge(k, 1, Integer::sum);
            Msg.aviso(p, Component.text("Elegiste ", NamedTextColor.GRAY).append(nombre(k)).append(Component.text(". Lo guardé en tus Boosts.", NamedTextColor.GRAY)));
        }
        pl.perfiles().guardar(f);
        return true;
    }

    // ---------- boosts guardados y activos ----------

    private static boolean vigente(Activo a) { return a != null && a.ms > 0; }

    boolean activar(Player p, Trabajo t, String k) {
        Perfil f = pl.perfiles().de(p);
        Perfil.Progreso g = f.de(t);
        if (g.boosts.getOrDefault(k, 0) <= 0) return false;
        if (f.activo != t) { Msg.error(p, "Tenés que tener el trabajo de " + t.nombre + " puesto para usarlo."); return false; }
        String tp = tipo(k);
        boolean xp = tp.equals("XP") || tp.equals("MEGA"), plata = tp.equals("PLATA") || tp.equals("MEGA");
        if ((xp && vigente(f.boostXp)) || (plata && vigente(f.boostPlata))) {
            Msg.error(p, "Ya tenés uno de ese tipo activo: no se acumulan.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        Rar r = rarezas.get(idx(k));
        Activo a = new Activo();
        a.clave = k;
        a.trabajo = t;
        a.mult = r.mult;
        a.ms = r.minutos * 60_000L;
        if (xp) f.boostXp = a;
        if (plata) f.boostPlata = a;
        int quedan = g.boosts.merge(k, -1, Integer::sum);
        if (quedan <= 0) g.boosts.remove(k);
        Msg.aviso(p, Component.text("Activaste ", NamedTextColor.GRAY).append(nombre(k)).append(Component.text(" por " + r.minutos + " min.", NamedTextColor.GRAY)));
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.4f);
        pl.perfiles().guardar(f);
        return true;
    }

    /** Cada segundo: descuenta el tiempo de los boosts activos del jugador (solo con el trabajo del boost puesto). */
    void tick(Player p, Perfil f) {
        Activo x = f.boostXp, y = f.boostPlata;
        if (x == null && y == null) return;
        descontar(p, f, x);
        if (y != x) descontar(p, f, y);
    }

    private void descontar(Player p, Perfil f, Activo a) {
        if (a == null) return;
        if (f.activo == a.trabajo) a.ms -= 1000;
        if (a.ms <= 0) {
            if (f.boostXp == a) f.boostXp = null;
            if (f.boostPlata == a) f.boostPlata = null;
            Msg.aviso(p, Component.text("Se terminó tu ", NamedTextColor.GRAY).append(nombre(a.clave)).append(Component.text(".", NamedTextColor.GRAY)));
        }
    }

    double multXp(Perfil f, Trabajo t) {
        Activo a = f.boostXp;
        return a != null && a.ms > 0 && a.trabajo == t ? a.mult : 1.0;
    }

    double multPlata(Perfil f, Trabajo t) {
        Activo a = f.boostPlata;
        return a != null && a.ms > 0 && a.trabajo == t ? a.mult : 1.0;
    }
}
