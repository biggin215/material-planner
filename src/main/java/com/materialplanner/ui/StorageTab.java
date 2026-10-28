package com.materialplanner.ui;

import com.materialplanner.items.ItemStore;
import com.materialplanner.items.Location;
import com.materialplanner.items.StoredItem;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Searchable view of saved storage: the bank, seed vault, cargo holds, tool kits and so on,
 * one at a time or all together.
 */
class StorageTab extends JPanel
{
	private static final String ALL = "All storage";
	private static final int ROW_HEIGHT = 42;

	static final class Row
	{
		final int id;
		final String name;
		final long quantity;
		/** "Bank 103 · Storm Clam 40" in the combined view; null for a single storage. */
		final String breakdown;

		Row(int id, String name, long quantity, String breakdown)
		{
			this.id = id;
			this.name = name;
			this.quantity = quantity;
			this.breakdown = breakdown;
		}
	}

	private final ItemStore store;
	private final IconCache icons;

	private final Kit.Dropdown<Object> locationBox = new Kit.Dropdown<>();
	private final Kit.TextBlock header = new Kit.TextBlock(RecipeTab.WIDTH_HINT);
	private final Kit.SearchField search = new Kit.SearchField();
	private final JList<Row> list = new ViewportList<>(new DefaultListModel<>());
	private String shownSignature;

	StorageTab(ItemStore store, IconCache icons)
	{
		this.store = store;
		this.icons = icons;

		setLayout(new BorderLayout(0, Theme.GAP));
		setOpaque(false);

		JPanel top = RecipeTab.column();
		top.setBorder(new EmptyBorder(0, 0, 0, Kit.ThinScrollBarUI.WIDTH));
		locationBox.onChange(o -> refresh());
		top.add(RecipeTab.stretch(locationBox));
		top.add(Box.createVerticalStrut(6));
		search.placeholder("Search your storage");
		search.field.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				refresh();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				refresh();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				refresh();
			}
		});
		top.add(RecipeTab.stretch(search));
		top.add(Box.createVerticalStrut(6));
		top.add(RecipeTab.stretch(header));
		add(top, BorderLayout.NORTH);

		list.setOpaque(false);
		list.setBackground(Theme.BG);
		list.setCellRenderer(new ItemRenderer());
		list.setFixedCellHeight(ROW_HEIGHT);
		add(Kit.scroll(list), BorderLayout.CENTER);

		refreshLocations();
		refresh();
	}

	private void refreshLocations()
	{
		List<Object> items = new ArrayList<>();
		items.add(ALL);
		for (Location loc : Location.values())
		{
			if (loc.isPersistent())
			{
				items.add(loc);
			}
		}
		locationBox.setItems(items, o -> o instanceof Location
			? store.label((Location) o) + (store.hasSnapshot((Location) o) ? "" : "  (not recorded)")
			: o.toString());
	}

	void refresh()
	{
		Object sel = locationBox.getSelected();
		String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);

		// Inventory changes also trigger refreshes; skip the rebuild unless something shown here changed
		StringBuilder sig = new StringBuilder(String.valueOf(sel)).append('|').append(q);
		for (Location loc : Location.values())
		{
			if (loc.isPersistent())
			{
				sig.append('|').append(store.getTime(loc)).append(store.label(loc));
			}
		}
		if (sig.toString().equals(shownSignature))
		{
			return;
		}
		shownSignature = sig.toString();
		refreshLocations();

		List<Row> rows = new ArrayList<>();
		if (sel instanceof Location)
		{
			for (StoredItem it : store.getItems((Location) sel))
			{
				rows.add(new Row(it.getId(), it.getName(), it.getQuantity(), null));
			}
		}
		else
		{
			for (ItemStore.Aggregate a : store.allStorage())
			{
				long total = 0;
				StringBuilder where = new StringBuilder();
				for (Map.Entry<Location, Long> e : a.counts.asMap().entrySet())
				{
					total += e.getValue();
					if (where.length() > 0)
					{
						where.append(" · ");
					}
					where.append(store.shortLabel(e.getKey()));
					// With a single storage the right-hand number already says how many
					if (a.counts.asMap().size() > 1)
					{
						where.append(' ').append(Theme.shortQty(e.getValue()));
					}
				}
				rows.add(new Row(a.id, a.name, total, where.toString()));
			}
		}

		DefaultListModel<Row> model = new DefaultListModel<>();
		for (Row r : rows)
		{
			if (q.isEmpty() || r.name.toLowerCase(Locale.ROOT).contains(q))
			{
				model.addElement(r);
			}
		}
		list.setModel(model);

		String status;
		if (sel instanceof Location)
		{
			Location loc = (Location) sel;
			status = !store.hasSnapshot(loc)
				? (loc.name().startsWith("CARGO") ? "Not recorded yet. Open a boat's cargo hold." : "Not recorded yet. Open it once in game.")
				: Theme.n(rows.size()) + " items · updated " + MaterialPlannerPanel.ago(store.getTime(loc));
		}
		else
		{
			int recorded = store.recordedStorage().size();
			status = recorded == 0 ? "Open your bank once to take a snapshot."
				: !q.isEmpty() && model.isEmpty() ? "Not in any recorded storage."
				: Theme.n(rows.size()) + " items in " + recorded + (recorded == 1 ? " storage" : " storages");
		}
		header.lines(new Kit.Line(status, Theme.small(), Theme.FAINT, 2));
	}

	/**
	 * Painted row: slot, name, where it's kept, quantity.
	 */
	private final class ItemRenderer extends JComponent implements ListCellRenderer<Row>
	{
		private Row row;
		private int index;
		private ImageIcon icon;

		@Override
		public Component getListCellRendererComponent(JList<? extends Row> l, Row value, int index,
			boolean isSelected, boolean cellHasFocus)
		{
			this.row = value;
			this.index = index;
			this.icon = icons.get(value.id, list);
			return this;
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(100, ROW_HEIGHT);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = Theme.smooth(g);
			int w = getWidth();
			if (index % 2 == 0)
			{
				g2.setColor(Theme.CARD);
				g2.fillRoundRect(0, 1, w, ROW_HEIGHT - 2, 8, 8);
			}
			int slotY = (ROW_HEIGHT - Kit.Slot.SIZE) / 2;
			Kit.Slot.paintSlot(this, g2, 4, slotY, icon, false);

			g2.setFont(Theme.small());
			FontMetrics fm = g2.getFontMetrics();
			int tx = 4 + Kit.Slot.SIZE + 8;
			String qty = Theme.shortQty(row.quantity);
			int tw = w - tx - fm.stringWidth(qty) - 14;

			g2.setColor(Theme.qtyColor(row.quantity));
			int qx = w - 6 - fm.stringWidth(qty);
			int midBase = (ROW_HEIGHT - fm.getHeight()) / 2 + fm.getAscent();
			g2.drawString(qty, qx, midBase);

			boolean twoLines = row.breakdown != null && !row.breakdown.isEmpty();
			int y1 = twoLines ? (ROW_HEIGHT - fm.getHeight() * 2) / 2 + fm.getAscent() : midBase;
			g2.setColor(Theme.TEXT);
			g2.drawString(Theme.fit(row.name, fm, tw), tx, y1);
			if (twoLines)
			{
				g2.setColor(Theme.MUTED);
				g2.drawString(Theme.fit(row.breakdown, fm, tw), tx, y1 + fm.getHeight());
			}
			g2.dispose();
		}
	}
}
