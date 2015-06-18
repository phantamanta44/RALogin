package io.github.phantamanta44.ralogin;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

public class TradeHandler {
	
	public final Map<String, TradePackage> trades;
	
	@SuppressWarnings("deprecation")
	public TradeHandler(ConfigurationSection sec) {
		trades = new HashMap<>();
		for (String k : sec.getKeys(false)) {
			ConfigurationSection subSec = sec.getConfigurationSection(k);
			String name = k;
			int price = subSec.getInt("price");
			int money = subSec.getInt("money");
			Map<ItemStack, String> items = new HashMap<>();
			for (String serItem : subSec.getStringList("items")) {
				String[] itemVals = serItem.split(";");
				ItemStack is = new ItemStack(Integer.parseInt(itemVals[0]), Integer.parseInt(itemVals[1]), Short.parseShort(itemVals[2]));
				items.put(is, itemVals[3]);
			}
			Map<String, String> cmds = new HashMap<>();
			for (String com : subSec.getStringList("commands")) {
				String[] comVals = com.split(";");
				cmds.put(comVals[0], comVals[1]);
			}
			trades.put(name, new TradePackage(name, price, money, items, cmds));
		}
	}
	
	public static class TradePackage {
		
		public final String name;
		public final int price;
		public final Map<ItemStack, String> items;
		public final Map<String, String> cmds;
		public final int money;
		
		public TradePackage(String packageName, int tokenPrice, int monetaryReward, Map<ItemStack, String> itemReward, Map<String, String> commands) {
			name = packageName;
			price = tokenPrice;
			money = monetaryReward;
			items = itemReward;
			cmds = commands;
		}
		
	}
	
}
