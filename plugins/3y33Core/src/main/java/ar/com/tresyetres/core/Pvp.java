package ar.com.tresyetres.core;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** PvP opcional por jugador + etiqueta de combate (si te desconectás peleando, morís y soltás todo). */
final class Pvp implements Listener {
    private final Core core;
    private final File archivo;
    private final Set<UUID> activado = new HashSet<>();
    private final Map<UUID, Long> combate = new HashMap<>();
    private final Map<UUID, Long> ultimoCambio = new HashMap<>();

    Pvp(Core core) {
        this.core = core;
        archivo = new File(core.getDataFolder(), "pvp.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(archivo);
        for (String s : y.getStringList("activado")) {
            try { activado.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) { }
        }
        Bukkit.getScheduler().runTaskTimer(core, this::tick, 20L, 20L);
    }

    // ---------------------------------------------------------------- estado

    private int segundos() { return core.getConfig().getInt("pvp.combate-segundos", 20); }

    boolean tienePvp(Player p) {
        return activado.contains(p.getUniqueId());
    }

    boolean enCombate(Player p) {
        Long h = combate.get(p.getUniqueId());
        return h != null && h > System.currentTimeMillis();
    }

    private void guardar() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> l = new ArrayList<>();
        for (UUID u : activado) l.add(u.toString());
        y.set("activado", l);
        try { y.save(archivo); } catch (IOException e) { core.getLogger().warning("No pude guardar pvp.yml: " + e.getMessage()); }
    }

    private void decir(Player p, String m) {
        p.sendMessage(core.mm(core.getConfig().getString("prefijo", "") + m));
    }

    // ---------------------------------------------------------------- comando

    void comando(Player p, String[] args) {
        if (enCombate(p)) {
            decir(p, "<red>No podés cambiar el PvP mientras estás en combate.");
            return;
        }
        long ahora = System.currentTimeMillis();
        long espera = core.getConfig().getInt("pvp.espera-cambio-segundos", 10) * 1000L;
        Long ult = ultimoCambio.get(p.getUniqueId());
        if (ult != null && ahora - ult < espera) {
            decir(p, "<red>Esperá <white>" + ((espera - (ahora - ult) + 999) / 1000) + "s</white> para volver a cambiar el PvP.");
            return;
        }
        boolean nuevo;
        if (args.length > 0 && (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("activar"))) nuevo = true;
        else if (args.length > 0 && (args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("desactivar"))) nuevo = false;
        else nuevo = !tienePvp(p);
        if (nuevo) activado.add(p.getUniqueId()); else activado.remove(p.getUniqueId());
        ultimoCambio.put(p.getUniqueId(), ahora);
        guardar();
        if (nuevo) {
            decir(p, "<red>⚔ PvP activado<gray>. Ahora podés pelear con otros jugadores que lo tengan activado.");
            p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.5f, 1.4f);
        } else {
            decir(p, "<green>⚔ PvP desactivado<gray>. Nadie puede pegarte ni vos a nadie.");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.4f);
        }
    }

    // ---------------------------------------------------------------- daño

    private Player atacante(Entity e) {
        if (e instanceof Player p) return p;
        if (e instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void alPegar(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victima)) return;
        Player ata = atacante(e.getDamager());
        if (ata == null || ata.equals(victima)) return;
        if (!tienePvp(ata)) {
            e.setCancelled(true);
            ata.sendActionBar(core.mm("<green>⚔ Tenés el PvP desactivado <gray>(/pvp para activarlo)"));
        } else if (!tienePvp(victima)) {
            e.setCancelled(true);
            ata.sendActionBar(core.mm("<green>⚔ " + victima.getName() + " tiene el PvP desactivado"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void etiquetar(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victima)) return;
        Player ata = atacante(e.getDamager());
        if (ata == null || ata.equals(victima)) return;
        long hasta = System.currentTimeMillis() + segundos() * 1000L;
        boolean nuevaA = !enCombate(ata), nuevaV = !enCombate(victima);
        combate.put(ata.getUniqueId(), hasta);
        combate.put(victima.getUniqueId(), hasta);
        if (nuevaA) decir(ata, "<red>⚔ Estás en combate. <gray>Si te desconectás, morís y perdés todo.");
        if (nuevaV) decir(victima, "<red>⚔ Estás en combate. <gray>Si te desconectás, morís y perdés todo.");
    }

    // ---------------------------------------------------------------- combate

    private void tick() {
        long ahora = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Long>> it = combate.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> en = it.next();
            Player p = Bukkit.getPlayer(en.getKey());
            if (en.getValue() <= ahora) {
                it.remove();
                if (p != null) {
                    p.sendActionBar(core.mm("<green>⚔ Saliste de combate. Ya podés desconectarte."));
                }
            } else if (p != null) {
                long s = (en.getValue() - ahora + 999) / 1000;
                p.sendActionBar(core.mm("<red>⚔ En combate: <white>" + s + "s"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alComando(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!enCombate(p) || p.hasPermission("tresyetres.staff")) return;
        String m = e.getMessage().toLowerCase(Locale.ROOT).trim();
        if (m.startsWith("/")) m = m.substring(1);
        int i = m.indexOf(':');
        if (i >= 0 && i < m.indexOf(' ') || (i >= 0 && !m.contains(" "))) m = m.substring(i + 1);
        for (String bloq : core.getConfig().getStringList("pvp.comandos-bloqueados")) {
            String b = bloq.toLowerCase(Locale.ROOT);
            if (m.equals(b) || m.startsWith(b + " ")) {
                e.setCancelled(true);
                decir(p, "<red>No podés usar ese comando en combate.");
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alSalir(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        boolean castigar = enCombate(p);
        combate.remove(p.getUniqueId());
        if (!castigar) return;
        PlayerQuitEvent.QuitReason r = e.getReason();
        if (r == PlayerQuitEvent.QuitReason.KICKED || r == PlayerQuitEvent.QuitReason.ERRONEOUS_STATE) return;
        Bukkit.broadcast(core.mm(core.getConfig().getString("prefijo", "") + "<red>⚔ <white>" + p.getName()
                + "</white> se desconectó en combate y perdió todo."));
        p.setHealth(0.0);
    }

    @EventHandler
    public void alMorir(PlayerDeathEvent e) {
        combate.remove(e.getEntity().getUniqueId());
        Player k = e.getEntity().getKiller();
        if (k != null) combate.remove(k.getUniqueId());
    }

    List<String> sugerencias() { return List.of("on", "off"); }
}
