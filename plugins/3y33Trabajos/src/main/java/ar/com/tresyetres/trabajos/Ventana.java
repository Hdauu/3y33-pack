package ar.com.tresyetres.trabajos;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Una ventana de menu: cada casillero puede tener una accion al hacer clic. */
final class Ventana implements InventoryHolder {
    private final Inventory inv;
    private final Map<Integer, Consumer<InventoryClickEvent>> acciones = new HashMap<>();

    Ventana(int filas, Component titulo) { inv = Bukkit.createInventory(this, filas * 9, titulo); }

    @Override
    public Inventory getInventory() { return inv; }

    void poner(int slot, ItemStack it) { inv.setItem(slot, it); }

    void poner(int slot, ItemStack it, Consumer<InventoryClickEvent> alClic) {
        inv.setItem(slot, it);
        acciones.put(slot, alClic);
    }

    void clic(InventoryClickEvent e) {
        Consumer<InventoryClickEvent> c = acciones.get(e.getSlot());
        if (c != null) c.accept(e);
    }

    void abrir(Player p) { p.openInventory(inv); }
}
