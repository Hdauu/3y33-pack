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
}
