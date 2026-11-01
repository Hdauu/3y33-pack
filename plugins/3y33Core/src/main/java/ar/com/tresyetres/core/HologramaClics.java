package ar.com.tresyetres.core;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.*;

/** Hace clickeables los carteles del spawn: mirás el cartel, hacés clic y se abre su menú. */
final class HologramaClics implements Listener {

    private static final Map<String, String> COMANDOS = Map.of(
            "terrenos", "tierras",
            "misiones", "misiones",
            "niveles", "nivel",
            "reglas", "reglas",
            "explorar", "rtp",
            "discord", "discord");

    private final Map<UUID, Long> ultimo = new HashMap<>();

    @EventHandler
    public void alClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() == Action.PHYSICAL) return;
        Player p = e.getPlayer();
        if (!Bukkit.getPluginManager().isPluginEnabled("FancyHolograms")) return;
        long ahora = System.currentTimeMillis();
        if (ahora - ultimo.getOrDefault(p.getUniqueId(), 0L) < 600) return;

        Location ojo = p.getEyeLocation();
        Vector dir = ojo.getDirection().normalize();
        String mejor = null;
        double mejorT = Double.MAX_VALUE;
        for (Hologram h : FancyHologramsPlugin.get().getHologramManager().getHolograms()) {
            String n = h.getData().getName().toLowerCase(Locale.ROOT);
            int i = n.indexOf('_');
            if (i < 0 || !(n.startsWith("spawn_") || n.startsWith("beta_"))) continue;
            String clave = n.substring(i + 1);
            if (!COMANDOS.containsKey(clave)) continue;
            Location l = h.getData().getLocation();
            if (l == null || l.getWorld() == null || !l.getWorld().equals(ojo.getWorld())) continue;
            Vector a = l.toVector().subtract(ojo.toVector());
            double t = a.dot(dir);
            if (t < 0 || t > 7) continue;
            double dist = a.clone().subtract(dir.clone().multiply(t)).length();
            // el cartel mide unos 2 bloques de ancho y 1 de alto
            Vector perp = a.clone().subtract(dir.clone().multiply(t));
            double horiz = Math.hypot(perp.getX(), perp.getZ());
            if (horiz > 1.3 || Math.abs(perp.getY()) > 0.7) continue;
            if (t < mejorT) { mejorT = t; mejor = clave; }
        }
        if (mejor == null) return;
        ultimo.put(p.getUniqueId(), ahora);
        e.setCancelled(true);
        if (mejor.equals("discord")) {
            var mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
            p.sendMessage(mm.deserialize("<dark_red><bold>3y33</bold> <dark_gray>» <white>Sumate a nuestro Discord haciendo clic "
                    + "<click:open_url:'https://ds.3y33.com.ar'><hover:show_text:'<gray>ds.3y33.com.ar'><red><bold><underlined>aquí</underlined></bold></red></hover></click><white>."));
            return;
        }
        p.performCommand(COMANDOS.get(mejor));
    }
}
