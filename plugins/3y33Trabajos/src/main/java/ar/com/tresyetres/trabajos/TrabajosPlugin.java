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
        iconos = new Iconos(this);
        colocados = new Colocados(this);
        servicio = new Servicio(this);
        menus = new Menus(this);

        Bukkit.getPluginManager().registerEvents(new Escucha(this), this);
        Bukkit.getPluginManager().registerEvents(iconos, this);
        Bukkit.getPluginManager().registerEvents(menus, this);
        npcs = new Npcs(this);
        Bukkit.getPluginManager().registerEvents(npcs, this);

        Comandos cmd = new Comandos(this);
        for (String nombre : new String[]{"trabajos", "trabajosadmin"}) {
            PluginCommand pc = getCommand(nombre);
            if (pc != null) { pc.setExecutor(cmd); pc.setTabCompleter(cmd); }
        }

        Bukkit.getScheduler().runTaskTimer(this, () -> perfiles.guardarTodo(), 6000L, 6000L);
        Bukkit.getScheduler().runTaskTimer(this, () -> rarezas.revisar(), 400L, 400L);
        Bukkit.getScheduler().runTaskTimer(this, () -> { misiones.revisar(); misiones.guardarSiHaceFalta(); }, 600L, 600L);
        Bukkit.getScheduler().runTaskTimer(this, () -> pago.porTiempo(), 1200L, 1200L);
        getLogger().info("3y33Trabajos listo: " + catalogo.todas().size() + " acciones en 5 trabajos.");
    }

    @Override
    public void onDisable() {
        if (perfiles != null) perfiles.guardarTodo();
        if (rarezas != null) rarezas.guardar();
        if (misiones != null) misiones.guardar();
        if (npcs != null) npcs.apagar();
    }
}
