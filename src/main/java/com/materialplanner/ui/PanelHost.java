package com.materialplanner.ui;

import com.materialplanner.CostMode;
import java.util.List;
import java.util.Set;

/**
 * What the side panel needs from the plugin: game state it can't read itself, and a way to report the plan.
 * All methods are safe to call from the Swing thread.
 */
public interface PanelHost
{
	/**
	 * @return the player's real (unboosted) level, or -1 when not logged in or the skill is unknown
	 */
	int realLevel(String skill);

	/**
	 * @return the player's XP in a skill, or -1 when unknown
	 */
	long skillXp(String skill);

	/**
	 * @return true on a members world, false on free-to-play, null when not logged in
	 */
	Boolean membersWorld();

	/**
	 * Quests named in a recipe's requirement text that the player hasn't finished. Empty when met or unknown.
	 */
	List<String> unmetQuests(String requirementText);

	/**
	 * Whether an item stacks, by name. Null when not known yet; the panel is refreshed once it is.
	 */
	Boolean stackable(String itemName);

	/**
	 * Cost mode with AUTO already resolved for the current account. Never returns AUTO.
	 */
	/**
	 * Where the player is as {x, y, plane}, or null when not logged in.
	 */
	int[] playerLocation();

	CostMode effectiveCostMode();

	/**
	 * @return true when the account is an ironman of any kind
	 */
	boolean ironman();

	/**
	 * Called whenever the plan is recalculated.
	 *
	 * @param bankNeeded normalized names of items the plan takes from the bank
	 */
	void onPlanChanged(Set<String> bankNeeded);
}
