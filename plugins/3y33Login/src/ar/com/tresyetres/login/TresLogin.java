package ar.com.tresyetres.login;

import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.events.LoginEvent;
import fr.xephi.authme.events.RegisterEvent;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 3y33Login: titulos en pantalla para el login de AuthMe.
 * - Sin loguear: titulo fijo "Inicia sesion" / "Registrate" hasta que se loguea.
 * - Al loguearse: titulo de bienvenida. Si fue automatico (premium o sesion), aviso en el chat.
 */
public final class TresLogin extends JavaPlugin implements Listener {

    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");

    private final Map<UUID, BukkitTask> prompts = new ConcurrentHashMap<>();
    private final Set<UUID> typedAuthCommand = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        for (BukkitTask t : prompts.values()) t.cancel();
        prompts.clear();
    }

    private FileConfiguration cfg() { return getConfig(); }

    private String c(String path, String def) { return color(cfg().getString(path, def)); }

    static String color(String s) {
        if (s == null) return "";
        Matcher m = HEX.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            StringBuilder rep = new StringBuilder("§x");
            for (char ch : m.group(1).toCharArray()) rep.append('§').append(ch);
            m.appendReplacement(sb, Matcher.quoteReplacement(rep.toString()));
        }
        m.appendTail(sb);
        char[] b = sb.toString().toCharArray();
        for (int i = 0; i < b.length - 1; i++) {
            if (b[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(b[i + 1]) > -1) {
                b[i] = '§';
                b[i + 1] = Character.toLowerCase(b[i + 1]);
            }
        }
        return new String(b);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        final UUID id = p.getUniqueId();
        typedAuthCommand.remove(id);
        // Esperar un poco: AuthMe decide en los primeros ticks si entra por premium o sesion.
        getServer().getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            AuthMeApi api = AuthMeApi.getInstance();
            if (api == null || api.isAuthenticated(p)) return;
            startPrompt(p);
        }, cfg().getInt("delay-ticks", 10));
    }

    private void startPrompt(final Player p) {
        stopPrompt(p.getUniqueId());
        final int period = 30;
        BukkitTask task = getServer().getScheduler().runTaskTimer(this, () -> {
            if (!p.isOnline()) { stopPrompt(p.getUniqueId()); return; }
            AuthMeApi api = AuthMeApi.getInstance();
            if (api == null || api.isAuthenticated(p)) { stopPrompt(p.getUniqueId()); return; }
            boolean registered = api.isRegistered(p.getName());
            String title = c("prompt.title", "&c&l3y33 SMP");
            String sub = registered
                    ? c("prompt.login", "&7Inicia sesion: &f/login contrasena")
                    : c("prompt.register", "&7Registrate: &f/register contrasena contrasena");
            p.sendTitle(title, sub, 0, period + 15, 0);
        }, 0L, period);
        prompts.put(p.getUniqueId(), task);
    }

    private void stopPrompt(UUID id) {
        BukkitTask t = prompts.remove(id);
        if (t != null) t.cancel();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage();
        if (msg == null) return;
        String cmd = msg.toLowerCase().split(" ")[0];
        if (cmd.equals("/login") || cmd.equals("/l") || cmd.equals("/log")
                || cmd.equals("/register") || cmd.equals("/reg")) {
            typedAuthCommand.add(e.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(LoginEvent e) {
        final Player p = e.getPlayer();
        if (p == null) return;
        stopPrompt(p.getUniqueId());
        boolean automatic = !typedAuthCommand.remove(p.getUniqueId());
        p.resetTitle();
        p.sendTitle(c("welcome.title", "&c&l3y33 SMP"),
                c("welcome.subtitle", "&7Bienvenido, &f%player%&7!").replace("%player%", p.getName()),
                5, cfg().getInt("welcome.stay-ticks", 50), 15);
        String sound = cfg().getString("welcome.sound", "entity.player.levelup");
        if (sound != null && !sound.isEmpty()) {
            try { p.playSound(p.getLocation(), sound, 0.4f, 1.6f); } catch (Throwable ignored) { }
        }
        if (automatic && cfg().getBoolean("auto-login-message.enabled", true)) {
            p.sendMessage(c("auto-login-message.text", "&4&l3y33 &8> &aEntraste automaticamente.").replace("%player%", p.getName()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRegister(RegisterEvent e) {
        Player p = e.getPlayer();
        if (p != null) stopPrompt(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        stopPrompt(e.getPlayer().getUniqueId());
        typedAuthCommand.remove(e.getPlayer().getUniqueId());
    }
}
