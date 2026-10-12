package com.materialplanner.ui;

import com.materialplanner.CostMode;
import java.util.Set;

/**
 * What the side panel needs from the plugin: game state it can't read itself, and a way to report the plan.
 */
public interface PanelHost
{
	/**
	 * @return the player's real (unboosted) level, or -1 when not logged in or the skill is unknown
	 */
	int realLevel(String skill);

	/**
	 * Cost mode with AUTO already resolved for the current account. Never returns AUTO.
	 */
	CostMode effectiveCostMode();

	/**
	 * Called whenever the plan is recalculated.
	 *
	 * @param bankNeeded normalized names of items the plan takes from the bank
	 */
	void onPlanChanged(Set<String> bankNeeded);
}
