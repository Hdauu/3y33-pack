package ar.com.tresyetres.tierras;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.ayosynk.landClaimPlugin.models.ClaimProfile;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Menú de naturaleza de un terreno (27 slots). */
final class MenuFlags implements InventoryHolder {

    static final MiniMessage MM = MiniMessage.miniMessage();
    // slots de cada flag, en el mismo orden que Flag.values()
    static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};
    static final int SLOT_CERRAR = 22;

    final UUID perfil;
    private final Inventory inv;

    MenuFlags(Tierras plugin, ClaimProfile p) {
        this.perfil = p.getProfileId();
        this.inv = Bukkit.createInventory(this, 27, MM.deserialize("<dark_gray>Naturaleza » " + escapar(p.getName())));
        dibujar(plugin, p);
    }

    static String escapar(String s) {
        return MM.escapeTags(s == null ? "" : s);
    }

    void dibujar(Tierras plugin, ClaimProfile p) {
        ItemStack vidrioN = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of());
        ItemStack vidrioG = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < 27; i++) inv.setItem(i, (i < 9 || i >= 18) ? vidrioN : vidrioG);

        Flag[] flags = Flag.values();
        for (int i = 0; i < flags.length && i < SLOTS.length; i++) {
            Flag f = flags[i];
            boolean on = plugin.flag(p, f);
            List<Component> lore = new ArrayList<>();
            for (String l : f.descripcion) lore.add(MM.deserialize("<!italic><gray>" + l));
            lore.add(Component.text(" "));
            lore.add(MM.deserialize(on ? "<!italic><green>✔ Sí <dark_gray>· click para apagar" : "<!italic><red>✖ No <dark_gray>· click para prender"));
            Component nombre = MM.deserialize("<!italic><gradient:" + f.c1 + ":" + f.c2 + "><bold>" + f.nombre + "</bold></gradient>");
            ItemStack it = item(f.icono, nombre, lore);
            if (on) {
                ItemMeta m = it.getItemMeta();
                m.setEnchantmentGlintOverride(true);
                it.setItemMeta(m);
            }
            inv.setItem(SLOTS[i], it);
        }
        inv.setItem(4, item(Material.NETHER_STAR,
                MM.deserialize("<!italic><gradient:#8ce99a:#2f9e44><bold>" + escapar(p.getName()) + "</bold></gradient>"),
                List.of(MM.deserialize("<!italic><gray>Qué pasa solo dentro de este terreno."),
                        MM.deserialize("<!italic><gray>Vale para todos sus chunks."))));
        inv.setItem(SLOT_CERRAR, item(Material.BARRIER, MM.deserialize("<!italic><red><bold>Cerrar"), List.of()));
    }

    static ItemStack item(Material mat, Component nombre, List<Component> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(nombre);
        m.lore(lore);
        m.addItemFlags(ItemFlag.values());
        it.setItemMeta(m);
        return it;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
