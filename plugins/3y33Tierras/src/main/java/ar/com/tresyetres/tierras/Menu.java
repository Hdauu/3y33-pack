package ar.com.tresyetres.tierras;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** Inventario genérico con acciones por slot. */
final class Menu implements InventoryHolder {

    static final MiniMessage MM = MiniMessage.miniMessage();

    private final Inventory inv;
    final Map<Integer, BiConsumer<Player, ClickType>> acciones = new HashMap<>();

    Menu(int filas, String titulo) {
        this.inv = Bukkit.createInventory(this, filas * 9, MM.deserialize(titulo));
    }

    static String esc(String s) {
        return MM.escapeTags(s == null ? "" : s);
    }

    void fondo() {
        ItemStack n = vidrio(Material.BLACK_STAINED_GLASS_PANE), g = vidrio(Material.GRAY_STAINED_GLASS_PANE);
        int filas = inv.getSize() / 9;
        for (int i = 0; i < inv.getSize(); i++) {
            int fila = i / 9, col = i % 9;
            boolean borde = fila == 0 || fila == filas - 1 || col == 0 || col == 8;
            inv.setItem(i, borde ? n : g);
        }
    }

    static ItemStack vidrio(Material m) {
        return item(m, " ");
    }

    void poner(int slot, ItemStack it, BiConsumer<Player, ClickType> accion) {
        inv.setItem(slot, it);
        if (accion != null) acciones.put(slot, accion);
        else acciones.remove(slot);
    }

    /** Ítem con nombre y lore en MiniMessage. Cada línea de lore ya incluye sus colores. */
    static ItemStack item(Material mat, String nombre, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(nombre.isBlank() ? Component.text(" ") : MM.deserialize("<!italic>" + nombre));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(s.isEmpty() ? Component.text(" ") : MM.deserialize("<!italic>" + s));
        m.lore(l);
        m.addItemFlags(ItemFlag.values());
        it.setItemMeta(m);
        return it;
    }

    static ItemStack cabeza(org.bukkit.OfflinePlayer jugador, String nombre, String... lore) {
        ItemStack it = item(Material.PLAYER_HEAD, nombre, lore);
        SkullMeta sm = (SkullMeta) it.getItemMeta();
        sm.setOwningPlayer(jugador);
        it.setItemMeta(sm);
        return it;
    }

    static ItemStack brillo(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        m.setEnchantmentGlintOverride(true);
        it.setItemMeta(m);
        return it;
    }

    static String g(String a, String b, String txt) {
        return "<gradient:" + a + ":" + b + "><bold>" + txt + "</bold></gradient>";
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
