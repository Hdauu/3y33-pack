package ar.com.tresyetres.trabajos;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Todas las acciones que pagan, leidas de acciones.yml. */
final class Catalogo {
    private final TrabajosPlugin pl;
    private final Map<String, Accion> porId = new LinkedHashMap<>();
    private final Map<Trabajo, List<Accion>> lista = new EnumMap<>(Trabajo.class);
    private final Map<Trabajo, Map<Material, Accion>> bloques = new EnumMap<>(Trabajo.class);
    private final Map<Trabajo, Map<EntityType, Accion>> mobs = new EnumMap<>(Trabajo.class);
    private final Map<String, Accion> pesca = new HashMap<>();
    private Accion cria;
    private final Set<Material> pagables = EnumSet.noneOf(Material.class);

    Catalogo(TrabajosPlugin pl) { this.pl = pl; }

    void cargar() {
        porId.clear();
        lista.clear();
        bloques.clear();
        mobs.clear();
        pesca.clear();
        pagables.clear();
        cria = null;
        for (Trabajo t : Trabajo.values()) {
            lista.put(t, new ArrayList<>());
            bloques.put(t, new EnumMap<>(Material.class));
            mobs.put(t, new EnumMap<>(EntityType.class));
        }
        File f = new File(pl.getDataFolder(), "acciones.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        double xpPorPeso = pl.getConfig().getDouble("xp-por-peso", 15);
        for (Trabajo t : Trabajo.values()) {
            ConfigurationSection s = y.getConfigurationSection(t.id);
            if (s == null) continue;
            ConfigurationSection b = s.getConfigurationSection("bloques");
            if (b != null) {
                for (String k : b.getKeys(false)) {
                    Material m = Material.matchMaterial(k);
                    if (m == null || !m.isBlock()) { pl.getLogger().warning("acciones.yml: bloque desconocido " + k + " (" + t.id + ")"); continue; }
                    double pago = b.getDouble(k);
                    Accion a = new Accion(t.id + ":" + m.name(), t, Accion.Tipo.BLOQUE, m.name(), pago, pago * xpPorPeso, iconoDe(m));
                    agregar(a);
                    bloques.get(t).put(m, a);
                    pagables.add(m);
                }
            }
            for (String seccion : new String[]{"mobs", "animales"}) {
                ConfigurationSection mm = s.getConfigurationSection(seccion);
                if (mm == null) continue;
                for (String k : mm.getKeys(false)) {
                    EntityType et;
                    try { et = EntityType.valueOf(k.toUpperCase(java.util.Locale.ROOT)); }
                    catch (IllegalArgumentException e) { pl.getLogger().warning("acciones.yml: mob desconocido " + k + " (" + t.id + ")"); continue; }
                    double pago = mm.getDouble(k);
                    Material huevo = Material.matchMaterial(et.name() + "_SPAWN_EGG");
                    Accion a = new Accion(t.id + ":" + et.name(), t, Accion.Tipo.MOB, et.name(), pago, pago * xpPorPeso, huevo != null ? huevo : Material.EGG);
                    agregar(a);
                    mobs.get(t).put(et, a);
                }
            }
            ConfigurationSection p = s.getConfigurationSection("pesca");
            if (p != null) {
                for (String k : p.getKeys(false)) {
                    double pago = p.getDouble(k);
                    Material ic = switch (k) {
                        case "PESCADO" -> Material.COD;
                        case "TROPICAL" -> Material.TROPICAL_FISH;
                        case "TESORO" -> Material.NAUTILUS_SHELL;
                        default -> Material.LILY_PAD;
                    };
                    Accion a = new Accion(t.id + ":" + k, t, Accion.Tipo.PESCA, k, pago, pago * xpPorPeso, ic);
                    agregar(a);
                    pesca.put(k, a);
                }
            }
            if (s.contains("crias")) {
                double pago = s.getDouble("crias");
                cria = new Accion(t.id + ":CRIA", t, Accion.Tipo.CRIA, "CRIA", pago, pago * xpPorPeso, Material.WHEAT);
                agregar(cria);
            }
        }
        pl.getLogger().info("Acciones cargadas: " + porId.size());
    }

    private void agregar(Accion a) {
        porId.put(a.id(), a);
        lista.get(a.trabajo()).add(a);
    }

    private static Material iconoDe(Material m) {
        if (m.isItem()) return m;
        return switch (m) {
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT;
            case COCOA -> Material.COCOA_BEANS;
            case SWEET_BERRY_BUSH -> Material.SWEET_BERRIES;
            default -> Material.PAPER;
        };
    }

    Accion bloque(Trabajo t, Material m) { return bloques.get(t).get(m); }
    Accion mob(Trabajo t, EntityType e) { return mobs.get(t).get(e); }
    Accion pesca(String clave) { return pesca.get(clave); }
    Accion cria() { return cria; }
    Accion porId(String id) { return porId.get(id); }
    List<Accion> de(Trabajo t) { return lista.get(t); }
    Collection<Accion> todas() { return porId.values(); }
    /** Bloques que pagan en algun trabajo: los puestos por jugadores se marcan para que no sirvan. */
    boolean pagable(Material m) { return pagables.contains(m); }
}
