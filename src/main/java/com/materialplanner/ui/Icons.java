package com.materialplanner.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

/**
 * Small vector icons, drawn rather than typed: the RuneScape fonts have no arrows, crosses or check marks.
 * Each draws centered in a {@code size}×{@code size} box at (x, y).
 */
final class Icons
{
	interface Painter
	{
		void paint(Graphics2D g, int x, int y, int size, Color color);
	}

	private Icons()
	{
	}

	private static BasicStroke stroke(float w)
	{
		return new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	}

	static final Painter CHEVRON_DOWN = (g, x, y, s, c) ->
	{
		g.setColor(c);
		g.setStroke(stroke(1.6f));
		double cx = x + s / 2.0;
		double cy = y + s / 2.0;
		double w = s * 0.26;
		Path2D p = new Path2D.Double();
		p.moveTo(cx - w, cy - w / 2);
		p.lineTo(cx, cy + w / 2);
		p.lineTo(cx + w, cy - w / 2);
		g.draw(p);
	};

	static final Painter CHEVRON_RIGHT = (g, x, y, s, c) ->
	{
		g.setColor(c);
		g.setStroke(stroke(1.6f));
		double cx = x + s / 2.0;
		double cy = y + s / 2.0;
		double w = s * 0.26;
		Path2D p = new Path2D.Double();
		p.moveTo(cx - w / 2, cy - w);
		p.lineTo(cx + w / 2, cy);
		p.lineTo(cx - w / 2, cy + w);
		g.draw(p);
	};

	static final Painter CROSS = (g, x, y, s, c) ->
	{
		g.setColor(c);
		g.setStroke(stroke(1.6f));
		double m = s * 0.3;
		g.draw(new Line2D.Double(x + m, y + m, x + s - m, y + s - m));
		g.draw(new Line2D.Double(x + s - m, y + m, x + m, y + s - m));
	};

	static final Painter PLUS = (g, x, y, s, c) ->
	{
		g.setColor(c);
		g.setStroke(stroke(1.6f));
		double m = s * 0.28;
		g.draw(new Line2D.Double(x + m, y + s / 2.0, x + s - m, y + s / 2.0));
		g.draw(new Line2D.Double(x + s / 2.0, y + m, x + s / 2.0, y + s - m));
	};

	static final Painter MINUS = (g, x, y, s, c) ->
	{
		g.setColor(c);
		g.setStroke(stroke(1.6f));
		double m = s * 0.28;
		g.draw(new Line2D.Double(x + m, y + s / 2.0, x + s - m, y + s / 2.0));
	};

	static final Painter SEARCH = (g, x, y, s, c) ->
	{
		g.setColor(c);
		g.setStroke(stroke(1.5f));
		double r = s * 0.27;
		double cx = x + s * 0.44;
		double cy = y + s * 0.44;
		g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
		g.draw(new Line2D.Double(cx + r * 0.72, cy + r * 0.72, x + s * 0.8, y + s * 0.8));
	};

	static final Painter REFRESH = (g, x, y, s, c) ->
	{
		g.setColor(c);
		g.setStroke(stroke(1.5f));
		double r = s * 0.28;
		double cx = x + s / 2.0;
		double cy = y + s / 2.0;
		g.draw(new java.awt.geom.Arc2D.Double(cx - r, cy - r, r * 2, r * 2, 60, 280, java.awt.geom.Arc2D.OPEN));
		// arrow head at the arc's start (60°)
		double ax = cx + r * Math.cos(Math.toRadians(60));
		double ay = cy - r * Math.sin(Math.toRadians(60));
		Path2D p = new Path2D.Double();
		p.moveTo(ax - s * 0.16, ay - s * 0.06);
		p.lineTo(ax, ay);
		p.lineTo(ax - s * 0.02, ay + s * 0.17);
		g.draw(p);
	};

	static final Painter CHECK = (g, x, y, s, c) ->
	{
		g.setColor(c);
		g.setStroke(stroke(1.7f));
		Path2D p = new Path2D.Double();
		p.moveTo(x + s * 0.24, y + s * 0.52);
		p.lineTo(x + s * 0.42, y + s * 0.7);
		p.lineTo(x + s * 0.76, y + s * 0.32);
		g.draw(p);
	};

	/** A filled dot, used as a status marker. */
	static final Painter DOT = (g, x, y, s, c) ->
	{
		g.setColor(c);
		double r = s * 0.22;
		g.fill(new Ellipse2D.Double(x + s / 2.0 - r, y + s / 2.0 - r, r * 2, r * 2));
	};
}
