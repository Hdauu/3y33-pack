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
        if (c.getName().equalsIgnoreCase("insignia")) { pl.menus().insignias(p); return true; }
        if (a.length == 0) { pl.menus().principal(p); return true; }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("abandonar")) { pl.menus().confirmarAbandono(p, pl.perfiles().de(p).activo == null ? Trabajo.MINERO : pl.perfiles().de(p).activo); return true; }
        if (sub.equals("insignia")) { pl.menus().insignias(p); return true; }
        if (sub.equals("unirse")) {
            Trabajo t = a.length > 1 ? Trabajo.de(a[1]) : null;
            if (t == null) { Msg.error(p, "Uso: /trabajos unirse <minero|leñador|granjero|cazador|pescador>"); return true; }
            pl.servicio().unirse(p, t);
            return true;
        }
        if (sub.equals("recompensas")) {
            Trabajo rt = a.length > 1 ? Trabajo.de(a[1]) : pl.perfiles().de(p).activo;
            if (rt == null) { Msg.error(p, "Uso: /trabajos recompensas <minero|leñador|granjero|cazador|pescador>"); return true; }
            int nodo = -1;
            if (a.length > 2) { try { nodo = Integer.parseInt(a[2]); } catch (NumberFormatException ignorada) { } }
            if (nodo > 0 && pl.perfiles().de(p).de(rt).pend.containsKey(nodo)) pl.menus().elegirRecompensa(p, rt, nodo);
            else pl.menus().recompensas(p, rt);
            return true;
        }
        Trabajo t = Trabajo.de(a[0]);
        if (t != null) { pl.menus().trabajo(p, t); return true; }
        Msg.error(p, "No conozco ese trabajo. Probá /trabajos.");
        return true;
    }

    private boolean admin(CommandSender s, String[] a) {
        if (!s.hasPermission("tresyetres.staff")) { Msg.error(s, "No tenés permiso."); return true; }
        if (a.length == 0) { Msg.info(s, "Uso: /trabajosadmin <reroll|nivel|xp|espera|tomar|quitar|misiones|global|mision|npc|holo|info|balance|ofrecer|insignias|recargar>"); return true; }
        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reroll" -> { pl.rarezas().rerollear(true); Msg.ok(s, "Rarezas renovadas."); }
            case "ofrecer" -> {
                // /trabajosadmin ofrecer <jugador> <trabajo>: deja pendientes las recompensas del camino que ya alcanzo y no tienen (para probar)
                Player op = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                Trabajo ot = a.length > 2 ? Trabajo.de(a[2]) : null;
                if (op == null || ot == null) { Msg.error(s, "Uso: /trabajosadmin ofrecer <jugador> <trabajo>"); return true; }
                Perfil of = pl.perfiles().de(op);
                int n = pl.recompensas().alcanzar(of, ot);
                pl.perfiles().guardar(of);
                Msg.ok(s, "Le dejé " + n + " recompensa(s) del camino pendientes a " + op.getName() + " en " + ot.nombre + ".");
            }
            case "insignias" -> {
                // /trabajosadmin insignias: te muestra en el chat las 20 insignias (las dibuja con el mismo texto que usa el chat)
                if (!(s instanceof Player pp)) { Msg.error(s, "Solo para jugadores."); return true; }
                var mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
                pp.sendMessage(net.kyori.adventure.text.Component.empty());
                for (Trabajo t : Trabajo.values()) {
                    net.kyori.adventure.text.Component l = net.kyori.adventure.text.Component.text(String.format("%-9s", t.nombre), t.color);
                    for (int r = 0; r < Insignias.NOMBRES.length; r++) l = l.append(net.kyori.adventure.text.Component.text(" ")).append(mm.deserialize(Insignias.mini(t, r)));
                    pp.sendMessage(l);
                }
                pp.sendMessage(net.kyori.adventure.text.Component.text("Común · Épica · Legendaria · Mítica (de izquierda a derecha)", net.kyori.adventure.text.format.NamedTextColor.GRAY));
                for (Trabajo t : Trabajo.values()) pp.sendMessage(net.kyori.adventure.text.Component.text("hdau » ", net.kyori.adventure.text.format.NamedTextColor.GRAY).append(mm.deserialize(Insignias.mini(t, 3))).append(net.kyori.adventure.text.Component.text(t.nombre + " mítica", net.kyori.adventure.text.format.NamedTextColor.WHITE)));
            }
            case "balance" -> {
                int horas = 24;
                if (a.length > 1) { try { horas = Math.max(1, Math.min(72, Integer.parseInt(a[1]))); } catch (NumberFormatException ignorada) { } }
                Msg.info(s, "Balance de las últimas " + horas + " h (plata por hora activa de cada trabajo):");
                double totalPlata = 0;
                for (Trabajo t : Trabajo.values()) {
                    double[] v = pl.balance().suma(t, horas);
                    double plata = v[Balance.ACCIONES] + v[Balance.TIEMPO] + v[Balance.MISIONES] + v[Balance.GLOBAL];
                    totalPlata += plata;
                    if (v[Balance.MINUTOS] < 1) { Msg.info(s, " · " + t.nombre + ": sin horas activas (plata creada: $" + Msg.num(plata, 0) + ")"); continue; }
                    double h = v[Balance.MINUTOS] / 60.0;
                    Msg.info(s, " · " + t.nombre + ": " + Msg.num(h, 1) + " h activas · $" + Msg.num(plata / h, 0) + "/h (acciones " + Msg.num(v[Balance.ACCIONES] / h, 0)
                            + " + tiempo " + Msg.num(v[Balance.TIEMPO] / h, 0) + " + misiones " + Msg.num(v[Balance.MISIONES] / h, 0) + " + global " + Msg.num(v[Balance.GLOBAL] / h, 0)
                            + ") · " + Msg.num(v[Balance.XP] / h, 0) + " XP/h");
                }
                Msg.info(s, "Plata creada por los trabajos en total: $" + Msg.num(totalPlata, 0));
            }
            case "recargar" -> {
                pl.reloadConfig();
                pl.catalogo().cargar();
                pl.rarezas().cargar();
                pl.misiones().cargar();
                pl.pago().cargarNiveles();
                pl.recompensas().cargar();
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
            case "tomar" -> {
                // staff: se pone un trabajo al instante, sin espera ni penalizacion. Uso: tomar <trabajo> [jugador]
                Trabajo t = a.length > 1 ? Trabajo.de(a[1]) : null;
                Player p = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : (s instanceof Player pj ? pj : null);
                if (t == null || p == null) { Msg.error(s, "Uso: /trabajosadmin tomar <trabajo> [jugador]"); return true; }
                Perfil f = pl.perfiles().de(p);
                f.activo = null;
                f.libreHasta = 0;
                f.pendiente = 0;
                pl.servicio().unirse(p, t);
                Msg.ok(s, p.getName() + " ahora es " + t.nombre + " (sin espera).");
            }
            case "quitar" -> {
                Player p = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : (s instanceof Player pj ? pj : null);
                if (p == null) { Msg.error(s, "Uso: /trabajosadmin quitar [jugador]"); return true; }
                Perfil f = pl.perfiles().de(p);
                f.activo = null;
                f.libreHasta = 0;
                f.pendiente = 0;
                pl.perfiles().guardar(f);
                Msg.ok(s, p.getName() + " quedó sin trabajo y sin espera.");
            }
            case "npc" -> {
                // /trabajosadmin npc crear <trabajo|general> | quitar: los NPC de misiones (uno por trabajo) y el aldeano general del spawn
                if (!(s instanceof Player pj) || a.length < 2) { Msg.error(s, "Uso: /trabajosadmin npc <crear <trabajo|general>|quitar> (parado donde va el NPC)"); return true; }
                if (a[1].equalsIgnoreCase("crear")) {
                    if (a.length < 3) { Msg.error(s, "Uso: /trabajosadmin npc crear <minero|lenador|granjero|cazador|pescador|general>"); return true; }
                    if (a[2].equalsIgnoreCase("general")) pl.npcs().crearGeneral(pj);
                    else {
                        Trabajo t = Trabajo.de(a[2]);
                        if (t == null) Msg.error(s, "Ese trabajo no existe."); else pl.npcs().crear(pj, t);
                    }
                } else if (a[1].equalsIgnoreCase("quitar")) {
                    int n = pl.npcs().quitar(pj);
                    if (n == 0) Msg.error(s, "No hay ningún NPC de Trabajos a menos de 4 bloques."); else Msg.ok(s, "Saqué " + n + " NPC de Trabajos.");
                } else Msg.error(s, "Uso: /trabajosadmin npc <crear <trabajo|general>|quitar>");
            }
            case "holo" -> {
                // /trabajosadmin holo crear <titulo|panel> <trabajo> | mover [titulo|panel] | quitar [titulo|panel] | lista
                if (!(s instanceof Player pj) || a.length < 2) { Msg.error(s, "Uso: /trabajosadmin holo <crear <titulo|panel> <trabajo>|mover [titulo|panel]|quitar [titulo|panel]|lista>"); return true; }
                switch (a[1].toLowerCase()) {
                    case "crear" -> {
                        Trabajo t = a.length > 3 ? Trabajo.de(a[3]) : null;
                        boolean titulo = a.length > 2 && a[2].equalsIgnoreCase("titulo"), panel = a.length > 2 && a[2].equalsIgnoreCase("panel");
                        if (t == null || (!titulo && !panel)) { Msg.error(s, "Uso: /trabajosadmin holo crear <titulo|panel> <minero|lenador|granjero|cazador|pescador>"); return true; }
                        Msg.ok(s, "Holograma " + pl.npcs().crearHolo(pj, panel, t) + " puesto donde estás. Para moverlo: /trabajosadmin holo mover.");
                    }
                    case "mover", "quitar" -> {
                        Boolean panel = a.length > 2 ? (a[2].equalsIgnoreCase("panel") ? Boolean.TRUE : a[2].equalsIgnoreCase("titulo") ? Boolean.FALSE : null) : null;
                        boolean mover = a[1].equalsIgnoreCase("mover");
                        String r = mover ? pl.npcs().moverHolo(pj, panel) : pl.npcs().quitarHolo(pj, panel);
                        if (r == null) Msg.error(s, "No hay ningún holograma a menos de 12 bloques. Acercate al que querés " + (mover ? "mover" : "sacar") + ".");
                        else Msg.ok(s, (mover ? "Moví el holograma " : "Saqué el holograma ") + r + ".");
                    }
                    case "lista" -> { for (String l : pl.npcs().listaHolos()) Msg.info(s, l); }
                    default -> Msg.error(s, "Uso: /trabajosadmin holo <crear|mover|quitar|lista>");
                }
            }
            case "misiones" -> {
                // /trabajosadmin misiones [jugador]: vuelve a generar las misiones del jugador (para probar)
                Player p = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : (s instanceof Player pj ? pj : null);
                if (p == null) { Msg.error(s, "Uso: /trabajosadmin misiones [jugador]"); return true; }
                Perfil f = pl.perfiles().de(p);
                pl.misiones().reiniciar(f);
                pl.perfiles().guardar(f);
                Msg.ok(s, "Se renovaron las misiones de " + p.getName() + ".");
            }
            case "global" -> {
                // /trabajosadmin global <trabajo> <cantidad>: suma esa cantidad a la mision global (para probar)
                Trabajo t = a.length > 1 ? Trabajo.de(a[1]) : null;
                if (t == null || a.length < 3) { Msg.error(s, "Uso: /trabajosadmin global <trabajo> <cantidad>"); return true; }
                long n;
                try { n = Long.parseLong(a[2]); } catch (NumberFormatException e) { Msg.error(s, "Número inválido."); return true; }
                pl.misiones().sumarGlobal(t, n);
                Msg.ok(s, "Sumé " + n + " a la misión global de " + t.nombre + ".");
            }
            case "mision" -> {
                // /trabajosadmin mision <trabajo> [item]: sin item muestra la mision global actual; con item la fuerza (para probar)
                Trabajo t = a.length > 1 ? Trabajo.de(a[1]) : null;
                if (t == null) { Msg.error(s, "Uso: /trabajosadmin mision <trabajo> [item]"); return true; }
                if (a.length < 3) {
                    Misiones.Global g = pl.misiones().global(t);
                    Msg.info(s, t.nombre + ": " + g.item + " x" + Msg.num(g.objetivo, 0) + " (" + pl.misiones().rarezaDe(t).nombre + "), llevan " + Msg.num(g.total, 0) + ".");
                    return true;
                }
                org.bukkit.Material m = org.bukkit.Material.matchMaterial(a[2]);
                if (m == null || !pl.misiones().fijarMision(t, m)) { Msg.error(s, "Ese item no existe."); return true; }
                Msg.ok(s, "Misión global de " + t.nombre + ": " + pl.misiones().global(t).item + " x" + Msg.num(pl.misiones().global(t).objetivo, 0) + ".");
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
            if (admin) { if (s.hasPermission("tresyetres.staff")) out.addAll(List.of("reroll", "nivel", "xp", "espera", "tomar", "quitar", "misiones", "global", "mision", "npc", "holo", "info", "balance", "ofrecer", "insignias", "recargar")); }
            else {
                out.addAll(List.of("unirse", "abandonar", "recompensas", "insignia"));
                for (Trabajo t : Trabajo.values()) out.add(t.id);
            }
        } else if (a.length == 2 && !admin && (a[0].equalsIgnoreCase("unirse") || a[0].equalsIgnoreCase("recompensas"))) {
            for (Trabajo t : Trabajo.values()) out.add(t.id);
        } else if (admin && s.hasPermission("tresyetres.staff")) {
            if (a.length == 2 && a[0].equalsIgnoreCase("npc")) out.addAll(List.of("crear", "quitar"));
            else if (a.length == 2 && a[0].equalsIgnoreCase("holo")) out.addAll(List.of("crear", "mover", "quitar", "lista"));
            else if (a.length == 3 && a[0].equalsIgnoreCase("holo")) out.addAll(List.of("titulo", "panel"));
            else if (a.length == 4 && a[0].equalsIgnoreCase("holo") && a[1].equalsIgnoreCase("crear")) { for (Trabajo t : Trabajo.values()) out.add(t.id); }
            else if (a.length == 3 && a[0].equalsIgnoreCase("npc") && a[1].equalsIgnoreCase("crear")) out.addAll(List.of("minero", "lenador", "granjero", "cazador", "pescador", "general"));
            else if (a.length == 2 && (a[0].equalsIgnoreCase("tomar") || a[0].equalsIgnoreCase("global") || a[0].equalsIgnoreCase("mision"))) for (Trabajo t : Trabajo.values()) out.add(t.id);
            else if (a.length == 3 && a[0].equalsIgnoreCase("tomar")) for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            else if (a.length == 2) for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            else if (a.length == 3 && (a[0].equalsIgnoreCase("nivel") || a[0].equalsIgnoreCase("xp"))) for (Trabajo t : Trabajo.values()) out.add(t.id);
        }
        String ult = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(ult));
        return out;
    }
}
