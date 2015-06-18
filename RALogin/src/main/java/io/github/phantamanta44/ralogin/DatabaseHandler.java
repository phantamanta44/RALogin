package io.github.phantamanta44.ralogin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class DatabaseHandler {
	
	public static final long ONE_DAY = 24 * 60 * 60 * 1000; // 24 hours represented in milliseconds

	private File dbFile;
	private Map<UUID, DBEntry> dbData;
	
	public DatabaseHandler(Plugin pl) {
		dbFile = new File(pl.getDataFolder(), "db.txt");
		dbData = new HashMap<>();
		if (!dbFile.exists())
			writeOut();
		readIn();
	}
	
	public DBEntry registerPlayer(Player pl) {
		DBEntry entry = new DBEntry(pl);
		dbData.put(pl.getUniqueId(), entry);
		return entry;
	}
	
	public DBEntry getPlayer(Player pl) {
		return dbData.get(pl.getUniqueId());
	}
	
	public DBEntry getPlayer(UUID id) {
		return dbData.get(id);
	}
	
	@SuppressWarnings("deprecation")
	public DBEntry getPlayer(String name) {
		try {
			OfflinePlayer pl = Bukkit.getServer().getOfflinePlayer(name);
			return dbData.get(pl.getUniqueId());
		}
		catch (NullPointerException ex) {
			return null;
		}
	}
	
	public void readIn() {
		try {
			dbData.clear();
			BufferedReader streamIn = new BufferedReader(new InputStreamReader(new FileInputStream(dbFile)));
			String ser;
			while ((ser = streamIn.readLine()) != null) {
				String[] parse = ser.split("%");
				UUID x = UUID.fromString(parse[0]);
				String y = parse[1];
				int z = Integer.parseInt(parse[2]);
				int j = Integer.parseInt(parse[3]);
				long k = Long.parseLong(parse[4]);
				dbData.put(x, new DBEntry(x, y, z, j, k));
			}
			streamIn.close();
		}
		catch (IOException ex) {
			Bukkit.getServer().getLogger().warning("RALogin database failed to load!");
			ex.printStackTrace();
		}
	}
	
	public void writeOut() {
		try {
			if (!dbFile.exists()) {
				dbFile.getParentFile().mkdirs();
				dbFile.createNewFile();
			}
			PrintStream streamOut = new PrintStream(new FileOutputStream(dbFile));
			for (DBEntry entry : dbData.values()) {
				StringBuilder ser = new StringBuilder(entry.playerId.toString()).append("%");
				ser.append(entry.playerName).append("%");
				ser.append(entry.tokenCount).append("%");
				ser.append(entry.totalTokenCount).append("%");
				ser.append(entry.lastTokenGain);
				streamOut.println(ser.toString());
			}
			streamOut.close();
		}
		catch (IOException ex) {
			Bukkit.getServer().getLogger().warning("RALogin database failed to save!");
			ex.printStackTrace();
		}
	}
	
	public static class DBEntry {
		
		private final UUID playerId;
		private String playerName;
		private int tokenCount;
		private int totalTokenCount;
		private long lastTokenGain;
		
		public DBEntry(Player pl) {
			this(pl.getUniqueId(), pl.getName());
		}
		
		public DBEntry(UUID id, String name) {
			this(id, name, 0, 0, 0);
		}
		
		public DBEntry(UUID id, String name, int tokens, int totalTokens, long lastGain) {
			playerId = id;
			playerName = name;
			tokenCount = tokens;
			totalTokenCount = totalTokens;
			lastTokenGain = lastGain;
		}
		
		public int getTokenCount() {
			return tokenCount;
		}
		
		public int getTotalTokenCount() {
			return totalTokenCount;
		}
		
		public long getLastTokenGain() {
			return lastTokenGain;
		}
		
		public boolean canReceiveTokens() {
			return Math.abs(new Date().getTime() - lastTokenGain) > ONE_DAY;
		}
		
		public void incrementCount(int amount) {
			lastTokenGain = new Date().getTime();
			if (amount <= 0)
				return;
			tokenCount += amount;
			totalTokenCount += amount;
		}
		
		public void incrementCountDiscreetly(int amount, boolean increaseTotal) {
			if (amount <= 0)
				return;
			tokenCount += amount;
			totalTokenCount += increaseTotal ? amount : 0;
		}
		
		public void decrementCount(int amount) {
			if (amount <=0 )
				return;
			tokenCount = Math.max(0, tokenCount - amount);
		}
		
		public void setCount(int amount) {
			tokenCount = Math.max(0, amount);
		}
		
		public void resetCount() {
			tokenCount = 0;
			totalTokenCount = 0;
		}
		
		public String getPlayerName() {
			return playerName;
		}
		
		public boolean updatePlayerName() {
			try {
				Player pl = Bukkit.getPlayer(playerId);
				if (playerName != pl.getName()) {
					playerName = pl.getName();
					return true;
				}
				return false;
			}
			catch (NullPointerException ex) {
				return false;
			}
		}
		
		public UUID getPlayerId() {
			return playerId;
		}
		
	}
	
}
