package ar.com.tresyetres.trabajos;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/** 3y33Trabajos: cinco trabajos con XP, rarezas diarias por accion, momentum y pago por Vault. */
public final class TrabajosPlugin extends JavaPlugin {
    private Pago pago;
    private Catalogo catalogo;
    private Rarezas rarezas;
    private Perfiles perfiles;
    private Iconos iconos;
    private Colocados colocados;
    private Balance balance;
    private Recompensas recompensas;
    private Servicio servicio;
    private Menus menus;
    private Misiones misiones;
    private Npcs npcs;

    Pago pago() { return pago; }
    Catalogo catalogo() { return catalogo; }
    Rarezas rarezas() { return rarezas; }
    Perfiles perfiles() { return perfiles; }
    Iconos iconos() { return iconos; }
    Colocados colocados() { return colocados; }
    Balance balance() { return balance; }
    Recompensas recompensas() { return recompensas; }
    private final Insignias insignias = new Insignias();
    Insignias insignias() { return insignias; }
    Servicio servicio() { return servicio; }
    Menus menus() { return menus; }
    Misiones misiones() { return misiones; }
    Npcs npcs() { return npcs; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "acciones.yml").exists()) saveResource("acciones.yml", false);
        pago = new Pago(this);
        if (!pago.iniciar()) {
            getLogger().severe("No encontré una economía de Vault. Apago el plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        catalogo = new Catalogo(this);
        catalogo.cargar();
        rarezas = new Rarezas(this);
        rarezas.cargar();
        perfiles = new Perfiles(this);
        misiones = new Misiones(this);
        misiones.cargar();
        pago.cargarNiveles();
        recompensas = new Recompensas(this);
        recompensas.cargar();
        iconos = new Iconos(this);
        colocados = new Colocados(this);
        balance = new Balance(this);
        balance.cargar();
        servicio = new Servicio(this);
        menus = new Menus(this);

        Escucha escucha = new Escucha(this);
        Bukkit.getPluginManager().registerEvents(escucha, this);
        Bukkit.getPluginManager().registerEvents(colocados, this);
        Bukkit.getPluginManager().registerEvents(iconos, this);
        Bukkit.getPluginManager().registerEvents(menus, this);
        npcs = new Npcs(this);
        Bukkit.getPluginManager().registerEvents(npcs, this);

        Comandos cmd = new Comandos(this);
        for (String nombre : new String[]{"trabajos", "trabajosadmin", "insignia"}) {
            PluginCommand pc = getCommand(nombre);
            if (pc != null) { pc.setExecutor(cmd); pc.setTabCompleter(cmd); }
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) { new TrabajosExpansion(this).register(); getLogger().info("Placeholder %trabajos_insignia% registrado."); }

        Bukkit.getScheduler().runTaskTimer(this, () -> perfiles.guardarTodo(), 6000L, 6000L);
        Bukkit.getScheduler().runTaskTimer(this, () -> rarezas.revisar(), 400L, 400L);
        Bukkit.getScheduler().runTaskTimer(this, () -> { misiones.revisar(); misiones.guardarSiHaceFalta(); }, 600L, 600L);
        Bukkit.getScheduler().runTaskTimer(this, () -> pago.porTiempo(), 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(this, escucha::muestrear, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, () -> balance.guardarSiHaceFalta(), 6000L, 6000L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                Perfil f = perfiles.existente(p);
                if (f != null) pago.vaciar(p, f);
            }
        }, 100L, 100L);
        getLogger().info("3y33Trabajos listo: " + catalogo.todas().size() + " acciones en 5 trabajos.");
    }

    @Override
    public void onDisable() {
        if (perfiles != null && pago != null) {
            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                Perfil f = perfiles.existente(p);
                if (f != null) pago.vaciar(p, f);
            }
        }
        if (colocados != null) colocados.volcarTodo();
        if (balance != null) balance.guardar();
        if (perfiles != null) { perfiles.guardarTodo(); perfiles.cerrar(); }
        if (rarezas != null) rarezas.guardar();
        if (misiones != null) misiones.guardar();
        if (npcs != null) npcs.apagar();
    }
}
