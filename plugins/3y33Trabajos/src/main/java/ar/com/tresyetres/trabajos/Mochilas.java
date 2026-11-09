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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mochilas: un item que se abre con click derecho teniéndolo en la mano. Hay 4 niveles (I a IV) y la cantidad de espacios de cada uno sale de
 * config.yml (mochilas.espacios). El contenido NO vive en el item sino en mochilas.yml, con el id de cada mochila: así no se pierde nada si el item
 * se rompe de alguna forma. Solo una persona puede tenerla abierta a la vez, y no se puede guardar una mochila adentro de otra.
 */
final class Mochilas implements Listener {
    static final String[] ROMANO = {"I", "II", "III", "IV"};
    private static final int[] RGB = {0x3FAE49, 0x8E3FE0, 0xF2B01E, 0xC42A55};

    /** Dueño del inventario abierto: así sabemos qué mochila es y la guardamos al cerrar. */
    static final class Titular implements InventoryHolder {
        final String id;
        final int nivel;
        Inventory inv;
        Titular(String id, int nivel) { this.id = id; this.nivel = nivel; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final TrabajosPlugin pl;
    private final NamespacedKey kId;
    private final NamespacedKey kNivel;
    private final File archivo;
    private YamlConfiguration datos;
    private int[] espacios = {9, 18, 36, 54};
    private final Map<String, Titular> abiertas = new HashMap<>();
    private final Map<UUID, Long> espera = new HashMap<>();

    Mochilas(TrabajosPlugin pl) {
        this.pl = pl;
        this.kId = new NamespacedKey(pl, "mochila_id");
        this.kNivel = new NamespacedKey(pl, "mochila_nivel");
        this.archivo = new File(pl.getDataFolder(), "mochilas.yml");
    }

    void cargar() {
        List<Integer> l = pl.getConfig().getIntegerList("mochilas.espacios");
        if (l.size() == 4) {
            int[] n = new int[4];
            boolean ok = true;
            for (int i = 0; i < 4; i++) {
                int v = l.get(i);
                if (v < 9 || v > 54 || v % 9 != 0) ok = false;
                n[i] = v;
            }
            if (ok) espacios = n;
            else pl.getLogger().warning("mochilas.espacios tiene que ser 4 numeros multiplos de 9 entre 9 y 54. Dejo los de siempre.");
        }
        datos = archivo.exists() ? YamlConfiguration.loadConfiguration(archivo) : new YamlConfiguration();
    }

    int espaciosDe(int nivel) { return espacios[Math.max(1, Math.min(4, nivel)) - 1]; }

    // ------------------------------------------------------------------ el item

    ItemStack crear(int nivel) {
        nivel = Math.max(1, Math.min(4, nivel));
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        TextColor c = TextColor.color(RGB[nivel - 1]);
        m.displayName(Component.text("Mochila " + ROMANO[nivel - 1], c).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Tené la mochila en la mano y hacé", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("click derecho para abrirla.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Espacios: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(espaciosDe(nivel), c)));
        m.lore(lore);
        m.setItemModel(new NamespacedKey("3y33", "trabajos/mochila_" + nivel));
        m.setMaxStackSize(1);
        m.getPersistentDataContainer().set(kId, PersistentDataType.STRING, UUID.randomUUID().toString());
        m.getPersistentDataContainer().set(kNivel, PersistentDataType.INTEGER, nivel);
        it.setItemMeta(m);
        return it;
    }

    boolean esMochila(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(kId, PersistentDataType.STRING);
    }

    private String idDe(ItemStack it) {
        return esMochila(it) ? it.getItemMeta().getPersistentDataContainer().get(kId, PersistentDataType.STRING) : null;
    }

    private int nivelDe(ItemStack it) {
        Integer n = esMochila(it) ? it.getItemMeta().getPersistentDataContainer().get(kNivel, PersistentDataType.INTEGER) : null;
        return n == null ? 1 : Math.max(1, Math.min(4, n));
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
        int nivel = nivelDe(item);
        Titular t = new Titular(id, nivel);
        Component titulo = Component.text("Mochila " + ROMANO[nivel - 1], TextColor.color(RGB[nivel - 1])).decoration(TextDecoration.ITALIC, false);
        t.inv = Bukkit.createInventory(t, espaciosDe(nivel), titulo);
        ConfigurationSection s = datos.getConfigurationSection("m." + id);
        if (s != null) {
            for (String k : s.getKeys(false)) {
                if (!k.startsWith("s")) continue;
                try {
                    int slot = Integer.parseInt(k.substring(1));
                    String b = s.getString(k);
                    if (b == null || slot < 0 || slot >= t.inv.getSize()) continue;
                    t.inv.setItem(slot, ItemStack.deserializeBytes(Base64.getDecoder().decode(b)));
                } catch (RuntimeException e) {
                    pl.getLogger().warning("No pude leer un item de la mochila " + id + " (" + k + "): " + e.getMessage());
                }
            }
        }
        abiertas.put(id, t);
        p.openInventory(t.inv);
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8f, 1f);
    }

    private void guardar(Titular t) {
        datos.set("m." + t.id, null);
        ItemStack[] c = t.inv.getContents();
        for (int i = 0; i < c.length; i++) {
            ItemStack it = c[i];
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
        ItemStack actual = e.getCurrentItem();
        ItemStack cursor = e.getCursor();
        // la mochila que está abierta no se mueve de lugar
        if (esMochila(actual) && t.id.equals(idDe(actual))) { e.setCancelled(true); return; }
        if (esMochila(cursor) && t.id.equals(idDe(cursor))) { e.setCancelled(true); return; }
        boolean enMochila = e.getClickedInventory() != null && e.getClickedInventory().equals(arriba);
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
        if (!(arriba.getHolder() instanceof Titular)) return;
        if (!esMochila(e.getOldCursor())) return;
        for (int raw : e.getRawSlots()) {
            if (raw < arriba.getSize()) { e.setCancelled(true); return; }
        }
    }

    @EventHandler
    public void alTirar(PlayerDropItemEvent e) {
        ItemStack it = e.getItemDrop().getItemStack();
        if (!esMochila(it)) return;
        if (abiertas.containsKey(idDe(it))) e.setCancelled(true);
    }
}
