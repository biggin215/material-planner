package com.materialplanner.ui;

import com.materialplanner.items.Counts;
import com.materialplanner.items.ItemStore;
import com.materialplanner.items.Location;
import com.materialplanner.items.StoredItem;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;

/**
 * Searchable view of saved storage: the bank, seed vault, cargo holds and so on, one at a time or all together.
 */
class StorageTab extends JPanel
{
	private static final String ALL = "All storage";

	private static final class Row
	{
		final int id;
		final String name;
		final long quantity;
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

	private final JLabel header = new JLabel();
	private final JComboBox<Object> locationBox = new JComboBox<>();
	private final IconTextField searchField = new IconTextField();
	private final JList<Row> list = new JList<>(new DefaultListModel<>());
	private String shownSignature;

	StorageTab(ItemStore store, IconCache icons)
	{
		this.store = store;
		this.icons = icons;

		setLayout(new BorderLayout(0, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);

		locationBox.addItem(ALL);
		for (Location loc : Location.values())
		{
			if (loc.isPersistent())
			{
				locationBox.addItem(loc);
			}
		}
		locationBox.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean focus)
			{
				Object shown = value instanceof Location ? store.label((Location) value) : value;
				return super.getListCellRendererComponent(l, shown, index, sel, focus);
			}
		});
		locationBox.setFocusable(false);
		locationBox.addActionListener(e -> refresh());
		locationBox.setAlignmentX(Component.LEFT_ALIGNMENT);
		locationBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, locationBox.getPreferredSize().height));
		top.add(locationBox);

		header.setFont(FontManager.getRunescapeSmallFont());
		header.setForeground(Palette.FAINT);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setBorder(new EmptyBorder(4, 0, 4, 0));
		top.add(header);

		searchField.setIcon(IconTextField.Icon.SEARCH);
		searchField.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 16, 30));
		searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
		searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchField.getDocument().addDocumentListener(new DocumentListener()
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
		top.add(searchField);
		add(top, BorderLayout.NORTH);

		list.setBackground(ColorScheme.DARK_GRAY_COLOR);
		list.setCellRenderer(new ItemRenderer());
		list.setFixedCellHeight(38);
		JScrollPane scroll = new JScrollPane(list);
		scroll.setBorder(null);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);

		refresh();
	}

	void refresh()
	{
		Object sel = locationBox.getSelectedItem();
		String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);

		// Inventory changes also trigger refreshes; skip the rebuild unless something we show changed
		StringBuilder sig = new StringBuilder(String.valueOf(sel)).append('|').append(q);
		for (Location loc : Location.values())
		{
			if (loc.isPersistent())
			{
				sig.append('|').append(store.getTime(loc));
			}
		}
		if (sig.toString().equals(shownSignature))
		{
			return;
		}
		shownSignature = sig.toString();

		List<Row> rows = new ArrayList<>();
		NumberFormat nf = NumberFormat.getIntegerInstance();
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
					where.append(store.shortLabel(e.getKey())).append(' ').append(nf.format(e.getValue()));
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

		if (sel instanceof Location)
		{
			Location loc = (Location) sel;
			if (!store.hasSnapshot(loc))
			{
				header.setText(loc.name().startsWith("CARGO")
					? "Not recorded yet. Open a boat's cargo hold."
					: "Not recorded yet. Open your " + loc.getLabel().toLowerCase(Locale.ROOT) + " once.");
			}
			else
			{
				header.setText(String.format("%,d items · updated %s", rows.size(), MaterialPlannerPanel.ago(store.getTime(loc))));
			}
		}
		else if (store.recordedStorage().isEmpty())
		{
			header.setText("Open your bank once to take a snapshot.");
		}
		else if (!q.isEmpty() && model.isEmpty())
		{
			header.setText("Not in any recorded storage.");
		}
		else
		{
			header.setText(String.format("%,d items in %d recorded storage", rows.size(), store.recordedStorage().size()));
		}
	}

	private final class ItemRenderer extends JPanel implements ListCellRenderer<Row>
	{
		private final JLabel icon = new JLabel();
		private final JLabel name = new JLabel();
		private final JLabel where = new JLabel();
		private final JLabel qty = new JLabel();

		ItemRenderer()
		{
			setLayout(new BorderLayout(6, 0));
			setBorder(new EmptyBorder(2, 4, 2, 8));
			icon.setPreferredSize(new Dimension(36, 32));
			icon.setHorizontalAlignment(SwingConstants.CENTER);
			name.setFont(FontManager.getRunescapeSmallFont());
			name.setForeground(Palette.TEXT);
			where.setFont(FontManager.getRunescapeSmallFont());
			where.setForeground(Palette.MUTED);
			qty.setFont(FontManager.getRunescapeSmallFont());
			qty.setForeground(Palette.WARN);

			JPanel text = new JPanel(new BorderLayout());
			text.setOpaque(false);
			text.add(name, BorderLayout.NORTH);
			text.add(where, BorderLayout.SOUTH);

			add(icon, BorderLayout.WEST);
			add(text, BorderLayout.CENTER);
			add(qty, BorderLayout.EAST);
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends Row> l, Row row, int index,
			boolean isSelected, boolean cellHasFocus)
		{
			icon.setIcon(icons.get(row.id, list));
			name.setText(row.name);
			where.setText(row.breakdown == null ? "" : row.breakdown);
			qty.setText(NumberFormat.getIntegerInstance().format(row.quantity));
			setBackground(index % 2 == 0 ? Palette.ROW : Palette.ROW_ALT);
			return this;
		}
	}
}
