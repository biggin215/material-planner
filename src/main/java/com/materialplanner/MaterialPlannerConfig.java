package com.materialplanner;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(MaterialPlannerConfig.GROUP)
public interface MaterialPlannerConfig extends Config
{
	String GROUP = "materialplanner";

	@ConfigSection(name = "Planner", description = "How material lists are worked out", position = 0)
	String plannerSection = "planner";

	@ConfigSection(name = "Storage", description = "Which items count as available", position = 1)
	String storageSection = "storage";

	@ConfigSection(name = "Recipe data", description = "Where recipes come from", position = 2)
	String dataSection = "data";

	// --- Planner -------------------------------------------------------------------------------------

	@ConfigItem(
		keyName = "expandRecipes",
		name = "Expand sub-recipes",
		description = "When you're missing an ingredient that can be crafted, plan its ingredients too",
		position = 1,
		section = plannerSection
	)
	default boolean expandRecipes()
	{
		return true;
	}

	@ConfigItem(
		keyName = "costMode",
		name = "Show costs",
		description = "GE prices of what you're short, plus coins the plan needs (sawmill fees and similar). "
			+ "Auto shows coins only on ironman accounts.",
		position = 2,
		section = plannerSection
	)
	default CostMode costMode()
	{
		return CostMode.AUTO;
	}

	@ConfigItem(
		keyName = "levelCheck",
		name = "Check levels",
		description = "Flag recipes your skill levels are too low for",
		position = 3,
		section = plannerSection
	)
	default boolean levelCheck()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideUnmet",
		name = "Hide recipes above my level",
		description = "Leave recipes you can't make yet out of the search dropdown",
		position = 4,
		section = plannerSection
	)
	default boolean hideUnmet()
	{
		return false;
	}

	@ConfigItem(
		keyName = "highlightBank",
		name = "Highlight in bank",
		description = "Outline items in the bank that your plan needs you to withdraw",
		position = 5,
		section = plannerSection
	)
	default boolean highlightBank()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "highlightColor",
		name = "Highlight color",
		description = "Outline color for bank highlighting",
		position = 6,
		section = plannerSection
	)
	default Color highlightColor()
	{
		return new Color(255, 170, 0, 230);
	}

	// --- Storage -------------------------------------------------------------------------------------

	@ConfigItem(
		keyName = "countWorn",
		name = "Count equipped items",
		description = "Count items you are wearing as available materials",
		position = 1,
		section = storageSection
	)
	default boolean countWorn()
	{
		return true;
	}

	@ConfigItem(
		keyName = "countOtherStorage",
		name = "Count other storage",
		description = "Count the seed vault, cargo holds, looting bag, seed box and group storage as available",
		position = 2,
		section = storageSection
	)
	default boolean countOtherStorage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "checkBankMenu",
		name = "'Check bank' menu option",
		description = "Adds a 'Check bank' option when right-clicking items in your inventory or on the ground",
		position = 3,
		section = storageSection
	)
	default boolean checkBankMenu()
	{
		return true;
	}

	// --- Recipe data ---------------------------------------------------------------------------------

	@ConfigItem(
		keyName = "wikiRecipes",
		name = "Download wiki recipes",
		description = "Download the full recipe list from the OSRS Wiki. When off, only the built-in recipes are available.",
		position = 1,
		section = dataSection
	)
	default boolean wikiRecipes()
	{
		return true;
	}

	@Range(min = 1, max = 60)
	@ConfigItem(
		keyName = "refreshDays",
		name = "Recipe refresh (days)",
		description = "How often to re-download recipes from the wiki",
		position = 2,
		section = dataSection
	)
	default int refreshDays()
	{
		return 7;
	}
}
