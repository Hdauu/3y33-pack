package ar.com.tresyetres.tierras;

import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.shop.Shop;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;

/** Panel del dueño de una tienda: todo en un menú simple y blanco, con colores solo en lo importante. */
final class TiendaPanel implements Listener {
    private final Tierras pl;
    private final TiendaTexto texto;
    private final Map<UUID, Long> confirmaBorrar = new HashMap<>();
    private final Map<UUID, Double> paso = new HashMap<>();

    TiendaPanel(Tierras pl, TiendaTexto texto) {
        this.pl = pl;
        this.texto = texto;
    }

    // ---------------------------------------------------------------- abrir

    @EventHandler(priority = EventPriority.HIGHEST)
    public void alClickear(PlayerInteractEvent e) {
        if (e.getAction() != Action.LEFT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (!p.isSneaking() || e.getClickedBlock() == null) return;
        var mgr = QuickShopAPI.getInstance().getShopManager();
        Shop sh = mgr.getShopIncludeAttached(e.getClickedBlock().getLocation());
        if (sh == null) return;
        e.setCancelled(true);
        if (!puede(p, sh)) {
            pl.msg(p, "&cSolo el dueño puede editar esta tienda.");
            return;
        }
        abrir(p, sh);
    }

    private boolean puede(Player p, Shop sh) {
        return sh.getOwner().getUniqueIdOptional().map(u -> u.equals(p.getUniqueId())).orElse(false)
                || p.hasPermission("tresyetres.staff");
    }

    private String tit(String t) { return "<white>" + t; }

    // ---------------------------------------------------------------- panel principal

    void abrir(Player p, Shop sh) {
        Menu m = new Menu(4, "<dark_gray>Tu tienda » <black>" + Menu.esc(texto.terreno(sh)));
        blanco(m, 4);
        ItemStack mostrar = sh.getItem().clone();
        mostrar.setAmount(1);
        var meta = mostrar.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(Menu.MM.deserialize("<!italic><gray>" + (sh.isBuying() ? "Compra" : "Vende") + " a <green><bold>" + TiendaTexto.plata(sh.getPrice())));
        lore.add(Menu.MM.deserialize("<!italic><gray>" + (sh.isBuying() ? "Compra hasta: " : "Stock: ") + "<yellow>" + texto.stock(sh)));
        if (sh.isFrozen()) lore.add(Menu.MM.deserialize("<!italic><aqua>Congelada"));
        meta.lore(lore);
        mostrar.setItemMeta(meta);
        m.poner(4, mostrar, null);

        m.poner(11, Menu.item(Material.GOLD_NUGGET, "<gold><bold>Precio", "<gray>Ahora: <green><bold>" + TiendaTexto.plata(sh.getPrice()),
                "", "<gray>Click para cambiarlo"), (pp, c) -> pedirPrecio(pp, sh));
        m.poner(13, Menu.item(sh.isBuying() ? Material.HOPPER : Material.CHEST, "<white><bold>Modo",
                "<gray>Ahora: " + (sh.isBuying() ? "<gold><bold>Comprás vos" : "<green><bold>Vendés vos"),
                "<dark_gray>" + (sh.isBuying() ? "Los jugadores te venden el objeto." : "Los jugadores te compran el objeto."),
                "", "<gray>Click para cambiar"), (pp, c) -> {
            sh.shopType(sh.isBuying() ? "SELLING" : "BUYING");
            sh.update();
            refrescar(pp, sh);
        });
        m.poner(15, Menu.item(sh.isFrozen() ? Material.PACKED_ICE : Material.SNOWBALL, "<white><bold>Congelar",
                "<gray>Ahora: " + (sh.isFrozen() ? "<aqua><bold>Congelada" : "<green><bold>Activa"),
                "<dark_gray>Congelada, nadie puede comprar ni vender.", "", "<gray>Click para " + (sh.isFrozen() ? "descongelar" : "congelar")), (pp, c) -> {
            sh.shopState(sh.isFrozen() ? "active" : "frozen");
            sh.update();
            refrescar(pp, sh);
        });
        m.poner(20, Menu.item(Material.NAME_TAG, "<white><bold>Texto flotante",
                "<gray>Movelo alrededor del cofre", "<gray>para acomodarlo a tu gusto.", "", "<gray>Click para abrir"), (pp, c) -> abrirTexto(pp, sh));
        m.poner(22, Menu.item(Material.CHEST_MINECART, "<white><bold>Stock", "<gray>" + (sh.isBuying() ? "Compra hasta: " : "Hay: ") + "<yellow><bold>" + texto.stock(sh),
                "<dark_gray>Abrí el cofre con clic derecho", "<dark_gray>para cargar o sacar objetos."), null);
        m.poner(24, Menu.cabeza(org.bukkit.Bukkit.getOfflinePlayer(sh.getOwner().getUniqueIdOptional().orElse(p.getUniqueId())),
                "<white><bold>Dueño", "<gray>" + Menu.esc(texto.dueno(sh)), "<gray>Terreno: <white>" + Menu.esc(texto.terreno(sh))), null);
        final java.util.function.BiConsumer<Player, org.bukkit.event.inventory.ClickType>[] borrar = new java.util.function.BiConsumer[1];
        borrar[0] = (pp, c) -> {
            Long hasta = confirmaBorrar.get(pp.getUniqueId());
            if (hasta != null && hasta > System.currentTimeMillis()) {
                confirmaBorrar.remove(pp.getUniqueId());
                texto.retirar(sh.getShopId());
                QuickShopAPI.getInstance().getShopManager().deleteShop(sh);
                pp.closeInventory();
                pl.msg(pp, "&7Tienda borrada.");
            } else {
                confirmaBorrar.put(pp.getUniqueId(), System.currentTimeMillis() + 5000);
                m.poner(31, Menu.item(Material.TNT, "<red><bold>¿Seguro?", "<gray>Tocá otra vez en 5 segundos", "<gray>para borrar la tienda."), borrar[0]);
                pp.playSound(pp.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            }
        };
        m.poner(31, Menu.item(Material.LAVA_BUCKET, "<red><bold>Borrar tienda", "<gray>Se saca la tienda, el cofre queda.", "", "<gray>Click para borrar"), borrar[0]);
        m.poner(35, Menu.item(Material.BARRIER, "<gray>Cerrar"), (pp, c) -> pp.closeInventory());
        p.openInventory(m.getInventory());
    }

    private void refrescar(Player p, Shop sh) {
        org.bukkit.Bukkit.getScheduler().runTaskLater(pl, () -> abrir(p, sh), 3L);
    }

    private void blanco(Menu m, int filas) {
        ItemStack b = Menu.vidrio(Material.WHITE_STAINED_GLASS_PANE);
        for (int i = 0; i < filas * 9; i++) m.poner(i, b, null);
    }

    // ---------------------------------------------------------------- precio por chat

    private void pedirPrecio(Player p, Shop sh) {
        p.closeInventory();
        pl.pedirTexto(p, "Precio", "el precio nuevo (por unidad)", txt -> {
            double v;
            try {
                v = Double.parseDouble(txt.trim().replace(",", ".").replace("$", ""));
            } catch (NumberFormatException e) {
                pl.msg(p, "&cEso no es un número. Escribí algo como &f25&c o &f12.5&c.");
                return;
            }
            if (v <= 0 || v > 1_000_000_000) {
                pl.msg(p, "&cEl precio tiene que ser mayor a 0.");
                return;
            }
            sh.setPrice(v);
            sh.update();
            pl.msg(p, "&7Precio nuevo: &a" + TiendaTexto.plata(v) + "&7.");
            refrescar(p, sh);
        });
    }

    // ---------------------------------------------------------------- mover el texto

    void abrirTexto(Player p, Shop sh) {
        double pasoActual = paso.getOrDefault(p.getUniqueId(), 0.5);
        Menu m = new Menu(5, "<dark_gray>Texto » <black>" + Menu.esc(texto.terreno(sh)));
        blanco(m, 5);
        Vector o = texto.offset(sh.getShopId());
        m.poner(0, Menu.item(Material.PAPER, "<white><bold>Mover el texto", "<gray>Cada click lo mueve <white>" + pasoActual + " <gray>bloques.",
                "<gray>Mirá el texto y movelo con las flechas."), null);
        flecha(m, 2, Material.BLUE_CONCRETE, "Norte", sh, 0, 0, -1);
        flecha(m, 10, Material.YELLOW_CONCRETE, "Oeste", sh, -1, 0, 0);
        flecha(m, 12, Material.RED_CONCRETE, "Este", sh, 1, 0, 0);
        flecha(m, 20, Material.CYAN_CONCRETE, "Sur", sh, 0, 0, 1);
        flecha(m, 13, Material.LIME_CONCRETE, "Arriba", sh, 0, 1, 0);
        flecha(m, 22, Material.GREEN_CONCRETE, "Abajo", sh, 0, -1, 0);
        m.poner(11, Menu.item(Material.CLOCK, "<white><bold>Paso: <yellow>" + pasoActual, "<gray>Click para cambiarlo", "<dark_gray>0.1 · 0.5 · 1"), (pp, c) -> {
            double n = pasoActual < 0.2 ? 0.5 : pasoActual < 0.9 ? 1.0 : 0.1;
            paso.put(pp.getUniqueId(), n);
            abrirTexto(pp, sh);
        });
        m.poner(15, Menu.item(Material.ENDER_PEARL, "<white><bold>Volver al centro", "<gray>Lo deja sobre el cofre."), (pp, c) -> {
            texto.restablecer(sh.getShopId());
            abrirTexto(pp, sh);
        });
        m.poner(31, Menu.item(Material.ARROW, "<gray>Volver"), (pp, c) -> abrir(pp, sh));
        m.poner(40, Menu.item(Material.BARRIER, "<gray>Cerrar"), (pp, c) -> pp.closeInventory());
        m.poner(36, Menu.item(Material.COMPASS, "<white><bold>Posición", "<gray>Este/Oeste: <white>" + String.format(Locale.ROOT, "%.1f", o.getX()),
                "<gray>Altura: <white>" + String.format(Locale.ROOT, "%.1f", o.getY()), "<gray>Norte/Sur: <white>" + String.format(Locale.ROOT, "%.1f", o.getZ())), null);
        p.openInventory(m.getInventory());
    }

    private void flecha(Menu m, int slot, Material mat, String nombre, Shop sh, int dx, int dy, int dz) {
        m.poner(slot, Menu.item(mat, "<white><bold>" + nombre, "<gray>Click para mover"), (pp, c) -> {
            double s = paso.getOrDefault(pp.getUniqueId(), 0.5);
            Vector v = texto.offset(sh.getShopId());
            v.add(new Vector(dx * s, dy * s, dz * s));
            texto.guardarOffset(sh.getShopId(), v);
            org.bukkit.Bukkit.getScheduler().runTaskLater(pl, () -> abrirTexto(pp, sh), 2L);
        });
    }
}
