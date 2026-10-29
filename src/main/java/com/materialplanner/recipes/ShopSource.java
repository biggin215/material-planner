package com.materialplanner.recipes;

/**
 * One shop that sells an item, from the wiki's storeline data.
 */
public class ShopSource
{
	public String item;
	public String store;
	/** The shop's wiki page when it differs from the shown name; may be null. */
	public String page;
	/** Price the shop charges, or -1 when unknown. */
	public int price = -1;
	/** "Coins" unless the shop uses another currency (tokkul, marks of grace...). */
	public String currency;
	/** Base stock as listed on the wiki, e.g. "10" or "∞"; may be null. */
	public String stock;
	/** Wiki location text, e.g. "Prifddinas, Tirannwn"; may be null. */
	public String location;
	public boolean members;
	/** Known spots for the shop or its owner as {x, y, plane}; may be null or empty. */
	public java.util.List<int[]> coords;

	public String pageName()
	{
		return page == null || page.isEmpty() ? store : page;
	}

	public boolean isCoins()
	{
		return currency == null || currency.isEmpty() || currency.equalsIgnoreCase("coins");
	}
}
