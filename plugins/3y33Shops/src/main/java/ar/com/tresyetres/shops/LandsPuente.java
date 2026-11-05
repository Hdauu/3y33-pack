package ar.com.tresyetres.shops;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

/** Habla con la API de Lands por reflexion, asi no hace falta tener el jar de Lands para compilar. */
final class LandsPuente {
    /** Terreno de Lands en un punto: nombre, dueño y si un jugador es miembro. */
    record Terreno(String nombre, UUID dueno, boolean miembro) { }

    private Object api;
    private Method getArea, getLand, getName, getOwner, isTrusted, getSpawn;

    LandsPuente(Plugin plugin) {
        try {
            Class<?> integ = Class.forName("me.angeschossen.lands.api.LandsIntegration");
            Class<?> area = Class.forName("me.angeschossen.lands.api.land.Area");
            Class<?> land = Class.forName("me.angeschossen.lands.api.land.Land");
            api = integ.getMethod("of", Plugin.class).invoke(null, plugin);
            getArea = integ.getMethod("getArea", Location.class);
            getLand = area.getMethod("getLand");
            isTrusted = area.getMethod("isTrusted", UUID.class);
            getName = land.getMethod("getName");
            getOwner = land.getMethod("getOwnerUID");
            try { getSpawn = land.getMethod("getSpawn"); } catch (Throwable ignored) { getSpawn = null; }
        } catch (Throwable t) {
            plugin.getLogger().warning("Lands API: " + t);
            api = null;
        }
    }

    boolean disponible() { return api != null; }

    /** El terreno de Lands que hay en ese punto (null si es zona libre). {@code jugador} sirve para saber si es miembro. */
    Terreno en(Location loc, UUID jugador) {
        if (api == null || loc == null || loc.getWorld() == null) return null;
        try {
            Object area = getArea.invoke(api, loc);
            if (area == null) return null;
            Object land = getLand.invoke(area);
            if (land == null) return null;
            String nombre = (String) getName.invoke(land);
            UUID dueno = (UUID) getOwner.invoke(land);
            boolean miembro = jugador != null && (jugador.equals(dueno) || (boolean) isTrusted.invoke(area, jugador));
            return new Terreno(nombre, dueno, miembro);
        } catch (Throwable t) {
            return null;
        }
    }

    /** El spawn de la tierra que contiene ese punto, o null si no tiene. */
    Location spawn(Location loc) {
        if (api == null || getSpawn == null || loc == null || loc.getWorld() == null) return null;
        try {
            Object area = getArea.invoke(api, loc);
            if (area == null) return null;
            Object land = getLand.invoke(area);
            if (land == null) return null;
            Object sp = getSpawn.invoke(land);
            return sp instanceof Location l && l.getWorld() != null ? l : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
