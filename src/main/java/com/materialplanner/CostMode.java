package com.materialplanner;

public enum CostMode
{
	AUTO("Auto (ironman: coins only)"),
	GE_PRICES("GE prices + coins"),
	COINS_ONLY("Coins only"),
	OFF("Off");

	private final String label;

	CostMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
