package ar.com.tresyetres.shops;

import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /tiendaadmin para el staff: lista, info y borrar. */
final class TiendaAdminComando implements CommandExecutor, TabCompleter {
    private final ShopsPlugin pl;

    TiendaAdminComando(ShopsPlugin pl) { this.pl = pl; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("tresyetres.staff")) { Msg.error(s, "No tenés permiso."); return true; }
        String sub = a.length == 0 ? "lista" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "lista" -> Msg.info(s, "Hay " + Msg.numero(pl.almacen().todas().size()) + " tiendas.");
            case "rotar" -> {
                if (a.length < 3) { Msg.error(s, "Uso: /tiendaadmin rotar <id de tienda> <ARRIBA|NORTE|ESTE|SUR|OESTE|ABAJO>"); return true; }
                Tienda t;
                try { t = pl.almacen().porId(Long.parseLong(a[1])); } catch (NumberFormatException e) { t = null; }
                String rot = a[2].toUpperCase(Locale.ROOT);
                if (t == null) { Msg.error(s, "No existe esa tienda."); return true; }
                if (!Ajustes.ROTACIONES.contains(rot)) { Msg.error(s, "Rotación inválida."); return true; }
                t.rotacion = rot;
                pl.almacen().guardar(t);
                Msg.ok(s, "Tienda " + t.id + " con rotación " + rot + ".");
            }
            case "recargar" -> { pl.reloadConfig(); Msg.ok(s, "Config recargada."); }
            case "info", "borrar" -> {
                if (!(s instanceof Player p)) { Msg.error(s, "Solo en el juego."); return true; }
                Block b = p.getTargetBlockExact(6);
                Tienda t = pl.almacen().en(b);
                if (t == null) { Msg.error(p, "Mirá el cofre de una tienda."); return true; }
                if (sub.equals("borrar")) {
                    pl.almacen().borrar(t);
                    Msg.ok(p, "Tienda " + t.id + " borrada. Los ítems siguen en el cofre.");
                    pl.getLogger().info(p.getName() + " borró (staff) la tienda " + t.id);
                } else {
                    String dueno = org.bukkit.Bukkit.getOfflinePlayer(t.dueno).getName();
                    Msg.info(p, "Tienda " + t.id + " | " + VentanaTienda.nombreItem(t) + " | dueño: " + dueno + " | tierra: " + t.tierra
                            + " | compra: " + (t.compra < 0 ? "-" : Msg.dinero(t.compra)) + " | venta: " + (t.venta < 0 ? "-" : Msg.dinero(t.venta)));
                }
            }
            default -> Msg.info(s, "Uso: /tiendaadmin [lista|info|borrar|rotar|recargar]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) return List.of("lista", "info", "borrar", "recargar").stream().filter(x -> x.startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
