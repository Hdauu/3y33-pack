package ar.com.tresyetres.trabajos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mochilas: un item que se abre con click derecho teniéndolo en la mano. Hay 4 niveles (I a IV). Cada mochila se abre en páginas de 45 espacios
 * (con una fila de flechas abajo si tiene más de una) y la cantidad total de cada nivel sale de config.yml (mochilas.espacios).
 * La I se craftea (8 cueros alrededor de un cofre) y cada nivel se mejora en la mesa de crafteo con la mochila anterior + materiales
 * (mochilas.mejoras): la mochila conserva su id, así que no pierde nada de lo que tenía adentro.
 * El contenido NO vive en el item sino en mochilas.yml, con el id de cada mochila. Solo una persona puede tenerla abierta a la vez,
 * y no se puede guardar una mochila adentro de otra.
 */
final class Mochilas implements Listener {
    static final String[] ROMANO = {"I", "II", "III", "IV"};
    private static final int[] RGB = {0x3FAE49, 0x8E3FE0, 0xF2B01E, 0xC42A55};
    /** Espacios por página (las 5 filas de arriba); la sexta fila es la de las flechas. */
    static final int PAG = 45;
    private static final int[] ESPACIOS_BASE = {45, 90, 135, 180};
    private static final String[][] MEJORAS_BASE = {
            {"LEATHER:4", "IRON_INGOT:4"},
            {"DIAMOND:4", "GOLD_INGOT:4"},
            {"NETHERITE_INGOT:2", "DIAMOND:3", "ENDER_PEARL:3"}
    };

    /** Dueño del inventario abierto: así sabemos qué mochila es y la guardamos al cerrar. */
    static final class Titular implements InventoryHolder {
        final String id;
        final int nivel;
        final ItemStack[] todo;
        int pag;
        Inventory inv;
        Titular(String id, int nivel, int total) { this.id = id; this.nivel = nivel; this.todo = new ItemStack[total]; }
        int paginas() { return todo.length / PAG; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final TrabajosPlugin pl;
    private final NamespacedKey kId;
    private final NamespacedKey kNivel;
    private final NamespacedKey kNav;
    private final NamespacedKey kLigada;
    private final File archivo;
    private YamlConfiguration datos;
    private int[] espacios = ESPACIOS_BASE.clone();
    /** mejoras.get(n) = materiales que hacen falta para pasar de la mochila n-1 a la n (n de 2 a 4). */
    private final Map<Integer, Map<Material, Integer>> mejoras = new HashMap<>();
    private final List<NamespacedKey> recetas = new ArrayList<>();
    private final Map<String, Titular> abiertas = new HashMap<>();
    private final Map<UUID, Long> espera = new HashMap<>();

    Mochilas(TrabajosPlugin pl) {
        this.pl = pl;
        this.kId = new NamespacedKey(pl, "mochila_id");
        this.kNivel = new NamespacedKey(pl, "mochila_nivel");
        this.kNav = new NamespacedKey(pl, "mochila_nav");
        this.kLigada = new NamespacedKey(pl, "mochila_ligada");
        this.archivo = new File(pl.getDataFolder(), "mochilas.yml");
    }

    void cargar() {
        espacios = ESPACIOS_BASE.clone();
        List<Integer> l = pl.getConfig().getIntegerList("mochilas.espacios");
        if (l.size() == 4) {
            int[] n = new int[4];
            boolean ok = true;
            for (int i = 0; i < 4; i++) {
                int v = l.get(i);
                if (v < PAG || v > PAG * 8 || v % PAG != 0) ok = false;
                n[i] = v;
            }
            if (ok) espacios = n;
            else pl.getLogger().warning("mochilas.espacios tiene que ser 4 numeros multiplos de " + PAG + " (45 a 360). Dejo los de siempre.");
        } else {
            pl.getLogger().warning("mochilas.espacios tiene que tener 4 numeros. Dejo los de siempre.");
        }
        datos = archivo.exists() ? YamlConfiguration.loadConfiguration(archivo) : new YamlConfiguration();
        cargarMejoras();
        registrarRecetas();
    }

    private void cargarMejoras() {
        mejoras.clear();
        for (int n = 2; n <= 4; n++) {
            List<String> l = pl.getConfig().getStringList("mochilas.mejoras." + n);
            Map<Material, Integer> m = leerMateriales(l);
            if (m == null) {
                pl.getLogger().warning("mochilas.mejoras." + n + " no es valido (hasta 8 materiales en total, formato MATERIAL:cantidad). Dejo los de siempre.");
                m = leerMateriales(List.of(MEJORAS_BASE[n - 2]));
            }
            mejoras.put(n, m);
        }
    }

    private Map<Material, Integer> leerMateriales(List<String> l) {
        if (l.isEmpty()) return null;
        Map<Material, Integer> m = new LinkedHashMap<>();
        int total = 0;
        for (String s : l) {
            String[] p = s.split(":");
            Material mat = Material.matchMaterial(p[0].trim());
            int c = 1;
            try { if (p.length > 1) c = Integer.parseInt(p[1].trim()); } catch (NumberFormatException e) { return null; }
            if (mat == null || mat.isAir() || mat == Material.PAPER || c < 1) return null;
            m.merge(mat, c, Integer::sum);
            total += c;
        }
        return total <= 8 ? m : null;
    }

    private void registrarRecetas() {
        for (NamespacedKey k : recetas) Bukkit.removeRecipe(k);
        recetas.clear();
        NamespacedKey kb = new NamespacedKey(pl, "mochila_base");
        ShapedRecipe base = new ShapedRecipe(kb, crear(1));
        base.shape("LLL", "LCL", "LLL");
        base.setIngredient('L', Material.LEATHER);
        base.setIngredient('C', Material.CHEST);
        Bukkit.addRecipe(base);
        recetas.add(kb);
        for (int n = 2; n <= 4; n++) {
            NamespacedKey k = new NamespacedKey(pl, "mochila_mejora_" + n);
            ShapelessRecipe r = new ShapelessRecipe(k, crear(n));
            r.addIngredient(Material.PAPER);
            for (Map.Entry<Material, Integer> e : mejoras.get(n).entrySet()) r.addIngredient(e.getValue(), e.getKey());
            Bukkit.addRecipe(r);
            recetas.add(k);
        }
    }

    /** Se llama al apagar el plugin: saca las recetas para que no queden colgadas hasta el próximo reinicio. */
    void quitarRecetas() {
        for (NamespacedKey k : recetas) Bukkit.removeRecipe(k);
        recetas.clear();
    }

    int espaciosDe(int nivel) { return espacios[Math.max(1, Math.min(4, nivel)) - 1]; }

    // ------------------------------------------------------------------ el item

    ItemStack crear(int nivel) { return crear(nivel, UUID.randomUUID().toString(), false); }

    /** La Mochila IV de recompensa (ligada) solo se abre si el trabajo que tenés puesto está en nivel 65 o más. */
    private ItemStack crear(int nivel, String id, boolean ligada) {
        nivel = Math.max(1, Math.min(4, nivel));
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        TextColor c = TextColor.color(RGB[nivel - 1]);
        m.displayName(Component.text("Mochila " + ROMANO[nivel - 1], c).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(gris("Tené la mochila en la mano y hacé"));
        lore.add(gris("click derecho para abrirla."));
        lore.add(Component.empty());
        int total = espaciosDe(nivel);
        lore.add(gris("Espacios: ").append(Component.text(total, c)));
        if (total > PAG) lore.add(gris("Páginas: ").append(Component.text(total / PAG, c)));
        if (ligada) {
            lore.add(Component.empty());
            lore.add(Component.text("Recompensa del nivel 65: solo se abre si", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("tu trabajo actual está en nivel 65 o más.", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        }
        if (nivel < 4) {
            lore.add(Component.empty());
            lore.add(gris("Se mejora a la Mochila " + ROMANO[nivel] + " en la mesa de"));
            lore.add(gris("crafteo con esta mochila y:"));
            for (Map.Entry<Material, Integer> e : mejoras.getOrDefault(nivel + 1, Map.of()).entrySet()) {
                lore.add(Component.text("  " + e.getValue() + " × ", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                        .append(Component.translatable(e.getKey().translationKey(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)));
            }
            if (nivel == 3) {
                lore.add(gris("Hace falta nivel 65 en tu trabajo actual."));
                lore.add(gris("Se puede armar una sola por cuenta."));
            }
        }
        m.lore(lore);
        m.setItemModel(new NamespacedKey("3y33", "trabajos/mochila_" + nivel));
        m.setMaxStackSize(1);
        m.getPersistentDataContainer().set(kId, PersistentDataType.STRING, id);
        m.getPersistentDataContainer().set(kNivel, PersistentDataType.INTEGER, nivel);
        if (ligada) m.getPersistentDataContainer().set(kLigada, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private static Component gris(String s) {
        return Component.text(s, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    boolean esMochila(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(kId, PersistentDataType.STRING);
    }

    private boolean esNav(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(kNav, PersistentDataType.BYTE);
    }

    private String idDe(ItemStack it) {
        return esMochila(it) ? it.getItemMeta().getPersistentDataContainer().get(kId, PersistentDataType.STRING) : null;
    }

    private int nivelDe(ItemStack it) {
        Integer n = esMochila(it) ? it.getItemMeta().getPersistentDataContainer().get(kNivel, PersistentDataType.INTEGER) : null;
        return n == null ? 1 : Math.max(1, Math.min(4, n));
    }

    private boolean ligada(ItemStack it) {
        return esMochila(it) && it.getItemMeta().getPersistentDataContainer().has(kLigada, PersistentDataType.BYTE);
    }

    /** ¿El trabajo que tiene puesto el jugador llegó al nivel 65? (el hito de la Mochila IV) */
    private boolean tieneNivel65(Player p) {
        Perfil f = pl.perfiles().de(p);
        return f.activo != null && f.de(f.activo).nivel >= 65;
    }

    /** Los materiales de la mejora a la IV: es lo que entrega el hito del nivel 65 (la mochila se arma en la mesa de crafteo). */
    void darMateriales(Player p) {
        for (Map.Entry<Material, Integer> e : mejoras.getOrDefault(4, Map.of()).entrySet()) {
            Map<Integer, ItemStack> sobra = p.getInventory().addItem(new ItemStack(e.getKey(), e.getValue()));
            for (ItemStack r : sobra.values()) p.getWorld().dropItemNaturally(p.getLocation(), r);
        }
    }

    /** Le da una mochila nueva al jugador (si no entra en el inventario, la deja a sus pies). */
    void darA(Player p, int nivel) {
        Map<Integer, ItemStack> sobra = p.getInventory().addItem(crear(nivel));
        for (ItemStack r : sobra.values()) p.getWorld().dropItemNaturally(p.getLocation(), r);
    }

    // ------------------------------------------------------------------ abrir y guardar

    private void abrir(Player p, ItemStack item) {
        String id = idDe(item);
        if (id == null) return;
        if (abiertas.containsKey(id)) {
            Msg.error(p, "Esa mochila ya está abierta.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            return;
        }
        if (ligada(item) && !tieneNivel65(p)) {
            Msg.error(p, "Para usar esta mochila tu trabajo actual tiene que estar en nivel 65 o más.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            return;
        }
        int nivel = nivelDe(item);
        Titular t = new Titular(id, nivel, espaciosDe(nivel));
        Component titulo = Component.text("Mochila " + ROMANO[nivel - 1], TextColor.color(RGB[nivel - 1])).decoration(TextDecoration.ITALIC, false);
        t.inv = Bukkit.createInventory(t, t.paginas() > 1 ? 54 : PAG, titulo);
        ConfigurationSection s = datos.getConfigurationSection("m." + id);
        if (s != null) {
            for (String k : s.getKeys(false)) {
                if (!k.startsWith("s")) continue;
                try {
                    int idx = Integer.parseInt(k.substring(1));
                    String b = s.getString(k);
                    if (b == null || idx < 0 || idx >= t.todo.length) continue;
                    t.todo[idx] = ItemStack.deserializeBytes(Base64.getDecoder().decode(b));
                } catch (RuntimeException e) {
                    pl.getLogger().warning("No pude leer un item de la mochila " + id + " (" + k + "): " + e.getMessage());
                }
            }
        }
        pintar(t);
        abiertas.put(id, t);
        p.openInventory(t.inv);
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8f, 1f);
    }

    /** Dibuja la página actual (y la fila de flechas si hay más de una). */
    private void pintar(Titular t) {
        for (int i = 0; i < PAG; i++) t.inv.setItem(i, t.todo[t.pag * PAG + i]);
        if (t.paginas() <= 1) return;
        TextColor c = TextColor.color(RGB[t.nivel - 1]);
        for (int i = PAG; i < 54; i++) t.inv.setItem(i, nav(Material.GRAY_STAINED_GLASS_PANE, Component.text(" ")));
        if (t.pag > 0) t.inv.setItem(45, nav(Material.ARROW, Component.text("◀ Página anterior", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)));
        if (t.pag < t.paginas() - 1) t.inv.setItem(53, nav(Material.ARROW, Component.text("Página siguiente ▶", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)));
        t.inv.setItem(49, nav(Material.BOOK, Component.text("Página " + (t.pag + 1) + " de " + t.paginas(), c).decoration(TextDecoration.ITALIC, false)));
    }

    private ItemStack nav(Material mat, Component nombre) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(nombre);
        m.getPersistentDataContainer().set(kNav, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    /** Copia lo que hay en pantalla a la memoria de la mochila. */
    private void volcar(Titular t) {
        for (int i = 0; i < PAG; i++) {
            ItemStack it = t.inv.getItem(i);
            t.todo[t.pag * PAG + i] = (it == null || it.getType().isAir()) ? null : it;
        }
    }

    private void cambiarPagina(Titular t, int delta, Player p) {
        int nueva = Math.max(0, Math.min(t.paginas() - 1, t.pag + delta));
        if (nueva == t.pag) return;
        volcar(t);
        t.pag = nueva;
        pintar(t);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1f);
    }

    private void guardar(Titular t) {
        volcar(t);
        datos.set("m." + t.id, null);
        for (int i = 0; i < t.todo.length; i++) {
            ItemStack it = t.todo[i];
            if (it == null || it.getType().isAir()) continue;
            datos.set("m." + t.id + ".s" + i, Base64.getEncoder().encodeToString(it.serializeAsBytes()));
        }
        escribir();
    }

    private void escribir() {
        try {
            if (!pl.getDataFolder().exists()) pl.getDataFolder().mkdirs();
            datos.save(archivo);
        } catch (IOException e) {
            pl.getLogger().severe("No pude guardar mochilas.yml: " + e.getMessage());
        }
    }

    /** Al apagar el plugin: guarda y cierra todas las que estén abiertas. */
    void guardarTodo() {
        for (Titular t : new ArrayList<>(abiertas.values())) {
            guardar(t);
            for (org.bukkit.entity.HumanEntity h : new ArrayList<>(t.inv.getViewers())) h.closeInventory();
        }
        abiertas.clear();
    }

    /** Guardado periódico de las abiertas (por si se cae el server con una abierta). */
    void guardarAbiertas() {
        for (Titular t : abiertas.values()) guardar(t);
    }

    // ------------------------------------------------------------------ eventos

    @EventHandler
    public void alUsar(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (!esMochila(it)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        long ahora = System.currentTimeMillis();
        Long ult = espera.get(p.getUniqueId());
        if (ult != null && ahora - ult < 300) return;
        espera.put(p.getUniqueId(), ahora);
        abrir(p, it);
    }

    @EventHandler
    public void alCerrar(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Titular t)) return;
        guardar(t);
        abiertas.remove(t.id);
        if (e.getPlayer() instanceof Player p) p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.6f, 0.8f);
    }

    @EventHandler
    public void alClic(InventoryClickEvent e) {
        Inventory arriba = e.getView().getTopInventory();
        if (!(arriba.getHolder() instanceof Titular t)) return;
        boolean enMochila = e.getClickedInventory() != null && e.getClickedInventory().equals(arriba);
        if (t.paginas() > 1) {
            // la fila de flechas no se toca; con click en una flecha se cambia de página
            if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR || esNav(e.getCurrentItem()) || esNav(e.getCursor())) {
                e.setCancelled(true);
                if (enMochila && e.getWhoClicked() instanceof Player p && (e.getClick() == ClickType.LEFT || e.getClick() == ClickType.RIGHT)) {
                    if (e.getSlot() == 45) cambiarPagina(t, -1, p);
                    else if (e.getSlot() == 53) cambiarPagina(t, 1, p);
                }
                return;
            }
            if (enMochila && e.getSlot() >= PAG) { e.setCancelled(true); return; }
        }
        ItemStack actual = e.getCurrentItem();
        ItemStack cursor = e.getCursor();
        // la mochila que está abierta no se mueve de lugar
        if (esMochila(actual) && t.id.equals(idDe(actual))) { e.setCancelled(true); return; }
        if (esMochila(cursor) && t.id.equals(idDe(cursor))) { e.setCancelled(true); return; }
        // una mochila no entra en otra mochila
        if (enMochila && esMochila(cursor)) { e.setCancelled(true); return; }
        if (e.isShiftClick() && !enMochila && esMochila(actual)) { e.setCancelled(true); return; }
        if (enMochila) {
            switch (e.getClick()) {
                case NUMBER_KEY -> {
                    ItemStack h = e.getWhoClicked().getInventory().getItem(e.getHotbarButton());
                    if (esMochila(h)) e.setCancelled(true);
                }
                case SWAP_OFFHAND -> {
                    if (esMochila(e.getWhoClicked().getInventory().getItemInOffHand())) e.setCancelled(true);
                }
                default -> { }
            }
        }
    }

    @EventHandler
    public void alArrastrar(InventoryDragEvent e) {
        Inventory arriba = e.getView().getTopInventory();
        if (!(arriba.getHolder() instanceof Titular t)) return;
        boolean mochila = esMochila(e.getOldCursor());
        for (int raw : e.getRawSlots()) {
            if (raw >= arriba.getSize()) continue;
            if (mochila || (t.paginas() > 1 && raw >= PAG)) { e.setCancelled(true); return; }
        }
    }

    @EventHandler
    public void alTirar(PlayerDropItemEvent e) {
        ItemStack it = e.getItemDrop().getItemStack();
        if (!esMochila(it)) return;
        if (abiertas.containsKey(idDe(it))) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ crafteo: la mochila I y las mejoras

    private boolean esReceta(Recipe r) {
        return r instanceof org.bukkit.Keyed k && recetas.contains(k.getKey());
    }

    @EventHandler
    public void alPrepararCrafteo(PrepareItemCraftEvent e) {
        Recipe r = e.getRecipe();
        if (!esReceta(r)) return;
        CraftingInventory ci = e.getInventory();
        String clave = ((org.bukkit.Keyed) r).getKey().getKey();
        if (clave.equals("mochila_base")) {
            ci.setResult(crear(1)); // id nuevo en cada crafteo
            return;
        }
        int destino = clave.charAt(clave.length() - 1) - '0';
        ItemStack origen = null;
        for (ItemStack it : ci.getMatrix()) {
            if (it == null || it.getType() != Material.PAPER) continue;
            if (origen != null || !esMochila(it)) { ci.setResult(null); return; }
            origen = it;
        }
        if (origen == null || nivelDe(origen) != destino - 1 || abiertas.containsKey(idDe(origen))) { ci.setResult(null); return; }
        if (destino == 4) {
            // la IV se arma con nivel 65 en el trabajo que tengas puesto y solo una vez por cuenta
            Player p = ci.getViewers().isEmpty() || !(ci.getViewers().get(0) instanceof Player pp) ? null : pp;
            if (p == null || !tieneNivel65(p) || pl.perfiles().de(p).desbloqueos.contains("mochila4_armada")) { ci.setResult(null); return; }
        }
        ci.setResult(crear(destino, idDe(origen), destino == 4));
    }

    @EventHandler
    public void alCraftear(CraftItemEvent e) {
        if (!esReceta(e.getRecipe())) return;
        // con shift se craftearían varias de una vez con el mismo id
        if (e.isShiftClick()) { e.setCancelled(true); return; }
        String clave = ((org.bukkit.Keyed) e.getRecipe()).getKey().getKey();
        if (clave.equals("mochila_mejora_4") && e.getWhoClicked() instanceof Player p) {
            Perfil f = pl.perfiles().de(p);
            f.desbloqueos.add("mochila4_armada");
            pl.perfiles().guardar(f);
        }
    }
}
