package com.materialplanner.recipes;

import com.materialplanner.items.Counts;
import com.materialplanner.items.Location;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class PlannerTest
{
	private Recipe hull;
	private Recipe large;
	private Recipe parts;
	private Recipe sawmill;
	private Recipe plankMake;
	private Recipe nails;
	private Map<String, List<Recipe>> idx;
	private Map<String, Counts> stock;

	private static Recipe recipe(String name, int out, String... mats)
	{
		Recipe r = new Recipe();
		r.name = name;
		r.outputQuantity = out;
		for (int i = 0; i < mats.length; i += 2)
		{
			r.materials.add(new Recipe.Material(mats[i], Integer.parseInt(mats[i + 1])));
		}
		return r;
	}

	private static Counts counts(long inv, long bank, long other)
	{
		Counts c = new Counts();
		if (inv > 0)
		{
			c.add(Location.INVENTORY, inv);
		}
		if (bank > 0)
		{
			c.add(Location.BANK, bank);
		}
		if (other > 0)
		{
			c.add(Location.SEED_VAULT, other);
		}
		return c;
	}

	private static Planner.Node find(List<Planner.Node> nodes, String name)
	{
		for (Planner.Node n : nodes)
		{
			if (n.name.equalsIgnoreCase(name))
			{
				return n;
			}
			Planner.Node f = find(n.children, name);
			if (f != null)
			{
				return f;
			}
		}
		return null;
	}

	private Map<Recipe, Integer> one(Recipe r, int qty)
	{
		Map<Recipe, Integer> sel = new LinkedHashMap<>();
		sel.put(r, qty);
		return sel;
	}

	@Before
	public void setUp()
	{
		hull = recipe("Mahogany hull (Sloop)", 1, "Large mahogany hull parts", "16", "Mithril nails", "600", "Swamp tar", "25", "Lead bar", "5");
		large = recipe("Large mahogany hull parts", 1, "Mahogany hull parts", "5");
		large.toolReqs = Arrays.asList(Collections.singletonList("Hammer"), Collections.singletonList("Saw"));
		parts = recipe("Mahogany hull parts", 1, "Mahogany plank", "5");
		parts.toolReqs = Arrays.asList(Collections.singletonList("Hammer"), Collections.singletonList("Saw"));
		sawmill = recipe("Mahogany plank", 1, "Mahogany logs", "1", "Coins", "1500");
		plankMake = recipe("Mahogany plank", 1, "Mahogany logs", "1", "Astral rune", "2", "Nature rune", "1", "Earth rune", "15");
		plankMake.skills.add(new Recipe.SkillReq("Magic", 86));
		nails = recipe("Mithril nails", 15, "Mithril bar", "1");
		nails.toolReqs = Collections.singletonList(Collections.singletonList("Hammer"));
		Recipe loopA = recipe("Thing A", 1, "Thing B", "1");
		Recipe loopB = recipe("Thing B", 1, "Thing A", "1");
		idx = Planner.indexByOutput(Arrays.asList(hull, large, parts, sawmill, plankMake, nails, loopA, loopB));

		stock = new HashMap<>();
		stock.put("mithril nails", counts(20, 118, 0));
		stock.put("swamp tar", counts(0, 103, 0));
		stock.put("lead bar", counts(0, 31, 0));
		stock.put("mahogany plank", counts(0, 117, 0));
		stock.put("coins", counts(118_000, 0, 0));
	}

	@Test
	public void expandsHullDownToRawMaterials()
	{
		Planner.Plan p = Planner.plan(one(hull, 1), idx, stock, new Planner.Options());
		assertEquals(16, find(p.roots, "Large mahogany hull parts").crafts);
		assertEquals(80, find(p.roots, "Mahogany hull parts").crafts);
		Planner.Node plank = find(p.roots, "Mahogany plank");
		assertEquals(400, plank.need);
		assertEquals(117, plank.fromBank);
		assertEquals(283, plank.crafts);
		assertSame("sawmill preferred over Plank Make", sawmill, plank.via);
		assertEquals(283L, (long) p.shortages.get("Mahogany logs"));
		assertEquals(283L * 1500, p.coinsNeeded);
		assertEquals(118_000, p.coinsAvailable);
	}

	@Test
	public void multiOutputRecipesRoundUpActions()
	{
		Planner.Plan p = Planner.plan(one(hull, 1), idx, stock, new Planner.Options());
		// 600 nails needed, 138 owned → 462 to make at 15 per bar → 31 actions
		assertEquals(31, find(p.roots, "Mithril nails").crafts);
		assertEquals(31L, (long) p.shortages.get("Mithril bar"));
	}

	@Test
	public void countsReadyAndShortRoots()
	{
		Planner.Plan p = Planner.plan(one(hull, 1), idx, stock, new Planner.Options());
		assertEquals(2, p.shortCount);
		assertEquals(2, p.ready);
		assertEquals(25L, (long) p.fromBank.get("swamp tar"));
	}

	@Test
	public void usesBankedIntermediatesFirst()
	{
		stock.put("large mahogany hull parts", counts(0, 6, 0));
		Planner.Plan p = Planner.plan(one(hull, 1), idx, stock, new Planner.Options());
		assertEquals(10, find(p.roots, "Large mahogany hull parts").crafts);
		assertEquals(250, find(p.roots, "Mahogany plank").need);
	}

	@Test
	public void sharedStockIsNotCountedTwice()
	{
		Map<Recipe, Integer> two = new LinkedHashMap<>();
		two.put(parts, 20);
		two.put(recipe("Other", 1, "Mahogany plank", "50"), 1);
		Planner.Plan p = Planner.plan(two, idx, stock, new Planner.Options());
		assertEquals(117L, (long) p.fromBank.get("mahogany plank"));
		assertEquals(33, p.roots.stream().mapToLong(n -> n.crafts).sum());
	}

	@Test
	public void methodOverrideAndDontCraft()
	{
		Planner.Options o = new Planner.Options();
		o.methods.put("mahogany plank", plankMake.key());
		Planner.Plan p = Planner.plan(one(hull, 1), idx, stock, o);
		assertSame(plankMake, find(p.roots, "Mahogany plank").via);
		assertEquals(0, p.coinsNeeded);

		o = new Planner.Options();
		o.methods.put("mahogany plank", Planner.NO_MAKE);
		p = Planner.plan(one(hull, 1), idx, stock, o);
		assertEquals(283L, (long) p.shortages.get("Mahogany plank"));

		o = new Planner.Options();
		o.expand = false;
		p = Planner.plan(one(hull, 1), idx, stock, o);
		assertEquals(16L, (long) p.shortages.get("Large mahogany hull parts"));
	}

	@Test
	public void prefersMethodsYouCanActuallyDo()
	{
		// A sawmill-coupon method is "shorter" (no coins), but nobody owns coupons
		Recipe coupon = recipe("Mahogany plank", 1, "Mahogany logs", "1", "Sawmill coupon (mahogany plank)", "1");
		Map<String, List<Recipe>> withCoupon = Planner.indexByOutput(Arrays.asList(hull, large, parts, sawmill, plankMake, coupon, nails));
		Planner.Plan p = Planner.plan(one(hull, 1), withCoupon, stock, new Planner.Options());
		assertSame(sawmill, find(p.roots, "Mahogany plank").via);

		// Own some coupons and the coupon method is fine again
		stock.put("sawmill coupon (mahogany plank)", counts(0, 300, 0));
		p = Planner.plan(one(hull, 1), withCoupon, stock, new Planner.Options());
		assertSame(coupon, find(p.roots, "Mahogany plank").via);
	}

	@Test
	public void recipeLoopsTerminate()
	{
		Planner.Plan p = Planner.plan(one(recipe("Top", 1, "Thing A", "2"), 1), idx, new HashMap<>(), new Planner.Options());
		assertEquals(2, find(p.roots, "Thing A").crafts);
		assertEquals(2, find(p.roots, "Thing B").shortBy);
	}

	@Test
	public void takesCarriedThenBankThenOtherStorage()
	{
		stock.clear();
		stock.put("lead bar", counts(2, 1, 10));
		Map<Recipe, Integer> lead = one(recipe("X", 1, "Lead bar", "5"), 1);
		Planner.Node lb = Planner.plan(lead, idx, stock, new Planner.Options()).roots.get(0);
		assertEquals(2, lb.fromCarried);
		assertEquals(1, lb.fromBank);
		assertEquals(2, lb.fromOther);

		Planner.Options o = new Planner.Options();
		o.includeOther = false;
		assertEquals(2, Planner.plan(lead, idx, stock, o).roots.get(0).shortBy);
	}

	@Test
	public void collectsEachToolOnceAcrossTheTree()
	{
		Planner.Plan p = Planner.plan(one(hull, 1), idx, stock, new Planner.Options());
		assertEquals(2, p.tools.size());
		assertEquals("Hammer", p.tools.get(0).label());
		assertEquals("Saw", p.tools.get(1).label());
		assertTrue(p.tools.get(0).usedBy.contains("Mithril nails"));
	}

	@Test
	public void inventorySlotsCountStackablesOnce()
	{
		Planner.Options o = new Planner.Options();
		o.stackable = name -> name.endsWith("nails");
		Planner.Plan p = Planner.plan(one(hull, 1), idx, stock, o);
		// Taken from stock: 138 nails (1 slot), 25 tar, 5 lead, 117 planks, 424,500 coins (1 slot) + 2 tools
		assertEquals(1 + 25 + 5 + 117 + 1 + 2, p.inventorySlots);
		assertEquals(6, Planner.inventories(p.inventorySlots));
		assertEquals(0, Planner.inventories(0));
		assertEquals(1, Planner.inventories(28));
		assertEquals(2, Planner.inventories(29));
	}

	@Test
	public void xpTable()
	{
		assertEquals(0, Xp.forLevel(1));
		assertEquals(83, Xp.forLevel(2));
		assertEquals(1_210_421, Xp.forLevel(75));
		assertEquals(1_475_581, Xp.forLevel(77));
		assertEquals(13_034_431, Xp.forLevel(99));
		assertEquals(75, Xp.levelFor(1_300_000));
		assertEquals(76, Xp.levelFor(1_475_580));
		assertEquals(99, Xp.levelFor(200_000_000));
	}

	@Test
	public void actionsToLevel()
	{
		// From exactly level 70 (737,627 xp) to 77 at 65 xp each
		assertEquals((int) Math.ceil((1_475_581 - 737_627) / 65.0), Xp.actionsToLevel(737_627, 77, 65));
		assertEquals(0, Xp.actionsToLevel(2_000_000, 77, 65));
		assertEquals(0, Xp.actionsToLevel(0, 10, 0));
	}

	@Test
	public void wikiMarkupIsCleaned()
	{
		assertEquals("Fishing rod", WikiText.clean(
			"<span class=\"plink-template\">[[File:Fishing rod.png|link=Fishing rod]]</span>[[Fishing rod|Fishing rod]]"));
		assertEquals("Mort'ton funeral pyre", WikiText.clean("[[Funeral Pyre (Mort'ton)|Mort'ton funeral pyre]]"));
		assertEquals("Tinderbox", WikiText.clean("[[Tinderbox]]"));
	}

	@Test
	public void toolRequirementsParse()
	{
		List<List<String>> t = WikiText.toolRequirements("Saw, Hammer");
		assertEquals(Arrays.asList(Collections.singletonList("Saw"), Collections.singletonList("Hammer")), t);

		t = WikiText.toolRequirements("<span class=\"plink-template\">[[File:Butterfly net.png|link=Butterfly net]]</span>"
			+ "[[Butterfly net|Butterfly net]] or <span class=\"plink-template\">[[File:Magic butterfly net.png|link=Magic butterfly net]]"
			+ "</span>[[Magic butterfly net|Magic butterfly net]]");
		assertEquals(Collections.singletonList(Arrays.asList("Butterfly net", "Magic butterfly net")), t);

		assertTrue(WikiText.toolRequirements("None").isEmpty());
		assertTrue(WikiText.toolRequirements("[[Hallowed focus|Hallowed focus]] (optional)").isEmpty());
	}

	@Test
	public void membersFlagParses()
	{
		assertEquals(false, WikiRecipeParser.parseMembers(new com.google.gson.JsonPrimitive(false)));
		assertEquals(false, WikiRecipeParser.parseMembers(new com.google.gson.JsonPrimitive("No")));
		assertEquals(true, WikiRecipeParser.parseMembers(new com.google.gson.JsonPrimitive(true)));
		assertEquals(false, WikiRecipeParser.parseMembers(null));
		assertEquals(true, WikiRecipeParser.parseMembers(new com.google.gson.JsonPrimitive("")));
	}

	@Test
	public void parsesExperienceFromWikiJson()
	{
		@SuppressWarnings("deprecation")
		com.google.gson.JsonElement json = new com.google.gson.JsonParser().parse(
			"{\"output\":{\"name\":\"Teak larder\",\"quantity\":\"1\"},"
				+ "\"materials\":[{\"name\":\"Teak plank\",\"quantity\":\"8\"},{\"name\":\"Bolt of cloth\",\"quantity\":\"2\"}],"
				+ "\"skills\":[{\"name\":\"construction\",\"level\":\"43\",\"experience\":\"480\"}],"
				+ "\"tools\":\"Saw, Hammer\",\"facilities\":\"Larder space\"}");
		Recipe r = WikiRecipeParser.parse(json, "Teak larder", new com.google.gson.JsonPrimitive(true));
		assertNotNull(r);
		assertEquals("Construction", r.skills.get(0).name);
		assertEquals(480.0, r.skills.get(0).experience, 0.001);
		assertSame(r.skills.get(0), r.xpSkill());
		assertEquals("Saw, Hammer", r.tools);
		assertEquals("Larder space", r.facility);
	}
}
