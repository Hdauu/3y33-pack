package ar.com.tresyetres.trabajos;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

    /** Fondos de ventana: cada uno es un caracter de la fuente 3y33:tienda del pack. */
    static final char FONDO_TRABAJOS = (char) 0xE304, FONDO_TRABAJO = (char) 0xE305, FONDO_ACCIONES = (char) 0xE306, FONDO_CONFIRMAR = (char) 0xE307, FONDO_MISIONES = (char) 0xE308, FONDO_ELEGIR = (char) 0xE309;
    private static final char CORRER_8 = (char) 0xF808;

    /** Titulo con el fondo ilustrado (corrido 8 a la izquierda para quedar pegado al borde); sin el pack, el titulo comun. */
    Component titulo(Player p, char fondo, String textoComun) {
        if (!conPack(p)) return Component.text(textoComun, NamedTextColor.DARK_RED, TextDecoration.BOLD);
        return Component.text("" + CORRER_8 + fondo).font(Key.key("3y33", "tienda")).color(NamedTextColor.WHITE);
    }

    /** Como se ve la carta: normal, activa (la del trabajo que tenes) o bloqueada (gris, no se puede tomar ahora). */
    enum Estado { NORMAL, ACTIVO, BLOQUEADO }

    /**
     * La carta de un trabajo. Con el pack es un item grande (48x72: 3 casilleros de ancho por 4 de alto) que cuelga del casillero
     * donde se pone; "detalle" es la misma carta centrada en la ventana del trabajo. Sin el pack, el item comun.
     */
    ItemStack carta(Player p, Trabajo t, int prestigio, Estado estado, boolean detalle, Component nombre, List<Component> lore) {
        ItemStack it = item(t.icono, nombre, lore);
        if (conPack(p)) {
            ItemMeta meta = it.getItemMeta();
            String marco = MARCOS[Math.max(0, Math.min(3, prestigio))];
            meta.setItemModel(new NamespacedKey("3y33", "trabajos/" + (detalle ? "detalle_" : "") + "carta_" + t.id + "_" + marco + "_" + estado.name().toLowerCase(Locale.ROOT)));
            it.setItemMeta(meta);
        }
        return it;
    }

    /** Flecha doble « » para pasar de pagina: la del pack (roja, como la de Masivo) o la flecha comun. */
    ItemStack flecha(Player p, boolean derecha, Component nombre, List<Component> lore) {
        ItemStack it = item(org.bukkit.Material.ARROW, nombre, lore);
        if (conPack(p)) {
            ItemMeta meta = it.getItemMeta();
            meta.setItemModel(new NamespacedKey("3y33", "trabajos/" + (derecha ? "flecha_der" : "flecha_izq")));
            it.setItemMeta(meta);
        }
        return it;
    }

    /** Un casillero invisible con el mismo nombre y lore que la carta, para que el cursor la muestre en toda su superficie. */
    ItemStack relleno(ItemStack carta) {
        ItemStack it = carta.clone();
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("3y33", "trabajos/vacio"));
        it.setItemMeta(meta);
        return it;
    }
}
