package com.materialplanner.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Colors, type, spacing and drawing helpers shared by every part of the panel.
 * <p>
 * All text in the panel is drawn through {@link #fit} or {@link #wrap}, which measure it against the space
 * available and shorten it with "..." rather than letting it run past an edge.
 */
final class Theme
{
	// --- surfaces ------------------------------------------------------------------------------------
	static final Color BG = new Color(40, 40, 40);
	static final Color CARD = new Color(31, 31, 32);
	static final Color CARD_HOVER = new Color(38, 38, 40);
	static final Color CARD_BORDER = new Color(50, 50, 52);
	static final Color INSET = new Color(24, 24, 25);
	static final Color SLOT = new Color(46, 44, 40);
	static final Color SLOT_EDGE = new Color(60, 57, 51);
	static final Color DIVIDER = new Color(46, 46, 48);
	static final Color GUIDE = new Color(62, 62, 66);

	// --- text ----------------------------------------------------------------------------------------
	static final Color TEXT = new Color(222, 220, 214);
	static final Color MUTED = new Color(150, 148, 142);
	static final Color FAINT = new Color(108, 106, 102);

	// --- accent & status ------------------------------------------------------------------------------
	/** Warm OSRS gold. */
	static final Color ACCENT = new Color(222, 176, 90);
	static final Color OK = new Color(118, 182, 112);
	static final Color WARN = new Color(222, 170, 84);
	static final Color MAKE = new Color(114, 156, 214);
	static final Color BAD = new Color(214, 108, 96);

	// --- OSRS quantity colors --------------------------------------------------------------------------
	static final Color QTY_YELLOW = new Color(236, 222, 96);
	static final Color QTY_WHITE = new Color(236, 236, 236);
	static final Color QTY_GREEN = new Color(110, 214, 120);

	// --- spacing ---------------------------------------------------------------------------------------
	static final int GAP = 8;
	static final int RADIUS = 8;
	static final int PANEL_PAD = 6;
	/** Full sidebar width RuneLite gives a plugin panel. */
	static final int SIDEBAR_WIDTH = PluginPanel.PANEL_WIDTH + PluginPanel.SCROLLBAR_WIDTH;

	private Theme()
	{
	}

	static Font small()
	{
		return FontManager.getRunescapeSmallFont();
	}

	static Font regular()
	{
		return FontManager.getRunescapeFont();
	}

	static Font bold()
	{
		return FontManager.getRunescapeBoldFont();
	}

	static Graphics2D smooth(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		// The RuneScape fonts are pixel fonts: they look right without text antialiasing
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		return g2;
	}

	static Color alpha(Color c, int a)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
	}

	static Color mix(Color a, Color b, double t)
	{
		return new Color(
			(int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
			(int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
			(int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
	}

	// --- text fitting ---------------------------------------------------------------------------------

	static final String ELLIPSIS = "...";

	/**
	 * The longest prefix of {@code s} that fits {@code width}, with "..." when shortened.
	 */
	static String fit(String s, FontMetrics fm, int width)
	{
		if (s == null)
		{
			return "";
		}
		if (fm.stringWidth(s) <= width)
		{
			return s;
		}
		int ell = fm.stringWidth(ELLIPSIS);
		if (ell >= width)
		{
			return "";
		}
		int lo = 0;
		int hi = s.length();
		while (lo < hi)
		{
			int mid = (lo + hi + 1) / 2;
			if (fm.stringWidth(s.substring(0, mid)) + ell <= width)
			{
				lo = mid;
			}
			else
			{
				hi = mid - 1;
			}
		}
		return s.substring(0, lo).trim() + ELLIPSIS;
	}

	/**
	 * Word-wraps {@code s} into at most {@code maxLines} lines of {@code width}; the last line is shortened
	 * with "..." if text remains.
	 */
	static List<String> wrap(String s, FontMetrics fm, int width, int maxLines)
	{
		List<String> lines = new ArrayList<>();
		if (s == null || s.isEmpty() || width <= 0)
		{
			return lines;
		}
		String[] words = s.split(" ");
		StringBuilder line = new StringBuilder();
		int i = 0;
		while (i < words.length)
		{
			String candidate = line.length() == 0 ? words[i] : line + " " + words[i];
			if (fm.stringWidth(candidate) <= width)
			{
				line.setLength(0);
				line.append(candidate);
				i++;
				continue;
			}
			if (line.length() == 0)
			{
				// A single word wider than the line: it gets shortened
				line.append(words[i]);
				i++;
			}
			if (lines.size() == maxLines - 1)
			{
				break;
			}
			lines.add(fit(line.toString(), fm, width));
			line.setLength(0);
		}
		StringBuilder rest = new StringBuilder(line);
		for (; i < words.length; i++)
		{
			rest.append(rest.length() == 0 ? "" : " ").append(words[i]);
		}
		if (rest.length() > 0)
		{
			lines.add(fit(rest.toString(), fm, width));
		}
		return lines;
	}

	static String escape(String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	// --- numbers ---------------------------------------------------------------------------------------

	static String n(long v)
	{
		return NumberFormat.getIntegerInstance().format(v);
	}

	/**
	 * OSRS inventory style: 12,345 / 150K / 12M.
	 */
	static String shortQty(long v)
	{
		if (v >= 10_000_000)
		{
			return (v / 1_000_000) + "M";
		}
		if (v >= 100_000)
		{
			return (v / 1000) + "K";
		}
		return n(v);
	}

	static Color qtyColor(long v)
	{
		if (v >= 10_000_000)
		{
			return QTY_GREEN;
		}
		if (v >= 100_000)
		{
			return QTY_WHITE;
		}
		return QTY_YELLOW;
	}

	static String gp(long v)
	{
		if (v >= 10_000_000)
		{
			return String.format("%.1fM", v / 1_000_000.0);
		}
		if (v >= 100_000)
		{
			return (v / 1000) + "K";
		}
		return n(v);
	}
}
