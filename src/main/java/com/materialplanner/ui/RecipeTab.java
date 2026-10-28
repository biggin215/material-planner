package com.materialplanner.ui;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.materialplanner.CostMode;
import com.materialplanner.MaterialPlannerConfig;
import com.materialplanner.items.Counts;
import com.materialplanner.items.ItemStore;
import com.materialplanner.items.Location;
import com.materialplanner.recipes.Planner;
import com.materialplanner.recipes.Recipe;
import com.materialplanner.recipes.RecipeRepository;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Type;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;

class RecipeTab extends JPanel
{
	private static final String ALL_SKILLS = "All skills";
	private static final String KEY_SELECTION = "selection";
	private static final String KEY_METHODS = "methods";
	// Enough to browse most of a skill from the dropdown; rows are fixed-height so this stays cheap
	private static final int MAX_SUGGESTIONS = 300;
	private static final int SEARCH_DELAY_MS = 150;
	private static final int TEXT_WIDTH = PluginPanel.PANEL_WIDTH - 60;
	// Name column in a Making row; leaves room for the quantity box and the remove control
	private static final int ROW_TEXT_WIDTH = PluginPanel.PANEL_WIDTH - 115;
	private static final int INDENT = 10;
	private static final Type SELECTION_TYPE = new TypeToken<List<SavedSelection>>()
	{
	}.getType();
	private static final Type METHODS_TYPE = new TypeToken<Map<String, String>>()
	{
	}.getType();

	private static final Color GREEN = Palette.OK;
	private static final Color YELLOW = Palette.WARN;
	private static final Color RED = Palette.BAD;
	private static final Color BLUE = Palette.MAKE;

	private static final class SavedSelection
	{
		String key;
		int quantity;

		SavedSelection(String key, int quantity)
		{
			this.key = key;
			this.quantity = quantity;
		}
	}

	private static final class Selection
	{
		final Recipe recipe;
		int quantity;

		Selection(Recipe recipe, int quantity)
		{
			this.recipe = recipe;
			this.quantity = quantity;
		}
	}

	private final ItemStore store;
	private final RecipeRepository recipes;
	private final ConfigManager configManager;
	private final Gson gson;
	private final MaterialPlannerConfig config;
	private final IconCache icons;
	private final PanelHost host;

	private final JLabel statusLabel = new JLabel();
	private final JComboBox<String> skillBox = new JComboBox<>();
	private final IconTextField searchField = new IconTextField();
	private DefaultListModel<Recipe> suggestionModel = new DefaultListModel<>();
	private final JList<Recipe> suggestionList = new ViewportList<>(suggestionModel);
	private final Timer searchTimer = new Timer(SEARCH_DELAY_MS, e -> updateSuggestions());
	private final JScrollPane suggestionScroll;
	private final JPanel selectedPanel = new JPanel();
	private final JPanel materialsPanel = new JPanel();

	private final List<Selection> selections = new ArrayList<>();
	private final Map<String, String> methods;
	private final Set<String> collapsed = new HashSet<>();
	private List<SavedSelection> pendingRestore;
	private boolean updatingSkills;

	RecipeTab(ItemStore store, RecipeRepository recipes, ConfigManager configManager, Gson gson,
		MaterialPlannerConfig config, IconCache icons, PanelHost host, Runnable refreshRecipes)
	{
		this.store = store;
		this.recipes = recipes;
		this.configManager = configManager;
		this.gson = gson;
		this.config = config;
		this.icons = icons;
		this.host = host;
		this.methods = loadMethods();

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		// --- Controls ---------------------------------------------------------------------------
		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		controls.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel statusRow = new JPanel(new BorderLayout());
		statusRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(Palette.FAINT);
		JButton refresh = smallButton("↻", "Re-download recipes from the OSRS Wiki");
		refresh.addActionListener(e -> refreshRecipes.run());
		statusRow.add(statusLabel, BorderLayout.CENTER);
		statusRow.add(refresh, BorderLayout.EAST);
		controls.add(fullWidth(statusRow));
		controls.add(Box.createVerticalStrut(4));

		skillBox.setFocusable(false);
		skillBox.addActionListener(e ->
		{
			if (!updatingSkills)
			{
				updateSuggestions();
			}
		});
		controls.add(fullWidth(skillBox));
		controls.add(Box.createVerticalStrut(4));

		searchField.setIcon(IconTextField.Icon.SEARCH);
		searchField.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 16, 30));
		searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				searchTimer.restart();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				searchTimer.restart();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				searchTimer.restart();
			}
		});
		searchField.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_DOWN && !suggestionModel.isEmpty())
				{
					suggestionList.requestFocusInWindow();
					suggestionList.setSelectedIndex(0);
				}
				else if (e.getKeyCode() == KeyEvent.VK_ENTER && !suggestionModel.isEmpty())
				{
					addSelection(suggestionModel.get(0));
				}
			}
		});
		controls.add(fullWidth(searchField));

		// Autocomplete dropdown
		searchTimer.setRepeats(false);
		suggestionList.setFixedCellHeight(34);
		suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		suggestionList.setBackground(Palette.ROW);
		suggestionList.setCellRenderer(new SuggestionRenderer());
		suggestionList.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				int idx = suggestionList.locationToIndex(e.getPoint());
				if (idx >= 0)
				{
					addSelection(suggestionModel.get(idx));
				}
			}
		});
		suggestionList.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ENTER && suggestionList.getSelectedValue() != null)
				{
					addSelection(suggestionList.getSelectedValue());
				}
			}
		});
		suggestionScroll = new JScrollPane(suggestionList);
		suggestionScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		suggestionScroll.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 16, 200));
		suggestionScroll.setBorder(BorderFactory.createMatteBorder(0, 1, 1, 1, ColorScheme.BORDER_COLOR));
		suggestionScroll.setVisible(false);
		controls.add(fullWidth(suggestionScroll));
		controls.setBorder(new EmptyBorder(0, 0, 8, 0));

		add(controls, BorderLayout.NORTH);

		// --- Body: selected recipes + materials --------------------------------------------------
		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);
		body.setBorder(new EmptyBorder(4, 0, 0, 0));

		JPanel makingHeader = new JPanel(new BorderLayout());
		makingHeader.setBackground(ColorScheme.DARK_GRAY_COLOR);
		makingHeader.add(sectionHeader("MAKING"), BorderLayout.WEST);
		JButton clearAll = smallButton("Clear all", "Remove every recipe from the list");
		clearAll.setFont(FontManager.getRunescapeSmallFont());
		clearAll.addActionListener(e ->
		{
			if (selections.isEmpty())
			{
				return;
			}
			int choice = JOptionPane.showConfirmDialog(this,
				"Remove all " + selections.size() + " recipes from the list?", "Clear all",
				JOptionPane.OK_CANCEL_OPTION);
			if (choice == JOptionPane.OK_OPTION)
			{
				selections.clear();
				afterSelectionChange();
			}
		});
		makingHeader.add(clearAll, BorderLayout.EAST);
		body.add(fullWidth(makingHeader));

		selectedPanel.setLayout(new BoxLayout(selectedPanel, BoxLayout.Y_AXIS));
		selectedPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		body.add(leftAlign(selectedPanel));
		body.add(Box.createVerticalStrut(10));

		materialsPanel.setLayout(new BoxLayout(materialsPanel, BoxLayout.Y_AXIS));
		materialsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		body.add(leftAlign(materialsPanel));

		JPanel bodyWrap = new WidthTrackingPanel(new BorderLayout());
		bodyWrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bodyWrap.add(body, BorderLayout.NORTH);
		JScrollPane scroll = new JScrollPane(bodyWrap);
		scroll.setBorder(null);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);

		pendingRestore = loadSavedSelection();
		onRecipesLoaded();
	}

	// --- recipes & suggestions -------------------------------------------------------------------

	void onRecipesLoaded()
	{
		statusLabel.setText(recipes.getStatus());

		Object current = skillBox.getSelectedItem();
		updatingSkills = true;
		skillBox.removeAllItems();
		skillBox.addItem(ALL_SKILLS);
		for (String s : recipes.getSkills())
		{
			skillBox.addItem(s);
		}
		skillBox.setSelectedItem(current != null ? current : ALL_SKILLS);
		updatingSkills = false;

		restoreSelections();
		updateSuggestions();
		rebuildSelected();
	}

	/**
	 * Levels or settings changed: redraw everything that depends on them.
	 */
	void refreshAll()
	{
		suggestionList.repaint();
		rebuildSelected();
	}

	private void restoreSelections()
	{
		if (pendingRestore == null || recipes.getRecipes().isEmpty())
		{
			return;
		}
		Map<String, Recipe> byKey = new HashMap<>();
		for (Recipe r : recipes.getRecipes())
		{
			byKey.put(r.key(), r);
		}
		List<SavedSelection> stillMissing = new ArrayList<>();
		for (SavedSelection s : pendingRestore)
		{
			Recipe r = byKey.get(s.key);
			if (r == null)
			{
				stillMissing.add(s);
			}
			else if (selections.stream().noneMatch(x -> x.recipe.equals(r)))
			{
				selections.add(new Selection(r, Math.max(1, s.quantity)));
			}
		}
		// Keep waiting for recipes that may arrive with the wiki download
		pendingRestore = stillMissing.isEmpty() ? null : stillMissing;
	}

	private void updateSuggestions()
	{
		String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
		Object skillSel = skillBox.getSelectedItem();
		String skill = skillSel == null || ALL_SKILLS.equals(skillSel) ? null : skillSel.toString();
		boolean hideUnmet = config.levelCheck() && config.hideUnmet();

		DefaultListModel<Recipe> model = new DefaultListModel<>();
		if (query.isEmpty() && skill == null)
		{
			suggestionModel = model;
			suggestionList.setModel(model);
			suggestionScroll.setVisible(false);
			revalidate();
			return;
		}

		// Names starting with the query rank above names merely containing it
		List<Recipe> starts = new ArrayList<>();
		List<Recipe> contains = new ArrayList<>();
		for (Recipe r : recipes.getRecipes())
		{
			if (skill != null && !r.usesSkill(skill))
			{
				continue;
			}
			if (hideUnmet && !unmetLevels(r).isEmpty())
			{
				continue;
			}
			String n = r.name.toLowerCase(Locale.ROOT);
			if (query.isEmpty() || n.startsWith(query))
			{
				starts.add(r);
			}
			else if (n.contains(query))
			{
				contains.add(r);
			}
			if (starts.size() >= MAX_SUGGESTIONS)
			{
				break;
			}
		}
		for (Recipe r : starts)
		{
			if (model.size() < MAX_SUGGESTIONS)
			{
				model.addElement(r);
			}
		}
		for (Recipe r : contains)
		{
			if (model.size() < MAX_SUGGESTIONS)
			{
				model.addElement(r);
			}
		}

		suggestionModel = model;
		suggestionList.setModel(model);
		suggestionScroll.setVisible(!model.isEmpty());
		revalidate();
		repaint();
	}

	private void addSelection(Recipe recipe)
	{
		for (Selection s : selections)
		{
			if (s.recipe.equals(recipe))
			{
				s.quantity++;
				afterSelectionChange();
				return;
			}
		}
		selections.add(new Selection(recipe, 1));
		searchField.setText("");
		afterSelectionChange();
	}

	private void afterSelectionChange()
	{
		saveSelection();
		rebuildSelected();
	}

	// --- levels ----------------------------------------------------------------------------------

	/**
	 * Requirements the player doesn't meet, e.g. ["Crafting 61 (you 55)"]. Empty when met or levels unknown.
	 */
	private List<String> unmetLevels(Recipe r)
	{
		List<String> out = new ArrayList<>();
		if (!config.levelCheck())
		{
			return out;
		}
		for (Recipe.SkillReq s : r.skills)
		{
			int have = host.realLevel(s.name);
			if (have >= 0 && s.level > have)
			{
				out.add(s.name + " " + s.level + " (you " + have + ")");
			}
		}
		return out;
	}

	// --- Making list -----------------------------------------------------------------------------

	private void rebuildSelected()
	{
		selectedPanel.removeAll();
		if (selections.isEmpty())
		{
			selectedPanel.add(fullWidth(hint("Search above or pick a skill, then click a recipe to add it.")));
		}
		for (Selection s : selections)
		{
			selectedPanel.add(fullWidth(selectionRow(s)));
			selectedPanel.add(Box.createVerticalStrut(4));
		}
		selectedPanel.revalidate();
		selectedPanel.repaint();
		refreshMaterials();
	}

	private JComponent selectionRow(Selection s)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(Palette.ROW);
		row.setBorder(new EmptyBorder(6, 8, 6, 4));

		List<String> unmet = unmetLevels(s.recipe);
		String sub = unmet.isEmpty() ? escape(s.recipe.subtitle())
			: "<font color='" + hex(RED) + "'>Needs " + escape(String.join(", ", unmet)) + "</font>";
		JLabel name = new JLabel("<html><body style='width:" + ROW_TEXT_WIDTH + "px'>" + escape(s.recipe.toString())
			+ "<br><font color='" + hex(Palette.MUTED) + "'>" + sub + "</font></body></html>");
		name.setForeground(Palette.TEXT);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setToolTipText(s.recipe.tools == null ? null : "Tools: " + s.recipe.tools);
		row.add(name, BorderLayout.CENTER);

		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
		right.setOpaque(false);
		JSpinner qty = new JSpinner(new SpinnerNumberModel(s.quantity, 1, 1_000_000, 1));
		qty.setPreferredSize(new Dimension(50, 22));
		qty.setToolTipText("How many times you will make this");
		qty.addChangeListener(e ->
		{
			s.quantity = (Integer) qty.getValue();
			saveSelection();
			refreshMaterials();
		});
		JLabel remove = new JLabel(IconCache.removeIcon(RED));
		remove.setToolTipText("Remove from list");
		remove.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		remove.setBorder(new EmptyBorder(4, 4, 4, 2));
		remove.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				selections.remove(s);
				afterSelectionChange();
			}
		});
		right.add(qty);
		right.add(remove);
		row.add(right, BorderLayout.EAST);
		return row;
	}

	// --- Materials tree --------------------------------------------------------------------------

	void refreshMaterials()
	{
		materialsPanel.removeAll();

		Map<Recipe, Integer> sel = new LinkedHashMap<>();
		for (Selection s : selections)
		{
			sel.merge(s.recipe, s.quantity, Integer::sum);
		}
		if (sel.isEmpty())
		{
			host.onPlanChanged(new HashSet<>());
			materialsPanel.revalidate();
			materialsPanel.repaint();
			return;
		}

		Planner.Options opt = new Planner.Options();
		opt.expand = config.expandRecipes();
		opt.includeWorn = config.countWorn();
		opt.includeOther = config.countOtherStorage();
		opt.methods = methods;
		Map<String, Counts> stock = store.countsByName();
		Planner.Plan plan = Planner.plan(sel, recipes.getByOutput(), stock, opt);
		host.onPlanChanged(new HashSet<>(plan.fromBank.keySet()));

		materialsPanel.add(fullWidth(sectionHeader("MATERIALS")));
		materialsPanel.add(fullWidth(summaryLine(plan)));
		for (JComponent c : costLines(plan))
		{
			materialsPanel.add(fullWidth(c));
		}
		materialsPanel.add(Box.createVerticalStrut(8));

		for (Planner.Node root : plan.roots)
		{
			addNodeRows(root, "", stock, opt);
		}

		if (!store.hasBankSnapshot())
		{
			materialsPanel.add(fullWidth(hint("No bank snapshot yet: open your bank once so bank counts are included.")));
		}
		else
		{
			materialsPanel.add(fullWidth(hint("Bank as of " + MaterialPlannerPanel.ago(store.getBankTime())
				+ ". Right-click an item to choose how it's made.")));
		}
		materialsPanel.revalidate();
		materialsPanel.repaint();
	}

	private JComponent summaryLine(Planner.Plan plan)
	{
		List<String> parts = new ArrayList<>();
		parts.add("<font color='" + hex(GREEN) + "'>" + plan.ready + " ready</font>");
		if (plan.toMake > 0)
		{
			parts.add("<font color='" + hex(BLUE) + "'>" + plan.toMake + " to make</font>");
		}
		if (plan.shortCount > 0)
		{
			parts.add("<font color='" + hex(RED) + "'>" + plan.shortCount + " short</font>");
		}
		JLabel l = new JLabel("<html>" + String.join(" · ", parts) + "</html>");
		l.setFont(FontManager.getRunescapeSmallFont());
		return l;
	}

	private List<JComponent> costLines(Planner.Plan plan)
	{
		List<JComponent> out = new ArrayList<>();
		CostMode mode = host.effectiveCostMode();
		if (mode == CostMode.OFF)
		{
			return out;
		}
		NumberFormat nf = NumberFormat.getIntegerInstance();

		if (plan.coinsNeeded > 0)
		{
			Color c = plan.coinsAvailable >= plan.coinsNeeded ? GREEN : RED;
			out.add(smallLine("Coins needed " + nf.format(plan.coinsNeeded) + " · have " + nf.format(plan.coinsAvailable), c));
		}

		if (mode == CostMode.GE_PRICES && !plan.shortages.isEmpty())
		{
			long total = 0;
			int unpriced = 0;
			for (Map.Entry<String, Long> e : plan.shortages.entrySet())
			{
				if (e.getKey().equalsIgnoreCase("coins"))
				{
					continue;
				}
				long p = icons.price(e.getKey());
				if (p > 0)
				{
					total += p * e.getValue();
				}
				else
				{
					unpriced++;
				}
			}
			String text = "Buy what's short ≈ " + shortGp(total) + " gp"
				+ (unpriced > 0 ? " (+" + unpriced + " untradeable)" : "");
			out.add(smallLine(text, Palette.MUTED));
		}
		return out;
	}

	private void addNodeRows(Planner.Node n, String parentPath, Map<String, Counts> stock, Planner.Options opt)
	{
		String path = n.path(parentPath);
		materialsPanel.add(fullWidth(nodeRow(n, path, stock, opt)));
		materialsPanel.add(Box.createVerticalStrut(2));
		if (!n.children.isEmpty() && !collapsed.contains(path))
		{
			for (Planner.Node c : n.children)
			{
				addNodeRows(c, path, stock, opt);
			}
		}
	}

	private JComponent nodeRow(Planner.Node n, String path, Map<String, Counts> stock, Planner.Options opt)
	{
		NumberFormat nf = NumberFormat.getIntegerInstance();
		Counts c = stock.getOrDefault(n.key, new Counts());
		long have = c.total(opt.includeWorn, opt.includeOther);

		Color status;
		String verdict;
		switch (n.status())
		{
			case ON_YOU:
				status = GREEN;
				verdict = "On you";
				break;
			case IN_STORAGE:
				status = YELLOW;
				verdict = withdrawText(n, nf);
				break;
			case MAKE:
				status = n.subtreeShort() ? RED : BLUE;
				verdict = makeText(n, nf);
				break;
			default:
				status = RED;
				verdict = "Need " + nf.format(n.shortBy) + " more" + priceSuffix(n.name, n.shortBy);
				break;
		}

		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(Palette.ROW);
		int indent = n.depth * INDENT;
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, indent, 0, 0, Palette.PANEL),
			BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 2, 0, 0, status),
				new EmptyBorder(4, 4, 4, 6))));

		// Expand/collapse arrow for crafted items, then the item sprite
		JPanel west = new JPanel(new BorderLayout());
		west.setOpaque(false);
		JLabel arrow = new JLabel(n.children.isEmpty() ? "" : (collapsed.contains(path) ? "▸" : "▾"));
		arrow.setForeground(Palette.MUTED);
		arrow.setPreferredSize(new Dimension(n.children.isEmpty() ? 0 : 10, 10));
		if (!n.children.isEmpty())
		{
			arrow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			arrow.setToolTipText("Show or hide ingredients");
			arrow.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					if (!collapsed.remove(path))
					{
						collapsed.add(path);
					}
					refreshMaterials();
				}
			});
		}
		west.add(arrow, BorderLayout.WEST);

		int id = store.idForName(n.name);
		if (id <= 0)
		{
			id = icons.idForName(n.name);
		}
		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(34, 30));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		if (id > 0)
		{
			icon.setIcon(icons.get(id, icon));
		}
		west.add(icon, BorderLayout.CENTER);
		row.add(west, BorderLayout.WEST);

		String levelWarn = "";
		if (n.via != null)
		{
			List<String> unmet = unmetLevels(n.via);
			if (!unmet.isEmpty())
			{
				levelWarn = "<br><font color='" + hex(RED) + "'>Needs " + escape(String.join(", ", unmet)) + "</font>";
			}
		}

		// Two lines: the item, then "have/need · what to do". Where it's stored is in the tooltip.
		int width = Math.max(60, TEXT_WIDTH - 45 - indent);
		Color countColor = have >= n.need ? Palette.MUTED : status;
		JLabel text = new JLabel("<html><body style='width:" + width + "px'>"
			+ "<font color='" + hex(Palette.TEXT) + "'>" + escape(n.name) + "</font>"
			+ "<br><font color='" + hex(countColor) + "'>" + nf.format(have) + "/" + nf.format(n.need) + "</font>"
			+ "<font color='" + hex(Palette.FAINT) + "'> · </font>"
			+ "<font color='" + hex(status) + "'>" + escape(verdict) + "</font>" + levelWarn
			+ "</body></html>");
		text.setFont(FontManager.getRunescapeSmallFont());
		row.add(text, BorderLayout.CENTER);

		String tip = locationTooltip(c);
		row.setToolTipText(tip);
		text.setToolTipText(tip);
		attachMethodMenu(row, n);
		attachMethodMenu(text, n);
		return row;
	}

	private static void appendCount(StringBuilder sb, String label, long qty, NumberFormat nf)
	{
		if (qty <= 0)
		{
			return;
		}
		if (sb.length() > 0)
		{
			sb.append(" · ");
		}
		sb.append(label).append(' ').append(nf.format(qty));
	}

	private static String withdrawText(Planner.Node n, NumberFormat nf)
	{
		List<String> parts = new ArrayList<>();
		if (n.fromBank > 0)
		{
			parts.add(nf.format(n.fromBank) + " from bank");
		}
		if (n.fromOther > 0)
		{
			parts.add(nf.format(n.fromOther) + " from other storage");
		}
		return "Withdraw " + String.join(", ", parts);
	}

	private static String makeText(Planner.Node n, NumberFormat nf)
	{
		long out = Math.max(1, n.via.outputQuantity);
		String make = out > 1
			? "Make " + nf.format(n.crafts * out) + " (" + nf.format(n.crafts) + " actions)"
			: "Make " + nf.format(n.crafts);
		if (n.fromStock() + n.fromSurplus > 0)
		{
			make = "Use " + nf.format(n.fromStock() + n.fromSurplus) + ", " + make.substring(0, 1).toLowerCase() + make.substring(1);
		}
		if (n.via.facility != null && !n.via.facility.isEmpty())
		{
			make += " · " + n.via.facility;
		}
		return make;
	}

	private String priceSuffix(String name, long qty)
	{
		if (host.effectiveCostMode() != CostMode.GE_PRICES)
		{
			return "";
		}
		long p = icons.price(name);
		return p > 0 ? " (≈" + shortGp(p * qty) + ")" : "";
	}

	private String locationTooltip(Counts c)
	{
		NumberFormat nf = NumberFormat.getIntegerInstance();
		StringBuilder sb = new StringBuilder("<html>");
		if (c.asMap().isEmpty())
		{
			sb.append("None owned");
		}
		for (Map.Entry<Location, Long> e : c.asMap().entrySet())
		{
			sb.append(escape(store.label(e.getKey()))).append(": ").append(nf.format(e.getValue())).append("<br>");
		}
		return sb.append("</html>").toString();
	}

	/**
	 * Right-click a material to pick which recipe makes it, or to stop crafting it.
	 */
	private void attachMethodMenu(JComponent target, Planner.Node n)
	{
		if (n.alternatives.isEmpty() && n.via == null)
		{
			return;
		}
		target.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				maybeShow(e);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				maybeShow(e);
			}

			private void maybeShow(MouseEvent e)
			{
				if (!e.isPopupTrigger())
				{
					return;
				}
				JPopupMenu menu = new JPopupMenu();
				JMenuItem title = new JMenuItem("Make " + n.name + " with:");
				title.setEnabled(false);
				menu.add(title);

				String current = methods.get(n.key);
				ButtonGroup group = new ButtonGroup();
				for (Recipe alt : n.alternatives)
				{
					String label = alt.materialsText();
					if (alt.usesSkill("Magic"))
					{
						label = "Spell: " + label;
					}
					if (alt.facility != null && !alt.facility.isEmpty())
					{
						label += " (" + alt.facility + ")";
					}
					if (label.length() > 70)
					{
						label = label.substring(0, 67) + "…";
					}
					JRadioButtonMenuItem item = new JRadioButtonMenuItem(label, n.via == alt);
					item.addActionListener(ev -> setMethod(n.key, alt.key()));
					group.add(item);
					menu.add(item);
				}
				menu.addSeparator();
				JRadioButtonMenuItem none = new JRadioButtonMenuItem("Don't craft it (I'll gather or buy it)",
					Planner.NO_MAKE.equals(current));
				none.addActionListener(ev -> setMethod(n.key, Planner.NO_MAKE));
				group.add(none);
				menu.add(none);
				if (current != null)
				{
					JMenuItem reset = new JMenuItem("Reset to default");
					reset.addActionListener(ev -> setMethod(n.key, null));
					menu.add(reset);
				}
				menu.show(e.getComponent(), e.getX(), e.getY());
			}
		});
	}

	private void setMethod(String materialKey, String recipeKey)
	{
		if (recipeKey == null)
		{
			methods.remove(materialKey);
		}
		else
		{
			methods.put(materialKey, recipeKey);
		}
		configManager.setConfiguration(MaterialPlannerConfig.GROUP, KEY_METHODS, gson.toJson(methods, METHODS_TYPE));
		refreshMaterials();
	}

	// --- persistence -----------------------------------------------------------------------------

	private void saveSelection()
	{
		List<SavedSelection> out = new ArrayList<>();
		for (Selection s : selections)
		{
			out.add(new SavedSelection(s.recipe.key(), s.quantity));
		}
		// Recipes still waiting on the wiki download stay saved
		if (pendingRestore != null)
		{
			out.addAll(pendingRestore);
		}
		configManager.setConfiguration(MaterialPlannerConfig.GROUP, KEY_SELECTION, gson.toJson(out, SELECTION_TYPE));
	}

	private List<SavedSelection> loadSavedSelection()
	{
		String json = configManager.getConfiguration(MaterialPlannerConfig.GROUP, KEY_SELECTION);
		if (json == null || json.isEmpty())
		{
			return null;
		}
		try
		{
			return gson.fromJson(json, SELECTION_TYPE);
		}
		catch (JsonSyntaxException e)
		{
			return null;
		}
	}

	private Map<String, String> loadMethods()
	{
		String json = configManager.getConfiguration(MaterialPlannerConfig.GROUP, KEY_METHODS);
		if (json != null && !json.isEmpty())
		{
			try
			{
				Map<String, String> m = gson.fromJson(json, METHODS_TYPE);
				if (m != null)
				{
					return new HashMap<>(m);
				}
			}
			catch (JsonSyntaxException ignored)
			{
			}
		}
		return new HashMap<>();
	}

	// --- small UI helpers ------------------------------------------------------------------------

	/**
	 * For containers whose contents change later: align left but never cap the height.
	 */
	private static <T extends JComponent> T leftAlign(T c)
	{
		c.setAlignmentX(Component.LEFT_ALIGNMENT);
		return c;
	}

	/**
	 * For fixed rows: stretch to full width, keep the row's own height.
	 */
	private static <T extends JComponent> T fullWidth(T c)
	{
		c.setAlignmentX(Component.LEFT_ALIGNMENT);
		c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
		return c;
	}

	private static JLabel sectionHeader(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(Palette.MUTED);
		l.setBorder(new EmptyBorder(2, 0, 6, 0));
		return l;
	}

	private static JLabel smallLine(String text, Color color)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(color);
		return l;
	}

	private static JLabel hint(String text)
	{
		JLabel l = new JLabel("<html><body style='width:" + TEXT_WIDTH + "px'>" + escape(text) + "</body></html>");
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(Palette.FAINT);
		l.setBorder(new EmptyBorder(6, 0, 4, 0));
		return l;
	}

	private static JButton smallButton(String text, String tooltip)
	{
		JButton b = new JButton(text);
		b.setToolTipText(tooltip);
		b.setFocusable(false);
		b.setMargin(new java.awt.Insets(0, 4, 0, 4));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return b;
	}

	static String shortGp(long v)
	{
		if (v >= 10_000_000)
		{
			return String.format("%.1fM", v / 1_000_000.0);
		}
		if (v >= 100_000)
		{
			return (v / 1000) + "k";
		}
		return NumberFormat.getIntegerInstance().format(v);
	}

	static String escape(String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	static String hex(Color c)
	{
		return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}

	/**
	 * Two plain labels instead of HTML: HTML cells are slow to measure and would stall the client
	 * when a whole skill's worth of recipes is listed.
	 */
	private final class SuggestionRenderer extends JPanel implements ListCellRenderer<Recipe>
	{
		private final JLabel title = new JLabel();
		private final JLabel subtitle = new JLabel();

		SuggestionRenderer()
		{
			setLayout(new BorderLayout());
			setBorder(new EmptyBorder(3, 6, 3, 6));
			title.setFont(FontManager.getRunescapeSmallFont());
			subtitle.setFont(FontManager.getRunescapeSmallFont());
			add(title, BorderLayout.NORTH);
			add(subtitle, BorderLayout.SOUTH);
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends Recipe> list, Recipe r, int index,
			boolean isSelected, boolean cellHasFocus)
		{
			boolean unmet = !unmetLevels(r).isEmpty();
			title.setText(r.toString());
			title.setForeground(unmet ? Palette.FAINT : Palette.TEXT);
			subtitle.setText(r.subtitle());
			subtitle.setForeground(unmet ? RED : Palette.MUTED);
			setBackground(isSelected ? Palette.ROW_SELECTED : Palette.ROW);
			return this;
		}
	}

	/**
	 * Scroll content that always matches the visible width, so rows wrap or shrink instead of
	 * extending past the right edge where they'd be clipped.
	 */
	private static final class WidthTrackingPanel extends JPanel implements Scrollable
	{
		WidthTrackingPanel(java.awt.LayoutManager layout)
		{
			super(layout);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation, int direction)
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
}
