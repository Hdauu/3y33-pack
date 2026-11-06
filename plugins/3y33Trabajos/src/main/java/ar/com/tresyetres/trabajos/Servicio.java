package ar.com.tresyetres.trabajos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Tomar y abandonar un trabajo. */
final class Servicio {
    private final TrabajosPlugin pl;

    Servicio(TrabajosPlugin pl) { this.pl = pl; }

    boolean unirse(Player p, Trabajo t) {
        Perfil f = pl.perfiles().de(p);
        long ahora = System.currentTimeMillis();
        if (f.activo == t) { Msg.info(p, "Ya sos " + t.nombre + "."); return false; }
        if (f.activo != null) {
            Msg.error(p, "Ya tenés un trabajo (" + f.activo.nombre + "). Para cambiar tenés que abandonarlo y esperar " + pl.getConfig().getInt("abandonar-horas", 13) + " horas.");
            return false;
        }
        if (ahora < f.libreHasta) {
            Msg.error(p, "Abandonaste un trabajo hace poco: podés tomar otro en " + Msg.tiempo(f.libreHasta - ahora) + ".");
            return false;
        }
        f.activo = t;
        f.tuvoTrabajo = true;
        f.momentum = 1.0;
        f.momentumT = ahora;
        f.accionesVentana = 0;
        f.ultimoPagoTiempo = 0;
        Msg.aviso(p, Component.text("Ahora sos ", NamedTextColor.GREEN).append(Component.text(t.nombre, t.color)).append(Component.text(". ¡A laburar!", NamedTextColor.GREEN)));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
        pl.perfiles().guardar(f);
        return true;
    }

    boolean abandonar(Player p) {
        Perfil f = pl.perfiles().de(p);
        if (f.activo == null) { Msg.error(p, "No tenés ningún trabajo."); return false; }
        Trabajo t = f.activo;
        pl.misiones().perderPendientes(f, t);
        long horas = pl.getConfig().getLong("abandonar-horas", 13);
        f.activo = null;
        f.libreHasta = System.currentTimeMillis() + horas * 3_600_000L;
        f.accionesVentana = 0;
        Msg.aviso(p, Component.text("Dejaste de ser ", NamedTextColor.GRAY).append(Component.text(t.nombre, t.color))
                .append(Component.text(". Vas a poder tomar otro trabajo en " + horas + " horas.", NamedTextColor.GRAY)));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
        pl.perfiles().guardar(f);
        return true;
    }
}
