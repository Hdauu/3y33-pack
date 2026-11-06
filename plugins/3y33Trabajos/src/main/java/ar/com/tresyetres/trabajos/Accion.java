package ar.com.tresyetres.trabajos;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/** Una cosa que paga: romper un bloque, matar un mob, pescar o criar. */
record Accion(String id, Trabajo trabajo, Tipo tipo, String clave, double pago, double xp, Material icono) {
    enum Tipo { BLOQUE, MOB, PESCA, CRIA }

    /** Nombre para mostrar; los bloques y mobs usan la traduccion del cliente. */
    Component nombre() {
        try {
            switch (tipo) {
                case BLOQUE: return Component.text("Romper ").append(Component.translatable(Material.valueOf(clave).translationKey()));
                case MOB: return Component.text("Matar ").append(Component.translatable(EntityType.valueOf(clave).translationKey()));
                case PESCA:
                    return Component.text(switch (clave) {
                        case "PESCADO" -> "Pescar un pez";
                        case "TROPICAL" -> "Pescar un pez tropical";
                        case "TESORO" -> "Pescar un tesoro";
                        default -> "Pescar basura";
                    });
                default: return Component.text("Criar animales");
            }
        } catch (IllegalArgumentException e) {
            return Component.text(clave);
        }
    }
}
