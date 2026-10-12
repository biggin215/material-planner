package com.materialplanner;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Launches a RuneLite dev client with this plugin loaded. Run with: ./gradlew run
 */
public class MaterialPlannerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(MaterialPlannerPlugin.class);
		RuneLite.main(args);
	}
}
