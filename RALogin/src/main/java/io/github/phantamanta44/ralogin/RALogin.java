package io.github.phantamanta44.ralogin;

import io.github.phantamanta44.ralogin.DatabaseHandler.DBEntry;
import io.github.phantamanta44.ralogin.TradeHandler.TradePackage;

import java.io.File;
import java.util.Date;
import java.util.Map.Entry;
import java.util.UUID;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class RALogin extends JavaPlugin {
	
	public static final String prefix = ChatColor.AQUA + "[RisingAllianceLogin] " + ChatColor.RESET;
	public static final String microfix = ChatColor.AQUA + "» " + ChatColor.RESET;
	
	private DatabaseHandler db;
	private TradeHandler th;
	private Economy econ;
	
	@Override
	public void onEnable() {
		db = new DatabaseHandler(this);
		this.reloadConfig();
		if (!setupEconomy()) {
            Bukkit.getServer().getLogger().warning("Vault not found! Disabling RALogin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
		Bukkit.getServer().getPluginManager().registerEvents(new LoginListener(this), this);
	}
	
	@Override
	public void onDisable() {
		db.writeOut();
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (cmd.getName().equalsIgnoreCase("logintoken")) {
			DBEntry entry;
			boolean isPlayer = sender instanceof Player;
			if (isPlayer) {
				entry = db.getPlayer((Player)sender);
				if (args.length < 1) {
					sender.sendMessage(new String[] {		
							prefix + ChatColor.GRAY + "You have " + ChatColor.BLUE + entry.getTokenCount() + ChatColor.GRAY + " login tokens.",
							prefix + ChatColor.GRAY + "You have received " + ChatColor.BLUE + entry.getTotalTokenCount() + ChatColor.GRAY + " tokens in total."
					});
					if (sender.hasPermission("ralogin.receive")) {
						if (!entry.canReceiveTokens())
							sender.sendMessage(prefix + ChatColor.GRAY + "You can receive more tokens after " + ChatColor.BLUE + formatTime(entry.getLastTokenGain() + DatabaseHandler.ONE_DAY - new Date().getTime()) + ChatColor.GRAY + ".");
						else
							sender.sendMessage(prefix + ChatColor.GRAY + "You can receive more tokens " + ChatColor.BLUE + "now" + ChatColor.GRAY + ".");
					}
				}
				else {
					if (args[0].equalsIgnoreCase("help")) {
						sender.sendMessage(prefix + ChatColor.BLUE + "Valid actions:");
						sender.sendMessage(microfix + ChatColor.BLUE + "/logintoken" + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "View your token balance.");
						if (sender.hasPermission("ralogin.trade")) {
							sender.sendMessage(microfix + ChatColor.BLUE + "/logintoken trades" + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "View available trade packages.");
							sender.sendMessage(microfix + ChatColor.BLUE + "/logintoken trade <package>" + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "Trade tokens for a package.");
						}
						if (sender.hasPermission("ralogin.view") || sender.hasPermission("ralogin.modify") || sender.hasPermission("ralogin.admin"))
							sender.sendMessage(microfix + ChatColor.BLUE + "/logintoken check <name|uuid>" + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "View a player's token balance.");
						if (sender.hasPermission("ralogin.modify") || sender.hasPermission("ralogin.admin"))
							sender.sendMessage(microfix + ChatColor.BLUE + "/logintoken <give|take|set> <name|uuid> <amount>" + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "Modify a player's balance.");
						if (sender.hasPermission("ralogin.admin")) {
							sender.sendMessage(microfix + ChatColor.BLUE + "/logintoken reset <name|uuid>" + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "Clear a player's database entry.");
							sender.sendMessage(microfix + ChatColor.BLUE + "/logintoken reload" + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "Reload the plugin.");
						}
					}
					else if (args[0].equalsIgnoreCase("trades")) {
						if (sender.hasPermission("ralogin.trade")) {
							sender.sendMessage(prefix + ChatColor.BLUE + "Available packages:");
							for (TradePackage pkg : th.trades.values()) {
								sender.sendMessage(microfix + ChatColor.GRAY + pkg.name + (entry.getTokenCount() >= pkg.price ? ChatColor.GREEN : ChatColor.RED) + " (" + pkg.price + " tokens)");
								if (pkg.money > 0)
									sender.sendMessage(ChatColor.BLUE + "Money: " + ChatColor.GRAY + pkg.money + " " + this.getConfig().getString("config.currency"));
								if (pkg.items.size() > 0) {
									sender.sendMessage(ChatColor.BLUE + "Items:");
									for (Entry<ItemStack, String> is : pkg.items.entrySet())
										sender.sendMessage(ChatColor.BLUE + "- " + ChatColor.GRAY + is.getValue() + " x" + is.getKey().getAmount());
								}
								if (pkg.cmds.size() > 0) {
									sender.sendMessage(ChatColor.BLUE + "Other:");
									for (Entry<String, String> com : pkg.cmds.entrySet())
										sender.sendMessage(ChatColor.BLUE + "- " + ChatColor.GRAY + com.getValue());
								}
							}
						}
						else
							sender.sendMessage(prefix + ChatColor.GRAY + "No permission!");
					}
					else if (args[0].equalsIgnoreCase("trade")) {
						if (sender.hasPermission("ralogin.trade")) {
							if (args.length != 2)
								sender.sendMessage(prefix + ChatColor.GRAY + "Invalid syntax. Try " + ChatColor.BLUE + "/logintoken help" + ChatColor.GRAY + ".");
							else {
								TradePackage pkg = th.trades.get(args[1]);
								if (pkg != null) {
									if (entry.getTokenCount() >= pkg.price) {
										entry.decrementCount(pkg.price);
										sender.sendMessage(prefix + ChatColor.GRAY + "Received trade package " + ChatColor.BLUE + pkg.name + ChatColor.GRAY + ".");
										if (pkg.money > 0) {
											EconomyResponse res = econ.depositPlayer((Player)sender, pkg.money);
											if (!res.transactionSuccess())
												sender.sendMessage("Something went wrong while transferring money! " + res.errorMessage);
											else
												sender.sendMessage(ChatColor.GREEN + "Received " + (int)res.amount + " " + this.getConfig().getString("config.currency") + ". New account balance: " + (int)res.balance);
										}
										for (String com : pkg.cmds.keySet()) {
											String rwdCmd = com.replaceAll("%PLAYER%", ((Player)sender).getName());
											Bukkit.getServer().getLogger().info("[RALogin] Running command: /" + rwdCmd);
											Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), rwdCmd);
										}
										for (ItemStack is : pkg.items.keySet())
											((Player)sender).getInventory().addItem(is.clone());
										db.writeOut();
									}
									else
										sender.sendMessage(prefix + ChatColor.GRAY + "Insufficient tokens! Try another package.");
								}
								else
									sender.sendMessage(prefix + ChatColor.GRAY + "Trade package does not exist. Try " + ChatColor.BLUE + "/logintoken trades" + ChatColor.GRAY + ".");
							}
						}
						else
							sender.sendMessage(prefix + ChatColor.GRAY + "No permission!");
					}
					else if (args[0].equalsIgnoreCase("check")) {
						if (sender.hasPermission("ralogin.view") || sender.hasPermission("ralogin.modify") || sender.hasPermission("ralogin.admin")) {
							if (args.length != 2)
								sender.sendMessage(prefix + ChatColor.GRAY + "Invalid syntax. Try " + ChatColor.BLUE + "/logintoken help" + ChatColor.GRAY + ".");
							else {
								try {
									DBEntry target = db.getPlayer(args[1]);
									if (target == null)
										target = db.getPlayer(UUID.fromString(args[1]));
									sender.sendMessage(new String[] {		
											prefix + ChatColor.BLUE + target.getPlayerName() + ChatColor.GRAY + " has " + ChatColor.BLUE + target.getTokenCount() + ChatColor.GRAY + " login tokens.",
											prefix + ChatColor.BLUE + target.getPlayerName() + ChatColor.GRAY + " has received " + ChatColor.BLUE + target.getTotalTokenCount() + ChatColor.GRAY + " tokens in total."
									});
									if (!target.canReceiveTokens())
										sender.sendMessage(prefix + ChatColor.BLUE + target.getPlayerName() + ChatColor.GRAY + " can receive more tokens after " + ChatColor.BLUE + formatTime(target.getLastTokenGain() + DatabaseHandler.ONE_DAY - new Date().getTime()) + ChatColor.GRAY + ".");
									else
										sender.sendMessage(prefix + ChatColor.BLUE + target.getPlayerName() + ChatColor.GRAY + " can receive more tokens " + ChatColor.BLUE + "now" + ChatColor.GRAY + ".");
								}
								catch (Exception ex) {
									sender.sendMessage(prefix + ChatColor.GRAY + "Not a valid player or UUID!");
								}
							}
						}
						else
							sender.sendMessage(prefix + ChatColor.GRAY + "No permission!");
					}
					else if (args[0].equalsIgnoreCase("give")) {
						if (sender.hasPermission("ralogin.modify") || sender.hasPermission("ralogin.admin")) {
							if (args.length != 3)
								sender.sendMessage(prefix + ChatColor.GRAY + "Invalid syntax. Try " + ChatColor.BLUE + "/logintoken help" + ChatColor.GRAY + ".");
							else {
								try {
									DBEntry target = db.getPlayer(args[1]);
									if (target == null)
										target = db.getPlayer(UUID.fromString(args[1]));
									int amount = Integer.parseInt(args[2]);
									target.incrementCountDiscreetly(amount, false);
									sender.sendMessage(prefix + ChatColor.BLUE + target.getPlayerName() + ChatColor.GRAY + "'s account balance set to " + ChatColor.BLUE + target.getTokenCount() + ChatColor.GRAY + ".");
									db.writeOut();
								}
								catch (Exception ex) {
									if (ex instanceof NumberFormatException)
										sender.sendMessage(prefix + ChatColor.GRAY + "Invalid amount specified!");
									else
										sender.sendMessage(prefix + ChatColor.GRAY + "Not a valid player or UUID!");
								}
							}
						}
						else
							sender.sendMessage(prefix + ChatColor.GRAY + "No permission!");
					}
					else if (args[0].equalsIgnoreCase("take")) {
						if (sender.hasPermission("ralogin.modify") || sender.hasPermission("ralogin.admin")) {
							if (args.length != 3)
								sender.sendMessage(prefix + ChatColor.GRAY + "Invalid syntax. Try " + ChatColor.BLUE + "/logintoken help" + ChatColor.GRAY + ".");
							else {
								try {
									DBEntry target = db.getPlayer(args[1]);
									if (target == null)
										target = db.getPlayer(UUID.fromString(args[1]));
									int amount = Integer.parseInt(args[2]);
									target.decrementCount(amount);
									sender.sendMessage(prefix + ChatColor.BLUE + target.getPlayerName() + ChatColor.GRAY + "'s account balance set to " + ChatColor.BLUE + target.getTokenCount() + ChatColor.GRAY + ".");
									db.writeOut();
								}
								catch (Exception ex) {
									if (ex instanceof NumberFormatException)
										sender.sendMessage(prefix + ChatColor.GRAY + "Invalid amount specified!");
									else
										sender.sendMessage(prefix + ChatColor.GRAY + "Not a valid player or UUID!");
								}
							}
						}
						else
							sender.sendMessage(prefix + ChatColor.GRAY + "No permission!");
					}
					else if (args[0].equalsIgnoreCase("set")) {
						if (sender.hasPermission("ralogin.modify") || sender.hasPermission("ralogin.admin")) {
							if (args.length != 3)
								sender.sendMessage(prefix + ChatColor.GRAY + "Invalid syntax. Try " + ChatColor.BLUE + "/logintoken help" + ChatColor.GRAY + ".");
							else {
								try {
									DBEntry target = db.getPlayer(args[1]);
									if (target == null)
										target = db.getPlayer(UUID.fromString(args[1]));
									int amount = Integer.parseInt(args[2]);
									target.setCount(amount);
									sender.sendMessage(prefix + ChatColor.BLUE + target.getPlayerName() + ChatColor.GRAY + "'s account balance set to " + ChatColor.BLUE + target.getTokenCount() + ChatColor.GRAY + ".");
									db.writeOut();
								}
								catch (Exception ex) {
									if (ex instanceof NumberFormatException)
										sender.sendMessage(prefix + ChatColor.GRAY + "Invalid amount specified!");
									else
										sender.sendMessage(prefix + ChatColor.GRAY + "Not a valid player or UUID!");
								}
							}
						}
						else
							sender.sendMessage(prefix + ChatColor.GRAY + "No permission!");
					}
					else if (args[0].equalsIgnoreCase("reset")) {
						if (sender.hasPermission("ralogin.admin")) {
							if (args.length != 2)
								sender.sendMessage(prefix + ChatColor.GRAY + "Invalid syntax. Try " + ChatColor.BLUE + "/logintoken help" + ChatColor.GRAY + ".");
							else {
								try {
									DBEntry target = db.getPlayer(args[1]);
									if (target == null)
										target = db.getPlayer(UUID.fromString(args[1]));
									target.resetCount();
									sender.sendMessage(prefix + ChatColor.BLUE + target.getPlayerName() + ChatColor.GRAY + "'s account reset successfully.");
									db.writeOut();
								}
								catch (Exception ex) {
									sender.sendMessage(prefix + ChatColor.GRAY + "Not a valid player or UUID!");
								}
							}
						}
						else
							sender.sendMessage(prefix + ChatColor.GRAY + "No permission!");
					}
					else if (args[0].equalsIgnoreCase("reload")) {
						if (sender.hasPermission("ralogin.admin")) {
							sender.sendMessage(ChatColor.GRAY + "Reloading RALogin configs and database...");
							this.reloadConfig();
							this.db.readIn();
							sender.sendMessage(ChatColor.GRAY + "Reload complete.");
						}
						else
							sender.sendMessage(prefix + ChatColor.GRAY + "No permission!");
					}
					else
						sender.sendMessage(prefix + ChatColor.GRAY + "Invalid action. Try " + ChatColor.BLUE + "/logintoken help" + ChatColor.GRAY + ".");
				}
			}
			else {
				if (args.length < 1)
					sender.sendMessage(new String[] {
							"Console usage:",
							"/logintoken <give|take|set> <name|uuid> <value>",
							"/logintoken <check|reset> <name|uuid>",
							"/logintoken reload"
					});
				else {
					if (args[0].equalsIgnoreCase("give")) {
						if (args.length != 3)
							sender.sendMessage(prefix + ChatColor.GRAY + "Invalid syntax. Try " + ChatColor.BLUE + "/logintoken help" + ChatColor.GRAY + ".");
						else {
							try {
								DBEntry target = db.getPlayer(args[1]);
								if (target == null)
									target = db.getPlayer(UUID.fromString(args[1]));
								int amount = Integer.parseInt(args[2]);
								target.incrementCountDiscreetly(amount, false);
								sender.sendMessage(prefix + ChatColor.BLUE + target.getPlayerName() + ChatColor.GRAY + "'s account balance set to " + ChatColor.BLUE + target.getTokenCount() + ChatColor.GRAY + ".");
								db.writeOut();
							}
							catch (Exception ex) {
								if (ex instanceof NumberFormatException)
									sender.sendMessage(prefix + ChatColor.GRAY + "Invalid amount specified!");
								else
									sender.sendMessage(prefix + ChatColor.GRAY + "Not a valid player or UUID!");
							}
						}
					}
					else if (args[0].equalsIgnoreCase("take")) {
						if (args.length != 3)
							sender.sendMessage(prefix + ChatColor.GRAY + "Invalid syntax. Try " + ChatColor.BLUE + "/logintoken help" + ChatColor.GRAY + ".");
						else {
							try {
								DBEntry target = db.getPlayer(args[1]);
								if (target == null)
									target = db.getPlayer(UUID.fromString(args[1]));
								int amount = Integer.parseInt(args[2]);
								target.decrementCount(amount);
								sender.sendMessage(prefix + ChatColor.BLUE + target.getPlayerName() + ChatColor.GRAY + "'s account balance set to " + ChatColor.BLUE + target.getTokenCount() + ChatColor.GRAY + ".");
								db.writeOut();
							}
							catch (Exception ex) {
								if (ex instanceof NumberFormatException)
									sender.sendMessage(prefix + ChatColor.GRAY + "Invalid amount specified!");
								else
									sender.sendMessage(prefix + ChatColor.GRAY + "Not a valid player or UUID!");
							}
						}
					}
					else if (args[0].equalsIgnoreCase("set")) {
						if (args.length != 3)
							sender.sendMessage(prefix + ChatColor.GRAY + "Invalid syntax. Try " + ChatColor.BLUE + "/logintoken help" + ChatColor.GRAY + ".");
						else {
							try {
								DBEntry target = db.getPlayer(args[1]);
								if (target == null)
									target = db.getPlayer(UUID.fromString(args[1]));
								int amount = Integer.parseInt(args[2]);
								target.setCount(amount);
								sender.sendMessage(prefix + ChatColor.BLUE + target.getPlayerName() + ChatColor.GRAY + "'s account balance set to " + ChatColor.BLUE + target.getTokenCount() + ChatColor.GRAY + ".");
								db.writeOut();
							}
							catch (Exception ex) {
								if (ex instanceof NumberFormatException)
									sender.sendMessage(prefix + ChatColor.GRAY + "Invalid amount specified!");
								else
									sender.sendMessage(prefix + ChatColor.GRAY + "Not a valid player or UUID!");
							}
						}
					}
					else if (args[0].equalsIgnoreCase("check")) {
						if (args.length != 2)
							sender.sendMessage(prefix + ChatColor.GRAY + "Invalid syntax. Try " + ChatColor.BLUE + "/logintoken help" + ChatColor.GRAY + ".");
						else {
							try {
								DBEntry target = db.getPlayer(args[1]);
								if (target == null)
									target = db.getPlayer(UUID.fromString(args[1]));
								sender.sendMessage(new String[] {		
										prefix + ChatColor.BLUE + target.getPlayerName() + ChatColor.GRAY + " has " + ChatColor.BLUE + target.getTokenCount() + ChatColor.GRAY + " login tokens.",
										prefix + ChatColor.BLUE + target.getPlayerName() + ChatColor.GRAY + " has received " + ChatColor.BLUE + target.getTotalTokenCount() + ChatColor.GRAY + " tokens in total."
								});
								if (!target.canReceiveTokens())
									sender.sendMessage(prefix + ChatColor.BLUE + target.getPlayerName() + ChatColor.GRAY + " can receive more tokens after " + ChatColor.BLUE + formatTime(target.getLastTokenGain() + DatabaseHandler.ONE_DAY - new Date().getTime()) + ChatColor.GRAY + ".");
								else
									sender.sendMessage(prefix + ChatColor.BLUE + target.getPlayerName() + ChatColor.GRAY + " can receive more tokens " + ChatColor.BLUE + "now" + ChatColor.GRAY + ".");
							}
							catch (Exception ex) {
								sender.sendMessage(prefix + ChatColor.GRAY + "Not a valid player or UUID!");
							}
						}
					}
					else if (args[0].equalsIgnoreCase("reset")) {
						if (args.length != 2)
							sender.sendMessage(prefix + ChatColor.GRAY + "Invalid syntax. Try " + ChatColor.BLUE + "/logintoken help" + ChatColor.GRAY + ".");
						else {
							try {
								DBEntry target = db.getPlayer(args[1]);
								if (target == null)
									target = db.getPlayer(UUID.fromString(args[1]));
								target.resetCount();
								sender.sendMessage(prefix + ChatColor.BLUE + target.getPlayerName() + ChatColor.GRAY + "'s account reset successfully.");
								db.writeOut();
							}
							catch (Exception ex) {
								sender.sendMessage(prefix + ChatColor.GRAY + "Not a valid player or UUID!");
							}
						}
					}
					else if (args[0].equalsIgnoreCase("reload")) {
						sender.sendMessage(ChatColor.GRAY + "Reloading RALogin configs and database...");
						this.reloadConfig();
						this.db.readIn();
						sender.sendMessage(ChatColor.GRAY + "Reload complete.");
					}
					else {
						sender.sendMessage(new String[] {
								"Console usage:",
								"/logintoken <give|take|set> <name|uuid> <value>",
								"/logintoken <check|reset> <name|uuid>",
								"/logintoken reload"
						});
					}
				}
			}
		}
		return true;
	}
	
	public DatabaseHandler getDb() {
		return db;
	}
	
	public static String formatTime(long totalMillis) {
		int millis = (int)(totalMillis % 1000);
		int totalSeconds = (int)(totalMillis - millis) / 1000;
		int seconds = totalSeconds % 60;
		int totalMinutes = (totalSeconds - seconds) / 60;
		int minutes = totalMinutes % 60;
		int hours = (totalMinutes - minutes) / 60;
		return hours + " hour" + (hours != 1 ? "s" : "") + ", " + minutes + " minute" + (minutes != 1 ? "s" : "") + ", and " + seconds + " second" + (seconds != 1 ? "s" : "");
	}
	
	private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null)
            return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null)
            return false;
        econ = rsp.getProvider();
        return econ != null;
    }
	
	@Override
	public void reloadConfig() {
		if (!new File(this.getDataFolder(), "config.yml").exists())
			this.saveDefaultConfig();
		super.reloadConfig();
		th = new TradeHandler(this.getConfig().getConfigurationSection("rewards"));
	}
	
}
