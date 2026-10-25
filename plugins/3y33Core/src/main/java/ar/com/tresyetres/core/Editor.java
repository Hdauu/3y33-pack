package ar.com.tresyetres.core;

import de.oliver.fancyholograms.api.data.TextHologramData;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;
import org.joml.Vector3f;
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

    private record Estilo(Sel sel) implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return Bukkit.createInventory(this, 9); }
    }

    private record Pedido(String nombre, String modo) {}

    private record Preset(String nombre, String a, String b, Material m) {}

    private static final Preset[] PRESETS = {
            new Preset("Marca 3y33", "#E03131", "#FF8787", Material.RED_DYE),
            new Preset("Verde", "#2F9E44", "#8CE99A", Material.LIME_DYE),
            new Preset("Dorado", "#F08C00", "#FFE066", Material.YELLOW_DYE),
            new Preset("Violeta", "#9C36B5", "#E599F7", Material.PURPLE_DYE),
            new Preset("Azul", "#1971C2", "#74C0FC", Material.LIGHT_BLUE_DYE),
            new Preset("Naranja", "#E8590C", "#FFC078", Material.ORANGE_DYE),
            new Preset("Discord", "#4263EB", "#91A7FF", Material.BLUE_DYE),
            new Preset("Rosa", "#D6336C", "#FAA2C1", Material.PINK_DYE),
            new Preset("Cian", "#0C8599", "#99E9F2", Material.CYAN_DYE),
            new Preset("Fuego", "#E03131", "#FFD43B", Material.MAGMA_CREAM),
            new Preset("Lima", "#5C940D", "#D8F5A2", Material.SLIME_BALL),
            new Preset("Plata", "#ADB5BD", "#F8F9FA", Material.LIGHT_GRAY_DYE),
            new Preset("Oro", "#E67700", "#FFF3BF", Material.GOLD_INGOT),
            new Preset("Amatista", "#7048E8", "#D0BFFF", Material.AMETHYST_SHARD)};

    private static final String[] FONDO_NOMBRES = {"Oscuro", "Muy oscuro", "Rojo oscuro", "Sin fondo", "Normal de Minecraft"};

    private static Color fondo(int i) {
        return switch (i) {
            case 0 -> Color.fromARGB(110, 0, 0, 0);
            case 1 -> Color.fromARGB(190, 0, 0, 0);
            case 2 -> Color.fromARGB(150, 90, 8, 8);
            case 3 -> Hologram.TRANSPARENT;
            default -> null;
        };
    }

    private final Map<UUID, Pedido> pidiendo = new HashMap<>();
    private final Map<UUID, Integer> fondoIdx = new HashMap<>();

    private record Crear() implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return Bukkit.createInventory(this, 9); }
    }

    private static final double[] PASOS = {0.1, 0.5, 1.0};

    private final Core core;
    private final NamespacedKey clave;
    private final Map<UUID, Sel> seleccion = new HashMap<>();
    private final Map<UUID, Integer> paso = new HashMap<>();
    /** Quién pidió borrar y cuándo (hay que confirmar con un segundo clic). */
    private final Map<UUID, Long> borrando = new HashMap<>();

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

    /** Borra el holograma o NPC del server (y de su archivo). */
    private boolean eliminar(Entrada e) {
        try {
            if (e.tipo == Tipo.HOLOGRAMA) {
                Hologram h = FancyHologramsPlugin.get().getHologramManager().getHologram(e.nombre).orElse(null);
                if (h == null) return false;
                h.deleteHologram();
                FancyHologramsPlugin.get().getHologramManager().removeHologram(h);
                try { FancyHologramsPlugin.get().getHologramStorage().delete(h); } catch (Throwable ignorar) { }
                FancyHologramsPlugin.get().getHologramManager().saveHolograms();
            } else {
                Npc npc = null;
                for (Npc x : FancyNpcsPlugin.get().getNpcManager().getAllNpcs())
                    if (x.getData().getName().equalsIgnoreCase(e.nombre)) { npc = x; break; }
                if (npc == null) return false;
                npc.removeForAll();
                FancyNpcsPlugin.get().getNpcManager().removeNpc(npc);
                FancyNpcsPlugin.get().getNpcManager().saveNpcs(false);
            }
            return true;
        } catch (Throwable t) {
            core.getLogger().warning("No pude borrar " + e.nombre + ": " + t);
            return false;
        }
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
            else abrirLista(p);
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
                core.mm(MARCA + " <dark_gray>»</dark_gray> <white>" + sel.nombre));
        pintar(inv, p, e);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    private static String deg(String a, String b, String t) {
        return "<gradient:" + a + ":" + b + "><bold>" + t + "</bold></gradient>";
    }

    private static final String MARCA = deg("#E03131", "#FF8787", "Editor");

    private void relleno(Inventory inv) {
        ItemStack fondo = boton(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, fondo);
    }

    private void pintar(Inventory inv, Player p, Entrada e) {
        relleno(inv);
        double s = pasoDe(p);
        String paso = num(s);
        String t = e.tipo == Tipo.NPC ? "NPC" : "Holograma";
        inv.setItem(0, boton(e.tipo == Tipo.NPC ? Material.VILLAGER_SPAWN_EGG : Material.NAME_TAG,
                deg("#E03131", "#FF8787", e.nombre),
                "<gray>Tipo: <white>" + t,
                "<gray>Mundo: <white>" + e.lugar.getWorld().getName(),
                " ",
                "<#FF6B6B>X <white>" + num(e.lugar.getX()) + "  <#FF6B6B>Y <white>" + num(e.lugar.getY()) + "  <#FF6B6B>Z <white>" + num(e.lugar.getZ())));
        // direcciones fijas: rojo = este/oeste, azul = norte/sur, verde = arriba/abajo
        String azul1 = "#1C7ED6", azul2 = "#74C0FC", rojo1 = "#E03131", rojo2 = "#FF8787", verde1 = "#2F9E44", verde2 = "#8CE99A";
        inv.setItem(2, boton(Material.BLUE_CONCRETE, deg(azul1, azul2, "Norte"), "<gray>Mueve <white>" + paso + "</white> bloques al norte", "<dark_gray>Eje Z, hacia atrás en el mapa"));
        inv.setItem(20, boton(Material.BLUE_CONCRETE, deg(azul1, azul2, "Sur"), "<gray>Mueve <white>" + paso + "</white> bloques al sur", "<dark_gray>Eje Z, hacia adelante en el mapa"));
        inv.setItem(10, boton(Material.RED_CONCRETE, deg(rojo1, rojo2, "Oeste"), "<gray>Mueve <white>" + paso + "</white> bloques al oeste", "<dark_gray>Eje X negativo"));
        inv.setItem(12, boton(Material.RED_CONCRETE, deg(rojo1, rojo2, "Este"), "<gray>Mueve <white>" + paso + "</white> bloques al este", "<dark_gray>Eje X positivo"));
        inv.setItem(13, boton(Material.LIME_CONCRETE, deg(verde1, verde2, "Arriba"), "<gray>Sube <white>" + paso + "</white> bloques"));
        inv.setItem(22, boton(Material.GREEN_CONCRETE, deg(verde1, verde2, "Abajo"), "<gray>Baja <white>" + paso + "</white> bloques"));
        inv.setItem(11, boton(Material.CLOCK, deg("#E03131", "#FF8787", "Paso: " + paso),
                "<gray>Cuánto se mueve en cada clic", " ", "<dark_gray>Clic para cambiar", "<dark_gray>0.1  ·  0.5  ·  1"));
        // direcciones según hacia dónde mirás: amarillo
        String am1 = "#F08C00", am2 = "#FFE066";
        inv.setItem(6, boton(Material.YELLOW_CONCRETE, deg(am1, am2, "Adelante"), "<gray>Hacia donde estás mirando"));
        inv.setItem(24, boton(Material.YELLOW_CONCRETE, deg(am1, am2, "Atrás"), "<gray>Hacia vos"));
        inv.setItem(14, boton(Material.ORANGE_CONCRETE, deg(am1, am2, "Izquierda"), "<gray>A tu izquierda"));
        inv.setItem(16, boton(Material.ORANGE_CONCRETE, deg(am1, am2, "Derecha"), "<gray>A tu derecha"));
        inv.setItem(15, boton(Material.ENDER_PEARL, deg("#9C36B5", "#E599F7", "Traer acá"), "<gray>Lo pone donde estás parado"));
        if (e.tipo == Tipo.HOLOGRAMA)
            inv.setItem(38, boton(Material.PAINTING, deg("#D6336C", "#FAA2C1", "Estilo y color"),
                    "<gray>Colores, fondo, tamaño", "<gray>y texto del holograma"));
        inv.setItem(36, boton(Material.BOOK, deg("#1971C2", "#74C0FC", "Lista"), "<gray>Todos los hologramas y NPCs", "<gray>cercanos"));
        Long pedido = borrando.get(p.getUniqueId());
        if (pedido != null && System.currentTimeMillis() - pedido < 5000) {
            inv.setItem(40, boton(Material.TNT, deg("#C92A2A", "#FF6B6B", "¿Seguro? Clic para BORRAR"), "<red>Se borra para siempre.", "<gray>Si no querés, esperá unos segundos."));
        } else {
            inv.setItem(40, boton(Material.LAVA_BUCKET, deg("#C92A2A", "#FF6B6B", "Borrar"),
                    "<gray>Elimina este " + (e.tipo == Tipo.NPC ? "NPC" : "holograma"), "<dark_gray>Pide confirmación"));
        }
        inv.setItem(44, boton(Material.BARRIER, deg("#868E96", "#CED4DA", "Cerrar")));
    }

    private void abrirLista(Player p) {
        List<Entrada> todas = new ArrayList<>();
        for (Entrada e : leer()) if (e.lugar.getWorld().equals(p.getWorld())) todas.add(e);
        todas.sort(Comparator.comparingDouble(e -> e.lugar.distanceSquared(p.getLocation())));
        if (todas.size() > 45) todas = new ArrayList<>(todas.subList(0, 45));
        if (todas.isEmpty()) {
            core.aviso(p, "<gray>No hay hologramas ni NPCs en este mundo todavía. Elegí una plantilla para crear el primero.");
            abrirPlantillas(p);
            return;
        }
        Inventory inv = Bukkit.createInventory(new Lista(todas), 54, core.mm(MARCA + " <dark_gray>»</dark_gray> <white>Cercanos"));
        relleno(inv);
        for (int i = 0; i < todas.size(); i++) {
            Entrada e = todas.get(i);
            inv.setItem(i, boton(e.tipo == Tipo.NPC ? Material.PLAYER_HEAD : Material.NAME_TAG,
                    deg("#E03131", "#FF8787", e.nombre),
                    "<gray>" + (e.tipo == Tipo.NPC ? "NPC" : "Holograma") + " · a <white>" + (int) e.lugar.distance(p.getLocation()) + "</white> bloques",
                    "<dark_gray>Clic para editar"));
        }
        inv.setItem(49, boton(Material.NETHER_STAR, deg("#E03131", "#FF8787", "Crear holograma nuevo"), "<gray>Elegí una plantilla de 3y33", "<gray>y se crea donde estás parado."));
        inv.setItem(53, boton(Material.BARRIER, deg("#868E96", "#CED4DA", "Cerrar")));
        p.openInventory(inv);
    }

    private static final String[][] PLANT = {
            {"bienvenida", "NETHER_STAR", "<gradient:#E03131:#FF8787><bold>Bienvenida</bold></gradient>"},
            {"terrenos", "LIME_DYE", "<gradient:#2F9E44:#8CE99A><bold>Terrenos</bold></gradient>"},
            {"misiones", "YELLOW_DYE", "<gradient:#F08C00:#FFE066><bold>Misiones</bold></gradient>"},
            {"niveles", "PURPLE_DYE", "<gradient:#9C36B5:#E599F7><bold>Niveles</bold></gradient>"},
            {"reglas", "LIGHT_BLUE_DYE", "<gradient:#1971C2:#74C0FC><bold>Reglas</bold></gradient>"},
            {"explorar", "ORANGE_DYE", "<gradient:#E8590C:#FFC078><bold>Explorar</bold></gradient>"},
            {"discord", "BLUE_DYE", "<gradient:#4263EB:#91A7FF><bold>Discord</bold></gradient>"}};

    private void abrirPlantillas(Player p) {
        Inventory inv = Bukkit.createInventory(new Crear(), 27, core.mm(MARCA + " <dark_gray>»</dark_gray> <white>Crear holograma"));
        relleno(inv);
        for (int i = 0; i < PLANT.length; i++)
            inv.setItem(10 + i, boton(Material.valueOf(PLANT[i][1]), PLANT[i][2], "<gray>Clic para crearlo acá", "<dark_gray>Después lo acomodás con el editor"));
        inv.setItem(22, boton(Material.BARRIER, deg("#868E96", "#CED4DA", "Cerrar")));
        p.openInventory(inv);
    }

    @EventHandler
    public void alArrastrar(InventoryDragEvent e) {
        InventoryHolder h = e.getView().getTopInventory().getHolder();
        if (h instanceof Panel || h instanceof Lista || h instanceof Crear || h instanceof Estilo) e.setCancelled(true);
    }

    @EventHandler
    public void alClickear(InventoryClickEvent e) {
        InventoryHolder h = e.getView().getTopInventory().getHolder();
        if (!(h instanceof Panel) && !(h instanceof Lista) && !(h instanceof Crear) && !(h instanceof Estilo)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getRawSlot();
        if (h instanceof Estilo es) { clickEstilo(p, es, slot); return; }
        if (h instanceof Crear) {
            if (slot == 22) { p.closeInventory(); return; }
            int i = slot - 10;
            if (i >= 0 && i < PLANT.length) {
                p.closeInventory();
                String id = PLANT[i][0];
                Bukkit.getScheduler().runTask(core, () -> {
                    String creado = Plantillas.crear(core, p, id, null);
                    if (creado != null) {
                        core.aviso(p, "<gray>Holograma <white>" + creado + "</white> creado. Acomodalo con el panel.");
                        Bukkit.getScheduler().runTaskLater(core, () -> comando(p, new String[]{creado}), 3L);
                    }
                });
            }
            return;
        }
        if (h instanceof Lista l) {
            if (slot == 53) { p.closeInventory(); return; }
            if (slot == 49) { Bukkit.getScheduler().runTask(core, () -> abrirPlantillas(p)); return; }
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
            case 38 -> {
                Entrada en = buscar(panel.sel);
                if (en != null && en.tipo == Tipo.HOLOGRAMA) Bukkit.getScheduler().runTask(core, () -> abrirEstilo(p, panel.sel));
                return;
            }
            case 40 -> {
                Long pedido = borrando.get(p.getUniqueId());
                Entrada en = buscar(panel.sel);
                if (en == null) { p.closeInventory(); return; }
                if (pedido != null && System.currentTimeMillis() - pedido < 5000) {
                    borrando.remove(p.getUniqueId());
                    p.closeInventory();
                    seleccion.remove(p.getUniqueId());
                    if (eliminar(en)) {
                        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 1.0f);
                        core.aviso(p, "<gray>Borraste <white>" + en.nombre + "</white>.");
                    } else {
                        core.aviso(p, "<red>No se pudo borrar. Mirá la consola.");
                    }
                } else {
                    borrando.put(p.getUniqueId(), System.currentTimeMillis());
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
                    refrescar(p, panel);
                    Bukkit.getScheduler().runTaskLater(core, () -> refrescar(p, panel), 105L);
                }
                return;
            }
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

    // ------------------------------------------------------------------ estilo del holograma

    private Hologram holo(String nombre) {
        try {
            return FancyHologramsPlugin.get().getHologramManager().getHologram(nombre).orElse(null);
        } catch (Throwable t) { return null; }
    }

    private TextHologramData texto(Hologram h) {
        return h != null && h.getData() instanceof TextHologramData t ? t : null;
    }

    private void guardarHolo(Hologram h) {
        h.forceUpdate();
        h.refreshForViewers();
        FancyHologramsPlugin.get().getHologramManager().saveHolograms();
    }

    private void abrirEstilo(Player p, Sel sel) {
        TextHologramData t = texto(holo(sel.nombre));
        if (t == null) { core.aviso(p, "<red>Solo se puede dar estilo a hologramas de texto."); return; }
        Inventory inv = Bukkit.createInventory(new Estilo(sel), 45, core.mm(MARCA + " <dark_gray>»</dark_gray> <white>Estilo de " + sel.nombre));
        pintarEstilo(inv, p, t);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    private static int slotPreset(int i) { return i < 7 ? 10 + i : 19 + (i - 7); }

    private void pintarEstilo(Inventory inv, Player p, TextHologramData t) {
        relleno(inv);
        for (int i = 0; i < PRESETS.length; i++) {
            Preset pr = PRESETS[i];
            inv.setItem(slotPreset(i), boton(pr.m, deg(pr.a, pr.b, pr.nombre), "<gray>Color del título", "<dark_gray>Clic para aplicar"));
        }
        inv.setItem(4, boton(Material.PAINTING, deg("#D6336C", "#FAA2C1", "Color del título"), "<gray>Elegí un degradado", "<gray>para la primera línea."));
        inv.setItem(28, boton(Material.NAME_TAG, deg("#E03131", "#FF8787", "Cambiar título"), "<gray>Escribís el texto nuevo en el chat", "<gray>(mantiene el color)"));
        inv.setItem(29, boton(Material.WRITABLE_BOOK, deg("#2F9E44", "#8CE99A", "Agregar línea"), "<gray>Escribís la línea en el chat", "<gray>(sale en gris)"));
        int lineas = t.getText().size();
        inv.setItem(30, boton(Material.SHEARS, deg("#C92A2A", "#FF6B6B", "Quitar última línea"), "<gray>Tiene " + lineas + " línea" + (lineas == 1 ? "" : "s")));
        int fi = fondoIdx.getOrDefault(p.getUniqueId(), 0);
        inv.setItem(32, boton(Material.BLACK_STAINED_GLASS, deg("#868E96", "#CED4DA", "Fondo: " + FONDO_NOMBRES[fi]), "<gray>Clic para cambiar", "<dark_gray>Oscuro · Muy oscuro · Rojo · Sin fondo · Normal"));
        inv.setItem(33, boton(t.hasTextShadow() ? Material.LIME_DYE : Material.GRAY_DYE,
                deg("#868E96", "#CED4DA", "Sombra del texto: " + (t.hasTextShadow() ? "sí" : "no")), "<gray>Clic para cambiar"));
        float s = t.getScale().x;
        inv.setItem(39, boton(Material.RED_CONCRETE, deg("#E03131", "#FF8787", "Más chico"), "<gray>-0.25"));
        inv.setItem(40, boton(Material.PAPER, deg("#F08C00", "#FFE066", "Tamaño: " + num(s)), "<gray>1 es el tamaño normal"));
        inv.setItem(41, boton(Material.LIME_CONCRETE, deg("#2F9E44", "#8CE99A", "Más grande"), "<gray>+0.25"));
        inv.setItem(36, boton(Material.ARROW, deg("#1971C2", "#74C0FC", "Volver al panel")));
        inv.setItem(44, boton(Material.BARRIER, deg("#868E96", "#CED4DA", "Cerrar")));
    }

    private void clickEstilo(Player p, Estilo es, int slot) {
        if (slot == 44) { p.closeInventory(); return; }
        if (slot == 36) { Bukkit.getScheduler().runTask(core, () -> abrirPanelDe(p, es.sel)); return; }
        Hologram h = holo(es.sel.nombre);
        TextHologramData t = texto(h);
        if (t == null) { p.closeInventory(); core.aviso(p, "<red>Ese holograma ya no existe."); return; }
        try {
            for (int i = 0; i < PRESETS.length; i++) {
                if (slot != slotPreset(i)) continue;
                List<String> l = new ArrayList<>(t.getText());
                if (l.isEmpty()) l.add("Título");
                String plano = l.get(0).replaceAll("<[^>]*>", "").trim();
                if (plano.isEmpty()) plano = "Título";
                l.set(0, deg(PRESETS[i].a, PRESETS[i].b, plano));
                t.setText(l);
                guardarHolo(h);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.3f);
                refrescarEstilo(p, es);
                return;
            }
            switch (slot) {
                case 28 -> pedir(p, es, "titulo", "el <white>nuevo título</white>");
                case 29 -> pedir(p, es, "linea", "la <white>línea nueva</white>");
                case 30 -> {
                    List<String> l = new ArrayList<>(t.getText());
                    if (l.size() > 1) { l.remove(l.size() - 1); t.setText(l); guardarHolo(h); }
                    refrescarEstilo(p, es);
                }
                case 32 -> {
                    int fi = (fondoIdx.getOrDefault(p.getUniqueId(), 0) + 1) % FONDO_NOMBRES.length;
                    fondoIdx.put(p.getUniqueId(), fi);
                    t.setBackground(fondo(fi));
                    guardarHolo(h);
                    refrescarEstilo(p, es);
                }
                case 33 -> { t.setTextShadow(!t.hasTextShadow()); guardarHolo(h); refrescarEstilo(p, es); }
                case 39, 41 -> {
                    float s = Math.max(0.5f, Math.min(4f, t.getScale().x + (slot == 41 ? 0.25f : -0.25f)));
                    t.setScale(new Vector3f(s, s, s));
                    guardarHolo(h);
                    refrescarEstilo(p, es);
                }
                default -> { }
            }
        } catch (Throwable ex) {
            core.getLogger().warning("No pude cambiar el estilo de " + es.sel.nombre + ": " + ex);
            core.aviso(p, "<red>No se pudo cambiar. Mirá la consola.");
        }
    }

    private void abrirPanelDe(Player p, Sel sel) {
        if (buscar(sel) != null) abrirPanel(p, sel);
    }

    private void refrescarEstilo(Player p, Estilo es) {
        Bukkit.getScheduler().runTaskLater(core, () -> {
            Inventory top = p.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof Estilo e2 && e2.sel.equals(es.sel)) {
                TextHologramData t = texto(holo(es.sel.nombre));
                if (t != null) pintarEstilo(top, p, t);
            }
        }, 2L);
    }

    private void pedir(Player p, Estilo es, String modo, String que) {
        pidiendo.put(p.getUniqueId(), new Pedido(es.sel.nombre, modo));
        p.closeInventory();
        core.aviso(p, "<gray>Escribí en el chat " + que + ". Podés usar colores de MiniMessage. <dark_gray>(escribí <white>cancelar</white> para salir)");
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void alEscribir(AsyncChatEvent e) {
        Pedido pe = pidiendo.remove(e.getPlayer().getUniqueId());
        if (pe == null) return;
        e.setCancelled(true);
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(core, () -> {
            Hologram h = holo(pe.nombre);
            TextHologramData t = texto(h);
            if (t == null) { core.aviso(p, "<red>Ese holograma ya no existe."); return; }
            if (!msg.equalsIgnoreCase("cancelar") && !msg.isEmpty()) {
                try {
                    List<String> l = new ArrayList<>(t.getText());
                    if (pe.modo.equals("titulo")) {
                        String a = "#E03131", b = "#FF8787";
                        if (!l.isEmpty()) {
                            java.util.regex.Matcher m = java.util.regex.Pattern.compile("<gradient:([^:>]+):([^:>]+)>").matcher(l.get(0));
                            if (m.find()) { a = m.group(1); b = m.group(2); }
                        }
                        String nuevo = msg.startsWith("<") ? msg : deg(a, b, msg);
                        if (l.isEmpty()) l.add(nuevo); else l.set(0, nuevo);
                    } else {
                        l.add(msg.startsWith("<") ? msg : "<gray>" + msg);
                    }
                    t.setText(l);
                    guardarHolo(h);
                } catch (Throwable ex) {
                    core.getLogger().warning("No pude cambiar el texto de " + pe.nombre + ": " + ex);
                    core.aviso(p, "<red>No se pudo cambiar el texto. Mirá la consola.");
                }
            }
            abrirEstilo(p, new Sel(Tipo.HOLOGRAMA, pe.nombre));
        });
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
        borrando.remove(e.getPlayer().getUniqueId());
        pidiendo.remove(e.getPlayer().getUniqueId());
        fondoIdx.remove(e.getPlayer().getUniqueId());
    }
}
