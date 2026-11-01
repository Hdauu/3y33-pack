package ar.com.tresyetres.core;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Color del nombre (solo VIP, VIP+ y staff). El tag de rango es siempre el mismo; esto cambia solo el degradado del nombre. */
final class Color implements Listener {
    record Preset(String id, String nombre, String a, String b, String c, Material mat, String perm) {
        Preset(String id, String nombre, String a, String b, Material mat) { this(id, nombre, a, b, null, mat, null); }
        /** colores del degradado separados por ":" (2 o 3). */
        String grad() { return c == null ? a + ":" + b : a + ":" + b + ":" + c; }
    }

    static final List<Preset> PRESETS = List.of(
            new Preset("blanco", "Blanco", "#FFFFFF", "#D8D8D8", Material.WHITE_DYE),
            new Preset("rojo", "Rojo", "#FF3B3B", "#FF9A9A", Material.RED_DYE),
            new Preset("naranja", "Naranja", "#FF8C00", "#FFD27F", Material.ORANGE_DYE),
            new Preset("dorado", "Dorado", "#F2A900", "#FFE07A", Material.YELLOW_DYE),
            new Preset("lima", "Lima", "#7CD43A", "#D9FF8C", Material.LIME_DYE),
            new Preset("verde", "Verde", "#1FA855", "#7CE8A5", Material.GREEN_DYE),
            new Preset("celeste", "Celeste", "#00C8FF", "#B5FFFF", Material.LIGHT_BLUE_DYE),
            new Preset("azul", "Azul", "#1670D9", "#6FB0FF", Material.BLUE_DYE),
            new Preset("violeta", "Violeta", "#7B3FE4", "#C09CFF", Material.PURPLE_DYE),
            new Preset("rosa", "Rosa", "#FF4FA3", "#FFB3D9", Material.PINK_DYE),
            new Preset("atardecer", "Atardecer", "#FF512F", "#F09819", Material.ORANGE_DYE),
            new Preset("oceano", "Océano", "#00C6FF", "#0072FF", Material.CYAN_DYE),
            new Preset("aurora", "Aurora", "#00F5A0", "#7B3FE4", Material.GLOW_INK_SAC),
            new Preset("fuego", "Fuego", "#F83600", "#FE8C00", Material.BLAZE_POWDER),
            // cosmeticos: se ganan jugando (no dependen del rango)
            new Preset("argentina", "Argentina", "#74ACDF", "#FFFFFF", "#74ACDF", Material.LIGHT_BLUE_BANNER, "tresyetres.estilo.argentina"));

    /** Color de rango por defecto (de mayor a menor), sale de un grupo de LuckPerms. */
    private static final String[][] RANGOS = {
            {"owner", "#A3123A", "#F0507A"}, {"admin", "#E02B2B", "#FF8A8A"}, {"dev", "#7B3FE4", "#C09CFF"},
            {"moderador", "#1670D9", "#6FB0FF"}, {"vipplus", "#F2A900", "#FFE07A"}, {"vip", "#00C8FF", "#B5FFFF"}};

    private final Core core;
    private final File archivo;
    private final Map<UUID, String> elegido = new HashMap<>();

    Color(Core core) {
        this.core = core;
        archivo = new File(core.getDataFolder(), "colores.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(archivo);
        for (String k : y.getKeys(false)) {
            try { elegido.put(UUID.fromString(k), y.getString(k)); } catch (IllegalArgumentException ignored) { }
        }
    }

    private void guardar() {
        YamlConfiguration y = new YamlConfiguration();
        elegido.forEach((u, v) -> y.set(u.toString(), v));
        try { y.save(archivo); } catch (IOException e) { core.getLogger().warning("No pude guardar colores.yml: " + e.getMessage()); }
    }

    private String[] rango(Player p) {
        // grupo principal de LuckPerms (los OP tienen todos los group.* en true, por eso no sirve mirar permisos)
        String g = "";
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null)
            g = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, "%vault_rank%").toLowerCase(Locale.ROOT);
        for (String[] r : RANGOS) if (r[0].equals(g)) return r;
        return null;
    }

    /** Tag de rango para el scoreboard: el glifo del pack, o "Usuario" si no tiene tag. */
    String etiqueta(Player p) {
        String g = "";
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null)
            g = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, "%vault_rank%").toLowerCase(Locale.ROOT);
        return switch (g) {
            case "vip" -> "\uE300";
            case "vipplus" -> "\uE301";
            case "moderador" -> "\uE302";
            case "dev" -> "\uE303";
            case "admin" -> "\uE304";
            case "owner" -> "\uE305";
            default -> "Usuario";
        };
    }

    /** id del color que se ve: el elegido, el del rango o blanco. Los ids "r_<rango>" son los colores de rango. */
    String efectivo(Player p) {
        Preset pr = valido(p);
        if (pr != null) return pr.id();
        String[] r = rango(p);
        return r != null ? "r_" + r[0] : "blanco";
    }

    /** El preset elegido, solo si el jugador todavia puede usarlo. */
    private Preset valido(Player p) {
        Preset pr = buscar(elegido.get(p.getUniqueId()));
        return pr != null && disponible(p, pr) ? pr : null;
    }

    private boolean libre(Player p) { return rango(p) != null || p.hasPermission("tresyetres.color"); }

    /** Los colores normales son de VIP/staff; los cosmeticos, de quien tenga su permiso. */
    private boolean disponible(Player p, Preset x) {
        return x.perm() == null ? libre(p) : p.hasPermission(x.perm());
    }

    /** 0 = sin color de chat, 1 = VIP (solidos), 2 = VIP+ y staff (degradados). */
    int nivelChat(Player p) {
        String[] r = rango(p);
        if (r != null) return r[0].equals("vip") ? 1 : 2;
        return p.hasPermission("tresyetres.color") ? 2 : 0;
    }

    ChatEstilo chat;

    boolean puede(Player p) {
        if (libre(p)) return true;
        for (Preset x : PRESETS) if (x.perm() != null && p.hasPermission(x.perm())) return true;
        return false;
    }

    /** Degradado para MiniMessage/TAB: "A:B" o "A:B:C". */
    String grad(Player p) {
        Preset pr = valido(p);
        if (pr != null) return pr.grad();
        String[] c = colores(p);
        return c[0] + ":" + c[1];
    }

    private Preset buscar(String id) {
        for (Preset x : PRESETS) if (x.id().equals(id)) return x;
        return null;
    }

    /** {inicio, fin} en #RRGGBB para los placeholders. */
    String[] colores(Player p) {
        String[] r = rango(p);
        Preset pr = valido(p);
        if (pr != null) return new String[]{pr.a(), pr.b()};
        if (r == null && !p.hasPermission("tresyetres.color")) return new String[]{"#FFFFFF", "#FFFFFF"};
        if (r != null) return new String[]{r[1], r[2]};
        return new String[]{"#FFFFFF", "#D8D8D8"};
    }

    // ------------------------------------------------------------- menu

    private static final class Menu implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return Bukkit.createInventory(this, 9); }
    }

    private Component sin(Component c) { return c.decoration(TextDecoration.ITALIC, false); }

    void abrir(Player p) {
        if (!puede(p)) {
            p.sendMessage(core.mm("<dark_gray>3y33 »</dark_gray> <white>El color del nombre es para <#00C8FF>VIP</#00C8FF> y <#F2A900>VIP+</#F2A900>, y los estilos de la beta para quienes jugaron la beta."));
            return;
        }
        Inventory inv = Bukkit.createInventory(new Menu(), 36, core.mm("<red>3y33</red> <dark_gray>»</dark_gray> <white>Color del nombre"));
        ItemStack fondo = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = fondo.getItemMeta();
        fm.displayName(Component.text(" "));
        fondo.setItemMeta(fm);
        for (int i = 0; i < 36; i++) inv.setItem(i, fondo);
        String actual = elegido.get(p.getUniqueId());
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28};
        for (int i = 0; i < PRESETS.size(); i++) {
            Preset x = PRESETS.get(i);
            if (!disponible(p, x)) continue;
            ItemStack it = new ItemStack(x.mat());
            ItemMeta m = it.getItemMeta();
            m.displayName(sin(core.mm("<gradient:" + x.grad() + "><bold>" + x.nombre() + "</bold></gradient>")));
            boolean sel = x.id().equals(actual);
            m.lore(List.of(
                    sin(core.mm("<gradient:" + x.grad() + ">" + p.getName() + "</gradient>")),
                    Component.empty(),
                    sin(core.mm(sel ? "<green>✔ Elegido" : "<gray>Click para elegir"))));
            if (sel) { m.addEnchant(Enchantment.UNBREAKING, 1, true); m.addItemFlags(ItemFlag.HIDE_ENCHANTS); }
            it.setItemMeta(m);
            inv.setItem(slots[i], it);
        }
        ItemStack r = new ItemStack(Material.BARRIER);
        ItemMeta rm = r.getItemMeta();
        rm.displayName(sin(core.mm("<white>Volver al color de mi rango")));
        rm.lore(List.of(sin(core.mm("<gray>Saca el color que elegiste"))));
        r.setItemMeta(rm);
        inv.setItem(31, r);
        ItemStack ch = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta cm = ch.getItemMeta();
        cm.displayName(sin(core.mm("<white><bold>Color del chat")));
        cm.lore(List.of(sin(core.mm("<gray>Cambiá cómo se ven tus mensajes"))));
        ch.setItemMeta(cm);
        inv.setItem(34, ch);
        p.openInventory(inv);
    }

    @EventHandler
    public void alClickear(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (!puede(p)) { p.closeInventory(); return; }
        int s = e.getSlot();
        if (s == 34 && chat != null) { chat.abrir(p); return; }
        if (s == 31) {
            elegido.remove(p.getUniqueId());
            guardar();
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f);
            p.sendMessage(core.mm("<dark_gray>3y33 »</dark_gray> <white>Volviste al color de tu rango."));
            abrir(p);
            return;
        }
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28};
        for (int i = 0; i < slots.length && i < PRESETS.size(); i++) {
            if (slots[i] != s) continue;
            Preset x = PRESETS.get(i);
            if (!disponible(p, x)) return;
            elegido.put(p.getUniqueId(), x.id());
            guardar();
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
            p.sendMessage(core.mm("<dark_gray>3y33 »</dark_gray> <white>Tu nombre ahora es <gradient:" + x.a() + ":" + x.b() + ">" + p.getName() + "</gradient><white>."));
            abrir(p);
            return;
        }
    }

    @EventHandler
    public void alArrastrar(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }
}
