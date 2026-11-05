package ar.com.tresyetres.shops;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Plata por Vault. Todos los montos entran en centavos. */
final class Economia {
    private Economy eco;

    boolean iniciar() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        eco = rsp.getProvider();
        return eco != null;
    }

    private static double aMonto(long centavos) { return centavos / 100.0; }

    boolean tiene(OfflinePlayer p, long centavos) { return eco.has(p, aMonto(centavos)); }

    /** Saldo en centavos (redondeado hacia abajo). */
    long saldo(OfflinePlayer p) { return (long) Math.floor(eco.getBalance(p) * 100.0 + 1e-6); }

    boolean retirar(OfflinePlayer p, long centavos) {
        if (centavos <= 0) return true;
        EconomyResponse r = eco.withdrawPlayer(p, aMonto(centavos));
        return r.transactionSuccess();
    }

    boolean depositar(OfflinePlayer p, long centavos) {
        if (centavos <= 0) return true;
        EconomyResponse r = eco.depositPlayer(p, aMonto(centavos));
        return r.transactionSuccess();
    }
}
