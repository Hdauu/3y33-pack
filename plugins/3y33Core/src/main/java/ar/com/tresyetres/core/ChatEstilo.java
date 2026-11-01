package ar.com.tresyetres.core;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Color de los mensajes del chat: VIP colores solidos, VIP+ y staff degradados, y el estilo Argentina para quienes jugaron la beta. */
final class ChatEstilo implements Listener {
    private static final class Menu implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return Bukkit.createInventory(this, 9); }
    }

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28};
    private final Core core;
    private final Color color;
    private final File archivo;
    private final Map<UUID, String> elegido = new HashMap<>();

    ChatEstilo(Core core, Color color) {
        this.core = core;
        this.color = color;
        archivo = new File(core.getDataFolder(), "chat.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(archivo);
        for (String k : y.getKeys(false)) {
            try { elegido.put(UUID.fromString(k), y.getString(k)); } catch (IllegalArgumentException ignored) { }
        }
    }

    private void guardar() {
        YamlConfiguration y = new YamlConfiguration();
        elegido.forEach((u, v) -> y.set(u.toString(), v));
        try { y.save(archivo); } catch (IOException e) { core.getLogger().warning("No pude guardar chat.yml: " + e.getMessage()); }
    }

    /** Los solidos son los primeros de la lista de colores del nombre (hasta "rosa"). */
    private static boolean solido(Color.Preset x) { return x.perm() == null && x.c() == null && !x.b().equals(x.a()) && esDeLaFila1(x); }

    private static boolean esDeLaFila1(Color.Preset x) {
        int i = Color.PRESETS.indexOf(x);
        return i >= 0 && i < 10;
    }

    boolean disponible(Player p, Color.Preset x) {
        if (x.perm() != null) return p.hasPermission(x.perm());
        int n = color.nivelChat(p);
        return esDeLaFila1(x) ? n >= 1 : n >= 2;
    }

    private Color.Preset actual(Player p) {
        String id = elegido.get(p.getUniqueId());
        if (id == null) return null;
        for (Color.Preset x : Color.PRESETS) if (x.id().equals(id) && disponible(p, x)) return x;
        return null;
    }

    // ------------------------------------------------------------- aplicar al mensaje

    private static TextColor mezcla(List<TextColor> cs, double t) {
        if (cs.size() == 1) return cs.get(0);
        double pos = t * (cs.size() - 1);
        int i = Math.min((int) pos, cs.size() - 2);
        double f = pos - i;
        TextColor a = cs.get(i), b = cs.get(i + 1);
        return TextColor.color((int) (a.red() + (b.red() - a.red()) * f), (int) (a.green() + (b.green() - a.green()) * f),
                (int) (a.blue() + (b.blue() - a.blue()) * f));
    }

    private static List<TextColor> colores(Color.Preset x) {
        List<TextColor> l = new ArrayList<>();
        l.add(TextColor.fromHexString(x.a()));
        if (x.b() != null && !x.id().equals("argentina")) l.add(TextColor.fromHexString(x.b()));
        if (x.id().equals("argentina")) { l.add(TextColor.fromHexString(x.b())); l.add(TextColor.fromHexString(x.c())); }
        return l;
    }

    static Component pintar(String texto, Color.Preset x) {
        List<TextColor> cs = colores(x);
        boolean plano = esDeLaFila1(x);
        if (plano) return Component.text(texto, TextColor.fromHexString(x.a()));
        int[] cps = texto.codePoints().toArray();
        Component r = Component.empty();
        int n = cps.length;
        for (int i = 0; i < n; i++) {
            String ch = new String(Character.toChars(cps[i]));
            r = r.append(Component.text(ch, mezcla(cs, n == 1 ? 0 : (double) i / (n - 1))));
        }
        return r;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void alEscribir(AsyncChatEvent e) {
        Color.Preset x = actual(e.getPlayer());
        if (x == null) return;
        String plano = PlainTextComponentSerializer.plainText().serialize(e.message());
        if (plano.isBlank()) return;
        e.message(pintar(plano, x));
    }

    // ------------------------------------------------------------- menu

    private Component sin(Component c) { return c.decoration(TextDecoration.ITALIC, false); }

    void abrir(Player p) {
        if (color.nivelChat(p) == 0 && actualCosmetico(p) == 0) {
            p.sendMessage(core.mm("<dark_gray>3y33 »</dark_gray> <white>El color del chat es para <#00C8FF>VIP</#00C8FF> y <#F2A900>VIP+</#F2A900>."));
            return;
        }
        Inventory inv = Bukkit.createInventory(new Menu(), 36, core.mm("<red>3y33</red> <dark_gray>»</dark_gray> <white>Color del chat"));
        ItemStack fondo = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = fondo.getItemMeta();
        fm.displayName(Component.text(" "));
        fondo.setItemMeta(fm);
        for (int i = 0; i < 36; i++) inv.setItem(i, fondo);
        String sel = elegido.get(p.getUniqueId());
        int pos = 0;
        for (Color.Preset x : Color.PRESETS) {
            if (!disponible(p, x) || pos >= SLOTS.length) continue;
            ItemStack it = new ItemStack(x.mat());
            ItemMeta m = it.getItemMeta();
            m.displayName(sin(core.mm("<gradient:" + x.grad() + "><bold>" + x.nombre() + "</bold></gradient>")));
            boolean on = x.id().equals(sel);
            m.lore(List.of(sin(pintar("Así se ven tus mensajes", x)), Component.empty(),
                    sin(core.mm(on ? "<green>✔ Elegido" : "<gray>Click para elegir"))));
            if (on) { m.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true); m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS); }
            it.setItemMeta(m);
            inv.setItem(SLOTS[pos++], it);
        }
        ItemStack r = new ItemStack(Material.BARRIER);
        ItemMeta rm = r.getItemMeta();
        rm.displayName(sin(core.mm("<white>Mensajes sin color")));
        r.setItemMeta(rm);
        inv.setItem(31, r);
        ItemStack v = new ItemStack(Material.ARROW);
        ItemMeta vm = v.getItemMeta();
        vm.displayName(sin(core.mm("<white>Volver")));
        v.setItemMeta(vm);
        inv.setItem(30, v);
        p.openInventory(inv);
    }

    private int actualCosmetico(Player p) {
        for (Color.Preset x : Color.PRESETS) if (x.perm() != null && p.hasPermission(x.perm())) return 1;
        return 0;
    }

    @EventHandler
    public void alClickear(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int s = e.getSlot();
        if (s == 30) { color.abrir(p); return; }
        if (s == 31) {
            elegido.remove(p.getUniqueId());
            guardar();
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f);
            p.sendMessage(core.mm("<dark_gray>3y33 »</dark_gray> <white>Tus mensajes vuelven a verse normales."));
            abrir(p);
            return;
        }
        int pos = 0;
        for (Color.Preset x : Color.PRESETS) {
            if (!disponible(p, x) || pos >= SLOTS.length) continue;
            if (SLOTS[pos++] != s) continue;
            elegido.put(p.getUniqueId(), x.id());
            guardar();
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
            p.sendMessage(core.mm("<dark_gray>3y33 »</dark_gray> <white>Tus mensajes ahora se ven así: ").append(pintar("hola " + p.getName(), x)));
            abrir(p);
            return;
        }
    }

    @EventHandler
    public void alArrastrar(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }
}
