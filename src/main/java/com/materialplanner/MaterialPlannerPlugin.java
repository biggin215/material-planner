package com.materialplanner;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.materialplanner.items.Counts;
import com.materialplanner.items.ItemStore;
import com.materialplanner.items.Location;
import com.materialplanner.items.StoredItem;
import com.materialplanner.recipes.RecipeRepository;
import com.materialplanner.ui.MaterialPlannerPanel;
import com.materialplanner.ui.PanelHost;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
	name = "Material Planner",
	description = "Remembers your bank and other storage, and plans the materials for any recipe",
	tags = {"bank", "memory", "recipe", "materials", "shopping", "skilling", "sailing", "storage"}
)
public class MaterialPlannerPlugin extends Plugin implements PanelHost
{
	private static final String BANK_FILLER = "Bank filler";

	/** Game container id → where we file its contents. */
	private static final Map<Integer, Location> CONTAINERS = new HashMap<>();

	static
	{
		CONTAINERS.put(InventoryID.INV, Location.INVENTORY);
		CONTAINERS.put(InventoryID.WORN, Location.WORN);
		CONTAINERS.put(InventoryID.BANK, Location.BANK);
		CONTAINERS.put(InventoryID.SEED_VAULT, Location.SEED_VAULT);
		CONTAINERS.put(InventoryID.SEED_BOX, Location.SEED_BOX);
		CONTAINERS.put(InventoryID.LOOTING_BAG, Location.LOOTING_BAG);
		CONTAINERS.put(InventoryID.INV_GROUP_TEMP, Location.GROUP_STORAGE);
	}

	private static final int[] POUCH_TYPE_VARBITS = {
		VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3,
		VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6
	};
	private static final int[] POUCH_QTY_VARBITS = {
		VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3,
		VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6
	};
	private static final Set<Integer> POUCH_VARBITS = new HashSet<>();

	static
	{
		Arrays.stream(POUCH_TYPE_VARBITS).forEach(POUCH_VARBITS::add);
		Arrays.stream(POUCH_QTY_VARBITS).forEach(POUCH_VARBITS::add);
	}

	/** Settings whose change should redraw the panel. */
	private static final Set<String> DISPLAY_KEYS = new HashSet<>(Arrays.asList(
		"expandRecipes", "costMode", "levelCheck", "hideUnmet", "countWorn", "countOtherStorage",
		"highlightBank", "highlightColor"));

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BankHighlightOverlay bankOverlay;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private MaterialPlannerConfig config;

	@Inject
	private Gson gson;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private ScheduledExecutorService executor;

	private ItemStore store;
	private RecipeRepository recipes;
	private MaterialPlannerPanel panel;
	private NavigationButton navButton;

	private final Map<String, Integer> levels = new ConcurrentHashMap<>();
	/** Boat name of the cargo hold window currently open, or null when it's closed. */
	private String openCargoBoat;
	private String lastCargoSignature;
	private volatile boolean ironman;

	@Provides
	MaterialPlannerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MaterialPlannerConfig.class);
	}

	@Override
	protected void startUp()
	{
		store = new ItemStore(configManager, gson);
		recipes = new RecipeRepository(gson, okHttpClient);
		panel = new MaterialPlannerPanel(itemManager, store, recipes, configManager, gson, config, this,
			() -> loadRecipes(true));

		BufferedImage icon = ImageUtil.loadImageResource(MaterialPlannerPlugin.class, "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Material Planner")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(bankOverlay);

		store.loadProfile();
		loadRecipes(false);

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(this::captureLoggedInState);
		}
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(bankOverlay);
		bankOverlay.setNeeded(new HashSet<>());
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		store = null;
		recipes = null;
		levels.clear();
	}

	/**
	 * Client thread. Reads everything available right now: open containers, levels, account type, rune pouch.
	 */
	private void captureLoggedInState()
	{
		for (Map.Entry<Integer, Location> e : CONTAINERS.entrySet())
		{
			ItemContainer c = client.getItemContainer(e.getKey());
			if (c != null)
			{
				record(e.getValue(), c);
			}
		}
		for (Skill s : Skill.values())
		{
			levels.put(s.getName().toLowerCase(Locale.ROOT), client.getRealSkillLevel(s));
		}
		ironman = client.getVarbitValue(VarbitID.IRONMAN) > 0;
		updateRunePouch();
		refreshPanelSettings();
	}

	private void loadRecipes(boolean force)
	{
		executor.execute(() ->
		{
			RecipeRepository repo = recipes;
			if (repo == null)
			{
				return;
			}
			SwingUtilities.invokeLater(this::refreshPanelRecipes);
			repo.load(config.wikiRecipes(), config.refreshDays(), force);
			SwingUtilities.invokeLater(this::refreshPanelRecipes);
		});
	}

	private void refreshPanelRecipes()
	{
		if (panel != null)
		{
			panel.onRecipesLoaded();
		}
	}

	private void refreshPanelSettings()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.onSettingsChanged();
			}
		});
	}

	// --- containers ------------------------------------------------------------------------------

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		Location loc = CONTAINERS.get(event.getContainerId());
		if (loc == null)
		{
			return;
		}
		record(loc, event.getItemContainer());
		if (loc == Location.INVENTORY)
		{
			// Rune pouch only counts while the pouch itself is carried
			updateRunePouch();
		}
	}

	/**
	 * Client thread.
	 */
	private void record(Location loc, ItemContainer container)
	{
		List<StoredItem> items = toStoredItems(container);
		// An empty bank event almost always means "not loaded", not an empty bank
		if (loc == Location.BANK && items.isEmpty())
		{
			return;
		}
		store.update(loc, items);
	}

	/**
	 * Must run on the client thread (item compositions).
	 */
	private List<StoredItem> toStoredItems(ItemContainer container)
	{
		List<int[]> stacks = new ArrayList<>();
		if (container != null)
		{
			for (Item item : container.getItems())
			{
				stacks.add(new int[]{item.getId(), item.getQuantity()});
			}
		}
		return toStoredItems(stacks);
	}

	/**
	 * Must run on the client thread.
	 *
	 * @param stacks pairs of {item id, quantity}
	 */
	private List<StoredItem> toStoredItems(List<int[]> stacks)
	{
		List<StoredItem> out = new ArrayList<>();
		for (int[] stack : stacks)
		{
			int itemId = stack[0];
			int qty = stack[1];
			if (itemId <= 0 || qty <= 0)
			{
				continue;
			}
			ItemComposition comp = itemManager.getItemComposition(itemId);
			if (comp.getPlaceholderTemplateId() != -1)
			{
				continue;
			}
			int canonical = itemManager.canonicalize(itemId);
			String name = itemManager.getItemComposition(canonical).getName();
			if (name == null || name.equals("null") || name.equals(BANK_FILLER))
			{
				continue;
			}
			out.add(new StoredItem(canonical, name, qty));
		}
		return out;
	}

	/**
	 * Client thread. Rune pouch contents live in varbits rather than an item container.
	 */
	private void updateRunePouch()
	{
		if (store == null)
		{
			return;
		}
		List<StoredItem> runes = new ArrayList<>();
		if (carryingRunePouch())
		{
			EnumComposition runeEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
			for (int i = 0; i < POUCH_TYPE_VARBITS.length; i++)
			{
				int type = client.getVarbitValue(POUCH_TYPE_VARBITS[i]);
				int qty = client.getVarbitValue(POUCH_QTY_VARBITS[i]);
				if (type <= 0 || qty <= 0 || runeEnum == null)
				{
					continue;
				}
				int itemId = runeEnum.getIntValue(type);
				if (itemId <= 0)
				{
					continue;
				}
				runes.add(new StoredItem(itemId, itemManager.getItemComposition(itemId).getName(), qty));
			}
		}
		store.update(Location.RUNE_POUCH, runes);
	}

	private boolean carryingRunePouch()
	{
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		if (inv == null)
		{
			return false;
		}
		for (Item item : inv.getItems())
		{
			if (item.getId() > 0)
			{
				String name = itemManager.getItemComposition(item.getId()).getName();
				if (name != null && name.toLowerCase(Locale.ROOT).contains("rune pouch"))
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Cargo holds are read straight from the cargo hold window while it's open: the window's title
	 * names the boat, which lets each boat keep its own snapshot.
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		Widget itemsWidget = client.getWidget(InterfaceID.SailingBoatCargohold.ITEMS);
		if (itemsWidget == null || itemsWidget.isHidden())
		{
			openCargoBoat = null;
			lastCargoSignature = null;
			return;
		}
		if (openCargoBoat == null)
		{
			openCargoBoat = findCargoBoatName();
		}

		List<int[]> stacks = new ArrayList<>();
		StringBuilder sig = new StringBuilder(openCargoBoat);
		Widget[] children = itemsWidget.getDynamicChildren();
		if (children != null)
		{
			for (Widget w : children)
			{
				if (w != null && !w.isHidden() && w.getItemId() > 0 && w.getItemQuantity() > 0)
				{
					stacks.add(new int[]{w.getItemId(), w.getItemQuantity()});
					sig.append('|').append(w.getItemId()).append('x').append(w.getItemQuantity());
				}
			}
		}
		// Only save when the contents change
		if (sig.toString().equals(lastCargoSignature))
		{
			return;
		}
		lastCargoSignature = sig.toString();
		store.update(store.cargoSlotFor(openCargoBoat), toStoredItems(stacks));
	}

	private String findCargoBoatName()
	{
		String title = findText(client.getWidget(InterfaceID.SailingBoatCargohold.UNIVERSE), "cargo hold", 0);
		if (title != null)
		{
			int colon = title.indexOf(':');
			String name = Text.removeTags(colon >= 0 ? title.substring(colon + 1) : title).trim();
			if (!name.isEmpty())
			{
				return name;
			}
		}
		return "Boat";
	}

	/**
	 * Depth-first search of a widget tree for text containing the given phrase.
	 */
	private static String findText(Widget w, String phrase, int depth)
	{
		if (w == null || depth > 6)
		{
			return null;
		}
		String text = w.getText();
		if (text != null && Text.removeTags(text).toLowerCase(Locale.ROOT).contains(phrase))
		{
			return text;
		}
		for (Widget[] group : new Widget[][]{w.getStaticChildren(), w.getDynamicChildren(), w.getNestedChildren()})
		{
			if (group == null)
			{
				continue;
			}
			for (Widget c : group)
			{
				String found = findText(c, phrase, depth + 1);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		int id = event.getVarbitId();
		if (POUCH_VARBITS.contains(id))
		{
			updateRunePouch();
		}
		else if (id == VarbitID.IRONMAN)
		{
			boolean now = event.getValue() > 0;
			if (now != ironman)
			{
				ironman = now;
				refreshPanelSettings();
			}
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		String key = event.getSkill().getName().toLowerCase(Locale.ROOT);
		Integer prev = levels.put(key, event.getLevel());
		// Fires on every xp drop; only redraw when a level actually changes
		if (prev == null || prev != event.getLevel())
		{
			refreshPanelSettings();
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		store.loadProfile();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			store.clearLive();
			levels.clear();
			ironman = false;
			refreshPanelSettings();
		}
		else if (event.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(this::captureLoggedInState);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!MaterialPlannerConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if ("wikiRecipes".equals(event.getKey()))
		{
			loadRecipes(false);
		}
		else if (DISPLAY_KEYS.contains(event.getKey()))
		{
			refreshPanelSettings();
		}
	}

	// --- PanelHost -------------------------------------------------------------------------------

	@Override
	public int realLevel(String skill)
	{
		if (skill == null)
		{
			return -1;
		}
		String key = skill.toLowerCase(Locale.ROOT);
		// The wiki says "Runecrafting"; RuneLite's skill is "Runecraft"
		if (key.equals("runecrafting"))
		{
			key = "runecraft";
		}
		return levels.getOrDefault(key, -1);
	}

	@Override
	public CostMode effectiveCostMode()
	{
		CostMode mode = config.costMode();
		if (mode == CostMode.AUTO)
		{
			return ironman ? CostMode.COINS_ONLY : CostMode.GE_PRICES;
		}
		return mode;
	}

	@Override
	public void onPlanChanged(Set<String> bankNeeded)
	{
		bankOverlay.setNeeded(bankNeeded);
	}

	// --- "Check bank" menu option ----------------------------------------------------------------

	/**
	 * Adds "Check bank" next to Examine on inventory items and ground items.
	 */
	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.checkBankMenu())
		{
			return;
		}

		MenuEntry entry = event.getMenuEntry();
		if (!"Examine".equals(entry.getOption()))
		{
			return;
		}

		final int itemId;
		if (entry.getType() == MenuAction.EXAMINE_ITEM_GROUND)
		{
			itemId = entry.getIdentifier();
		}
		else if ((entry.getParam1() >>> 16) == InterfaceID.INVENTORY && entry.getItemId() > 0)
		{
			itemId = entry.getItemId();
		}
		else
		{
			return;
		}

		client.getMenu().createMenuEntry(-1)
			.setOption("Check bank")
			.setTarget(entry.getTarget())
			.setType(MenuAction.RUNELITE)
			.onClick(e -> checkBank(itemId));
	}

	private void checkBank(int itemId)
	{
		int canonical = itemManager.canonicalize(itemId);
		String name = itemManager.getItemComposition(canonical).getName();

		String msg;
		if (!store.hasBankSnapshot())
		{
			msg = "Material Planner: no bank snapshot yet. Open your bank once to record it.";
		}
		else
		{
			Counts c = store.countById(canonical);
			NumberFormat nf = NumberFormat.getIntegerInstance();
			StringBuilder sb = new StringBuilder(name)
				.append(" - Bank: <col=ff9040>").append(nf.format(c.bank())).append("</col>")
				.append(", Inventory: ").append(nf.format(c.get(Location.INVENTORY)));
			for (Map.Entry<Location, Long> e : c.asMap().entrySet())
			{
				Location loc = e.getKey();
				if (loc != Location.BANK && loc != Location.INVENTORY)
				{
					sb.append(", ").append(store.label(loc)).append(": ").append(nf.format(e.getValue()));
				}
			}
			sb.append(" <col=7f7f7f>(bank as of ").append(MaterialPlannerPanel.ago(store.getBankTime())).append(")</col>");
			msg = sb.toString();
		}
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, null);
	}
}
