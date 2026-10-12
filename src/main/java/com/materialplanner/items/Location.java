package com.materialplanner.items;

/**
 * Every place the plugin tracks items. "Carried" locations count as already on you;
 * "persistent" ones are saved per account and survive restarts.
 */
public enum Location
{
	INVENTORY("Inventory", "Inv", true, false),
	WORN("Worn", "Worn", true, false),
	RUNE_POUCH("Rune pouch", "Pouch", true, false),
	BANK("Bank", "Bank", false, true),
	SEED_VAULT("Seed vault", "Vault", false, true),
	SEED_BOX("Seed box", "Seed box", false, true),
	LOOTING_BAG("Looting bag", "Loot bag", false, true),
	GROUP_STORAGE("Group storage", "Group", false, true),
	CARGO_HOLD_1("Cargo hold (boat 1)", "Hold 1", false, true),
	CARGO_HOLD_2("Cargo hold (boat 2)", "Hold 2", false, true),
	CARGO_HOLD_3("Cargo hold (boat 3)", "Hold 3", false, true),
	CARGO_HOLD_4("Cargo hold (boat 4)", "Hold 4", false, true),
	CARGO_HOLD_5("Cargo hold (boat 5)", "Hold 5", false, true);

	private final String label;
	private final String shortLabel;
	private final boolean carried;
	private final boolean persistent;

	Location(String label, String shortLabel, boolean carried, boolean persistent)
	{
		this.label = label;
		this.shortLabel = shortLabel;
		this.carried = carried;
		this.persistent = persistent;
	}

	public String getLabel()
	{
		return label;
	}

	public String getShortLabel()
	{
		return shortLabel;
	}

	public boolean isCarried()
	{
		return carried;
	}

	public boolean isPersistent()
	{
		return persistent;
	}

	/**
	 * Storage that isn't the bank and isn't on you.
	 */
	public boolean isOtherStorage()
	{
		return !carried && this != BANK;
	}
}
