package com.materialplanner.items;

import java.util.EnumMap;
import java.util.Map;

/**
 * How many of one item are in each tracked location.
 */
public final class Counts
{
	private final EnumMap<Location, Long> byLocation = new EnumMap<>(Location.class);

	public void add(Location loc, long qty)
	{
		byLocation.merge(loc, qty, Long::sum);
	}

	public long get(Location loc)
	{
		return byLocation.getOrDefault(loc, 0L);
	}

	public Map<Location, Long> asMap()
	{
		return byLocation;
	}

	/**
	 * On you right now. Worn items only count when the user allows it.
	 */
	public long carried(boolean includeWorn)
	{
		long sum = 0;
		for (Map.Entry<Location, Long> e : byLocation.entrySet())
		{
			if (e.getKey().isCarried() && (includeWorn || e.getKey() != Location.WORN))
			{
				sum += e.getValue();
			}
		}
		return sum;
	}

	public long bank()
	{
		return get(Location.BANK);
	}

	public long other()
	{
		long sum = 0;
		for (Map.Entry<Location, Long> e : byLocation.entrySet())
		{
			if (e.getKey().isOtherStorage())
			{
				sum += e.getValue();
			}
		}
		return sum;
	}

	public long total(boolean includeWorn, boolean includeOther)
	{
		return carried(includeWorn) + bank() + (includeOther ? other() : 0);
	}
}
