package ar.com.tresyetres.trabajos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/** Calcula y paga cada accion: plata por Vault, XP, momentum y subida de nivel. */
final class Pago {
    private final TrabajosPlugin pl;
    private Economy eco;

    Pago(TrabajosPlugin pl) { this.pl = pl; }

    boolean iniciar() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        eco = rsp.getProvider();
        return eco != null;
    }

    private static double nivelBase = 1200, nivelLineal = 80, nivelCuad = 1.5;

    /** Lee la curva de niveles de config.yml (niveles.base / lineal / cuadratico). */
    void cargarNiveles() {
        nivelBase = pl.getConfig().getDouble("niveles.base", 1200);
        nivelLineal = pl.getConfig().getDouble("niveles.lineal", 80);
        nivelCuad = pl.getConfig().getDouble("niveles.cuadratico", 1.5);
        mundos = pl.getConfig().getStringList("mundos").stream().map(m -> m.toLowerCase(Locale.ROOT)).toList();
    }

    private List<String> mundos = List.of();

    /** true si en ese mundo pagan las acciones (los nombres de config.yml valen como prefijo: 3y33SMP incluye el Nether y el End). */
    boolean mundoPaga(World w) {
        if (mundos.isEmpty()) return true;
        String n = w.getName().toLowerCase(Locale.ROOT);
        for (String m : mundos) if (n.startsWith(m)) return true;
        return false;
    }

    /** XP que hace falta para pasar del nivel n al siguiente. */
    static double xpNecesaria(int n) { return nivelBase + nivelLineal * n + nivelCuad * n * n; }

    /** Multiplicador personal: +1% por nivel, +25% por prestigio y hasta +20% por la racha de misiones. */
    double personal(Perfil.Progreso g) { return 1.0 + 0.01 * (g.nivel - 1) + 0.25 * g.prestigio + pl.misiones().bonoRacha(g); }

    /** Momentum de ahora: el guardado menos lo que bajo por estar quieto. */
    double momentum(Perfil f, long ahora) {
        double quieto = pl.getConfig().getDouble("momentum.quieto-segundos", 10) * 1000.0;
        long dt = ahora - f.momentumT;
        if (dt <= quieto) return f.momentum;
        double baja = ((dt - quieto) / 1000.0) * pl.getConfig().getDouble("momentum.baja-por-segundo", 0.01);
        return Math.max(1.0, f.momentum - baja);
    }

    /** Paga una accion hecha por el jugador (el trabajo activo ya se verifico afuera). */
    void accion(Player p, Accion a) {
        if (!mundoPaga(p.getWorld())) return;
        Perfil f = pl.perfiles().de(p);
        if (f.activo != a.trabajo()) return;
        long ahora = System.currentTimeMillis();
        Rareza r = pl.rarezas().de(a);
        Perfil.Progreso g = f.de(a.trabajo());
        if (ahora - f.ultimoMov > pl.getConfig().getLong("afk-segundos", 60) * 1000L) {
            f.momentum = 1.0;
            f.momentumT = ahora;
        }
        double mom = momentum(f, ahora);
        double pesos = a.pago() * (personal(g) + r.bono()) * mom * (1.0 + pl.misiones().bonoGlobal(a.trabajo()));
        double xp = a.xp() * (1.0 + r.bono()) * mom * (1.0 + pl.misiones().bonoGlobal(a.trabajo()));
        f.momentum = Math.min(pl.getConfig().getDouble("momentum.maximo", 3.0), mom + pl.getConfig().getDouble("momentum.por-accion", 0.02));
        f.momentumT = ahora;
        f.accionesVentana++;
        f.ultimaAccion = ahora;
        pl.balance().plata(a.trabajo(), Balance.ACCIONES, pesos);
        if (f.ultimoPagoTiempo == 0) f.ultimoPagoTiempo = ahora;
        depositar(p, f, pesos);
        subirXp(p, f, a.trabajo(), xp);
        p.sendActionBar(Component.text(r.nombre + "  ", r.color)
                .append(Component.text("+" + Msg.dinero(pesos), NamedTextColor.GREEN))
                .append(Component.text("  +" + Msg.num(xp, 2) + " XP", NamedTextColor.AQUA))
                .append(Component.text("  momentum x" + Msg.num(mom, 2), NamedTextColor.GRAY)));
    }

    /** Una vez por minuto: paga el extra por tiempo a quien estuvo trabajando de verdad. */
    void porTiempo() {
        long ahora = System.currentTimeMillis();
        long cada = pl.getConfig().getLong("pago-por-tiempo.cada-minutos", 10) * 60_000L;
        double base = pl.getConfig().getDouble("pago-por-tiempo.pesos", 15);
        long afk = pl.getConfig().getLong("afk-segundos", 60) * 1000L;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Perfil f = pl.perfiles().existente(p);
            if (f != null && f.activo != null && ahora - f.ultimaAccion <= 60_000L) pl.balance().minuto(f.activo);
            if (f == null || f.activo == null || f.accionesVentana <= 0) continue;
            if (ahora - f.ultimoPagoTiempo < cada) continue;
            if (ahora - f.ultimoMov > afk) {          // quieto: no cobra y empieza de cero
                f.accionesVentana = 0;
                f.ultimoPagoTiempo = ahora;
                continue;
            }
            double pesos = base * personal(f.de(f.activo)) * (1.0 + pl.misiones().bonoGlobal(f.activo));
            depositar(p, f, pesos);
            pl.balance().plata(f.activo, Balance.TIEMPO, pesos);
            f.accionesVentana = 0;
            f.ultimoPagoTiempo = ahora;
            Msg.aviso(p, Component.text("Pago por tiempo de " + f.activo.nombre + ": ", NamedTextColor.GRAY)
                    .append(Component.text("+" + Msg.dinero(pesos), NamedTextColor.GREEN)));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
        }
    }

    /** Suma plata al acumulado del jugador; se deposita en Vault cuando junta $1 o cada pocos segundos (no en cada bloque). */
    void depositar(Player p, Perfil f, double pesos) {
        f.pendiente += pesos;
        if (f.pendiente >= 1.0) vaciar(p, f);
    }

    /** Deposita en Vault lo acumulado (en centavos enteros: Vault no maneja fracciones). */
    void vaciar(Player p, Perfil f) {
        long centavos = (long) Math.floor(f.pendiente * 100.0 + 1e-9);
        if (centavos <= 0) return;
        f.pendiente -= centavos / 100.0;
        eco.depositPlayer(p, centavos / 100.0);
    }

    void subirXp(Player p, Perfil f, Trabajo t, double xp) {
        Perfil.Progreso g = f.de(t);
        if (g.nivel >= Perfil.NIVEL_MAX) return;
        pl.balance().xp(t, xp);
        g.xp += xp;
        while (g.nivel < Perfil.NIVEL_MAX && g.xp >= xpNecesaria(g.nivel)) {
            g.xp -= xpNecesaria(g.nivel);
            g.nivel++;
            subioDeNivel(p, t, g);
        }
        if (g.nivel >= Perfil.NIVEL_MAX) g.xp = 0;
    }

    /** Linea de chat con el nivel y el XP que falta, para que se vea siempre como viene. */
    void avisarNivel(Player p, Trabajo t, Perfil.Progreso g) {
        if (g.nivel >= Perfil.NIVEL_MAX) return;
        double need = xpNecesaria(g.nivel);
        Msg.aviso(p, Component.text(t.nombre + " · nivel " + g.nivel + " · ", t.color)
                .append(Component.text(Msg.num(g.xp, 0) + " / " + Msg.num(need, 0) + " XP", NamedTextColor.AQUA))
                .append(Component.text(" (" + Msg.num(g.xp * 100 / need, 0) + "%) · multiplicador x" + Msg.num(personal(g), 2), NamedTextColor.GRAY)));
    }

    private void subioDeNivel(Player p, Trabajo t, Perfil.Progreso g) {
        p.showTitle(Title.title(
                Component.text("¡Nivel " + g.nivel + "!", t.color),
                Component.text(t.nombre + " · multiplicador x" + Msg.num(personal(g), 2), NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1800), Duration.ofMillis(500))));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.0f);
        Msg.aviso(p, Component.text("¡Subiste a nivel " + g.nivel + " de ", NamedTextColor.GREEN)
                .append(Component.text(t.nombre, t.color))
                .append(Component.text("! Multiplicador x" + Msg.num(personal(g), 2) + ". Mirá tu nivel y lo que falta en /trabajos (ícono de la botella de XP).", NamedTextColor.GRAY)));
        if (g.nivel == Perfil.NIVEL_MAX) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            Bukkit.broadcast(Component.text("★ ", NamedTextColor.GOLD)
                    .append(Component.text(p.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" llegó al nivel 100 de ", NamedTextColor.GRAY))
                    .append(Component.text(t.nombre, t.color))
                    .append(Component.text("!", NamedTextColor.GRAY)));
        }
    }
}
