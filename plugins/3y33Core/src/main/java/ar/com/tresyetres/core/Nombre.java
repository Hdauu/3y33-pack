package ar.com.tresyetres.core;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Nombre global del jugador: insignia de rango + degradado de su color.
 * Se fija como display name, asi lo usan todos los plugins que nombran jugadores
 * (Essentials: AFK, modo de juego, tpa; mensajes de muerte, etc.) sin tocar uno por uno.
 * Essentials tiene que tener change-displayname: false para no pisarlo.
 */
final class Nombre implements Listener {
    private final Core core;
    private final Color color;
    private final Map<UUID, String> aplicado = new HashMap<>();

    Nombre(Core core, Color color) {
        this.core = core;
        this.color = color;
        Bukkit.getScheduler().runTaskTimer(core, this::repasar, 100L, 100L);
    }

    /** El nombre que ven todos: [insignia] nombre con degradado. */
    String de(Player p) {
        // §f: el glifo del pack se tiñe con el color del texto de alrededor; en blanco se ve con sus colores originales
        String ins = color.insignia(p);
        return (ins.isEmpty() ? "" : "§f" + ins) + color.nombreLegacy(p) + "§r";
    }

    void refrescar(Player p) {
        if (!p.isOnline()) return;
        String n;
        try { n = de(p); } catch (RuntimeException e) { return; }
        if (n.equals(aplicado.get(p.getUniqueId()))) return;
        aplicado.put(p.getUniqueId(), n);
        p.displayName(LegacyComponentSerializer.legacySection().deserialize(n));
    }

    private void repasar() {
        for (Player p : Bukkit.getOnlinePlayers()) refrescar(p);
    }

    @EventHandler
    public void alEntrar(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        aplicado.remove(p.getUniqueId());
        refrescar(p);
        // despues de que LuckPerms y Essentials terminen de cargar al jugador
        Bukkit.getScheduler().runTaskLater(core, () -> { aplicado.remove(p.getUniqueId()); refrescar(p); }, 20L);
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent e) { aplicado.remove(e.getPlayer().getUniqueId()); }

    /** Para cuando cambia el color o el estilo: se aplica ya, sin esperar al repaso. */
    void forzar(Player p) {
        aplicado.remove(p.getUniqueId());
        refrescar(p);
    }
}
