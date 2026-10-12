package com.materialplanner.ui;

import com.google.gson.Gson;
import com.materialplanner.MaterialPlannerConfig;
import com.materialplanner.items.ItemStore;
import com.materialplanner.recipes.RecipeRepository;
import java.awt.BorderLayout;
import java.util.concurrent.TimeUnit;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

public class MaterialPlannerPanel extends PluginPanel
{
	private final RecipeTab recipeTab;
	private final StorageTab storageTab;

	public MaterialPlannerPanel(ItemManager itemManager, ItemStore store, RecipeRepository recipes,
		ConfigManager configManager, Gson gson, MaterialPlannerConfig config, PanelHost host, Runnable refreshRecipes)
	{
		super(false);
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(8, 8, 8, 8));

		IconCache icons = new IconCache(itemManager);
		recipeTab = new RecipeTab(store, recipes, configManager, gson, config, icons, host, refreshRecipes);
		storageTab = new StorageTab(store, icons);

		JPanel display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);

		MaterialTabGroup tabs = new MaterialTabGroup(display);
		tabs.setBorder(new EmptyBorder(0, 0, 8, 0));
		MaterialTab recipesTabButton = new MaterialTab("Recipes", tabs, recipeTab);
		MaterialTab storageTabButton = new MaterialTab("Storage", tabs, storageTab);
		tabs.addTab(recipesTabButton);
		tabs.addTab(storageTabButton);
		tabs.select(recipesTabButton);

		add(tabs, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);

		store.addListener(() -> SwingUtilities.invokeLater(this::onItemsChanged));
	}

	public void onRecipesLoaded()
	{
		recipeTab.onRecipesLoaded();
	}

	public void onItemsChanged()
	{
		recipeTab.refreshMaterials();
		storageTab.refresh();
	}

	/**
	 * Levels, account type or settings changed.
	 */
	public void onSettingsChanged()
	{
		recipeTab.refreshAll();
		storageTab.refresh();
	}

	public static String ago(long time)
	{
		if (time <= 0)
		{
			return "never";
		}
		long diff = Math.max(0, System.currentTimeMillis() - time);
		if (diff < TimeUnit.MINUTES.toMillis(1))
		{
			return "just now";
		}
		if (diff < TimeUnit.HOURS.toMillis(1))
		{
			return TimeUnit.MILLISECONDS.toMinutes(diff) + "m ago";
		}
		if (diff < TimeUnit.DAYS.toMillis(1))
		{
			return TimeUnit.MILLISECONDS.toHours(diff) + "h ago";
		}
		return TimeUnit.MILLISECONDS.toDays(diff) + "d ago";
	}
}
