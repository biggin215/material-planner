package com.materialplanner.recipes;

/**
 * OSRS experience table math.
 */
public final class Xp
{
	public static final int MAX_LEVEL = 99;
	private static final int[] TABLE = new int[MAX_LEVEL + 1];

	static
	{
		double points = 0;
		for (int lvl = 1; lvl <= MAX_LEVEL; lvl++)
		{
			TABLE[lvl] = (int) Math.floor(points / 4);
			points += Math.floor(lvl + 300 * Math.pow(2, lvl / 7.0));
		}
	}

	private Xp()
	{
	}

	/**
	 * XP needed to reach a level, e.g. 99 → 13,034,431.
	 */
	public static int forLevel(int level)
	{
		return TABLE[Math.max(1, Math.min(MAX_LEVEL, level))];
	}

	public static int levelFor(long xp)
	{
		for (int lvl = MAX_LEVEL; lvl > 1; lvl--)
		{
			if (xp >= TABLE[lvl])
			{
				return lvl;
			}
		}
		return 1;
	}

	/**
	 * Actions needed to go from {@code currentXp} to {@code targetLevel}, or 0 if already there.
	 */
	public static int actionsToLevel(long currentXp, int targetLevel, double xpPerAction)
	{
		if (xpPerAction <= 0)
		{
			return 0;
		}
		long needed = forLevel(targetLevel) - currentXp;
		if (needed <= 0)
		{
			return 0;
		}
		return (int) Math.ceil(needed / xpPerAction);
	}
}
