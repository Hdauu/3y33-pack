package ar.com.tresyetres.tierras;

import org.ayosynk.landClaimPlugin.LandClaimPlugin;
import org.ayosynk.landClaimPlugin.models.ChunkPosition;
import org.ayosynk.landClaimPlugin.models.ClaimProfile;
import org.ayosynk.landClaimPlugin.models.Warp;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Menús propios estilo Lands: hub de terrenos, panel de un terreno y compra de chunks. */
final class MenusTerrenos {

    private static final int[] SLOTS_TERRENOS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final Tierras pl;

    MenusTerrenos(Tierras pl) {
        this.pl = pl;
    }

    private LandClaimPlugin lcp() {
        return LandClaimPlugin.getInstance();
    }

    UUID activo(Player p) {
        ClaimProfile a = lcp().getClaimManager().getActiveProfile(p);
        return a == null ? null : a.getProfileId();
    }

    void hacerActivo(Player p, ClaimProfile perfil) {
        var cp = lcp().getCacheManager().getPlayerCache().getIfPresent(p.getUniqueId());
        if (cp == null) return;
        cp.setActiveProfileId(perfil.getProfileId());
        lcp().getDatabaseManager().getPlayerDao().savePlayer(cp);
    }

    private static void sonido(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    private String rol(ClaimProfile perfil, UUID id) {
        if (perfil.isOwner(id)) return "<gold>Dueño";
        String r = perfil.getMemberRole(id);
        return "<yellow>" + Menu.esc(r == null ? "Miembro" : r);
    }

    private boolean gestiona(Player p, ClaimProfile perfil, String permiso) {
        return perfil.isOwner(p.getUniqueId()) || p.hasPermission("landclaim.admin")
                || pl.api().hasPermission(perfil, p.getUniqueId(), permiso);
    }

    // ------------------------------------------------------------------ hub

    void abrirHub(Player p) {
        UUID id = p.getUniqueId();
        Menu m = new Menu(6, "<dark_gray>Mis terrenos");
        m.fondo();

        int usados = pl.api().getTotalChunksByOwner(id);
        int limite = pl.api().getClaimLimit(p);
        int libres = Math.max(0, limite - usados);
        int barras = 20;
        int llenas = limite <= 0 ? 0 : Math.min(barras, Math.round(usados * (float) barras / limite));
        String barra = "<green>" + "|".repeat(llenas) + "<dark_gray>" + "|".repeat(barras - llenas);

        m.poner(4, Menu.cabeza(p, Menu.g("#8ce99a", "#2f9e44", "Tus terrenos"),
                "<gray>Chunks: <white>" + usados + "<dark_gray>/<white>" + limite + " <dark_gray>(<green>" + libres + " libres<dark_gray>)",
                barra,
                "",
                "<gray>Terrenos: <white>" + pl.terrenosDe(id) + "<dark_gray>/<white>" + pl.maxTerrenos(p),
                "<gray>Máximo por terreno: <white>" + pl.maxPorTerreno(p) + " chunks",
                "<gray>Chunks comprados: <white>" + pl.comprados(id),
                "<gray>Próximo chunk: <gold>" + pl.plata(pl.precioSiguiente(id))), null);

        List<ClaimProfile> propios = new ArrayList<>(pl.api().getClaimsByOwner(id));
        List<ClaimProfile> ajenos = new ArrayList<>(pl.api().getClaimsByMember(id));
        ajenos.removeIf(c -> c.isOwner(id));
        UUID activo = activo(p);

        int i = 0;
        for (ClaimProfile c : propios) {
            if (i >= SLOTS_TERRENOS.length) break;
            m.poner(SLOTS_TERRENOS[i++], itemTerreno(c, id, activo, pl.maxPorTerreno(p)), (pp, click) -> clickTerreno(pp, c, click));
        }
        for (ClaimProfile c : ajenos) {
            if (i >= SLOTS_TERRENOS.length) break;
            m.poner(SLOTS_TERRENOS[i++], itemTerreno(c, id, activo, -1), (pp, click) -> clickTerreno(pp, c, click));
        }
        if (i == 0) {
            m.poner(22, Menu.item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>Todavía no tenés terrenos",
                    "<dark_gray>Parate donde quieras construir", "<dark_gray>y apretá <white>Reclamar este chunk<dark_gray>."), null);
        }

        m.poner(46, Menu.item(Material.GOLDEN_SHOVEL, Menu.g("#ffe066", "#f08c00", "Reclamar este chunk"),
                "<gray>Suma el chunk donde estás parado", "<gray>a tu terreno activo.", "",
                libres > 0 ? "<green>Te quedan " + libres + " chunks libres" : "<red>No te quedan chunks libres",
                "", "<yellow>▸ Click para reclamar"), (pp, c) -> {
            pp.closeInventory();
            pp.performCommand("claim");
        });
        m.poner(47, Menu.item(Material.LIME_DYE, Menu.g("#8ce99a", "#2f9e44", "Crear terreno"),
                "<gray>Un terreno nuevo y separado,", "<gray>ideal para granjas.", "",
                "<gray>Tenés <white>" + pl.terrenosDe(id) + "<dark_gray>/<white>" + pl.maxTerrenos(p),
                "", "<yellow>▸ Click y escribí el nombre"), (pp, c) -> {
            pp.closeInventory();
            pl.pedirNombre(pp);
        });
        m.poner(49, Menu.item(Material.EMERALD, Menu.g("#69db7c", "#2b8a3e", "Comprar chunks"),
                "<gray>Agrandá tus terrenos.", "", "<gray>Próximo chunk: <gold>" + pl.plata(pl.precioSiguiente(id)),
                "", "<yellow>▸ Click para ver precios"), (pp, c) -> abrirCompra(pp));
        m.poner(51, Menu.item(Material.FILLED_MAP, Menu.g("#5ee7df", "#3a9bdc", "Mapa"),
                "<gray>Un mapa en tu mano con", "<gray>los terrenos de alrededor.", "", "<yellow>▸ Click para recibirlo"), (pp, c) -> {
            pp.closeInventory();
            pp.performCommand("claim map");
        });
        m.poner(52, Menu.item(Material.ENDER_EYE, Menu.g("#e0aaff", "#9d4edd", "Puntos públicos"),
                "<gray>Lugares que otros jugadores", "<gray>abrieron para todos.", "", "<yellow>▸ Click para ver"), (pp, c) -> pp.performCommand("claim publicwarps"));
        m.poner(53, Menu.item(Material.BARRIER, "<red><bold>Cerrar"), (pp, c) -> pp.closeInventory());
        m.poner(45, Menu.item(Material.BOOK, Menu.g("#ced4da", "#868e96", "Ayuda"),
                "<gray>Todos los comandos.", "", "<yellow>▸ Click para ver"), (pp, c) -> {
            pp.closeInventory();
            pp.performCommand("tierra ayuda");
        });
        p.openInventory(m.getInventory());
    }

    private org.bukkit.inventory.ItemStack itemTerreno(ClaimProfile c, UUID id, UUID activo, int max) {
        boolean esActivo = c.getProfileId().equals(activo);
        int chunks = c.getOwnedChunks().size();
        int miembros = c.getMemberRoles().size();
        int puntos = c.getWarps() == null ? 0 : c.getWarps().size();
        String donde = "<dark_gray>Sin chunks todavía";
        if (!c.getOwnedChunks().isEmpty()) {
            ChunkPosition cp = c.getOwnedChunks().iterator().next();
            donde = "<dark_gray>" + mundo(cp.world()) + " · X " + (cp.x() * 16 + 8) + " Z " + (cp.z() * 16 + 8);
        }
        List<String> lore = new ArrayList<>(List.of(
                "<gray>Rol: " + rol(c, id),
                "<gray>Chunks: <white>" + chunks + (max > 0 && max < 1_000_000 ? "<dark_gray>/<white>" + max : ""),
                "<gray>Miembros: <white>" + miembros,
                "<gray>Puntos: <white>" + puntos,
                donde, ""));
        if (esActivo) lore.add("<green>✔ Terreno activo <dark_gray>(acá suman los chunks)");
        lore.add("<yellow>▸ Click izquierdo: <white>abrir");
        lore.add("<yellow>▸ Click derecho: <white>ir al terreno");
        org.bukkit.inventory.ItemStack it = Menu.item(esActivo ? Material.MOSS_BLOCK : Material.GRASS_BLOCK,
                Menu.g("#8ce99a", "#2f9e44", Menu.esc(c.getName())), lore.toArray(new String[0]));
        return esActivo ? Menu.brillo(it) : it;
    }

    static String mundo(String w) {
        if (w == null) return "?";
        if (w.endsWith("_nether")) return "Nether";
        if (w.endsWith("_the_end")) return "End";
        return "Mundo";
    }

    private void clickTerreno(Player p, ClaimProfile c, ClickType click) {
        sonido(p);
        if (click.isRightClick()) {
            p.closeInventory();
            irAlTerreno(p, c);
        } else {
            abrirPanel(p, c);
        }
    }

    void irAlTerreno(Player p, ClaimProfile c) {
        Location destino = null;
        Map<String, Warp> warps = c.getWarps();
        if (warps != null && !warps.isEmpty()) {
            Warp w = warps.containsKey("inicio") ? warps.get("inicio") : warps.values().iterator().next();
            destino = w.getLocation();
        } else if (!c.getOwnedChunks().isEmpty()) {
            ChunkPosition cp = c.getOwnedChunks().iterator().next();
            World w = Bukkit.getWorld(cp.world());
            if (w != null) {
                int x = cp.x() * 16 + 8, z = cp.z() * 16 + 8;
                destino = new Location(w, x + 0.5, w.getHighestBlockYAt(x, z) + 1, z + 0.5);
            }
        }
        if (destino == null) {
            pl.msg(p, "&cEse terreno todavía no tiene chunks.");
            return;
        }
        pl.iniciarTp(p, destino);
    }

    // ------------------------------------------------------------------ panel de un terreno

    void abrirPanel(Player p, ClaimProfile c) {
        UUID id = p.getUniqueId();
        boolean dueno = c.isOwner(id) || p.hasPermission("landclaim.admin");
        Menu m = new Menu(6, "<dark_gray>Terreno » " + Menu.esc(c.getName()));
        m.fondo();

        String due = Bukkit.getOfflinePlayer(c.getOwnerId()).getName();
        m.poner(4, Menu.brillo(Menu.item(Material.NETHER_STAR, Menu.g("#8ce99a", "#2f9e44", Menu.esc(c.getName())),
                "<gray>Dueño: <white>" + Menu.esc(due == null ? "?" : due),
                "<gray>Tu rol: " + rol(c, id),
                "<gray>Chunks: <white>" + c.getOwnedChunks().size() + (c.isOwner(id) && pl.maxPorTerreno(p) < 1_000_000 ? "<dark_gray>/<white>" + pl.maxPorTerreno(p) : ""),
                "<gray>Miembros: <white>" + c.getMemberRoles().size(),
                "<gray>Puntos: <white>" + (c.getWarps() == null ? 0 : c.getWarps().size()) + (c.isOwner(id) ? "<dark_gray>/<white>" + limitePuntos(p) : ""),
                "",
                "<gray>Monstruos: " + sn(pl.flag(c, Flag.MONSTRUOS)) + " <dark_gray>· <gray>Animales: " + sn(pl.flag(c, Flag.ANIMALES)),
                "<gray>Fuego: " + sn(pl.flag(c, Flag.FUEGO)) + " <dark_gray>· <gray>PvP: " + sn(c.isPvpEnabled()))), null);

        LandClaimPlugin lcp = lcp();
        m.poner(19, Menu.item(Material.ENDER_PEARL, Menu.g("#e0aaff", "#9d4edd", "Ir al terreno"),
                "<gray>Te lleva al punto <white>inicio<gray>,", "<gray>al primero que tenga o al centro.", "", "<yellow>▸ Click para ir"), (pp, ck) -> {
            pp.closeInventory();
            irAlTerreno(pp, c);
        });
        m.poner(20, Menu.item(Material.ENDER_EYE, Menu.g("#e0aaff", "#9d4edd", "Puntos"),
                "<gray>Teletransportes guardados", "<gray>dentro de este terreno.", "", "<dark_gray>Crear: <white>/tierra punto [nombre]", "", "<yellow>▸ Click para ver"),
                (pp, ck) -> org.ayosynk.landClaimPlugin.gui.WarpManagementGUI.open(pp, c, lcp));
        m.poner(21, Menu.item(Material.PLAYER_HEAD, Menu.g("#8ce99a", "#2f9e44", "Miembros"),
                "<gray>Invitá amigos y elegí", "<gray>el rol de cada uno.", "", "<yellow>▸ Click para abrir"),
                gestiona(p, c, "MANAGE_MEMBERS") ? (pp, ck) -> org.ayosynk.landClaimPlugin.gui.MemberManagementGUI.open(pp, c, lcp) : sinPermiso());
        m.poner(22, Menu.item(Material.OAK_SAPLING, Menu.g("#b2f2bb", "#2b8a3e", "Naturaleza"),
                "<gray>Monstruos, phantoms, animales,", "<gray>fuego, líquidos, cultivos y hojas.", "", "<yellow>▸ Click para abrir"),
                gestiona(p, c, "MANAGE_SETTINGS") ? (pp, ck) -> pp.openInventory(new MenuFlags(pl, c).getInventory()) : sinPermiso());
        m.poner(23, Menu.item(Material.OAK_DOOR, Menu.g("#ced4da", "#868e96", "Visitas"),
                "<gray>Qué puede hacer la gente", "<gray>que no es de tu terreno.", "", "<yellow>▸ Click para abrir"),
                gestiona(p, c, "MANAGE_SETTINGS") ? (pp, ck) -> org.ayosynk.landClaimPlugin.gui.VisitorSettingsGUI.open(pp, c, lcp) : sinPermiso());
        m.poner(24, Menu.item(Material.TRIPWIRE_HOOK, Menu.g("#ffe066", "#fab005", "Confianza"),
                "<gray>Permisos para un jugador", "<gray>puntual sin hacerlo miembro.", "", "<yellow>▸ Click para abrir"),
                gestiona(p, c, "MANAGE_MEMBERS") ? (pp, ck) -> org.ayosynk.landClaimPlugin.gui.TrustManagementGUI.open(pp, c, lcp) : sinPermiso());
        m.poner(25, Menu.item(Material.COMPARATOR, Menu.g("#ffd36e", "#ff9a3c", "Ajustes"),
                "<gray>Nombre, color, roles, PvP", "<gray>y títulos de entrada.", "", "<yellow>▸ Click para abrir"),
                gestiona(p, c, "MANAGE_SETTINGS") ? (pp, ck) -> org.ayosynk.landClaimPlugin.gui.ClaimSettingsGUI.open(pp, c, lcp) : sinPermiso());

        boolean esActivo = c.getProfileId().equals(activo(p));
        if (c.isOwner(id)) {
            m.poner(30, esActivo
                    ? Menu.brillo(Menu.item(Material.MOSS_BLOCK, Menu.g("#8ce99a", "#2f9e44", "Terreno activo"),
                    "<gray>Los chunks que reclames", "<gray>se suman a este terreno."))
                    : Menu.item(Material.GRASS_BLOCK, Menu.g("#ffe066", "#f08c00", "Usar este terreno"),
                    "<gray>Hacé que los chunks que", "<gray>reclames se sumen acá.", "", "<yellow>▸ Click para elegir"),
                    esActivo ? null : (pp, ck) -> {
                        hacerActivo(pp, c);
                        pl.msg(pp, "&aAhora los chunks se suman a &e" + c.getName() + "&a.");
                        abrirPanel(pp, c);
                    });
            m.poner(31, Menu.item(Material.GOLDEN_SHOVEL, Menu.g("#ffe066", "#f08c00", "Reclamar acá"),
                    "<gray>Suma a este terreno el chunk", "<gray>donde estás parado.", "", "<yellow>▸ Click para reclamar"), (pp, ck) -> {
                hacerActivo(pp, c);
                pp.closeInventory();
                pp.performCommand("claim");
            });
            m.poner(33, Menu.item(Material.OAK_HANGING_SIGN, Menu.g("#fff3bf", "#fcc419", "Mensaje de entrada"),
                    "<gray>Lo que ven los demás debajo", "<gray>del nombre al entrar.", "",
                    pl.saludo(c).isBlank() ? "<dark_gray>Ahora: <white>Terreno de tu nombre" : "<dark_gray>Ahora: <white>" + Stickers.procesar(pl.saludo(c), pl.tienePack(p), false),
                    "<dark_gray>Stickers: <white>/tierra stickers",
                    "", "<yellow>▸ Click para cambiarlo"), (pp, ck) -> {
                pp.closeInventory();
                pl.pedirSaludo(pp, c);
            });
            Stickers.Sticker ic = Stickers.TODOS.get(pl.icono(c));
            m.poner(28, Menu.item(ic == null ? Material.ITEM_FRAME : ic.icono(), Menu.g("#e0aaff", "#9d4edd", "Ícono"),
                    "<gray>Un sticker a los costados", "<gray>de tu mensaje de entrada.", "",
                    "<dark_gray>Ahora: <white>" + (ic == null ? "ninguno" : ":" + ic.nombre() + ":"),
                    "", "<yellow>▸ Click para elegir"), (pp, ck) -> abrirIconos(pp, c));
            m.poner(29, Menu.item(Material.BRUSH, Menu.g("#f783ac", "#d6336c", "Color del nombre"),
                    "<gray>El color del título de entrada", "<gray>y de los bordes del terreno.", "", "<yellow>▸ Click para elegir"),
                    (pp, ck) -> org.ayosynk.landClaimPlugin.gui.ChangeClaimColorGUI.open(pp, c, lcp));
            m.poner(32, Menu.item(Material.SHIELD, Menu.g("#74c0fc", "#4263eb", "Aliados"),
                    "<gray>Terrenos amigos y qué pueden", "<gray>hacer dentro del tuyo.", "", "<yellow>▸ Click para abrir"),
                    (pp, ck) -> org.ayosynk.landClaimPlugin.gui.AllyManagementGUI.open(pp, c, lcp));
        }
        m.poner(48, Menu.item(Material.ARROW, "<red><bold>« Volver", "<gray>Volver a tus terrenos"), (pp, ck) -> abrirHub(pp));
        m.poner(50, Menu.item(Material.BARRIER, "<red><bold>Cerrar"), (pp, ck) -> pp.closeInventory());
        p.openInventory(m.getInventory());
    }

    private java.util.function.BiConsumer<Player, ClickType> sinPermiso() {
        return (pp, ck) -> {
            pp.playSound(pp.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            pl.msg(pp, "&cNo tenés permiso para cambiar eso en este terreno.");
        };
    }

    int limitePuntos(Player p) {
        int l = lcp().getWarpManager().getWarpLimit(p);
        return l >= 1_000_000 ? 999 : l;
    }

    private static String sn(boolean b) {
        return b ? "<green>Sí" : "<red>No";
    }

    // ------------------------------------------------------------------ elegir ícono

    void abrirIconos(Player p, ClaimProfile c) {
        Menu m = new Menu(6, "<dark_gray>Ícono » " + Menu.esc(c.getName()));
        m.fondo();
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        String actual = pl.icono(c);
        int i = 0;
        for (Stickers.Sticker st : Stickers.TODOS.values()) {
            if (i >= slots.length) break;
            boolean es = st.nombre().equals(actual);
            var it = Menu.item(st.icono(), Menu.g("#e0aaff", "#9d4edd", ":" + st.nombre() + ":"),
                    "<gray>Se ve así: " + Stickers.dibujar(st, pl.tienePack(p), false),
                    "", es ? "<green>✔ Elegido" : "<yellow>▸ Click para elegir");
            m.poner(slots[i++], es ? Menu.brillo(it) : it, (pp, ck) -> {
                pl.setIcono(c, st.nombre());
                pl.mostrarTitulo(pp, c);
                abrirPanel(pp, c);
            });
        }
        m.poner(4, Menu.item(Material.BOOK, Menu.g("#e0aaff", "#9d4edd", "Stickers"),
                "<gray>El ícono va a los dos costados", "<gray>del mensaje de entrada.", "",
                "<gray>También podés escribirlos en el", "<gray>mensaje, ej. <white>:corazon:"), null);
        m.poner(47, Menu.item(Material.BARRIER, "<red><bold>Sin ícono", "<gray>Saca el ícono del título"), (pp, ck) -> {
            pl.setIcono(c, null);
            abrirPanel(pp, c);
        });
        m.poner(49, Menu.item(Material.ARROW, "<red><bold>« Volver", "<gray>Volver al terreno"), (pp, ck) -> abrirPanel(pp, c));
        p.openInventory(m.getInventory());
    }

    // ------------------------------------------------------------------ compra de chunks

    void abrirCompra(Player p) {
        UUID id = p.getUniqueId();
        Menu m = new Menu(3, "<dark_gray>Comprar chunks");
        m.fondo();
        int usados = pl.api().getTotalChunksByOwner(id);
        int limite = pl.api().getClaimLimit(p);
        m.poner(4, Menu.cabeza(p, Menu.g("#69db7c", "#2b8a3e", "Tus chunks"),
                "<gray>Usados: <white>" + usados + "<dark_gray>/<white>" + limite,
                "<gray>Comprados: <white>" + pl.comprados(id),
                "<gray>Tu plata: <gold>" + pl.plata(pl.saldo(p)),
                "", "<dark_gray>Cada chunk cuesta un poco más", "<dark_gray>que el anterior."), null);
        int[][] opciones = {{11, 1}, {13, 5}, {15, 10}};
        Material[] mats = {Material.EMERALD, Material.EMERALD_BLOCK, Material.DIAMOND_BLOCK};
        for (int k = 0; k < opciones.length; k++) {
            int slot = opciones[k][0], cant = opciones[k][1];
            double precio = pl.precioDe(id, cant);
            boolean alcanza = pl.saldo(p) >= precio;
            m.poner(slot, Menu.item(mats[k], Menu.g("#69db7c", "#2b8a3e", "+" + cant + (cant == 1 ? " chunk" : " chunks")),
                    "<gray>Precio: <gold>" + pl.plata(precio),
                    "",
                    alcanza ? "<yellow>▸ Click para comprar" : "<red>No te alcanza la plata"), (pp, ck) -> {
                pl.comprarDirecto(pp, cant);
                abrirCompra(pp);
            });
        }
        m.poner(22, Menu.item(Material.ARROW, "<red><bold>« Volver", "<gray>Volver a tus terrenos"), (pp, ck) -> abrirHub(pp));
        p.openInventory(m.getInventory());
    }
}
