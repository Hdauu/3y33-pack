package ar.com.tresyetres.shops;

import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;
import java.util.function.Consumer;

/** Formulario de Bedrock con un cuadro de texto (Floodgate). Va en una clase aparte para no cargar Floodgate si no esta. */
final class EntradaBedrock {
    private EntradaBedrock() { }

    static void enviar(UUID jugador, String titulo, String ayuda, String etiqueta, String inicial, Consumer<String> recibir, Runnable cancelar) {
        String texto = ayuda == null || ayuda.isBlank() ? etiqueta : etiqueta + "\n" + ayuda;
        CustomForm form = CustomForm.builder()
                .title(titulo)
                .input(texto, "", inicial == null ? "" : inicial)
                .validResultHandler(resp -> recibir.accept(resp.asInput(0)))
                .closedOrInvalidResultHandler(cancelar)
                .build();
        FloodgateApi.getInstance().sendForm(jugador, form);
    }
}
