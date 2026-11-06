package ar.com.tresyetres.trabajos;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

/** Marca los bloques que pagan y puso un jugador (en el propio chunk), para que romper y volver a poner no sirva. */
final class Colocados {
    private static final int MAX_POR_CHUNK = 4096;
    private final NamespacedKey clave;

    Colocados(TrabajosPlugin pl) { this.clave = new NamespacedKey(pl, "colocados"); }

    private static long pos(Block b) {
        return ((long) (b.getX() & 15) << 40) | ((long) (b.getZ() & 15) << 32) | ((long) (b.getY() + 2048) & 0xFFFFFFFFL);
    }

    void marcar(Block b) {
        PersistentDataContainer c = b.getChunk().getPersistentDataContainer();
        long[] a = c.getOrDefault(clave, PersistentDataType.LONG_ARRAY, new long[0]);
        long p = pos(b);
        for (long v : a) if (v == p) return;
        long[] n;
        if (a.length >= MAX_POR_CHUNK) {
            n = Arrays.copyOfRange(a, 1, a.length + 1);     // se descarta el mas viejo
            n[n.length - 1] = p;
        } else {
            n = Arrays.copyOf(a, a.length + 1);
            n[a.length] = p;
        }
        c.set(clave, PersistentDataType.LONG_ARRAY, n);
    }

    /** true si el bloque estaba marcado (y le saca la marca). */
    boolean quitar(Block b) {
        PersistentDataContainer c = b.getChunk().getPersistentDataContainer();
        long[] a = c.get(clave, PersistentDataType.LONG_ARRAY);
        if (a == null || a.length == 0) return false;
        long p = pos(b);
        int i = -1;
        for (int k = 0; k < a.length; k++) if (a[k] == p) { i = k; break; }
        if (i < 0) return false;
        long[] n = new long[a.length - 1];
        System.arraycopy(a, 0, n, 0, i);
        System.arraycopy(a, i + 1, n, i, a.length - i - 1);
        if (n.length == 0) c.remove(clave); else c.set(clave, PersistentDataType.LONG_ARRAY, n);
        return true;
    }
}
