package ar.com.tresyetres.trabajos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

/** El NPC del spawn: un aldeano quieto que abre /trabajos al hacerle clic derecho. Se crea y se saca con /trabajosadmin npc. */
final class Npcs implements Listener {
    private final TrabajosPlugin pl;
    private final NamespacedKey marca;

    Npcs(TrabajosPlugin pl) {
        this.pl = pl;
        this.marca = new NamespacedKey(pl, "npc-trabajos");
    }

    private boolean esNpc(Entity e) { return e.getPersistentDataContainer().has(marca, PersistentDataType.BYTE); }

    /** Crea el NPC donde esta parado el jugador, mirando hacia el. */
    void crear(Player p) {
        Location l = p.getLocation().clone();
        l.setYaw(p.getLocation().getYaw() + 180f);
        l.setPitch(0f);
        Villager v = l.getWorld().spawn(l, Villager.class, a -> {
            a.getPersistentDataContainer().set(marca, PersistentDataType.BYTE, (byte) 1);
            a.setAI(false);
            a.setInvulnerable(true);
            a.setSilent(true);
            a.setCollidable(false);
            a.setPersistent(true);
            a.setRemoveWhenFarAway(false);
            a.setProfession(Villager.Profession.LIBRARIAN);
            a.setVillagerLevel(5);
            a.customName(Component.text("TRABAJOS", NamedTextColor.RED, TextDecoration.BOLD));
            a.setCustomNameVisible(true);
        });
        Msg.ok(p, "Listo: puse el NPC de Trabajos en " + v.getLocation().getBlockX() + ", " + v.getLocation().getBlockY() + ", " + v.getLocation().getBlockZ() + ".");
    }

    /** Saca los NPC de Trabajos que esten a menos de 4 bloques del jugador. Devuelve cuantos saco. */
    int quitar(Player p) {
        int n = 0;
        for (Entity e : p.getNearbyEntities(4, 4, 4)) {
            if (esNpc(e)) { e.remove(); n++; }
        }
        return n;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void alTocar(PlayerInteractEntityEvent e) {
        if (!esNpc(e.getRightClicked())) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        pl.menus().principal(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void alDanar(EntityDamageEvent e) {
        if (esNpc(e.getEntity())) e.setCancelled(true);
    }
}
