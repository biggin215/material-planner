package com.materialplanner.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JComponent;

/**
 * One material in the plan tree, fully self-painted so it can't overflow:
 * <pre>
 *  ›  [slot]  Name ............................  117 / 400
 *             ▬▬▬▬▬▬▬▬▬░░░░░░░░░░░░░░░░░░░░░░░░░
 *             Use 117, make 283 · Sawmill
 * </pre>
 */
class MaterialRow extends JComponent
{
	static final int INDENT = 14;
	private static final int CHEVRON = 12;
	private static final int PAD_Y = 5;
	private static final int TEXT_GAP = 6;
	private static final int BAR_H = 4;

	static final class Detail
	{
		final String text;
		final Color color;
		final int maxLines;

		Detail(String text, Color color, int maxLines)
		{
			this.text = text;
			this.color = color;
			this.maxLines = maxLines;
		}
	}

	private final String name;
	private final long have;
	private final long need;
	private final Color status;
	private final int depth;
	private final boolean expandable;
	private final boolean collapsed;
	private final List<Detail> details = new ArrayList<>();
	private ImageIcon icon;
	private Runnable onToggle;
	/** rails[L] (1..depth): whether the guide line at level L continues below this row. */
	private boolean[] rails = new boolean[0];
	private int widthHint;
	private int measuredWidth = -1;
	private int measuredHeight;

	MaterialRow(String name, long have, long need, Color status, int depth, boolean expandable, boolean collapsed, int widthHint)
	{
		this.name = name;
		this.have = have;
		this.need = need;
		this.status = status;
		this.depth = depth;
		this.expandable = expandable;
		this.collapsed = collapsed;
		this.widthHint = widthHint;
		setOpaque(false);
		if (expandable)
		{
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					if (e.getButton() == MouseEvent.BUTTON1 && e.getX() < slotX() + Kit.Slot.SIZE && onToggle != null)
					{
						onToggle.run();
					}
				}
			});
			addMouseMotionListener(new MouseAdapter()
			{
				@Override
				public void mouseMoved(MouseEvent e)
				{
					setCursor(e.getX() < slotX() + Kit.Slot.SIZE
						? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
				}
			});
		}
	}

	MaterialRow detail(String text, Color color, int maxLines)
	{
		if (text != null && !text.isEmpty())
		{
			details.add(new Detail(text, color, maxLines));
			measuredWidth = -1;
		}
		return this;
	}

	MaterialRow rails(boolean[] r)
	{
		rails = r;
		return this;
	}

	/** x of the vertical guide for children at {@code level}: under the parent's chevron. */
	static int guideX(int level)
	{
		return (level - 1) * INDENT + CHEVRON / 2;
	}

	MaterialRow icon(ImageIcon i)
	{
		icon = i;
		return this;
	}

	MaterialRow onToggle(Runnable r)
	{
		onToggle = r;
		return this;
	}

	List<Detail> getDetails()
	{
		return details;
	}

	String getItemName()
	{
		return name;
	}

	private int slotX()
	{
		return depth * INDENT + CHEVRON;
	}

	private int textX()
	{
		return slotX() + Kit.Slot.SIZE + TEXT_GAP;
	}

	private int textWidth(int width)
	{
		return Math.max(20, width - textX() - 6);
	}

	private Font font()
	{
		return Theme.small();
	}

	private int heightFor(int width)
	{
		FontMetrics fm = getFontMetrics(font());
		int lh = fm.getHeight();
		// line 1: name; line 2: bar + count; then details
		int h = lh + 1 + lh + 2;
		for (Detail d : details)
		{
			h += Theme.wrap(d.text, fm, textWidth(width), d.maxLines).size() * lh;
		}
		return Math.max(Kit.Slot.SIZE, h) + PAD_Y * 2;
	}

	private int layoutWidth()
	{
		return getWidth() > 0 ? getWidth() : widthHint;
	}

	@Override
	public Dimension getPreferredSize()
	{
		int w = layoutWidth();
		if (w != measuredWidth)
		{
			measuredWidth = w;
			measuredHeight = heightFor(w);
		}
		return new Dimension(widthHint, measuredHeight);
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	@Override
	public void setBounds(int x, int y, int width, int height)
	{
		int before = getWidth();
		super.setBounds(x, y, width, height);
		if (width != before && width > 0 && heightFor(width) != height)
		{
			measuredWidth = -1;
			javax.swing.SwingUtilities.invokeLater(this::revalidate);
		}
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();
		g2.setFont(font());
		FontMetrics fm = g2.getFontMetrics();
		int lh = fm.getHeight();

		// Tree guides: rails for ancestors that continue below, and an elbow into this row's slot
		g2.setColor(Theme.GUIDE);
		for (int level = 1; level < depth; level++)
		{
			if (level < rails.length && rails[level])
			{
				g2.drawLine(guideX(level), 0, guideX(level), h);
			}
		}
		if (depth > 0)
		{
			int gx = guideX(depth);
			int midY = PAD_Y + Kit.Slot.SIZE / 2;
			boolean continues = depth < rails.length && rails[depth];
			g2.drawLine(gx, 0, gx, continues ? h : midY);
			g2.drawLine(gx, midY, slotX() - 2, midY);
		}

		if (expandable)
		{
			int cy = PAD_Y + (Kit.Slot.SIZE - CHEVRON) / 2;
			(collapsed ? Icons.CHEVRON_RIGHT : Icons.CHEVRON_DOWN).paint(g2, slotX() - CHEVRON, cy, CHEVRON, Theme.MUTED);
		}

		Kit.Slot.paintSlot(this, g2, slotX(), PAD_Y, icon, false);

		int tx = textX();
		int tw = textWidth(w);
		int y = PAD_Y;

		// Line 1: the name, with the whole width to itself
		g2.setColor(Theme.TEXT);
		g2.drawString(Theme.fit(name, fm, tw), tx, y + fm.getAscent());
		y += lh + 1;

		// Line 2: progress bar, then have / need
		String count = Theme.n(have) + " / " + Theme.n(need);
		if (fm.stringWidth(count) > tw / 2)
		{
			// Big numbers go to the in-game K / M form so the bar keeps some room
			count = Theme.shortQty(have) + " / " + Theme.shortQty(need);
		}
		int cw = fm.stringWidth(count);
		int barW = Math.max(16, tw - cw - 8);
		int barY = y + (lh - BAR_H) / 2;
		double frac = need <= 0 ? 1 : Math.min(1.0, (double) have / need);
		g2.setColor(Theme.INSET);
		g2.fill(new RoundRectangle2D.Double(tx, barY, barW, BAR_H, BAR_H, BAR_H));
		int fill = (int) Math.round(barW * frac);
		if (fill > 0)
		{
			g2.setColor(status);
			g2.fill(new RoundRectangle2D.Double(tx, barY, Math.max(fill, BAR_H), BAR_H, BAR_H, BAR_H));
		}
		g2.setColor(have >= need ? Theme.MUTED : status);
		g2.drawString(Theme.fit(count, fm, tw - barW - 8), tx + barW + 8, y + fm.getAscent());
		y += lh + 2;

		for (Detail d : details)
		{
			g2.setColor(d.color);
			for (String line : Theme.wrap(d.text, fm, tw, d.maxLines))
			{
				g2.drawString(line, tx, y + fm.getAscent());
				y += lh;
			}
		}
		g2.dispose();
	}
}
