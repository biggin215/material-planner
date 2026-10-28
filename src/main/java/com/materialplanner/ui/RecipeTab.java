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
import com.materialplanner.recipes.ShopAccess;
import com.materialplanner.recipes.ShopSource;
import com.materialplanner.recipes.Xp;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.config.ConfigManager;

class RecipeTab extends JPanel
{
	private static final String ALL_SKILLS = "All skills";
	private static final String KEY_SELECTION = "selection";
	private static final String KEY_METHODS = "methods";
	private static final int MAX_SUGGESTIONS = 300;
	private static final int SEARCH_DELAY_MS = 150;
	private static final int SUGGESTION_ROW = 36;
	private static final Type SELECTION_TYPE = new TypeToken<List<SavedSelection>>()
	{
	}.getType();
	private static final Type METHODS_TYPE = new TypeToken<Map<String, String>>()
	{
	}.getType();

	/** Width used to size wrapped text before the panel's first layout. */
	static final int WIDTH_HINT = Theme.SIDEBAR_WIDTH - Theme.PANEL_PAD * 2 - Kit.ThinScrollBarUI.WIDTH;

	private static final class SavedSelection
	{
		String key;
		int quantity;
		/** 0 when planning by count; otherwise the target level for the recipe's skill. */
		int targetLevel;

		SavedSelection(String key, int quantity, int targetLevel)
		{
			this.key = key;
			this.quantity = quantity;
			this.targetLevel = targetLevel;
		}
	}

	private static final class Selection
	{
		final Recipe recipe;
		int quantity;
		int targetLevel;

		Selection(Recipe recipe, int quantity, int targetLevel)
		{
			this.recipe = recipe;
			this.quantity = quantity;
			this.targetLevel = targetLevel;
		}
	}

	private final ItemStore store;
	private final RecipeRepository recipes;
	private final ConfigManager configManager;
	private final Gson gson;
	private final MaterialPlannerConfig config;
	private final IconCache icons;
	private final PanelHost host;
	/** The player as the shop access rules see them. */
	private final ShopAccess.Player shopPlayer = new ShopAccess.Player()
	{
		@Override
		public Boolean membersWorld()
		{
			return host.membersWorld();
		}

		@Override
		public List<String> unmetQuests(String requirementText)
		{
			return host.unmetQuests(requirementText);
		}

		@Override
		public int realLevel(String skill)
		{
			return host.realLevel(skill);
		}

		@Override
		public int[] location()
		{
			return host.playerLocation();
		}
	};

	private final Kit.TextBlock statusText = new Kit.TextBlock(WIDTH_HINT - 30);
	private final Kit.Dropdown<String> skillBox = new Kit.Dropdown<>();
	private final Kit.SearchField search = new Kit.SearchField();
	private DefaultListModel<Recipe> suggestionModel = new DefaultListModel<>();
	private final JList<Recipe> suggestionList = new ViewportList<>(suggestionModel);
	private final Timer searchTimer = new Timer(SEARCH_DELAY_MS, e -> updateSuggestions());
	private final JScrollPane suggestionScroll;
	private final JPanel body = new JPanel();

	private final List<Selection> selections = new ArrayList<>();
	private final Map<String, String> methods;
	private final Set<String> collapsed = new HashSet<>();
	private List<SavedSelection> pendingRestore;

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
		setOpaque(false);

		// --- Controls ------------------------------------------------------------------------------
		JPanel controls = column();
		controls.setBorder(new EmptyBorder(0, 0, Theme.GAP, Kit.ThinScrollBarUI.WIDTH));

		JPanel statusRow = new JPanel(new BorderLayout(6, 0));
		statusRow.setOpaque(false);
		statusRow.add(statusText, BorderLayout.CENTER);
		statusRow.add(new Kit.IconButton(Icons.REFRESH, 20, "Re-download recipes from the OSRS Wiki")
			.onClick(refreshRecipes), BorderLayout.EAST);
		controls.add(stretch(statusRow));
		controls.add(Box.createVerticalStrut(6));

		search.field.getDocument().addDocumentListener(new DocumentListener()
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
		search.field.addKeyListener(new KeyAdapter()
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
				else if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
				{
					search.setText("");
				}
			}
		});
		controls.add(stretch(search));
		controls.add(Box.createVerticalStrut(6));

		skillBox.onChange(s -> updateSuggestions());
		controls.add(stretch(skillBox));

		searchTimer.setRepeats(false);
		suggestionList.setFixedCellHeight(SUGGESTION_ROW);
		suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		suggestionList.setOpaque(false);
		suggestionList.setBackground(Theme.CARD);
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
		suggestionScroll = Kit.scroll(suggestionList);
		suggestionScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		JPanel suggestionFrame = new Kit.Card(new BorderLayout()).fill(Theme.CARD);
		suggestionFrame.setBorder(new EmptyBorder(3, 3, 3, 3));
		suggestionFrame.add(suggestionScroll, BorderLayout.CENTER);
		suggestionFrame.setPreferredSize(new Dimension(WIDTH_HINT, SUGGESTION_ROW * 6 + 6));
		suggestionFrame.setMaximumSize(new Dimension(Integer.MAX_VALUE, SUGGESTION_ROW * 6 + 6));
		suggestionFrame.setVisible(false);
		this.suggestionFrame = suggestionFrame;
		controls.add(Box.createVerticalStrut(4));
		controls.add(stretch(suggestionFrame));

		add(controls, BorderLayout.NORTH);

		// --- Body ------------------------------------------------------------------------------------
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setOpaque(false);
		JPanel wrap = new Kit.WidthTracking(new BorderLayout());
		wrap.add(body, BorderLayout.NORTH);
		add(Kit.scroll(wrap), BorderLayout.CENTER);

		pendingRestore = loadSavedSelection();
		onRecipesLoaded();
	}

	private final JPanel suggestionFrame;

	// =================================================================================================
	// Recipes & suggestions

	void onRecipesLoaded()
	{
		int total = recipes.getRecipes().size();
		statusText.lines(new Kit.Line(recipes.getStatus(), Theme.small(), Theme.FAINT));
		search.placeholder(total > 0 ? "Search " + Theme.n(total) + " recipes" : "Search recipes");

		String current = skillBox.getSelected();
		List<String> skills = new ArrayList<>();
		skills.add(ALL_SKILLS);
		skills.addAll(recipes.getSkills());
		skillBox.setItems(skills, s -> s);
		if (current != null)
		{
			skillBox.setSelected(current);
		}

		restoreSelections();
		updateSuggestions();
		rebuild();
	}

	void refreshAll()
	{
		suggestionList.repaint();
		rebuild();
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
				selections.add(new Selection(r, Math.max(1, s.quantity), Math.max(0, s.targetLevel)));
			}
		}
		pendingRestore = stillMissing.isEmpty() ? null : stillMissing;
	}

	private void updateSuggestions()
	{
		String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
		String skillSel = skillBox.getSelected();
		String skill = skillSel == null || ALL_SKILLS.equals(skillSel) ? null : skillSel;
		boolean hideBlocked = config.levelCheck() && config.hideUnmet();

		DefaultListModel<Recipe> model = new DefaultListModel<>();
		if (query.isEmpty() && skill == null)
		{
			setSuggestions(model);
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
			if (hideBlocked && !blockers(r).isEmpty())
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
		setSuggestions(model);
	}

	private void setSuggestions(DefaultListModel<Recipe> model)
	{
		suggestionModel = model;
		suggestionList.setModel(model);
		int rows = Math.min(6, Math.max(1, model.size()));
		Dimension d = new Dimension(WIDTH_HINT, SUGGESTION_ROW * rows + 6);
		suggestionFrame.setPreferredSize(d);
		suggestionFrame.setMaximumSize(new Dimension(Integer.MAX_VALUE, d.height));
		suggestionFrame.setVisible(!model.isEmpty());
		suggestionList.ensureIndexIsVisible(0);
		revalidate();
		repaint();
	}

	private void addSelection(Recipe recipe)
	{
		for (Selection s : selections)
		{
			if (s.recipe.equals(recipe))
			{
				if (s.targetLevel == 0)
				{
					s.quantity++;
				}
				afterSelectionChange();
				return;
			}
		}
		selections.add(new Selection(recipe, 1, 0));
		search.setText("");
		afterSelectionChange();
	}

	private void afterSelectionChange()
	{
		saveSelection();
		rebuild();
	}

	// =================================================================================================
	// Requirements

	/**
	 * Reasons the player can't do a recipe right now, e.g. "Crafting 61 (you 55)", "Members only",
	 * "Quest: Dragon Slayer I". Empty when nothing is known to block it.
	 */
	List<String> blockers(Recipe r)
	{
		List<String> out = new ArrayList<>();
		if (config.levelCheck())
		{
			for (Recipe.SkillReq s : r.skills)
			{
				int have = host.realLevel(s.name);
				if (have >= 0 && s.level > have)
				{
					out.add(s.name + " " + s.level + " (you " + have + ")");
				}
			}
		}
		if (r.members && Boolean.FALSE.equals(host.membersWorld()))
		{
			out.add("Members only");
		}
		for (String q : host.unmetQuests(r.quest))
		{
			out.add("Quest: " + q);
		}
		return out;
	}

	/**
	 * How many times a selection will be made: its count, or the actions needed to reach its target level.
	 */
	private int effectiveQuantity(Selection s)
	{
		if (s.targetLevel <= 0)
		{
			return s.quantity;
		}
		Recipe.SkillReq skill = s.recipe.xpSkill();
		if (skill == null)
		{
			// No XP data (e.g. after a data refresh): fall back to the count
			return s.quantity;
		}
		long xp = host.skillXp(skill.name);
		if (xp < 0)
		{
			return 0;
		}
		return Xp.actionsToLevel(xp, s.targetLevel, skill.experience);
	}

	// =================================================================================================
	// Body

	private void rebuild()
	{
		body.removeAll();

		JPanel makingHeader = new JPanel(new BorderLayout());
		makingHeader.setOpaque(false);
		Kit.SectionHeader making = new Kit.SectionHeader("MAKING");
		if (!selections.isEmpty())
		{
			making.detail(String.valueOf(selections.size()));
		}
		makingHeader.add(making, BorderLayout.CENTER);
		if (!selections.isEmpty())
		{
			makingHeader.add(new Kit.TextButton("Clear all").onClick(this::confirmClear), BorderLayout.EAST);
		}
		body.add(stretch(makingHeader));
		body.add(Box.createVerticalStrut(4));

		if (selections.isEmpty())
		{
			body.add(stretch(emptyState()));
		}
		for (Selection s : selections)
		{
			body.add(stretch(makingCard(s)));
			body.add(Box.createVerticalStrut(6));
		}

		buildPlan();

		body.revalidate();
		body.repaint();
	}

	private void confirmClear()
	{
		int choice = JOptionPane.showConfirmDialog(this,
			"Remove all " + selections.size() + " recipes from the list?", "Clear all",
			JOptionPane.OK_CANCEL_OPTION);
		if (choice == JOptionPane.OK_OPTION)
		{
			selections.clear();
			afterSelectionChange();
		}
	}

	private JComponent emptyState()
	{
		Kit.Card card = new Kit.Card(new BorderLayout()).fill(Theme.CARD).edge(Theme.CARD_BORDER);
		card.setBorder(new EmptyBorder(14, 12, 14, 12));
		Kit.TextBlock t = new Kit.TextBlock(WIDTH_HINT - 24).lines(
			new Kit.Line("Nothing planned yet", Theme.bold(), Theme.TEXT),
			new Kit.Line("Search above, or pick a skill to browse everything you can make in it.", Theme.small(), Theme.MUTED, 3));
		card.add(t, BorderLayout.CENTER);
		return card;
	}

	private JComponent makingCard(Selection s)
	{
		Kit.Card card = new Kit.Card(new BorderLayout(0, 6));
		List<String> blocked = blockers(s.recipe);
		if (!blocked.isEmpty())
		{
			card.accent(Theme.BAD);
		}

		// Top: icon, name + requirements, remove
		JPanel top = new JPanel(new BorderLayout(Theme.GAP, 0));
		top.setOpaque(false);
		Kit.Slot slot = new Kit.Slot();
		slot.icon(iconFor(s.recipe.name, slot));
		top.add(slot, BorderLayout.WEST);

		String sub = blocked.isEmpty() ? s.recipe.subtitle() : "Needs " + String.join(", ", blocked);
		Kit.TextBlock text = new Kit.TextBlock(WIDTH_HINT - 90).lines(
			new Kit.Line(s.recipe.toString(), Theme.small(), Theme.TEXT, 2),
			new Kit.Line(sub, Theme.small(), blocked.isEmpty() ? Theme.MUTED : Theme.BAD, 2));
		if (s.recipe.tools != null)
		{
			text.setToolTipText("Tools: " + s.recipe.tools);
		}
		top.add(text, BorderLayout.CENTER);

		JPanel removeWrap = new JPanel(new BorderLayout());
		removeWrap.setOpaque(false);
		removeWrap.add(new Kit.IconButton(Icons.CROSS, 20, "Remove from list")
			.colors(Theme.FAINT, Theme.BAD)
			.onClick(() ->
			{
				selections.remove(s);
				afterSelectionChange();
			}), BorderLayout.NORTH);
		top.add(removeWrap, BorderLayout.EAST);
		card.add(top, BorderLayout.NORTH);

		// Bottom: Count / Level mode and the number
		Recipe.SkillReq xpSkill = s.recipe.xpSkill();
		JPanel controls = new JPanel(new GridLayout(1, 2, 6, 0));
		controls.setOpaque(false);
		Kit.Segmented mode = new Kit.Segmented(false, "Count", "Level");
		mode.setSelectedQuietly(s.targetLevel > 0 && xpSkill != null ? 1 : 0);
		mode.setToolTipText(xpSkill == null ? "No XP data for this recipe, so it can only be planned by count"
			: "Plan by count, or by a target " + xpSkill.name + " level");
		Kit.Stepper stepper = new Kit.Stepper(1, 1, 1_000_000);
		configureStepper(stepper, s, xpSkill);
		stepper.onChange(v ->
		{
			if (s.targetLevel > 0)
			{
				s.targetLevel = v;
			}
			else
			{
				s.quantity = v;
			}
			saveSelection();
			rebuild();
		});
		mode.onChange(i ->
		{
			if (i == 1 && xpSkill == null)
			{
				mode.setSelectedQuietly(0);
				return;
			}
			if (i == 1)
			{
				int cur = Math.max(1, host.realLevel(xpSkill.name));
				s.targetLevel = Math.min(Xp.MAX_LEVEL, cur + 1);
			}
			else
			{
				s.targetLevel = 0;
			}
			saveSelection();
			rebuild();
		});
		controls.add(mode);
		controls.add(stepper);

		JPanel bottom = new JPanel(new BorderLayout(0, 4));
		bottom.setOpaque(false);
		bottom.add(controls, BorderLayout.NORTH);
		String goal = goalText(s, xpSkill);
		if (goal != null)
		{
			bottom.add(new Kit.TextBlock(WIDTH_HINT - 24).lines(new Kit.Line(goal, Theme.small(), Theme.MAKE, 2)), BorderLayout.CENTER);
		}
		card.add(bottom, BorderLayout.CENTER);
		return card;
	}

	private void configureStepper(Kit.Stepper stepper, Selection s, Recipe.SkillReq xpSkill)
	{
		if (s.targetLevel > 0 && xpSkill != null)
		{
			int cur = Math.max(1, host.realLevel(xpSkill.name));
			stepper.range(Math.min(Xp.MAX_LEVEL, cur + 1), Xp.MAX_LEVEL).prefix("Lv ");
			stepper.setQuietly(s.targetLevel);
		}
		else
		{
			stepper.range(1, 1_000_000).prefix("x");
			stepper.setQuietly(s.quantity);
		}
	}

	private String goalText(Selection s, Recipe.SkillReq xpSkill)
	{
		if (s.targetLevel <= 0 || xpSkill == null)
		{
			return null;
		}
		long xp = host.skillXp(xpSkill.name);
		if (xp < 0)
		{
			return "Log in to plan by level";
		}
		int actions = effectiveQuantity(s);
		if (actions == 0)
		{
			return "Already " + xpSkill.name + " " + Xp.levelFor(xp);
		}
		long needed = Xp.forLevel(s.targetLevel) - xp;
		return Theme.n(actions) + " actions to " + xpSkill.name + " " + s.targetLevel + " (" + Theme.gp(needed) + " xp)";
	}

	// =================================================================================================
	// Plan: overview, tools, materials

	private void buildPlan()
	{
		Map<Recipe, Integer> sel = new LinkedHashMap<>();
		for (Selection s : selections)
		{
			int q = effectiveQuantity(s);
			if (q > 0)
			{
				sel.merge(s.recipe, q, Integer::sum);
			}
		}
		if (sel.isEmpty())
		{
			host.onPlanChanged(new HashSet<>());
			return;
		}

		Planner.Options opt = new Planner.Options();
		opt.expand = config.expandRecipes();
		opt.includeWorn = config.countWorn();
		opt.includeOther = config.countOtherStorage();
		opt.methods = methods;
		opt.stackable = name -> Boolean.TRUE.equals(host.stackable(name));
		Map<String, Counts> stock = store.countsByName();
		Planner.Plan plan = Planner.plan(sel, recipes.getByOutput(), stock, opt);
		host.onPlanChanged(new HashSet<>(plan.fromBank.keySet()));

		body.add(Box.createVerticalStrut(6));
		body.add(stretch(new Kit.SectionHeader("OVERVIEW")));
		body.add(Box.createVerticalStrut(4));
		body.add(stretch(overview(plan)));

		if (!plan.tools.isEmpty())
		{
			body.add(Box.createVerticalStrut(Theme.GAP + 4));
			Kit.SectionHeader th = new Kit.SectionHeader("TOOLS");
			th.detail(String.valueOf(plan.tools.size()));
			body.add(stretch(th));
			body.add(Box.createVerticalStrut(4));
			body.add(stretch(toolsCard(plan, stock, opt)));
		}

		body.add(Box.createVerticalStrut(Theme.GAP + 4));
		Kit.SectionHeader mh = new Kit.SectionHeader("MATERIALS");
		mh.detail(String.valueOf(plan.roots.size()));
		body.add(stretch(mh));
		body.add(Box.createVerticalStrut(4));

		for (Planner.Node root : plan.roots)
		{
			Kit.Card group = new Kit.Card(null);
			group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
			group.setBorder(new EmptyBorder(1, 2, 1, 2));
			addNodeRows(group, root, "", stock, opt, new boolean[0], 0);
			body.add(stretch(group));
			body.add(Box.createVerticalStrut(5));
		}

		String when = store.hasBankSnapshot()
			? "Bank as of " + MaterialPlannerPanel.ago(store.getBankTime()) + ". Right-click a material to choose how it's made."
			: "No bank snapshot yet: open your bank once so bank counts are included.";
		body.add(Box.createVerticalStrut(2));
		body.add(stretch(new Kit.TextBlock(WIDTH_HINT).lines(new Kit.Line(when, Theme.small(), Theme.FAINT, 3))));
	}

	private JComponent overview(Planner.Plan plan)
	{
		Kit.Card card = new Kit.Card(null);
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

		StackBar bar = new StackBar(plan.ready, plan.toMake, plan.shortCount);
		card.add(stretch(bar));
		card.add(Box.createVerticalStrut(6));
		card.add(stretch(new Legend(plan.ready, plan.toMake, plan.shortCount)));

		List<Kit.Line> lines = new ArrayList<>();
		CostMode mode = host.effectiveCostMode();
		if (mode != CostMode.OFF && plan.coinsNeeded > 0)
		{
			boolean enough = plan.coinsAvailable >= plan.coinsNeeded;
			lines.add(new Kit.Line("Coins: " + Theme.gp(plan.coinsNeeded) + " needed, " + Theme.gp(plan.coinsAvailable) + " owned",
				Theme.small(), enough ? Theme.OK : Theme.BAD, 2));
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
			lines.add(new Kit.Line("Buying what's short: ~" + Theme.gp(total) + " gp"
				+ (unpriced > 0 ? " (" + unpriced + " untradeable)" : ""), Theme.small(), Theme.MUTED, 2));
		}
		int inv = Planner.inventories(plan.inventorySlots);
		if (inv > 0)
		{
			lines.add(new Kit.Line("About " + inv + (inv == 1 ? " inventory" : " inventories") + " of materials to carry",
				Theme.small(), Theme.MUTED, 2));
		}
		if (!lines.isEmpty())
		{
			card.add(Box.createVerticalStrut(6));
			card.add(stretch(new Kit.TextBlock(WIDTH_HINT - 24).lines(lines.toArray(new Kit.Line[0]))));
		}
		return card;
	}

	private JComponent toolsCard(Planner.Plan plan, Map<String, Counts> stock, Planner.Options opt)
	{
		Kit.Card card = new Kit.Card(null);
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBorder(new EmptyBorder(4, Theme.GAP, 4, Theme.GAP));
		for (Planner.ToolNeed t : plan.tools)
		{
			long carried = 0;
			long stored = 0;
			boolean knownItem = false;
			for (String alt : t.alternatives)
			{
				Counts c = stock.get(Planner.normalize(alt));
				if (c != null)
				{
					carried += c.carried(opt.includeWorn);
					stored += c.bank() + (opt.includeOther ? c.other() : 0);
					knownItem = true;
				}
				if (icons.idForName(alt) > 0)
				{
					knownItem = true;
				}
			}
			String state;
			Color color;
			if (carried > 0)
			{
				state = "On you";
				color = Theme.OK;
			}
			else if (stored > 0)
			{
				state = "In bank";
				color = Theme.WARN;
			}
			else if (knownItem)
			{
				state = "Missing";
				color = Theme.BAD;
			}
			else
			{
				// Spells and untradeable unlocks we can't check for
				state = "Needed";
				color = Theme.MUTED;
			}
			ToolRow row = new ToolRow(t.label(), state, color);
			row.setToolTipText("Used by " + String.join(", ", t.usedBy));
			card.add(stretch(row));
		}
		return card;
	}

	private void addNodeRows(JComponent parent, Planner.Node n, String parentPath, Map<String, Counts> stock,
		Planner.Options opt, boolean[] rails, int depth)
	{
		String path = n.path(parentPath);
		parent.add(stretch(nodeRow(n, path, stock, opt, rails, depth)));
		if (!n.children.isEmpty() && !collapsed.contains(path))
		{
			for (int i = 0; i < n.children.size(); i++)
			{
				boolean last = i == n.children.size() - 1;
				boolean[] childRails = java.util.Arrays.copyOf(rails, depth + 2);
				childRails[depth + 1] = !last;
				addNodeRows(parent, n.children.get(i), path, stock, opt, childRails, depth + 1);
			}
		}
	}

	private MaterialRow nodeRow(Planner.Node n, String path, Map<String, Counts> stock, Planner.Options opt,
		boolean[] rails, int depth)
	{
		Counts c = stock.getOrDefault(n.key, new Counts());
		long have = c.total(opt.includeWorn, opt.includeOther);

		Color status;
		String verdict;
		switch (n.status())
		{
			case ON_YOU:
				status = Theme.OK;
				verdict = "On you";
				break;
			case IN_STORAGE:
				status = Theme.WARN;
				verdict = withdrawText(n);
				break;
			case MAKE:
				status = n.subtreeShort() ? Theme.BAD : Theme.MAKE;
				verdict = makeText(n);
				break;
			default:
				status = Theme.BAD;
				verdict = "Need " + Theme.n(n.shortBy) + " more" + priceSuffix(n.name, n.shortBy);
				break;
		}

		boolean expandable = !n.children.isEmpty();
		MaterialRow row = new MaterialRow(n.name, have, n.need, status, depth, expandable, collapsed.contains(path), WIDTH_HINT - 4)
			.rails(rails)
			.detail(verdict, status, 2);

		if (n.via != null)
		{
			List<String> blocked = blockers(n.via);
			if (!blocked.isEmpty())
			{
				row.detail("Needs " + String.join(", ", blocked), Theme.BAD, 2);
			}
		}
		if (n.shortBy > 0 && n.via == null)
		{
			ShopAccess.Pick pick = ShopAccess.pick(recipes.getShops(n.name), shopPlayer);
			if (pick.cheapest != null)
			{
				row.detail("Shop: " + shopText(pick.cheapest), Theme.MUTED, 3);
				if (pick.nearest != null)
				{
					row.detail("Nearest: " + shopText(pick.nearest), Theme.MUTED, 3);
				}
			}
			else if (!pick.locked.isEmpty())
			{
				row.detail("Shops: none you can use yet", Theme.FAINT, 1);
			}
		}

		row.icon(iconFor(n.name, row));
		if (expandable)
		{
			row.onToggle(() ->
			{
				if (!collapsed.remove(path))
				{
					collapsed.add(path);
				}
				rebuild();
			});
		}
		row.setToolTipText(tooltip(n, c));
		attachMethodMenu(row, n);
		return row;
	}

	private static String shopText(ShopSource s)
	{
		String price = s.price < 0 ? "" : " · " + Theme.n(s.price) + (s.isCoins() ? " gp" : " " + s.currency.toLowerCase(Locale.ROOT));
		return s.store + price;
	}

	private String tooltip(Planner.Node n, Counts c)
	{
		StringBuilder sb = new StringBuilder("<html><b>").append(Theme.escape(n.name)).append("</b><br>");
		if (c.asMap().isEmpty())
		{
			sb.append("None owned<br>");
		}
		for (Map.Entry<Location, Long> e : c.asMap().entrySet())
		{
			sb.append(Theme.escape(store.label(e.getKey()))).append(": ").append(Theme.n(e.getValue())).append("<br>");
		}
		List<ShopSource> shops = n.shortBy > 0 ? recipes.getShops(n.name) : java.util.Collections.emptyList();
		if (!shops.isEmpty())
		{
			int[] here = host.playerLocation();
			int shown = 0;
			sb.append("<br>Sold by:<br>");
			for (ShopSource s : shops)
			{
				if (shown == 6)
				{
					break;
				}
				List<String> why = ShopAccess.blockers(s, shopPlayer);
				int d = ShopAccess.distance(s, here);
				sb.append("&nbsp;&nbsp;").append(Theme.escape(shopText(s)));
				if (!why.isEmpty())
				{
					sb.append(" <font color='#e07b6a'>needs ").append(Theme.escape(String.join(", ", why))).append("</font>");
				}
				else if (d >= 0)
				{
					sb.append(" <font color='#9a9a9a'>~").append(Theme.n(d)).append(" tiles</font>");
				}
				sb.append("<br>");
				shown++;
			}
		}
		if (!n.alternatives.isEmpty())
		{
			sb.append("<br><i>Right-click to choose how it's made</i>");
		}
		return sb.append("</html>").toString();
	}

	private static String withdrawText(Planner.Node n)
	{
		List<String> parts = new ArrayList<>();
		if (n.fromBank > 0)
		{
			parts.add(Theme.n(n.fromBank) + " from bank");
		}
		if (n.fromOther > 0)
		{
			parts.add(Theme.n(n.fromOther) + " from storage");
		}
		return "Withdraw " + String.join(", ", parts);
	}

	private static String makeText(Planner.Node n)
	{
		long out = Math.max(1, n.via.outputQuantity);
		String make = out > 1
			? "make " + Theme.n(n.crafts * out) + " (" + Theme.n(n.crafts) + " actions)"
			: "make " + Theme.n(n.crafts);
		String text = n.fromStock() + n.fromSurplus > 0
			? "Use " + Theme.n(n.fromStock() + n.fromSurplus) + ", " + make
			: Character.toUpperCase(make.charAt(0)) + make.substring(1);
		if (n.via.facility != null && !n.via.facility.isEmpty())
		{
			text += " · " + n.via.facility;
		}
		return text;
	}

	private String priceSuffix(String name, long qty)
	{
		if (host.effectiveCostMode() != CostMode.GE_PRICES)
		{
			return "";
		}
		long p = icons.price(name);
		return p > 0 ? " (~" + Theme.gp(p * qty) + " gp)" : "";
	}

	private javax.swing.ImageIcon iconFor(String name, Component repaintTarget)
	{
		int id = store.idForName(name);
		if (id <= 0)
		{
			id = icons.idForName(name);
		}
		return id > 0 ? icons.get(id, repaintTarget) : null;
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
				JPopupMenu menu = Kit.popup();
				menu.add(Kit.menuTitle("Make " + n.name + " with:"));
				String current = methods.get(n.key);
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
					javax.swing.JMenuItem item = Kit.menuItem(label, n.via == alt);
					item.addActionListener(ev -> setMethod(n.key, alt.key()));
					menu.add(item);
				}
				menu.add(Kit.separator());
				javax.swing.JMenuItem none = Kit.menuItem("Don't craft it (I'll gather or buy it)", Planner.NO_MAKE.equals(current));
				none.addActionListener(ev -> setMethod(n.key, Planner.NO_MAKE));
				menu.add(none);
				if (current != null)
				{
					javax.swing.JMenuItem reset = Kit.menuItem("Reset to default", false);
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
		rebuild();
	}

	/**
	 * Items changed (bank opened, inventory moved): redraw the plan.
	 */
	void refreshMaterials()
	{
		rebuild();
	}

	// =================================================================================================
	// Persistence

	private void saveSelection()
	{
		List<SavedSelection> out = new ArrayList<>();
		for (Selection s : selections)
		{
			out.add(new SavedSelection(s.recipe.key(), s.quantity, s.targetLevel));
		}
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

	// =================================================================================================
	// Small pieces

	static JPanel column()
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setOpaque(false);
		return p;
	}

	/**
	 * Left-aligned and allowed to stretch to the full width, keeping its own height.
	 */
	static <T extends JComponent> T stretch(T c)
	{
		c.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (!(c instanceof MaterialRow) && !(c instanceof Kit.SectionHeader) && !(c instanceof Kit.SearchField)
			&& !(c instanceof Kit.Dropdown))
		{
			c.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		}
		return c;
	}

	/**
	 * Proportional bar of ready / to make / short.
	 */
	private static final class StackBar extends JComponent
	{
		private final int ready;
		private final int make;
		private final int shortCount;

		StackBar(int ready, int make, int shortCount)
		{
			this.ready = ready;
			this.make = make;
			this.shortCount = shortCount;
			setOpaque(false);
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(100, 8);
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, 8);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = Theme.smooth(g);
			int w = getWidth();
			int h = getHeight();
			RoundRectangle2D clip = new RoundRectangle2D.Double(0, 0, w, h, h, h);
			g2.setColor(Theme.INSET);
			g2.fill(clip);
			int total = ready + make + shortCount;
			if (total > 0)
			{
				g2.setClip(clip);
				int x = 0;
				int[] counts = {ready, make, shortCount};
				Color[] colors = {Theme.OK, Theme.MAKE, Theme.BAD};
				for (int i = 0; i < 3; i++)
				{
					int segW = i == 2 ? w - x : (int) Math.round((double) w * counts[i] / total);
					if (counts[i] > 0 && segW > 0)
					{
						g2.setColor(colors[i]);
						g2.fillRect(x, 0, segW, h);
						x += segW;
						if (x < w)
						{
							g2.setColor(Theme.CARD);
							g2.fillRect(x, 0, 1, h);
							x += 1;
						}
					}
				}
			}
			g2.dispose();
		}
	}

	/**
	 * Dot-and-count legend for ready / to make / short, shortened to fit.
	 */
	private static final class Legend extends JComponent
	{
		private final int[] counts;
		private static final String[] LABELS = {"ready", "to make", "short"};
		private static final Color[] COLORS = {Theme.OK, Theme.MAKE, Theme.BAD};

		Legend(int ready, int make, int shortCount)
		{
			counts = new int[]{ready, make, shortCount};
			setOpaque(false);
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(100, getFontMetrics(Theme.small()).getHeight());
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
			int x = 0;
			int base = fm.getAscent();
			for (int i = 0; i < 3; i++)
			{
				if (counts[i] == 0)
				{
					continue;
				}
				String s = counts[i] + " " + LABELS[i];
				int need = 12 + fm.stringWidth(s);
				if (x + need > getWidth())
				{
					break;
				}
				Icons.DOT.paint(g2, x - 2, (fm.getHeight() - 14) / 2, 14, COLORS[i]);
				g2.setColor(Theme.TEXT);
				g2.drawString(s, x + 10, base);
				x += need + 10;
			}
			g2.dispose();
		}
	}

	/**
	 * One tool: dot, name, and where it is, on one line.
	 */
	private static final class ToolRow extends JComponent
	{
		private final String name;
		private final String state;
		private final Color color;

		ToolRow(String name, String state, Color color)
		{
			this.name = name;
			this.state = state;
			this.color = color;
			setOpaque(false);
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
			int base = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
			Icons.DOT.paint(g2, -3, (getHeight() - 14) / 2, 14, color);
			int sw = fm.stringWidth(state);
			g2.setColor(color);
			g2.drawString(state, getWidth() - sw, base);
			g2.setColor(Theme.TEXT);
			g2.drawString(Theme.fit(name, fm, getWidth() - sw - 22), 12, base);
			g2.dispose();
		}
	}

	/**
	 * Painted suggestion row: name, then requirements (or what blocks it, in red).
	 */
	private final class SuggestionRenderer extends JComponent implements ListCellRenderer<Recipe>
	{
		private Recipe recipe;
		private boolean selected;
		private List<String> blocked = new ArrayList<>();

		@Override
		public Component getListCellRendererComponent(JList<? extends Recipe> list, Recipe value, int index,
			boolean isSelected, boolean cellHasFocus)
		{
			recipe = value;
			selected = isSelected;
			blocked = blockers(value);
			return this;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = Theme.smooth(g);
			if (selected)
			{
				g2.setColor(Theme.CARD_HOVER);
				g2.fill(new RoundRectangle2D.Double(0, 1, getWidth(), getHeight() - 2, 6, 6));
			}
			g2.setFont(Theme.small());
			FontMetrics fm = g2.getFontMetrics();
			int w = getWidth() - 12;
			int y1 = (getHeight() - fm.getHeight() * 2) / 2 + fm.getAscent();
			g2.setColor(blocked.isEmpty() ? Theme.TEXT : Theme.FAINT);
			g2.drawString(Theme.fit(recipe.toString(), fm, w), 6, y1);
			String sub = blocked.isEmpty() ? recipe.subtitle() : "Needs " + String.join(", ", blocked);
			g2.setColor(blocked.isEmpty() ? Theme.MUTED : Theme.BAD);
			g2.drawString(Theme.fit(sub, fm, w), 6, y1 + fm.getHeight());
			g2.dispose();
		}
	}
}
