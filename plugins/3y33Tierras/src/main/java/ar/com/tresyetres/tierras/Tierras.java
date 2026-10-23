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
    private MenusTerrenos menus;
    private final Map<UUID, java.util.function.Consumer<String>> esperandoTexto = new HashMap<>();
    private final Set<UUID> conPack = new HashSet<>();
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
        menus = new MenusTerrenos(this);
        Stickers.cargar(this);
        dataFile = new File(getDataFolder(), "terrenos.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);

        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }
        if (economy == null) getLogger().warning("No se encontró economía de Vault: /tierra comprar no va a funcionar.");

        getServer().getPluginManager().registerEvents(this, this);
        // Cada 3 segundos saca los monstruos naturales que entraron caminando a terrenos con monstruos apagados
        getServer().getScheduler().runTaskTimer(this, this::limpiarMonstruos, 60L, 60L);
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

    boolean flag(ClaimProfile p, Flag f) {
        return data.getBoolean("terrenos." + p.getProfileId() + "." + f.clave, getConfig().getBoolean("flags-por-defecto." + f.clave));
    }

    void setFlag(ClaimProfile p, Flag f, boolean v) {
        data.set("terrenos." + p.getProfileId() + "." + f.clave, v);
        guardarDatos();
    }

    boolean monstruos(ClaimProfile p) {
        return flag(p, Flag.MONSTRUOS);
    }

    boolean animales(ClaimProfile p) {
        return flag(p, Flag.ANIMALES);
    }

    void abrirFlags(Player p) {
        ClaimProfile perfil = api.getClaimAt(p.getLocation());
        if (perfil == null) {
            msg(p, "&cParate dentro de tu terreno para ver sus opciones.");
            return;
        }
        if (!puedeAdministrar(p, perfil)) {
            msg(p, "&cNo podés cambiar las opciones de este terreno.");
            return;
        }
        p.openInventory(new MenuFlags(this, perfil).getInventory());
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
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

    /** Máximo de chunks por terreno: el cupo total repartido en partes iguales entre los terrenos permitidos. */
    int maxPorTerreno(Player p) {
        int limite = api.getClaimLimit(p);
        if (limite >= 1_000_000) return limite;
        return Math.max(1, limite / Math.max(1, maxTerrenos(p)));
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
        if (nombre.equals("tierras") && args.length == 0) {
            menus.abrirHub(p);
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
            case "flags", "naturaleza", "opciones" -> abrirFlags(p);
            case "stickers", "sticker", "emojis" -> listarStickers(p);
            case "saludo", "mensaje" -> {
                ClaimProfile aca = api.getClaimAt(p.getLocation());
                if (aca == null || !puedeAdministrar(p, aca)) {
                    msg(p, "&cParate dentro de tu terreno para cambiar su mensaje de entrada.");
                } else if (args.length >= 2) {
                    String limpio = limpiarSaludo(p, resto);
                    if (limpio == null) return true;
                    if (limpio.equalsIgnoreCase("borrar")) data.set("terrenos." + aca.getProfileId() + ".saludo", null);
                    else data.set("terrenos." + aca.getProfileId() + ".saludo", limpio);
                    guardarDatos();
                    mostrarTitulo(p, aca);
                } else pedirSaludo(p, aca);
            }
            case "mobs", "monstruos" -> alternarMobs(p, true);
            case "animales" -> alternarMobs(p, false);
            case "menu", "menú" -> {
                ClaimProfile aca = api.getClaimAt(p.getLocation());
                if (aca != null && (aca.isOwner(p.getUniqueId()) || aca.isMember(p.getUniqueId()))) menus.abrirPanel(p, aca);
                else menus.abrirHub(p);
            }
            case "lista", "terrenos" -> menus.abrirHub(p);
            case "comprarmenu", "tienda" -> menus.abrirCompra(p);
            case "borrar", "liberar" -> p.performCommand("unclaim");
            case "mapa" -> p.performCommand("claim map");
            case "ayuda", "help" -> ayuda(p);
            default -> {
                if (SUBS_LCP.contains(sub)) p.performCommand("claim " + String.join(" ", args));
                else ayuda(p);
            }
        }
        return true;
    }

    private void ayuda(Player p) {
        p.sendMessage(color("&8&m                                        "));
        p.sendMessage(color("&4&l3y33 &8» &fTerrenos"));
        p.sendMessage(color("&e/tierra &8» &7reclama el chunk donde estás"));
        p.sendMessage(color("&e/tierras &8» &7menú principal: tus terrenos, chunks y opciones"));
        p.sendMessage(color("&e/tierra menu &8» &7miembros, permisos y ajustes"));
        p.sendMessage(color("&e/tierra crear <nombre> &8» &7nuevo terreno (ej. una granja)"));
        p.sendMessage(color("&e/tierra punto <nombre> &8» &7guarda un punto de teletransporte"));
        p.sendMessage(color("&e/tierra ir <nombre> &8» &7te lleva a un punto"));
        p.sendMessage(color("&e/tierra comprar [cantidad] &8» &7comprá chunks extra"));
        p.sendMessage(color("&e/tierra flags &8» &7mobs, fuego, líquidos y más del terreno"));
        p.sendMessage(color("&e/tierra saludo <texto> &8» &7mensaje que ven al entrar a tu terreno"));
        p.sendMessage(color("&e/tierra stickers &8» &7íconos para usar en tu mensaje"));
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
        p.sendMessage(color("&7Terrenos: &a" + terrenosDe(id) + "&8/&a" + maxTerrenos(p) + " &8(&7hasta &f" + maxPorTerreno(p) + " &7chunks cada uno&8)"));
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

    double saldo(Player p) {
        return economy == null ? 0 : economy.getBalance(p);
    }

    /** Compra desde el menú (el click ya es la confirmación). */
    void comprarDirecto(Player p, int cantidad) {
        if (!getConfig().getBoolean("compra.habilitada", true) || economy == null) {
            msg(p, "&cLa compra de chunks no está disponible.");
            return;
        }
        UUID id = p.getUniqueId();
        int max = getConfig().getInt("compra.maximo-comprados", 200);
        if (comprados(id) + cantidad > max) {
            msg(p, "&cLlegaste al máximo de chunks comprados (&f" + max + "&c).");
            return;
        }
        double total = precioDe(id, cantidad);
        if (economy.getBalance(p) < total) {
            msg(p, "&cNo te alcanza: te faltan &6" + plata(total - economy.getBalance(p)) + "&c.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            return;
        }
        EconomyResponse r = economy.withdrawPlayer(p, total);
        if (!r.transactionSuccess()) {
            msg(p, "&cNo se pudo cobrar: " + r.errorMessage);
            return;
        }
        data.set("jugadores." + id + ".comprados", comprados(id) + cantidad);
        guardarDatos();
        api.addBonusBlocks(id, cantidad);
        msg(p, "&a¡Compraste " + cantidad + (cantidad == 1 ? " chunk" : " chunks") + " por &6" + plata(total) + "&a!");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
    }

    void pedirNombre(Player p) {
        if (terrenosDe(p.getUniqueId()) >= maxTerrenos(p)) {
            msg(p, "&cYa tenés el máximo de terrenos de tu rango (&f" + maxTerrenos(p) + "&c).");
            return;
        }
        pedirTexto(p, "Nuevo terreno", "el nombre del terreno", nombre -> {
            if (!nombre.matches("[A-Za-z0-9ÁÉÍÓÚáéíóúÑñ ]{3,32}")) {
                msg(p, "&cEl nombre tiene que tener entre 3 y 32 letras o números.");
                return;
            }
            p.performCommand("claim create " + nombre);
        });
    }

    void pedirTexto(Player p, String titulo, String que, java.util.function.Consumer<String> alRecibir) {
        esperandoTexto.put(p.getUniqueId(), alRecibir);
        p.sendTitle(color("&a&l" + titulo), color("&7Escribí " + que + " en el chat"), 5, 60, 10);
        msg(p, "&7Escribí " + que + " en el chat &8(&7o &fcancelar&8)&7.");
    }

    /** Limpia el mensaje de un jugador: sin etiquetas ni colores, hasta 60 caracteres y 4 stickers. */
    String limpiarSaludo(Player p, String txt) {
        String limpio = txt.replaceAll("[<>&§\\\\]", "").trim();
        if (limpio.length() > 60) limpio = limpio.substring(0, 60);
        if (Stickers.contar(limpio) > 4) {
            msg(p, "&cMáximo 4 stickers por mensaje.");
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(":([a-z]+):").matcher(limpio);
        while (m.find()) {
            if (Stickers.TODOS.containsKey(m.group(1)) && !puedeSticker(p, m.group(1))) {
                msg(p, "&cEl sticker &f:" + m.group(1) + ": &ces para " + rangoSticker(m.group(1)) + "&c.");
                return null;
            }
        }
        return limpio;
    }

    /** Rango mínimo para usar un sticker ("" = todos). */
    String rangoSticker(String nombre) {
        String r = getConfig().getString("stickers-por-rango." + nombre, "");
        return r == null || r.isBlank() ? "" : r.substring(0, 1).toUpperCase() + r.substring(1) + " o más";
    }

    boolean puedeSticker(Player p, String nombre) {
        String r = getConfig().getString("stickers-por-rango." + nombre, "");
        return r == null || r.isBlank() || p.hasPermission("tierras.stickers." + r) || p.hasPermission("tierras.admin");
    }

    String icono(ClaimProfile c) {
        return data.getString("terrenos." + c.getProfileId() + ".icono", "");
    }

    void setIcono(ClaimProfile c, String nombre) {
        data.set("terrenos." + c.getProfileId() + ".icono", nombre == null || nombre.isEmpty() ? null : nombre);
        guardarDatos();
    }

    boolean tienePack(Player p) {
        return conPack.contains(p.getUniqueId());
    }

    void listarStickers(Player p) {
        boolean pack = tienePack(p);
        p.sendMessage(color("&8&m                                        "));
        p.sendMessage(color("&4&l3y33 &8» &fStickers &7(click para usarlo en tu mensaje)"));
        net.kyori.adventure.text.Component linea = net.kyori.adventure.text.Component.empty();
        int i = 0;
        for (Stickers.Sticker st : Stickers.TODOS.values()) {
            boolean puede = puedeSticker(p, st.nombre());
            String mm = Stickers.dibujar(st, pack, false) + (puede ? " <gray>:" : " <dark_gray>:") + st.nombre() + ":   ";
            linea = linea.append(Menu.MM.deserialize(mm)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand("/tierra saludo :" + st.nombre() + ": "))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Menu.MM.deserialize(puede ? "<yellow>Click para escribirlo"
                            : "<red>Solo para " + rangoSticker(st.nombre())))));
            if (++i % 4 == 0) {
                p.sendMessage(linea);
                linea = net.kyori.adventure.text.Component.empty();
            }
        }
        if (i % 4 != 0) p.sendMessage(linea);
        p.sendMessage(color("&7Ejemplo: &f/tierra saludo :corona: Bienvenidos :corona:"));
        p.sendMessage(color("&8&m                                        "));
    }

    String saludo(ClaimProfile c) {
        return data.getString("terrenos." + c.getProfileId() + ".saludo", "");
    }

    void pedirSaludo(Player p, ClaimProfile c) {
        pedirTexto(p, "Mensaje de entrada", "el mensaje que ven al entrar (o &fborrar&7)", txt -> {
            if (txt.equalsIgnoreCase("borrar")) {
                data.set("terrenos." + c.getProfileId() + ".saludo", null);
                guardarDatos();
                msg(p, "&7Mensaje de entrada borrado.");
                return;
            }
            String limpio = limpiarSaludo(p, txt);
            if (limpio == null) return;
            data.set("terrenos." + c.getProfileId() + ".saludo", limpio);
            guardarDatos();
            msg(p, "&aListo. Al entrar a &e" + c.getName() + " &ase va a ver: &f" + limpio);
            mostrarTitulo(p, c);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    @SuppressWarnings("deprecation")
    public void alEscribir(org.bukkit.event.player.AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        var accion = esperandoTexto.remove(p.getUniqueId());
        if (accion == null) return;
        e.setCancelled(true);
        String texto = e.getMessage().trim();
        Bukkit.getScheduler().runTask(this, () -> {
            if (texto.equalsIgnoreCase("cancelar")) {
                msg(p, "&7Cancelado.");
                return;
            }
            accion.accept(texto);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void alClickMenuGenerico(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getInventory()) return;
        var accion = menu.acciones.get(e.getRawSlot());
        if (accion == null) return;
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.3f);
        Bukkit.getScheduler().runTask(this, () -> accion.accept(p, e.getClick()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void alArrastrarMenuGenerico(org.bukkit.event.inventory.InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) e.setCancelled(true);
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
        Flag f = monstruos ? Flag.MONSTRUOS : Flag.ANIMALES;
        boolean nuevo = !flag(perfil, f);
        setFlag(perfil, f, nuevo);
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
            List<String> subs = List.of("comprar", "info", "crear", "punto", "ir", "flags", "saludo", "stickers", "mobs", "animales", "menu", "liberar", "mapa", "ayuda");
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
        if (!NATURALES.contains(r) && !HUEVOS.contains(r)) return;
        Flag f;
        if (e.getEntity() instanceof org.bukkit.entity.Phantom) f = Flag.PHANTOMS;
        else if (e.getEntity() instanceof org.bukkit.entity.Enemy) f = Flag.MONSTRUOS;
        else if (e.getEntity() instanceof Animals) f = Flag.ANIMALES;
        else return;
        ClaimProfile perfil = api.getClaimAt(e.getLocation());
        if (perfil != null && !flag(perfil, f)) e.setCancelled(true);
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
        iniciarTp(p, destino);
    }

    /** Teletransporte con cooldown y espera sin moverse. */
    void iniciarTp(Player p, Location destino) {
        UUID id = p.getUniqueId();
        if (p.hasPermission("tierras.bypass.cooldown")) {
            ejecutarTp(p, destino.clone());
            return;
        }
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

    static final Set<CreatureSpawnEvent.SpawnReason> NATURALES = EnumSet.of(
            CreatureSpawnEvent.SpawnReason.NATURAL, CreatureSpawnEvent.SpawnReason.REINFORCEMENTS,
            CreatureSpawnEvent.SpawnReason.PATROL, CreatureSpawnEvent.SpawnReason.JOCKEY,
            CreatureSpawnEvent.SpawnReason.MOUNT, CreatureSpawnEvent.SpawnReason.CHUNK_GEN,
            CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION, CreatureSpawnEvent.SpawnReason.RAID);
    static final Set<CreatureSpawnEvent.SpawnReason> HUEVOS = EnumSet.of(
            CreatureSpawnEvent.SpawnReason.SPAWNER_EGG, CreatureSpawnEvent.SpawnReason.DISPENSE_EGG);
    static final Set<String> SUBS_LCP = Set.of("radius", "profiles", "create", "auto", "map", "minimap", "menu", "info",
            "visible", "toggle", "visualization", "notify", "chatnotify", "rename", "color", "unstuck", "abandon", "pvp",
            "member", "trust", "ally", "accept", "deny", "setwarp", "delwarp", "warp", "warps", "publicwarps", "ban",
            "unban", "banlist", "leave", "admin", "reload");

    private void limpiarMonstruos() {
        for (org.bukkit.World w : getServer().getWorlds()) {
            for (org.bukkit.entity.LivingEntity le : w.getLivingEntities()) {
                if (!(le instanceof org.bukkit.entity.Enemy) || le.customName() != null || !le.getRemoveWhenFarAway()) continue;
                if (!NATURALES.contains(le.getEntitySpawnReason())) continue;
                ClaimProfile perfil = api.getClaimAt(le.getLocation());
                if (perfil == null) continue;
                Flag f = le instanceof org.bukkit.entity.Phantom ? Flag.PHANTOMS : Flag.MONSTRUOS;
                if (!flag(perfil, f)) le.remove();
            }
        }
    }

    // ---------------- títulos al entrar y salir de un terreno
    private net.kyori.adventure.title.Title.Times tiempos() {
        return net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(getConfig().getLong("titulos.aparecer-ms", 400)),
                java.time.Duration.ofMillis(getConfig().getLong("titulos.duracion-ms", 3500)),
                java.time.Duration.ofMillis(getConfig().getLong("titulos.desaparecer-ms", 900)));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alEstadoPack(org.bukkit.event.player.PlayerResourcePackStatusEvent e) {
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> conPack.add(e.getPlayer().getUniqueId());
            case DECLINED, FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL, DISCARDED -> conPack.remove(e.getPlayer().getUniqueId());
            default -> { }
        }
    }

    /** Color principal del terreno: el que eligió el dueño o uno según la relación. */
    private String colorTerreno(ClaimProfile c, UUID id) {
        String col = c.getClaimColor();
        if (col != null && col.matches("#[0-9a-fA-F]{6}")) return col;
        if (c.isOwner(id)) return "#51cf66";
        if (c.isMember(id)) return "#4dabf7";
        return "#ffd43b";
    }

    private static String aclarar(String hex, double t) {
        int r = Integer.parseInt(hex.substring(1, 3), 16), g = Integer.parseInt(hex.substring(3, 5), 16), b = Integer.parseInt(hex.substring(5, 7), 16);
        r = (int) Math.round(r + (255 - r) * t);
        g = (int) Math.round(g + (255 - g) * t);
        b = (int) Math.round(b + (255 - b) * t);
        return String.format("#%02x%02x%02x", r, g, b);
    }

    void mostrarTitulo(Player p, ClaimProfile c) {
        UUID id = p.getUniqueId();
        boolean fuente = conPack.contains(id);
        String nombre = Menu.esc(c.getName());
        String dueno = Bukkit.getOfflinePlayer(c.getOwnerId()).getName();
        String col = colorTerreno(c, id);
        String claro = aclarar(col, 0.45);
        String fTit = fuente ? (c.getName().length() <= 14 ? "<font:3y33:titulo>" : "<font:3y33:texto>") : "<bold>";
        String fSub = fuente ? "<font:3y33:texto>" : "";
        String saludo = saludo(c);
        String sub;
        if (!saludo.isBlank()) sub = Stickers.procesar(saludo, fuente, false);
        else if (c.isOwner(id)) sub = "Tu terreno";
        else sub = "Terreno de " + Menu.esc(dueno == null ? "?" : dueno);
        if (!c.isOwner(id) && c.isPvpEnabled()) sub += " <red>· PvP";
        Stickers.Sticker ic = Stickers.TODOS.get(icono(c));
        if (ic != null) {
            String d = Stickers.dibujar(ic, fuente, false);
            sub = d + " " + sub + " " + d;
        }
        String titulo = fTit + "<gradient:" + claro + ":" + col + ">" + nombre + "</gradient>";
        String subtitulo = fSub + "<color:" + aclarar(col, 0.7) + ">" + sub;
        p.showTitle(net.kyori.adventure.title.Title.title(Menu.MM.deserialize(titulo), Menu.MM.deserialize(subtitulo), tiempos()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alEntrarTerreno(org.ayosynk.landClaimPlugin.api.event.PlayerEnterClaimEvent e) {
        if (!getConfig().getBoolean("titulos.habilitados", true)) return;
        ClaimProfile c = e.getProfile();
        if (c == null || c.isEnterTitleEnabled()) return; // el dueño puso títulos propios en Ajustes
        mostrarTitulo(e.getPlayer(), c);
        if (!c.isOwner(e.getPlayer().getUniqueId()))
            e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.2f);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alSalirTerreno(org.ayosynk.landClaimPlugin.api.event.PlayerLeaveClaimEvent e) {
        if (!getConfig().getBoolean("titulos.habilitados", true)) return;
        if (!getConfig().getBoolean("titulos.mostrar-salida", false)) return;
        if (!e.isToWilderness()) return; // si pasa a otro terreno, lo muestra el título de entrada
        ClaimProfile c = e.getProfile();
        if (c == null || c.isEnterTitleEnabled()) return;
        boolean fuente = conPack.contains(e.getPlayer().getUniqueId());
        e.getPlayer().showTitle(net.kyori.adventure.title.Title.title(net.kyori.adventure.text.Component.empty(),
                Menu.MM.deserialize((fuente ? "<font:3y33:texto>" : "") + "<gray>Zona libre"), tiempos()));
    }

    // ---------------- máximo de chunks por terreno
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alReclamar(org.ayosynk.landClaimPlugin.api.event.ClaimCreateEvent e) {
        ClaimProfile perfil = e.getProfile();
        if (perfil == null) return;
        Player dueno = Bukkit.getPlayer(perfil.getOwnerId());
        if (dueno == null || dueno.hasPermission("landclaim.admin")) return;
        int max = maxPorTerreno(dueno);
        if (perfil.getOwnedChunks().size() >= max) {
            e.setCancelled(true);
            Player quien = e.getCreatorId() == null ? dueno : Bukkit.getPlayer(e.getCreatorId());
            if (quien != null) {
                msg(quien, "&c" + perfil.getName() + " ya tiene el máximo de &f" + max + " chunks&c.");
                quien.sendMessage(color("&7Tu cupo de &f" + api.getClaimLimit(dueno) + " &7chunks se reparte entre tus &f"
                        + maxTerrenos(dueno) + " &7terrenos. Comprá más con &f/tierra comprar&7."));
            }
        }
    }

    // ---------------- redirigir los menús principales de LandClaimPlugin a los nuestros
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void alAbrirMenu(org.bukkit.event.inventory.InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Object holder = e.getInventory().getHolder();
        if (holder == null || !holder.getClass().getName().startsWith("org.ayosynk")) return;
        String titulo = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(e.getView().title());
        if (titulo.startsWith("Menú » ")) {
            e.setCancelled(true);
            String nombre = titulo.substring("Menú » ".length());
            ClaimProfile perfil = api.getClaimByName(nombre);
            if (perfil == null) perfil = api.getClaimAt(p.getLocation());
            final ClaimProfile fp = perfil;
            Bukkit.getScheduler().runTask(this, () -> {
                if (fp != null) menus.abrirPanel(p, fp);
                else menus.abrirHub(p);
            });
        } else if (titulo.equals("Lista de terrenos")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(this, () -> menus.abrirHub(p));
        }
    }

    // ---------------- menú de naturaleza
    @EventHandler(priority = EventPriority.HIGHEST)
    public void alClickMenu(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuFlags menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getInventory()) return;
        int slot = e.getRawSlot();
        if (slot == MenuFlags.SLOT_CERRAR) {
            ClaimProfile perfil = api.getClaimById(menu.perfil);
            Bukkit.getScheduler().runTask(this, () -> {
                if (perfil != null) menus.abrirPanel(p, perfil);
                else p.closeInventory();
            });
            return;
        }
        Flag[] flags = Flag.values();
        for (int i = 0; i < flags.length && i < MenuFlags.SLOTS.length; i++) {
            if (MenuFlags.SLOTS[i] != slot) continue;
            ClaimProfile perfil = api.getClaimById(menu.perfil);
            if (perfil == null || !puedeAdministrar(p, perfil)) {
                Bukkit.getScheduler().runTask(this, (Runnable) p::closeInventory);
                return;
            }
            boolean nuevo = !flag(perfil, flags[i]);
            setFlag(perfil, flags[i], nuevo);
            menu.dibujar(this, perfil);
            p.playSound(p.getLocation(), nuevo ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, nuevo ? 1.6f : 0.8f);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void alArrastrarMenu(org.bukkit.event.inventory.InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof MenuFlags) e.setCancelled(true);
    }

    private ClaimProfile perfilEn(org.bukkit.block.Block b) {
        return api.getClaimAt(b.getLocation());
    }

    // ---------------- fuego
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alPropagar(org.bukkit.event.block.BlockSpreadEvent e) {
        if (e.getSource().getType() != org.bukkit.Material.FIRE && e.getSource().getType() != org.bukkit.Material.SOUL_FIRE) return;
        ClaimProfile p = perfilEn(e.getBlock());
        if (p != null && !flag(p, Flag.FUEGO)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alQuemar(org.bukkit.event.block.BlockBurnEvent e) {
        ClaimProfile p = perfilEn(e.getBlock());
        if (p != null && !flag(p, Flag.FUEGO)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alEncender(org.bukkit.event.block.BlockIgniteEvent e) {
        var c = e.getCause();
        if (c != org.bukkit.event.block.BlockIgniteEvent.IgniteCause.SPREAD && c != org.bukkit.event.block.BlockIgniteEvent.IgniteCause.LAVA
                && c != org.bukkit.event.block.BlockIgniteEvent.IgniteCause.LIGHTNING) return;
        ClaimProfile p = perfilEn(e.getBlock());
        if (p != null && !flag(p, Flag.FUEGO)) e.setCancelled(true);
    }

    // ---------------- líquidos que entran desde afuera
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alFluir(org.bukkit.event.block.BlockFromToEvent e) {
        org.bukkit.block.Block de = e.getBlock(), a = e.getToBlock();
        if ((de.getX() >> 4) == (a.getX() >> 4) && (de.getZ() >> 4) == (a.getZ() >> 4)) return;
        ClaimProfile destino = perfilEn(a);
        if (destino == null || flag(destino, Flag.LIQUIDOS)) return;
        ClaimProfile origen = perfilEn(de);
        if (origen == null || !origen.getProfileId().equals(destino.getProfileId())) e.setCancelled(true);
    }

    // ---------------- cultivos pisados por mobs
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alPisar(org.bukkit.event.entity.EntityInteractEvent e) {
        if (e.getBlock().getType() != org.bukkit.Material.FARMLAND) return;
        ClaimProfile p = perfilEn(e.getBlock());
        if (p != null && !flag(p, Flag.CULTIVOS)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alCambiarBloque(org.bukkit.event.entity.EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof Player || e.getBlock().getType() != org.bukkit.Material.FARMLAND) return;
        ClaimProfile p = perfilEn(e.getBlock());
        if (p != null && !flag(p, Flag.CULTIVOS)) e.setCancelled(true);
    }

    // ---------------- hojas
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alCaerHojas(org.bukkit.event.block.LeavesDecayEvent e) {
        ClaimProfile p = perfilEn(e.getBlock());
        if (p != null && !flag(p, Flag.HOJAS)) e.setCancelled(true);
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent e) {
        cancelarEspera(e.getPlayer().getUniqueId());
        compraPendiente.remove(e.getPlayer().getUniqueId());
        esperandoTexto.remove(e.getPlayer().getUniqueId());
        conPack.remove(e.getPlayer().getUniqueId());
    }
}
