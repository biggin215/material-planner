package com.materialplanner.recipes;

/**
 * One shop that sells an item, from the wiki's storeline data.
 */
public class ShopSource
{
	public String item;
	public String store;
	/** Price the shop charges, or -1 when unknown. */
	public int price = -1;
	/** "Coins" unless the shop uses another currency (tokkul, marks of grace...). */
	public String currency;
	/** Base stock as listed on the wiki, e.g. "10" or "∞"; may be null. */
	public String stock;

	public boolean isCoins()
	{
		return currency == null || currency.isEmpty() || currency.equalsIgnoreCase("coins");
	}
}
