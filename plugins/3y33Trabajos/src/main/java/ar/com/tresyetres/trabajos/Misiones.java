package ar.com.tresyetres.trabajos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Misiones de entregar items: diaria aleatoria, diaria elegida (1 de 4), semanal personal, global semanal y racha.
 * Van atadas al trabajo activo: solo se entregan con ese trabajo puesto y, si lo abandonas, las que no cobraste se pierden.
 */
final class Misiones {
    static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    static final int ALEATORIA = 0, ELEGIDA = 1, SEMANAL = 2;

    /** Estado de la mision global semanal de un trabajo (de todo el servidor). */
    static final class Global {
        long semana;
        double objetivo;
        long total;
        long totalAnterior;
        boolean completada;
        long bonoHasta;
        final Map<UUID, Integer> aportes = new HashMap<>();
        final Set<UUID> cobraron = new HashSet<>();
    }

    private final TrabajosPlugin pl;
    private final File archivoGlobal;
    private final File archivoConfig;
    private final Random azar = new Random();
    private final Map<Trabajo, Global> globales = new EnumMap<>(Trabajo.class);
    private final File archivoEntregas;
    private YamlConfiguration cfg = new YamlConfiguration();
    private YamlConfiguration ent = new YamlConfiguration();
    private final Map<Trabajo, Map<Material, Integer>> puntos = new EnumMap<>(Trabajo.class);
    private boolean sucio;

    Misiones(TrabajosPlugin pl) {
        this.pl = pl;
        this.archivoGlobal = new File(pl.getDataFolder(), "misiones-globales.yml");
        this.archivoConfig = new File(pl.getDataFolder(), "misiones.yml");
        this.archivoEntregas = new File(pl.getDataFolder(), "entregas.yml");
    }

    // ---------- carga y guardado ----------

    void cargar() {
        if (!archivoConfig.exists()) pl.saveResource("misiones.yml", false);
        cfg = YamlConfiguration.loadConfiguration(archivoConfig);
        if (!archivoEntregas.exists()) pl.saveResource("entregas.yml", false);
        ent = YamlConfiguration.loadConfiguration(archivoEntregas);
        cargarPuntos();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(archivoGlobal);
        // la global de antes contaba acciones, la de ahora cuenta puntos de items entregados: se empieza de cero una sola vez
        boolean viejo = archivoGlobal.exists() && !"entrega".equals(y.getString("modo"));
        if (viejo) pl.getLogger().info("Las globales eran por acciones: se reinician para pasar a entrega de items.");
        for (Trabajo t : Trabajo.values()) {
            Global g = globales.computeIfAbsent(t, k -> new Global());
            ConfigurationSection s = viejo ? null : y.getConfigurationSection(t.id);
            if (s == null) continue;
            g.semana = s.getLong("semana", 0L);
            g.objetivo = s.getDouble("objetivo", 0);
            g.total = s.getLong("total", 0L);
            g.totalAnterior = s.getLong("total-anterior", 0L);
            g.completada = s.getBoolean("completada", false);
            g.bonoHasta = s.getLong("bono-hasta", 0L);
            g.aportes.clear();
            g.cobraron.clear();
            ConfigurationSection a = s.getConfigurationSection("aportes");
            if (a != null) for (String k : a.getKeys(false)) {
                try { g.aportes.put(UUID.fromString(k), a.getInt(k)); } catch (IllegalArgumentException ignorada) { }
            }
            for (String k : s.getStringList("cobraron")) {
                try { g.cobraron.add(UUID.fromString(k)); } catch (IllegalArgumentException ignorada) { }
            }
        }
        revisar();
    }

    void guardar() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("modo", "entrega");
        for (Map.Entry<Trabajo, Global> e : globales.entrySet()) {
            Global g = e.getValue();
            String b = e.getKey().id;
            y.set(b + ".semana", g.semana);
            y.set(b + ".objetivo", g.objetivo);
            y.set(b + ".total", g.total);
            y.set(b + ".total-anterior", g.totalAnterior);
            y.set(b + ".completada", g.completada);
            y.set(b + ".bono-hasta", g.bonoHasta);
            for (Map.Entry<UUID, Integer> a : g.aportes.entrySet()) y.set(b + ".aportes." + a.getKey(), a.getValue());
            List<String> c = new ArrayList<>();
            for (UUID u : g.cobraron) c.add(u.toString());
            y.set(b + ".cobraron", c);
        }
        try { y.save(archivoGlobal); } catch (IOException ex) { pl.getLogger().warning("No pude guardar misiones-globales.yml: " + ex); }
        sucio = false;
    }

    /** Cada tanto: guarda si hubo cambios. */
    void guardarSiHaceFalta() { if (sucio) guardar(); }

    // ---------- tiempos ----------

    private int horaReset() { return Math.max(0, Math.min(23, pl.getConfig().getInt("reroll-hora", 6))); }

    /** Numero del dia de misiones: cambia a la hora del reroll. */
    long diaActual() { return ZonedDateTime.now(ZONA).minusHours(horaReset()).toLocalDate().toEpochDay(); }

    /** Numero de la semana de misiones: el lunes en que empezo (a las 0:00). */
    long semanaActual() { return LocalDate.now(ZONA).with(DayOfWeek.MONDAY).toEpochDay(); }

    long msHastaDia() {
        ZonedDateTime ahora = ZonedDateTime.now(ZONA);
        ZonedDateTime t = ahora.withHour(horaReset()).withMinute(0).withSecond(0).withNano(0);
        if (!t.isAfter(ahora)) t = t.plusDays(1);
        return Duration.between(ahora, t).toMillis();
    }

    long msHastaSemana() {
        ZonedDateTime ahora = ZonedDateTime.now(ZONA);
        ZonedDateTime t = ahora.toLocalDate().with(DayOfWeek.MONDAY).plusWeeks(1).atStartOfDay(ZONA);
        return Duration.between(ahora, t).toMillis();
    }

    // ---------- mision personal: generar ----------

    private Rareza sortearRareza() {
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

    private Perfil.Mision generar(Trabajo t, Rareza r) {
        ConfigurationSection s = cfg.getConfigurationSection("pools." + t.id + "." + r.name());
        List<String> claves = s == null ? new ArrayList<>() : new ArrayList<>(s.getKeys(false));
        while (!claves.isEmpty()) {
            String k = claves.remove(azar.nextInt(claves.size()));
            Material m = Material.matchMaterial(k);
            if (m != null) return new Perfil.Mision(m, Math.max(1, s.getInt(k, 1)), r);
        }
        return new Perfil.Mision(Material.COBBLESTONE, 64, r);   // nunca deberia pasar: el pool esta vacio o mal escrito
    }

    /** Genera las misiones del dia y de la semana si todavia no estan (o si ya cambio el dia). */
    void revisar(Perfil.Progreso g, Trabajo t) {
        long dia = diaActual();
        if (g.dia != dia || g.aleatoria == null) {
            g.dia = dia;
            g.aleatoria = generar(t, sortearRareza());
            for (int i = 0; i < 4; i++) g.opciones[i] = generar(t, Rareza.values()[i]);
            g.elegida = null;
            g.elegidaPerdida = false;
        }
        long semana = semanaActual();
        if (g.semana != semana || g.semanal == null) {
            g.semana = semana;
            Perfil.Mision m = generar(t, sortearRareza());
            m.meta = m.meta * Math.max(1, cfg.getInt("semanal-factor", 8));
            g.semanal = m;
        }
    }

    // ---------- plata de cada mision ----------

    double base(int tipo, Rareza r) {
        if (tipo == SEMANAL) return cfg.getDouble("recompensas.semanal-base", 1500) * r.mult;
        List<Double> l = cfg.getDoubleList("recompensas." + (tipo == ALEATORIA ? "aleatoria" : "elegida"));
        return r.ordinal() < l.size() ? l.get(r.ordinal()) : 100;
    }

    /** Lo que cobra de verdad: la base por el multiplicador personal. */
    double recompensa(int tipo, Perfil.Mision m, Perfil.Progreso g) { return base(tipo, m.rareza) * pl.pago().personal(g); }

    // ---------- racha ----------

    /** Dias seguidos de racha que siguen vivos (si dejaste pasar un dia entero sin completar las dos, se corta). */
    int racha(Perfil.Progreso g) { return diaActual() - g.rachaDia <= 1 ? g.racha : 0; }

    double bonoRacha(Perfil.Progreso g) {
        int max = cfg.getInt("racha.maximo-dias", 10);
        return cfg.getDouble("racha.por-dia", 0.02) * Math.min(racha(g), max);
    }

    boolean diariasHechas(Perfil.Progreso g) {
        return g.aleatoria != null && g.aleatoria.estado == Perfil.Mision.COMPLETA
                && g.elegida != null && g.elegida.estado == Perfil.Mision.COMPLETA;
    }

    // ---------- entregar ----------

    /** Items "limpios" de ese material (sin nombre ni encantamientos) que tiene en el inventario. */
    private int contar(Player p, Material m) {
        int n = 0;
        ItemStack molde = new ItemStack(m);
        for (ItemStack it : p.getInventory().getStorageContents()) if (it != null && it.isSimilar(molde)) n += it.getAmount();
        return n;
    }

    private void sacar(Player p, Material m, int cantidad) {
        ItemStack molde = new ItemStack(m);
        ItemStack[] cont = p.getInventory().getStorageContents();
        for (int i = 0; i < cont.length && cantidad > 0; i++) {
            ItemStack it = cont[i];
            if (it == null || !it.isSimilar(molde)) continue;
            int sacado = Math.min(cantidad, it.getAmount());
            cantidad -= sacado;
            if (it.getAmount() == sacado) cont[i] = null; else it.setAmount(it.getAmount() - sacado);
        }
        p.getInventory().setStorageContents(cont);
    }

    /** Entrega lo que tenga el jugador para esa mision. true si cambio algo. */
    boolean entregar(Player p, Trabajo t, int tipo, Perfil.Mision m) {
        Perfil f = pl.perfiles().de(p);
        if (!puedeEntregar(p, t)) return false;
        if (m == null || m.estado != Perfil.Mision.ACTIVA) { Msg.error(p, "Esa misión ya no está activa."); return false; }
        int falta = m.meta - m.entregado;
        int tiene = contar(p, m.item);
        int n = Math.min(falta, tiene);
        if (n <= 0) {
            Msg.error(p, "No tenés ninguno de esos items (sin nombre ni encantamientos) en el inventario.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return false;
        }
        sacar(p, m.item, n);
        m.entregado += n;
        if (m.entregado >= m.meta) completar(p, f, t, tipo, m);
        else {
            Msg.aviso(p, Component.text("Entregaste " + n + " · ", NamedTextColor.GRAY)
                    .append(Component.text(m.entregado + "/" + m.meta, NamedTextColor.YELLOW)));
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
        }
        pl.perfiles().guardar(f);
        return true;
    }

    private void completar(Player p, Perfil f, Trabajo t, int tipo, Perfil.Mision m) {
        Perfil.Progreso g = f.de(t);
        m.estado = Perfil.Mision.COMPLETA;
        double pesos = recompensa(tipo, m, g);
        pl.pago().depositar(p, f, pesos);
        p.showTitle(Title.title(
                Component.text("¡Misión cumplida!", m.rareza.color),
                Component.text("+" + Msg.dinero(pesos), NamedTextColor.GREEN),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1600), Duration.ofMillis(500))));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        if (tipo != SEMANAL && diariasHechas(g)) sumarRacha(p, g, t);
    }

    private void sumarRacha(Player p, Perfil.Progreso g, Trabajo t) {
        long dia = diaActual();
        if (g.rachaDia == dia) return;
        g.racha = (dia - g.rachaDia == 1) ? g.racha + 1 : 1;
        g.rachaDia = dia;
        Msg.aviso(p, Component.text("¡Completaste las dos diarias! Racha de " + g.racha + (g.racha == 1 ? " día" : " días"), NamedTextColor.GOLD)
                .append(Component.text("  (+" + Msg.num(bonoRacha(g) * 100, 0) + "% de multiplicador)", NamedTextColor.YELLOW)));
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
    }

    /** Elige la diaria elegida entre las 4 opciones. */
    boolean elegir(Player p, Trabajo t, int indice) {
        Perfil f = pl.perfiles().de(p);
        Perfil.Progreso g = f.de(t);
        revisar(g, t);
        if (f.activo != t) { Msg.error(p, "Tenés que tener el trabajo de " + t.nombre + "."); return false; }
        if (g.elegida != null || g.elegidaPerdida) { Msg.error(p, "Ya elegiste tu misión de hoy."); return false; }
        Perfil.Mision m = g.opciones[indice];
        if (m == null) return false;
        g.elegida = new Perfil.Mision(m.item, m.meta, m.rareza);
        pl.perfiles().guardar(f);
        return true;
    }

    /** Al abandonar el trabajo: lo que no cobraste de ese trabajo se pierde. */
    void perderPendientes(Perfil f, Trabajo t) {
        Perfil.Progreso g = f.de(t);
        if (g.dia != diaActual() && g.semana != semanaActual()) return;
        for (Perfil.Mision m : new Perfil.Mision[]{g.aleatoria, g.elegida, g.semanal})
            if (m != null && m.estado == Perfil.Mision.ACTIVA) m.estado = Perfil.Mision.PERDIDA;
        if (g.elegida == null && g.dia == diaActual()) g.elegidaPerdida = true;
    }

    /** Cuantas misiones sin cobrar tiene de ese trabajo (para avisar al abandonar). */
    int pendientes(Perfil f, Trabajo t) {
        Perfil.Progreso g = f.de(t);
        int n = 0;
        if (g.dia == diaActual()) {
            if (g.aleatoria != null && g.aleatoria.estado == Perfil.Mision.ACTIVA) n++;
            if (g.elegida != null && g.elegida.estado == Perfil.Mision.ACTIVA) n++;
        }
        if (g.semana == semanaActual() && g.semanal != null && g.semanal.estado == Perfil.Mision.ACTIVA) n++;
        return n;
    }

    // ---------- global semanal ----------

    Global global(Trabajo t) { return globales.computeIfAbsent(t, k -> new Global()); }

    /** Cambia de semana si hace falta: el objetivo nuevo es el mayor entre el minimo y un % del total de la semana pasada. */
    void revisar() {
        long semana = semanaActual();
        for (Trabajo t : Trabajo.values()) {
            Global g = global(t);
            if (g.semana == semana && g.objetivo > 0) continue;
            long anterior = g.semana == 0 ? 0 : g.total;
            g.totalAnterior = anterior;
            double minimo = ent.getDouble("minimo." + t.id, 5000);
            g.objetivo = Math.max(minimo, Math.floor(anterior * cfg.getDouble("global.porcentaje-anterior", 0.6)));
            g.semana = semana;
            g.total = 0;
            g.completada = false;
            g.aportes.clear();
            g.cobraron.clear();
            sucio = true;
            pl.getLogger().info("Global semanal de " + t.nombre + ": objetivo " + (long) g.objetivo);
        }
    }

    // ---------- entrega de items a la global ----------

    private void cargarPuntos() {
        puntos.clear();
        for (Trabajo t : Trabajo.values()) {
            Map<Material, Integer> m = new java.util.LinkedHashMap<>();
            ConfigurationSection s = ent.getConfigurationSection("puntos." + t.id);
            if (s != null) for (String k : s.getKeys(false)) {
                int pts = s.getInt(k, 0);
                if (pts <= 0) continue;
                if (k.equals("TRONCOS")) {
                    for (Material mat : Material.values()) {
                        String n = mat.name();
                        if (!mat.isItem() || mat.isLegacy()) continue;
                        if (n.endsWith("_LOG") || n.endsWith("_STEM") || n.endsWith("_WOOD") || n.endsWith("_HYPHAE") || n.equals("BAMBOO_BLOCK") || n.equals("STRIPPED_BAMBOO_BLOCK")) m.put(mat, pts);
                    }
                    continue;
                }
                Material mat = Material.matchMaterial(k);
                if (mat == null) pl.getLogger().warning("entregas.yml: material desconocido " + k + " en " + t.id);
                else m.put(mat, pts);
            }
            puntos.put(t, m);
        }
    }

    /** Puntos que vale un item para la global de ese trabajo (0 si no sirve). */
    int puntosDe(Trabajo t, Material m) { return puntos.getOrDefault(t, Map.of()).getOrDefault(m, 0); }

    /** Los items que sirven, del que mas vale al que menos. */
    List<Map.Entry<Material, Integer>> aceptados(Trabajo t) {
        List<Map.Entry<Material, Integer>> l = new ArrayList<>(puntos.getOrDefault(t, Map.of()).entrySet());
        l.sort((a, b) -> b.getValue() != a.getValue().intValue() ? Integer.compare(b.getValue(), a.getValue()) : a.getKey().name().compareTo(b.getKey().name()));
        return l;
    }

    boolean soloEnNpc() { return ent.getBoolean("solo-en-npc", true); }

    /** true si puede entregar: tiene el trabajo puesto y, si hace falta, esta cerca del NPC. Si no, le avisa. */
    boolean puedeEntregar(Player p, Trabajo t) {
        Perfil f = pl.perfiles().de(p);
        if (f.activo != t) { Msg.error(p, "Tenés que tener el trabajo de " + t.nombre + " para entregar."); return false; }
        if (!enNpc(p, t)) { Msg.error(p, "Las entregas se hacen en el NPC de " + t.nombre + ". Acercate y hacele clic."); return false; }
        return true;
    }

    /** true si no hace falta NPC o si el jugador esta cerca del NPC de ese trabajo. */
    boolean enNpc(Player p, Trabajo t) { return !soloEnNpc() || pl.npcs().cercaDeNpc(p, t, ent.getDouble("distancia-npc", 8)); }

    /** Entrega a la global lo que tenga el jugador (o solo lo que tiene en la mano). Devuelve los puntos sumados. */
    int entregarGlobal(Player p, Trabajo t, boolean soloMano) {
        if (!puedeEntregar(p, t)) return 0;
        Global g = global(t);
        if (g.completada) { Msg.error(p, "La misión global de esta semana ya se completó. ¡Cobrá tu parte!"); return 0; }
        Map<Material, Integer> val = puntos.getOrDefault(t, Map.of());
        ItemStack[] cont = p.getInventory().getStorageContents();
        // primero lo que menos vale, asi no se va un diamante cuando faltaban pocos puntos
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < cont.length; i++) {
            ItemStack it = cont[i];
            if (it == null || !val.containsKey(it.getType()) || !it.isSimilar(new ItemStack(it.getType()))) continue;
            if (soloMano && i != p.getInventory().getHeldItemSlot()) continue;
            idx.add(i);
        }
        if (idx.isEmpty()) {
            Msg.error(p, soloMano ? "Lo que tenés en la mano no suma a esta misión." : "No tenés nada que sume a esta misión (items sin nombre ni encantamientos).");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return 0;
        }
        idx.sort((a, b) -> Integer.compare(val.get(cont[a].getType()), val.get(cont[b].getType())));
        long faltan = (long) Math.ceil(g.objetivo - g.total);
        long sumados = 0;
        int items = 0;
        for (int i : idx) {
            if (sumados >= faltan) break;
            ItemStack it = cont[i];
            int v = val.get(it.getType());
            int n = (int) Math.min(it.getAmount(), (faltan - sumados + v - 1) / v);
            sumados += (long) n * v;
            items += n;
            if (n == it.getAmount()) cont[i] = null; else it.setAmount(it.getAmount() - n);
        }
        p.getInventory().setStorageContents(cont);
        g.total += sumados;
        g.aportes.merge(p.getUniqueId(), (int) sumados, Integer::sum);
        sucio = true;
        Msg.aviso(p, Component.text("Entregaste " + items + " items · ", NamedTextColor.GRAY)
                .append(Component.text("+" + sumados + " puntos", NamedTextColor.GREEN))
                .append(Component.text("  (" + Msg.num(Math.min(g.total, g.objetivo), 0) + "/" + Msg.num(g.objetivo, 0) + ")", NamedTextColor.YELLOW)));
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.1f);
        if (!g.completada && g.total >= g.objetivo) completarGlobal(t, g);
        return (int) sumados;
    }

    private void completarGlobal(Trabajo t, Global g) {
        g.completada = true;
        g.bonoHasta = System.currentTimeMillis() + cfg.getLong("global.bono-horas", 48) * 3_600_000L;
        guardar();
        int pct = (int) Math.round(cfg.getDouble("global.bono", 0.20) * 100);
        Bukkit.broadcast(Component.text("★ ", NamedTextColor.GOLD)
                .append(Component.text("¡Se completó la misión global de ", NamedTextColor.GRAY))
                .append(Component.text(t.nombre, t.color))
                .append(Component.text("! Todos los " + t.nombre + " tienen +" + pct + "% de pago por " + cfg.getLong("global.bono-horas", 48) + " horas. Cobrá tu parte en /trabajos.", NamedTextColor.GRAY)));
        for (Player o : Bukkit.getOnlinePlayers()) o.playSound(o.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
    }

    /** Suma sobre el pago (0,20 = +20%) para los de este trabajo mientras dure el bono global. */
    double bonoGlobal(Trabajo t) {
        Global g = globales.get(t);
        return g != null && System.currentTimeMillis() < g.bonoHasta ? cfg.getDouble("global.bono", 0.20) : 0;
    }

    int aporteDe(Trabajo t, Player p) { return global(t).aportes.getOrDefault(p.getUniqueId(), 0); }

    int aporteMinimo() { return cfg.getInt("global.aporte-minimo", 100); }

    double pagoGlobal() { return cfg.getDouble("global.pago", 600); }

    boolean puedeCobrarGlobal(Trabajo t, Player p) {
        Global g = global(t);
        return g.completada && aporteDe(t, p) >= aporteMinimo() && !g.cobraron.contains(p.getUniqueId());
    }

    boolean cobrarGlobal(Player p, Trabajo t) {
        Perfil f = pl.perfiles().de(p);
        if (f.activo != t) { Msg.error(p, "Tenés que tener el trabajo de " + t.nombre + " para cobrar."); return false; }
        if (!puedeCobrarGlobal(t, p)) { Msg.error(p, "Todavía no podés cobrar la misión global."); return false; }
        global(t).cobraron.add(p.getUniqueId());
        sucio = true;
        double pesos = pagoGlobal();
        pl.pago().depositar(p, f, pesos);
        pl.perfiles().guardar(f);
        Msg.aviso(p, Component.text("Cobraste tu parte de la misión global: ", NamedTextColor.GRAY)
                .append(Component.text("+" + Msg.dinero(pesos), NamedTextColor.GREEN)));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
        return true;
    }

    // ---------- staff ----------

    /** Vuelve a generar las misiones de ese jugador (sirve para probar). */
    void reiniciar(Perfil f) {
        for (Trabajo t : Trabajo.values()) {
            Perfil.Progreso g = f.de(t);
            g.dia = 0;
            g.semana = 0;
            g.aleatoria = null;
            g.elegida = null;
            g.semanal = null;
            g.elegidaPerdida = false;
        }
    }

    void sumarGlobal(Trabajo t, long n) {
        Global g = global(t);
        g.total += n;
        sucio = true;
        if (!g.completada && g.total >= g.objetivo) completarGlobal(t, g);
    }

    /** Linea de lore de una mision: "Entregá: 64 × Hierro en bruto". */
    static Component pedido(Perfil.Mision m) {
        return Iconos.txt("Entregá: " + m.meta + " × ", NamedTextColor.WHITE)
                .append(Component.translatable(m.item.translationKey()).color(m.rareza.color).decoration(TextDecoration.ITALIC, false));
    }
}
