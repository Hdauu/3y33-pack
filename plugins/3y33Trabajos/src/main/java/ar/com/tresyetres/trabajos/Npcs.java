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
        UNIDAD.put(Trabajo.MINERO, "menas");
        UNIDAD.put(Trabajo.LENADOR, "troncos");
        UNIDAD.put(Trabajo.GRANJERO, "cultivos");
        UNIDAD.put(Trabajo.CAZADOR, "mobs");
        UNIDAD.put(Trabajo.PESCADOR, "pescas");
    }

    /** Los carteles de un NPC: el titulo (lo ve todo el mundo) y un panel por jugador cercano. */
    private static final class Carteles {
        TextDisplay titulo;
        final Map<UUID, TextDisplay> paneles = new HashMap<>();
    }

    private final TrabajosPlugin pl;
    private final NamespacedKey marca;
    private final NamespacedKey yaw0;
    private final Map<UUID, Carteles> carteles = new HashMap<>();

    Npcs(TrabajosPlugin pl) {
        this.pl = pl;
        this.marca = new NamespacedKey(pl, "npc-trabajos");
        this.yaw0 = new NamespacedKey(pl, "npc-yaw0");
        Bukkit.getScheduler().runTaskTimer(pl, this::actualizar, 40L, 20L);
        Bukkit.getScheduler().runTaskTimer(pl, this::mirar, 40L, 2L);
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

    private static String num(double d) { return String.format(Locale.GERMANY, "%,d", (long) d); }

    /** Crea el NPC del trabajo donde esta parado el jugador, mirando hacia el. Va por consola porque el skin sale del pack. */
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
        Msg.ok(p, "Listo: puse el NPC de " + t.nombre + " en " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + ". Ponele un pedestal abajo.");
        Bukkit.getScheduler().runTaskLater(pl, this::actualizar, 5L);
    }

    /** Saca los NPC de Trabajos (de trabajo y el aldeano general) que esten a menos de 4 bloques del jugador. Devuelve cuantos saco. */
    int quitar(Player p) {
        int n = 0;
        for (Entity e : p.getNearbyEntities(4, 4, 4)) {
            if (esNpc(e)) { e.remove(); n++; }
            else if (trabajoDe(e) != null && e.getScoreboardTags().contains(TAG)) { sacarCarteles(e.getUniqueId()); e.remove(); n++; }
        }
        return n;
    }

    private void sacarCarteles(UUID id) {
        Carteles c = carteles.remove(id);
        if (c == null) return;
        if (c.titulo != null) c.titulo.remove();
        for (TextDisplay pa : c.paneles.values()) pa.remove();
    }

    /** Saca todos los carteles (al apagar el plugin). */
    void apagar() {
        for (UUID id : new ArrayList<>(carteles.keySet())) sacarCarteles(id);
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

    private static String nombreDe(UUID u) {
        OfflinePlayer o = Bukkit.getOfflinePlayer(u);
        return o.getName() != null ? o.getName() : "?";
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
                .append(Component.text(MISION.get(t), t.color, TextDecoration.BOLD)).appendNewline();
        if (g.completada) {
            c = c.append(Component.text("¡Completada!", NamedTextColor.GREEN, TextDecoration.BOLD)).appendNewline();
        } else {
            c = c.append(Component.text(num(g.total) + " / " + num(g.objetivo) + " " + UNIDAD.get(t), NamedTextColor.WHITE))
                    .append(Component.text(" (" + pct + "%)", pct >= 100 ? NamedTextColor.GREEN : NamedTextColor.YELLOW)).appendNewline();
        }
        TextColor naranja = TextColor.color(0xFFAA00), verde = TextColor.color(0x55FF55);
        c = c.append(Component.text("Tu aporte: ", gris)).append(Component.text(num(mio), verde)).appendNewline()
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

    /** El yaw con el que se creo el NPC (no cambia aunque mire a los jugadores). */
    private float yawFijo(Mannequin m) {
        Float g = m.getPersistentDataContainer().get(yaw0, PersistentDataType.FLOAT);
        return g != null ? g : m.getLocation().getYaw();
    }

    /** El panel queda fijo a la izquierda de como se ve al NPC de frente (el NPC mira hacia donde se creo). */
    private static Location posPanel(Location base, float yaw) {
        double r = Math.toRadians(yaw);
        return base.clone().add(-Math.cos(r) * 2.1, 1.7, -Math.sin(r) * 2.1);
    }

    /** Saca el gorro 3D de prueba (3y33:gorro/...) si algun NPC todavia lo tiene puesto. */
    private void sacarGorroViejo(Mannequin m) {
        ItemStack ya = m.getEquipment().getHelmet();
        if (ya != null && ya.hasItemMeta() && ya.getItemMeta().hasItemModel() && "3y33".equals(ya.getItemMeta().getItemModel().getNamespace())) {
            m.getEquipment().setHelmet(null);
        }
    }

    /** Cada 2 ticks: el NPC mira al jugador mas cercano (a menos de 8 bloques); si no hay nadie, vuelve a su direccion. */
    private void mirar() {
        for (World w : Bukkit.getWorlds()) {
            if (w.getPlayers().isEmpty()) continue;
            for (Mannequin m : w.getEntitiesByClass(Mannequin.class)) {
                if (!m.getScoreboardTags().contains(TAG)) continue;
                Location o = m.getLocation();
                Float guardado = m.getPersistentDataContainer().get(yaw0, PersistentDataType.FLOAT);
                if (guardado == null) { guardado = o.getYaw(); m.getPersistentDataContainer().set(yaw0, PersistentDataType.FLOAT, guardado); }
                float inicial = guardado;
                Player cerca = null;
                double mejor = 8 * 8;
                for (Player p : w.getPlayers()) {
                    double d = p.getLocation().distanceSquared(o);
                    if (d < mejor) { mejor = d; cerca = p; }
                }
                float yaw = inicial, pitch = 0f;
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
    }

    /** Cada segundo: crea o saca los carteles de cada NPC y actualiza el panel de cada jugador cercano. */
    private void actualizar() {
        List<UUID> vistos = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            for (Mannequin m : w.getEntitiesByClass(Mannequin.class)) {
                if (!m.getScoreboardTags().contains(TAG)) continue;
                Trabajo t = trabajoDe(m);
                if (t == null) continue;
                vistos.add(m.getUniqueId());
                Carteles c = carteles.computeIfAbsent(m.getUniqueId(), k -> new Carteles());
                Location base = m.getLocation();
                sacarGorroViejo(m);
                if (c.titulo == null || !c.titulo.isValid()) c.titulo = texto(base.clone().add(0, 2.75, 0), true, titulo(t), 2.0f, 0, Display.Billboard.CENTER);
                Location lp = posPanel(base, yawFijo(m));
                Iterator<Map.Entry<UUID, TextDisplay>> it = c.paneles.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<UUID, TextDisplay> en = it.next();
                    Player p = Bukkit.getPlayer(en.getKey());
                    if (p == null || !p.isOnline() || !p.getWorld().equals(w) || p.getLocation().distanceSquared(base) > ALCANCE * ALCANCE || !en.getValue().isValid()) {
                        en.getValue().remove();
                        it.remove();
                    }
                }
                for (Player p : w.getPlayers()) {
                    if (p.getLocation().distanceSquared(base) > ALCANCE * ALCANCE) continue;
                    TextDisplay d = c.paneles.get(p.getUniqueId());
                    Component txt = panel(t, p);
                    if (d == null) {
                        d = texto(lp, false, txt, 0.85f, 0, Display.Billboard.VERTICAL);
                        c.paneles.put(p.getUniqueId(), d);
                        p.showEntity(pl, d);
                    } else d.text(txt);
                }
            }
        }
        for (UUID id : new ArrayList<>(carteles.keySet())) if (!vistos.contains(id)) sacarCarteles(id);
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
