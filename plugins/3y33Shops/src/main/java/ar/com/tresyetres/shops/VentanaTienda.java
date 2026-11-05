package ar.com.tresyetres.shops;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** La ventana "TIENDA" que se abre con clic derecho al cofre: comprar, vender, cantidad personalizada y almacenamiento. */
final class VentanaTienda implements Listener {
    static final int S_MENOS64 = 10, S_MENOS32 = 11, S_MENOS1 = 12, S_ITEM = 13, S_MAS1 = 14, S_MAS32 = 15, S_MAS64 = 16;
    static final int S_ALMACEN = 18, S_PERSONAL = 22;

    private static final class Titular implements InventoryHolder {
        final long tienda;
        Titular(long tienda) { this.tienda = tienda; }
        @Override public Inventory getInventory() { return null; }
    }

    private record Pendiente(long tienda, boolean venta) { }

    private final ShopsPlugin pl;
    private final Map<UUID, Pendiente> escribiendo = new HashMap<>();
    private final Map<UUID, Long> ultimoClic = new HashMap<>();

    VentanaTienda(ShopsPlugin pl) { this.pl = pl; }

    // ---------- abrir con clic derecho al cofre ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void alTocar(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        Tienda t = pl.almacen().en(b);
        if (t == null) return;
        Player p = e.getPlayer();
        // agachado con un bloque en la mano: dejá que ponga el bloque
        if (p.isSneaking() && p.getInventory().getItemInMainHand().getType().isBlock() && !p.getInventory().getItemInMainHand().getType().isAir()) return;
        e.setCancelled(true);
        e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        if (Operaciones.inventario(t) == null) {
            Msg.error(p, "El cofre de esta tienda ya no existe. La tienda se borró.");
            pl.almacen().borrar(t);
            return;
        }
        abrir(p, t);
    }

    // ---------- construir la ventana ----------

    void abrir(Player p, Tienda t) {
        Inventory inv = Bukkit.createInventory(new Titular(t.id), 27, Component.text("TIENDA", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        ItemStack relleno = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < 27; i++) inv.setItem(i, relleno);
        llenar(inv, p, t);
        p.openInventory(inv);
    }

    private void llenar(Inventory inv, Player p, Tienda t) {
        Operaciones ops = pl.operaciones();
        Inventory shop = Operaciones.inventario(t);
        int stock = shop == null ? 0 : Operaciones.contar(shop, t.item);

        inv.setItem(S_ITEM, itemTienda(t, stock));

        inv.setItem(S_MENOS64, botonVender(t, 64, stock));
        inv.setItem(S_MENOS32, botonVender(t, 32, stock));
        inv.setItem(S_MENOS1, botonVender(t, 1, stock));
        inv.setItem(S_MAS1, botonComprar(t, 1, stock));
        inv.setItem(S_MAS32, botonComprar(t, 32, stock));
        inv.setItem(S_MAS64, botonComprar(t, 64, stock));

        int puedeComprar = ops.maxComprar(p, t);
        int puedeVender = ops.maxVender(p, t);
        List<Component> lp = new ArrayList<>();
        lp.add(linea("Elegí exactamente cuántas unidades operar.", NamedTextColor.GRAY));
        lp.add(Component.empty());
        lp.add(linea("Podés vender: ", NamedTextColor.GRAY).append(Component.text(Msg.numero(puedeVender), NamedTextColor.GOLD)));
        lp.add(linea("Podés comprar: ", NamedTextColor.GRAY).append(Component.text(Msg.numero(puedeComprar), NamedTextColor.GREEN)));
        lp.add(Component.empty());
        lp.add(linea("Clic izquierdo: vender.", NamedTextColor.GOLD));
        lp.add(linea("Clic derecho: comprar.", NamedTextColor.GREEN));
        inv.setItem(S_PERSONAL, item(Material.NAME_TAG, Component.text("Cantidad personalizada", NamedTextColor.AQUA, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false), lp));

        if (t.administra(p)) {
            inv.setItem(S_ALMACEN, item(Material.CHEST, Component.text("Almacenamiento", NamedTextColor.AQUA, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false),
                    List.of(linea("Abrí el cofre de la tienda para reponer o retirar.", NamedTextColor.GRAY), Component.empty(), linea("Clic izquierdo: abrir.", NamedTextColor.YELLOW))));
        }
    }

    private static Component linea(String texto, NamedTextColor color) { return Component.text(texto, color).decoration(TextDecoration.ITALIC, false); }

    private static ItemStack item(Material m, Component nombre, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.displayName(nombre);
        if (!lore.isEmpty()) im.lore(lore);
        it.setItemMeta(im);
        return it;
    }

    private ItemStack itemTienda(Tienda t, int stock) {
        ItemStack it = t.item.clone();
        it.setAmount(1);
        ItemMeta im = it.getItemMeta();
        List<Component> lore = im.hasLore() && im.lore() != null ? new ArrayList<>(im.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        String nombreDueno = Bukkit.getOfflinePlayer(t.dueno).getName();
        lore.add(linea("Dueño: ", NamedTextColor.GRAY).append(Component.text(nombreDueno == null ? "?" : nombreDueno, NamedTextColor.WHITE)));
        if (!t.tierra.isBlank()) lore.add(linea("Tierra: ", NamedTextColor.GRAY).append(Component.text(t.tierra, NamedTextColor.WHITE)));
        lore.add(linea("Stock: ", NamedTextColor.GRAY).append(Component.text(Msg.numero(stock), stock == 0 ? NamedTextColor.RED : NamedTextColor.WHITE)));
        if (t.puedeComprar()) lore.add(linea("Comprar: ", NamedTextColor.GRAY).append(Component.text(Msg.dinero(t.compra) + " c/u", NamedTextColor.GREEN)));
        if (t.puedeVender()) lore.add(linea("Vender: ", NamedTextColor.GRAY).append(Component.text(Msg.dinero(t.venta) + " c/u", NamedTextColor.GOLD)));
        im.lore(lore);
        it.setItemMeta(im);
        return it;
    }

    private ItemStack botonComprar(Tienda t, int n, int stock) {
        if (!t.puedeComprar()) {
            return item(Material.GRAY_STAINED_GLASS_PANE, Component.text("No se puede comprar", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    List.of(linea("Esta tienda no vende este ítem.", NamedTextColor.DARK_GRAY)));
        }
        List<Component> l = new ArrayList<>();
        l.add(linea("Pagás: ", NamedTextColor.GRAY).append(Component.text(Msg.dinero(t.compra * n), NamedTextColor.WHITE)));
        l.add(Component.empty());
        if (stock < n) l.add(linea(stock == 0 ? "Sin stock." : "Stock insuficiente (hay " + Msg.numero(stock) + ").", NamedTextColor.RED));
        else l.add(linea("Clic izquierdo: comprar.", NamedTextColor.YELLOW));
        return item(Material.LIME_CONCRETE, Component.text("Comprar " + n + "x", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false), l);
    }

    private ItemStack botonVender(Tienda t, int n, int stock) {
        if (!t.puedeVender()) {
            return item(Material.GRAY_STAINED_GLASS_PANE, Component.text("No se puede vender", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    List.of(linea("Esta tienda no compra este ítem.", NamedTextColor.DARK_GRAY)));
        }
        return item(Material.RED_CONCRETE, Component.text("Vender " + n + "x", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false),
                List.of(linea("Recibís: ", NamedTextColor.GRAY).append(Component.text(Msg.dinero(t.venta * n), NamedTextColor.WHITE)), Component.empty(),
                        linea("Clic izquierdo: vender.", NamedTextColor.YELLOW)));
    }

    // ---------- clics ----------

    @EventHandler(priority = EventPriority.HIGH)
    public void alClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Titular h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != top) return;
        long ahora = System.currentTimeMillis();
        long pausa = pl.getConfig().getLong("pausa-clics-ms", 150);
        if (ahora - ultimoClic.getOrDefault(p.getUniqueId(), 0L) < pausa) return;
        ultimoClic.put(p.getUniqueId(), ahora);

        Tienda t = pl.almacen().porId(h.tienda);
        if (t == null || Operaciones.inventario(t) == null) { p.closeInventory(); Msg.error(p, "Esta tienda ya no existe."); return; }
        int slot = e.getSlot();
        switch (slot) {
            case S_MAS1 -> comprar(p, t, 1, top);
            case S_MAS32 -> comprar(p, t, 32, top);
            case S_MAS64 -> comprar(p, t, 64, top);
            case S_MENOS1 -> vender(p, t, 1, top);
            case S_MENOS32 -> vender(p, t, 32, top);
            case S_MENOS64 -> vender(p, t, 64, top);
            case S_ALMACEN -> {
                if (!t.administra(p)) return;
                Inventory inv = Operaciones.inventario(t);
                if (inv != null) p.openInventory(inv);
            }
            case S_PERSONAL -> {
                boolean venta = e.isLeftClick();
                escribiendo.put(p.getUniqueId(), new Pendiente(t.id, venta));
                p.closeInventory();
                Msg.info(p, "Escribí en el chat cuántas unidades querés " + (venta ? "vender" : "comprar") + " (o escribí: cancelar).");
            }
            default -> { }
        }
    }

    private void comprar(Player p, Tienda t, int n, Inventory ventana) {
        Operaciones.Resultado r = pl.operaciones().comprar(p, t, n);
        if (r.ok()) {
            Msg.ok(p, "Compraste " + Msg.numero(n) + "x " + nombreItem(t) + " por " + Msg.dinero(r.totalCentavos()) + ".");
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        } else {
            Msg.error(p, r.mensaje());
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
        }
        llenar(ventana, p, t);
    }

    private void vender(Player p, Tienda t, int n, Inventory ventana) {
        Operaciones.Resultado r = pl.operaciones().vender(p, t, n);
        if (r.ok()) {
            Msg.ok(p, "Vendiste " + Msg.numero(n) + "x " + nombreItem(t) + " y recibiste " + Msg.dinero(r.totalCentavos()) + ".");
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 0.9f);
        } else {
            Msg.error(p, r.mensaje());
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
        }
        llenar(ventana, p, t);
    }

    static String nombreItem(Tienda t) {
        return PlainTextComponentSerializer.plainText().serialize(t.item.effectiveName());
    }

    @EventHandler
    public void alArrastrar(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Titular) e.setCancelled(true);
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent e) {
        escribiendo.remove(e.getPlayer().getUniqueId());
        ultimoClic.remove(e.getPlayer().getUniqueId());
    }

    // ---------- cantidad personalizada por chat ----------

    @EventHandler(priority = EventPriority.LOWEST)
    public void alChat(AsyncChatEvent e) {
        Pendiente pend = escribiendo.remove(e.getPlayer().getUniqueId());
        if (pend == null) return;
        e.setCancelled(true);
        String texto = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(pl, () -> {
            if (texto.equalsIgnoreCase("cancelar")) { Msg.info(p, "Cancelado."); return; }
            int n;
            try { n = Integer.parseInt(texto.replace(".", "")); } catch (NumberFormatException ex) { Msg.error(p, "Eso no es un número. Volvé a abrir la tienda y probá de nuevo."); return; }
            int tope = pl.getConfig().getInt("cantidad-maxima-personalizada", 2304);
            if (n < 1 || n > tope) { Msg.error(p, "La cantidad tiene que estar entre 1 y " + Msg.numero(tope) + "."); return; }
            Tienda t = pl.almacen().porId(pend.tienda());
            if (t == null || Operaciones.inventario(t) == null) { Msg.error(p, "Esa tienda ya no existe."); return; }
            Operaciones.Resultado r = pend.venta() ? pl.operaciones().vender(p, t, n) : pl.operaciones().comprar(p, t, n);
            if (r.ok()) {
                Msg.ok(p, (pend.venta() ? "Vendiste " : "Compraste ") + Msg.numero(n) + "x " + nombreItem(t) + (pend.venta() ? " y recibiste " : " por ") + Msg.dinero(r.totalCentavos()) + ".");
            } else {
                Msg.error(p, r.mensaje());
            }
            abrir(p, t);
        });
    }
}
