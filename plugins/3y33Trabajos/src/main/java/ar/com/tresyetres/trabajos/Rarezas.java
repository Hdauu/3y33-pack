package ar.com.tresyetres.trabajos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Rareza de cada accion. Se sortea una vez por dia (a la hora fija de la config), igual para todos, y se guarda para que un reinicio no la cambie. */
final class Rarezas {
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    private final TrabajosPlugin pl;
    private final File archivo;
    private final Map<String, Rareza> mapa = new HashMap<>();
    private final Random azar = new Random();
    private long proximo;

    Rarezas(TrabajosPlugin pl) {
        this.pl = pl;
        this.archivo = new File(pl.getDataFolder(), "estado.yml");
    }

    void cargar() {
        mapa.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(archivo);
        proximo = y.getLong("proximo", 0L);
        ConfigurationSection s = y.getConfigurationSection("rarezas");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                try { mapa.put(k, Rareza.valueOf(s.getString(k, "COMUN"))); } catch (IllegalArgumentException ignorada) { }
            }
        }
        if (proximo <= System.currentTimeMillis() || mapa.isEmpty()) rerollear(false);
    }

    Rareza de(Accion a) { return mapa.computeIfAbsent(a.id(), k -> sortear()); }

    long msRestantes() { return Math.max(0, proximo - System.currentTimeMillis()); }

    /** Se llama seguido: si ya paso la hora, renueva. */
    void revisar() {
        if (System.currentTimeMillis() >= proximo) rerollear(true);
    }

    private Rareza sortear() {
        ConfigurationSection s = pl.getConfig().getConfigurationSection("rarezas.pesos");
        double total = 0;
        double[] w = new double[Rareza.values().length];
        for (Rareza r : Rareza.values()) {
            w[r.ordinal()] = s == null ? 1 : Math.max(0, s.getDouble(r.name(), 1));
            total += w[r.ordinal()];
        }
        double x = azar.nextDouble() * total;
        for (Rareza r : Rareza.values()) {
            x -= w[r.ordinal()];
            if (x < 0) return r;
        }
        return Rareza.COMUN;
    }

    void rerollear(boolean avisar) {
        mapa.clear();
        for (Accion a : pl.catalogo().todas()) mapa.put(a.id(), sortear());
        proximo = siguiente();
        guardar();
        pl.getLogger().info("Rarezas renovadas. Proxima renovacion en " + Msg.tiempo(msRestantes()));
        if (avisar) anunciar();
    }

    private long siguiente() {
        int hora = Math.max(0, Math.min(23, pl.getConfig().getInt("reroll-hora", 6)));
        ZonedDateTime ahora = ZonedDateTime.now(ZONA);
        ZonedDateTime t = ahora.withHour(hora).withMinute(0).withSecond(0).withNano(0);
        if (!t.isAfter(ahora)) t = t.plusDays(1);
        return t.toInstant().toEpochMilli();
    }

    private void anunciar() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Perfil f = pl.perfiles().existente(p);
            Component cuerpo = Component.text("¡Se renovaron las rarezas de las acciones! ", NamedTextColor.RED, TextDecoration.BOLD)
                    .append(Component.text("Mirá qué conviene hoy en /trabajos.", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false));
            p.sendMessage(cuerpo);
            if (f != null && f.activo != null) {
                int leg = 0, epi = 0;
                for (Accion a : pl.catalogo().de(f.activo)) {
                    Rareza r = de(a);
                    if (r == Rareza.LEGENDARIA) leg++;
                    else if (r == Rareza.EPICA) epi++;
                }
                Msg.aviso(p, Component.text("En " + f.activo.nombre + " hoy hay ", NamedTextColor.GRAY)
                        .append(Component.text(leg + " legendarias", Rareza.LEGENDARIA.color))
                        .append(Component.text(" y ", NamedTextColor.GRAY))
                        .append(Component.text(epi + " épicas", Rareza.EPICA.color))
                        .append(Component.text(".", NamedTextColor.GRAY)));
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
            }
        }
    }

    void guardar() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("proximo", proximo);
        for (Map.Entry<String, Rareza> e : mapa.entrySet()) y.set("rarezas." + e.getKey().replace('.', '_'), e.getValue().name());
        try { y.save(archivo); } catch (IOException e) { pl.getLogger().warning("No pude guardar estado.yml: " + e); }
    }
}
