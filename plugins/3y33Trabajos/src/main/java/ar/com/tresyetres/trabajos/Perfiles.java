package ar.com.tresyetres.trabajos;

import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/** Perfiles de los jugadores conectados, guardados uno por archivo (escritura aparte y atomica: un corte no deja archivos a medias). */
final class Perfiles {
    private final TrabajosPlugin pl;
    private final File carpeta;
    private final Map<UUID, Perfil> mapa = new ConcurrentHashMap<>();
    /** Un solo hilo para escribir: los guardados quedan en orden y el ultimo siempre gana. */
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "3y33Trabajos-guardado");
        t.setDaemon(true);
        return t;
    });

    Perfiles(TrabajosPlugin pl) {
        this.pl = pl;
        carpeta = new File(pl.getDataFolder(), "jugadores");
        carpeta.mkdirs();
    }

    private File archivo(UUID u) { return new File(carpeta, u + ".yml"); }

    /** El perfil del jugador; lo carga del disco la primera vez. */
    Perfil de(Player p) {
        return mapa.computeIfAbsent(p.getUniqueId(), u -> {
            Perfil f = new Perfil(u, p.getName());
            f.cargar(archivo(u));
            return f;
        });
    }

    /** El perfil si ya esta cargado, o null. */
    Perfil existente(Player p) { return mapa.get(p.getUniqueId()); }

    /** Arma el texto ahora (en el hilo del servidor) y lo escribe en el hilo de guardado. */
    void guardar(Perfil f) {
        String texto = f.aTexto();
        File destino = archivo(f.id);
        try {
            io.execute(() -> escribir(destino, texto));
        } catch (RejectedExecutionException cerrado) {
            escribir(destino, texto);
        }
    }

    private void escribir(File destino, String texto) {
        File tmp = new File(destino.getParentFile(), destino.getName() + ".tmp");
        try {
            Files.writeString(tmp.toPath(), texto, StandardCharsets.UTF_8);
            try {
                Files.move(tmp.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException noAtomico) {
                Files.move(tmp.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            pl.getLogger().warning("No pude guardar " + destino.getName() + ": " + ex);
        }
    }

    void descargar(Player p) {
        Perfil f = mapa.remove(p.getUniqueId());
        if (f != null) guardar(f);
    }

    void guardarTodo() {
        for (Perfil f : mapa.values()) guardar(f);
    }

    /** Al apagar: espera a que terminen las escrituras pendientes. */
    void cerrar() {
        io.shutdown();
        try { io.awaitTermination(15, TimeUnit.SECONDS); } catch (InterruptedException ignorada) { Thread.currentThread().interrupt(); }
    }
}
