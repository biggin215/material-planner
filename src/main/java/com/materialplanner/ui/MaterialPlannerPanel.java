package com.materialplanner.ui;

import com.google.gson.Gson;
import com.materialplanner.MaterialPlannerConfig;
import com.materialplanner.items.ItemStore;
import com.materialplanner.recipes.RecipeRepository;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.concurrent.TimeUnit;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.PluginPanel;

public class MaterialPlannerPanel extends PluginPanel
{
	private final RecipeTab recipeTab;
	private final StorageTab storageTab;

	public MaterialPlannerPanel(ItemManager itemManager, ItemStore store, RecipeRepository recipes,
		ConfigManager configManager, Gson gson, MaterialPlannerConfig config, PanelHost host, Runnable refreshRecipes)
	{
		super(false);
		setLayout(new BorderLayout(0, Theme.GAP));
		setBackground(Theme.BG);
		setBorder(new EmptyBorder(Theme.PANEL_PAD + 2, Theme.PANEL_PAD, Theme.PANEL_PAD, Theme.PANEL_PAD - 2));

		IconCache icons = new IconCache(itemManager);
		recipeTab = new RecipeTab(store, recipes, configManager, gson, config, icons, host, refreshRecipes);
		storageTab = new StorageTab(store, icons);

		CardLayout cards = new CardLayout();
		JPanel content = new JPanel(cards);
		content.setOpaque(false);
		content.add(recipeTab, "recipes");
		content.add(storageTab, "storage");

		Kit.Segmented tabs = new Kit.Segmented(true, "Recipes", "Storage");
		tabs.onChange(i -> cards.show(content, i == 0 ? "recipes" : "storage"));
		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.setBorder(new EmptyBorder(0, 0, 0, Kit.ThinScrollBarUI.WIDTH));
		header.add(tabs, BorderLayout.CENTER);

		add(header, BorderLayout.NORTH);
		add(content, BorderLayout.CENTER);

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
	 * Levels, quests, account type or settings changed.
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
