package ar.com.tresyetres.tiendas;

import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.shop.Shop;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Cuando alguien viaja a una tienda desde el explorador de QuickShop, lo llevamos al spawn de la TIERRA (no al cofre). */
final class BrowseLand implements Listener {
    private final TiendasPlugin pl;
    private final LandsPuente lands;
    private final Map<UUID, Long> clics = new HashMap<>();

    BrowseLand(TiendasPlugin pl, LandsPuente lands) { this.pl = pl; this.lands = lands; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getSize() != 54 || e.getClickedInventory() != top) return;
        clics.put(e.getWhoClicked().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent e) { clics.remove(e.getPlayer().getUniqueId()); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void alTeletransportar(PlayerTeleportEvent e) {
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN && e.getCause() != PlayerTeleportEvent.TeleportCause.COMMAND) return;
        Player p = e.getPlayer();
        Long t = clics.get(p.getUniqueId());
        if (t == null || System.currentTimeMillis() - t > 3000) return;
        Location to = e.getTo();
        if (to == null || to.getWorld() == null) return;
        Shop mejor = null;
        double d2 = 16;
        for (Shop s : (Set<Shop>) (Set) QuickShopAPI.getInstance().getShopManager().getLoadedShops()) {
            Location l = s.bukkitLocation();
            if (l == null || !l.getWorld().equals(to.getWorld())) continue;
            double d = l.clone().add(0.5, 0.5, 0.5).distanceSquared(to);
            if (d < d2) { d2 = d; mejor = s; }
        }
        if (mejor == null) return;
        clics.remove(p.getUniqueId());
        Location sp = lands.spawn(mejor.bukkitLocation());
        LandsPuente.Terreno ter = lands.en(mejor.bukkitLocation(), null);
        if (sp == null) {
            if (ter != null) pl.msg(p, "La tierra " + ter.nombre() + " no tiene un punto de teletransporte; te llevé a la tienda.");
            return;
        }
        e.setTo(sp);
        if (ter != null) pl.msg(p, "Te llevé a la tierra " + ter.nombre() + ".");
    }
}
