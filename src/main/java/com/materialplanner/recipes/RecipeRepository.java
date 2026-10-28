package com.materialplanner.recipes;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Supplies every known recipe. Sources, merged in order:
 * <ol>
 *   <li>Built-in seed recipes bundled with the plugin (always available, curated).</li>
 *   <li>The OSRS Wiki recipe database, downloaded and cached in the plugin's data directory.</li>
 * </ol>
 */
@Slf4j
public class RecipeRepository
{
	private static final String WIKI_API = "https://oldschool.runescape.wiki/api.php";
	private static final String USER_AGENT = "RuneLite Material Planner plugin";
	private static final int PAGE_SIZE = 500;
	private static final int MAX_ROWS = 40_000;
	// v2: adds XP, cleaned tools/facilities, quest text and the real members flag
	private static final String CACHE_NAME = "recipes-v2.json";
	private static final String SHOP_CACHE_NAME = "shops-v1.json";
	private static final int MAX_SHOP_ROWS = 60_000;
	private static final Type SHOP_LIST_TYPE = new TypeToken<List<ShopSource>>()
	{
	}.getType();
	private static final Type LIST_TYPE = new TypeToken<List<Recipe>>()
	{
	}.getType();

	/**
	 * Skills hidden from the skill filter: combat and spellcasting skills appear on the wiki mostly as
	 * level requirements or spell costs, not things you make. Their recipes are still searchable.
	 */
	private static final java.util.Set<String> HIDDEN_FILTER_SKILLS = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);

	static
	{
		HIDDEN_FILTER_SKILLS.addAll(java.util.Arrays.asList(
			"Attack", "Strength", "Defence", "Ranged", "Magic", "Hitpoints", "Prayer"));
	}

	private static final java.util.regex.Pattern BOAT_SUFFIX = java.util.regex.Pattern.compile("\\s*\\((Raft|Skiff|Sloop)\\)$");

	private final Gson gson;
	private final OkHttpClient http;
	/** Plugin data directory, or null when it couldn't be opened (the cache is then skipped). */
	private final Filepath cacheDir;

	private volatile List<Recipe> recipes = Collections.emptyList();
	private volatile List<String> skills = Collections.emptyList();
	private volatile Map<String, List<Recipe>> byOutput = Collections.emptyMap();
	private volatile Map<String, List<ShopSource>> shops = Collections.emptyMap();
	private volatile int xpRecipeCount;
	private volatile String status = "Loading recipes…";

	/**
	 * @param cacheDir the plugin's data directory from {@code Plugin#getPluginDirectory()}, or null
	 */
	public RecipeRepository(Gson gson, OkHttpClient http, Filepath cacheDir)
	{
		this.gson = gson;
		this.http = http;
		this.cacheDir = cacheDir;
	}

	public List<Recipe> getRecipes()
	{
		return recipes;
	}

	/**
	 * All recipes indexed by normalized output name, for sub-recipe expansion.
	 */
	public Map<String, List<Recipe>> getByOutput()
	{
		return byOutput;
	}

	/**
	 * Shops selling an item, cheapest coin price first. Empty when unknown.
	 */
	public List<ShopSource> getShops(String itemName)
	{
		return shops.getOrDefault(Planner.normalize(itemName), Collections.emptyList());
	}

	/**
	 * How many recipes have XP data, which level goals rely on.
	 */
	public int getXpRecipeCount()
	{
		return xpRecipeCount;
	}

	public List<String> getSkills()
	{
		return skills;
	}

	public String getStatus()
	{
		return status;
	}

	/**
	 * Blocking; call off the client and Swing threads.
	 *
	 * @param useWiki      whether wiki data may be used at all
	 * @param refreshDays  re-download when the cache is older than this
	 * @param forceRefresh ignore cache age
	 */
	public void load(boolean useWiki, int refreshDays, boolean forceRefresh)
	{
		List<Recipe> seed = loadSeed();
		List<Recipe> wiki = Collections.emptyList();
		String wikiStatus = null;

		if (useWiki)
		{
			List<Recipe> cached = readCache();
			boolean stale = cached == null || forceRefresh
				|| System.currentTimeMillis() - cacheModified() > TimeUnit.DAYS.toMillis(refreshDays);

			if (stale)
			{
				status = "Downloading recipes from the OSRS Wiki…";
				try
				{
					List<Recipe> fresh = fetchWiki();
					if (!fresh.isEmpty())
					{
						writeCache(fresh);
						cached = fresh;
					}
				}
				catch (IOException | RuntimeException e)
				{
					log.warn("Wiki recipe download failed", e);
					wikiStatus = cached == null
						? "Wiki download failed; built-in recipes only"
						: "Wiki download failed; using cached recipes";
				}
			}
			if (cached != null)
			{
				wiki = cached;
			}
			loadShops(stale);
		}

		// Built-in boat-part recipes are labelled per boat; drop the wiki's unlabelled copies of them
		java.util.Set<String> seedBaseNames = new java.util.HashSet<>();
		for (Recipe r : seed)
		{
			seedBaseNames.add(BOAT_SUFFIX.matcher(r.name).replaceFirst("").toLowerCase());
		}

		Map<String, Recipe> merged = new LinkedHashMap<>();
		for (Recipe r : seed)
		{
			merged.put(r.key(), r);
		}
		for (Recipe r : wiki)
		{
			if (!seedBaseNames.contains(r.name.toLowerCase()))
			{
				merged.putIfAbsent(r.key(), r);
			}
		}
		List<Recipe> all = new ArrayList<>(merged.values());
		labelDuplicates(all);
		all.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name));

		TreeSet<String> skillSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (Recipe r : all)
		{
			for (Recipe.SkillReq s : r.skills)
			{
				if (!HIDDEN_FILTER_SKILLS.contains(s.name))
				{
					skillSet.add(s.name);
				}
			}
		}

		int withXp = 0;
		for (Recipe r : all)
		{
			if (r.xpSkill() != null)
			{
				withXp++;
			}
		}
		xpRecipeCount = withXp;
		byOutput = Planner.indexByOutput(all);
		recipes = Collections.unmodifiableList(all);
		skills = Collections.unmodifiableList(new ArrayList<>(skillSet));
		status = wikiStatus != null ? wikiStatus : String.format("%,d recipes", all.size());
	}

	/**
	 * The wiki lists raft/skiff/sloop versions of boat parts under one name. Three Sailing recipes
	 * sharing a name, each needing more materials than the last, are labelled Raft, Skiff, Sloop.
	 * Any other same-named recipes show their materials instead so they can be told apart.
	 */
	private static void labelDuplicates(List<Recipe> all)
	{
		Map<String, List<Recipe>> byName = new LinkedHashMap<>();
		for (Recipe r : all)
		{
			r.variant = null;
			r.showMaterials = false;
			byName.computeIfAbsent(r.name.toLowerCase(), k -> new ArrayList<>()).add(r);
		}
		String[] boats = {"Raft", "Skiff", "Sloop"};
		for (List<Recipe> group : byName.values())
		{
			if (group.size() < 2)
			{
				continue;
			}
			if (group.size() == 3 && group.stream().allMatch(r -> r.usesSkill("Sailing")))
			{
				List<Recipe> sorted = new ArrayList<>(group);
				sorted.sort(java.util.Comparator.comparingInt(Recipe::totalMaterialQuantity));
				if (sorted.get(0).totalMaterialQuantity() < sorted.get(1).totalMaterialQuantity()
					&& sorted.get(1).totalMaterialQuantity() < sorted.get(2).totalMaterialQuantity())
				{
					for (int i = 0; i < 3; i++)
					{
						sorted.get(i).variant = boats[i];
					}
					continue;
				}
			}
			group.forEach(r -> r.showMaterials = true);
		}
	}

	private List<Recipe> loadSeed()
	{
		try (InputStream in = RecipeRepository.class.getResourceAsStream("seed-recipes.json"))
		{
			if (in == null)
			{
				return Collections.emptyList();
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				List<Recipe> list = gson.fromJson(reader, LIST_TYPE);
				if (list == null)
				{
					return Collections.emptyList();
				}
				list.forEach(r -> r.source = "built-in");
				return list;
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Could not load built-in recipes", e);
			return Collections.emptyList();
		}
	}

	private Filepath cacheFile()
	{
		return cacheFile(CACHE_NAME);
	}

	private Filepath cacheFile(String name)
	{
		return cacheDir == null ? null : cacheDir.join(name);
	}

	private long cacheModified()
	{
		Filepath f = cacheFile();
		try
		{
			return f != null && f.exists() ? f.getLastModifiedTime().toMillis() : 0;
		}
		catch (IOException e)
		{
			return 0;
		}
	}

	private List<Recipe> readCache()
	{
		Filepath f = cacheFile();
		if (f == null || !f.exists())
		{
			return null;
		}
		try (Reader reader = f.openBufferedReader())
		{
			return gson.fromJson(reader, LIST_TYPE);
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Recipe cache unreadable, will re-download", e);
			return null;
		}
	}

	private void writeCache(List<Recipe> list)
	{
		writeJson(CACHE_NAME, list, LIST_TYPE);
	}

	private void writeJson(String name, Object value, Type type)
	{
		Filepath f = cacheFile(name);
		if (f == null)
		{
			return;
		}
		try
		{
			cacheDir.createDirectories();
			// Write to a temp file first so a crash mid-write can't leave a corrupt cache
			Filepath tmp = cacheDir.join(name + ".tmp");
			try (Writer w = tmp.openBufferedWriter())
			{
				gson.toJson(value, type, w);
			}
			tmp.moveTo(f, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException e)
		{
			log.warn("Could not write {}", name, e);
		}
	}

	// --- shops -----------------------------------------------------------------------------------

	/**
	 * Shop data is a nice-to-have: any failure leaves shop info empty and never affects recipes.
	 */
	private void loadShops(boolean refresh)
	{
		List<ShopSource> list = null;
		Filepath f = cacheFile(SHOP_CACHE_NAME);
		if (!refresh && f != null && f.exists())
		{
			try (Reader reader = f.openBufferedReader())
			{
				list = gson.fromJson(reader, SHOP_LIST_TYPE);
			}
			catch (IOException | RuntimeException e)
			{
				log.warn("Shop cache unreadable", e);
			}
		}
		if (list == null)
		{
			try
			{
				status = "Downloading shop data from the OSRS Wiki…";
				list = fetchShops();
				if (!list.isEmpty())
				{
					writeJson(SHOP_CACHE_NAME, list, SHOP_LIST_TYPE);
				}
			}
			catch (IOException | RuntimeException e)
			{
				log.warn("Wiki shop download failed", e);
				return;
			}
		}
		shops = indexShops(list);
	}

	static Map<String, List<ShopSource>> indexShops(List<ShopSource> list)
	{
		Map<String, List<ShopSource>> idx = new java.util.HashMap<>();
		for (ShopSource s : list)
		{
			if (s.item != null && s.store != null)
			{
				idx.computeIfAbsent(Planner.normalize(s.item), k -> new ArrayList<>()).add(s);
			}
		}
		// Coin shops first, then cheapest
		java.util.Comparator<ShopSource> order = java.util.Comparator
			.comparing((ShopSource s) -> !s.isCoins())
			.thenComparingInt(s -> s.price < 0 ? Integer.MAX_VALUE : s.price);
		for (List<ShopSource> l : idx.values())
		{
			l.sort(order);
		}
		return idx;
	}

	private List<ShopSource> fetchShops() throws IOException
	{
		List<ShopSource> out = new ArrayList<>();
		for (int offset = 0; offset < MAX_SHOP_ROWS; offset += PAGE_SIZE)
		{
			String query = "bucket('storeline').select('sold_item','sold_by','store_sell_price','store_currency','store_stock')"
				+ ".limit(" + PAGE_SIZE + ").offset(" + offset + ").run()";
			JsonArray rows = queryBucket(query);
			for (JsonElement row : rows)
			{
				if (!row.isJsonObject())
				{
					continue;
				}
				JsonObject ro = row.getAsJsonObject();
				ShopSource s = new ShopSource();
				s.item = WikiText.clean(text(ro.get("sold_item")));
				s.store = WikiText.clean(text(ro.get("sold_by")));
				s.currency = WikiText.clean(text(ro.get("store_currency")));
				s.stock = WikiText.clean(text(ro.get("store_stock")));
				s.price = WikiRecipeParser.num(ro.get("store_sell_price"), -1);
				if (s.item != null && !s.item.isEmpty() && s.store != null && !s.store.isEmpty())
				{
					out.add(s);
				}
			}
			if (rows.size() < PAGE_SIZE)
			{
				break;
			}
		}
		return out;
	}

	private static String text(JsonElement e)
	{
		if (e == null || e.isJsonNull())
		{
			return null;
		}
		if (e.isJsonArray())
		{
			JsonArray a = e.getAsJsonArray();
			return a.size() == 0 ? null : text(a.get(0));
		}
		return e.isJsonPrimitive() ? e.getAsString() : null;
	}

	/**
	 * Page through the wiki's "recipe" bucket, which backs the wiki's own recipe and calculator tables.
	 */
	private List<Recipe> fetchWiki() throws IOException
	{
		List<Recipe> out = new ArrayList<>();
		for (int offset = 0; offset < MAX_ROWS; offset += PAGE_SIZE)
		{
			String query = "bucket('recipe').select('page_name','production_json','is_members_only')"
				+ ".limit(" + PAGE_SIZE + ").offset(" + offset + ").run()";
			JsonArray rows = queryBucket(query);

			for (JsonElement row : rows)
			{
				if (!row.isJsonObject())
				{
					continue;
				}
				JsonObject ro = row.getAsJsonObject();
				String page = text(ro.get("page_name"));
				Recipe r = WikiRecipeParser.parse(ro.get("production_json"), page, ro.get("is_members_only"));
				if (r != null)
				{
					out.add(r);
				}
			}

			status = String.format("Downloading recipes from the OSRS Wiki… %,d", out.size());
			if (rows.size() < PAGE_SIZE)
			{
				break;
			}
		}
		log.debug("Downloaded {} recipes from the wiki", out.size());
		return out;
	}

	private JsonArray queryBucket(String query) throws IOException
	{
		String url = WIKI_API + "?action=bucket&format=json&query="
			+ URLEncoder.encode(query, StandardCharsets.UTF_8.name());
		Request req = new Request.Builder().url(url).header("User-Agent", USER_AGENT).build();
		try (Response resp = http.newCall(req).execute())
		{
			ResponseBody body = resp.body();
			if (!resp.isSuccessful() || body == null)
			{
				throw new IOException("Wiki API returned HTTP " + resp.code());
			}
			@SuppressWarnings("deprecation")
			JsonElement root = new JsonParser().parse(body.charStream());
			return extractRows(root);
		}
	}

	/**
	 * The bucket API returns {"bucket": [...]}; fall back to the first array found in case of minor format changes.
	 */
	private static JsonArray extractRows(JsonElement root)
	{
		if (root == null || !root.isJsonObject())
		{
			throw new IllegalStateException("Unexpected wiki response");
		}
		JsonObject o = root.getAsJsonObject();
		if (o.has("error"))
		{
			throw new IllegalStateException("Wiki API error: " + o.get("error"));
		}
		if (o.has("bucket") && o.get("bucket").isJsonArray())
		{
			return o.getAsJsonArray("bucket");
		}
		for (Map.Entry<String, JsonElement> e : o.entrySet())
		{
			if (e.getValue().isJsonArray())
			{
				return e.getValue().getAsJsonArray();
			}
		}
		throw new IllegalStateException("No recipe rows in wiki response");
	}
}
