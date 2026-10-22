package ar.com.tresyetres.tierras;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.ayosynk.landClaimPlugin.api.LandClaimAPI;
import org.ayosynk.landClaimPlugin.models.ClaimProfile;
import org.ayosynk.landClaimPlugin.models.Warp;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.*;

public final class Tierras extends JavaPlugin implements Listener, TabCompleter {

    private LandClaimAPI api;
    private Economy economy;

    private File dataFile;
    private YamlConfiguration data;

    // Teletransporte: cooldowns, esperas en curso y teletransportes ya autorizados
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, BukkitTask> esperas = new HashMap<>();
    private final Map<UUID, Location> origenEspera = new HashMap<>();
    private final Set<UUID> autorizados = new HashSet<>();

    // Confirmación de compra (jugador -> cantidad, vence a los 15 s)
    private final Map<UUID, long[]> compraPendiente = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        api = LandClaimAPI.getInstance();
        dataFile = new File(getDataFolder(), "terrenos.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);

        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }
        if (economy == null) getLogger().warning("No se encontró economía de Vault: /tierra comprar no va a funcionar.");

        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("tierra")).setExecutor(this);
        Objects.requireNonNull(getCommand("tierra")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("tierras")).setExecutor(this);
        Objects.requireNonNull(getCommand("tierrasadmin")).setExecutor(this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new TierrasExpansion(this).register();
            getLogger().info("Placeholders %tierras_...% registrados.");
        }
    }

    @Override
    public void onDisable() {
        esperas.values().forEach(BukkitTask::cancel);
        guardarDatos();
    }

    // ------------------------------------------------------------------ utilidades

    String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    void msg(CommandSender s, String texto) {
        s.sendMessage(color(getConfig().getString("prefijo", "&4&l3y33 &8» ") + texto));
    }

    String plata(double v) {
        NumberFormat nf = NumberFormat.getIntegerInstance(new Locale("es", "AR"));
        return "$" + nf.format(Math.round(v));
    }

    LandClaimAPI api() {
        return api;
    }

    private void guardarDatos() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("No se pudo guardar terrenos.yml: " + e.getMessage());
        }
    }

    boolean monstruos(ClaimProfile p) {
        return data.getBoolean("terrenos." + p.getProfileId() + ".monstruos", getConfig().getBoolean("mobs-por-defecto.monstruos", true));
    }

    boolean animales(ClaimProfile p) {
        return data.getBoolean("terrenos." + p.getProfileId() + ".animales", getConfig().getBoolean("mobs-por-defecto.animales", true));
    }

    int comprados(UUID id) {
        return data.getInt("jugadores." + id + ".comprados", 0);
    }

    double precioSiguiente(UUID id) {
        return getConfig().getDouble("compra.base", 500) + getConfig().getDouble("compra.incremento", 250) * comprados(id);
    }

    double precioDe(UUID id, int cantidad) {
        double total = 0;
        int ya = comprados(id);
        double base = getConfig().getDouble("compra.base", 500);
        double inc = getConfig().getDouble("compra.incremento", 250);
        for (int i = 0; i < cantidad; i++) total += base + inc * (ya + i);
        return total;
    }

    int maxTerrenos(Player p) {
        if (p.hasPermission("tierras.admin")) return 100;
        int max = getConfig().getInt("terrenos.por-defecto", 2);
        for (PermissionAttachmentInfo info : p.getEffectivePermissions()) {
            String perm = info.getPermission();
            if (info.getValue() && perm.startsWith("tierras.terrenos.")) {
                try {
                    max = Math.max(max, Integer.parseInt(perm.substring("tierras.terrenos.".length())));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return max;
    }

    int terrenosDe(UUID id) {
        return api.getClaimsByOwner(id).size();
    }

    private boolean puedeAdministrar(Player p, ClaimProfile perfil) {
        return perfil.isOwner(p.getUniqueId()) || p.hasPermission("landclaim.admin")
                || api.hasPermission(perfil, p.getUniqueId(), "MANAGE_SETTINGS");
    }

    // ------------------------------------------------------------------ comandos

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String nombre = cmd.getName().toLowerCase(Locale.ROOT);
        if (nombre.equals("tierrasadmin")) return comandoAdmin(sender, args);
        if (!(sender instanceof Player p)) {
            msg(sender, "&cSolo jugadores.");
            return true;
        }
        if (nombre.equals("tierras")) {
            p.performCommand("claim profiles");
            return true;
        }
        if (args.length == 0) {
            p.performCommand("claim");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String resto = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        switch (sub) {
            case "comprar" -> comprar(p, args);
            case "info", "chunks" -> info(p);
            case "crear" -> {
                if (args.length < 2) {
                    msg(p, "&cUsá &f/tierra crear <nombre>");
                    return true;
                }
                p.performCommand("claim create " + resto);
            }
            case "punto" -> {
                if (args.length < 2) {
                    msg(p, "&cUsá &f/tierra punto <nombre> &7(parado donde querés el punto)");
                    return true;
                }
                p.performCommand("claim setwarp " + resto);
            }
            case "ir", "tp" -> {
                if (args.length < 2) {
                    p.performCommand("claim warps");
                    return true;
                }
                p.performCommand("claim warp " + resto);
            }
            case "mobs", "monstruos" -> alternarMobs(p, true);
            case "animales" -> alternarMobs(p, false);
            case "menu", "menú" -> p.performCommand("claim menu");
            case "lista", "terrenos" -> p.performCommand("claim profiles");
            case "borrar", "liberar" -> p.performCommand("unclaim");
            case "mapa" -> p.performCommand("claim map");
            case "ayuda", "help" -> ayuda(p);
            default -> p.performCommand("claim " + String.join(" ", args));
        }
        return true;
    }

    private void ayuda(Player p) {
        p.sendMessage(color("&8&m                                        "));
        p.sendMessage(color("&4&l3y33 &8» &fTerrenos"));
        p.sendMessage(color("&e/tierra &8» &7reclama el chunk donde estás"));
        p.sendMessage(color("&e/tierras &8» &7tus terrenos (elegí en cuál sumar chunks)"));
        p.sendMessage(color("&e/tierra menu &8» &7miembros, permisos y ajustes"));
        p.sendMessage(color("&e/tierra crear <nombre> &8» &7nuevo terreno (ej. una granja)"));
        p.sendMessage(color("&e/tierra punto <nombre> &8» &7guarda un punto de teletransporte"));
        p.sendMessage(color("&e/tierra ir <nombre> &8» &7te lleva a un punto"));
        p.sendMessage(color("&e/tierra comprar [cantidad] &8» &7comprá chunks extra"));
        p.sendMessage(color("&e/tierra mobs &8/ &eanimales &8» &7apaga o prende los mobs"));
        p.sendMessage(color("&e/tierra info &8» &7tus chunks y terrenos"));
        p.sendMessage(color("&e/tierra liberar &8» &7libera el chunk donde estás"));
        p.sendMessage(color("&8&m                                        "));
    }

    private void info(Player p) {
        UUID id = p.getUniqueId();
        int usados = api.getTotalChunksByOwner(id);
        int limite = api.getClaimLimit(p);
        p.sendMessage(color("&8&m                                        "));
        p.sendMessage(color("&4&l3y33 &8» &fTus terrenos"));
        p.sendMessage(color("&7Chunks: &a" + usados + "&8/&a" + limite + " &8(&7te quedan &f" + Math.max(0, limite - usados) + "&8)"));
        p.sendMessage(color("&7Terrenos: &a" + terrenosDe(id) + "&8/&a" + maxTerrenos(p)));
        p.sendMessage(color("&7Chunks comprados: &f" + comprados(id) + " &8· &7el próximo cuesta &6" + plata(precioSiguiente(id))));
        ClaimProfile aca = api.getClaimAt(p.getLocation());
        if (aca != null) {
            p.sendMessage(color("&7Acá: &e" + aca.getName() + " &8· &7monstruos " + (monstruos(aca) ? "&así" : "&cno")
                    + " &8· &7animales " + (animales(aca) ? "&así" : "&cno")));
        }
        p.sendMessage(color("&8&m                                        "));
    }

    private void comprar(Player p, String[] args) {
        if (!getConfig().getBoolean("compra.habilitada", true)) {
            msg(p, "&cLa compra de chunks está desactivada.");
            return;
        }
        if (economy == null) {
            msg(p, "&cLa economía no está disponible, avisale a un admin.");
            return;
        }
        UUID id = p.getUniqueId();
        boolean confirmar = args.length >= 2 && args[args.length - 1].equalsIgnoreCase("confirmar");
        int cantidad = 1;
        if (args.length >= 2 && !args[1].equalsIgnoreCase("confirmar")) {
            try {
                cantidad = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                msg(p, "&cUsá &f/tierra comprar [cantidad]");
                return;
            }
        }
        if (cantidad < 1 || cantidad > 64) {
            msg(p, "&cPodés comprar entre 1 y 64 chunks por vez.");
            return;
        }
        int max = getConfig().getInt("compra.maximo-comprados", 200);
        if (comprados(id) + cantidad > max) {
            msg(p, "&cLlegaste al máximo de chunks comprados (&f" + max + "&c).");
            return;
        }
        double total = precioDe(id, cantidad);
        long[] pendiente = compraPendiente.get(id);
        boolean vigente = pendiente != null && pendiente[0] == cantidad && System.currentTimeMillis() < pendiente[1];
        if (!confirmar || !vigente) {
            compraPendiente.put(id, new long[]{cantidad, System.currentTimeMillis() + 15_000});
            msg(p, "&f" + cantidad + (cantidad == 1 ? " chunk" : " chunks") + " &7cuesta" + (cantidad == 1 ? "" : "n") + " &6" + plata(total)
                    + "&7. Tenés &6" + plata(economy.getBalance(p)) + "&7.");
            String comando = "/tierra comprar " + cantidad + " confirmar";
            net.md_5.bungee.api.chat.TextComponent boton = new net.md_5.bungee.api.chat.TextComponent(color("  &a&l[CONFIRMAR]"));
            boton.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, comando));
            p.spigot().sendMessage(boton);
            p.sendMessage(color("  &7o escribí &f" + comando + " &7(15 segundos)"));
            return;
        }
        compraPendiente.remove(id);
        if (economy.getBalance(p) < total) {
            msg(p, "&cNo te alcanza: te faltan &6" + plata(total - economy.getBalance(p)) + "&c.");
            return;
        }
        EconomyResponse r = economy.withdrawPlayer(p, total);
        if (!r.transactionSuccess()) {
            msg(p, "&cNo se pudo cobrar: " + r.errorMessage);
            return;
        }
        final int cant = cantidad;
        data.set("jugadores." + id + ".comprados", comprados(id) + cant);
        guardarDatos();
        api.addBonusBlocks(id, cant).thenAccept(nuevo -> Bukkit.getScheduler().runTask(this, () -> {
            msg(p, "&a¡Compraste " + cant + (cant == 1 ? " chunk" : " chunks") + "! &7Ahora podés tener &f" + api.getClaimLimit(p)
                    + " &7chunks. El próximo cuesta &6" + plata(precioSiguiente(id)) + "&7.");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        }));
    }

    private void alternarMobs(Player p, boolean monstruos) {
        ClaimProfile perfil = api.getClaimAt(p.getLocation());
        if (perfil == null) {
            msg(p, "&cTenés que estar parado en tu terreno.");
            return;
        }
        if (!puedeAdministrar(p, perfil)) {
            msg(p, "&cNo podés cambiar esto en este terreno.");
            return;
        }
        String clave = "terrenos." + perfil.getProfileId() + (monstruos ? ".monstruos" : ".animales");
        boolean nuevo = !(monstruos ? monstruos(perfil) : animales(perfil));
        data.set(clave, nuevo);
        guardarDatos();
        msg(p, "&7" + (monstruos ? "Monstruos" : "Animales") + " en &e" + perfil.getName() + "&7: "
                + (nuevo ? "&aaparecen" : "&cno aparecen más") + "&7.");
    }

    private boolean comandoAdmin(CommandSender s, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("recargar")) {
            reloadConfig();
            msg(s, "&aConfiguración recargada.");
            return true;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("comprados")) {
            UUID id = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
            try {
                data.set("jugadores." + id + ".comprados", Math.max(0, Integer.parseInt(args[2])));
                guardarDatos();
                msg(s, "&aChunks comprados de &f" + args[1] + "&a: &f" + args[2] + " &7(solo cambia el precio, no el límite).");
            } catch (NumberFormatException e) {
                msg(s, "&cNúmero inválido.");
            }
            return true;
        }
        msg(s, "&7/tierrasadmin recargar &8· &7/tierrasadmin comprados <jugador> <n>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("tierra")) return List.of();
        if (args.length == 1) {
            List<String> subs = List.of("comprar", "info", "crear", "punto", "ir", "mobs", "animales", "menu", "liberar", "mapa", "ayuda");
            String a = args[0].toLowerCase(Locale.ROOT);
            return subs.stream().filter(x -> x.startsWith(a)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("ir") && sender instanceof Player p) {
            ClaimProfile perfil = api.getClaimAt(p.getLocation());
            List<String> out = new ArrayList<>();
            for (ClaimProfile c : api.getClaimsByOwner(p.getUniqueId())) out.addAll(api.getWarps(c.getProfileId()).keySet());
            return out;
        }
        return List.of();
    }

    // ------------------------------------------------------------------ eventos

    // Límite de terrenos por rango (tierras.terrenos.N)
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void alCrearTerreno(PlayerCommandPreprocessEvent e) {
        String[] partes = e.getMessage().trim().split("\\s+");
        if (partes.length < 2) return;
        String base = partes[0].toLowerCase(Locale.ROOT);
        if (base.contains(":")) base = "/" + base.substring(base.indexOf(':') + 1);
        if (!(base.equals("/claim") || base.equals("/c"))) return;
        if (!partes[1].equalsIgnoreCase("create")) return;
        Player p = e.getPlayer();
        int max = maxTerrenos(p);
        if (terrenosDe(p.getUniqueId()) >= max) {
            e.setCancelled(true);
            msg(p, "&cYa tenés el máximo de terrenos de tu rango (&f" + max + "&c).");
        }
    }

    // Monstruos y animales por terreno
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alAparecer(CreatureSpawnEvent e) {
        CreatureSpawnEvent.SpawnReason r = e.getSpawnReason();
        boolean natural = r == CreatureSpawnEvent.SpawnReason.NATURAL || r == CreatureSpawnEvent.SpawnReason.REINFORCEMENTS
                || r == CreatureSpawnEvent.SpawnReason.PATROL || r == CreatureSpawnEvent.SpawnReason.JOCKEY
                || r == CreatureSpawnEvent.SpawnReason.MOUNT || r == CreatureSpawnEvent.SpawnReason.CHUNK_GEN;
        if (!natural) return;
        boolean esMonstruo = e.getEntity() instanceof Monster;
        boolean esAnimal = e.getEntity() instanceof Animals;
        if (!esMonstruo && !esAnimal) return;
        ClaimProfile perfil = api.getClaimAt(e.getLocation());
        if (perfil == null) return;
        if (esMonstruo && !monstruos(perfil)) e.setCancelled(true);
        else if (esAnimal && !animales(perfil)) e.setCancelled(true);
    }

    // Espera y cooldown al ir a los puntos de los terrenos
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alTeletransportar(PlayerTeleportEvent e) {
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) return;
        Player p = e.getPlayer();
        if (autorizados.remove(p.getUniqueId())) return;
        Location destino = e.getTo();
        if (destino == null || !esPunto(destino)) return;
        if (p.hasPermission("tierras.bypass.cooldown")) return;

        e.setCancelled(true);
        UUID id = p.getUniqueId();
        long ahora = System.currentTimeMillis();
        long hasta = cooldowns.getOrDefault(id, 0L);
        if (ahora < hasta) {
            msg(p, "&cEsperá &f" + ((hasta - ahora + 999) / 1000) + "s &cpara volver a teletransportarte.");
            return;
        }
        if (esperas.containsKey(id)) {
            msg(p, "&7Ya te estás teletransportando...");
            return;
        }
        int espera = getConfig().getInt("teletransporte.espera-segundos", 3);
        Location destinoFinal = destino.clone();
        if (espera <= 0) {
            ejecutarTp(p, destinoFinal);
            return;
        }
        origenEspera.put(id, p.getLocation().clone());
        p.sendTitle(color("&e" + espera), color("&7No te muevas..."), 0, 25, 5);
        final int[] restante = {espera};
        BukkitTask t = Bukkit.getScheduler().runTaskTimer(this, () -> {
            restante[0]--;
            if (!p.isOnline()) {
                cancelarEspera(id);
                return;
            }
            if (restante[0] <= 0) {
                cancelarEspera(id);
                ejecutarTp(p, destinoFinal);
            } else {
                p.sendTitle(color("&e" + restante[0]), color("&7No te muevas..."), 0, 25, 5);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.2f);
            }
        }, 20L, 20L);
        esperas.put(id, t);
    }

    private void ejecutarTp(Player p, Location destino) {
        autorizados.add(p.getUniqueId());
        cooldowns.put(p.getUniqueId(), System.currentTimeMillis() + getConfig().getInt("teletransporte.cooldown-segundos", 30) * 1000L);
        p.teleportAsync(destino).thenAccept(ok -> {
            autorizados.remove(p.getUniqueId());
            if (ok) {
                p.sendTitle("", color("&aLlegaste"), 0, 20, 10);
                p.playSound(destino, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.2f);
            }
        });
    }

    private void cancelarEspera(UUID id) {
        BukkitTask t = esperas.remove(id);
        if (t != null) t.cancel();
        origenEspera.remove(id);
    }

    private boolean esPunto(Location destino) {
        ClaimProfile perfil = api.getClaimAt(destino);
        if (perfil == null) return false;
        Map<String, Warp> warps = api.getWarps(perfil.getProfileId());
        if (warps == null) return false;
        for (Warp w : warps.values()) {
            Location l = w.getLocation();
            if (l != null && l.getWorld() != null && l.getWorld().equals(destino.getWorld()) && l.distanceSquared(destino) < 0.01) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void alMoverse(PlayerMoveEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        Location origen = origenEspera.get(id);
        if (origen == null || e.getTo() == null) return;
        if (origen.getWorld() != e.getTo().getWorld() || origen.distanceSquared(e.getTo()) > 0.25) {
            cancelarEspera(id);
            e.getPlayer().sendTitle("", color("&cTeletransporte cancelado"), 0, 25, 10);
        }
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent e) {
        cancelarEspera(e.getPlayer().getUniqueId());
        compraPendiente.remove(e.getPlayer().getUniqueId());
    }
}
