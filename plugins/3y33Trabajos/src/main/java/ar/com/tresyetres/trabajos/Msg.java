package ar.com.tresyetres.trabajos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;

import java.text.NumberFormat;
import java.util.Locale;

/** Mensajes y formatos. */
final class Msg {
    private Msg() { }

    private static final Locale AR = Locale.forLanguageTag("es-AR");
    private static final Component PREFIJO = Component.text("Trabajos ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
            .append(Component.text("» ", NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, false));

    static void ok(CommandSender s, String texto) { s.sendMessage(PREFIJO.append(Component.text(texto, NamedTextColor.GREEN))); }
    static void info(CommandSender s, String texto) { s.sendMessage(PREFIJO.append(Component.text(texto, NamedTextColor.GRAY))); }
    static void error(CommandSender s, String texto) { s.sendMessage(PREFIJO.append(Component.text(texto, NamedTextColor.RED))); }
    static void aviso(CommandSender s, Component texto) { s.sendMessage(PREFIJO.append(texto)); }

    static String num(double v, int decimales) {
        NumberFormat nf = NumberFormat.getNumberInstance(AR);
        nf.setMinimumFractionDigits(decimales);
        nf.setMaximumFractionDigits(decimales);
        return nf.format(v);
    }

    /** Plata en pesos: los montos chicos (menos de 10 centavos) llevan 4 decimales para que se vea que algo suma. */
    static String dinero(double pesos) { return "$" + (Math.abs(pesos) < 0.1 ? num(pesos, 4) : num(pesos, 2)); }

    /** 3725000 -> "1h 2m 5s". */
    static String tiempo(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, seg = s % 60;
        if (h > 0) return h + "h " + m + "m " + seg + "s";
        if (m > 0) return m + "m " + seg + "s";
        return seg + "s";
    }
}
