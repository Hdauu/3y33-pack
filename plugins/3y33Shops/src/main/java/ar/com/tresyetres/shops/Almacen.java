package ar.com.tresyetres.shops;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Guarda las tiendas en SQLite y las mantiene en memoria para consultarlas rapido. Todo el acceso a la base va por un solo hilo. */
final class Almacen {
    private final ShopsPlugin pl;
    private Connection con;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "3y33Shops-db");
        t.setDaemon(true);
        return t;
    });
    private final Map<Long, Tienda> porId = new HashMap<>();
    private final Map<String, Tienda> porBloque = new HashMap<>();
    private long ultimoId = 0;
    private final Map<String, Perfil> perfiles = new HashMap<>();
    private final Map<String, Punto> puntos = new java.util.concurrent.ConcurrentHashMap<>();

    /** Punto de viaje guardado (de una tienda: clave S:id; de una tierra: T:nombre). */
    record Punto(String mundo, double x, double y, double z, float yaw, float pitch) { }
    private final Map<UUID, String[]> skins = new java.util.concurrent.ConcurrentHashMap<>();

    /** Titulo y descripcion de la tierra que ven los jugadores en el buscador. */
    record Perfil(String titulo, String descripcion) { }

    Almacen(ShopsPlugin pl) { this.pl = pl; }

    static String clave(String mundo, int x, int y, int z) { return mundo + ";" + x + ";" + y + ";" + z; }
    static String clave(Block b) { return clave(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()); }
    static String clave(Location l) { return clave(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ()); }

    void abrir() throws SQLException {
        pl.getDataFolder().mkdirs();
        File f = new File(pl.getDataFolder(), "tiendas.db");
        try { Class.forName("org.sqlite.JDBC"); } catch (ClassNotFoundException ignored) { }
        con = DriverManager.getConnection("jdbc:sqlite:" + f.getAbsolutePath());
        try (Statement s = con.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=NORMAL");
            s.execute("CREATE TABLE IF NOT EXISTS tiendas(id INTEGER PRIMARY KEY, mundo TEXT NOT NULL, x INT, y INT, z INT,"
                    + " doble INT DEFAULT 0, x2 INT, y2 INT, z2 INT, tierra TEXT, dueno TEXT NOT NULL, item BLOB NOT NULL,"
                    + " compra INT NOT NULL, venta INT NOT NULL, bloq_compra INT DEFAULT 0, bloq_venta INT DEFAULT 0,"
                    + " modo_ingreso TEXT DEFAULT 'DUENO', bajo_stock INT DEFAULT 10, mensaje TEXT DEFAULT '',"
                    + " rotacion TEXT DEFAULT 'ARRIBA', creada INT, avisos INT DEFAULT 1)");
            try { s.execute("ALTER TABLE tiendas ADD COLUMN avisos INT DEFAULT 1"); } catch (SQLException ignored) { }
            s.execute("CREATE TABLE IF NOT EXISTS admins(tienda INT, uuid TEXT, PRIMARY KEY(tienda, uuid))");
            s.execute("CREATE TABLE IF NOT EXISTS historial(id INTEGER PRIMARY KEY AUTOINCREMENT, tienda INT, cuando INT,"
                    + " jugador TEXT, tipo TEXT, cantidad INT, total INT)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_hist ON historial(tienda, cuando)");
            s.execute("CREATE TABLE IF NOT EXISTS puntos(clave TEXT PRIMARY KEY, mundo TEXT, x REAL, y REAL, z REAL, yaw REAL, pitch REAL)");
            s.execute("CREATE TABLE IF NOT EXISTS skins(uuid TEXT PRIMARY KEY, valor TEXT, firma TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS perfiles(tierra TEXT PRIMARY KEY, titulo TEXT, descripcion TEXT)");
        }
        // una sola vez: antes "SUR" era el valor por defecto sin efecto; ahora la posicion por defecto es ARRIBA
        try (Statement s = con.createStatement(); ResultSet rs = s.executeQuery("PRAGMA user_version")) {
            if (rs.next() && rs.getInt(1) < 1) {
                try (Statement u = con.createStatement()) {
                    u.executeUpdate("UPDATE tiendas SET rotacion='ARRIBA'");
                    u.execute("PRAGMA user_version=1");
                }
            }
        }
        cargar();
    }

    private void cargar() throws SQLException {
        porId.clear();
        porBloque.clear();
        try (Statement s = con.createStatement(); ResultSet rs = s.executeQuery("SELECT * FROM tiendas")) {
            while (rs.next()) {
                Tienda t = new Tienda();
                t.id = rs.getLong("id");
                t.mundo = rs.getString("mundo");
                t.x = rs.getInt("x"); t.y = rs.getInt("y"); t.z = rs.getInt("z");
                t.doble = rs.getInt("doble") != 0;
                t.x2 = rs.getInt("x2"); t.y2 = rs.getInt("y2"); t.z2 = rs.getInt("z2");
                t.tierra = rs.getString("tierra") == null ? "" : rs.getString("tierra");
                t.dueno = UUID.fromString(rs.getString("dueno"));
                t.item = ItemStack.deserializeBytes(rs.getBytes("item"));
                t.compra = rs.getLong("compra");
                t.venta = rs.getLong("venta");
                t.bloqCompra = rs.getInt("bloq_compra") != 0;
                t.bloqVenta = rs.getInt("bloq_venta") != 0;
                t.modoIngreso = rs.getString("modo_ingreso");
                t.bajoStock = rs.getInt("bajo_stock");
                t.mensaje = rs.getString("mensaje") == null ? "" : rs.getString("mensaje");
                t.rotacion = rs.getString("rotacion") == null ? "ARRIBA" : rs.getString("rotacion");
                t.creada = rs.getLong("creada");
                t.avisos = rs.getInt("avisos") != 0;
                indexar(t);
                ultimoId = Math.max(ultimoId, t.id);
            }
        }
        puntos.clear();
        try (Statement s = con.createStatement(); ResultSet rs = s.executeQuery("SELECT clave, mundo, x, y, z, yaw, pitch FROM puntos")) {
            while (rs.next()) puntos.put(rs.getString(1), new Punto(rs.getString(2), rs.getDouble(3), rs.getDouble(4), rs.getDouble(5), (float) rs.getDouble(6), (float) rs.getDouble(7)));
        }
        skins.clear();
        try (Statement s = con.createStatement(); ResultSet rs = s.executeQuery("SELECT uuid, valor, firma FROM skins")) {
            while (rs.next()) skins.put(UUID.fromString(rs.getString(1)), new String[]{rs.getString(2), rs.getString(3) == null ? "" : rs.getString(3)});
        }
        perfiles.clear();
        try (Statement s = con.createStatement(); ResultSet rs = s.executeQuery("SELECT tierra, titulo, descripcion FROM perfiles")) {
            while (rs.next()) perfiles.put(rs.getString(1), new Perfil(rs.getString(2) == null ? "" : rs.getString(2), rs.getString(3) == null ? "" : rs.getString(3)));
        }
        try (Statement s = con.createStatement(); ResultSet rs = s.executeQuery("SELECT tienda, uuid FROM admins")) {
            while (rs.next()) {
                Tienda t = porId.get(rs.getLong("tienda"));
                if (t != null) t.admins.add(UUID.fromString(rs.getString("uuid")));
            }
        }
    }

    private void indexar(Tienda t) {
        porId.put(t.id, t);
        porBloque.put(clave(t.mundo, t.x, t.y, t.z), t);
        if (t.doble) porBloque.put(clave(t.mundo, t.x2, t.y2, t.z2), t);
    }

    private void desindexar(Tienda t) {
        porId.remove(t.id);
        porBloque.remove(clave(t.mundo, t.x, t.y, t.z));
        if (t.doble) porBloque.remove(clave(t.mundo, t.x2, t.y2, t.z2));
    }

    // ---------- consultas (hilo principal) ----------

    Tienda en(Block b) { return b == null ? null : porBloque.get(clave(b)); }
    Tienda en(Location l) { return l == null || l.getWorld() == null ? null : porBloque.get(clave(l)); }
    Tienda porId(long id) { return porId.get(id); }
    Collection<Tienda> todas() { return new ArrayList<>(porId.values()); }

    int cantidadEnTierra(String nombre) {
        int n = 0;
        for (Tienda t : porId.values()) if (t.tierra.equalsIgnoreCase(nombre)) n++;
        return n;
    }

    // ---------- cambios ----------

    long nuevoId() { return ++ultimoId; }

    void agregar(Tienda t) {
        indexar(t);
        guardar(t);
    }

    void borrar(Tienda t) {
        desindexar(t);
        if (pl.holograma() != null) pl.holograma().quitar(t.id);
        borrarPunto(Puntos.claveTienda(t.id));
        final long id = t.id;
        io.execute(() -> {
            try (PreparedStatement p = con.prepareStatement("DELETE FROM tiendas WHERE id=?")) { p.setLong(1, id); p.executeUpdate(); }
            catch (SQLException e) { pl.getLogger().severe("No pude borrar la tienda " + id + ": " + e); }
            try (PreparedStatement p = con.prepareStatement("DELETE FROM admins WHERE tienda=?")) { p.setLong(1, id); p.executeUpdate(); }
            catch (SQLException e) { pl.getLogger().severe("No pude borrar los admins de la tienda " + id + ": " + e); }
        });
    }

    /** Guarda (o actualiza) una tienda. Copia los datos en el hilo principal y escribe en el hilo de la base. */
    void guardar(Tienda t) {
        if (pl.holograma() != null) pl.holograma().refrescar(t);
        final Tienda c = t;
        final byte[] item = t.item.serializeAsBytes();
        final long id = c.id; final String mundo = c.mundo; final int x = c.x, y = c.y, z = c.z; final boolean doble = c.doble;
        final int x2 = c.x2, y2 = c.y2, z2 = c.z2; final String tierra = c.tierra; final String dueno = c.dueno.toString();
        final long compra = c.compra, venta = c.venta; final boolean bc = c.bloqCompra, bv = c.bloqVenta;
        final String modo = c.modoIngreso; final int bajo = c.bajoStock; final String msj = c.mensaje; final String rot = c.rotacion;
        final long creada = c.creada; final boolean avisos = c.avisos;
        final List<String> admins = new ArrayList<>();
        for (UUID u : c.admins) admins.add(u.toString());
        io.execute(() -> {
            try (PreparedStatement p = con.prepareStatement("INSERT OR REPLACE INTO tiendas(id,mundo,x,y,z,doble,x2,y2,z2,tierra,dueno,item,"
                    + "compra,venta,bloq_compra,bloq_venta,modo_ingreso,bajo_stock,mensaje,rotacion,creada,avisos) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                int i = 1;
                p.setLong(i++, id); p.setString(i++, mundo); p.setInt(i++, x); p.setInt(i++, y); p.setInt(i++, z);
                p.setInt(i++, doble ? 1 : 0); p.setInt(i++, x2); p.setInt(i++, y2); p.setInt(i++, z2);
                p.setString(i++, tierra); p.setString(i++, dueno); p.setBytes(i++, item);
                p.setLong(i++, compra); p.setLong(i++, venta); p.setInt(i++, bc ? 1 : 0); p.setInt(i++, bv ? 1 : 0);
                p.setString(i++, modo); p.setInt(i++, bajo); p.setString(i++, msj); p.setString(i++, rot); p.setLong(i++, creada); p.setInt(i, avisos ? 1 : 0);
                p.executeUpdate();
            } catch (SQLException e) { pl.getLogger().severe("No pude guardar la tienda " + id + ": " + e); }
            try (PreparedStatement p = con.prepareStatement("DELETE FROM admins WHERE tienda=?")) { p.setLong(1, id); p.executeUpdate(); }
            catch (SQLException e) { pl.getLogger().severe("No pude actualizar los admins de la tienda " + id + ": " + e); }
            for (String u : admins) {
                try (PreparedStatement p = con.prepareStatement("INSERT OR IGNORE INTO admins(tienda,uuid) VALUES(?,?)")) { p.setLong(1, id); p.setString(2, u); p.executeUpdate(); }
                catch (SQLException e) { pl.getLogger().severe("No pude guardar un admin de la tienda " + id + ": " + e); }
            }
        });
    }

    void registrar(Tienda t, UUID jugador, String tipo, int cantidad, long totalCentavos) {
        final long id = t.id; final long ahora = System.currentTimeMillis(); final String j = jugador.toString();
        io.execute(() -> {
            try (PreparedStatement p = con.prepareStatement("INSERT INTO historial(tienda,cuando,jugador,tipo,cantidad,total) VALUES(?,?,?,?,?,?)")) {
                p.setLong(1, id); p.setLong(2, ahora); p.setString(3, j); p.setString(4, tipo); p.setInt(5, cantidad); p.setLong(6, totalCentavos);
                p.executeUpdate();
            } catch (SQLException e) { pl.getLogger().severe("No pude registrar la operacion de la tienda " + id + ": " + e); }
        });
    }

    record Registro(long cuando, UUID jugador, String tipo, int cantidad, long total) { }

    /** Ultimas operaciones de una tienda. Lee en el hilo de la base y devuelve el resultado en el hilo principal. */
    void ultimas(long id, int max, java.util.function.Consumer<List<Registro>> alTerminar) {
        io.execute(() -> {
            List<Registro> lista = new ArrayList<>();
            try (PreparedStatement p = con.prepareStatement("SELECT cuando,jugador,tipo,cantidad,total FROM historial WHERE tienda=? ORDER BY cuando DESC LIMIT ?")) {
                p.setLong(1, id); p.setInt(2, max);
                try (ResultSet rs = p.executeQuery()) {
                    while (rs.next()) lista.add(new Registro(rs.getLong(1), UUID.fromString(rs.getString(2)), rs.getString(3), rs.getInt(4), rs.getLong(5)));
                }
            } catch (SQLException e) { pl.getLogger().severe("No pude leer el historial de la tienda " + id + ": " + e); }
            org.bukkit.Bukkit.getScheduler().runTask(pl, () -> alTerminar.accept(lista));
        });
    }

    Punto punto(String clave) { return puntos.get(clave); }

    void guardarPunto(String clave, org.bukkit.Location l) {
        final Punto pt = new Punto(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
        puntos.put(clave, pt);
        io.execute(() -> {
            try (PreparedStatement p = con.prepareStatement("INSERT OR REPLACE INTO puntos(clave,mundo,x,y,z,yaw,pitch) VALUES(?,?,?,?,?,?,?)")) {
                p.setString(1, clave); p.setString(2, pt.mundo()); p.setDouble(3, pt.x()); p.setDouble(4, pt.y()); p.setDouble(5, pt.z());
                p.setDouble(6, pt.yaw()); p.setDouble(7, pt.pitch());
                p.executeUpdate();
            } catch (SQLException e) { pl.getLogger().severe("No pude guardar el punto " + clave + ": " + e); }
        });
    }

    void borrarPunto(String clave) {
        if (puntos.remove(clave) == null) return;
        io.execute(() -> {
            try (PreparedStatement p = con.prepareStatement("DELETE FROM puntos WHERE clave=?")) { p.setString(1, clave); p.executeUpdate(); }
            catch (SQLException e) { pl.getLogger().severe("No pude borrar el punto " + clave + ": " + e); }
        });
    }

    String[] skin(UUID u) { return skins.get(u); }

    void guardarSkin(UUID u, String valor, String firma) {
        String[] ant = skins.get(u);
        if (ant != null && ant[0].equals(valor)) return;
        final String f = firma == null ? "" : firma;
        skins.put(u, new String[]{valor, f});
        io.execute(() -> {
            try (PreparedStatement p = con.prepareStatement("INSERT OR REPLACE INTO skins(uuid,valor,firma) VALUES(?,?,?)")) {
                p.setString(1, u.toString()); p.setString(2, valor); p.setString(3, f);
                p.executeUpdate();
            } catch (SQLException e) { pl.getLogger().severe("No pude guardar la skin de " + u + ": " + e); }
        });
    }

    Perfil perfil(String tierra) {
        Perfil p = perfiles.get(tierra.toLowerCase(java.util.Locale.ROOT));
        return p == null ? new Perfil("", "") : p;
    }

    void guardarPerfil(String tierra, String titulo, String descripcion) {
        final String k = tierra.toLowerCase(java.util.Locale.ROOT);
        perfiles.put(k, new Perfil(titulo, descripcion));
        io.execute(() -> {
            try (PreparedStatement p = con.prepareStatement("INSERT OR REPLACE INTO perfiles(tierra,titulo,descripcion) VALUES(?,?,?)")) {
                p.setString(1, k); p.setString(2, titulo); p.setString(3, descripcion);
                p.executeUpdate();
            } catch (SQLException e) { pl.getLogger().severe("No pude guardar el perfil de la tierra " + k + ": " + e); }
        });
    }

    void cerrar() {
        io.shutdown();
        try { io.awaitTermination(10, TimeUnit.SECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        try { if (con != null) con.close(); } catch (SQLException ignored) { }
    }
}
