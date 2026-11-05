package ar.com.tresyetres.shops;

import org.bukkit.plugin.java.JavaPlugin;

/** 3y33Shops: tiendas de cofre con motor propio, atadas a las tierras de Lands. */
public final class ShopsPlugin extends JavaPlugin {
    private Almacen almacen;
    private Economia economia;
    private LandsPuente lands;
    private Operaciones operaciones;
    private VentanaTienda ventana;
    private Ajustes ajustes;
    private Entrada entrada;

    Almacen almacen() { return almacen; }
    Economia economia() { return economia; }
    LandsPuente lands() { return lands; }
    Operaciones operaciones() { return operaciones; }
    VentanaTienda ventana() { return ventana; }
    Ajustes ajustes() { return ajustes; }
    Entrada entrada() { return entrada; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        economia = new Economia();
        if (!economia.iniciar()) {
            getLogger().severe("No encontré una economía de Vault. Apago el plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        lands = new LandsPuente(this);
        if (!lands.disponible()) {
            getLogger().severe("No pude conectar con la API de Lands. Apago el plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        almacen = new Almacen(this);
        try {
            almacen.abrir();
        } catch (Exception e) {
            getLogger().severe("No pude abrir la base de datos de tiendas: " + e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        operaciones = new Operaciones(this);
        ventana = new VentanaTienda(this);
        ajustes = new Ajustes(this);
        entrada = new Entrada(this);
        getServer().getPluginManager().registerEvents(ventana, this);
        getServer().getPluginManager().registerEvents(ajustes, this);
        getServer().getPluginManager().registerEvents(entrada, this);
        getServer().getPluginManager().registerEvents(new Proteccion(this), this);
        TiendaComando tc = new TiendaComando(this);
        var cmd = getCommand("tienda");
        if (cmd != null) { cmd.setExecutor(tc); cmd.setTabCompleter(tc); }
        TiendaAdminComando ta = new TiendaAdminComando(this);
        var adm = getCommand("tiendaadmin");
        if (adm != null) { adm.setExecutor(ta); adm.setTabCompleter(ta); }
        getLogger().info("3y33Shops listo: " + almacen.todas().size() + " tiendas cargadas.");
    }

    @Override
    public void onDisable() {
        if (almacen != null) almacen.cerrar();
    }
}
