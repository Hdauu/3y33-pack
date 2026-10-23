package ar.com.tresyetres.core;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** /rtp simple: solo en el mundo normal, con espera, cooldown por rango y lugar seguro. */
final class Rtp implements Listener {

    private static final Set<Material> MALOS = EnumSet.of(Material.WATER, Material.LAVA, Material.MAGMA_BLOCK,
            Material.POWDER_SNOW, Material.CACTUS, Material.FIRE, Material.SOUL_FIRE, Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE, Material.SWEET_BERRY_BUSH, Material.POINTED_DRIPSTONE, Material.BEDROCK);

    private final Core core;
    private final Map<UUID, Long> ultimoUso = new HashMap<>();
    private final Map<UUID, BukkitTask> esperando = new HashMap<>();
    private final Set<UUID> buscando = new HashSet<>();

    Rtp(Core core) {
        this.core = core;
    }

    private String cfg(String k, String def) { return core.getConfig().getString("rtp." + k, def); }
    private int cfgInt(String k, int def) { return core.getConfig().getInt("rtp." + k, def); }

    void usar(Player p) {
        World mundo = Bukkit.getWorld(cfg("mundo", "3y33SMP"));
        if (mundo == null) { core.aviso(p, "<red>El RTP no está disponible ahora."); return; }
        if (!p.getWorld().equals(mundo)) {
            core.aviso(p, "<red>El /rtp solo funciona en el mundo normal.");
            return;
        }
        UUID id = p.getUniqueId();
        if (esperando.containsKey(id) || buscando.contains(id)) {
            core.aviso(p, "<gray>Ya estás por viajar, esperá un momento.");
            return;
        }
        long espera = cooldown(p) * 1000L;
        Long ultimo = ultimoUso.get(id);
        long ahora = System.currentTimeMillis();
        if (ultimo != null && ahora - ultimo < espera) {
            core.aviso(p, "<red>Podés volver a usar /rtp en <white>" + tiempo((espera - (ahora - ultimo)) / 1000 + 1) + "</white>.");
            return;
        }
        int segundos = Math.max(0, cfgInt("espera-segundos", 3));
        if (segundos == 0 || p.hasPermission("tresyetres.staff")) { buscar(p, mundo); return; }
        Location inicio = p.getLocation();
        int[] quedan = {segundos};
        esperando.put(id, Bukkit.getScheduler().runTaskTimer(core, () -> {
            if (!p.isOnline()) { cancelar(id); return; }
            if (quedan[0] <= 0) {
                cancelar(id);
                buscar(p, mundo);
                return;
            }
            p.sendActionBar(core.mm("<#FF6B6B>Viajando en <white>" + quedan[0] + "</white>... no te muevas"));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.4f);
            quedan[0]--;
        }, 0L, 20L));
    }

    private void buscar(Player p, World mundo) {
        UUID id = p.getUniqueId();
        buscando.add(id);
        p.sendActionBar(core.mm("<gray>Buscando un lugar seguro..."));
        intentar(p, mundo, cfgInt("intentos", 20));
    }

    private void intentar(Player p, World mundo, int quedan) {
        UUID id = p.getUniqueId();
        if (!p.isOnline()) { buscando.remove(id); return; }
        if (quedan <= 0) {
            buscando.remove(id);
            core.aviso(p, "<red>No encontramos un lugar seguro. Probá de nuevo.");
            return;
        }
        int min = cfgInt("radio-minimo", 500), max = cfgInt("radio-maximo", 9500);
        int cx = cfgInt("centro-x", 0), cz = cfgInt("centro-z", 0);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int x, z;
        do {
            x = cx + r.nextInt(-max, max + 1);
            z = cz + r.nextInt(-max, max + 1);
        } while (Math.max(Math.abs(x - cx), Math.abs(z - cz)) < min);
        final int fx = x, fz = z;
        mundo.getChunkAtAsync(fx >> 4, fz >> 4).thenAccept(chunk -> {
            Block suelo = mundo.getHighestBlockAt(fx, fz);
            Material t = suelo.getType();
            boolean ok = t.isSolid() && !MALOS.contains(t) && !t.name().endsWith("_LEAVES")
                    && suelo.getY() > mundo.getMinHeight() + 5
                    && suelo.getRelative(0, 1, 0).isPassable() && !MALOS.contains(suelo.getRelative(0, 1, 0).getType())
                    && suelo.getRelative(0, 2, 0).isPassable();
            if (!ok) { intentar(p, mundo, quedan - 1); return; }
            Location destino = new Location(mundo, fx + 0.5, suelo.getY() + 1, fz + 0.5, p.getLocation().getYaw(), 0f);
            p.teleportAsync(destino, PlayerTeleportEvent.TeleportCause.COMMAND).thenAccept(listo -> {
                buscando.remove(id);
                if (!listo) { core.aviso(p, "<red>No se pudo viajar. Probá de nuevo."); return; }
                ultimoUso.put(id, System.currentTimeMillis());
                p.playSound(destino, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f);
                p.showTitle(Title.title(core.mm("<#E03131><bold>¡A explorar!</bold></#E03131>"),
                        core.mm("<gray>Estás en <white>" + fx + "</white>, <white>" + fz + "</white>"),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(700))));
                core.aviso(p, "<gray>Si te gusta el lugar, protegelo con <#FF6B6B><click:run_command:'/terrenos'>/terrenos</click></#FF6B6B>.");
            });
        });
    }

    private int cooldown(Player p) {
        if (p.hasPermission("tresyetres.staff")) return 0;
        int base = cfgInt("cooldown.default", 600);
        for (String rango : List.of("global", "supreme", "sheriff")) {
            if (p.hasPermission("group." + rango) && core.getConfig().contains("rtp.cooldown." + rango))
                return Math.min(base, cfgInt("cooldown." + rango, base));
        }
        return base;
    }

    private static String tiempo(long s) {
        return s >= 60 ? (s / 60) + "m " + (s % 60) + "s" : s + "s";
    }

    private void cancelar(UUID id) {
        BukkitTask t = esperando.remove(id);
        if (t != null) t.cancel();
    }

    @EventHandler(ignoreCancelled = true)
    public void alMoverse(PlayerMoveEvent e) {
        if (!e.hasChangedBlock() || !esperando.containsKey(e.getPlayer().getUniqueId())) return;
        cancelar(e.getPlayer().getUniqueId());
        e.getPlayer().sendActionBar(core.mm("<red>Viaje cancelado porque te moviste."));
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent e) {
        cancelar(e.getPlayer().getUniqueId());
        buscando.remove(e.getPlayer().getUniqueId());
    }
}
