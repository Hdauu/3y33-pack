package ar.com.tresyetres.trabajos;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /trabajos (jugadores) y /trabajosadmin (staff). */
final class Comandos implements CommandExecutor, TabCompleter {
    private final TrabajosPlugin pl;

    Comandos(TrabajosPlugin pl) { this.pl = pl; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (c.getName().equalsIgnoreCase("trabajosadmin")) return admin(s, a);
        if (!(s instanceof Player p)) { Msg.error(s, "Solo para jugadores."); return true; }
        if (a.length == 0) { pl.menus().principal(p); return true; }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("abandonar")) { pl.menus().confirmarAbandono(p, pl.perfiles().de(p).activo == null ? Trabajo.MINERO : pl.perfiles().de(p).activo); return true; }
        if (sub.equals("unirse")) {
            Trabajo t = a.length > 1 ? Trabajo.de(a[1]) : null;
            if (t == null) { Msg.error(p, "Uso: /trabajos unirse <minero|leñador|granjero|cazador|pescador>"); return true; }
            pl.servicio().unirse(p, t);
            return true;
        }
        Trabajo t = Trabajo.de(a[0]);
        if (t != null) { pl.menus().trabajo(p, t); return true; }
        Msg.error(p, "No conozco ese trabajo. Probá /trabajos.");
        return true;
    }

    private boolean admin(CommandSender s, String[] a) {
        if (!s.hasPermission("tresyetres.staff")) { Msg.error(s, "No tenés permiso."); return true; }
        if (a.length == 0) { Msg.info(s, "Uso: /trabajosadmin <reroll|nivel|xp|espera|quitar|info|recargar>"); return true; }
        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reroll" -> { pl.rarezas().rerollear(true); Msg.ok(s, "Rarezas renovadas."); }
            case "recargar" -> {
                pl.reloadConfig();
                pl.catalogo().cargar();
                pl.rarezas().cargar();
                Msg.ok(s, "Configuración y acciones recargadas.");
            }
            case "nivel", "xp" -> {
                if (a.length < 4) { Msg.error(s, "Uso: /trabajosadmin " + sub + " <jugador> <trabajo> <número>"); return true; }
                Player p = Bukkit.getPlayerExact(a[1]);
                Trabajo t = Trabajo.de(a[2]);
                if (p == null || t == null) { Msg.error(s, "Jugador o trabajo inválido."); return true; }
                double n;
                try { n = Double.parseDouble(a[3].replace(',', '.')); } catch (NumberFormatException e) { Msg.error(s, "Número inválido."); return true; }
                Perfil f = pl.perfiles().de(p);
                Perfil.Progreso g = f.de(t);
                if (sub.equals("nivel")) {
                    g.nivel = (int) Math.max(1, Math.min(Perfil.NIVEL_MAX, n));
                    g.xp = 0;
                    Msg.ok(s, p.getName() + " ahora es nivel " + g.nivel + " de " + t.nombre + ".");
                } else {
                    pl.pago().subirXp(p, f, t, n);
                    Msg.ok(s, "Le sumé " + Msg.num(n, 0) + " XP a " + p.getName() + " en " + t.nombre + ".");
                }
                pl.perfiles().guardar(f);
            }
            case "espera" -> {
                Player p = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                if (p == null) { Msg.error(s, "Uso: /trabajosadmin espera <jugador>"); return true; }
                Perfil f = pl.perfiles().de(p);
                f.libreHasta = 0;
                pl.perfiles().guardar(f);
                Msg.ok(s, "Le saqué la espera a " + p.getName() + ".");
            }
            case "quitar" -> {
                Player p = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                if (p == null) { Msg.error(s, "Uso: /trabajosadmin quitar <jugador>"); return true; }
                Perfil f = pl.perfiles().de(p);
                f.activo = null;
                f.libreHasta = 0;
                f.pendiente = 0;
                pl.perfiles().guardar(f);
                Msg.ok(s, p.getName() + " quedó sin trabajo y sin espera.");
            }
            case "info" -> {
                Player p = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : (s instanceof Player pj ? pj : null);
                if (p == null) { Msg.error(s, "Uso: /trabajosadmin info <jugador>"); return true; }
                Perfil f = pl.perfiles().de(p);
                Msg.info(s, p.getName() + ": trabajo " + (f.activo == null ? "ninguno" : f.activo.nombre)
                        + ", momentum x" + Msg.num(pl.pago().momentum(f, System.currentTimeMillis()), 2)
                        + ", espera " + Msg.tiempo(Math.max(0, f.libreHasta - System.currentTimeMillis())));
                for (Trabajo t : Trabajo.values()) {
                    Perfil.Progreso g = f.de(t);
                    Msg.info(s, " · " + t.nombre + ": nivel " + g.nivel + " (" + Msg.num(g.xp, 0) + " XP), prestigio " + g.prestigio);
                }
            }
            default -> Msg.error(s, "Subcomando desconocido.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        boolean admin = c.getName().equalsIgnoreCase("trabajosadmin");
        if (a.length == 1) {
            if (admin) { if (s.hasPermission("tresyetres.staff")) out.addAll(List.of("reroll", "nivel", "xp", "espera", "quitar", "info", "recargar")); }
            else {
                out.addAll(List.of("unirse", "abandonar"));
                for (Trabajo t : Trabajo.values()) out.add(t.id);
            }
        } else if (a.length == 2 && !admin && a[0].equalsIgnoreCase("unirse")) {
            for (Trabajo t : Trabajo.values()) out.add(t.id);
        } else if (admin && s.hasPermission("tresyetres.staff")) {
            if (a.length == 2) for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            else if (a.length == 3 && (a[0].equalsIgnoreCase("nivel") || a[0].equalsIgnoreCase("xp"))) for (Trabajo t : Trabajo.values()) out.add(t.id);
        }
        String ult = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(ult));
        return out;
    }
}
