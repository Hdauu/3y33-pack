package ar.com.tresyetres.shops;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Le pide un texto al jugador por el chat (funciona igual en Java y en Bedrock). Lo que escribe no sale en el chat publico. */
final class Entrada implements Listener {
    private final ShopsPlugin pl;
    private final Map<UUID, Consumer<String>> espera = new ConcurrentHashMap<>();

    Entrada(ShopsPlugin pl) { this.pl = pl; }

    /**
     * Le pide un texto al jugador en una ventana con cuadro de texto: Java con un dialogo de pantalla, Bedrock con un formulario.
     * Si no se puede, cae al chat. {@code alCancelar} se llama si toca "Cancelar" (puede ser null).
     */
    void dialogo(Player p, String titulo, String ayuda, String etiqueta, String inicial, int max, Runnable alCancelar, Consumer<String> alRecibir) {
        p.closeInventory();
        final Consumer<String> recibir = txt -> Bukkit.getScheduler().runTask(pl, () -> { if (p.isOnline()) alRecibir.accept(txt == null ? "" : txt.trim()); });
        final Runnable cancelar = () -> Bukkit.getScheduler().runTask(pl, () -> { if (p.isOnline() && alCancelar != null) alCancelar.run(); });
        try {
            if (Holograma.esBedrock(p)) {
                if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
                    EntradaBedrock.enviar(p.getUniqueId(), titulo, ayuda, etiqueta, inicial, recibir, cancelar);
                    return;
                }
            } else {
                dialogoJava(p, titulo, ayuda, etiqueta, inicial, max, recibir, cancelar);
                return;
            }
        } catch (Throwable t) {
            pl.getLogger().warning("No pude abrir el cuadro de texto, uso el chat: " + t);
        }
        pedir(p, etiqueta + " (" + ayuda + ")", alRecibir);
    }

    private void dialogoJava(Player p, String titulo, String ayuda, String etiqueta, String inicial, int max, Consumer<String> recibir, Runnable cancelar) {
        List<DialogBody> cuerpo = ayuda == null || ayuda.isBlank() ? List.of() : List.of(DialogBody.plainMessage(Component.text(ayuda, NamedTextColor.GRAY), 300));
        DialogBase base = DialogBase.builder(Component.text(titulo, NamedTextColor.RED, TextDecoration.BOLD))
                .canCloseWithEscape(true)
                .body(cuerpo)
                .inputs(List.of(DialogInput.text("valor", Component.text(etiqueta)).initial(inicial == null ? "" : inicial).maxLength(Math.max(1, max)).width(300).build()))
                .build();
        ClickCallback.Options opciones = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();
        ActionButton guardar = ActionButton.create(Component.text("Guardar", NamedTextColor.GREEN), null, 120,
                DialogAction.customClick((view, audiencia) -> recibir.accept(view.getText("valor")), opciones));
        ActionButton cancel = ActionButton.create(Component.text("Cancelar", NamedTextColor.RED), null, 120,
                DialogAction.customClick((view, audiencia) -> cancelar.run(), opciones));
        Dialog dialogo = Dialog.create(b -> b.empty().base(base).type(DialogType.confirmation(guardar, cancel)));
        p.showDialog(dialogo);
    }

    /** Pide por el chat (solo como ultimo recurso). */
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
