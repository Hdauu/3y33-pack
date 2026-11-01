package ar.com.tresyetres.tiendas;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Reglas de tiendas de 3y33 SMP sobre Lands: solo dentro del terreno de tu hermandad y con el nombre del terreno. */
public final class TiendasPlugin extends JavaPlugin {
    private LandsPuente lands;

    @Override
    public void onEnable() {
        lands = new LandsPuente(this);
        if (!lands.disponible()) {
            getLogger().severe("No pude conectar con la API de Lands: las reglas de tiendas quedan apagadas.");
            return;
        }
        getServer().getPluginManager().registerEvents(new Tiendas(this, lands), this);
        getLogger().info("Reglas de tiendas conectadas con Lands.");
    }

    void msg(Player p, String texto) {
        p.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c3y33 &8» &f" + texto));
    }
}
