package ar.com.tresyetres.trabajos;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/** Insignias de chat: marco por rareza (nivel 30, 65, 100, 130) + emblema del trabajo, dibujadas con la fuente insignias:chat del pack. */
final class Insignias {
    static final int[] NIVELES = {30, 65, 100, 130};
    static final String[] NOMBRES = {"Común", "Épica", "Legendaria", "Mítica"};
    static final int[] RGB = {0x3FAE49, 0x8E3FE0, 0xF2B01E, 0xC42A55};

    /** Que insignia se esta mostrando: de que trabajo y de que rareza (0..3). */
    record Sel(Trabajo t, int tier) { }

    /** Rareza que corresponde a un nivel: -1 si todavia no llega a la primera. */
    static int tier(int nivel) {
        int r = -1;
        for (int i = 0; i < NIVELES.length; i++) if (nivel >= NIVELES[i]) r = i;
        return r;
    }

    static TextColor color(int tier) { return TextColor.color(RGB[tier]); }

    /** Los 4 caracteres: marco, espacio negativo, emblema, espacio (en la fuente insignias:chat). */
    static String cadena(Trabajo t, int tier) {
        return "" + (char) (0xE100 + tier) + '' + (char) (0xE110 + t.ordinal() * 4 + tier) + '';
    }

    /** La insignia como texto de Adventure (tiene que ir en BLANCO para que no se tiña). */
    static Component componente(Trabajo t, int tier) {
        return Component.text(cadena(t, tier)).font(Key.key("insignias", "chat")).color(NamedTextColor.WHITE).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    /** Insignia + espacio, en un contenedor aparte: si no, lo que se le agregue después hereda la fuente insignias:chat y sale como cuadraditos. */
    static Component conEspacio(Trabajo t, int tier) { return Component.empty().append(componente(t, tier)).append(Component.text(" ")); }

    /** Version en MiniMessage para el chat (placeholder %trabajos_insignia%), con la rareza al pasar el mouse. Vacio si no tiene. */
    String mini(Perfil f) {
        Sel s = mostrada(f);
        if (s == null) return "";
        String hex = String.format("#%06X", RGB[s.tier()]);
        return "<hover:show_text:'<" + hex + ">" + s.t().nombre + " · " + NOMBRES[s.tier()] + "'><font:insignias:chat><white>" + cadena(s.t(), s.tier()) + "</white></font></hover> ";
    }

    /** La que se muestra: la elegida si ya la tiene; si no (automatica), la de mayor rareza y, a igual rareza, la de mayor nivel. */
    Sel mostrada(Perfil f) {
        String e = f.insignia;
        if ("ninguna".equals(e)) return null;
        Trabajo elegido = Trabajo.de(e);
        if (elegido != null) {
            int ti = tier(f.de(elegido).nivel);
            if (ti >= 0) return new Sel(elegido, ti);
        }
        Sel mejor = null;
        for (Trabajo t : Trabajo.values()) {
            int nv = f.de(t).nivel, ti = tier(nv);
            if (ti < 0) continue;
            if (mejor == null || ti > mejor.tier() || (ti == mejor.tier() && nv > f.de(mejor.t()).nivel)) mejor = new Sel(t, ti);
        }
        return mejor;
    }
}
