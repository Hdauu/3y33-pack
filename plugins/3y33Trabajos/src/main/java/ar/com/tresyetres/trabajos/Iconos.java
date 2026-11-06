package ar.com.tresyetres.trabajos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Items de los menus. Con el resource pack, los trabajos usan su icono con marco (segun el prestigio); sin el, el item comun. */
final class Iconos implements Listener {
    /** Marco segun el prestigio: 0 hierro, 1 bronce, 2 plata, 3 oro. */
    static final String[] MARCOS = {"hierro", "bronce", "plata", "oro"};

    private final TrabajosPlugin pl;
    private final Set<UUID> sinPack = new HashSet<>();

    Iconos(TrabajosPlugin pl) { this.pl = pl; }

    @EventHandler
    public void alEstadoDelPack(PlayerResourcePackStatusEvent e) {
        switch (e.getStatus()) {
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> sinPack.add(e.getPlayer().getUniqueId());
            case SUCCESSFULLY_LOADED -> sinPack.remove(e.getPlayer().getUniqueId());
            default -> { }
        }
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent e) { sinPack.remove(e.getPlayer().getUniqueId()); }

    static boolean esBedrock(Player p) { return p.getUniqueId().getMostSignificantBits() == 0L; }

    /** Si a este jugador hay que mostrarle los iconos del pack. */
    boolean conPack(Player p) {
        String modo = pl.getConfig().getString("iconos-pack", "todos").toLowerCase(Locale.ROOT);
        if (modo.equals("no") || esBedrock(p)) return false;
        if (modo.equals("staff")) return p.hasPermission("tresyetres.staff");
        return !sinPack.contains(p.getUniqueId());
    }

    static Component txt(String s, TextColor c) { return Component.text(s, c).decoration(TextDecoration.ITALIC, false); }

    static Component txtNegrita(String s, TextColor c) { return Component.text(s, c).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true); }

    /** Un item con nombre y lore, sin atributos a la vista. */
    static ItemStack item(org.bukkit.Material m, Component nombre, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(nombre.decoration(TextDecoration.ITALIC, false));
        if (lore != null && !lore.isEmpty()) meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }

    /** La carta de un trabajo: icono con marco del pack (si lo tiene) o el item comun. */
    ItemStack carta(Player p, Trabajo t, int prestigio, boolean activo, Component nombre, List<Component> lore) {
        ItemStack it = item(t.icono, nombre, lore);
        if (conPack(p)) {
            ItemMeta meta = it.getItemMeta();
            String marco = MARCOS[Math.max(0, Math.min(3, prestigio))];
            meta.setItemModel(new NamespacedKey("3y33", "trabajos/" + t.id + "_" + marco + "_" + (activo ? "activo" : "normal")));
            it.setItemMeta(meta);
        }
        return it;
    }
}
