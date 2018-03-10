package de.paul1365972.slimefunfix;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.SlimefunItem;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.handlers.BlockPlaceHandler;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.handlers.ItemHandler;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.machines.CargoInputNode;
import me.mrCookieSlime.Slimefun.api.BlockStorage;

public class InputNodeFix extends JavaPlugin {

	private CargoInputNode cargoInputNode;

	private String version;
	private Class<?> craftWorldClass, tileEntityClass, nbtTagCompoundClass, nbtTagListClass;

	private boolean fullyEnabled = false;
	
	@Override
	public void onEnable() {
		_init(this);
		getCommand("slimefunfix").setExecutor(this);
	}
	
	private void _init(JavaPlugin plugin) {
		version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3] + ".";
		craftWorldClass = getOCBClass("CraftWorld");
		tileEntityClass = getNMSClass("TileEntity");
		nbtTagCompoundClass = getNMSClass("NBTTagCompound");
		nbtTagListClass = getNMSClass("NBTTagList");
		Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
			@Override
			public void run() {
				for (SlimefunItem sfItem : SlimefunItem.all) {
					if (sfItem instanceof CargoInputNode) {
						cargoInputNode = (CargoInputNode) sfItem;
					}
				}
				fullyEnabled = true;
			}
		}, 1);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!fullyEnabled)
			return sendMessage(sender, "Plugin not fully enabled yet!");
		
		if (!(sender instanceof Player))
			return sendMessage(sender, "You need to be a player");
		Player p = (Player) sender;
		
		if (!p.hasPermission("slimefunfix.nodes"))
			return sendMessage(sender, "You got no permission");
		
		boolean force = false;
		boolean blacklist = false;
		for (int i = 0; i < args.length; i++) {
			if (args[i].equalsIgnoreCase("force"))
				force = true;
			else if (args[i].equalsIgnoreCase("blacklist"))
				blacklist = true;
		}
		
		Location pLoc = p.getLocation();
		int y = pLoc.getBlockY();
		sendMessage(p, "Fixing Input Nodes in this chunk from Y " + (y - 5) + "-" + (y + 5) + " with arguments: " + (force ? "force" : "keep") + ", " + (blacklist ? "blacklist" : "whitelist"));
		int edited = 0, found = 0;
		exit:
		for (int i = 0; i < 16; i++) {
			for (int j = 0; j < 16; j++) {
				for (int k = -5; k < 6; k++) {
					Block block = pLoc.getChunk().getBlock(i, y + k, j);
					try {
						if (processBlock(block)) {
							found++;
							if (fix(block, p, force, blacklist))
								edited++;
						}
					} catch (Exception e) {
						e.printStackTrace();
						sendMessage(p, "Version: " + version);
						sendMessage(p, "Exception: " + e);
						for (StackTraceElement traceElement : e.getStackTrace())
							sendMessage(p, traceElement.toString());
						if (e.getCause() != null) {
							for (StackTraceElement traceElement : e.getCause().getStackTrace())
								sendMessage(p, traceElement.toString());
						}
						break exit;
					}
				}
			}
		}
		sendMessage(p, "Fixed " + edited + " input nodes (" + (found - edited) + " were already fixed)");
		return true;
	}
	
	private boolean fix(Block b, Player p, boolean force, boolean blacklist) {
		if ((!force) && BlockStorage.hasBlockInfo(b))
			return false;
		BlockStorage.addBlockInfo(b, "id", cargoInputNode.getID(), true);
		if (SlimefunItem.blockhandler.containsKey(cargoInputNode.getID())) {
			SlimefunItem.blockhandler.get(cargoInputNode.getID()).onPlace(p, b, cargoInputNode);
		} else {
			for (ItemHandler handler : SlimefunItem.getHandlers("BlockPlaceHandler")) {
				if (((BlockPlaceHandler) handler).onBlockPlace(new BlockPlaceEvent(b, null, null, cargoInputNode.getItem(), p, true, EquipmentSlot.HAND), cargoInputNode.getItem()))
					break;
			}
		}
		if (blacklist)
			BlockStorage.addBlockInfo(b, "filter-type", "blacklist", true);
		return true;
	}

	private boolean processBlock(Block block) throws Exception {
		if (block == null || block.getType() != Material.SKULL)
			return false;
		Location loc = block.getLocation();
		Object tileEntity = craftWorldClass.getMethod("getTileEntityAt", int.class, int.class, int.class).invoke(block.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
		Object ntc = nbtTagCompoundClass.getConstructor().newInstance();
		tileEntityClass.getMethod("save", nbtTagCompoundClass).invoke(tileEntity, ntc);

		ntc = nbtTagCompoundClass.getMethod("getCompound", String.class).invoke(ntc, "Owner");
		if (ntc == null)
			return false;
		ntc = nbtTagCompoundClass.getMethod("getCompound", String.class).invoke(ntc, "Properties");
		if (ntc == null)
			return false;
		ntc = nbtTagCompoundClass.getMethod("get", String.class).invoke(ntc, "textures");
		if (ntc == null || (Boolean) nbtTagListClass.getMethod("isEmpty").invoke(ntc))
			return false;
		ntc = nbtTagListClass.getMethod("get", int.class).invoke(ntc, 0);
		if (ntc == null)
			return false;
		String texture = (String) nbtTagCompoundClass.getMethod("getString", String.class).invoke(ntc, "Value");
		if (texture == null)
			return false;
		return texture.equals("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTZkMWMxYTY5YTNkZTlmZWM5NjJhNzdiZjNiMmUzNzZkZDI1Yzg3M2EzZDhmMTRmMWRkMzQ1ZGFlNGM0In19fQ==");
	}
	
	private boolean sendMessage(CommandSender cs, String message) {
		cs.sendMessage(message);
		return true;
	}

	private Class<?> getOCBClass(String ocbClassString) {
		String name = "org.bukkit.craftbukkit." + version + ocbClassString;
		Class<?> ocbClass = null;
		try {
			ocbClass = Class.forName(name);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return ocbClass;
	}

	private Class<?> getNMSClass(String nmsClassString) {
		String name = "net.minecraft.server." + version + nmsClassString;
		Class<?> nmsClass = null;
		try {
			nmsClass = Class.forName(name);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return nmsClass;
	}
}
