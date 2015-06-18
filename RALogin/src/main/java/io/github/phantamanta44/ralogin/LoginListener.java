package io.github.phantamanta44.ralogin;

import io.github.phantamanta44.ralogin.DatabaseHandler.DBEntry;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class LoginListener implements Listener {
	
	private RALogin plg;
	
	public LoginListener(RALogin plugin) {
		plg = plugin;
	}
	
	@EventHandler
	public void onLogin(PlayerLoginEvent event) {
		boolean dirty = false;
		DBEntry entry;
		final Player player = event.getPlayer();
		if ((entry = plg.getDb().getPlayer(player)) == null) {
			entry = plg.getDb().registerPlayer(player);
			dirty = true;
		}
		if (entry.updatePlayerName())
			dirty = true;
		if (player.hasPermission("ralogin.receive") && entry.canReceiveTokens()) {
			final int tokenInc = plg.getConfig().getInt("config.tokensperday");
			entry.incrementCount(tokenInc);
			new BukkitRunnable() {
				public void run() {
					player.sendMessage(RALogin.prefix + ChatColor.GRAY + "You received " + ChatColor.BLUE + tokenInc + ChatColor.GRAY + " login token" + (tokenInc != 1 ? "s" : "") + " for joining today!");
				}
			}.runTaskLater(plg, 20);
			dirty = true;
		}
		if (dirty)
			plg.getDb().writeOut();
	}
	
}
