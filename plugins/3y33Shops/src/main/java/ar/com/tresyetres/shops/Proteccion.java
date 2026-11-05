package ar.com.tresyetres.shops;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Protecciones de las tiendas (las mismas que activa QuickShop en este server: explosiones, tolvas, droppers y entidades,
 * mas romper, pistones, fuego, cofres que se juntan y tolvas que levantan items).
 */
final class Proteccion implements Listener {
    private final ShopsPlugin pl;

    Proteccion(ShopsPlugin pl) { this.pl = pl; }

    private boolean esTienda(Block b) { return pl.almacen().en(b) != null; }

    /** Si un inventario (o alguna mitad de un doble cofre) pertenece a una tienda. */
    private boolean esTienda(Inventory inv) {
        if (inv == null) return false;
        InventoryHolder h;
        try { h = inv.getHolder(false); } catch (Throwable t) { h = inv.getHolder(); }
        if (h instanceof DoubleChest dc) return esTienda(dc.getLeftSide(false)) || esTienda(dc.getRightSide(false));
        return esTienda(h);
    }

    private boolean esTienda(InventoryHolder h) {
        if (h instanceof BlockState bs) return esTienda(bs.getBlock());
        return false;
    }

    // ---------- romper ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void alRomper(BlockBreakEvent e) {
        if (!esTienda(e.getBlock())) return;
        e.setCancelled(true);
        Msg.error(e.getPlayer(), "Ese cofre es una tienda. Para sacarla usá /tienda eliminar mirándola.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void alQuemarse(BlockBurnEvent e) {
        if (esTienda(e.getBlock())) e.setCancelled(true);
    }

    // ---------- explosiones ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void alExplotar(EntityExplodeEvent e) { e.blockList().removeIf(this::esTienda); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void alExplotarBloque(BlockExplodeEvent e) { e.blockList().removeIf(this::esTienda); }

    // ---------- pistones ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void pistonEmpuja(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) if (esTienda(b)) { e.setCancelled(true); return; }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void pistonTira(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) if (esTienda(b)) { e.setCancelled(true); return; }
    }

    // ---------- tolvas, droppers y vagonetas ----------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void alMoverItems(InventoryMoveItemEvent e) {
        if (esTienda(e.getSource()) || esTienda(e.getDestination())) e.setCancelled(true);
    }

    /** Una tolva que levanta items del piso y los mete al cofre de una tienda. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void alLevantarItems(InventoryPickupItemEvent e) {
        if (esTienda(e.getInventory())) e.setCancelled(true);
    }

    // ---------- entidades (endermen, etc.) ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void alCambiarBloque(EntityChangeBlockEvent e) {
        if (esTienda(e.getBlock())) e.setCancelled(true);
    }

    // ---------- cofres que se juntan ----------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void alPoner(BlockPlaceEvent e) {
        Material m = e.getBlockPlaced().getType();
        if (m != Material.CHEST) return;
        Block b = e.getBlockPlaced();
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block vecino = b.getRelative(f);
            if (vecino.getType() == m && esTienda(vecino)) {
                e.setCancelled(true);
                Player p = e.getPlayer();
                Msg.error(p, "No podés poner un cofre pegado a una tienda.");
                return;
            }
        }
    }
}
