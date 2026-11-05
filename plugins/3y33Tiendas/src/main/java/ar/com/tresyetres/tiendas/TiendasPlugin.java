package ar.com.tresyetres.tiendas;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Reglas de tiendas de 3y33 SMP sobre Lands: solo dentro del terreno de tu hermandad y con el nombre del terreno. Ademas, holograma propio por tienda. */
public final class TiendasPlugin extends JavaPlugin {
    private LandsPuente lands;
    private Hologramas holos;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        lands = new LandsPuente(this);
        if (!lands.disponible()) {
            getLogger().severe("No pude conectar con la API de Lands: las reglas de tiendas quedan apagadas.");
        } else {
            getServer().getPluginManager().registerEvents(new Tiendas(this, lands), this);
            getServer().getPluginManager().registerEvents(new BrowseLand(this, lands), this);
            getLogger().info("Reglas de tiendas conectadas con Lands.");
        }
        holos = new Hologramas(this);
        getServer().getPluginManager().registerEvents(holos, this);
        HoloComando hc = new HoloComando(this, holos);
        var cmd = getCommand("holo");
        if (cmd != null) { cmd.setExecutor(hc); cmd.setTabCompleter(hc); }
        getServer().getPluginManager().registerEvents(new MenuHolo(this, hc), this);
        getServer().getPluginManager().registerEvents(new KeeperAjustes(this, holos, hc), this);
        try {
            com.ghostchu.quickshop.api.QuickShopAPI.getInstance().getShopControlPanelManager().addComponent(new PanelHolograma());
        } catch (Throwable t) { getLogger().warning("No pude sumar el holograma al panel de la tienda: " + t); }
        // QuickShop carga las tiendas un poco despues de arrancar: sincronizamos seguido
        getServer().getScheduler().runTaskTimer(this, holos::sincronizar, 100L, 100L);
        getServer().getScheduler().runTaskTimer(this, holos::visibilidad, 40L, 20L);
        getServer().getScheduler().runTaskLater(this, holos::limpiarHuerfanas, 200L);
    }

    @Override
    public void onDisable() {
        if (holos != null) holos.quitarTodo();
    }

    void msg(Player p, String texto) {
        p.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c3y33 &8» &f" + texto));
    }
}
