package ar.com.tresyetres.trabajos;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

/**
 * Marca los bloques que pagan y puso un jugador, para que romper y volver a poner no sirva.
 * Las marcas viven en memoria mientras el chunk esta cargado (rapido) y se vuelcan al chunk al descargarlo y al guardar el mundo.
 */
final class Colocados implements Listener {
    private static final int MAX_POR_CHUNK = 4096;
    private final NamespacedKey clave;

    private record ClaveChunk(UUID mundo, long chunk) { }

    private static final class Datos {
        final LinkedHashSet<Long> marcas = new LinkedHashSet<>();
        boolean sucio;
    }

    private final Map<ClaveChunk, Datos> cache = new HashMap<>();

    Colocados(TrabajosPlugin pl) { this.clave = new NamespacedKey(pl, "colocados"); }

    private static long pos(Block b) {
        return ((long) (b.getX() & 15) << 40) | ((long) (b.getZ() & 15) << 32) | ((long) (b.getY() + 2048) & 0xFFFFFFFFL);
    }

    private static ClaveChunk clave(Chunk c) { return new ClaveChunk(c.getWorld().getUID(), c.getChunkKey()); }

    /** Las marcas del chunk (las lee del chunk la primera vez). */
    private Datos datos(Chunk c) {
        ClaveChunk k = clave(c);
        Datos d = cache.get(k);
        if (d == null) {
            d = new Datos();
            long[] a = c.getPersistentDataContainer().get(clave, PersistentDataType.LONG_ARRAY);
            if (a != null) for (long v : a) d.marcas.add(v);
            cache.put(k, d);
        }
        return d;
    }

    void marcar(Block b) {
        Datos d = datos(b.getChunk());
        long p = pos(b);
        d.marcas.remove(p);
        d.marcas.add(p);
        if (d.marcas.size() > MAX_POR_CHUNK) {          // se descarta la mas vieja
            Iterator<Long> it = d.marcas.iterator();
            it.next();
            it.remove();
        }
        d.sucio = true;
    }

    /** true si el bloque estaba marcado (y le saca la marca). */
    boolean quitar(Block b) {
        Chunk c = b.getChunk();
        Datos d = cache.get(clave(c));
        if (d == null) {
            if (!c.getPersistentDataContainer().has(clave, PersistentDataType.LONG_ARRAY)) return false;   // chunk sin marcas: no se carga nada
            d = datos(c);
        }
        if (!d.marcas.remove(pos(b))) return false;
        d.sucio = true;
        return true;
    }

    private void volcar(Chunk c, Datos d) {
        if (!d.sucio) return;
        PersistentDataContainer pdc = c.getPersistentDataContainer();
        if (d.marcas.isEmpty()) pdc.remove(clave);
        else {
            long[] a = new long[d.marcas.size()];
            int i = 0;
            for (long v : d.marcas) a[i++] = v;
            pdc.set(clave, PersistentDataType.LONG_ARRAY, a);
        }
        d.sucio = false;
    }

    @EventHandler
    public void alDescargar(ChunkUnloadEvent e) {
        Datos d = cache.remove(clave(e.getChunk()));
        if (d != null) volcar(e.getChunk(), d);
    }

    @EventHandler
    public void alGuardarMundo(WorldSaveEvent e) { volcarMundo(e.getWorld()); }

    private void volcarMundo(World w) {
        for (Map.Entry<ClaveChunk, Datos> en : cache.entrySet()) {
            if (!en.getKey().mundo().equals(w.getUID()) || !en.getValue().sucio) continue;
            long ck = en.getKey().chunk();
            int x = (int) ck, z = (int) (ck >> 32);
            if (w.isChunkLoaded(x, z)) volcar(w.getChunkAt(x, z), en.getValue());
        }
    }

    /** Al apagar el plugin: todo lo que falte al chunk. */
    void volcarTodo() {
        for (World w : Bukkit.getWorlds()) volcarMundo(w);
    }
}
