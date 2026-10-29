package com.materialplanner.recipes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Works out whether a player can reach a shop and ranks the ones they can.
 *
 * The wiki's shop location text ("Prifddinas", "Keldagrim") is matched against a hand-kept list of areas
 * that sit behind a quest or a skill level. Unlisted places are assumed open.
 */
public final class ShopAccess
{
	/** What we know about the player; any value may be unknown. */
	public interface Player
	{
		/** null when unknown. */
		Boolean membersWorld();

		/** Quests named in the text that the player hasn't done (partial completion accepted where the text says so). */
		List<String> unmetQuests(String requirementText);

		/** Real level, or 0 or less when unknown. */
		int realLevel(String skill);

		/** {x, y, plane} or null when not logged in. */
		int[] location();
	}

	private static final class Rule
	{
		final String[] places;
		final String quest;
		final String skill;
		final int level;

		Rule(String quest, String skill, int level, String... places)
		{
			this.places = places;
			this.quest = quest;
			this.skill = skill;
			this.level = level;
		}
	}

	/**
	 * Quest names follow RuneLite's quest list. "partial" means starting the quest is enough to get in.
	 */
	private static final List<Rule> RULES = java.util.Arrays.asList(
		new Rule("Song of the Elves", null, 0, "prifddinas"),
		new Rule("Partial completion of Mourning's End Part I", null, 0, "lletya"),
		new Rule("Underground Pass", null, 0, "tirannwn", "isafdar", "elf camp", "lletya", "prifddinas"),
		new Rule("Lost City", null, 0, "zanaris"),
		new Rule("Partial completion of The Giant Dwarf", null, 0, "keldagrim"),
		new Rule("Monkey Madness I", null, 0, "ape atoll", "marim"),
		new Rule("Cabin Fever", null, 0, "mos le'harmless"),
		new Rule("Lunar Diplomacy", null, 0, "lunar isle"),
		new Rule("Partial completion of The Fremennik Isles", null, 0, "neitiznot", "jatizso"),
		new Rule("Partial completion of Throne of Miscellania", null, 0, "miscellania", "etceteria"),
		new Rule("The Fremennik Trials", null, 0, "rellekka", "waterbirth"),
		new Rule("Priest in Peril", null, 0, "morytania", "canifis", "port phasmatys", "mort'ton", "burgh de rott",
			"darkmeyer", "meiyerditch", "slepe"),
		new Rule("Partial completion of In Aid of the Myreque", null, 0, "burgh de rott"),
		new Rule("Sins of the Father", null, 0, "darkmeyer", "slepe"),
		new Rule("Partial completion of Icthlarin's Little Helper", null, 0, "sophanem"),
		new Rule("Bone Voyage", null, 0, "fossil island"),
		new Rule("Shilo Village", null, 0, "shilo village"),
		new Rule("Death to the Dorgeshuun", null, 0, "dorgesh-kaan"),
		new Rule("Dragon Slayer II", null, 0, "myths' guild"),
		new Rule("Heroes' Quest", null, 0, "heroes' guild"),
		new Rule("Legends' Quest", null, 0, "legends' guild"),
		new Rule(null, "Crafting", 40, "crafting guild"),
		new Rule(null, "Cooking", 32, "cooks' guild", "cooking guild"),
		new Rule(null, "Fishing", 68, "fishing guild"),
		new Rule(null, "Mining", 60, "mining guild"),
		new Rule(null, "Woodcutting", 60, "woodcutting guild"),
		new Rule(null, "Farming", 45, "farming guild"),
		new Rule(null, "Ranged", 40, "ranging guild"),
		new Rule(null, "Hunter", 46, "hunter guild", "hunters' guild")
	);

	private ShopAccess()
	{
	}

	/**
	 * Why the player can't use this shop, or an empty list when nothing is known to stop them.
	 */
	public static List<String> blockers(ShopSource s, Player p)
	{
		Set<String> out = new LinkedHashSet<>();
		if (s.members && Boolean.FALSE.equals(p.membersWorld()))
		{
			out.add("members");
		}
		String where = ((s.location == null ? "" : s.location) + " " + (s.store == null ? "" : s.store))
			.toLowerCase(Locale.ROOT);
		for (Rule r : RULES)
		{
			if (!mentions(where, r.places))
			{
				continue;
			}
			if (r.quest != null)
			{
				out.addAll(p.unmetQuests(r.quest));
			}
			if (r.skill != null)
			{
				int lvl = p.realLevel(r.skill);
				if (lvl > 0 && lvl < r.level)
				{
					out.add(r.level + " " + r.skill);
				}
			}
		}
		return new ArrayList<>(out);
	}

	private static boolean mentions(String where, String[] places)
	{
		for (String place : places)
		{
			if (where.contains(place))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Approximate tiles between the player and the shop's nearest known spot, or -1 when either is unknown.
	 * Underground areas sit 6400 tiles north of the surface they're under, so they're folded back first.
	 */
	public static int distance(ShopSource s, int[] player)
	{
		if (player == null)
		{
			return -1;
		}
		List<int[]> spots = s.coords;
		if (spots == null || spots.isEmpty())
		{
			// Most single-spawn shopkeepers have no map spots on the wiki; use the town instead
			int[] town = ShopPlaces.find(s.location, s.store);
			if (town == null)
			{
				return -1;
			}
			spots = java.util.Collections.singletonList(town);
		}
		int best = -1;
		for (int[] c : spots)
		{
			if (c == null || c.length < 2)
			{
				continue;
			}
			int d = Math.max(Math.abs(c[0] - player[0]), Math.abs(surfaceY(c[1]) - surfaceY(player[1])));
			if (best < 0 || d < best)
			{
				best = d;
			}
		}
		return best;
	}

	private static int surfaceY(int y)
	{
		return y >= 6400 ? y - 6400 : y;
	}

	/** The shops worth showing for an item. */
	public static final class Pick
	{
		/** Cheapest reachable coin shop, nearest first among equal prices; null when none. */
		public ShopSource cheapest;
		/** Nearest reachable coin shop when it isn't the cheapest one; otherwise null. */
		public ShopSource nearest;
		/** Shops that sell it but can't be used yet, each with the reason. */
		public final List<String> locked = new ArrayList<>();
	}

	public static Pick pick(List<ShopSource> shops, Player p)
	{
		Pick pick = new Pick();
		int[] here = p.location();
		List<ShopSource> open = new ArrayList<>();
		for (ShopSource s : shops)
		{
			if (!s.isCoins() || s.price < 0)
			{
				continue;
			}
			List<String> why = blockers(s, p);
			if (why.isEmpty())
			{
				open.add(s);
			}
			else
			{
				pick.locked.add(s.store + " (needs " + String.join(", ", why) + ")");
			}
		}
		if (open.isEmpty())
		{
			return pick;
		}
		Comparator<ShopSource> byDistance = Comparator.comparingInt(s ->
		{
			int d = distance(s, here);
			return d < 0 ? Integer.MAX_VALUE : d;
		});
		open.sort(Comparator.comparingInt((ShopSource s) -> s.price).thenComparing(byDistance));
		pick.cheapest = open.get(0);
		ShopSource near = open.stream().min(byDistance).orElse(null);
		if (near != null && near != pick.cheapest && distance(near, here) >= 0)
		{
			pick.nearest = near;
		}
		return pick;
	}

	/**
	 * Pulls coordinates out of wiki location text: "x:3210,y:3424" pairs, or bare "3210,3424" pairs.
	 */
	public static List<int[]> parseCoordinates(String raw)
	{
		List<int[]> out = new ArrayList<>();
		if (raw == null)
		{
			return out;
		}
		java.util.regex.Matcher kv = java.util.regex.Pattern.compile("x\\s*[:=]\\s*(\\d{3,5})\\s*,\\s*y\\s*[:=]\\s*(\\d{3,5})")
			.matcher(raw);
		while (kv.find())
		{
			out.add(new int[]{Integer.parseInt(kv.group(1)), Integer.parseInt(kv.group(2)), 0});
		}
		if (out.isEmpty())
		{
			java.util.regex.Matcher bare = java.util.regex.Pattern.compile("(?<![\\d.])(\\d{3,5})\\s*,\\s*(\\d{3,5})(?![\\d.])")
				.matcher(raw);
			while (bare.find())
			{
				out.add(new int[]{Integer.parseInt(bare.group(1)), Integer.parseInt(bare.group(2)), 0});
			}
		}
		return out;
	}
}
