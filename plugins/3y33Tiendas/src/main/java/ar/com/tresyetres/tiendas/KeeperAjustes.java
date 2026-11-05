package ar.com.tresyetres.tiendas;

import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.shop.Shop;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Suma los controles del holograma DENTRO del panel del dueño de QuickShop (la ventana de ajustes de la tienda):
 * rotacion del item, mensaje y mover. Cada boton actua sobre la tienda de ESA ventana.
 */
final class KeeperAjustes implements Listener {
    static final int SLOT_ROTACION = 11, SLOT_MENSAJE = 13, SLOT_MOVER = 15;

    private final TiendasPlugin pl;
    private final Hologramas holos;
    private final HoloComando cmd;
    /** Ultima tienda que toco cada jugador (para saber a cual pertenece la ventana que se abre). */
    private final Map<UUID, Long> ultima = new HashMap<>();
    private final Map<UUID, Long> ultimaTiempo = new HashMap<>();
    /** Ventanas del panel que ya tienen nuestros botones: ventana -> id de tienda. */
    private final Map<Inventory, Long> ventanas = new java.util.WeakHashMap<>();
    /** Jugadores a los que les estamos pidiendo el mensaje por chat. */
    private final Map<UUID, Long> escribiendo = new HashMap<>();

    KeeperAjustes(TiendasPlugin pl, Hologramas holos, HoloComando cmd) { this.pl = pl; this.holos = holos; this.cmd = cmd; }

    // ---------- que tienda tocó el jugador ----------

    @SuppressWarnings({"unchecked", "rawtypes"})
    @EventHandler(priority = EventPriority.LOWEST)
    public void alTocar(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;
        Location c = e.getClickedBlock().getLocation().add(0.5, 0.5, 0.5);
        Shop mejor = null;
        double d2 = 2.6;
        for (Shop s : (Set<Shop>) (Set) QuickShopAPI.getInstance().getShopManager().getLoadedShops()) {
            Location l = s.bukkitLocation();
            if (l == null || !l.getWorld().equals(c.getWorld())) continue;
            double d = l.clone().add(0.5, 0.5, 0.5).distanceSquared(c);
            if (d < d2) { d2 = d; mejor = s; }
        }
        if (mejor == null) return;
        ultima.put(e.getPlayer().getUniqueId(), mejor.getShopId());
        ultimaTiempo.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent e) {
        ultima.remove(e.getPlayer().getUniqueId());
        ultimaTiempo.remove(e.getPlayer().getUniqueId());
        escribiendo.remove(e.getPlayer().getUniqueId());
    }

    // ---------- ventana del panel del dueño ----------

    /** El panel del dueño de QuickShop: 4 filas con el cofre en el slot 22, la TNT en el 25 y la puerta en el 31 (ver gui.yml). */
    private static boolean esPanelDelDueno(Inventory inv) {
        if (inv.getSize() != 36) return false;
        int coincide = 0;
        ItemStack a = inv.getItem(22), b = inv.getItem(25), c = inv.getItem(31);
        if (a != null && a.getType() == Material.CHEST) coincide++;
        if (b != null && b.getType() == Material.TNT) coincide++;
        if (c != null && c.getType() == Material.OAK_DOOR) coincide++;
        return coincide >= 2;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alAbrir(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Long t = ultimaTiempo.get(p.getUniqueId());
        if (t == null || System.currentTimeMillis() - t > 4000) return;
        // QuickShop puede llenar la ventana unos ticks despues de abrirla: probamos varias veces
        for (int espera : new int[]{1, 2, 4, 8, 16}) {
            Bukkit.getScheduler().runTaskLater(pl, () -> intentar(p, espera == 16), espera);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void intentar(Player p, boolean ultimoIntento) {
        if (!p.isOnline()) return;
        Inventory inv = p.getOpenInventory().getTopInventory();
        if (ventanas.containsKey(inv)) return;
        if (!esPanelDelDueno(inv)) {
            if (ultimoIntento && inv.getSize() != 0 && inv.getType() == org.bukkit.event.inventory.InventoryType.CHEST) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack it = inv.getItem(i);
                    if (it != null && it.getType() != Material.BLACK_STAINED_GLASS_PANE) sb.append(i).append('=').append(it.getType()).append(' ');
                }
                pl.getLogger().info("Ventana no reconocida como panel del dueño (" + p.getName() + "): size=" + inv.getSize()
                        + " titulo=" + PlainTextComponentSerializer.plainText().serialize(p.getOpenInventory().title()) + " items: " + sb);
            }
            return;
        }
        Long id = ultima.get(p.getUniqueId());
        if (id == null) return;
        Shop s;
        try { s = QuickShopAPI.getInstance().getShopManager().getShop(id); } catch (Throwable x) { return; }
        if (s == null) { pl.getLogger().info("Panel del dueño: no encontré la tienda " + id); return; }
        if (!cmd.puedeEditar(p, s)) return;
        ventanas.put(inv, id);
        poner(inv, s);
        pl.getLogger().info("Botones de holograma puestos en el panel de " + p.getName() + " (tienda " + id + ")");
    }

    private void poner(Inventory inv, Shop<?, ?> s) {
        Hologramas.Rotacion actual = holos.rotacion(s.bukkitLocation());
        List<Component> lr = new ArrayList<>();
        lr.add(Component.text("Hacia dónde mira el ítem del holograma:", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        Component linea = Component.empty().decoration(TextDecoration.ITALIC, false);
        for (Hologramas.Rotacion r : Hologramas.Rotacion.values()) {
            Component x = Component.text(r.etiqueta, r == actual ? NamedTextColor.GOLD : NamedTextColor.GREEN);
            if (r == actual) x = x.decorate(TextDecoration.UNDERLINED, TextDecoration.BOLD);
            linea = linea.append(x).append(Component.text(" "));
        }
        lr.add(linea);
        lr.add(Component.empty());
        lr.add(Component.text("Clic izquierdo / derecho: cambiar la rotación", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        inv.setItem(SLOT_ROTACION, boton(Material.COMPASS, "Rotación del holograma", NamedTextColor.GOLD, lr));

        String msj = holos.mensaje(s.bukkitLocation());
        List<Component> lm = new ArrayList<>();
        lm.add(Component.text(msj.isBlank() ? "Todavía no tiene mensaje." : "Mensaje: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(msj.isBlank() ? Component.empty() : net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(msj)));
        lm.add(Component.empty());
        lm.add(Component.text("Clic izquierdo: escribir un mensaje", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        lm.add(Component.text("Clic derecho: borrar el mensaje", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        inv.setItem(SLOT_MENSAJE, boton(Material.OAK_SIGN, "Mensaje del holograma", NamedTextColor.AQUA, lm));

        inv.setItem(SLOT_MOVER, boton(Material.ITEM_FRAME, "Mover el holograma", NamedTextColor.GREEN,
                List.of(Component.text("Acercalo, alejalo, subilo o bajalo.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        Component.text("Clic para abrir", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))));
    }

    private static ItemStack boton(Material m, String nombre, NamedTextColor color, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.displayName(Component.text(nombre, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        im.lore(lore);
        it.setItemMeta(im);
        return it;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @EventHandler(priority = EventPriority.HIGH)
    public void alClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        Long id = ventanas.get(top);
        if (id == null) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != top) return;
        int slot = e.getSlot();
        if (slot != SLOT_ROTACION && slot != SLOT_MENSAJE && slot != SLOT_MOVER) return;
        e.setCancelled(true);
        Shop s;
        try { s = QuickShopAPI.getInstance().getShopManager().getShop(id); } catch (Throwable x) { s = null; }
        if (s == null) { p.closeInventory(); return; }
        if (!cmd.puedeEditar(p, s)) { p.closeInventory(); pl.msg(p, "Solo el dueño de la tienda puede cambiar su holograma."); return; }
        switch (slot) {
            case SLOT_ROTACION -> {
                Hologramas.Rotacion nueva = holos.rotacion(s.bukkitLocation()).siguiente(e.isRightClick() ? -1 : 1);
                holos.fijarRotacion(s, nueva);
                poner(top, s);
            }
            case SLOT_MENSAJE -> {
                if (e.isRightClick()) { holos.fijarMensaje(s, null); poner(top, s); pl.msg(p, "Listo, borraste el mensaje del holograma."); }
                else {
                    escribiendo.put(p.getUniqueId(), id);
                    p.closeInventory();
                    pl.msg(p, "Escribí en el chat el mensaje para el holograma (hasta " + max() + " letras, podés usar &colores). Para cancelar escribí: cancelar");
                }
            }
            case SLOT_MOVER -> MenuHolo.abrir(p, s);
            default -> { }
        }
    }

    @EventHandler
    public void alArrastrar(InventoryDragEvent e) {
        if (ventanas.containsKey(e.getView().getTopInventory())) e.setCancelled(true);
    }

    @EventHandler
    public void alCerrar(InventoryCloseEvent e) { ventanas.remove(e.getInventory()); }

    private int max() { return pl.getConfig().getInt("mensaje-max-letras", 32); }

    // ---------- mensaje por chat ----------

    @SuppressWarnings({"unchecked", "rawtypes"})
    @EventHandler(priority = EventPriority.LOWEST)
    public void alChat(AsyncChatEvent e) {
        Long id = escribiendo.remove(e.getPlayer().getUniqueId());
        if (id == null) return;
        e.setCancelled(true);
        String texto = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(pl, () -> {
            if (texto.equalsIgnoreCase("cancelar")) { pl.msg(p, "Cancelado."); return; }
            Shop s;
            try { s = QuickShopAPI.getInstance().getShopManager().getShop(id); } catch (Throwable x) { s = null; }
            if (s == null || !cmd.puedeEditar(p, s)) { pl.msg(p, "No pude encontrar esa tienda."); return; }
            String limpio = texto.replaceAll("(?i)https?://\\S+|www\\.\\S+", "").replaceAll("&[kK]", "").trim();
            if (limpio.isEmpty()) { pl.msg(p, "El mensaje quedó vacío. Probá de nuevo desde los ajustes de la tienda."); return; }
            if (limpio.replaceAll("&[0-9a-fA-Fl-oL-OrR]", "").length() > max()) {
                pl.msg(p, "Es muy largo (máximo " + max() + " letras). Probá de nuevo desde los ajustes de la tienda."); return;
            }
            holos.fijarMensaje(s, limpio);
            pl.getLogger().info("Mensaje de holograma de " + p.getName() + " en la tienda " + id + ": " + limpio);
            pl.msg(p, "Listo, el holograma de tu tienda ya tiene mensaje.");
        });
    }
}
