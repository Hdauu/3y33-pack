package ar.com.tresyetres.shops;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;

import java.util.List;
import java.util.Locale;

/** /tienda crear y /tienda eliminar (el resto de los comandos llega en las proximas etapas). */
final class TiendaComando implements CommandExecutor, TabCompleter {
    private final ShopsPlugin pl;

    TiendaComando(ShopsPlugin pl) { this.pl = pl; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Solo en el juego."); return true; }
        if (a.length == 0) { ayuda(p); return true; }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "crear", "create" -> crear(p, a);
            case "eliminar", "borrar", "delete", "remove" -> eliminar(p);
            case "reload", "recargar" -> {
                if (!p.hasPermission("tresyetres.staff")) { Msg.error(p, "No tenés permiso."); return true; }
                pl.reloadConfig();
                if (pl.holograma() != null) pl.holograma().reconstruir();
                Msg.ok(p, "Recargué la config y rearmé los hologramas.");
            }
            default -> ayuda(p);
        }
        return true;
    }

    private void ayuda(Player p) {
        Msg.info(p, "Tiendas de cofre:");
        linea(p, "/tienda crear (precio de compra) (precio de venta)", "Crea una tienda en un cofre que estés mirando con el ítem que querés vender en la mano. Usá - para desactivar compra o venta.");
        linea(p, "/tienda eliminar", "Elimina la tienda del cofre que estás mirando.");
        Msg.info(p, "Después hacé clic derecho al cofre para abrir la tienda.");
    }

    private void linea(Player p, String comando, String detalle) {
        p.sendMessage(Component.text("- ", NamedTextColor.DARK_GRAY).append(Component.text(comando, NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(" | " + detalle, NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false)));
    }

    // ---------- crear ----------

    private static boolean valido(Block b) {
        Material m = b.getType();
        if (m != Material.CHEST && m != Material.TRAPPED_CHEST && m != Material.BARREL && !m.name().contains("COPPER_CHEST")) return false;
        return b.getState() instanceof Container;
    }

    /** La otra mitad de un doble cofre, o null si el cofre es simple. */
    static Block otraMitad(Block b) {
        if (!(b.getBlockData() instanceof org.bukkit.block.data.type.Chest cd) || cd.getType() == org.bukkit.block.data.type.Chest.Type.SINGLE) return null;
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block v = b.getRelative(f);
            if (v.getType() != b.getType()) continue;
            if (v.getBlockData() instanceof org.bukkit.block.data.type.Chest cv && cv.getType() != org.bukkit.block.data.type.Chest.Type.SINGLE
                    && cv.getFacing() == cd.getFacing()) return v;
        }
        return null;
    }

    private long precio(String s) { return Msg.precio(s, pl.getConfig().getDouble("precio-maximo", 1_000_000)); }

    private static boolean tieneContenido(ItemStack it) {
        if (it.getItemMeta() instanceof BlockStateMeta bsm && bsm.hasBlockState() && bsm.getBlockState() instanceof Container c && !c.getInventory().isEmpty()) return true;
        return it.getItemMeta() instanceof BundleMeta bm && bm.hasItems();
    }

    private void crear(Player p, String[] a) {
        if (a.length < 2) { Msg.error(p, "Uso: /tienda crear (precio de compra) (precio de venta)"); return; }
        ItemStack mano = p.getInventory().getItemInMainHand();
        if (mano.getType().isAir()) { Msg.error(p, "Tenés que tener en la mano el ítem que querés vender o comprar."); return; }
        if (tieneContenido(mano)) { Msg.error(p, "No se pueden vender ítems con otros ítems adentro (shulkers, bundles, etc.)."); return; }
        Block b = p.getTargetBlockExact(6);
        if (b == null || !valido(b)) { Msg.error(p, "Mirá el cofre (o barril) donde querés crear la tienda."); return; }
        if (pl.almacen().en(b) != null) { Msg.error(p, "Ese cofre ya es una tienda."); return; }
        Block otra = otraMitad(b);
        if (otra != null && pl.almacen().en(otra) != null) { Msg.error(p, "Ese cofre ya es una tienda."); return; }

        LandsPuente.Terreno ter = pl.lands().en(b.getLocation(), p.getUniqueId());
        if (ter == null) { Msg.error(p, "Solo podés crear tiendas dentro de una tierra. Creá la tuya con /lands."); return; }
        if (!ter.miembro() && !p.hasPermission("tresyetres.staff")) { Msg.error(p, "Solo podés crear tiendas en tierras donde seas miembro."); return; }
        int max = pl.getConfig().getInt("maximo-tiendas-por-tierra", 30);
        if (pl.almacen().cantidadEnTierra(ter.nombre()) >= max && !p.hasPermission("tresyetres.staff")) {
            Msg.error(p, "La tierra " + ter.nombre() + " ya llegó al máximo de " + max + " tiendas."); return;
        }

        long compra = precio(a[1]);
        long venta = a.length > 2 ? precio(a[2]) : -1;
        if (compra == -2 || venta == -2) { Msg.error(p, "Los precios tienen que ser números (hasta 2 decimales) o - para desactivar."); return; }
        if (compra == -3 || venta == -3) { Msg.error(p, "El precio máximo es " + Msg.dinero((long) (pl.getConfig().getDouble("precio-maximo", 1_000_000) * 100)) + "."); return; }
        if (compra < 0 && venta < 0) { Msg.error(p, "Al menos una de las dos operaciones tiene que estar activa."); return; }

        Tienda t = new Tienda();
        t.id = pl.almacen().nuevoId();
        t.mundo = b.getWorld().getName();
        t.x = b.getX(); t.y = b.getY(); t.z = b.getZ();
        if (otra != null) { t.doble = true; t.x2 = otra.getX(); t.y2 = otra.getY(); t.z2 = otra.getZ(); }
        t.tierra = ter.nombre();
        t.dueno = p.getUniqueId();
        t.item = mano.clone();
        t.item.setAmount(1);
        t.compra = compra;
        t.venta = venta;
        t.creada = System.currentTimeMillis();
        pl.almacen().agregar(t);
        Msg.ok(p, "¡Creaste una tienda en el cofre! Hacé clic derecho para abrirla.");
        pl.getLogger().info(p.getName() + " creó la tienda " + t.id + " (" + VentanaTienda.nombreItem(t) + ", compra " + compra + ", venta " + venta + ") en " + t.mundo + " " + t.x + "," + t.y + "," + t.z);
    }

    // ---------- eliminar ----------

    private void eliminar(Player p) {
        Block b = p.getTargetBlockExact(6);
        Tienda t = pl.almacen().en(b);
        if (t == null) { Msg.error(p, "Mirá el cofre de la tienda que querés eliminar."); return; }
        if (!t.esDeLosDuenos(p) && !p.hasPermission("tresyetres.staff")) { Msg.error(p, "Solo el dueño puede eliminar esta tienda."); return; }
        pl.almacen().borrar(t);
        Msg.ok(p, "Eliminaste la tienda. Los ítems siguen en el cofre.");
        pl.getLogger().info(p.getName() + " eliminó la tienda " + t.id);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) return (s.hasPermission("tresyetres.staff") ? List.of("crear", "eliminar", "reload") : List.of("crear", "eliminar")).stream().filter(x -> x.startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
        if (a.length >= 2 && a[0].equalsIgnoreCase("crear")) return List.of("-", "1", "10", "100");
        return List.of();
    }
}
