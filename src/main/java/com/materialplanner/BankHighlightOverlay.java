package com.materialplanner;

import com.materialplanner.items.ItemStore;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Outlines bank items the current plan needs you to withdraw.
 */
class BankHighlightOverlay extends WidgetItemOverlay
{
	private final ItemManager itemManager;
	private final MaterialPlannerConfig config;

	private volatile Set<String> neededNames = Collections.emptySet();
	/** item id → whether it's needed; reset whenever the needed set changes */
	private volatile Map<Integer, Boolean> matchCache = new ConcurrentHashMap<>();

	@Inject
	BankHighlightOverlay(ItemManager itemManager, MaterialPlannerConfig config)
	{
		this.itemManager = itemManager;
		this.config = config;
		showOnBank();
	}

	/**
	 * @param names normalized item names to highlight
	 */
	void setNeeded(Set<String> names)
	{
		neededNames = names;
		matchCache = new ConcurrentHashMap<>();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!config.highlightBank() || neededNames.isEmpty())
		{
			return;
		}

		Boolean hit = matchCache.computeIfAbsent(itemId, id ->
		{
			int canonical = itemManager.canonicalize(id);
			String name = itemManager.getItemComposition(canonical).getName();
			return neededNames.contains(ItemStore.normalize(name));
		});
		if (!hit)
		{
			return;
		}

		Color color = config.highlightColor();
		Rectangle bounds = widgetItem.getCanvasBounds();
		BufferedImage outline = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), color);
		if (outline != null && bounds != null)
		{
			graphics.drawImage(outline, (int) bounds.getX(), (int) bounds.getY(), null);
		}
	}
}
