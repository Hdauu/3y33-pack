package ar.com.tresyetres.tiendas;

import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.shop.ControlComponent;
import com.ghostchu.quickshop.api.shop.Shop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

/** Linea "Holograma" dentro del panel de control de la tienda: mueve el item + texto de ESA tienda. */
@SuppressWarnings({"rawtypes", "unchecked"})
final class PanelHolograma implements ControlComponent {
    static final String ID = "holograma";

    @Override
    public String identifier() { return ID; }

    @Override
    public boolean applies(QuickShopAPI api, Player player, Shop shop) {
        if (player.hasPermission("tresyetres.staff")) return true;
        try { return shop.getOwner().getUniqueIdIfRealPlayer().map(u -> u.equals(player.getUniqueId())).orElse(false); } catch (Throwable t) { return false; }
    }

    @Override
    public Component generate(QuickShopAPI api, Player player, Shop shop) {
        long id = shop.getShopId();
        Component c = Component.text("Holograma: ", NamedTextColor.GRAY);
        c = c.append(boton("◀", "izquierda", id)).append(Component.text(" "))
                .append(boton("▶", "derecha", id)).append(Component.text(" "))
                .append(boton("▲ adelante", "adelante", id)).append(Component.text(" "))
                .append(boton("▼ atrás", "atras", id)).append(Component.text("  "))
                .append(boton("subir", "subir", id)).append(Component.text(" "))
                .append(boton("bajar", "bajar", id)).append(Component.text(" "))
                .append(boton("reiniciar", "reiniciar", id));
        return c;
    }

    private static Component boton(String texto, String accion, long id) {
        return Component.text("[" + texto + "]", NamedTextColor.RED, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/holo " + accion + " 1 " + id))
                .hoverEvent(HoverEvent.showText(Component.text("Mover el holograma: " + accion)));
    }
}
