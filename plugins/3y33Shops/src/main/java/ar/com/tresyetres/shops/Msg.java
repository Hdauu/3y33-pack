package ar.com.tresyetres.shops;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;

import java.text.NumberFormat;
import java.util.Locale;

/** Mensajes y formato de plata. */
final class Msg {
    private Msg() { }

    private static final Component PREFIJO = Component.text("Tiendas ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
            .append(Component.text("» ", NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, false));

    static void ok(CommandSender s, String texto) { s.sendMessage(PREFIJO.append(Component.text(texto, NamedTextColor.GREEN))); }
    static void info(CommandSender s, String texto) { s.sendMessage(PREFIJO.append(Component.text(texto, NamedTextColor.GRAY))); }
    static void error(CommandSender s, String texto) { s.sendMessage(PREFIJO.append(Component.text(texto, NamedTextColor.RED))); }

    /** Plata en centavos a texto: 12345 -> "$123,45". */
    static String dinero(long centavos) {
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "AR"));
        nf.setMaximumFractionDigits(2);
        nf.setMinimumFractionDigits(0);
        return "$" + nf.format(centavos / 100.0);
    }

    static String numero(long n) { return NumberFormat.getIntegerInstance(new Locale("es", "AR")).format(n); }

    /** Precio escrito por el jugador a centavos. "-" o "no" = -1 (desactivado). -2 = no es un numero valido, -3 = supera el maximo. */
    static long precio(String s, double maxPesos) {
        if (s.equals("-") || s.equalsIgnoreCase("no")) return -1;
        try {
            java.math.BigDecimal v = new java.math.BigDecimal(s.replace(',', '.'));
            if (v.signum() < 0) return -2;
            if (v.stripTrailingZeros().scale() > 2) return -2;
            long c = v.setScale(2, java.math.RoundingMode.DOWN).movePointRight(2).longValueExact();
            return c > (long) (maxPesos * 100) ? -3 : c;
        } catch (NumberFormatException | ArithmeticException e) { return -2; }
    }
}
