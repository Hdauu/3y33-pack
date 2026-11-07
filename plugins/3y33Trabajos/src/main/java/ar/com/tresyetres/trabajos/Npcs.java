package ar.com.tresyetres.trabajos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.io.File;
import java.io.IOException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Los NPC de Trabajos.
 * - Uno por trabajo (maniquies con skin del pack, la herramienta en la mano): titulo flotante "Misiones / Trabajo" y, para cada
 *   jugador que se acerca, un panel con la mision global de ese trabajo (progreso, tu aporte, tu puesto y el top 5). Clic = menu de misiones.
 * - El aldeano general del spawn (abre /trabajos), que sigue andando como antes.
 */
final class Npcs implements Listener {
    private static final String TAG = "3y33npc";
    private static final double ALCANCE = 24;

    private static final Map<Trabajo, String> HERRAMIENTA = new EnumMap<>(Trabajo.class);
    private static final Map<Trabajo, String> MISION = new EnumMap<>(Trabajo.class);
    private static final Map<Trabajo, String> UNIDAD = new EnumMap<>(Trabajo.class);
    static {
        HERRAMIENTA.put(Trabajo.MINERO, "netherite_pickaxe");
        HERRAMIENTA.put(Trabajo.LENADOR, "netherite_axe");
        HERRAMIENTA.put(Trabajo.GRANJERO, "netherite_hoe");
        HERRAMIENTA.put(Trabajo.CAZADOR, "netherite_sword");
        HERRAMIENTA.put(Trabajo.PESCADOR, "fishing_rod");
        MISION.put(Trabajo.MINERO, "Fiebre del Subsuelo");
        MISION.put(Trabajo.LENADOR, "Tala Colectiva");
        MISION.put(Trabajo.GRANJERO, "Cosecha del Pueblo");
        MISION.put(Trabajo.CAZADOR, "Cacería Mayor");
        MISION.put(Trabajo.PESCADOR, "La Gran Pesca");
        UNIDAD.put(Trabajo.MINERO, "puntos");
        UNIDAD.put(Trabajo.LENADOR, "puntos");
        UNIDAD.put(Trabajo.GRANJERO, "puntos");
        UNIDAD.put(Trabajo.CAZADOR, "puntos");
        UNIDAD.put(Trabajo.PESCADOR, "puntos");
    }

    /** Un holograma guardado: un titulo ("Misiones / Trabajo") o un panel de la mision global. Se puede poner y mover donde sea. */
    private static final class Holo {
        final String id;
        final boolean panel;
        final Trabajo trabajo;
        Location loc;
        Float yaw;                                            // solo los paneles: hacia donde miran (fijos)
        TextDisplay titulo;                                   // solo los titulos
        final Map<UUID, TextDisplay> paneles = new HashMap<>(); // un panel por jugador cercano
        Holo(String id, boolean panel, Trabajo trabajo, Location loc) { this.id = id; this.panel = panel; this.trabajo = trabajo; this.loc = loc; }
    }

    private final TrabajosPlugin pl;
    private final NamespacedKey marca;
    private final NamespacedKey yaw0;
    private final File archivo;
    private final Map<String, Holo> holos = new java.util.LinkedHashMap<>();

    Npcs(TrabajosPlugin pl) {
        this.pl = pl;
        this.marca = new NamespacedKey(pl, "npc-trabajos");
        this.yaw0 = new NamespacedKey(pl, "npc-yaw0");
        this.archivo = new File(pl.getDataFolder(), "hologramas.yml");
        cargar();
        Bukkit.getScheduler().runTaskTimer(pl, this::actualizar, 40L, 100L);
        Bukkit.getScheduler().runTaskTimer(pl, this::mirar, 40L, 4L);
        Bukkit.getScheduler().runTaskLater(pl, this::migrar, 100L);
    }

    // ---------- hologramas guardados ----------

    private void cargar() {
        holos.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(archivo);
        ConfigurationSection s = y.getConfigurationSection("hologramas");
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            ConfigurationSection h = s.getConfigurationSection(id);
            Trabajo t = h == null ? null : Trabajo.de(h.getString("trabajo"));
            World w = h == null ? null : Bukkit.getWorld(h.getString("mundo", ""));
            if (t == null || w == null) continue;
            Holo n = new Holo(id, "panel".equals(h.getString("tipo")), t, new Location(w, h.getDouble("x"), h.getDouble("y"), h.getDouble("z")));
            if (h.contains("yaw")) n.yaw = (float) h.getDouble("yaw");
            holos.put(id, n);
        }
    }

    private void guardar() {
        YamlConfiguration y = new YamlConfiguration();
        for (Holo h : holos.values()) {
            String b = "hologramas." + h.id;
            y.set(b + ".tipo", h.panel ? "panel" : "titulo");
            y.set(b + ".trabajo", h.trabajo.id);
            y.set(b + ".mundo", h.loc.getWorld().getName());
            y.set(b + ".x", h.loc.getX());
            y.set(b + ".y", h.loc.getY());
            y.set(b + ".z", h.loc.getZ());
            if (h.yaw != null) y.set(b + ".yaw", h.yaw.doubleValue());
        }
        try { y.save(archivo); } catch (IOException e) { pl.getLogger().warning("No pude guardar hologramas.yml: " + e.getMessage()); }
    }

    /** La primera vez (sin hologramas.yml): le arma titulo y panel a los NPC que ya estaban puestos. */
    private void migrar() {
        if (archivo.exists()) return;
        for (World w : Bukkit.getWorlds()) {
            for (Mannequin m : w.getEntitiesByClass(Mannequin.class)) {
                Trabajo t = trabajoDe(m);
                if (t == null || !m.getScoreboardTags().contains(TAG)) continue;
                Float y = m.getPersistentDataContainer().get(yaw0, PersistentDataType.FLOAT);
                crearHolos(m.getLocation(), y != null ? y : m.getLocation().getYaw(), t);
            }
        }
        guardar();
    }

    private String nuevoId() {
        int n = 1;
        while (holos.containsKey("h" + n)) n++;
        return "h" + n;
    }

    /** Titulo arriba del NPC y panel a la izquierda de como se lo ve de frente (yaw = hacia donde mira el NPC). */
    private void crearHolos(Location base, float yaw, Trabajo t) {
        String a = nuevoId();
        holos.put(a, new Holo(a, false, t, base.clone().add(0, 2.75, 0)));
        double r = Math.toRadians(yaw);
        String b = nuevoId();
        Holo panel = new Holo(b, true, t, base.clone().add(-Math.cos(r) * 3.4, 1.5, -Math.sin(r) * 3.4));
        panel.yaw = yaw;
        holos.put(b, panel);
    }

    /** Pone un holograma suelto donde esta el jugador (a la altura de los ojos). */
    String crearHolo(Player p, boolean panel, Trabajo t) {
        String id = nuevoId();
        Holo n = new Holo(id, panel, t, p.getEyeLocation().clone());
        if (panel) n.yaw = p.getLocation().getYaw() + 180f;
        holos.put(id, n);
        guardar();
        actualizar();
        return id;
    }

    private Holo masCercano(Player p, Boolean panel) {
        Holo mejor = null; double d = 12 * 12;
        for (Holo h : holos.values()) {
            if (panel != null && h.panel != panel) continue;
            if (!h.loc.getWorld().equals(p.getWorld())) continue;
            double x = h.loc.distanceSquared(p.getEyeLocation());
            if (x < d) { d = x; mejor = h; }
        }
        return mejor;
    }

    /** Mueve el holograma mas cercano (a menos de 12 bloques) a donde esta el jugador. Devuelve su id o null. */
    String moverHolo(Player p, Boolean panel) {
        Holo h = masCercano(p, panel);
        if (h == null) return null;
        h.loc = p.getEyeLocation().clone();
        if (h.panel) h.yaw = p.getLocation().getYaw() + 180f;
        if (h.titulo != null) h.titulo.teleport(h.loc);
        for (TextDisplay d : h.paneles.values()) d.teleport(ubicacion(h));
        guardar();
        return h.id + " (" + (h.panel ? "panel" : "titulo") + " de " + h.trabajo.nombre + ")";
    }

    /** Donde va el cartel de un holograma: los paneles son fijos y miran hacia su yaw. */
    private Location ubicacion(Holo h) {
        Location l = h.loc.clone();
        l.setYaw(h.yaw != null ? h.yaw : 0f);
        l.setPitch(0f);
        return l;
    }

    /** Un panel viejo sin direccion: mira para donde mira el NPC del trabajo que tenga cerca (o al sur). */
    private void orientar(Holo h) {
        float y = 0f;
        for (Entity e : h.loc.getWorld().getNearbyEntities(h.loc, 12, 12, 12)) {
            if (trabajoDe(e) != h.trabajo || !e.getScoreboardTags().contains(TAG)) continue;
            Float a = e.getPersistentDataContainer().get(yaw0, PersistentDataType.FLOAT);
            y = a != null ? a : e.getLocation().getYaw();
            break;
        }
        h.yaw = y;
        guardar();
    }

    String quitarHolo(Player p, Boolean panel) {
        Holo h = masCercano(p, panel);
        if (h == null) return null;
        sacarCarteles(h);
        holos.remove(h.id);
        guardar();
        return h.id + " (" + (h.panel ? "panel" : "titulo") + " de " + h.trabajo.nombre + ")";
    }

    List<String> listaHolos() {
        List<String> l = new ArrayList<>();
        for (Holo h : holos.values()) l.add(h.id + ": " + (h.panel ? "panel" : "titulo") + " de " + h.trabajo.nombre + " en " + h.loc.getWorld().getName() + " " + h.loc.getBlockX() + ", " + h.loc.getBlockY() + ", " + h.loc.getBlockZ());
        return l;
    }

    // ---------- el aldeano general ----------
    private boolean esNpc(Entity e) { return e.getPersistentDataContainer().has(marca, PersistentDataType.BYTE); }

    /** Crea el NPC donde esta parado el jugador, mirando hacia el. */
    void crearGeneral(Player p) {
        Location l = p.getLocation().clone();
        l.setYaw(p.getLocation().getYaw() + 180f);
        l.setPitch(0f);
        Villager v = l.getWorld().spawn(l, Villager.class, a -> {
            a.getPersistentDataContainer().set(marca, PersistentDataType.BYTE, (byte) 1);
            a.setAI(false);
            a.setInvulnerable(true);
            a.setSilent(true);
            a.setCollidable(false);
            a.setPersistent(true);
            a.setRemoveWhenFarAway(false);
            a.setProfession(Villager.Profession.LIBRARIAN);
            a.setVillagerLevel(5);
            a.customName(Component.text("TRABAJOS", NamedTextColor.RED, TextDecoration.BOLD));
            a.setCustomNameVisible(true);
        });
        Msg.ok(p, "Listo: puse el NPC de Trabajos en " + v.getLocation().getBlockX() + ", " + v.getLocation().getBlockY() + ", " + v.getLocation().getBlockZ() + ".");
    }

    // ---------- los NPC de cada trabajo ----------

    private static String idDe(Entity e) {
        for (String t : e.getScoreboardTags()) if (t.startsWith(TAG + "_")) return t.substring(TAG.length() + 1);
        return null;
    }

    private static Trabajo trabajoDe(Entity e) { return e instanceof Mannequin ? Trabajo.de(idDe(e)) : null; }

    /** true si hay un NPC de ese trabajo a menos de tantos bloques del jugador. */
    boolean cercaDeNpc(Player p, Trabajo t, double dist) {
        for (Entity e : p.getNearbyEntities(dist, dist, dist)) {
            if (trabajoDe(e) == t && e.getScoreboardTags().contains(TAG)) return true;
        }
        return false;
    }

    private static String num(double d) { return String.format(Locale.GERMANY, "%,d", (long) d); }

    /** Crea el NPC del trabajo donde esta parado el jugador, mirando hacia el, con su titulo y su panel. Va por consola porque el skin sale del pack. */
    void crear(Player p, Trabajo t) {
        Location l = p.getLocation();
        float yaw = l.getYaw() + 180f;
        String dim = l.getWorld().getKey().toString();
        String tag = TAG + "_" + t.id;
        String cmd = String.format(Locale.ROOT,
                "execute in %s positioned %.2f %.2f %.2f run summon minecraft:mannequin ~ ~ ~ {Rotation:[%.1ff,0.0f],profile:{texture:\"3y33:npc/%s\",model:\"wide\"},"
                        + "immovable:1b,hide_description:1b,Invulnerable:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"%s\",\"%s\"],"
                        + "equipment:{mainhand:{id:\"minecraft:%s\",count:1}}}",
                dim, l.getX(), l.getY(), l.getZ(), yaw, t.id, TAG, tag, HERRAMIENTA.get(t));
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        crearHolos(l, yaw, t);
        guardar();
        Msg.ok(p, "Listo: puse el NPC de " + t.nombre + " en " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + " con su titulo y su panel. Los hologramas se mueven con /trabajosadmin holo mover.");
        Bukkit.getScheduler().runTaskLater(pl, this::actualizar, 5L);
    }

    /** Saca los NPC de Trabajos (de trabajo y el aldeano general) a menos de 4 bloques, y los hologramas a menos de 6. Devuelve cuantos saco. */
    int quitar(Player p) {
        int n = 0;
        for (Entity e : p.getNearbyEntities(4, 4, 4)) {
            if (esNpc(e)) { e.remove(); n++; }
            else if (trabajoDe(e) != null && e.getScoreboardTags().contains(TAG)) { e.remove(); n++; }
        }
        if (n > 0) {
            for (Holo h : new ArrayList<>(holos.values())) {
                if (h.loc.getWorld().equals(p.getWorld()) && h.loc.distanceSquared(p.getLocation()) <= 36) { sacarCarteles(h); holos.remove(h.id); }
            }
            guardar();
        }
        return n;
    }

    private void sacarCarteles(Holo h) {
        if (h.titulo != null) { h.titulo.remove(); h.titulo = null; }
        for (TextDisplay d : h.paneles.values()) d.remove();
        h.paneles.clear();
    }

    /** Saca todos los carteles (al apagar el plugin). */
    void apagar() {
        for (Holo h : holos.values()) sacarCarteles(h);
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        for (Holo h : holos.values()) {
            TextDisplay d = h.paneles.remove(u);
            if (d != null) d.remove();
        }
    }

    private TextDisplay texto(Location l, boolean visible, Component c, float escala, int fondo, Display.Billboard billboard) {
        return l.getWorld().spawn(l, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setVisibleByDefault(visible);
            d.setBillboard(billboard);
            d.setSeeThrough(false);
            d.setShadowed(true);
            d.setDefaultBackground(false);
            d.setLineWidth(400);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(6);
            d.setBackgroundColor(Color.fromARGB(fondo, 0, 0, 0));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(escala, escala, escala), new AxisAngle4f()));
            d.text(c);
        });
    }

    private Component titulo(Trabajo t) {
        return Component.text("Misiones", NamedTextColor.GOLD, TextDecoration.BOLD).appendNewline()
                .append(Component.text(t.nombre, t.color, TextDecoration.BOLD));
    }

    private final Map<UUID, String> nombres = new HashMap<>();

    private String nombreDe(UUID u) {
        String n = nombres.get(u);
        if (n != null) return n;
        OfflinePlayer o = Bukkit.getOfflinePlayer(u);
        if (o.getName() == null) return "?";
        nombres.put(u, o.getName());
        return o.getName();
    }

    private Component panel(Trabajo t, Player p) {
        Misiones.Global g = pl.misiones().global(t);
        int pct = g.objetivo <= 0 ? 0 : (int) Math.min(100, Math.floor(g.total * 100.0 / g.objetivo));
        List<Map.Entry<UUID, Integer>> top = new ArrayList<>(g.aportes.entrySet());
        top.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        int mio = pl.misiones().aporteDe(t, p);
        String puesto = "—";
        for (int i = 0; i < top.size(); i++) if (top.get(i).getKey().equals(p.getUniqueId())) { puesto = "#" + (i + 1); break; }
        NamedTextColor gris = NamedTextColor.GRAY;
        Component c = Component.text("MISIÓN GLOBAL", TextColor.color(0xFFAA00), TextDecoration.BOLD).appendNewline()
                .append(pl.misiones().pedidoGlobal(t).decoration(TextDecoration.BOLD, true)).appendNewline();
        if (g.completada) {
            c = c.append(Component.text("¡Completada!", NamedTextColor.GREEN, TextDecoration.BOLD)).appendNewline();
        } else {
            c = c.append(Component.text(num(g.total) + " / " + num(g.objetivo), NamedTextColor.WHITE))
                    .append(Component.text(" (" + pct + "%)", pct >= 100 ? NamedTextColor.GREEN : NamedTextColor.YELLOW)).appendNewline();
        }
        TextColor naranja = TextColor.color(0xFFAA00), verde = TextColor.color(0x55FF55);
        double parte = pl.misiones().parte(t, p.getUniqueId());
        Misiones.Rar rar = pl.misiones().rarezaDe(t);
        c = c.append(Component.text("Pozo: ", gris)).append(Component.text("$" + Msg.num(pl.misiones().pozo(t), 0), verde))
                .append(Component.text(" · " + rar.nombre, rar.color)).appendNewline()
                .append(Component.text("Tu aporte: ", gris)).append(Component.text(num(mio), verde))
                .append(Component.text(parte > 0 ? " (" + Msg.num(parte * 100, 1) + "%)" : "", gris)).appendNewline()
                .append(Component.text("Tu puesto ", gris)).append(Component.text(puesto, naranja, TextDecoration.BOLD)).appendNewline();
        for (int i = 0; i < 5; i++) {
            if (i > 0) c = c.appendNewline();
            if (i < top.size()) {
                c = c.append(Component.text("#" + (i + 1), naranja, TextDecoration.BOLD))
                        .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(nombreDe(top.get(i).getKey()), NamedTextColor.WHITE))
                        .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(num(top.get(i).getValue()), verde));
            } else c = c.append(Component.text("#" + (i + 1) + " — vacío", NamedTextColor.DARK_GRAY));
        }
        return c;
    }

    /** Saca el gorro 3D de prueba (3y33:gorro/...) si algun NPC todavia lo tiene puesto. */
    private void sacarGorroViejo(Mannequin m) {
        ItemStack ya = m.getEquipment().getHelmet();
        if (ya != null && ya.hasItemMeta() && ya.getItemMeta().hasItemModel() && "3y33".equals(ya.getItemMeta().getItemModel().getNamespace())) {
            m.getEquipment().setHelmet(null);
        }
    }

    private record NpcMira(Mannequin m, float yaw0) { }
    private List<NpcMira> miran = new ArrayList<>();
    private long miranHasta;

    /** Busca los NPC cargados (barrer las entidades del mundo es caro: se hace cada 3 segundos, no a cada giro). */
    private void reconstruirMiran() {
        List<NpcMira> nueva = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            if (w.getPlayers().isEmpty()) continue;
            for (Mannequin m : w.getEntitiesByClass(Mannequin.class)) {
                if (!m.getScoreboardTags().contains(TAG)) continue;
                Float guardado = m.getPersistentDataContainer().get(yaw0, PersistentDataType.FLOAT);
                if (guardado == null) { guardado = m.getLocation().getYaw(); m.getPersistentDataContainer().set(yaw0, PersistentDataType.FLOAT, guardado); }
                nueva.add(new NpcMira(m, guardado));
            }
        }
        miran = nueva;
    }

    /** Cada 4 ticks: el NPC mira al jugador mas cercano (a menos de 8 bloques); si no hay nadie, vuelve a su direccion. */
    private void mirar() {
        long ahora = System.currentTimeMillis();
        if (ahora >= miranHasta) { reconstruirMiran(); miranHasta = ahora + 3000L; }
        for (NpcMira n : miran) {
            Mannequin m = n.m();
            if (!m.isValid()) continue;
            World w = m.getWorld();
            Location o = m.getLocation();
            Player cerca = null;
            double mejor = 8 * 8;
            for (Player p : w.getPlayers()) {
                double d = p.getLocation().distanceSquared(o);
                if (d < mejor) { mejor = d; cerca = p; }
            }
            float yaw = n.yaw0(), pitch = 0f;
            if (cerca != null) {
                Location ojos = cerca.getEyeLocation();
                double dx = ojos.getX() - o.getX(), dz = ojos.getZ() - o.getZ();
                double dy = ojos.getY() - (o.getY() + 1.6);
                yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            }
            if (Math.abs(o.getPitch() - pitch) < 1.5f && Math.abs(((o.getYaw() - yaw + 540f) % 360f) - 180f) < 1.5f) continue;
            m.setRotation(yaw, pitch);
        }
    }

    /** Cada 5 segundos (para no cargar el servidor): pone, saca y actualiza los hologramas: el titulo lo ve todo el mundo y el panel es uno por jugador cercano. */
    void actualizar() {
        for (Holo h : holos.values()) {
            World w = h.loc.getWorld();
            if (w == null || !w.isChunkLoaded(h.loc.getBlockX() >> 4, h.loc.getBlockZ() >> 4)) { sacarCarteles(h); continue; }
            if (!h.panel) {
                if (h.titulo == null || !h.titulo.isValid()) h.titulo = texto(h.loc, true, titulo(h.trabajo), 2.0f, 0, Display.Billboard.CENTER);
                continue;
            }
            Iterator<Map.Entry<UUID, TextDisplay>> it = h.paneles.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, TextDisplay> en = it.next();
                Player p = Bukkit.getPlayer(en.getKey());
                if (p == null || !p.isOnline() || !p.getWorld().equals(w) || p.getLocation().distanceSquared(h.loc) > ALCANCE * ALCANCE || !en.getValue().isValid()) {
                    en.getValue().remove();
                    it.remove();
                }
            }
            for (Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(h.loc) > ALCANCE * ALCANCE) continue;
                TextDisplay d = h.paneles.get(p.getUniqueId());
                Component txt = panel(h.trabajo, p);
                if (d == null) {
                    if (h.yaw == null) orientar(h);
                    d = texto(ubicacion(h), false, txt, 0.85f, 0, Display.Billboard.FIXED);
                    h.paneles.put(p.getUniqueId(), d);
                    p.showEntity(pl, d);
                } else d.text(txt);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void alTocar(PlayerInteractEntityEvent e) {
        Entity x = e.getRightClicked();
        Trabajo t = trabajoDe(x);
        if (t != null && x.getScoreboardTags().contains(TAG)) {
            e.setCancelled(true);
            if (e.getHand() != EquipmentSlot.HAND) return;
            pl.menus().misiones(e.getPlayer(), t);
            return;
        }
        if (!esNpc(x)) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        pl.menus().principal(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void alDanar(EntityDamageEvent e) {
        Entity x = e.getEntity();
        if (esNpc(x) || (x instanceof Mannequin && x.getScoreboardTags().contains(TAG))) e.setCancelled(true);
    }
}
