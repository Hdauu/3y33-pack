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
import java.util.LinkedHashMap;
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
        double bonoPct;                  // bono (0,20 = +20%) que dejo la mision completada
        long completadaEn;
        String item;                     // Material de la mision actual
        String rareza;                   // id de la rareza (entregas.yml)
        double factor = 1.0;             // se ajusta solo segun lo rapido que se completan las misiones
        final List<String> historial = new ArrayList<>();
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
    private boolean sucio;
    /** Lo que ya se gano de globales terminadas y todavia no se le dio a quien no estaba conectado: [plata, xp, 1 si la mision se completo]. */
    private final Map<UUID, Map<Trabajo, double[]>> pendientes = new HashMap<>();

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
        cargarPool();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(archivoGlobal);
        // las globales de antes eran por acciones o por puntos: ahora cada una pide un item, asi que se empieza de cero una sola vez
        boolean viejo = archivoGlobal.exists() && !"item".equals(y.getString("modo"));
        if (viejo) pl.getLogger().info("Las globales pasan a pedir un solo item: se reinician.");
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
            g.item = s.getString("item");
            g.rareza = s.getString("rareza");
            g.factor = s.getDouble("factor", 1.0);
            g.bonoPct = s.getDouble("bono-pct", 0);
            g.completadaEn = s.getLong("completada-en", 0L);
            g.historial.clear();
            g.historial.addAll(s.getStringList("historial"));
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
        pendientes.clear();
        ConfigurationSection pe = y.getConfigurationSection("pendientes");
        if (pe != null) for (String k : pe.getKeys(false)) {
            UUID u;
            try { u = UUID.fromString(k); } catch (IllegalArgumentException ignorada) { continue; }
            for (Trabajo t : Trabajo.values()) {
                ConfigurationSection c = pe.getConfigurationSection(k + "." + t.id);
                if (c == null) continue;
                pendientes.computeIfAbsent(u, x -> new EnumMap<>(Trabajo.class)).put(t, new double[]{c.getDouble("plata"), c.getDouble("xp"), c.getDouble("completa")});
            }
        }
        revisar();
    }

    void guardar() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("modo", "item");
        for (Map.Entry<Trabajo, Global> e : globales.entrySet()) {
            Global g = e.getValue();
            String b = e.getKey().id;
            y.set(b + ".semana", g.semana);
            y.set(b + ".objetivo", g.objetivo);
            y.set(b + ".total", g.total);
            y.set(b + ".total-anterior", g.totalAnterior);
            y.set(b + ".completada", g.completada);
            y.set(b + ".bono-hasta", g.bonoHasta);
            y.set(b + ".item", g.item);
            y.set(b + ".rareza", g.rareza);
            y.set(b + ".factor", g.factor);
            y.set(b + ".bono-pct", g.bonoPct);
            y.set(b + ".completada-en", g.completadaEn);
            y.set(b + ".historial", g.historial);
            for (Map.Entry<UUID, Integer> a : g.aportes.entrySet()) y.set(b + ".aportes." + a.getKey(), a.getValue());
            List<String> c = new ArrayList<>();
            for (UUID u : g.cobraron) c.add(u.toString());
            y.set(b + ".cobraron", c);
        }
        for (Map.Entry<UUID, Map<Trabajo, double[]>> pe : pendientes.entrySet())
            for (Map.Entry<Trabajo, double[]> t : pe.getValue().entrySet()) {
                String b = "pendientes." + pe.getKey() + "." + t.getKey().id;
                y.set(b + ".plata", t.getValue()[0]);
                y.set(b + ".xp", t.getValue()[1]);
                y.set(b + ".completa", t.getValue()[2]);
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

    /** Dia (epoch) en que arranco el ciclo actual de la global. Los ciclos arrancan en lunes, cada ciclo-dias dias. */
    long cicloActual() {
        long ciclo = Math.max(1, ent.getInt("ciclo-dias", 3));
        long hoy = LocalDate.now(ZONA).toEpochDay();
        long ancla = LocalDate.of(2026, 1, 5).toEpochDay();   // un lunes
        return hoy - Math.floorMod(hoy - ancla, ciclo);
    }

    long msHastaCiclo() {
        ZonedDateTime ahora = ZonedDateTime.now(ZONA);
        long fin = cicloActual() + Math.max(1, ent.getInt("ciclo-dias", 3));
        return Duration.between(ahora, LocalDate.ofEpochDay(fin).atStartOfDay(ZONA)).toMillis();
    }

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

    /** XP que da una mision personal: un multiplo de su plata (xp-por-peso en misiones.yml). */
    double recompensaXp(int tipo, Perfil.Mision m, Perfil.Progreso g) { return Math.floor(recompensa(tipo, m, g) * cfg.getDouble("xp-por-peso", 2.0)); }

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
        if (m == null || m.estado != Perfil.Mision.ACTIVA) { Msg.error(p, "Esa misión ya no está activa."); fallo(p); return false; }
        int falta = m.meta - m.entregado;
        int tiene = contar(p, m.item);
        int n = Math.min(falta, tiene);
        if (n <= 0) {
            Msg.aviso(p, Component.text("No tenés ", NamedTextColor.RED).append(Component.translatable(m.item.translationKey()))
                    .append(Component.text(" (sin nombre ni encantamientos) en el inventario. Ojo con los parecidos: un bloque infestado no es el común.", NamedTextColor.RED)));
            fallo(p);
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
        double xp = recompensaXp(tipo, m, g);
        pl.pago().depositar(p, f, pesos);
        pl.balance().plata(t, Balance.MISIONES, pesos);
        p.showTitle(Title.title(
                Component.text("¡Misión cumplida!", m.rareza.color),
                Component.text("+" + Msg.dinero(pesos), NamedTextColor.GREEN).append(Component.text("   +" + Msg.num(xp, 0) + " XP", NamedTextColor.AQUA)),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1600), Duration.ofMillis(500))));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        pl.pago().subirXp(p, f, t, xp);
        pl.pago().avisarNivel(p, t, g);
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

    // ---------- global ----------

    Global global(Trabajo t) { return globales.computeIfAbsent(t, k -> new Global()); }

    /** Rareza de una mision global: segun lo dificil que es el item. Define la plata, el XP (un % de la plata) y el bono. */
    static final class Rar {
        final String id, nombre;
        final int peso;
        final double hasta, pozo, bono;
        final NamedTextColor color;
        Rar(String id, String nombre, double hasta, int peso, double pozo, double bono, NamedTextColor color) {
            this.id = id; this.nombre = nombre; this.hasta = hasta; this.peso = peso; this.pozo = pozo; this.bono = bono; this.color = color;
        }
    }

    private static final NamedTextColor[] COLORES_RAREZA = {NamedTextColor.GRAY, NamedTextColor.AQUA, NamedTextColor.LIGHT_PURPLE, NamedTextColor.GOLD};
    private final List<Rar> rarezas = new ArrayList<>();
    private final Map<Trabajo, Map<Material, Integer>> pool = new EnumMap<>(Trabajo.class);

    private static final String[] MADERAS = {"OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK", "MANGROVE", "CHERRY", "PALE_OAK", "BAMBOO", "CRIMSON", "WARPED"};

    private static boolean esItem(Material m) { return m.isItem() && !m.isLegacy(); }

    /** Pone (o saca, con 0) todos los materiales que cubre una clave: un material, un comodin con * o algo con {M}. */
    private void aplicar(Map<Material, Integer> dest, String clave, int pts, Trabajo t, String grupo, boolean silencio) {
        if (clave.contains("{M}")) {
            for (String madera : MADERAS) aplicar(dest, clave.replace("{M}", madera), pts, t, grupo, true);
            return;
        }
        if (clave.contains("*")) {
            java.util.regex.Pattern pat = java.util.regex.Pattern.compile("^" + clave.toUpperCase(java.util.Locale.ROOT).replace("*", ".*") + "$");
            for (Material mat : Material.values()) {
                if (!esItem(mat) || !pat.matcher(mat.name()).matches()) continue;
                if (pts > 0) dest.put(mat, pts); else dest.remove(mat);
            }
            return;
        }
        Material mat = Material.matchMaterial(clave);
        if (mat == null || !esItem(mat)) {
            // con {M} es normal que algunas maderas no tengan ese item; lo demas, avisa para corregir el nombre
            if (pts > 0 && !silencio) pl.getLogger().warning("entregas.yml: material desconocido " + clave + " (" + t.id + "/" + grupo + ")");
            return;
        }
        if (pts > 0) dest.put(mat, pts); else dest.remove(mat);
    }

    /** Lee las rarezas y arma, para cada trabajo, el conjunto de items que pueden salir con su valor de dificultad. */
    private void cargarPool() {
        rarezas.clear();
        ConfigurationSection rs = ent.getConfigurationSection("rarezas");
        if (rs != null) {
            int i = 0;
            for (String id : rs.getKeys(false)) {
                ConfigurationSection c = rs.getConfigurationSection(id);
                if (c == null) continue;
                rarezas.add(new Rar(id, c.getString("nombre", id), c.getDouble("hasta", 1.0), Math.max(1, c.getInt("peso", 1)),
                        c.getDouble("pozo", 80000), c.getDouble("bono", 0.20), COLORES_RAREZA[Math.min(i, COLORES_RAREZA.length - 1)]));
                i++;
            }
        }
        if (rarezas.isEmpty()) rarezas.add(new Rar("comun", "Común", 1.0, 1, 80000, 0.20, NamedTextColor.GRAY));
        pool.clear();
        for (Trabajo t : Trabajo.values()) {
            Map<Material, Integer> todo = new LinkedHashMap<>();
            ConfigurationSection s = ent.getConfigurationSection("grupos." + t.id);
            if (s != null) for (String id : s.getKeys(false)) {
                ConfigurationSection items = s.getConfigurationSection(id);
                if (items == null) continue;
                Map<Material, Integer> grupo = new LinkedHashMap<>();
                for (String k : items.getKeys(false)) aplicar(grupo, k, items.getInt(k, 0), t, id, false);
                for (Map.Entry<Material, Integer> e : grupo.entrySet()) todo.merge(e.getKey(), e.getValue(), Math::max);
            }
            pool.put(t, todo);
            if (todo.isEmpty()) pl.getLogger().warning("entregas.yml: " + t.id + " no tiene items para las misiones globales.");
        }
    }

    /** Rareza de un item segun cuantos items del trabajo valen menos que el: cada rareza cubre una franja de la lista ordenada por valor. */
    private int indiceRareza(Trabajo t, int valor) {
        Map<Material, Integer> todo = pool.getOrDefault(t, Map.of());
        if (todo.isEmpty()) return 0;
        int menores = 0;
        for (int v : todo.values()) if (v < valor) menores++;
        double fraccion = (double) menores / todo.size();
        for (int i = 0; i < rarezas.size(); i++) if (fraccion < rarezas.get(i).hasta) return i;
        return rarezas.size() - 1;
    }

    /** Cambia de ciclo si hace falta: ajusta la dificultad segun como salio el anterior y sortea la mision nueva de cada trabajo. */
    void revisar() {
        long ciclo = cicloActual();
        for (Trabajo t : Trabajo.values()) {
            Global g = global(t);
            if (g.semana == ciclo && g.objetivo > 0) {
                Material actual = g.item == null ? null : Material.matchMaterial(g.item);
                boolean valida = actual != null && pool.getOrDefault(t, Map.of()).containsKey(actual);
                if (!valida && !pool.getOrDefault(t, Map.of()).isEmpty()) { nuevaMision(t, g); sucio = true; }
                continue;
            }
            liquidar(t, g);
            ajustarFactor(g);
            g.semana = ciclo;
            nuevaMision(t, g);
            sucio = true;
            pl.getLogger().info("Global de " + t.nombre + ": " + g.item + " x" + (long) g.objetivo + " (" + g.rareza + ", factor " + Msg.num(g.factor, 2) + ")");
        }
    }

    /**
     * Al terminar el ciclo: si la global se completo, a quien no cobro se le paga su parte sola; si no se completo, se paga una compensacion baja
     * (compensacion x el avance x el fondo, repartido por aporte). Los que no estan conectados la reciben al entrar.
     */
    private void liquidar(Trabajo t, Global g) {
        if (g.item == null || g.objetivo <= 0 || g.aportes.isEmpty()) return;
        double fraccion = g.completada ? 1.0 : ent.getDouble("compensacion", 0.10) * Math.min(1.0, g.total / g.objetivo);
        if (fraccion <= 0) return;
        double pozo = pozo(t), pozoXp = pozoXp(t);
        for (UUID u : new ArrayList<>(g.aportes.keySet())) {
            if (g.completada && g.cobraron.contains(u)) continue;
            double parte = parte(t, u);
            if (parte <= 0) continue;
            double plata = Math.floor(pozo * fraccion * parte), xp = Math.floor(pozoXp * fraccion * parte);
            if (plata <= 0 && xp <= 0) continue;
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) pagarGlobal(p, t, plata, xp, g.completada);
            else {
                double[] v = pendientes.computeIfAbsent(u, k -> new EnumMap<>(Trabajo.class)).computeIfAbsent(t, k -> new double[3]);
                v[0] += plata;
                v[1] += xp;
                if (g.completada) v[2] = 1;
            }
        }
        sucio = true;
    }

    private void pagarGlobal(Player p, Trabajo t, double plata, double xp, boolean completa) {
        Perfil f = pl.perfiles().de(p);
        pl.pago().depositar(p, f, plata);
        pl.pago().vaciar(p, f);
        pl.balance().plata(t, Balance.GLOBAL, plata);
        if (xp > 0) pl.pago().subirXp(p, f, t, xp);
        pl.perfiles().guardar(f);
        Msg.aviso(p, Component.text(completa ? "Tu parte de la misión global de " + t.nombre + " (se completó y no la habías cobrado): "
                        : "La misión global de " + t.nombre + " no se completó. Compensación por tu aporte: ", NamedTextColor.GRAY)
                .append(Component.text("+$" + Msg.num(plata, 0), NamedTextColor.GREEN))
                .append(Component.text("  +" + Msg.num(xp, 0) + " XP", NamedTextColor.AQUA)));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
    }

    /** Al entrar: entrega lo que le quedo pendiente de globales que terminaron mientras no estaba. */
    void pagarPendientes(Player p) {
        Map<Trabajo, double[]> m = pendientes.remove(p.getUniqueId());
        if (m == null) return;
        sucio = true;
        for (Map.Entry<Trabajo, double[]> e : m.entrySet()) pagarGlobal(p, e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2] > 0);
    }

    /** Si la mision anterior se completo rapido pide mas la proxima vez (cuanto mas rapido, mas); si casi nadie la hizo, pide menos. */
    private void ajustarFactor(Global g) {
        if (g.objetivo <= 0 || g.item == null) return;
        double f = g.factor;
        long dur = Math.max(1, ent.getInt("ciclo-dias", 3)) * 86_400_000L;
        if (g.completada) {
            long ini = LocalDate.ofEpochDay(g.semana).atStartOfDay(ZONA).toInstant().toEpochMilli();
            double usado = g.completadaEn > 0 ? Math.max(0.03, (double) (g.completadaEn - ini) / dur) : 0.7;
            // se apunta a que cueste casi todo el ciclo: si se completo en el 10% del tiempo, la proxima pide hasta 3 veces mas
            f *= Math.max(1.05, Math.min(3.0, 0.8 / usado));
        } else {
            double avance = g.total / g.objetivo;
            if (avance < 0.6) f *= Math.max(0.4, avance / 0.6);
        }
        g.factor = Math.max(0.05, Math.min(100.0, f));
    }

    /** Sortea la mision: primero la rareza (con sus pesos) y despues un item de esa rareza que no haya salido hace poco. */
    private void nuevaMision(Trabajo t, Global g) {
        g.total = 0;
        g.completada = false;
        g.completadaEn = 0;
        g.bonoPct = 0;
        g.aportes.clear();
        g.cobraron.clear();
        Map<Material, Integer> todo = pool.getOrDefault(t, Map.of());
        if (todo.isEmpty()) { g.item = null; g.rareza = null; g.objetivo = 1; return; }
        Set<String> recientes = new HashSet<>(g.historial.subList(Math.max(0, g.historial.size() - 10), g.historial.size()));
        List<List<Material>> porRareza = new ArrayList<>();
        for (int i = 0; i < rarezas.size(); i++) porRareza.add(new ArrayList<>());
        for (Map.Entry<Material, Integer> e : todo.entrySet()) {
            if (!recientes.contains(e.getKey().name())) porRareza.get(indiceRareza(t, e.getValue())).add(e.getKey());
        }
        int suma = 0;
        for (int i = 0; i < rarezas.size(); i++) if (!porRareza.get(i).isEmpty()) suma += rarezas.get(i).peso;
        if (suma == 0) {   // salieron todos hace poco: se vuelve a empezar
            for (Map.Entry<Material, Integer> e : todo.entrySet()) porRareza.get(indiceRareza(t, e.getValue())).add(e.getKey());
            for (int i = 0; i < rarezas.size(); i++) if (!porRareza.get(i).isEmpty()) suma += rarezas.get(i).peso;
        }
        int tiro = azar.nextInt(suma);
        for (int i = 0; i < rarezas.size(); i++) {
            List<Material> l = porRareza.get(i);
            if (l.isEmpty()) continue;
            tiro -= rarezas.get(i).peso;
            if (tiro < 0) { fijar(t, g, l.get(azar.nextInt(l.size()))); return; }
        }
    }

    /** Deja la mision pidiendo ese item, con la cantidad que sale de su dificultad. */
    private void fijar(Trabajo t, Global g, Material mat) {
        int valor = pool.getOrDefault(t, Map.of()).getOrDefault(mat, 4);
        Rar r = rarezas.get(indiceRareza(t, valor));
        double esfuerzo = ent.getDouble("esfuerzo." + t.id, 5000);
        g.item = mat.name();
        g.rareza = r.id;
        double exp = ent.getDouble("exponente-valor", 1.3);
        double cantidad = esfuerzo * g.factor / Math.pow(valor, exp);
        // tope: lo que se puede juntar y llevar razonablemente (los que no se apilan, como botes o picos, mucho menos)
        double tope = mat.getMaxStackSize() > 1 ? mat.getMaxStackSize() * (double) ent.getInt("max-pilas", 1500) : ent.getInt("max-no-apilables", 150);
        g.objetivo = Math.max(ent.getInt("cantidad-minima", 3), Math.round(Math.min(cantidad, tope)));
        g.historial.add(mat.name());
        while (g.historial.size() > 40) g.historial.remove(0);
    }

    /** Staff: fuerza el item de la mision (y la reinicia). */
    boolean fijarMision(Trabajo t, Material mat) {
        if (!esItem(mat)) return false;
        Global g = global(t);
        g.total = 0;
        g.completada = false;
        g.completadaEn = 0;
        g.bonoPct = 0;
        g.aportes.clear();
        g.cobraron.clear();
        fijar(t, g, mat);
        sucio = true;
        return true;
    }

    // ---------- lo que se ve de la mision actual ----------

    Material itemGlobal(Trabajo t) { String n = global(t).item; return n == null ? null : Material.matchMaterial(n); }

    Rar rarezaDe(Trabajo t) {
        String id = global(t).rareza;
        for (Rar r : rarezas) if (r.id.equals(id)) return r;
        return rarezas.get(0);
    }

    /** Nombre del item de la mision, traducido por el cliente y del color de su rareza. */
    Component itemGlobalNombre(Trabajo t) {
        Material m = itemGlobal(t);
        if (m == null) return Component.text("-", NamedTextColor.GRAY);
        return Component.translatable(m.translationKey()).color(rarezaDe(t).color).decoration(TextDecoration.ITALIC, false);
    }

    /** "Entregá 18.000 × Cobblestone" */
    Component pedidoGlobal(Trabajo t) {
        return Iconos.txt("Entregá " + Msg.num(global(t).objetivo, 0) + " × ", NamedTextColor.WHITE).append(itemGlobalNombre(t));
    }

    // ---------- entrega ----------

    int cicloDias() { return Math.max(1, ent.getInt("ciclo-dias", 3)); }

    boolean soloEnNpc() { return ent.getBoolean("solo-en-npc", true); }

    /** true si puede entregar: tiene el trabajo puesto y, si hace falta, esta cerca del NPC. Si no, le avisa. */
    /** Ruido de "no se pudo" para cualquier entrega que falla. */
    private void fallo(Player p) { p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f); }

    boolean puedeEntregar(Player p, Trabajo t) {
        Perfil f = pl.perfiles().de(p);
        if (f.activo != t) { Msg.error(p, "Tenés que tener el trabajo de " + t.nombre + " para entregar."); fallo(p); return false; }
        if (!enNpc(p, t)) { Msg.error(p, "Las entregas se hacen en el NPC de " + t.nombre + ". Acercate y hacele clic."); fallo(p); return false; }
        return true;
    }

    /** true si no hace falta NPC o si el jugador esta cerca del NPC de ese trabajo. */
    boolean enNpc(Player p, Trabajo t) { return !soloEnNpc() || pl.npcs().cercaDeNpc(p, t, ent.getDouble("distancia-npc", 8)); }

    /** Entrega a la global el item que pide (todo lo que tenga el jugador, o solo lo que tiene en la mano). Devuelve cuantos entrego. */
    int entregarGlobal(Player p, Trabajo t, boolean soloMano) {
        if (!puedeEntregar(p, t)) return 0;
        Global g = global(t);
        Material mat = itemGlobal(t);
        if (mat == null) { Msg.error(p, "Esta misión global todavía no tiene un item."); fallo(p); return 0; }
        if (g.completada) { Msg.error(p, "La misión global ya se completó. ¡Cobrá tu parte!"); fallo(p); return 0; }
        long faltan = (long) Math.ceil(g.objetivo - g.total);
        ItemStack molde = new ItemStack(mat);
        ItemStack[] cont = p.getInventory().getStorageContents();
        long tomados = 0;
        for (int i = 0; i < cont.length && tomados < faltan; i++) {
            ItemStack it = cont[i];
            if (it == null || !it.isSimilar(molde)) continue;
            if (soloMano && i != p.getInventory().getHeldItemSlot()) continue;
            int n = (int) Math.min(it.getAmount(), faltan - tomados);
            tomados += n;
            if (n == it.getAmount()) cont[i] = null; else it.setAmount(it.getAmount() - n);
        }
        if (tomados == 0) {
            Msg.aviso(p, Component.text("No tenés ", NamedTextColor.RED).append(itemGlobalNombre(t))
                    .append(Component.text(soloMano ? " en la mano (sin nombre ni encantamientos)." : " (tiene que ser sin nombre ni encantamientos).", NamedTextColor.RED)));
            fallo(p);
            return 0;
        }
        p.getInventory().setStorageContents(cont);
        g.total += tomados;
        g.aportes.merge(p.getUniqueId(), (int) tomados, Integer::sum);
        sucio = true;
        Msg.aviso(p, Component.text("Entregaste " + Msg.num(tomados, 0) + " × ", NamedTextColor.GRAY).append(itemGlobalNombre(t))
                .append(Component.text("  (" + Msg.num(Math.min(g.total, g.objetivo), 0) + "/" + Msg.num(g.objetivo, 0) + ")", NamedTextColor.YELLOW)));
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.1f);
        if (!g.completada && g.total >= g.objetivo) completarGlobal(t, g);
        return (int) tomados;
    }

    private void completarGlobal(Trabajo t, Global g) {
        Rar r = rarezaDe(t);
        g.completada = true;
        g.completadaEn = System.currentTimeMillis();
        g.bonoPct = r.bono;
        g.bonoHasta = g.completadaEn + bonoHoras() * 3_600_000L;
        guardar();
        Bukkit.broadcast(Component.text("★ ", NamedTextColor.GOLD)
                .append(Component.text("¡Se completó la misión global de ", NamedTextColor.GRAY))
                .append(Component.text(t.nombre, t.color))
                .append(Component.text(" (" + r.nombre + ")! Todos los " + t.nombre + " tienen +" + Math.round(r.bono * 100) + "% de pago y XP por " + bonoHoras() + " horas. Cobrá tu parte en /trabajos.", NamedTextColor.GRAY)));
        for (Player o : Bukkit.getOnlinePlayers()) o.playSound(o.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
    }

    /** Suma sobre el pago y el XP (0,20 = +20%) para los de este trabajo mientras dure el bono global. */
    double bonoGlobal(Trabajo t) {
        Global g = globales.get(t);
        return g != null && System.currentTimeMillis() < g.bonoHasta ? g.bonoPct : 0;
    }

    /** Fraccion maxima del fondo que puede cobrar una sola persona. */
    double topeParte() { return ent.getDouble("tope-parte", 0.40); }

    /** Horas que dura el bono despues de completarse. */
    long bonoHoras() { return ent.getLong("bono-horas", 48); }

    // ---------- reparto ----------

    int aporteDe(Trabajo t, Player p) { return global(t).aportes.getOrDefault(p.getUniqueId(), 0); }

    /** Lo minimo que hay que entregar para cobrar: el 1% de lo que pide la mision (al menos 1). */
    int aporteMinimo(Trabajo t) { return (int) Math.max(1, Math.ceil(global(t).objetivo * ent.getDouble("participacion-minima", 0.01))); }

    /** Plata total de la mision actual (el "fondo colaborador"): depende de la rareza. */
    double pozo(Trabajo t) { return rarezaDe(t).pozo; }

    /** XP total de la mision actual (el "fondo XP"): un % de la plata. */
    double pozoXp(Trabajo t) { return Math.floor(pozo(t) * ent.getDouble("xp-ratio", 0.25)); }

    /** Parte (0 a 1) de este jugador en el reparto: lo que entrego sobre lo que entregaron todos los que llegaron al minimo. */
    double parte(Trabajo t, UUID u) {
        Global g = global(t);
        int min = aporteMinimo(t);
        long suma = 0;
        for (int a : g.aportes.values()) if (a >= min) suma += a;
        int mio = g.aportes.getOrDefault(u, 0);
        double cruda = suma <= 0 || mio < min ? 0 : (double) mio / suma;
        return Math.min(cruda, ent.getDouble("tope-parte", 0.40));   // nadie cobra mas que este % del fondo
    }

    double pagoGlobal(Trabajo t, UUID u) { return Math.floor(pozo(t) * parte(t, u)); }

    double xpGlobal(Trabajo t, UUID u) { return Math.floor(pozoXp(t) * parte(t, u)); }

    boolean puedeCobrarGlobal(Trabajo t, Player p) {
        Global g = global(t);
        return g.completada && aporteDe(t, p) >= aporteMinimo(t) && !g.cobraron.contains(p.getUniqueId());
    }

    boolean cobrarGlobal(Player p, Trabajo t) {
        Perfil f = pl.perfiles().de(p);
        if (f.activo != t) { Msg.error(p, "Tenés que tener el trabajo de " + t.nombre + " para cobrar."); return false; }
        if (!puedeCobrarGlobal(t, p)) { Msg.error(p, "Todavía no podés cobrar la misión global."); return false; }
        global(t).cobraron.add(p.getUniqueId());
        sucio = true;
        double pesos = pagoGlobal(t, p.getUniqueId());
        double xp = xpGlobal(t, p.getUniqueId());
        pl.pago().depositar(p, f, pesos);
        pl.balance().plata(t, Balance.GLOBAL, pesos);
        if (xp > 0) pl.pago().subirXp(p, f, t, xp);
        pl.perfiles().guardar(f);
        Msg.aviso(p, Component.text("Cobraste tu parte de la misión global: ", NamedTextColor.GRAY)
                .append(Component.text("+$" + Msg.num(pesos, 0), NamedTextColor.GREEN))
                .append(Component.text("  +" + Msg.num(xp, 0) + " XP", NamedTextColor.AQUA)));
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
