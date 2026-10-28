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
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.WorldType;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
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
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import net.runelite.http.api.item.ItemPrice;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
	name = "Material Planner",
	description = "Remembers your bank and other storage, and plans the materials for any recipe",
	internalName = "material-planner",
	legacyDataDirectory = "material-planner",
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
		CONTAINERS.put(InventoryID.TACKLE_BOX, Location.TACKLE_BOX);
		CONTAINERS.put(InventoryID.FORESTRY_KIT, Location.FORESTRY_KIT);
		CONTAINERS.put(InventoryID.HUNTSMANS_KIT, Location.HUNTSMANS_KIT);
	}

	/** Re-read quest progress this often (game ticks); the check runs a client script per quest. */
	private static final int QUEST_REFRESH_TICKS = 100;
	/** Redraw for XP changes at most this often, so level goals keep up without redrawing every xp drop. */
	private static final int XP_REFRESH_TICKS = 10;

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
	private final Map<String, Long> xp = new ConcurrentHashMap<>();
	/** Lower-cased quest name → state. Empty until logged in. */
	private final Map<String, QuestState> questStates = new ConcurrentHashMap<>();
	private final Map<String, List<String>> unmetQuestCache = new ConcurrentHashMap<>();
	/** Normalized item name → stacks in one slot. */
	private final Map<String, Boolean> stackables = new ConcurrentHashMap<>();
	private final Set<String> stackLookups = ConcurrentHashMap.newKeySet();
	private volatile Boolean membersWorld;
	private volatile boolean xpDirty;
	private int tickCount;
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
		Filepath dataDir = null;
		try
		{
			dataDir = getPluginDirectory();
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Plugin data directory unavailable; recipes won't be cached", e);
		}
		recipes = new RecipeRepository(gson, okHttpClient, dataDir);
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
		clearAccountState();
	}

	private void clearAccountState()
	{
		levels.clear();
		xp.clear();
		questStates.clear();
		unmetQuestCache.clear();
		membersWorld = null;
		ironman = false;
		playerLocation = null;
		lastRefreshLocation = null;
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
			xp.put(s.getName().toLowerCase(Locale.ROOT), (long) client.getSkillExperience(s));
		}
		ironman = client.getVarbitValue(VarbitID.IRONMAN) > 0;
		membersWorld = client.getWorldType().contains(WorldType.MEMBERS);
		refreshQuests();
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

	private final java.util.concurrent.atomic.AtomicBoolean refreshQueued = new java.util.concurrent.atomic.AtomicBoolean();

	/**
	 * Redraw the panel. Bursts of calls (e.g. many item lookups finishing at once) collapse into one redraw.
	 */
	private void refreshPanelSettings()
	{
		if (!refreshQueued.compareAndSet(false, true))
		{
			return;
		}
		SwingUtilities.invokeLater(() ->
		{
			refreshQueued.set(false);
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
			ItemComposition canonicalComp = itemManager.getItemComposition(canonical);
			String name = canonicalComp.getName();
			if (name == null || name.equals("null") || name.equals(BANK_FILLER))
			{
				continue;
			}
			stackables.putIfAbsent(ItemStore.normalize(name), canonicalComp.isStackable());
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
		tickCount++;
		if (tickCount % QUEST_REFRESH_TICKS == 0)
		{
			refreshQuests();
		}
		if (xpDirty && tickCount % XP_REFRESH_TICKS == 0)
		{
			xpDirty = false;
			refreshPanelSettings();
		}
		readCargoHold();
		trackLocation();
	}

	/** Tiles the player must move before shop "nearest" picks are worked out again. */
	private static final int LOCATION_REFRESH_TILES = 100;
	private volatile int[] playerLocation;
	private int[] lastRefreshLocation;

	/**
	 * Client thread. Keeps a copy of where the player is, and refreshes the panel after a long move
	 * (a teleport, usually) so the nearest shop follows them.
	 */
	private void trackLocation()
	{
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return;
		}
		WorldPoint wp = WorldPoint.fromLocalInstance(client, me.getLocalLocation());
		if (wp == null)
		{
			return;
		}
		int[] here = {wp.getX(), wp.getY(), wp.getPlane()};
		playerLocation = here;
		if (lastRefreshLocation == null)
		{
			lastRefreshLocation = here;
			return;
		}
		int moved = Math.max(Math.abs(here[0] - lastRefreshLocation[0]), Math.abs(here[1] - lastRefreshLocation[1]));
		if (moved >= LOCATION_REFRESH_TILES)
		{
			lastRefreshLocation = here;
			refreshPanelSettings();
		}
	}

	@Override
	public int[] playerLocation()
	{
		return playerLocation;
	}

	/**
	 * Client thread.
	 */
	private void refreshQuests()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		boolean changed = false;
		for (Quest q : Quest.values())
		{
			QuestState s = q.getState(client);
			QuestState prev = questStates.put(q.getName().toLowerCase(Locale.ROOT), s);
			changed |= prev != s;
		}
		if (changed)
		{
			unmetQuestCache.clear();
			refreshPanelSettings();
		}
	}

	private void readCargoHold()
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
		Long prevXp = xp.put(key, (long) event.getXp());
		if (prevXp == null || prevXp != event.getXp())
		{
			xpDirty = true;
		}
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
			clearAccountState();
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
	public long skillXp(String skill)
	{
		if (skill == null)
		{
			return -1;
		}
		String key = skill.toLowerCase(Locale.ROOT);
		if (key.equals("runecrafting"))
		{
			key = "runecraft";
		}
		return xp.getOrDefault(key, -1L);
	}

	@Override
	public Boolean membersWorld()
	{
		return membersWorld;
	}

	@Override
	public boolean ironman()
	{
		return ironman;
	}

	@Override
	public List<String> unmetQuests(String requirementText)
	{
		if (requirementText == null || requirementText.isEmpty() || questStates.isEmpty())
		{
			return Collections.emptyList();
		}
		return unmetQuestCache.computeIfAbsent(requirementText, text ->
		{
			String lower = text.toLowerCase(Locale.ROOT);
			// "Partial completion of X" / "started X" only needs the quest begun
			boolean partial = lower.contains("partial") || lower.contains("started") || lower.contains("during");
			List<String> unmet = new ArrayList<>();
			for (Quest q : Quest.values())
			{
				String name = q.getName().toLowerCase(Locale.ROOT);
				if (!lower.contains(name))
				{
					continue;
				}
				QuestState s = questStates.get(name);
				boolean met = s == QuestState.FINISHED || (partial && s == QuestState.IN_PROGRESS);
				if (s != null && !met)
				{
					unmet.add(q.getName());
				}
			}
			return unmet;
		});
	}

	@Override
	public Boolean stackable(String itemName)
	{
		String key = ItemStore.normalize(itemName);
		Boolean known = stackables.get(key);
		if (known != null || !stackLookups.add(key))
		{
			return known;
		}
		// Not seen in any container yet: look the item up by name, then read its definition on the client thread
		int id = -1;
		for (ItemPrice p : itemManager.search(itemName))
		{
			if (p.getName().equalsIgnoreCase(itemName))
			{
				id = p.getId();
				break;
			}
		}
		if (id <= 0)
		{
			stackables.put(key, false);
			return false;
		}
		final int itemId = id;
		clientThread.invokeLater(() ->
		{
			stackables.put(key, itemManager.getItemComposition(itemId).isStackable());
			refreshPanelSettings();
		});
		return null;
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
