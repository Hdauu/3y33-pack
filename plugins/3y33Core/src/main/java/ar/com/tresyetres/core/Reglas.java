package ar.com.tresyetres.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;

/**
 * Reglas obligatorias con FancyDialogs: el jugador nuevo (o cuando cambia la versión de las reglas)
 * no puede moverse, hablar ni usar comandos hasta aceptar.
 */
final class Reglas implements Listener {

    private static final Set<String> COMANDOS_PERMITIDOS = Set.of(
            "reglas", "rules", "normas", "login", "l", "log", "register", "reg", "premium", "logout", "3y33core");

    private final Core core;
    private final File archivo;
    private final YamlConfiguration aceptadas;
    private final Map<UUID, Long> ultimaApertura = new HashMap<>();

    Reglas(Core core) {
        this.core = core;
        this.archivo = new File(core.getDataFolder(), "reglas-aceptadas.yml");
        this.aceptadas = YamlConfiguration.loadConfiguration(archivo);
        Bukkit.getScheduler().runTaskTimer(core, this::revisar, 40L, 40L);
    }

    private boolean habilitado() {
        return core.getConfig().getBoolean("reglas-dialogo.habilitado", true)
                && Bukkit.getPluginManager().isPluginEnabled("FancyDialogs");
    }

    private int version() {
        return core.getConfig().getInt("reglas-dialogo.version", 1);
    }

    boolean acepto(Player p) {
        return aceptadas.getInt(p.getUniqueId().toString(), 0) >= version();
    }

    private boolean pendiente(Player p) {
        return habilitado() && !acepto(p) && !p.hasPermission("tresyetres.staff") && autenticado(p) && !esBedrock(p);
    }

    /** /reglas: abre el diálogo (Java) o el libro (Bedrock o sin FancyDialogs). Devuelve false si hay que usar el libro. */
    boolean abrir(Player p) {
        if (!habilitado() || esBedrock(p)) return false;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "dialog open " + core.getConfig().getString("reglas-dialogo.dialogo", "reglas_inicio") + " " + p.getName());
        ultimaApertura.put(p.getUniqueId(), System.currentTimeMillis());
        return true;
    }

    void aceptar(Player p) {
        boolean nuevo = !acepto(p);
        aceptadas.set(p.getUniqueId().toString(), version());
        guardar();
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
        if (nuevo) {
            p.showTitle(Title.title(core.mm("<#E03131><bold>¡Bienvenido!</bold></#E03131>"),
                    core.mm("<gray>Ya podés jugar. Todo está en <#FF6B6B>/menu</#FF6B6B>"),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(3000), Duration.ofMillis(800))));
        } else {
            core.aviso(p, "<gray>Gracias por repasar las reglas.");
        }
    }

    void rechazar(Player p) {
        if (acepto(p) || p.hasPermission("tresyetres.staff")) {
            core.aviso(p, "<gray>Ya habías aceptado las reglas antes; no cambia nada.");
            return;
        }
        p.kick(core.mm(core.getConfig().getString("reglas-dialogo.mensaje-rechazo",
                "<#E03131><bold>3y33 SMP</bold></#E03131>\n\n<white>Para jugar acá tenés que aceptar las reglas.\n<gray>Cuando quieras, volvé y aceptalas.")));
    }

    private void revisar() {
        long ahora = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!pendiente(p)) continue;
            Long ultima = ultimaApertura.get(p.getUniqueId());
            if (ultima == null) abrir(p);
            else if (ahora - ultima > 2000) p.sendActionBar(core.mm("<#FF6B6B>Leé y aceptá las reglas para empezar a jugar"));
        }
    }

    private void reabrirSiHaceFalta(Player p) {
        Long ultima = ultimaApertura.get(p.getUniqueId());
        if (ultima == null || System.currentTimeMillis() - ultima > 4000) abrir(p);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void alMoverse(PlayerMoveEvent e) {
        if (!e.hasChangedBlock() || !pendiente(e.getPlayer())) return;
        e.setCancelled(true);
        reabrirSiHaceFalta(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void alHablar(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!pendiente(p)) return;
        e.setCancelled(true);
        Bukkit.getScheduler().runTask(core, () -> reabrirSiHaceFalta(p));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void alUsarComando(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!pendiente(p)) return;
        String base = e.getMessage().substring(1).split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (base.contains(":")) base = base.substring(base.indexOf(':') + 1);
        if (COMANDOS_PERMITIDOS.contains(base)) return;
        e.setCancelled(true);
        reabrirSiHaceFalta(p);
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent e) {
        ultimaApertura.remove(e.getPlayer().getUniqueId());
    }

    private void guardar() {
        try {
            aceptadas.save(archivo);
        } catch (IOException ex) {
            core.getLogger().warning("No se pudo guardar reglas-aceptadas.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ integraciones por reflexión

    private boolean autenticado(Player p) {
        Plugin authme = Bukkit.getPluginManager().getPlugin("AuthMe");
        if (authme == null || !authme.isEnabled()) return true;
        try {
            Class<?> api = Class.forName("fr.xephi.authme.api.v3.AuthMeApi", true, authme.getClass().getClassLoader());
            Object inst = api.getMethod("getInstance").invoke(null);
            Method m = api.getMethod("isAuthenticated", Player.class);
            return (boolean) m.invoke(inst, p);
        } catch (Throwable t) {
            return true;
        }
    }

    private boolean esBedrock(Player p) {
        Plugin fg = Bukkit.getPluginManager().getPlugin("floodgate");
        if (fg == null || !fg.isEnabled()) return false;
        try {
            Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi", true, fg.getClass().getClassLoader());
            Object inst = api.getMethod("getInstance").invoke(null);
            return (boolean) api.getMethod("isFloodgatePlayer", UUID.class).invoke(inst, p.getUniqueId());
        } catch (Throwable t) {
            return false;
        }
    }
}
