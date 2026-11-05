package ar.com.tresyetres.shops;

import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

/**
 * Cabezas con la skin del jugador aunque este desconectado. El server no es premium, asi que no puede pedirle la skin a Mojang:
 * la guardamos cuando el jugador entra o sale.
 */
final class Cabezas implements Listener {
    private final ShopsPlugin pl;

    Cabezas(ShopsPlugin pl) { this.pl = pl; }

    void iniciar() {
        for (Player p : Bukkit.getOnlinePlayers()) capturar(p);
    }

    /** Guarda la skin actual del jugador (si tiene). */
    void capturar(Player p) {
        try {
            for (ProfileProperty prop : p.getPlayerProfile().getProperties()) {
                if (prop.getName().equals("textures") && prop.getValue() != null && !prop.getValue().isBlank()) {
                    pl.almacen().guardarSkin(p.getUniqueId(), prop.getValue(), prop.getSignature());
                    return;
                }
            }
        } catch (Throwable t) {
            pl.getLogger().warning("No pude leer la skin de " + p.getName() + ": " + t);
        }
    }

    /** Le pone a la cabeza la skin del jugador: la de ahora si esta conectado, o la ultima guardada. */
    void aplicar(SkullMeta sm, UUID uuid) {
        if (uuid == null) return;
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            capturar(online);
            try { sm.setOwningPlayer(online); return; } catch (Throwable ignored) { }
        }
        String[] skin = pl.almacen().skin(uuid);
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        if (skin != null) {
            try {
                var perfil = Bukkit.createProfile(uuid, op.getName());
                perfil.setProperty(new ProfileProperty("textures", skin[0], skin[1].isBlank() ? null : skin[1]));
                sm.setPlayerProfile(perfil);
                return;
            } catch (Throwable t) {
                pl.getLogger().warning("No pude armar la cabeza de " + op.getName() + ": " + t);
            }
        }
        try { sm.setOwningPlayer(op); } catch (Throwable ignored) { }
    }

    @EventHandler
    public void alEntrar(PlayerJoinEvent e) {
        // SkinsRestorer aplica la skin un instante despues de entrar
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(pl, () -> { if (p.isOnline()) capturar(p); }, 80L);
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent e) { capturar(e.getPlayer()); }
}
