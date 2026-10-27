package ar.com.tresyetres.tierras;

import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.shop.Shop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

/**
 * Texto flotante propio de las tiendas (reemplaza al de QuickShop): se ve solo de cerca,
 * al agacharte aparece el detalle, y el dueño puede moverlo alrededor del cofre.
 * Son entidades de texto reales, así que Java y Bedrock lo ven igual.
 */
final class TiendaTexto implements Listener {
    static final double ALCANCE = 3.4;           // hasta dónde se ve el texto básico
    static final Vector POR_DEFECTO = new Vector(0, 1.35, 0); // respecto al centro del cofre

    private final Tierras pl;
    private final File archivo;
    private final YamlConfiguration datos;
    private final Map<Long, Par> activos = new HashMap<>();
    private final Map<UUID, Long> detalleVisible = new HashMap<>();
    private static final DecimalFormat PLATA = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(new Locale("es", "AR")));

    private static final class Par {
        TextDisplay basico, detalle;
        String ultimoBasico = "", ultimoDetalle = "";
        Location sitio;
    }

    TiendaTexto(Tierras pl) {
        this.pl = pl;
        archivo = new File(pl.getDataFolder(), "tiendas.yml");
        datos = YamlConfiguration.loadConfiguration(archivo);
        Bukkit.getScheduler().runTaskTimer(pl, this::tick, 40L, 20L);
    }

    // ---------------------------------------------------------------- posición

    Vector offset(long id) {
        String s = datos.getString("offsets." + id);
        if (s == null) return POR_DEFECTO.clone();
        try {
            String[] p = s.split(";");
            return new Vector(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
        } catch (RuntimeException e) {
            return POR_DEFECTO.clone();
        }
    }

    void guardarOffset(long id, Vector v) {
        v.setX(Math.max(-2.5, Math.min(2.5, v.getX())));
        v.setY(Math.max(0.6, Math.min(3.5, v.getY())));
        v.setZ(Math.max(-2.5, Math.min(2.5, v.getZ())));
        datos.set("offsets." + id, String.format(Locale.ROOT, "%.2f;%.2f;%.2f", v.getX(), v.getY(), v.getZ()));
        guardar();
        retirar(id);   // se vuelve a crear en el próximo tick con la posición nueva
    }

    void restablecer(long id) {
        datos.set("offsets." + id, null);
        guardar();
        retirar(id);
    }

    private void guardar() {
        try { datos.save(archivo); } catch (IOException e) { pl.getLogger().warning("No pude guardar tiendas.yml: " + e.getMessage()); }
    }

    // ---------------------------------------------------------------- ciclo

    private void tick() {
        var mgr = QuickShopAPI.getInstance().getShopManager();
        Map<Long, Shop> cerca = new HashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location l = p.getLocation();
            World w = l.getWorld();
            int cx = l.getBlockX() >> 4, cz = l.getBlockZ() >> 4;
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (!w.isChunkLoaded(cx + dx, cz + dz)) continue;
                    var mapa = mgr.getShops(w.getChunkAt(cx + dx, cz + dz));
                    if (mapa == null) continue;
                    for (Shop sh : mapa.values()) {
                        Location sl = sh.bukkitLocation();
                        if (sl != null && sl.getWorld() == w && sl.distanceSquared(l) < 28 * 28) cerca.put(sh.getShopId(), sh);
                    }
                }
        }
        for (Long id : new ArrayList<>(activos.keySet())) if (!cerca.containsKey(id)) retirar(id);
        for (Shop sh : cerca.values()) {
            try { mantener(sh); } catch (RuntimeException e) { pl.getLogger().warning("Texto de tienda " + sh.getShopId() + ": " + e); }
        }
        for (Player p : Bukkit.getOnlinePlayers()) actualizarDetalle(p);
    }

    private void mantener(Shop sh) {
        long id = sh.getShopId();
        Location centro = sh.bukkitLocation().clone().add(0.5, 0.5, 0.5);
        Location donde = centro.clone().add(offset(id));
        Par par = activos.get(id);
        if (par == null || par.basico == null || !par.basico.isValid()) {
            retirar(id);
            par = new Par();
            par.sitio = donde;
            par.basico = crear(donde, true);
            par.detalle = crear(donde, false);
            activos.put(id, par);
        }
        String b = clave(sh, false), d = clave(sh, true);
        if (!b.equals(par.ultimoBasico)) { par.basico.text(basico(sh)); par.ultimoBasico = b; }
        if (!d.equals(par.ultimoDetalle)) { par.detalle.text(detalle(sh)); par.ultimoDetalle = d; }
    }

    private TextDisplay crear(Location donde, boolean publico) {
        return donde.getWorld().spawn(donde, TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setBillboard(Display.Billboard.CENTER);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setShadowed(true);
            t.setSeeThrough(false);
            t.setBrightness(new Display.Brightness(15, 15));
            t.setLineWidth(220);
            t.setAlignment(TextDisplay.TextAlignment.CENTER);
            t.setViewRange(publico ? 0.05f : 0.07f);
            t.setVisibleByDefault(publico);
            t.setInterpolationDuration(0);
        });
    }

    void retirar(long id) {
        Par par = activos.remove(id);
        if (par == null) return;
        if (par.basico != null) par.basico.remove();
        if (par.detalle != null) par.detalle.remove();
        detalleVisible.values().removeIf(v -> v == id);
    }

    void apagar() {
        for (Long id : new ArrayList<>(activos.keySet())) retirar(id);
    }

    // ---------------------------------------------------------------- detalle al agacharte

    private void actualizarDetalle(Player p) {
        Long actual = detalleVisible.get(p.getUniqueId());
        Long quiere = null;
        if (p.isSneaking()) {
            double mejor = ALCANCE * ALCANCE;
            for (Map.Entry<Long, Par> en : activos.entrySet()) {
                Par par = en.getValue();
                if (par.sitio == null || par.sitio.getWorld() != p.getWorld()) continue;
                double d = par.sitio.distanceSquared(p.getEyeLocation());
                if (d < mejor) { mejor = d; quiere = en.getKey(); }
            }
        }
        if (Objects.equals(actual, quiere)) return;
        if (actual != null) {
            Par a = activos.get(actual);
            if (a != null && a.detalle != null) { p.hideEntity(pl, a.detalle); p.showEntity(pl, a.basico); }
            detalleVisible.remove(p.getUniqueId());
        }
        if (quiere != null) {
            Par n = activos.get(quiere);
            if (n != null && n.detalle != null) {
                p.showEntity(pl, n.detalle);
                p.hideEntity(pl, n.basico);   // el detalle reemplaza al texto básico mientras estás agachado
                detalleVisible.put(p.getUniqueId(), quiere);
            }
        }
    }

    @EventHandler
    public void alAgacharse(PlayerToggleSneakEvent e) {
        Bukkit.getScheduler().runTask(pl, () -> actualizarDetalle(e.getPlayer()));
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent e) {
        detalleVisible.remove(e.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- texto

    static String plata(double v) {
        return PLATA.format(v) + " $";
    }

    private String clave(Shop sh, boolean det) {
        return det ? sh.getPrice() + "|" + sh.isBuying() + "|" + sh.isFrozen() + "|" + sh.isUnlimited() + "|" + stock(sh) + "|" + dueno(sh) + "|" + terreno(sh) + "|" + sh.getItem().getType()
                : sh.getPrice() + "|" + sh.isBuying() + "|" + sh.isFrozen() + "|" + sh.getItem().getType() + "|" + sh.getItem().getItemMeta();
    }

    private Component nombreItem(Shop sh) {
        return sh.getItem().effectiveName();
    }

    private Component basico(Shop sh) {
        Component l1 = nombreItem(sh).color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD);
        Component l2;
        if (sh.isFrozen()) l2 = Component.text("Congelada", NamedTextColor.GRAY);
        else l2 = Component.text((sh.isBuying() ? "Vender: " : "Comprar: ") + plata(sh.getPrice()),
                sh.isBuying() ? NamedTextColor.GOLD : NamedTextColor.GREEN).decorate(TextDecoration.BOLD);
        return Component.empty().append(l1).append(Component.newline()).append(l2);
    }

    private Component detalle(Shop sh) {
        Component res = basico(sh);
        return res.append(Component.newline())
                .append(Component.text(sh.isBuying() ? "Compra hasta: " : "Stock: ", NamedTextColor.GRAY))
                .append(Component.text(stock(sh), NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("Dueño: ", NamedTextColor.GRAY)).append(Component.text(dueno(sh), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Terreno: ", NamedTextColor.GRAY)).append(Component.text(terreno(sh), NamedTextColor.WHITE));
    }

    String stock(Shop sh) {
        if (sh.isUnlimited()) return "ilimitado";
        try {
            return "x" + (sh.isBuying() ? sh.getRemainingSpace() : sh.getRemainingStock());
        } catch (RuntimeException e) {
            return "?";
        }
    }

    String dueno(Shop sh) {
        String n = sh.getOwner().getUsernameOptional().orElse(null);
        return n == null ? "?" : n;
    }

    String terreno(Shop sh) {
        var c = pl.api().getClaimAt(sh.bukkitLocation());
        return c == null ? "-" : c.getName();
    }
}
