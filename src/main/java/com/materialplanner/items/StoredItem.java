package com.materialplanner.items;

/**
 * One stack of an item: canonical (un-noted) item id, its name at capture time, and quantity.
 */
public final class StoredItem
{
	private final int id;
	private final String name;
	private final long quantity;

	public StoredItem(int id, String name, long quantity)
	{
		this.id = id;
		this.name = name;
		this.quantity = quantity;
	}

	public int getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	public long getQuantity()
	{
		return quantity;
	}
}
