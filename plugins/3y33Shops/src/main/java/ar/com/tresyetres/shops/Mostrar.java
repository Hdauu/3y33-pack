package ar.com.tresyetres.shops;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /tienda mostrar vacias|bajostock: resalta, solo para vos, tus tiendas cercanas que estan vacias o con poco stock. */
final class Mostrar implements Listener {
    private static final class Activo {
        BukkitTask tarea;
        final List<UUID> entidades = new ArrayList<>();
    }

    private final ShopsPlugin pl;
    private final Map<UUID, Activo> activos = new HashMap<>();

    Mostrar(ShopsPlugin pl) { this.pl = pl; }

    void iniciar(Player p, boolean vacias) {
        apagar(p.getUniqueId());
        double radio = pl.getConfig().getDouble("mostrar.radio", 40.0);
        double r2 = radio * radio;
        List<Tienda> encontradas = new ArrayList<>();
        for (Tienda t : pl.almacen().todas()) {
            World w = t.mundoObj();
            if (w == null || !w.equals(p.getWorld()) || !t.administra(p) || !t.puedeComprar()) continue;
            if (!w.isChunkLoaded(t.x >> 4, t.z >> 4)) continue;
            if (p.getLocation().distanceSquared(t.ubicacion()) > r2) continue;
            Inventory inv = Operaciones.inventario(t);
            if (inv == null) continue;
            int stock = Operaciones.contar(inv, t.item);
            if (vacias ? stock == 0 : (stock > 0 && stock <= t.bajoStock)) encontradas.add(t);
        }
        String que = vacias ? "vacías" : "con bajo stock";
        if (encontradas.isEmpty()) { Msg.info(p, "No tenés tiendas " + que + " cerca tuyo (a menos de " + (int) radio + " bloques)."); return; }
        Msg.ok(p, "Tiendas " + que + " cerca tuyo: " + encontradas.size() + ". Las resalté por un rato.");

        Color color = vacias ? Color.fromRGB(255, 40, 40) : Color.fromRGB(255, 160, 0);
        Activo a = new Activo();
        boolean bedrock = Holograma.esBedrock(p);
        if (!bedrock) {
            for (Tienda t : encontradas) {
                List<Block> bloques = new ArrayList<>();
                bloques.add(t.bloque());
                if (t.doble) bloques.add(t.mundoObj().getBlockAt(t.x2, t.y2, t.z2));
                for (Block b : bloques) {
                    if (b == null) continue;
                    BlockDisplay d = b.getWorld().spawn(b.getLocation(), BlockDisplay.class, x -> {
                        x.setBlock(b.getBlockData());
                        x.setGlowing(true);
                        x.setGlowColorOverride(color);
                        x.setTransformation(new Transformation(new Vector3f(-0.01f, -0.01f, -0.01f), new Quaternionf(), new Vector3f(1.02f, 1.02f, 1.02f), new Quaternionf()));
                        x.setPersistent(false);
                        x.setVisibleByDefault(false);
                    });
                    p.showEntity(pl, d);
                    a.entidades.add(d.getUniqueId());
                }
            }
        }
        final long fin = System.currentTimeMillis() + pl.getConfig().getLong("mostrar.segundos", 20) * 1000L;
        final Particle.DustOptions polvo = new Particle.DustOptions(color, 1.6f);
        a.tarea = Bukkit.getScheduler().runTaskTimer(pl, () -> {
            if (!p.isOnline() || System.currentTimeMillis() > fin) { apagar(p.getUniqueId()); return; }
            for (Tienda t : encontradas) {
                Location c = t.ubicacion().add(0, 0.9, 0);
                p.spawnParticle(Particle.DUST, c, 5, 0.2, 0.9, 0.2, 0, polvo);
            }
        }, 0L, 10L);
        activos.put(p.getUniqueId(), a);
    }

    void apagar(UUID jugador) {
        Activo a = activos.remove(jugador);
        if (a == null) return;
        if (a.tarea != null) a.tarea.cancel();
        for (UUID u : a.entidades) {
            Entity e = Bukkit.getEntity(u);
            if (e != null) e.remove();
        }
    }

    void detener() { for (UUID u : new ArrayList<>(activos.keySet())) apagar(u); }

    @EventHandler
    public void alSalir(PlayerQuitEvent e) { apagar(e.getPlayer().getUniqueId()); }
}
