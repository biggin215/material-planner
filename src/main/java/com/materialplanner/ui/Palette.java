package com.materialplanner.ui;

import java.awt.Color;

/**
 * Muted colors for the side panel. RuneLite's stock status colors are fully saturated, which
 * gets harsh when a whole list of materials is colored at once; these sit closer to the
 * panel's gray so color reads as a hint rather than a highlight.
 */
final class Palette
{
	/** Main text: off-white rather than pure white. */
	static final Color TEXT = new Color(214, 214, 214);
	/** Secondary text: subtitles, counts. */
	static final Color MUTED = new Color(145, 145, 145);
	/** Tertiary text: hints, section labels. */
	static final Color FAINT = new Color(110, 110, 110);

	static final Color OK = new Color(122, 184, 122);
	static final Color WARN = new Color(212, 170, 92);
	static final Color MAKE = new Color(120, 160, 210);
	static final Color BAD = new Color(214, 112, 104);

	static final Color PANEL = new Color(40, 40, 40);
	static final Color ROW = new Color(33, 33, 33);
	static final Color ROW_ALT = new Color(37, 37, 37);
	static final Color ROW_SELECTED = new Color(50, 50, 50);
	static final Color DIVIDER = new Color(52, 52, 52);

	private Palette()
	{
	}

	static String hex(Color c)
	{
		return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}
}
