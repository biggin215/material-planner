package com.materialplanner.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * The panel's small component kit. Everything paints itself with {@link Theme} colors and measures its own
 * text, so it looks the same under any look-and-feel and can't overflow its bounds.
 */
final class Kit
{
	private Kit()
	{
	}

	// =================================================================================================
	// Containers

	/**
	 * A rounded card.
	 */
	static class Card extends JPanel
	{
		private Color fill = Theme.CARD;
		private Color edge = Theme.CARD_BORDER;
		private Color accent;
		private boolean hover;
		private boolean hoverable;

		Card(java.awt.LayoutManager layout)
		{
			super(layout);
			setOpaque(false);
			setBorder(new EmptyBorder(Theme.GAP, Theme.GAP, Theme.GAP, Theme.GAP));
		}

		Card fill(Color c)
		{
			fill = c;
			return this;
		}

		Card edge(Color c)
		{
			edge = c;
			return this;
		}

		/** A colored stripe down the left edge, for status. */
		Card accent(Color c)
		{
			accent = c;
			repaint();
			return this;
		}

		Card hoverable()
		{
			if (!hoverable)
			{
				hoverable = true;
				addMouseListener(new MouseAdapter()
				{
					@Override
					public void mouseEntered(MouseEvent e)
					{
						hover = true;
						repaint();
					}

					@Override
					public void mouseExited(MouseEvent e)
					{
						if (!contains(e.getPoint()))
						{
							hover = false;
							repaint();
						}
					}
				});
			}
			return this;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = Theme.smooth(g);
			RoundRectangle2D r = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, Theme.RADIUS, Theme.RADIUS);
			g2.setColor(hover ? Theme.CARD_HOVER : fill);
			g2.fill(r);
			if (accent != null)
			{
				g2.setClip(r);
				g2.setColor(accent);
				g2.fillRect(0, 0, 3, getHeight());
				g2.setClip(null);
			}
			if (edge != null)
			{
				g2.setColor(edge);
				g2.draw(r);
			}
			g2.dispose();
		}
	}

	/**
	 * Scroll content that always matches the visible width, so nothing extends past the right edge.
	 */
	static class WidthTracking extends JPanel implements Scrollable
	{
		WidthTracking(java.awt.LayoutManager layout)
		{
			super(layout);
			setOpaque(false);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return Math.max(visibleRect.height - 16, 16);
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	static JScrollPane scroll(Component view)
	{
		JScrollPane s = new JScrollPane(view);
		s.setBorder(null);
		s.setOpaque(false);
		s.getViewport().setOpaque(false);
		s.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		// Always shown so the content width never jumps when a list grows past the window
		s.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		JScrollBar bar = s.getVerticalScrollBar();
		bar.setUI(new ThinScrollBarUI());
		bar.setPreferredSize(new Dimension(ThinScrollBarUI.WIDTH, 0));
		bar.setOpaque(false);
		bar.setUnitIncrement(16);
		return s;
	}

	/**
	 * Slim rounded scrollbar with no arrow buttons.
	 */
	static class ThinScrollBarUI extends BasicScrollBarUI
	{
		static final int WIDTH = 8;

		@Override
		protected JButton createDecreaseButton(int orientation)
		{
			return zeroButton();
		}

		@Override
		protected JButton createIncreaseButton(int orientation)
		{
			return zeroButton();
		}

		private static JButton zeroButton()
		{
			JButton b = new JButton();
			Dimension z = new Dimension(0, 0);
			b.setPreferredSize(z);
			b.setMinimumSize(z);
			b.setMaximumSize(z);
			return b;
		}

		@Override
		protected void paintTrack(Graphics g, JComponent c, Rectangle r)
		{
			// no track: the thumb floats over the panel
		}

		@Override
		protected void paintThumb(Graphics g, JComponent c, Rectangle r)
		{
			if (r.isEmpty() || !scrollbar.isEnabled() || r.height >= scrollbar.getHeight())
			{
				return;
			}
			Graphics2D g2 = Theme.smooth(g);
			g2.setColor(isThumbRollover() ? Theme.mix(Theme.GUIDE, Theme.MUTED, 0.4) : Theme.GUIDE);
			g2.fill(new RoundRectangle2D.Double(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 4, 4));
			g2.dispose();
		}

		@Override
		protected Dimension getMinimumThumbSize()
		{
			return new Dimension(WIDTH, 24);
		}
	}

	// =================================================================================================
	// Text

	/**
	 * One line of styled text in a {@link TextBlock}.
	 */
	static final class Line
	{
		final String text;
		final Font font;
		final Color color;
		final int maxLines;

		Line(String text, Font font, Color color)
		{
			this(text, font, color, 1);
		}

		Line(String text, Font font, Color color, int maxLines)
		{
			this.text = text == null ? "" : text;
			this.font = font;
			this.color = color;
			this.maxLines = Math.max(1, maxLines);
		}
	}

	/**
	 * Stacked lines of text that always fit: each line is shortened, or wrapped up to its own limit, to the
	 * component's width. Its preferred height follows from that.
	 */
	static class TextBlock extends JComponent
	{
		static final int LINE_GAP = 1;
		private List<Line> lines = new ArrayList<>();
		/** Used for sizing before the component has been laid out. */
		private int widthHint;
		private int measuredWidth = -1;
		private int measuredHeight;

		TextBlock(int widthHint)
		{
			this.widthHint = widthHint;
			setOpaque(false);
		}

		TextBlock lines(Line... ls)
		{
			lines = new ArrayList<>();
			for (Line l : ls)
			{
				if (l != null && !l.text.isEmpty())
				{
					lines.add(l);
				}
			}
			measuredWidth = -1;
			revalidate();
			repaint();
			return this;
		}

		List<Line> getLines()
		{
			return lines;
		}

		private int layoutWidth()
		{
			return getWidth() > 0 ? getWidth() : widthHint;
		}

		private List<List<String>> wrapped(int width)
		{
			List<List<String>> out = new ArrayList<>();
			for (Line l : lines)
			{
				FontMetrics fm = getFontMetrics(l.font);
				out.add(l.maxLines == 1
					? java.util.Collections.singletonList(Theme.fit(l.text, fm, width))
					: Theme.wrap(l.text, fm, width, l.maxLines));
			}
			return out;
		}

		private int heightFor(int width)
		{
			int h = 0;
			List<List<String>> w = wrapped(width);
			for (int i = 0; i < lines.size(); i++)
			{
				FontMetrics fm = getFontMetrics(lines.get(i).font);
				h += w.get(i).size() * (fm.getHeight() + LINE_GAP);
			}
			return Math.max(0, h - LINE_GAP);
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
		public Dimension getMinimumSize()
		{
			return new Dimension(0, getPreferredSize().height);
		}

		@Override
		public void setBounds(int x, int y, int width, int height)
		{
			int before = getWidth();
			super.setBounds(x, y, width, height);
			// Wrapping can change with width: if it does, ask the parent to lay out again
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
			int y = 0;
			List<List<String>> w = wrapped(getWidth());
			for (int i = 0; i < lines.size(); i++)
			{
				Line l = lines.get(i);
				g2.setFont(l.font);
				g2.setColor(l.color);
				FontMetrics fm = g2.getFontMetrics();
				for (String s : w.get(i))
				{
					g2.drawString(s, 0, y + fm.getAscent());
					y += fm.getHeight() + LINE_GAP;
				}
			}
			g2.dispose();
		}
	}

	/**
	 * Small uppercase section label with an optional count and a thin rule.
	 */
	static class SectionHeader extends JComponent
	{
		private final String title;
		private String detail;

		SectionHeader(String title)
		{
			this.title = title;
			setOpaque(false);
		}

		void detail(String d)
		{
			detail = d;
			repaint();
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(100, getFontMetrics(Theme.small()).getHeight() + 6);
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = Theme.smooth(g);
			g2.setFont(Theme.small());
			FontMetrics fm = g2.getFontMetrics();
			int base = fm.getAscent() + 1;
			g2.setColor(Theme.ACCENT);
			g2.drawString(title, 0, base);
			int x = fm.stringWidth(title) + 6;
			if (detail != null)
			{
				g2.setColor(Theme.FAINT);
				String d = Theme.fit(detail, fm, getWidth() - x);
				g2.drawString(d, x, base);
				x += fm.stringWidth(d) + 6;
			}
			int midY = base - fm.getAscent() / 2 + 1;
			if (x < getWidth())
			{
				g2.setColor(Theme.DIVIDER);
				g2.drawLine(x, midY, getWidth() - 1, midY);
			}
			g2.dispose();
		}
	}

	// =================================================================================================
	// Controls

	/**
	 * A round icon button with a hover state.
	 */
	static class IconButton extends JComponent
	{
		private final Icons.Painter icon;
		private Color color = Theme.MUTED;
		private Color hoverColor = Theme.TEXT;
		private boolean hover;
		private Runnable action;
		private final int size;

		IconButton(Icons.Painter icon, int size, String tooltip)
		{
			this.icon = icon;
			this.size = size;
			setToolTipText(tooltip);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setOpaque(false);
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover = false;
					repaint();
				}

				@Override
				public void mouseClicked(MouseEvent e)
				{
					if (action != null && e.getButton() == MouseEvent.BUTTON1)
					{
						action.run();
					}
				}
			});
		}

		IconButton colors(Color normal, Color hovered)
		{
			color = normal;
			hoverColor = hovered;
			return this;
		}

		IconButton onClick(Runnable r)
		{
			action = r;
			return this;
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(size, size);
		}

		@Override
		public Dimension getMinimumSize()
		{
			return getPreferredSize();
		}

		@Override
		public Dimension getMaximumSize()
		{
			return getPreferredSize();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = Theme.smooth(g);
			if (hover)
			{
				g2.setColor(Theme.alpha(Color.WHITE, 22));
				g2.fillOval(0, 0, size - 1, size - 1);
			}
			icon.paint(g2, 0, 0, size, hover ? hoverColor : color);
			g2.dispose();
		}
	}

	/**
	 * A text button that looks like a link: muted until hovered.
	 */
	static class TextButton extends JComponent
	{
		private final String text;
		private boolean hover;
		private Runnable action;

		TextButton(String text)
		{
			this.text = text;
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setOpaque(false);
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover = false;
					repaint();
				}

				@Override
				public void mouseClicked(MouseEvent e)
				{
					if (action != null && e.getButton() == MouseEvent.BUTTON1)
					{
						action.run();
					}
				}
			});
		}

		TextButton onClick(Runnable r)
		{
			action = r;
			return this;
		}

		@Override
		public Dimension getPreferredSize()
		{
			FontMetrics fm = getFontMetrics(Theme.small());
			return new Dimension(fm.stringWidth(text) + 2, fm.getHeight() + 6);
		}

		@Override
		public Dimension getMaximumSize()
		{
			return getPreferredSize();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = Theme.smooth(g);
			g2.setFont(Theme.small());
			FontMetrics fm = g2.getFontMetrics();
			g2.setColor(hover ? Theme.ACCENT : Theme.MUTED);
			g2.drawString(text, 1, fm.getAscent() + 1);
			g2.dispose();
		}
	}

	/**
	 * Segmented control: a row of options with the selected one raised. Used for tabs and mode toggles.
	 */
	static class Segmented extends JComponent
	{
		private final String[] options;
		private int selected;
		private int hover = -1;
		private IntConsumer listener;
		private final boolean large;

		Segmented(boolean large, String... options)
		{
			this.options = options;
			this.large = large;
			setOpaque(false);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			MouseAdapter m = new MouseAdapter()
			{
				@Override
				public void mouseMoved(MouseEvent e)
				{
					int h = indexAt(e.getX());
					if (h != hover)
					{
						hover = h;
						repaint();
					}
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover = -1;
					repaint();
				}

				@Override
				public void mousePressed(MouseEvent e)
				{
					int i = indexAt(e.getX());
					if (i >= 0 && i != selected)
					{
						select(i);
					}
				}
			};
			addMouseListener(m);
			addMouseMotionListener(m);
		}

		private int indexAt(int x)
		{
			if (getWidth() <= 0)
			{
				return -1;
			}
			return Math.max(0, Math.min(options.length - 1, x * options.length / getWidth()));
		}

		void onChange(IntConsumer l)
		{
			listener = l;
		}

		void select(int i)
		{
			selected = i;
			repaint();
			if (listener != null)
			{
				listener.accept(i);
			}
		}

		void setSelectedQuietly(int i)
		{
			selected = i;
			repaint();
		}

		int getSelected()
		{
			return selected;
		}

		private Font font()
		{
			return large ? Theme.bold() : Theme.small();
		}

		@Override
		public Dimension getPreferredSize()
		{
			FontMetrics fm = getFontMetrics(font());
			int w = 0;
			for (String o : options)
			{
				w = Math.max(w, fm.stringWidth(o));
			}
			return new Dimension((w + 16) * options.length + 4, fm.getHeight() + (large ? 12 : 6));
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = Theme.smooth(g);
			int w = getWidth();
			int h = getHeight();
			g2.setColor(Theme.INSET);
			g2.fill(new RoundRectangle2D.Double(0, 0, w, h, h, h));
			int seg = (w - 4) / options.length;
			g2.setFont(font());
			FontMetrics fm = g2.getFontMetrics();
			for (int i = 0; i < options.length; i++)
			{
				int x = 2 + i * seg;
				if (i == selected)
				{
					g2.setColor(Theme.CARD_HOVER);
					g2.fill(new RoundRectangle2D.Double(x, 2, seg, h - 4, h - 4, h - 4));
					g2.setColor(Theme.alpha(Theme.ACCENT, 90));
					g2.draw(new RoundRectangle2D.Double(x + 0.5, 2.5, seg - 1, h - 5, h - 5, h - 5));
				}
				String label = Theme.fit(options[i], fm, seg - 8);
				g2.setColor(i == selected ? Theme.ACCENT : (i == hover ? Theme.TEXT : Theme.MUTED));
				int tx = x + (seg - fm.stringWidth(label)) / 2;
				int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
				g2.drawString(label, tx, ty);
			}
			g2.dispose();
		}
	}

	/**
	 * Rounded text field with a search icon, placeholder and clear button.
	 */
	static class SearchField extends JPanel
	{
		final JTextField field = new JTextField();
		private String placeholder = "";
		private final IconButton clear;

		SearchField()
		{
			super(new BorderLayout(4, 0));
			setOpaque(false);
			setBorder(new EmptyBorder(0, 26, 0, 4));
			field.setOpaque(false);
			field.setBorder(BorderFactory.createEmptyBorder());
			field.setForeground(Theme.TEXT);
			field.setCaretColor(Theme.ACCENT);
			field.setSelectionColor(Theme.alpha(Theme.ACCENT, 90));
			field.setFont(Theme.small());
			add(field, BorderLayout.CENTER);
			clear = new IconButton(Icons.CROSS, 18, "Clear").onClick(() -> field.setText(""));
			JPanel east = new JPanel(new java.awt.GridBagLayout());
			east.setOpaque(false);
			east.add(clear);
			add(east, BorderLayout.EAST);
			clear.setVisible(false);
			field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
			{
				private void update()
				{
					clear.setVisible(!field.getText().isEmpty());
					repaint();
				}

				@Override
				public void insertUpdate(javax.swing.event.DocumentEvent e)
				{
					update();
				}

				@Override
				public void removeUpdate(javax.swing.event.DocumentEvent e)
				{
					update();
				}

				@Override
				public void changedUpdate(javax.swing.event.DocumentEvent e)
				{
					update();
				}
			});
			field.addFocusListener(new FocusAdapter()
			{
				@Override
				public void focusGained(FocusEvent e)
				{
					repaint();
				}

				@Override
				public void focusLost(FocusEvent e)
				{
					repaint();
				}
			});
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					field.requestFocusInWindow();
				}
			});
		}

		void placeholder(String p)
		{
			placeholder = p;
			repaint();
		}

		String getText()
		{
			return field.getText();
		}

		void setText(String t)
		{
			field.setText(t);
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(100, 30);
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, 30);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = Theme.smooth(g);
			RoundRectangle2D r = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, Theme.RADIUS, Theme.RADIUS);
			g2.setColor(Theme.INSET);
			g2.fill(r);
			g2.setColor(field.hasFocus() ? Theme.alpha(Theme.ACCENT, 120) : Theme.CARD_BORDER);
			g2.draw(r);
			Icons.SEARCH.paint(g2, 6, (getHeight() - 16) / 2, 16, field.hasFocus() ? Theme.ACCENT : Theme.FAINT);
			if (field.getText().isEmpty())
			{
				g2.setFont(Theme.small());
				FontMetrics fm = g2.getFontMetrics();
				g2.setColor(Theme.FAINT);
				Insets in = getInsets();
				int avail = getWidth() - in.left - in.right - 4;
				g2.drawString(Theme.fit(placeholder, fm, avail), in.left + 1, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
			}
			g2.dispose();
		}
	}

	/**
	 * A rounded select box that opens a styled menu.
	 */
	static class Dropdown<T> extends JComponent
	{
		private List<T> items = new ArrayList<>();
		private T selected;
		private Function<T, String> label = String::valueOf;
		private Consumer<T> listener;
		private boolean hover;

		Dropdown()
		{
			setOpaque(false);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover = false;
					repaint();
				}

				@Override
				public void mousePressed(MouseEvent e)
				{
					if (e.getButton() == MouseEvent.BUTTON1)
					{
						open();
					}
				}
			});
		}

		void setItems(List<T> items, Function<T, String> label)
		{
			this.items = new ArrayList<>(items);
			this.label = label;
			if (selected == null || !this.items.contains(selected))
			{
				selected = this.items.isEmpty() ? null : this.items.get(0);
			}
			repaint();
		}

		void onChange(Consumer<T> l)
		{
			listener = l;
		}

		T getSelected()
		{
			return selected;
		}

		void setSelected(T t)
		{
			if (items.contains(t))
			{
				selected = t;
				repaint();
			}
		}

		private void open()
		{
			JPopupMenu menu = popup();
			for (T item : items)
			{
				JMenuItem mi = menuItem(label.apply(item), item.equals(selected));
				mi.addActionListener(ev ->
				{
					selected = item;
					repaint();
					if (listener != null)
					{
						listener.accept(item);
					}
				});
				menu.add(mi);
			}
			menu.setPreferredSize(new Dimension(getWidth(), menu.getPreferredSize().height));
			menu.show(this, 0, getHeight() + 2);
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(100, 28);
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, 28);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = Theme.smooth(g);
			RoundRectangle2D r = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, Theme.RADIUS, Theme.RADIUS);
			g2.setColor(hover ? Theme.CARD_HOVER : Theme.CARD);
			g2.fill(r);
			g2.setColor(Theme.CARD_BORDER);
			g2.draw(r);
			g2.setFont(Theme.small());
			FontMetrics fm = g2.getFontMetrics();
			String text = selected == null ? "" : label.apply(selected);
			g2.setColor(Theme.TEXT);
			g2.drawString(Theme.fit(text, fm, getWidth() - 34), 10, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
			Icons.CHEVRON_DOWN.paint(g2, getWidth() - 22, (getHeight() - 14) / 2, 14, Theme.MUTED);
			g2.dispose();
		}
	}

	static JPopupMenu popup()
	{
		JPopupMenu menu = new JPopupMenu();
		menu.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(Theme.CARD_BORDER),
			BorderFactory.createEmptyBorder(3, 0, 3, 0)));
		menu.setBackground(Theme.CARD);
		return menu;
	}

	static JMenuItem menuItem(String text, boolean checked)
	{
		JMenuItem mi = new JMenuItem(text)
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = Theme.smooth(g);
				g2.setColor(getModel().isArmed() ? Theme.CARD_HOVER : Theme.CARD);
				g2.fillRect(0, 0, getWidth(), getHeight());
				g2.setFont(Theme.small());
				FontMetrics fm = g2.getFontMetrics();
				if (checked)
				{
					Icons.CHECK.paint(g2, 6, (getHeight() - 14) / 2, 14, Theme.ACCENT);
				}
				g2.setColor(!isEnabled() ? Theme.FAINT : checked ? Theme.ACCENT : Theme.TEXT);
				g2.drawString(Theme.fit(getText(), fm, getWidth() - 30), 24, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
				g2.dispose();
			}

			@Override
			public Dimension getPreferredSize()
			{
				FontMetrics fm = getFontMetrics(Theme.small());
				return new Dimension(Math.min(320, fm.stringWidth(getText()) + 34), fm.getHeight() + 8);
			}
		};
		mi.setOpaque(false);
		mi.setBorder(BorderFactory.createEmptyBorder());
		return mi;
	}

	static JComponent separator()
	{
		JComponent s = new JComponent()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				g.setColor(Theme.CARD);
				g.fillRect(0, 0, getWidth(), getHeight());
				g.setColor(Theme.DIVIDER);
				g.fillRect(8, getHeight() / 2, getWidth() - 16, 1);
			}

			@Override
			public Dimension getPreferredSize()
			{
				return new Dimension(10, 7);
			}
		};
		return s;
	}

	/**
	 * A non-clickable heading inside a popup menu.
	 */
	static JMenuItem menuTitle(String text)
	{
		JMenuItem mi = menuItem(text, false);
		mi.setEnabled(false);
		return mi;
	}

	/**
	 * A compact number control: [−] value [+]. Click the value to type, or scroll over it.
	 */
	static class Stepper extends JPanel
	{
		private int value;
		private int min;
		private int max;
		private String prefix = "";
		private IntConsumer listener;
		private final JComponent display;
		private final JTextField editor = new JTextField();
		private boolean editing;

		Stepper(int value, int min, int max)
		{
			super(new BorderLayout());
			this.value = value;
			this.min = min;
			this.max = max;
			setOpaque(false);
			IconButton minus = new IconButton(Icons.MINUS, 20, "Less").onClick(() -> set(this.value - step()));
			IconButton plus = new IconButton(Icons.PLUS, 20, "More").onClick(() -> set(this.value + step()));
			display = new JComponent()
			{
				@Override
				protected void paintComponent(Graphics g)
				{
					if (editing)
					{
						return;
					}
					Graphics2D g2 = Theme.smooth(g);
					g2.setFont(Theme.small());
					FontMetrics fm = g2.getFontMetrics();
					String p = prefix;
					String v = Theme.n(Stepper.this.value);
					String full = Theme.fit(p + v, fm, getWidth() - 2);
					int x = (getWidth() - fm.stringWidth(full)) / 2;
					int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
					if (full.startsWith(p) && !p.isEmpty())
					{
						g2.setColor(Theme.MUTED);
						g2.drawString(p, x, y);
						g2.setColor(Theme.TEXT);
						g2.drawString(full.substring(p.length()), x + fm.stringWidth(p), y);
					}
					else
					{
						g2.setColor(Theme.TEXT);
						g2.drawString(full, x, y);
					}
					g2.dispose();
				}
			};
			display.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
			display.setToolTipText("Click to type a number, or scroll");
			display.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					startEdit();
				}
			});
			addMouseWheelListener(e -> set(this.value - e.getWheelRotation() * step()));

			editor.setFont(Theme.small());
			editor.setForeground(Theme.TEXT);
			editor.setCaretColor(Theme.ACCENT);
			editor.setOpaque(false);
			editor.setBorder(BorderFactory.createEmptyBorder());
			editor.setHorizontalAlignment(JTextField.CENTER);
			editor.addActionListener(e -> commitEdit());
			editor.addFocusListener(new FocusAdapter()
			{
				@Override
				public void focusLost(FocusEvent e)
				{
					commitEdit();
				}
			});
			editor.addKeyListener(new KeyAdapter()
			{
				@Override
				public void keyPressed(KeyEvent e)
				{
					if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
					{
						stopEdit();
					}
				}
			});

			JPanel center = new JPanel(new java.awt.CardLayout());
			center.setOpaque(false);
			center.add(display, "show");
			center.add(editor, "edit");
			add(wrapCenter(minus), BorderLayout.WEST);
			add(center, BorderLayout.CENTER);
			add(wrapCenter(plus), BorderLayout.EAST);
			setBorder(new EmptyBorder(2, 3, 2, 3));
		}

		private static JPanel wrapCenter(JComponent c)
		{
			JPanel p = new JPanel(new java.awt.GridBagLayout());
			p.setOpaque(false);
			p.add(c);
			return p;
		}

		/** Shift-free stepping that scales with the value, so big counts don't take forever. */
		private int step()
		{
			return value >= 1000 ? 100 : value >= 100 ? 10 : 1;
		}

		Stepper prefix(String p)
		{
			prefix = p;
			display.repaint();
			return this;
		}

		Stepper range(int min, int max)
		{
			this.min = min;
			this.max = max;
			set(value);
			return this;
		}

		void onChange(IntConsumer l)
		{
			listener = l;
		}

		int getValue()
		{
			return value;
		}

		void setQuietly(int v)
		{
			value = Math.max(min, Math.min(max, v));
			display.repaint();
		}

		void set(int v)
		{
			int nv = Math.max(min, Math.min(max, v));
			if (nv != value)
			{
				value = nv;
				display.repaint();
				if (listener != null)
				{
					listener.accept(nv);
				}
			}
		}

		private void startEdit()
		{
			editing = true;
			editor.setText(String.valueOf(value));
			((java.awt.CardLayout) editor.getParent().getLayout()).show(editor.getParent(), "edit");
			editor.requestFocusInWindow();
			editor.selectAll();
		}

		private void commitEdit()
		{
			if (!editing)
			{
				return;
			}
			try
			{
				set(Integer.parseInt(editor.getText().replace(",", "").trim()));
			}
			catch (NumberFormatException ignored)
			{
			}
			stopEdit();
		}

		private void stopEdit()
		{
			editing = false;
			((java.awt.CardLayout) editor.getParent().getLayout()).show(editor.getParent(), "show");
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(96, 26);
		}

		@Override
		public Dimension getMaximumSize()
		{
			return getPreferredSize();
		}

		@Override
		public Dimension getMinimumSize()
		{
			// The number is fitted when painted, so it can shrink below its preferred width
			return new Dimension(64, 26);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = Theme.smooth(g);
			RoundRectangle2D r = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
			g2.setColor(Theme.INSET);
			g2.fill(r);
			g2.setColor(editing ? Theme.alpha(Theme.ACCENT, 120) : Theme.CARD_BORDER);
			g2.draw(r);
			g2.dispose();
		}
	}

	/**
	 * A thin rounded progress bar.
	 */
	static class Bar extends JComponent
	{
		private double fraction;
		private Color color = Theme.OK;

		Bar()
		{
			setOpaque(false);
		}

		Bar set(double fraction, Color color)
		{
			this.fraction = Math.max(0, Math.min(1, fraction));
			this.color = color;
			repaint();
			return this;
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(50, 4);
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, 4);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = Theme.smooth(g);
			int h = getHeight();
			g2.setColor(Theme.INSET);
			g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), h, h, h));
			int w = (int) Math.round(getWidth() * fraction);
			if (w > 0)
			{
				g2.setColor(color);
				g2.fill(new RoundRectangle2D.Double(0, 0, Math.max(w, h), h, h, h));
			}
			g2.dispose();
		}
	}

	/**
	 * An item sprite on an inventory-slot style tile.
	 */
	static class Slot extends JComponent
	{
		static final int SIZE = 34;
		private ImageIcon icon;
		private boolean dim;

		Slot()
		{
			setOpaque(false);
		}

		Slot icon(ImageIcon i)
		{
			icon = i;
			repaint();
			return this;
		}

		Slot dim(boolean d)
		{
			dim = d;
			repaint();
			return this;
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(SIZE, SIZE);
		}

		@Override
		public Dimension getMinimumSize()
		{
			return getPreferredSize();
		}

		@Override
		public Dimension getMaximumSize()
		{
			return getPreferredSize();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			paintSlot(this, g, 0, 0, icon, dim);
		}

		static void paintSlot(Component c, Graphics g, int x, int y, ImageIcon icon, boolean dim)
		{
			Graphics2D g2 = Theme.smooth(g);
			RoundRectangle2D r = new RoundRectangle2D.Double(x + 0.5, y + 0.5, SIZE - 1, SIZE - 1, 6, 6);
			g2.setColor(Theme.SLOT);
			g2.fill(r);
			g2.setColor(Theme.SLOT_EDGE);
			g2.draw(r);
			if (icon != null && icon.getIconWidth() > 0)
			{
				if (dim)
				{
					g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.45f));
				}
				int ix = x + (SIZE - icon.getIconWidth()) / 2;
				int iy = y + (SIZE - icon.getIconHeight()) / 2;
				icon.paintIcon(c, g2, ix, iy);
			}
			g2.dispose();
		}
	}
}
