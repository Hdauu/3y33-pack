package ar.com.tresyetres.tierras;

import org.ayosynk.landClaimPlugin.LandClaimPlugin;
import org.ayosynk.landClaimPlugin.models.ClaimProfile;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Hermandades: un grupo con nombre, tag y rangos (líder, oficial, miembro).
 * Cada integrante conserva su propio terreno. La hermandad puede tener UN terreno propio (el del líder) al que
 * todos los integrantes entran como miembros del terreno (LandClaim se encarga de los permisos).
 */
final class Hermandades implements CommandExecutor, TabCompleter, Listener {

    static final class Her {
        String nombre;
        String tag;
        UUID lider;
        UUID terreno;                                  // perfil de LandClaim de la hermandad (del líder)
        final Map<UUID, Integer> rango = new LinkedHashMap<>(); // 0 miembro, 1 oficial, 2 líder
        final Set<UUID> sincronizados = new HashSet<>();        // miembros que agregamos nosotros al terreno
    }

    private final Tierras pl;
    private final File archivo;
    private final Map<String, Her> porNombre = new LinkedHashMap<>();
    private final Map<UUID, String> deJugador = new HashMap<>();
    private final Map<UUID, String> invitaciones = new HashMap<>();
    private final Map<UUID, Long> venceInvitacion = new HashMap<>();
    private final Set<UUID> confirmaDisolver = new HashSet<>();

    Hermandades(Tierras pl) {
        this.pl = pl;
        archivo = new File(pl.getDataFolder(), "hermandades.yml");
        cargar();
    }

    // ------------------------------------------------------------- datos

    private void cargar() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(archivo);
        ConfigurationSection s = y.getConfigurationSection("hermandades");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(k);
            if (c == null) continue;
            try {
                Her h = new Her();
                h.nombre = c.getString("nombre", k);
                h.tag = c.getString("tag", "");
                h.lider = UUID.fromString(Objects.requireNonNull(c.getString("lider")));
                String t = c.getString("terreno");
                h.terreno = t == null || t.isBlank() ? null : UUID.fromString(t);
                ConfigurationSection r = c.getConfigurationSection("miembros");
                if (r != null) for (String u : r.getKeys(false)) h.rango.put(UUID.fromString(u), r.getInt(u));
                for (String u : c.getStringList("sincronizados")) h.sincronizados.add(UUID.fromString(u));
                porNombre.put(h.nombre.toLowerCase(Locale.ROOT), h);
                for (UUID u : h.rango.keySet()) deJugador.put(u, h.nombre.toLowerCase(Locale.ROOT));
            } catch (Exception e) {
                pl.getLogger().warning("Hermandad '" + k + "' con datos inválidos: " + e.getMessage());
            }
        }
    }

    private void guardar() {
        YamlConfiguration y = new YamlConfiguration();
        for (Her h : porNombre.values()) {
            String p = "hermandades." + h.nombre.toLowerCase(Locale.ROOT);
            y.set(p + ".nombre", h.nombre);
            y.set(p + ".tag", h.tag);
            y.set(p + ".lider", h.lider.toString());
            y.set(p + ".terreno", h.terreno == null ? "" : h.terreno.toString());
            for (var e : h.rango.entrySet()) y.set(p + ".miembros." + e.getKey(), e.getValue());
            List<String> sinc = new ArrayList<>();
            for (UUID u : h.sincronizados) sinc.add(u.toString());
            y.set(p + ".sincronizados", sinc);
        }
        try { y.save(archivo); } catch (IOException e) { pl.getLogger().warning("No pude guardar hermandades.yml: " + e.getMessage()); }
    }

    Her de(UUID u) {
        String k = deJugador.get(u);
        return k == null ? null : porNombre.get(k);
    }

    // ------------------------------------------------------------- terreno de la hermandad

    private ClaimProfile perfil(Her h) {
        if (h.terreno == null) return null;
        try { return pl.api().getClaimById(h.terreno); } catch (Throwable t) { return null; }
    }

    private String rolPara(ClaimProfile c, boolean oficial) {
        Map<String, ?> roles = c.getRoles();
        if (roles == null || roles.isEmpty()) return "member";
        String comun = null, alto = null;
        for (String k : roles.keySet()) {
            String l = k.toLowerCase(Locale.ROOT);
            if (l.matches(".*(admin|oficial|officer|manager|mod).*")) { if (alto == null) alto = k; }
            else if (comun == null || l.matches(".*(member|miembro|builder|trusted).*")) comun = k;
        }
        if (comun == null) comun = roles.keySet().iterator().next();
        return oficial && alto != null ? alto : comun;
    }

    /** Deja a los integrantes de la hermandad como miembros de su terreno (y saca a los que ya no están). */
    private void sincronizar(Her h) {
        ClaimProfile c = perfil(h);
        if (c == null) return;
        try {
            int base = 8;
            c.setBonusMemberSlots(Math.max(0, h.rango.size() - base));
            Set<UUID> deberian = new HashSet<>();
            for (var e : h.rango.entrySet()) {
                UUID u = e.getKey();
                if (c.isOwner(u)) continue;
                deberian.add(u);
                String rol = rolPara(c, e.getValue() >= 1);
                if (!c.isMember(u) || !rol.equals(c.getMemberRole(u))) c.setMemberRole(u, rol);
                h.sincronizados.add(u);
            }
            for (Iterator<UUID> it = h.sincronizados.iterator(); it.hasNext(); ) {
                UUID u = it.next();
                if (!deberian.contains(u)) {
                    if (c.isMember(u)) c.removeMember(u);
                    it.remove();
                }
            }
            LandClaimPlugin.getInstance().getClaimManager().saveAndSync(c);
        } catch (Throwable t) {
            pl.getLogger().warning("No pude sincronizar el terreno de la hermandad " + h.nombre + ": " + t);
        }
        guardar();
    }

    private void quitarDelTerreno(Her h, UUID u) {
        ClaimProfile c = perfil(h);
        if (c == null) return;
        try {
            if (h.sincronizados.remove(u) && c.isMember(u)) c.removeMember(u);
            LandClaimPlugin.getInstance().getClaimManager().saveAndSync(c);
        } catch (Throwable t) {
            pl.getLogger().warning("No pude sacar a " + u + " del terreno de la hermandad: " + t);
        }
    }

    // ------------------------------------------------------------- comandos

    private static final net.kyori.adventure.text.minimessage.MiniMessage MM = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();

    private void m(CommandSender s, String t) {
        s.sendMessage(MM.deserialize("<dark_red><bold>3y33</bold> <dark_gray>» <reset>" + t));
    }

    private static String esc(String s) { return s.replace("<", "").replace(">", ""); }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (!(sender instanceof Player p)) { m(sender, "<red>Solo jugadores."); return true; }
        String sub = a.length == 0 ? "info" : a[0].toLowerCase(Locale.ROOT);
        Her h = de(p.getUniqueId());
        switch (sub) {
            case "crear" -> crear(p, h, a);
            case "invitar" -> invitar(p, h, a);
            case "aceptar" -> aceptar(p, h);
            case "rechazar" -> { if (invitaciones.remove(p.getUniqueId()) != null) m(p, "<gray>Rechazaste la invitación."); else m(p, "<gray>No tenés invitaciones."); }
            case "salir" -> salir(p, h);
            case "expulsar" -> expulsar(p, h, a);
            case "ascender", "degradar" -> cambiarRango(p, h, a, sub.equals("ascender"));
            case "lider" -> transferir(p, h, a);
            case "tag" -> tag(p, h, a);
            case "terreno" -> terreno(p, h);
            case "disolver" -> disolver(p, h, a);
            case "lista" -> lista(p);
            case "ayuda" -> ayuda(p);
            default -> info(p, a.length > 1 ? porNombre.get(a[1].toLowerCase(Locale.ROOT)) : (sub.equals("info") ? h : porNombre.get(sub)), h);
        }
        return true;
    }

    private void ayuda(Player p) {
        m(p, "<white>Hermandades <dark_gray>»");
        m(p, "<gray>/hermandad crear <nombre> <dark_gray>- <white>creá la tuya");
        m(p, "<gray>/hermandad invitar <jugador> <dark_gray>- <white>sumá gente");
        m(p, "<gray>/hermandad aceptar <dark_gray>| <white>rechazar <dark_gray>| <white>salir");
        m(p, "<gray>/hermandad terreno <dark_gray>- <white>el terreno donde estás pasa a ser de la hermandad");
        m(p, "<gray>/hermandad info [nombre] <dark_gray>| <white>lista");
        m(p, "<gray>Líder: <white>expulsar, ascender, degradar, lider, tag, disolver");
    }

    private void crear(Player p, Her h, String[] a) {
        if (h != null) { m(p, "<red>Ya estás en la hermandad <white>" + esc(h.nombre) + "<red>. Salí primero."); return; }
        if (a.length < 2) { m(p, "<gray>Uso: <white>/hermandad crear <nombre>"); return; }
        String nombre = String.join(" ", Arrays.copyOfRange(a, 1, a.length)).trim();
        if (!nombre.matches("[A-Za-z0-9ÁÉÍÓÚáéíóúÑñ ]{3,20}")) { m(p, "<red>El nombre tiene que tener entre 3 y 20 letras o números."); return; }
        if (porNombre.containsKey(nombre.toLowerCase(Locale.ROOT))) { m(p, "<red>Ese nombre ya existe."); return; }
        Her n = new Her();
        n.nombre = nombre;
        n.tag = nombre.replace(" ", "").substring(0, Math.min(4, nombre.replace(" ", "").length())).toUpperCase(Locale.ROOT);
        n.lider = p.getUniqueId();
        n.rango.put(p.getUniqueId(), 2);
        porNombre.put(nombre.toLowerCase(Locale.ROOT), n);
        deJugador.put(p.getUniqueId(), nombre.toLowerCase(Locale.ROOT));
        guardar();
        m(p, "<white>Creaste la hermandad <red>" + esc(nombre) + "<white>. Tag: <red>" + n.tag + "<white>. Invitá gente con <red>/hermandad invitar <jugador><white>.");
    }

    private void invitar(Player p, Her h, String[] a) {
        if (h == null) { m(p, "<red>No estás en ninguna hermandad."); return; }
        if (h.rango.get(p.getUniqueId()) < 1) { m(p, "<red>Solo el líder y los oficiales invitan."); return; }
        if (a.length < 2) { m(p, "<gray>Uso: <white>/hermandad invitar <jugador>"); return; }
        int max = pl.getConfig().getInt("hermandades.max-miembros", 12);
        if (h.rango.size() >= max) { m(p, "<red>La hermandad está llena (" + max + ")."); return; }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) { m(p, "<red>Ese jugador no está conectado."); return; }
        if (de(t.getUniqueId()) != null) { m(p, "<red>Ese jugador ya está en una hermandad."); return; }
        invitaciones.put(t.getUniqueId(), h.nombre.toLowerCase(Locale.ROOT));
        venceInvitacion.put(t.getUniqueId(), System.currentTimeMillis() + 120_000L);
        m(p, "<gray>Invitaste a <white>" + esc(t.getName()) + "<gray>.");
        m(t, "<white>" + esc(p.getName()) + " <gray>te invitó a la hermandad <red>" + esc(h.nombre) + "<gray>. Escribí <white>/hermandad aceptar <gray>o <white>/hermandad rechazar<gray> (vence en 2 minutos).");
    }

    private void aceptar(Player p, Her actual) {
        String k = invitaciones.get(p.getUniqueId());
        Long vence = venceInvitacion.get(p.getUniqueId());
        if (k == null || vence == null || vence < System.currentTimeMillis()) { invitaciones.remove(p.getUniqueId()); m(p, "<gray>No tenés invitaciones vigentes."); return; }
        if (actual != null) { m(p, "<red>Ya estás en una hermandad."); return; }
        Her h = porNombre.get(k);
        invitaciones.remove(p.getUniqueId());
        if (h == null) { m(p, "<red>Esa hermandad ya no existe."); return; }
        h.rango.put(p.getUniqueId(), 0);
        deJugador.put(p.getUniqueId(), k);
        guardar();
        sincronizar(h);
        aviso(h, "<white>" + esc(p.getName()) + " <gray>se unió a la hermandad.");
    }

    private void salir(Player p, Her h) {
        if (h == null) { m(p, "<red>No estás en ninguna hermandad."); return; }
        if (h.lider.equals(p.getUniqueId())) { m(p, "<red>Sos el líder: pasale el liderazgo a otro (<white>/hermandad lider <jugador><red>) o disolvela."); return; }
        quitar(h, p.getUniqueId());
        m(p, "<gray>Saliste de la hermandad <white>" + esc(h.nombre) + "<gray>.");
        aviso(h, "<white>" + esc(p.getName()) + " <gray>salió de la hermandad.");
    }

    private void quitar(Her h, UUID u) {
        quitarDelTerreno(h, u);
        h.rango.remove(u);
        deJugador.remove(u);
        guardar();
    }

    private void expulsar(Player p, Her h, String[] a) {
        if (h == null || h.rango.get(p.getUniqueId()) < 1) { m(p, "<red>Solo el líder y los oficiales pueden expulsar."); return; }
        if (a.length < 2) { m(p, "<gray>Uso: <white>/hermandad expulsar <jugador>"); return; }
        OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(a[1]);
        if (t == null || !h.rango.containsKey(t.getUniqueId())) { m(p, "<red>Ese jugador no está en tu hermandad."); return; }
        if (h.rango.get(t.getUniqueId()) >= h.rango.get(p.getUniqueId())) { m(p, "<red>No podés expulsar a alguien de igual o mayor rango."); return; }
        quitar(h, t.getUniqueId());
        aviso(h, "<white>" + esc(a[1]) + " <gray>fue expulsado de la hermandad.");
        Player on = t.getPlayer();
        if (on != null) m(on, "<red>Te expulsaron de la hermandad <white>" + esc(h.nombre) + "<red>.");
    }

    private void cambiarRango(Player p, Her h, String[] a, boolean sube) {
        if (h == null || !h.lider.equals(p.getUniqueId())) { m(p, "<red>Solo el líder cambia rangos."); return; }
        if (a.length < 2) { m(p, "<gray>Uso: <white>/hermandad " + (sube ? "ascender" : "degradar") + " <jugador>"); return; }
        OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(a[1]);
        if (t == null || !h.rango.containsKey(t.getUniqueId()) || t.getUniqueId().equals(h.lider)) { m(p, "<red>Ese jugador no está en tu hermandad (o es el líder)."); return; }
        h.rango.put(t.getUniqueId(), sube ? 1 : 0);
        guardar();
        sincronizar(h);
        aviso(h, "<white>" + esc(a[1]) + " <gray>ahora es <white>" + (sube ? "oficial" : "miembro") + "<gray>.");
    }

    private void transferir(Player p, Her h, String[] a) {
        if (h == null || !h.lider.equals(p.getUniqueId())) { m(p, "<red>Solo el líder puede pasar el liderazgo."); return; }
        if (a.length < 2) { m(p, "<gray>Uso: <white>/hermandad lider <jugador>"); return; }
        OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(a[1]);
        if (t == null || !h.rango.containsKey(t.getUniqueId()) || t.getUniqueId().equals(h.lider)) { m(p, "<red>Ese jugador no está en tu hermandad."); return; }
        h.rango.put(h.lider, 1);
        h.lider = t.getUniqueId();
        h.rango.put(h.lider, 2);
        h.terreno = null;   // el terreno de la hermandad era del líder anterior: el nuevo líder elige el suyo
        h.sincronizados.clear();
        guardar();
        aviso(h, "<white>" + esc(a[1]) + " <gray>es el nuevo líder. Elegí el terreno de la hermandad con <white>/hermandad terreno<gray>.");
    }

    private void tag(Player p, Her h, String[] a) {
        if (h == null || !h.lider.equals(p.getUniqueId())) { m(p, "<red>Solo el líder cambia el tag."); return; }
        if (a.length < 2 || !a[1].matches("[A-Za-z0-9]{2,4}")) { m(p, "<gray>Uso: <white>/hermandad tag <2 a 4 letras o números>"); return; }
        h.tag = a[1].toUpperCase(Locale.ROOT);
        guardar();
        aviso(h, "<gray>El tag de la hermandad ahora es <red>" + h.tag + "<gray>.");
    }

    private void terreno(Player p, Her h) {
        if (h == null || !h.lider.equals(p.getUniqueId())) { m(p, "<red>Solo el líder elige el terreno de la hermandad."); return; }
        ClaimProfile c = pl.api().getClaimAt(p.getLocation());
        if (c == null || !c.isOwner(p.getUniqueId())) { m(p, "<red>Pará dentro de uno de tus terrenos para elegirlo."); return; }
        ClaimProfile viejo = perfil(h);
        if (viejo != null && !viejo.getProfileId().equals(c.getProfileId())) {
            for (UUID u : new ArrayList<>(h.sincronizados)) quitarDelTerreno(h, u);
        }
        h.terreno = c.getProfileId();
        guardar();
        sincronizar(h);
        aviso(h, "<gray>El terreno <white>" + esc(c.getName()) + "<gray> ahora es de la hermandad: todos entran como miembros.");
    }

    private void disolver(Player p, Her h, String[] a) {
        if (h == null || !h.lider.equals(p.getUniqueId())) { m(p, "<red>Solo el líder puede disolver la hermandad."); return; }
        if (a.length < 2 || !a[1].equalsIgnoreCase("confirmar") || !confirmaDisolver.contains(p.getUniqueId())) {
            confirmaDisolver.add(p.getUniqueId());
            m(p, "<red>Esto borra la hermandad y saca a todos de su terreno. Si estás seguro: <white>/hermandad disolver confirmar");
            return;
        }
        confirmaDisolver.remove(p.getUniqueId());
        for (UUID u : new ArrayList<>(h.rango.keySet())) { quitarDelTerreno(h, u); deJugador.remove(u); }
        porNombre.remove(h.nombre.toLowerCase(Locale.ROOT));
        guardar();
        m(p, "<gray>Disolviste la hermandad <white>" + esc(h.nombre) + "<gray>.");
    }

    private void lista(Player p) {
        if (porNombre.isEmpty()) { m(p, "<gray>Todavía no hay hermandades."); return; }
        m(p, "<white>Hermandades <dark_gray>»");
        for (Her h : porNombre.values())
            m(p, "<red>" + h.tag + " <dark_gray>- <white>" + esc(h.nombre) + " <gray>(" + h.rango.size() + ")");
    }

    private void info(Player p, Her ver, Her mia) {
        Her h = ver != null ? ver : mia;
        if (h == null) { m(p, "<gray>No estás en ninguna hermandad. Creá una con <white>/hermandad crear <nombre><gray> o mirá <white>/hermandad ayuda<gray>."); return; }
        m(p, "<red>" + h.tag + " <dark_gray>» <white>" + esc(h.nombre));
        StringBuilder sb = new StringBuilder();
        for (var e : h.rango.entrySet()) {
            OfflinePlayer o = Bukkit.getOfflinePlayer(e.getKey());
            String n = o.getName() == null ? "?" : o.getName();
            String c = e.getValue() == 2 ? "<gold>" : e.getValue() == 1 ? "<yellow>" : "<white>";
            if (sb.length() > 0) sb.append("<dark_gray>, ");
            sb.append(c).append(esc(n));
        }
        m(p, "<gray>Integrantes (" + h.rango.size() + "): " + sb);
        ClaimProfile c = perfil(h);
        m(p, "<gray>Terreno: <white>" + (c == null ? "sin elegir" : esc(c.getName())));
    }

    private void aviso(Her h, String texto) {
        for (UUID u : h.rango.keySet()) {
            Player on = Bukkit.getPlayer(u);
            if (on != null) m(on, texto);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) {
            List<String> r = new ArrayList<>();
            for (String x : List.of("crear", "invitar", "aceptar", "rechazar", "salir", "expulsar", "ascender", "degradar", "lider", "tag", "terreno", "disolver", "info", "lista", "ayuda"))
                if (x.startsWith(a[0].toLowerCase(Locale.ROOT))) r.add(x);
            return r;
        }
        if (a.length == 2 && List.of("invitar", "expulsar", "ascender", "degradar", "lider").contains(a[0].toLowerCase(Locale.ROOT))) return null;
        return List.of();
    }
}
