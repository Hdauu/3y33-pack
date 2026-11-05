package ar.com.tresyetres.shops;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Le pide un texto al jugador por el chat (funciona igual en Java y en Bedrock). Lo que escribe no sale en el chat publico. */
final class Entrada implements Listener {
    private final ShopsPlugin pl;
    private final Map<UUID, Consumer<String>> espera = new ConcurrentHashMap<>();

    Entrada(ShopsPlugin pl) { this.pl = pl; }

    void pedir(Player p, String pregunta, Consumer<String> alRecibir) {
        espera.put(p.getUniqueId(), alRecibir);
        p.closeInventory();
        Msg.info(p, pregunta + " (o escribí: cancelar)");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void alChat(AsyncChatEvent e) {
        Consumer<String> cb = espera.remove(e.getPlayer().getUniqueId());
        if (cb == null) return;
        e.setCancelled(true);
        String texto = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(pl, () -> {
            if (texto.equalsIgnoreCase("cancelar")) { Msg.info(p, "Cancelado."); return; }
            cb.accept(texto);
        });
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent e) { espera.remove(e.getPlayer().getUniqueId()); }
}
