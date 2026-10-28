package com.materialplanner.recipes;

import com.materialplanner.items.Counts;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns "make these recipes" into a material tree.
 * <p>
 * For every material it first uses what you already own (on you, then bank, then other storage), and only
 * plans to craft what is still missing, recursing into that recipe's ingredients. Stock is shared across
 * the whole plan, so two recipes needing the same planks don't both count the same planks.
 * <p>
 * Pure logic with no RuneLite dependencies, so it can be tested on its own.
 */
public final class Planner
{
	/**
	 * Method override meaning "don't craft this, I'll get it another way".
	 */
	public static final String NO_MAKE = "-";

	private static final Set<String> NEVER_EXPAND = new HashSet<>(Collections.singletonList("coins"));

	public enum Status
	{
		/** Enough carried right now. */
		ON_YOU,
		/** Enough once withdrawn from bank or other storage. */
		IN_STORAGE,
		/** Some or all of it will be crafted; see children. */
		MAKE,
		/** Not enough and not craftable (or crafting disabled). */
		SHORT
	}

	public static final class Options
	{
		public boolean expand = true;
		public int maxDepth = 8;
		public boolean includeWorn = true;
		public boolean includeOther = true;
		/** normalized material name → recipe key to use, or NO_MAKE */
		public Map<String, String> methods = new HashMap<>();
		/** Whether an item stacks in one inventory slot, by display name. Null: only coins stack. */
		public java.util.function.Predicate<String> stackable;
	}

	/**
	 * A tool the plan needs at least once (not consumed). Any one of {@code alternatives} will do.
	 */
	public static final class ToolNeed
	{
		public final List<String> alternatives;
		/** Recipes that need it, for the tooltip. */
		public final List<String> usedBy = new ArrayList<>();

		ToolNeed(List<String> alternatives)
		{
			this.alternatives = alternatives;
		}

		public String label()
		{
			return String.join(" or ", alternatives);
		}
	}

	public static final class Node
	{
		public final String name;
		public final String key;
		public final long need;
		public final int depth;
		public long fromCarried;
		public long fromBank;
		public long fromOther;
		public long fromSurplus;
		public Recipe via;
		public long crafts;
		public long shortBy;
		public List<Recipe> alternatives = Collections.emptyList();
		public final List<Node> children = new ArrayList<>();

		Node(String name, long need, int depth)
		{
			this.name = name;
			this.key = normalize(name);
			this.need = need;
			this.depth = depth;
		}

		public long fromStock()
		{
			return fromCarried + fromBank + fromOther;
		}

		public Status status()
		{
			if (shortBy > 0)
			{
				return Status.SHORT;
			}
			if (via != null && crafts > 0)
			{
				return Status.MAKE;
			}
			if (fromBank + fromOther > 0)
			{
				return Status.IN_STORAGE;
			}
			return Status.ON_YOU;
		}

		public boolean subtreeShort()
		{
			if (shortBy > 0)
			{
				return true;
			}
			for (Node c : children)
			{
				if (c.subtreeShort())
				{
					return true;
				}
			}
			return false;
		}

		/**
		 * Stable id for remembering which rows the user collapsed.
		 */
		public String path(String parentPath)
		{
			return parentPath + "/" + key;
		}
	}

	public static final class Plan
	{
		public final List<Node> roots = new ArrayList<>();
		/** Raw materials still missing after crafting what can be crafted (display name → qty). */
		public final Map<String, Long> shortages = new LinkedHashMap<>();
		/** Items the plan takes from the bank, by normalized name. Drives bank highlighting. */
		public final Map<String, Long> fromBank = new HashMap<>();
		/** Items the plan takes from other storage, by normalized name. */
		public final Map<String, Long> fromOther = new HashMap<>();
		public long coinsNeeded;
		public long coinsAvailable;
		/** Every distinct tool the plan needs, in first-seen order. */
		public final List<ToolNeed> tools = new ArrayList<>();
		/** Inventory slots taken by materials you'll carry (stackables count once), plus tools. */
		public int inventorySlots;
		public int ready;
		public int toMake;
		public int shortCount;
	}

	private final Map<String, List<Recipe>> byOutput;
	private final Map<String, Counts> stock;
	private final Options opt;
	/** Remaining unallocated stock per item: [carried, bank, other]. */
	private final Map<String, long[]> remaining = new HashMap<>();
	/** Extra output from crafts that make more than needed (e.g. 15 arrows per action). */
	private final Map<String, Long> surplus = new HashMap<>();

	private Planner(Map<String, List<Recipe>> byOutput, Map<String, Counts> stock, Options opt)
	{
		this.byOutput = byOutput;
		this.stock = stock;
		this.opt = opt;
	}

	/**
	 * @param selections recipe → number of times it will be made, in display order
	 * @param byOutput   all recipes indexed by normalized output name
	 * @param stock      item counts indexed by normalized name
	 */
	public static Plan plan(Map<Recipe, Integer> selections, Map<String, List<Recipe>> byOutput,
		Map<String, Counts> stock, Options opt)
	{
		Planner p = new Planner(byOutput, stock, opt);
		Plan plan = new Plan();

		// Sum top-level needs first so shared materials are allocated once
		Map<String, Long> need = new LinkedHashMap<>();
		Map<String, String> display = new HashMap<>();
		Set<String> selectedOutputs = new HashSet<>();
		for (Map.Entry<Recipe, Integer> e : selections.entrySet())
		{
			selectedOutputs.add(normalize(e.getKey().name));
			for (Recipe.Material m : e.getKey().materials)
			{
				String k = normalize(m.name);
				need.merge(k, (long) m.quantity * e.getValue(), Long::sum);
				display.putIfAbsent(k, m.name);
			}
		}

		for (Map.Entry<String, Long> e : need.entrySet())
		{
			Node root = p.expand(display.get(e.getKey()), e.getValue(), 0, selectedOutputs);
			plan.roots.add(root);
			if (root.subtreeShort())
			{
				plan.shortCount++;
			}
			else if (containsMake(root))
			{
				plan.toMake++;
			}
			else
			{
				plan.ready++;
			}
		}

		Map<String, ToolNeed> tools = new LinkedHashMap<>();
		for (Recipe r : selections.keySet())
		{
			addTools(tools, r);
		}
		Map<String, Long> carry = new LinkedHashMap<>();
		for (Node r : plan.roots)
		{
			p.collect(r, plan);
			p.collectExtras(r, tools, carry);
		}
		plan.tools.addAll(tools.values());
		int slots = 0;
		for (Map.Entry<String, Long> e : carry.entrySet())
		{
			slots += p.isStackable(e.getKey()) ? 1 : (int) Math.min(Integer.MAX_VALUE, e.getValue());
		}
		plan.inventorySlots = slots + plan.tools.size();
		Counts coins = stock.get("coins");
		plan.coinsAvailable = coins == null ? 0 : coins.total(opt.includeWorn, opt.includeOther);
		return plan;
	}

	private Node expand(String name, long need, int depth, Set<String> ancestry)
	{
		Node n = new Node(name, need, depth);
		long left = need;

		long s = surplus.getOrDefault(n.key, 0L);
		if (s > 0)
		{
			n.fromSurplus = Math.min(s, left);
			surplus.put(n.key, s - n.fromSurplus);
			left -= n.fromSurplus;
		}

		long[] r = remaining.computeIfAbsent(n.key, this::initialStock);
		n.fromCarried = take(r, 0, left);
		left -= n.fromCarried;
		n.fromBank = take(r, 1, left);
		left -= n.fromBank;
		n.fromOther = take(r, 2, left);
		left -= n.fromOther;

		if (left <= 0)
		{
			return n;
		}

		List<Recipe> candidates = candidates(n.key, ancestry);
		n.alternatives = candidates;
		Recipe chosen = opt.expand && depth < opt.maxDepth && !NEVER_EXPAND.contains(n.key)
			? choose(n.key, candidates) : null;

		if (chosen == null)
		{
			n.shortBy = left;
			return n;
		}

		long out = Math.max(1, chosen.outputQuantity);
		long crafts = (left + out - 1) / out;
		long extra = crafts * out - left;
		if (extra > 0)
		{
			surplus.merge(n.key, extra, Long::sum);
		}
		n.via = chosen;
		n.crafts = crafts;

		Set<String> childAncestry = new HashSet<>(ancestry);
		childAncestry.add(n.key);
		for (Recipe.Material m : chosen.materials)
		{
			n.children.add(expand(m.name, m.quantity * crafts, depth + 1, childAncestry));
		}
		return n;
	}

	private long[] initialStock(String key)
	{
		Counts c = stock.get(key);
		if (c == null)
		{
			return new long[3];
		}
		return new long[]{c.carried(opt.includeWorn), c.bank(), opt.includeOther ? c.other() : 0};
	}

	private static long take(long[] pool, int idx, long want)
	{
		if (want <= 0)
		{
			return 0;
		}
		long t = Math.min(pool[idx], want);
		pool[idx] -= t;
		return t;
	}

	/**
	 * Recipes that produce this item without needing anything already being made higher up the tree.
	 */
	private List<Recipe> candidates(String key, Set<String> ancestry)
	{
		List<Recipe> all = byOutput.getOrDefault(key, Collections.emptyList());
		List<Recipe> out = new ArrayList<>();
		for (Recipe r : all)
		{
			boolean loops = false;
			for (Recipe.Material m : r.materials)
			{
				String mk = normalize(m.name);
				if (mk.equals(key) || ancestry.contains(mk))
				{
					loops = true;
					break;
				}
			}
			if (!loops)
			{
				out.add(r);
			}
		}
		out.sort(DEFAULT_ORDER);
		return out;
	}

	/**
	 * Preference when the user hasn't picked a method: plain crafting before spells, fewer distinct
	 * ingredients, then fewer ingredients per item made.
	 */
	static final Comparator<Recipe> DEFAULT_ORDER = Comparator
		.comparing((Recipe r) -> r.usesSkill("Magic"))
		.thenComparingInt(r -> r.materials.size())
		.thenComparingDouble(r -> (double) r.totalMaterialQuantity() / Math.max(1, r.outputQuantity))
		.thenComparing(r -> r.name == null ? "" : r.name);

	private Recipe choose(String key, List<Recipe> candidates)
	{
		String pick = opt.methods.get(key);
		if (NO_MAKE.equals(pick))
		{
			return null;
		}
		if (pick != null)
		{
			for (Recipe r : candidates)
			{
				if (r.key().equals(pick))
				{
					return r;
				}
			}
		}
		if (candidates.isEmpty())
		{
			return null;
		}
		// Prefer a method whose ingredients you can actually get: owned already, or craftable.
		// Otherwise rare unlocks (sawmill coupons, vouchers) win just for being short recipes.
		Recipe best = candidates.get(0);
		int bestMissing = unobtainable(best);
		for (Recipe r : candidates)
		{
			int m = unobtainable(r);
			if (m < bestMissing)
			{
				best = r;
				bestMissing = m;
			}
		}
		return best;
	}

	/**
	 * Ingredients of {@code r} you don't own any of and can't craft.
	 */
	private int unobtainable(Recipe r)
	{
		int missing = 0;
		for (Recipe.Material m : r.materials)
		{
			String k = normalize(m.name);
			Counts c = stock.get(k);
			boolean owned = c != null && c.total(opt.includeWorn, opt.includeOther) > 0;
			if (!owned && !byOutput.containsKey(k))
			{
				missing++;
			}
		}
		return missing;
	}

	private void collect(Node n, Plan plan)
	{
		if (n.fromBank > 0)
		{
			plan.fromBank.merge(n.key, n.fromBank, Long::sum);
		}
		if (n.fromOther > 0)
		{
			plan.fromOther.merge(n.key, n.fromOther, Long::sum);
		}
		if (n.shortBy > 0)
		{
			plan.shortages.merge(n.name, n.shortBy, Long::sum);
		}
		if (n.key.equals("coins"))
		{
			plan.coinsNeeded += n.need;
		}
		for (Node c : n.children)
		{
			collect(c, plan);
		}
	}

	private void collectExtras(Node n, Map<String, ToolNeed> tools, Map<String, Long> carry)
	{
		long taken = n.fromStock();
		if (taken > 0)
		{
			carry.merge(n.name, taken, Long::sum);
		}
		if (n.via != null)
		{
			addTools(tools, n.via);
		}
		for (Node c : n.children)
		{
			collectExtras(c, tools, carry);
		}
	}

	private static void addTools(Map<String, ToolNeed> tools, Recipe r)
	{
		if (r.toolReqs == null)
		{
			return;
		}
		for (List<String> alts : r.toolReqs)
		{
			String key = normalize(String.join("|", alts));
			ToolNeed t = tools.computeIfAbsent(key, k -> new ToolNeed(alts));
			if (!t.usedBy.contains(r.displayName()))
			{
				t.usedBy.add(r.displayName());
			}
		}
	}

	private boolean isStackable(String name)
	{
		if ("coins".equals(normalize(name)))
		{
			return true;
		}
		return opt.stackable != null && opt.stackable.test(name);
	}

	/**
	 * Inventories needed to carry {@code slots} items, 28 slots each.
	 */
	public static int inventories(int slots)
	{
		return slots <= 0 ? 0 : (slots + 27) / 28;
	}

	private static boolean containsMake(Node n)
	{
		if (n.status() == Status.MAKE)
		{
			return true;
		}
		for (Node c : n.children)
		{
			if (containsMake(c))
			{
				return true;
			}
		}
		return false;
	}

	public static String normalize(String name)
	{
		return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * Index recipes by what they produce.
	 */
	public static Map<String, List<Recipe>> indexByOutput(List<Recipe> recipes)
	{
		Map<String, List<Recipe>> idx = new HashMap<>();
		for (Recipe r : recipes)
		{
			idx.computeIfAbsent(normalize(r.name), k -> new ArrayList<>()).add(r);
		}
		return idx;
	}
}
