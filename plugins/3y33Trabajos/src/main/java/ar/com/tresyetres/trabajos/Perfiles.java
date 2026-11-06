package ar.com.tresyetres.trabajos;

import org.bukkit.entity.Player;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Perfiles de los jugadores conectados, guardados uno por archivo. */
final class Perfiles {
    private final File carpeta;
    private final Map<UUID, Perfil> mapa = new ConcurrentHashMap<>();

    Perfiles(TrabajosPlugin pl) {
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

    void guardar(Perfil f) { f.guardar(archivo(f.id)); }

    void descargar(Player p) {
        Perfil f = mapa.remove(p.getUniqueId());
        if (f != null) guardar(f);
    }

    void guardarTodo() {
        for (Perfil f : mapa.values()) guardar(f);
    }
}
