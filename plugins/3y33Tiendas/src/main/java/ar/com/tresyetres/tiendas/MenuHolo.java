package ar.com.tresyetres.tiendas;

import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.shop.Shop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

/** Ventana para mover el holograma de una tienda con clics (funciona igual en Java y Bedrock). */
final class MenuHolo implements Listener {
    private static MenuHolo instancia;

    /** Dueño del inventario: guarda a que tienda pertenece la ventana. */
    private static final class Titular implements InventoryHolder {
        final long tienda;
        Titular(long tienda) { this.tienda = tienda; }
        @Override public Inventory getInventory() { return null; }
    }

    // slot -> accion
    private static final Map<Integer, String> ACCIONES = Map.of(
            4, "adelante", 12, "izquierda", 14, "derecha", 22, "atras",
            7, "subir", 25, "bajar", 16, "reiniciar");

    private final TiendasPlugin pl;
    private final HoloComando cmd;

    MenuHolo(TiendasPlugin pl, HoloComando cmd) {
        this.pl = pl;
        this.cmd = cmd;
        instancia = this;
    }

    static void abrir(Player p, Shop<?, ?> shop) {
        if (instancia == null) return;
        Inventory inv = Bukkit.createInventory(new Titular(shop.getShopId()), 27,
                Component.text("Mover holograma", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        ItemStack relleno = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < 27; i++) inv.setItem(i, relleno);
        inv.setItem(4, flecha(Material.ARROW, "▲ Adelante", "Lo aleja de vos"));
        inv.setItem(12, flecha(Material.ARROW, "◀ Izquierda", "Hacia tu izquierda"));
        inv.setItem(14, flecha(Material.ARROW, "▶ Derecha", "Hacia tu derecha"));
        inv.setItem(22, flecha(Material.ARROW, "▼ Atrás", "Lo acerca a vos"));
        inv.setItem(7, flecha(Material.FEATHER, "Subir", "Lo sube un poco"));
        inv.setItem(25, flecha(Material.ANVIL, "Bajar", "Lo baja un poco"));
        inv.setItem(16, flecha(Material.BARRIER, "Reiniciar", "Vuelve a la posición original"));
        ItemStack muestra = shop.getItem() == null ? new ItemStack(Material.CHEST) : shop.getItem().clone();
        muestra.setAmount(1);
        ItemMeta m = muestra.getItemMeta();
        if (m != null) {
            m.lore(List.of(Component.text("Tienda en " + shop.bukkitLocation().getBlockX() + ", " + shop.bukkitLocation().getBlockY() + ", " + shop.bukkitLocation().getBlockZ(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Solo mueve el holograma de ESTA tienda.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            muestra.setItemMeta(m);
        }
        inv.setItem(13, muestra);
        p.openInventory(inv);
    }

    private static ItemStack flecha(Material mat, String nombre, String detalle) {
        return item(mat, Component.text(nombre, NamedTextColor.RED, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false),
                List.of(Component.text(detalle, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("Clic: 1 paso · Shift+clic: 4 pasos", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
    }

    private static ItemStack item(Material mat, Component nombre, List<Component> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(nombre);
        if (!lore.isEmpty()) m.lore(lore);
        it.setItemMeta(m);
        return it;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @EventHandler
    public void alClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Titular t)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        String accion = ACCIONES.get(e.getSlot());
        if (accion == null) return;
        Shop s;
        try { s = QuickShopAPI.getInstance().getShopManager().getShop(t.tienda); } catch (Throwable x) { s = null; }
        if (s == null) { p.closeInventory(); pl.msg(p, "Esa tienda ya no existe."); return; }
        if (!cmd.puedeEditar(p, s)) { p.closeInventory(); pl.msg(p, "Solo el dueño de la tienda puede mover su holograma."); return; }
        cmd.aplicar(p, s, accion, e.isShiftClick() ? 4 : 1);
    }

    @EventHandler
    public void alArrastrar(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Titular) e.setCancelled(true);
    }
}
