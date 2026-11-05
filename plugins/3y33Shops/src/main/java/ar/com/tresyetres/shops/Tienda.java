package ar.com.tresyetres.shops;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Una tienda: un contenedor (o doble cofre) con un solo item a la venta / compra. */
final class Tienda {
    long id;
    String mundo;
    int x, y, z;
    boolean doble;
    int x2, y2, z2;
    String tierra = "";
    UUID dueno;
    ItemStack item;
    /** Lo que paga el jugador para COMPRAR 1 item (centavos). -1 = no se puede comprar. */
    long compra = -1;
    /** Lo que recibe el jugador al VENDER 1 item (centavos). -1 = no se puede vender. */
    long venta = -1;
    boolean bloqCompra, bloqVenta;
    String modoIngreso = "DUENO";
    int bajoStock = 10;
    String mensaje = "";
    String rotacion = "SUR";
    long creada;
    final Set<UUID> admins = new HashSet<>();

    World mundoObj() { return Bukkit.getWorld(mundo); }

    Block bloque() {
        World w = mundoObj();
        return w == null ? null : w.getBlockAt(x, y, z);
    }

    Location ubicacion() { return new Location(mundoObj(), x + 0.5, y + 0.5, z + 0.5); }

    boolean puedeComprar() { return compra >= 0 && !bloqCompra; }
    boolean puedeVender() { return venta >= 0 && !bloqVenta; }

    /** Dueño, administradores de la tienda y staff. */
    boolean administra(Player p) {
        return p.getUniqueId().equals(dueno) || admins.contains(p.getUniqueId()) || p.hasPermission("tresyetres.staff");
    }

    boolean esDeLosDuenos(Player p) { return p.getUniqueId().equals(dueno) || admins.contains(p.getUniqueId()); }
}
