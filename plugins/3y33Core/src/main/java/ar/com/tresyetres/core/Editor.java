package ar.com.tresyetres.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.hologram.Hologram;
import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;

import java.util.*;

/**
 * Editor de hologramas (FancyHolograms) y NPCs (FancyNpcs) con un panel de botones.
 * Usa directamente la API de FancyHolograms y FancyNpcs: lee las posiciones en vivo y los mueve sin pasar por comandos.
 */
final class Editor implements Listener {

    private enum Tipo { HOLOGRAMA, NPC }

    private record Entrada(Tipo tipo, String nombre, Location lugar) {}

    private record Sel(Tipo tipo, String nombre) {}

    private record Panel(Sel sel) implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return Bukkit.createInventory(this, 9); }
    }

    private record Lista(List<Entrada> items) implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return Bukkit.createInventory(this, 9); }
    }

    private static final double[] PASOS = {0.1, 0.5, 1.0};

    private final Core core;
    private final NamespacedKey clave;
    private final Map<UUID, Sel> seleccion = new HashMap<>();
    private final Map<UUID, Integer> paso = new HashMap<>();

    Editor(Core core) {
        this.core = core;
        this.clave = new NamespacedKey(core, "editor");
    }

    // ------------------------------------------------------------------ lectura (API de los plugins)

    private List<Entrada> leer() {
        List<Entrada> r = new ArrayList<>();
        if (Bukkit.getPluginManager().isPluginEnabled("FancyHolograms")) {
            try {
                for (Hologram h : FancyHologramsPlugin.get().getHologramManager().getHolograms()) {
                    Location l = h.getData().getLocation();
                    if (l != null && l.getWorld() != null) r.add(new Entrada(Tipo.HOLOGRAMA, h.getName(), l));
                }
            } catch (Throwable t) {
                core.getLogger().warning("No pude leer los hologramas: " + t);
            }
        }
        if (Bukkit.getPluginManager().isPluginEnabled("FancyNpcs")) {
            try {
                for (Npc n : FancyNpcsPlugin.get().getNpcManager().getAllNpcs()) {
                    Location l = n.getData().getLocation();
                    if (l != null && l.getWorld() != null) r.add(new Entrada(Tipo.NPC, n.getData().getName(), l));
                }
            } catch (Throwable t) {
                core.getLogger().warning("No pude leer los NPCs: " + t);
            }
        }
        return r;
    }

    private Entrada buscar(Sel s) {
        for (Entrada e : leer()) if (e.tipo == s.tipo && e.nombre.equalsIgnoreCase(s.nombre)) return e;
        return null;
    }

    /** Pone el holograma o NPC en la posición nueva (mantiene su rotación) y guarda. */
    private boolean aplicar(Entrada e, Location n) {
        try {
            if (e.tipo == Tipo.HOLOGRAMA) {
                Hologram h = FancyHologramsPlugin.get().getHologramManager().getHologram(e.nombre).orElse(null);
                if (h == null) return false;
                n.setYaw(h.getData().getLocation().getYaw());
                n.setPitch(h.getData().getLocation().getPitch());
                h.getData().setLocation(n);
                h.forceUpdate();
                h.refreshForViewers();
                FancyHologramsPlugin.get().getHologramManager().saveHolograms();
            } else {
                Npc npc = null;
                for (Npc x : FancyNpcsPlugin.get().getNpcManager().getAllNpcs())
                    if (x.getData().getName().equalsIgnoreCase(e.nombre)) { npc = x; break; }
                if (npc == null) return false;
                n.setYaw(npc.getData().getLocation().getYaw());
                n.setPitch(npc.getData().getLocation().getPitch());
                npc.getData().setLocation(n);
                npc.moveForAll();
                FancyNpcsPlugin.get().getNpcManager().saveNpcs(false);
            }
            return true;
        } catch (Throwable t) {
            core.getLogger().warning("No pude mover " + e.nombre + ": " + t);
            return false;
        }
    }

    // ------------------------------------------------------------------ comando /editor

    boolean comando(Player p, String[] args) {
        if (!p.hasPermission("tresyetres.staff")) { core.aviso(p, "<red>No tenés permiso."); return true; }
        if (args.length == 0) {
            darItem(p);
            Sel s = seleccion.get(p.getUniqueId());
            if (s != null && buscar(s) != null) abrirPanel(p, s);
            return true;
        }
        if (args[0].equalsIgnoreCase("lista")) { abrirLista(p); return true; }
        for (Entrada e : leer()) {
            if (e.nombre.equalsIgnoreCase(args[0])) {
                Sel s = new Sel(e.tipo, e.nombre);
                seleccion.put(p.getUniqueId(), s);
                abrirPanel(p, s);
                return true;
            }
        }
        core.aviso(p, "<red>No encontré ningún holograma o NPC con ese nombre. Usá <white>/editor lista</white>.");
        return true;
    }

    List<String> nombres() {
        List<String> r = new ArrayList<>(List.of("lista"));
        for (Entrada e : leer()) r.add(e.nombre);
        return r;
    }

    private boolean esEditor(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(clave, PersistentDataType.BYTE);
    }

    private void darItem(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (esEditor(it)) {
                core.aviso(p, "<gray>Ya tenés el editor. Clic derecho apuntando a un holograma o NPC.");
                return;
            }
        }
        ItemStack it = new ItemStack(Material.BLAZE_ROD);
        ItemMeta m = it.getItemMeta();
        m.displayName(sin(core.mm("<gradient:#E03131:#FF8787><bold>Editor 3y33</bold></gradient>")));
        m.lore(List.of(
                sin(core.mm("<gray>Clic derecho apuntando a un holograma o NPC:")),
                sin(core.mm("<gray>lo selecciona y abre el panel para moverlo.")),
                sin(core.mm("<gray>Agachado + clic derecho: lista de cercanos."))));
        m.getPersistentDataContainer().set(clave, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        p.getInventory().addItem(it);
        core.aviso(p, "<gray>Te di el <#FF6B6B>Editor 3y33</#FF6B6B>. Apuntá a un holograma o NPC y hacé clic derecho.");
    }

    // ------------------------------------------------------------------ selección

    @EventHandler
    public void alUsar(PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!esEditor(e.getItem())) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!p.hasPermission("tresyetres.staff")) return;
        if (p.isSneaking()) { abrirLista(p); return; }
        Entrada a = apuntado(p);
        if (a == null) {
            Sel s = seleccion.get(p.getUniqueId());
            if (s != null && buscar(s) != null) { abrirPanel(p, s); return; }
            core.aviso(p, "<red>No hay nada apuntado. Mirá un holograma o NPC (hasta 20 bloques) o agachate para ver la lista.");
            return;
        }
        Sel s = new Sel(a.tipo, a.nombre);
        seleccion.put(p.getUniqueId(), s);
        abrirPanel(p, s);
    }

    private Entrada apuntado(Player p) {
        Location ojo = p.getEyeLocation();
        Vector dir = ojo.getDirection().normalize();
        Entrada mejor = null;
        double mejorD = Double.MAX_VALUE;
        for (Entrada e : leer()) {
            if (!e.lugar.getWorld().equals(p.getWorld())) continue;
            Vector pt = e.lugar.toVector().add(new Vector(0, e.tipo == Tipo.NPC ? 1.0 : 0.5, 0));
            Vector v = pt.subtract(ojo.toVector());
            double t = v.dot(dir);
            if (t < 0 || t > 20) continue;
            double perp = v.clone().subtract(dir.clone().multiply(t)).length();
            double margen = e.tipo == Tipo.NPC ? 1.4 : 1.6;
            if (perp < margen && perp < mejorD) { mejor = e; mejorD = perp; }
        }
        return mejor;
    }

    // ------------------------------------------------------------------ menús

    private Component sin(Component c) { return c.decoration(TextDecoration.ITALIC, false); }

    private ItemStack boton(Material m, String nombre, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(sin(core.mm(nombre)));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(sin(core.mm(s)));
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    private double pasoDe(Player p) { return PASOS[paso.getOrDefault(p.getUniqueId(), 1)]; }

    private static String num(double d) { return String.format(Locale.ROOT, "%.2f", d).replaceAll("\\.?0+$", ""); }

    private void abrirPanel(Player p, Sel sel) {
        Entrada e = buscar(sel);
        if (e == null) { core.aviso(p, "<red>Ese holograma o NPC ya no existe."); return; }
        Inventory inv = Bukkit.createInventory(new Panel(sel), 45,
                core.mm("<dark_gray>Editor » <#E03131>" + sel.nombre));
        pintar(inv, p, e);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    private void pintar(Inventory inv, Player p, Entrada e) {
        ItemStack relleno = boton(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, relleno);
        double s = pasoDe(p);
        String t = e.tipo == Tipo.NPC ? "NPC" : "Holograma";
        inv.setItem(0, boton(Material.PAPER, "<#FF6B6B><bold>" + e.nombre + "</bold>",
                "<gray>Tipo: <white>" + t,
                "<gray>Mundo: <white>" + e.lugar.getWorld().getName(),
                "<gray>X: <white>" + num(e.lugar.getX()) + " <gray>Y: <white>" + num(e.lugar.getY()) + " <gray>Z: <white>" + num(e.lugar.getZ())));
        inv.setItem(2, boton(Material.ARROW, "<white><bold>Norte", "<gray>Mueve " + num(s) + " hacia el norte (-Z)"));
        inv.setItem(10, boton(Material.ARROW, "<white><bold>Oeste", "<gray>Mueve " + num(s) + " hacia el oeste (-X)"));
        inv.setItem(12, boton(Material.ARROW, "<white><bold>Este", "<gray>Mueve " + num(s) + " hacia el este (+X)"));
        inv.setItem(20, boton(Material.ARROW, "<white><bold>Sur", "<gray>Mueve " + num(s) + " hacia el sur (+Z)"));
        inv.setItem(11, boton(Material.CLOCK, "<#FF6B6B><bold>Paso: " + num(s) + " bloques",
                "<gray>Clic para cambiar", "<dark_gray>0.1 · 0.5 · 1"));
        inv.setItem(13, boton(Material.SPECTRAL_ARROW, "<green><bold>Arriba", "<gray>Sube " + num(s)));
        inv.setItem(22, boton(Material.SPECTRAL_ARROW, "<red><bold>Abajo", "<gray>Baja " + num(s)));
        inv.setItem(6, boton(Material.ARROW, "<white><bold>Adelante", "<gray>Hacia donde mirás"));
        inv.setItem(14, boton(Material.ARROW, "<white><bold>Izquierda", "<gray>A tu izquierda"));
        inv.setItem(16, boton(Material.ARROW, "<white><bold>Derecha", "<gray>A tu derecha"));
        inv.setItem(24, boton(Material.ARROW, "<white><bold>Atrás", "<gray>Hacia vos"));
        inv.setItem(15, boton(Material.ENDER_PEARL, "<#FF6B6B><bold>Traer acá", "<gray>Lo pone donde estás parado"));
        inv.setItem(36, boton(Material.BOOK, "<white><bold>Lista", "<gray>Holograma o NPC más cercanos"));
        inv.setItem(44, boton(Material.BARRIER, "<red><bold>Cerrar"));
    }

    private void abrirLista(Player p) {
        List<Entrada> todas = new ArrayList<>();
        for (Entrada e : leer()) if (e.lugar.getWorld().equals(p.getWorld())) todas.add(e);
        todas.sort(Comparator.comparingDouble(e -> e.lugar.distanceSquared(p.getLocation())));
        if (todas.size() > 45) todas = new ArrayList<>(todas.subList(0, 45));
        if (todas.isEmpty()) { core.aviso(p, "<gray>No hay hologramas ni NPCs en este mundo todavía."); return; }
        Inventory inv = Bukkit.createInventory(new Lista(todas), 54, core.mm("<dark_gray>Editor » <#E03131>Cercanos"));
        for (int i = 0; i < todas.size(); i++) {
            Entrada e = todas.get(i);
            inv.setItem(i, boton(e.tipo == Tipo.NPC ? Material.PLAYER_HEAD : Material.NAME_TAG,
                    "<white><bold>" + e.nombre,
                    "<gray>" + (e.tipo == Tipo.NPC ? "NPC" : "Holograma") + " · a <white>" + (int) e.lugar.distance(p.getLocation()) + "</white> bloques",
                    "<dark_gray>Clic para editar"));
        }
        inv.setItem(53, boton(Material.BARRIER, "<red><bold>Cerrar"));
        p.openInventory(inv);
    }

    @EventHandler
    public void alArrastrar(InventoryDragEvent e) {
        InventoryHolder h = e.getView().getTopInventory().getHolder();
        if (h instanceof Panel || h instanceof Lista) e.setCancelled(true);
    }

    @EventHandler
    public void alClickear(InventoryClickEvent e) {
        InventoryHolder h = e.getView().getTopInventory().getHolder();
        if (!(h instanceof Panel) && !(h instanceof Lista)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getRawSlot();
        if (h instanceof Lista l) {
            if (slot == 53) { p.closeInventory(); return; }
            if (slot >= 0 && slot < l.items.size()) {
                Entrada en = l.items.get(slot);
                Sel s = new Sel(en.tipo, en.nombre);
                seleccion.put(p.getUniqueId(), s);
                Bukkit.getScheduler().runTask(core, () -> abrirPanel(p, s));
            }
            return;
        }
        Panel panel = (Panel) h;
        switch (slot) {
            case 44 -> { p.closeInventory(); return; }
            case 36 -> { Bukkit.getScheduler().runTask(core, () -> abrirLista(p)); return; }
            case 11 -> {
                paso.put(p.getUniqueId(), (paso.getOrDefault(p.getUniqueId(), 1) + 1) % PASOS.length);
                refrescar(p, panel);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
                return;
            }
            default -> { }
        }
        double s = pasoDe(p);
        double[] d = switch (slot) {
            case 2 -> new double[]{0, 0, -s};
            case 20 -> new double[]{0, 0, s};
            case 10 -> new double[]{-s, 0, 0};
            case 12 -> new double[]{s, 0, 0};
            case 13 -> new double[]{0, s, 0};
            case 22 -> new double[]{0, -s, 0};
            case 6, 24, 14, 16 -> relativo(p, slot, s);
            default -> null;
        };
        if (slot == 15) { traer(p, panel); return; }
        if (d == null) return;
        mover(p, panel, d[0], d[1], d[2]);
    }

    /** Adelante/atrás/izquierda/derecha según hacia dónde mira el jugador (alineado al eje más cercano). */
    private double[] relativo(Player p, int slot, double s) {
        float yaw = p.getLocation().getYaw();
        yaw = ((yaw % 360) + 360) % 360;
        double fx, fz;
        if (yaw >= 315 || yaw < 45) { fx = 0; fz = 1; }
        else if (yaw < 135) { fx = -1; fz = 0; }
        else if (yaw < 225) { fx = 0; fz = -1; }
        else { fx = 1; fz = 0; }
        double rx = -fz, rz = fx;
        return switch (slot) {
            case 6 -> new double[]{fx * s, 0, fz * s};
            case 24 -> new double[]{-fx * s, 0, -fz * s};
            case 16 -> new double[]{rx * s, 0, rz * s};
            default -> new double[]{-rx * s, 0, -rz * s};
        };
    }

    // ------------------------------------------------------------------ mover

    private void mover(Player p, Panel panel, double dx, double dy, double dz) {
        Entrada e = buscar(panel.sel);
        if (e == null) { p.closeInventory(); core.aviso(p, "<red>Ese holograma o NPC ya no existe."); return; }
        Location n = e.lugar.clone().add(dx, dy, dz);
        if (!aplicar(e, n)) { core.aviso(p, "<red>No se pudo mover. Mirá la consola."); return; }
        feedback(p, n);
        refrescar(p, panel);
    }

    private void traer(Player p, Panel panel) {
        Entrada e = buscar(panel.sel);
        if (e == null) { p.closeInventory(); return; }
        Location n = p.getLocation().clone();
        if (!aplicar(e, n)) { core.aviso(p, "<red>No se pudo mover. Mirá la consola."); return; }
        feedback(p, n);
        refrescar(p, panel);
    }

    private void feedback(Player p, Location n) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
        p.spawnParticle(Particle.END_ROD, n.clone().add(0, 0.3, 0), 6, 0.1, 0.1, 0.1, 0.01);
    }

    private void refrescar(Player p, Panel panel) {
        Bukkit.getScheduler().runTaskLater(core, () -> {
            Inventory top = p.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof Panel pn && pn.sel.equals(panel.sel)) {
                Entrada e = buscar(panel.sel);
                if (e != null) pintar(top, p, e);
            }
        }, 2L);
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent e) {
        seleccion.remove(e.getPlayer().getUniqueId());
        paso.remove(e.getPlayer().getUniqueId());
    }
}
