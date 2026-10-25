package ar.com.tresyetres.core;

import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public final class Core extends JavaPlugin implements Listener, TabCompleter {

    private static final String STAFF = "tresyetres.staff";
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Random random = new Random();
    private BukkitTask tareaAnuncios;
    private int siguienteAnuncio = 0;
    private File archivoReportes;
    private YamlConfiguration reportes;
    private Reglas reglasObligatorias;
    private Rtp rtp;
    private Editor editor;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        archivoReportes = new File(getDataFolder(), "reportes.yml");
        reportes = YamlConfiguration.loadConfiguration(archivoReportes);
        getServer().getPluginManager().registerEvents(this, this);
        reglasObligatorias = new Reglas(this);
        getServer().getPluginManager().registerEvents(reglasObligatorias, this);
        rtp = new Rtp(this);
        getServer().getPluginManager().registerEvents(rtp, this);
        editor = new Editor(this);
        getServer().getPluginManager().registerEvents(editor, this);
        for (String c : List.of("reglas", "report", "reportes", "3y33core", "rtp", "editor")) {
            Objects.requireNonNull(getCommand(c)).setExecutor(this);
            Objects.requireNonNull(getCommand(c)).setTabCompleter(this);
        }
        iniciarAnuncios();
    }

    @Override
    public void onDisable() {
        if (tareaAnuncios != null) tareaAnuncios.cancel();
    }

    private Component txt(String s) {
        return mm.deserialize(s);
    }

    Component mm(String s) {
        return mm.deserialize(s);
    }

    void aviso(CommandSender s, String m) {
        msg(s, m);
    }

    private void msg(CommandSender s, String m) {
        s.sendMessage(txt(getConfig().getString("prefijo", "") + m));
    }

    private void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    // ------------------------------------------------------------------ anuncios

    private void iniciarAnuncios() {
        if (tareaAnuncios != null) tareaAnuncios.cancel();
        if (!getConfig().getBoolean("anuncios.habilitados", true)) return;
        long ticks = Math.max(1, getConfig().getLong("anuncios.intervalo-minutos", 6)) * 60L * 20L;
        tareaAnuncios = Bukkit.getScheduler().runTaskTimer(this, this::anunciar, ticks, ticks);
    }

    private void anunciar() {
        List<String> lista = getConfig().getStringList("anuncios.lista");
        if (lista.isEmpty() || Bukkit.getOnlinePlayers().isEmpty()) return;
        String elegido;
        if (getConfig().getBoolean("anuncios.en-orden", false)) {
            elegido = lista.get(siguienteAnuncio % lista.size());
            siguienteAnuncio++;
        } else {
            elegido = lista.get(random.nextInt(lista.size()));
        }
        Component c = txt(getConfig().getString("anuncios.formato", "<prefijo><mensaje>")
                .replace("<prefijo>", getConfig().getString("prefijo", "")).replace("<mensaje>", elegido));
        boolean sonido = getConfig().getBoolean("anuncios.sonido", false);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(c);
            if (sonido) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, 1.6f);
        }
    }

    // ------------------------------------------------------------------ comandos

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "reglas" -> reglas(sender);
            case "rtp" -> {
                if (sender instanceof Player jp) rtp.usar(jp);
                else msg(sender, "<red>Solo jugadores.");
            }
            case "editor" -> {
                if (sender instanceof Player jp) editor.comando(jp, args);
                else msg(sender, "<red>Solo jugadores.");
            }
            case "report" -> report(sender, args);
            case "reportes" -> verReportes(sender);
            case "3y33core" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                    reloadConfig();
                    iniciarAnuncios();
                    msg(sender, "<green>Configuración recargada.");
                } else if (args.length > 1 && args[0].equalsIgnoreCase("holograma")) {
                    if (!(sender instanceof Player jp)) { msg(sender, "<red>Solo jugadores."); return true; }
                    String creado = Plantillas.crear(this, jp, args[1], args.length > 2 ? args[2] : null);
                    if (creado != null) {
                        msg(sender, "<gray>Holograma <white>" + creado + "</white> creado. Se abre el editor para acomodarlo.");
                        Bukkit.getScheduler().runTaskLater(this, () -> editor.comando(jp, new String[]{creado}), 3L);
                    }
                } else if (args.length > 0 && args[0].equalsIgnoreCase("anuncio")) {
                    anunciar();
                } else if (args.length > 1 && (args[0].equalsIgnoreCase("aceptar") || args[0].equalsIgnoreCase("rechazar"))) {
                    Player objetivo = Bukkit.getPlayerExact(args[1]);
                    if (objetivo == null) { msg(sender, "<red>Ese jugador no está conectado."); return true; }
                    if (args[0].equalsIgnoreCase("aceptar")) reglasObligatorias.aceptar(objetivo);
                    else reglasObligatorias.rechazar(objetivo);
                } else {
                    msg(sender, "<gray>/3y33core reload <dark_gray>·</dark_gray> anuncio <dark_gray>·</dark_gray> holograma <plantilla> [nombre]");
                }
            }
        }
        return true;
    }

    private void reglas(CommandSender sender) {
        if (sender instanceof Player jp && reglasObligatorias.abrir(jp)) return;
        List<Component> paginas = new ArrayList<>();
        for (String p : getConfig().getStringList("reglas.paginas")) paginas.add(txt(p));
        if (sender instanceof Player p) {
            p.openBook(Book.book(txt(getConfig().getString("reglas.titulo", "Reglas")), txt("3y33 SMP"), paginas));
            click(p);
        } else {
            paginas.forEach(sender::sendMessage);
        }
    }

    private void report(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { msg(sender, "<red>Solo jugadores."); return; }
        if (args.length < 2) {
            msg(p, "<gray>Uso: <#FF6B6B>/report <jugador> <motivo>");
            msg(p, "<dark_gray>Ejemplo: /report Steve usa x-ray");
            return;
        }
        Player objetivo = Bukkit.getPlayerExact(args[0]);
        String nombre = objetivo != null ? objetivo.getName() : args[0];
        if (!nombre.matches("[A-Za-z0-9_.*]{1,20}")) { msg(p, "<red>Nombre de jugador inválido."); return; }
        if (nombre.equalsIgnoreCase(p.getName())) { msg(p, "<red>No te podés reportar a vos mismo."); return; }
        if (objetivo == null && Bukkit.getOfflinePlayer(nombre).getFirstPlayed() == 0) {
            msg(p, "<red>Ese jugador nunca entró al server.");
            return;
        }
        long espera = getConfig().getLong("reportes.cooldown-segundos", 60) * 1000L;
        String clave = p.getUniqueId() + ":report";
        long ahora = System.currentTimeMillis();
        Long ultimo = cooldowns.get(clave);
        if (ultimo != null && ahora - ultimo < espera && !p.hasPermission(STAFF)) {
            msg(p, "<red>Esperá <white>" + ((espera - (ahora - ultimo)) / 1000 + 1) + "s</white> para reportar de nuevo.");
            return;
        }
        cooldowns.put(clave, ahora);
        String motivo = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String motivoSeguro = mm.escapeTags(motivo);
        Location l = objetivo != null ? objetivo.getLocation() : p.getLocation();
        String fecha = new SimpleDateFormat("dd/MM HH:mm").format(new Date());

        int n = reportes.getInt("contador", 0) + 1;
        reportes.set("contador", n);
        String base = "lista." + n;
        reportes.set(base + ".fecha", fecha);
        reportes.set(base + ".autor", p.getName());
        reportes.set(base + ".reportado", nombre);
        reportes.set(base + ".motivo", motivo);
        reportes.set(base + ".lugar", l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ());
        guardarReportes();

        msg(p, "<green>Gracias, tu reporte sobre <white>" + nombre + "</white> llegó al staff.");
        click(p);

        Component aviso = txt("<dark_gray>[</dark_gray><red><bold>REPORTE #" + n + "</bold></red><dark_gray>]</dark_gray> <white>" + p.getName()
                + "</white> <gray>reportó a</gray> <#FF6B6B>" + nombre + "</#FF6B6B>\n<dark_gray>» </dark_gray><gray>" + motivoSeguro + "</gray> "
                + (objetivo != null ? "<click:run_command:'/tp " + nombre + "'><hover:show_text:'<gray>Ir hasta " + nombre + "'><#FF6B6B>[Ir]</#FF6B6B></hover></click> " : "")
                + "<click:run_command:'/co lookup u:" + nombre + " t:1h'><hover:show_text:'<gray>Ver lo que hizo en la última hora'><gray>[Historial]</gray></hover></click>");
        int staffOnline = 0;
        for (Player s : Bukkit.getOnlinePlayers()) {
            if (s.hasPermission(STAFF)) {
                s.sendMessage(aviso);
                s.playSound(s.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.0f);
                staffOnline++;
            }
        }
        getLogger().info("Reporte #" + n + ": " + p.getName() + " -> " + nombre + ": " + motivo);
        if (staffOnline == 0) msg(p, "<gray>No hay staff conectado ahora, lo van a ver apenas entren.");
    }

    private void verReportes(CommandSender sender) {
        int total = reportes.getInt("contador", 0);
        if (total == 0) { msg(sender, "<gray>No hay reportes."); return; }
        msg(sender, "<white>Últimos reportes:");
        for (int i = total; i > Math.max(0, total - 10); i--) {
            String b = "lista." + i;
            if (!reportes.contains(b)) continue;
            sender.sendMessage(txt("<dark_gray>#" + i + " " + reportes.getString(b + ".fecha") + "</dark_gray> <white>"
                    + reportes.getString(b + ".autor") + "</white> <dark_gray>»</dark_gray> <#FF6B6B>" + reportes.getString(b + ".reportado")
                    + "</#FF6B6B><gray>: " + mm.escapeTags(String.valueOf(reportes.getString(b + ".motivo"))) + "</gray> <dark_gray>("
                    + reportes.getString(b + ".lugar") + ")"));
        }
    }

    private void guardarReportes() {
        try {
            reportes.save(archivoReportes);
        } catch (IOException e) {
            getLogger().warning("No se pudo guardar reportes.yml: " + e.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        String n = cmd.getName().toLowerCase(Locale.ROOT);
        if (n.equals("report")) {
            if (args.length == 1) {
                List<String> r = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers())
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)) && !p.equals(sender)) r.add(p.getName());
                return r;
            }
            if (args.length == 2) {
                List<String> r = new ArrayList<>();
                for (String m : getConfig().getStringList("reportes.motivos-rapidos"))
                    if (m.startsWith(args[1].toLowerCase(Locale.ROOT))) r.add(m);
                return r;
            }
        }
        if (n.equals("editor") && args.length == 1 && sender.hasPermission(STAFF)) {
            List<String> r = new ArrayList<>();
            for (String s : editor.nombres()) if (s.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) r.add(s);
            return r;
        }
        if (n.equals("3y33core") && args.length == 2 && args[0].equalsIgnoreCase("holograma")) {
            List<String> r = new ArrayList<>();
            for (String s : Plantillas.nombres()) if (s.startsWith(args[1].toLowerCase(Locale.ROOT))) r.add(s);
            return r;
        }
        if (n.equals("3y33core") && args.length == 1) return List.of("reload", "anuncio", "aceptar", "rechazar", "holograma");
        return List.of();
    }

    // ------------------------------------------------------------------ bloqueo y cooldowns

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void alUsarComando(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission(STAFF)) return;
        String[] partes = e.getMessage().substring(1).trim().split("\\s+");
        if (partes.length == 0 || partes[0].isEmpty()) return;
        String base = partes[0].toLowerCase(Locale.ROOT);
        if (base.contains(":")) base = base.substring(base.indexOf(':') + 1);

        if (getConfig().getStringList("comandos-bloqueados").contains(base)) {
            e.setCancelled(true);
            p.sendMessage(txt(getConfig().getString("mensaje-bloqueado", "<red>Ese comando no existe.")));
            return;
        }

        int seg = getConfig().getInt("cooldowns." + base, 0);
        if (seg > 0 && partes.length > 1) {
            String clave = p.getUniqueId() + ":" + base;
            long ahora = System.currentTimeMillis();
            Long ultimo = cooldowns.get(clave);
            if (ultimo != null && ahora - ultimo < seg * 1000L) {
                e.setCancelled(true);
                msg(p, "<red>Esperá <white>" + ((seg * 1000L - (ahora - ultimo)) / 1000 + 1) + "s</white> antes de usar <white>/" + base + "</white> de nuevo.");
                return;
            }
            cooldowns.put(clave, ahora);
        }
    }

    @EventHandler
    public void alEnviarComandos(PlayerCommandSendEvent e) {
        if (e.getPlayer().hasPermission(STAFF)) return;
        List<String> bloqueados = getConfig().getStringList("comandos-bloqueados");
        boolean ocultarPrefijos = getConfig().getBoolean("ocultar-prefijos", true);
        e.getCommands().removeIf(c -> {
            String b = c.toLowerCase(Locale.ROOT);
            if (ocultarPrefijos && b.contains(":")) return true;
            return bloqueados.contains(b);
        });
    }
}
