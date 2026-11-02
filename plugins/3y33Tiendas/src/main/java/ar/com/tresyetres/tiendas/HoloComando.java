package ar.com.tresyetres.tiendas;

import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.shop.Shop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** /holo: mueve el holograma (item + texto) de TU tienda. Mirá la tienda y usá la accion. */
final class HoloComando implements CommandExecutor, TabCompleter {
    private static final double PASO = 0.25;
    private final TiendasPlugin pl;
    private final Hologramas holos;

    HoloComando(TiendasPlugin pl, Hologramas holos) { this.pl = pl; this.holos = holos; }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Shop<?, ?> tienda(Player p) {
        var sm = QuickShopAPI.getInstance().getShopManager();
        Block b = p.getTargetBlockExact(6);
        if (b != null) {
            Shop s = sm.getShop(b.getLocation());
            if (s != null) return s;
        }
        Shop mejor = null;
        double d2 = 25;
        for (Shop s : (Set<Shop>) (Set) sm.getLoadedShops()) {
            Location l = s.bukkitLocation();
            if (l == null || !l.getWorld().equals(p.getWorld())) continue;
            double d = l.clone().add(0.5, 0.5, 0.5).distanceSquared(p.getEyeLocation());
            if (d < d2 && esDueno(p, s)) { d2 = d; mejor = s; }
        }
        return mejor;
    }

    private boolean esDueno(Player p, Shop<?, ?> s) {
        if (p.hasPermission("tresyetres.staff")) return true;
        try { return s.getOwner().getUniqueIdIfRealPlayer().map(u -> u.equals(p.getUniqueId())).orElse(false); } catch (Throwable t) { return false; }
    }

    private static BlockFace derecha(BlockFace f) {
        return switch (f) { case NORTH -> BlockFace.EAST; case EAST -> BlockFace.SOUTH; case SOUTH -> BlockFace.WEST; default -> BlockFace.NORTH; };
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Solo en el juego."); return true; }
        if (a.length == 0) { panel(p); return true; }
        Shop<?, ?> s = tienda(p);
        if (s == null) { pl.msg(p, "Mirá tu tienda (o acercate) y volvé a usar el comando."); return true; }
        if (!esDueno(p, s)) { pl.msg(p, "Solo el dueño de la tienda puede mover su holograma."); return true; }
        int pasos = 1;
        if (a.length > 1) {
            try { pasos = Math.max(1, Math.min(12, Integer.parseInt(a[1]))); } catch (NumberFormatException e) { pl.msg(p, "El segundo dato tiene que ser un número de pasos (1 a 12)."); return true; }
        }
        double d = PASO * pasos;
        BlockFace f = p.getFacing();
        BlockFace r = derecha(f);
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "izquierda", "izq" -> holos.mover(s, -r.getModX() * d, 0, -r.getModZ() * d);
            case "derecha", "der" -> holos.mover(s, r.getModX() * d, 0, r.getModZ() * d);
            case "adelante" -> holos.mover(s, f.getModX() * d, 0, f.getModZ() * d);
            case "atras" -> holos.mover(s, -f.getModX() * d, 0, -f.getModZ() * d);
            case "subir", "arriba" -> holos.mover(s, 0, d, 0);
            case "bajar", "abajo" -> holos.mover(s, 0, -d, 0);
            case "reiniciar", "reset" -> holos.reiniciar(s);
            default -> { panel(p); return true; }
        }
        pl.msg(p, "Listo, moviste el holograma de tu tienda.");
        return true;
    }

    private Component boton(String texto, String accion) {
        return Component.text("[" + texto + "]", NamedTextColor.RED, TextDecoration.BOLD).clickEvent(ClickEvent.runCommand("/holo " + accion))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text("Click para: " + texto)));
    }

    private void panel(Player p) {
        pl.msg(p, "Mirá tu tienda y tocá un botón (también podés escribir /holo <acción> [pasos]):");
        p.sendMessage(Component.text("  ").append(boton("izquierda", "izquierda")).append(Component.text(" "))
                .append(boton("derecha", "derecha")).append(Component.text(" "))
                .append(boton("adelante", "adelante")).append(Component.text(" "))
                .append(boton("atrás", "atras")));
        p.sendMessage(Component.text("  ").append(boton("subir", "subir")).append(Component.text(" "))
                .append(boton("bajar", "bajar")).append(Component.text(" "))
                .append(boton("reiniciar", "reiniciar")));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) return holos.nombresAcciones().stream().filter(x -> x.startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
        if (a.length == 2) return List.of("1", "2", "4", "8");
        return List.of();
    }
}
