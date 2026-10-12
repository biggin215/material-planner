package com.materialplanner.items;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Last-seen contents of every tracked location. Storage snapshots are saved per account;
 * inventory, equipment and rune pouch are live only.
 */
@Slf4j
public class ItemStore
{
	private static final String GROUP = "materialplanner";
	private static final Type LIST_TYPE = new TypeToken<List<StoredItem>>()
	{
	}.getType();

	/**
	 * One stack summed across locations, for the "All storage" view.
	 */
	public static final class Aggregate
	{
		public final int id;
		public final String name;
		public final Counts counts = new Counts();

		Aggregate(int id, String name)
		{
			this.id = id;
			this.name = name;
		}
	}

	private final ConfigManager configManager;
	private final Gson gson;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

	private static final String KEY_CARGO_NAMES = "cargoBoatNames";
	private static final Type NAMES_TYPE = new TypeToken<Map<String, String>>()
	{
	}.getType();
	private static final Location[] CARGO_SLOTS = {
		Location.CARGO_HOLD_1, Location.CARGO_HOLD_2, Location.CARGO_HOLD_3, Location.CARGO_HOLD_4, Location.CARGO_HOLD_5
	};

	private final EnumMap<Location, Map<Integer, StoredItem>> items = new EnumMap<>(Location.class);
	/** Cargo hold slot → boat name read from the cargo hold window title. */
	private final EnumMap<Location, String> cargoNames = new EnumMap<>(Location.class);
	private final EnumMap<Location, Long> times = new EnumMap<>(Location.class);

	public ItemStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	public void addListener(Runnable r)
	{
		listeners.add(r);
	}

	private void fire()
	{
		listeners.forEach(Runnable::run);
	}

	// The bank keeps its original keys so snapshots from earlier versions still load
	private static String itemsKey(Location loc)
	{
		return loc == Location.BANK ? "bankSnapshot" : "snapshot." + loc.name();
	}

	private static String timeKey(Location loc)
	{
		return loc == Location.BANK ? "bankSnapshotTime" : "snapshotTime." + loc.name();
	}

	/**
	 * Load saved snapshots for the current RuneScape profile and forget live containers.
	 */
	public void loadProfile()
	{
		EnumMap<Location, Map<Integer, StoredItem>> loaded = new EnumMap<>(Location.class);
		EnumMap<Location, Long> loadedTimes = new EnumMap<>(Location.class);
		for (Location loc : Location.values())
		{
			if (!loc.isPersistent())
			{
				continue;
			}
			String json = configManager.getRSProfileConfiguration(GROUP, itemsKey(loc));
			if (json == null || json.isEmpty())
			{
				continue;
			}
			try
			{
				List<StoredItem> list = gson.fromJson(json, LIST_TYPE);
				Map<Integer, StoredItem> m = new LinkedHashMap<>();
				if (list != null)
				{
					for (StoredItem it : list)
					{
						m.put(it.getId(), it);
					}
				}
				loaded.put(loc, m);
				String t = configManager.getRSProfileConfiguration(GROUP, timeKey(loc));
				loadedTimes.put(loc, t == null ? 0L : Long.parseLong(t));
			}
			catch (JsonSyntaxException | NumberFormatException e)
			{
				log.warn("Could not read saved snapshot for {}", loc, e);
			}
		}
		EnumMap<Location, String> names = new EnumMap<>(Location.class);
		String namesJson = configManager.getRSProfileConfiguration(GROUP, KEY_CARGO_NAMES);
		if (namesJson != null && !namesJson.isEmpty())
		{
			try
			{
				Map<String, String> m = gson.fromJson(namesJson, NAMES_TYPE);
				if (m != null)
				{
					for (Map.Entry<String, String> e : m.entrySet())
					{
						names.put(Location.valueOf(e.getKey()), e.getValue());
					}
				}
			}
			catch (JsonSyntaxException | IllegalArgumentException e)
			{
				log.warn("Could not read cargo hold names", e);
			}
		}

		synchronized (this)
		{
			items.clear();
			times.clear();
			cargoNames.clear();
			items.putAll(loaded);
			times.putAll(loadedTimes);
			cargoNames.putAll(names);
		}
		fire();
	}

	/**
	 * The storage slot for a boat's cargo hold, assigned the first time that boat's hold is seen.
	 */
	public Location cargoSlotFor(String boatName)
	{
		Location slot = null;
		synchronized (this)
		{
			for (Location loc : CARGO_SLOTS)
			{
				if (boatName.equalsIgnoreCase(cargoNames.get(loc)))
				{
					return loc;
				}
			}
			for (Location loc : CARGO_SLOTS)
			{
				if (!cargoNames.containsKey(loc))
				{
					slot = loc;
					break;
				}
			}
			if (slot == null)
			{
				// More than five boats seen; reuse the last slot
				slot = CARGO_SLOTS[CARGO_SLOTS.length - 1];
			}
			cargoNames.put(slot, boatName);
		}
		Map<String, String> save = new HashMap<>();
		synchronized (this)
		{
			for (Map.Entry<Location, String> e : cargoNames.entrySet())
			{
				save.put(e.getKey().name(), e.getValue());
			}
		}
		configManager.setRSProfileConfiguration(GROUP, KEY_CARGO_NAMES, gson.toJson(save, NAMES_TYPE));
		return slot;
	}

	/**
	 * Display name, using the boat's name for cargo holds.
	 */
	public synchronized String label(Location loc)
	{
		String boat = cargoNames.get(loc);
		return boat == null ? loc.getLabel() : "Cargo hold: " + boat;
	}

	public synchronized String shortLabel(Location loc)
	{
		String boat = cargoNames.get(loc);
		return boat == null ? loc.getShortLabel() : boat;
	}

	public void update(Location location, List<StoredItem> list)
	{
		Map<Integer, StoredItem> map = new LinkedHashMap<>();
		for (StoredItem it : list)
		{
			StoredItem prev = map.get(it.getId());
			map.put(it.getId(), prev == null ? it
				: new StoredItem(it.getId(), it.getName(), prev.getQuantity() + it.getQuantity()));
		}

		long now = System.currentTimeMillis();
		synchronized (this)
		{
			items.put(location, map);
			times.put(location, now);
		}

		if (location.isPersistent())
		{
			configManager.setRSProfileConfiguration(GROUP, itemsKey(location), gson.toJson(new ArrayList<>(map.values())));
			configManager.setRSProfileConfiguration(GROUP, timeKey(location), String.valueOf(now));
		}
		fire();
	}

	/**
	 * Forget inventory, equipment and rune pouch (logout / account switch). Saved storage is kept.
	 */
	public void clearLive()
	{
		synchronized (this)
		{
			for (Location loc : Location.values())
			{
				if (!loc.isPersistent())
				{
					items.remove(loc);
					times.remove(loc);
				}
			}
		}
		fire();
	}

	public synchronized boolean hasSnapshot(Location loc)
	{
		return times.getOrDefault(loc, 0L) > 0;
	}

	public synchronized long getTime(Location loc)
	{
		return times.getOrDefault(loc, 0L);
	}

	public boolean hasBankSnapshot()
	{
		return hasSnapshot(Location.BANK);
	}

	public long getBankTime()
	{
		return getTime(Location.BANK);
	}

	public synchronized List<StoredItem> getItems(Location loc)
	{
		Map<Integer, StoredItem> m = items.get(loc);
		return m == null ? new ArrayList<>() : new ArrayList<>(m.values());
	}

	/**
	 * Persistent locations that have been recorded at least once.
	 */
	public synchronized List<Location> recordedStorage()
	{
		List<Location> out = new ArrayList<>();
		for (Location loc : Location.values())
		{
			if (loc.isPersistent() && times.getOrDefault(loc, 0L) > 0)
			{
				out.add(loc);
			}
		}
		return out;
	}

	/**
	 * Everything in saved storage (not on you), summed by item.
	 */
	public synchronized List<Aggregate> allStorage()
	{
		Map<Integer, Aggregate> out = new LinkedHashMap<>();
		for (Map.Entry<Location, Map<Integer, StoredItem>> e : items.entrySet())
		{
			if (!e.getKey().isPersistent())
			{
				continue;
			}
			for (StoredItem it : e.getValue().values())
			{
				out.computeIfAbsent(it.getId(), id -> new Aggregate(id, it.getName())).counts.add(e.getKey(), it.getQuantity());
			}
		}
		return new ArrayList<>(out.values());
	}

	public synchronized Counts countById(int canonicalId)
	{
		Counts c = new Counts();
		for (Map.Entry<Location, Map<Integer, StoredItem>> e : items.entrySet())
		{
			StoredItem it = e.getValue().get(canonicalId);
			if (it != null)
			{
				c.add(e.getKey(), it.getQuantity());
			}
		}
		return c;
	}

	/**
	 * Recipes reference materials by name, so count every stack whose name matches (case-insensitive).
	 */
	public synchronized Map<String, Counts> countsByName()
	{
		Map<String, Counts> out = new HashMap<>();
		for (Map.Entry<Location, Map<Integer, StoredItem>> e : items.entrySet())
		{
			for (StoredItem it : e.getValue().values())
			{
				out.computeIfAbsent(normalize(it.getName()), k -> new Counts()).add(e.getKey(), it.getQuantity());
			}
		}
		return out;
	}

	/**
	 * Find an item id for a name from anything we have seen. Used for icons of untradeable items.
	 */
	public synchronized int idForName(String name)
	{
		String n = normalize(name);
		for (Map<Integer, StoredItem> m : items.values())
		{
			for (StoredItem it : m.values())
			{
				if (normalize(it.getName()).equals(n))
				{
					return it.getId();
				}
			}
		}
		return -1;
	}

	public static String normalize(String name)
	{
		return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
	}
}
