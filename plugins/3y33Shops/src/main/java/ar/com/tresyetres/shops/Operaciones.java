package ar.com.tresyetres.shops;

import org.bukkit.OfflinePlayer;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;

/**
 * Compras y ventas. Todo corre en el hilo principal, de una sola vez: se verifica todo antes, se mueve la plata y los items
 * y, si algo falla en el medio, se revierte lo que ya se hizo.
 */
final class Operaciones {
    record Resultado(boolean ok, String mensaje, long totalCentavos) {
        static Resultado error(String m) { return new Resultado(false, m, 0); }
    }

    private final ShopsPlugin pl;

    Operaciones(ShopsPlugin pl) { this.pl = pl; }

    // ---------- utilidades de inventario ----------

    /** Solo se permiten tiendas en barril, cofre y cofre doble (el cofre trampa emite redstone). */
    static boolean contenedorPermitido(org.bukkit.Material m) { return m == org.bukkit.Material.CHEST || m == org.bukkit.Material.BARREL; }

    /** El inventario real del contenedor de la tienda (null si el cofre ya no esta). */
    static Inventory inventario(Tienda t) {
        Block b = t.bloque();
        if (b == null) return null;
        if (b.getState(false) instanceof Container c) return c.getInventory();     // sin copia de los items: se llama muy seguido
        return null;
    }

    static int contar(Inventory inv, ItemStack modelo) {
        int n = 0;
        for (ItemStack it : inv.getStorageContents()) if (it != null && it.isSimilar(modelo)) n += it.getAmount();
        return n;
    }

    /** Cuantas unidades de {@code modelo} entran todavia en ese inventario. */
    static int capacidad(Inventory inv, ItemStack modelo) {
        int max = modelo.getMaxStackSize();
        int lugar = 0;
        for (ItemStack it : inv.getStorageContents()) {
            if (it == null || it.getType().isAir()) lugar += max;
            else if (it.isSimilar(modelo)) lugar += Math.max(0, max - it.getAmount());
        }
        return lugar;
    }

    private static ItemStack cantidad(ItemStack modelo, int n) {
        ItemStack c = modelo.clone();
        c.setAmount(n);
        return c;
    }

    private static boolean quitar(Inventory inv, ItemStack modelo, int n) {
        Map<Integer, ItemStack> sobra = inv.removeItem(cantidad(modelo, n));
        if (sobra.isEmpty()) return true;
        int faltan = sobra.values().stream().mapToInt(ItemStack::getAmount).sum();
        int quitados = n - faltan;
        if (quitados > 0) inv.addItem(cantidad(modelo, quitados));
        return false;
    }

    private static boolean poner(Inventory inv, ItemStack modelo, int n) {
        Map<Integer, ItemStack> sobra = inv.addItem(cantidad(modelo, n));
        if (sobra.isEmpty()) return true;
        int faltan = sobra.values().stream().mapToInt(ItemStack::getAmount).sum();
        int puestos = n - faltan;
        if (puestos > 0) inv.removeItem(cantidad(modelo, puestos));
        return false;
    }

    // ---------- cuanto se puede operar ----------

    int maxComprar(Player p, Tienda t) {
        if (!t.puedeComprar()) return 0;
        Inventory shop = inventario(t);
        if (shop == null) return 0;
        long max = contar(shop, t.item);
        max = Math.min(max, capacidad(p.getInventory(), t.item));
        if (t.compra > 0) max = Math.min(max, pl.economia().saldo(p) / t.compra);
        return (int) Math.max(0, max);
    }

    int maxVender(Player p, Tienda t) {
        if (!t.puedeVender()) return 0;
        Inventory shop = inventario(t);
        if (shop == null) return 0;
        long max = contar(p.getInventory(), t.item);
        max = Math.min(max, capacidad(shop, t.item));
        if (t.venta > 0) max = Math.min(max, pl.economia().saldo(Bukkit.getOfflinePlayer(t.dueno)) / t.venta);
        return (int) Math.max(0, max);
    }

    // ---------- operaciones ----------

    /** El jugador COMPRA n items de la tienda. */
    Resultado comprar(Player p, Tienda t, int n) {
        if (n <= 0) return Resultado.error("La cantidad tiene que ser mayor a cero.");
        String sus = pl.integridad().motivo(t);
        if (sus != null) return Resultado.error("Esta tienda está suspendida: " + sus + ".");
        if (t.esDeLosDuenos(p)) return Resultado.error("No podés comprar en tu propia tienda.");
        if (!t.puedeComprar()) return Resultado.error("Esta tienda no vende este ítem.");
        Inventory shop = inventario(t);
        if (shop == null) return Resultado.error("El cofre de la tienda ya no existe.");
        int stock = contar(shop, t.item);
        if (stock < n) return Resultado.error(stock == 0 ? "Esta tienda está vacía." : "No hay suficiente stock: quedan " + Msg.numero(stock) + ".");
        PlayerInventory inv = p.getInventory();
        if (capacidad(inv, t.item) < n) return Resultado.error("No tenés lugar en el inventario para " + Msg.numero(n) + ".");
        final long total;
        try { total = Math.multiplyExact(t.compra, (long) n); } catch (ArithmeticException e) { return Resultado.error("El monto es demasiado grande."); }
        Economia eco = pl.economia();
        if (!eco.tiene(p, total)) return Resultado.error("No te alcanza la plata: necesitás " + Msg.dinero(total) + ".");
        OfflinePlayer dueno = Bukkit.getOfflinePlayer(t.dueno);

        // 1) cobrarle al jugador
        if (!eco.retirar(p, total)) return Resultado.error("No pude cobrarte. No se hizo nada.");
        // 2) sacar del cofre
        if (!quitar(shop, t.item, n)) {
            eco.depositar(p, total);
            return Resultado.error("El stock cambió justo ahora. No se hizo nada.");
        }
        // 3) dar al jugador
        if (!poner(inv, t.item, n)) {
            poner(shop, t.item, n);
            eco.depositar(p, total);
            return Resultado.error("No tenés lugar en el inventario. No se hizo nada.");
        }
        // 4) pagarle al dueño (o repartir entre los administradores)
        pagarIngreso(t, total, p.getName());
        pl.almacen().registrar(t, p.getUniqueId(), "COMPRA", n, total);
        avisar(t, p, true, n, total, shop);
        return new Resultado(true, "", total);
    }

    /** El jugador VENDE n items a la tienda. */
    Resultado vender(Player p, Tienda t, int n) {
        if (n <= 0) return Resultado.error("La cantidad tiene que ser mayor a cero.");
        String sus = pl.integridad().motivo(t);
        if (sus != null) return Resultado.error("Esta tienda está suspendida: " + sus + ".");
        if (t.esDeLosDuenos(p)) return Resultado.error("No podés vender en tu propia tienda.");
        if (!t.puedeVender()) return Resultado.error("Esta tienda no compra este ítem.");
        Inventory shop = inventario(t);
        if (shop == null) return Resultado.error("El cofre de la tienda ya no existe.");
        PlayerInventory inv = p.getInventory();
        int tiene = contar(inv, t.item);
        if (tiene < n) return Resultado.error("No tenés suficientes: tenés " + Msg.numero(tiene) + ".");
        if (capacidad(shop, t.item) < n) return Resultado.error("El cofre de la tienda no tiene lugar para tantos.");
        final long total;
        try { total = Math.multiplyExact(t.venta, (long) n); } catch (ArithmeticException e) { return Resultado.error("El monto es demasiado grande."); }
        Economia eco = pl.economia();
        OfflinePlayer dueno = Bukkit.getOfflinePlayer(t.dueno);
        if (!eco.tiene(dueno, total)) return Resultado.error("El dueño de la tienda no tiene plata suficiente para pagarte " + Msg.dinero(total) + ".");

        // 1) cobrarle al dueño
        if (!eco.retirar(dueno, total)) return Resultado.error("No se pudo cobrar al dueño. No se hizo nada.");
        // 2) sacar del inventario del jugador
        if (!quitar(inv, t.item, n)) {
            eco.depositar(dueno, total);
            return Resultado.error("Tu inventario cambió justo ahora. No se hizo nada.");
        }
        // 3) guardar en el cofre
        if (!poner(shop, t.item, n)) {
            poner(inv, t.item, n);
            eco.depositar(dueno, total);
            return Resultado.error("El cofre no tiene lugar. No se hizo nada.");
        }
        // 4) pagarle al jugador
        if (!eco.depositar(p, total)) {
            quitar(shop, t.item, n);
            poner(inv, t.item, n);
            eco.depositar(dueno, total);
            return Resultado.error("No pude pagarte. No se hizo nada.");
        }
        pl.almacen().registrar(t, p.getUniqueId(), "VENTA", n, total);
        avisar(t, p, false, n, total, shop);
        return new Resultado(true, "", total);
    }

    /** Le cuenta al dueño (y a los administradores que cobran) lo que pasó en su tienda, si estan conectados. */
    private void avisar(Tienda t, Player actor, boolean compro, int n, long total, Inventory shop) {
        if (pl.holograma() != null) pl.holograma().refrescar(t);
        if (!t.avisos) return;
        String item = VentanaTienda.nombreItem(t);
        java.util.List<java.util.UUID> receptores = new java.util.ArrayList<>();
        receptores.add(t.dueno);
        if (compro && "COMPARTIDO".equals(t.modoIngreso)) receptores.addAll(t.admins);
        long parte = total / receptores.size();
        int stock = contar(shop, t.item);
        for (int i = 0; i < receptores.size(); i++) {
            Player dest = Bukkit.getPlayer(receptores.get(i));
            if (dest == null) continue;
            boolean esDueno = receptores.get(i).equals(t.dueno);
            if (compro) {
                long monto = parte + (i == 0 ? total - parte * receptores.size() : 0);
                String extra = receptores.size() > 1 ? " Tu parte: " + Msg.dinero(monto) + " (repartido entre " + receptores.size() + ")." : "";
                Msg.ok(dest, actor.getName() + " te compró " + Msg.numero(n) + "x " + item + ". Se depositaron " + Msg.dinero(total) + " en tu cuenta." + extra);
            } else {
                Msg.info(dest, actor.getName() + " te vendió " + Msg.numero(n) + "x " + item + ". Se descontaron " + Msg.dinero(total) + " de tu cuenta.");
            }
            dest.playSound(dest.getLocation(), org.bukkit.Sound.ENTITY_ARROW_HIT_PLAYER, 0.6f, 1.4f);
            // la alerta salta una sola vez, cuando el stock llega al minimo que puso el dueño en ESTA tienda (o se acaba)
            if (compro && esDueno) {
                int antes = stock + n;
                if (stock == 0) Msg.error(dest, "Tu tienda de " + item + " se quedó sin stock.");
                else if (stock <= t.bajoStock && antes > t.bajoStock) Msg.error(dest, "A tu tienda de " + item + " le quedan solo " + Msg.numero(stock) + " unidades (tu aviso está en " + Msg.numero(t.bajoStock) + ").");
            }
        }
    }

    /** Paga lo que cobró la tienda: al dueño, o repartido entre dueño y administradores en modo COMPARTIDO. */
    private void pagarIngreso(Tienda t, long total, String comprador) {
        Economia eco = pl.economia();
        java.util.List<OfflinePlayer> receptores = new java.util.ArrayList<>();
        receptores.add(Bukkit.getOfflinePlayer(t.dueno));
        if ("COMPARTIDO".equals(t.modoIngreso)) for (java.util.UUID u : t.admins) receptores.add(Bukkit.getOfflinePlayer(u));
        long parte = total / receptores.size();
        long resto = total - parte * receptores.size();
        for (int i = 0; i < receptores.size(); i++) {
            long monto = parte + (i == 0 ? resto : 0);
            if (!eco.depositar(receptores.get(i), monto)) {
                pl.getLogger().severe("NO PUDE PAGAR a " + receptores.get(i).getName() + " " + monto + " centavos por una compra de "
                        + comprador + " en la tienda " + t.id + ". Revisar a mano.");
            }
        }
    }
}
