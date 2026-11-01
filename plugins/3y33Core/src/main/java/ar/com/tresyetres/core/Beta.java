package ar.com.tresyetres.core;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Regalo por jugar la beta: el estilo de nombre "Argentina" (permiso tresyetres.estilo.argentina). Se apaga con beta.activa: false. */
final class Beta implements Listener {
    private static final String PERMISO = "tresyetres.estilo.argentina";
    private final Core core;

    Beta(Core core) {
        this.core = core;
        // una sola vez: a todos los que ya jugaron antes de que existiera el regalo
        Bukkit.getScheduler().runTaskLater(core, () -> {
            if (!core.getConfig().getBoolean("beta.activa", true) || core.getConfig().getBoolean("beta.retroactivo-hecho", false)) return;
            int n = 0;
            for (OfflinePlayer o : Bukkit.getOfflinePlayers()) {
                if (!o.hasPlayedBefore()) continue;
                dar(o.getUniqueId().toString());
                n++;
            }
            core.getConfig().set("beta.retroactivo-hecho", true);
            core.saveConfig();
            core.getLogger().info("Regalo de la beta entregado a " + n + " jugadores que ya habian jugado.");
        }, 200L);
    }

    private void dar(String uuid) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + uuid + " permission set " + PERMISO + " true");
    }

    @EventHandler
    public void alEntrar(PlayerJoinEvent e) {
        if (!core.getConfig().getBoolean("beta.activa", true)) return;
        Player p = e.getPlayer();
        if (p.hasPermission(PERMISO)) return;
        dar(p.getUniqueId().toString());
        Bukkit.getScheduler().runTaskLater(core, () -> {
            if (p.isOnline()) p.sendMessage(core.mm("<dark_gray>3y33 »</dark_gray> <white>Por jugar la beta ganaste el estilo <gradient:#74ACDF:#FFFFFF:#74ACDF><bold>Argentina</bold></gradient><white> para tu nombre. Elegilo en <red>/color</red><white>."));
        }, 100L);
    }
}
