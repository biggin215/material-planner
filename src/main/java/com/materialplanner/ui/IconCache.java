package com.materialplanner.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.ImageIcon;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.http.api.item.ItemPrice;

/**
 * Item sprites by id, name → id lookup for materials the player doesn't own, and GE prices.
 */
class IconCache
{
	private final ItemManager itemManager;
	private final Map<Integer, ImageIcon> icons = new HashMap<>();
	private final Map<String, Integer> nameToId = new HashMap<>();

	IconCache(ItemManager itemManager)
	{
		this.itemManager = itemManager;
	}

	/**
	 * @param repaintTarget repainted once the sprite finishes loading
	 */
	ImageIcon get(int itemId, Component repaintTarget)
	{
		if (itemId <= 0)
		{
			return null;
		}
		return icons.computeIfAbsent(itemId, id ->
		{
			AsyncBufferedImage img = itemManager.getImage(id);
			if (repaintTarget != null)
			{
				img.onLoaded(repaintTarget::repaint);
			}
			return new ImageIcon(img);
		});
	}

	/**
	 * Exact-name lookup against the Grand Exchange item list. Untradeable items return -1.
	 */
	int idForName(String name)
	{
		String key = name.toLowerCase();
		return nameToId.computeIfAbsent(key, k ->
		{
			List<ItemPrice> results = itemManager.search(name);
			for (ItemPrice p : results)
			{
				if (p.getName().equalsIgnoreCase(name))
				{
					return p.getId();
				}
			}
			return -1;
		});
	}

	/**
	 * GE price each, or -1 for untradeable or unknown items.
	 */
	long price(String name)
	{
		if ("coins".equalsIgnoreCase(name))
		{
			return 1;
		}
		int id = idForName(name);
		if (id <= 0)
		{
			return -1;
		}
		long p = itemManager.getItemPrice(id);
		return p > 0 ? p : -1;
	}

	/**
	 * A small drawn "x" so the remove control doesn't depend on the font having a ✕ glyph.
	 */
	static ImageIcon removeIcon(Color color)
	{
		int s = 10;
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(color);
		g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.drawLine(1, 1, s - 2, s - 2);
		g.drawLine(s - 2, 1, 1, s - 2);
		g.dispose();
		return new ImageIcon(img);
	}
}
