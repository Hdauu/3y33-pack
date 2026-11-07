package ar.com.tresyetres.trabajos;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.persistence.PersistentDataType;

/** Lo que el jugador hace y paga: romper, matar, pescar y criar, con las reglas anti-trampa. */
final class Escucha implements Listener {
    private static final java.util.Set<org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason> SIN_PAGO =
            java.util.EnumSet.noneOf(org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.class);
    static {
        for (var r : org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.values()) {
            String n = r.name();
            if (n.contains("SPAWNER") || n.contains("EGG")) SIN_PAGO.add(r);
        }
    }

    private final TrabajosPlugin pl;
    private final NamespacedKey noPaga;

    Escucha(TrabajosPlugin pl) {
        this.pl = pl;
        this.noPaga = new NamespacedKey(pl, "nopaga");
    }

    private static boolean enJuego(Player p) {
        GameMode g = p.getGameMode();
        return g == GameMode.SURVIVAL || g == GameMode.ADVENTURE;
    }

    @EventHandler
    public void alEntrar(PlayerJoinEvent e) {
        Perfil f = pl.perfiles().de(e.getPlayer());
        f.nombre = e.getPlayer().getName();
        f.ultimoMov = System.currentTimeMillis();
        Player jugador = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(pl, () -> { if (jugador.isOnline()) pl.misiones().pagarPendientes(jugador); }, 60L);
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent e) {
        Perfil f = pl.perfiles().existente(e.getPlayer());
        if (f != null) pl.pago().vaciar(e.getPlayer(), f);
        pl.perfiles().descargar(e.getPlayer());
    }

    /** Cada segundo mira quien se movio o giro (en vez de escuchar cada paso de cada jugador, que es mucho mas caro). */
    void muestrear() {
        long ahora = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Perfil f = pl.perfiles().existente(p);
            if (f == null) continue;
            pl.recompensas().tick(p, f);
            Location l = p.getLocation();
            if (l.getX() != f.mx || l.getY() != f.my || l.getZ() != f.mz || l.getYaw() != f.myaw || l.getPitch() != f.mpitch) {
                f.mx = l.getX(); f.my = l.getY(); f.mz = l.getZ(); f.myaw = l.getYaw(); f.mpitch = l.getPitch();
                f.ultimoMov = ahora;
            }
        }
    }

    /** Un bloque que paga y lo pone un jugador se marca: despues no paga. Los cultivos no se marcan (se miran por madurez). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void alColocar(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        if (!pl.pago().mundoPaga(b.getWorld())) return;
        if (!pl.catalogo().pagable(b.getType())) return;
        if (b.getBlockData() instanceof Ageable) return;
        pl.colocados().marcar(b);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void alRomper(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!enJuego(p) || !pl.pago().mundoPaga(p.getWorld())) return;
        Perfil f = pl.perfiles().existente(p);
        if (f == null || f.activo == null) return;
        Block b = e.getBlock();
        Accion a = pl.catalogo().bloque(f.activo, b.getType());
        if (a == null) return;
        if (b.getBlockData() instanceof Ageable ag) {
            if (ag.getAge() < ag.getMaximumAge()) return;      // los cultivos pagan solo maduros
        } else if (pl.colocados().quitar(b)) {
            return;                                             // lo puso un jugador
        }
        pl.pago().accion(p, a);
    }

    /** Los mobs que salen de un spawner o de un huevo no pagan. */
    @EventHandler(ignoreCancelled = true)
    public void alAparecer(CreatureSpawnEvent e) {
        if (SIN_PAGO.contains(e.getSpawnReason())) {
            e.getEntity().getPersistentDataContainer().set(noPaga, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alMorir(EntityDeathEvent e) {
        LivingEntity muerto = e.getEntity();
        Player p = muerto.getKiller();
        if (p == null || !enJuego(p)) return;
        Perfil f = pl.perfiles().existente(p);
        if (f == null || f.activo == null) return;
        Accion a = pl.catalogo().mob(f.activo, muerto.getType());
        if (a == null) return;
        if (muerto.getPersistentDataContainer().has(noPaga, PersistentDataType.BYTE)) return;
        if (muerto instanceof org.bukkit.entity.Ageable cria && !cria.isAdult()) return;
        if (f.tope("mob:" + muerto.getType().name(), pl.getConfig().getInt("topes.mobs-por-hora", 120), System.currentTimeMillis())) return;
        pl.pago().accion(p, a);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void alCriar(EntityBreedEvent e) {
        if (!(e.getBreeder() instanceof Player p) || !enJuego(p)) return;
        Perfil f = pl.perfiles().existente(p);
        if (f == null || f.activo == null) return;
        Accion a = pl.catalogo().cria();
        if (a == null || a.trabajo() != f.activo) return;
        if (f.tope("cria", pl.getConfig().getInt("topes.crias-por-hora", 20), System.currentTimeMillis())) return;
        pl.pago().accion(p, a);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void alPescar(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player p = e.getPlayer();
        if (!enJuego(p)) return;
        Perfil f = pl.perfiles().existente(p);
        if (f == null || f.activo != Trabajo.PESCADOR) return;
        Entity c = e.getCaught();
        if (!(c instanceof Item it)) return;
        Accion a = pl.catalogo().pesca(clasificar(it.getItemStack()));
        if (a != null) pl.pago().accion(p, a);
    }

    private static String clasificar(ItemStack it) {
        Material m = it.getType();
        return switch (m) {
            case COD, SALMON -> "PESCADO";
            case TROPICAL_FISH, PUFFERFISH -> "TROPICAL";
            case ENCHANTED_BOOK, NAME_TAG, SADDLE, NAUTILUS_SHELL -> "TESORO";
            case BOW, FISHING_ROD -> it.getEnchantments().isEmpty() ? "BASURA" : "TESORO";
            default -> "BASURA";
        };
    }
}
