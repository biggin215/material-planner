package com.materialplanner.recipes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ShopAccessTest
{
	/** A player who has finished the listed quests and stands at a fixed spot. */
	private static ShopAccess.Player player(boolean members, int[] at, int craftingLevel, String... done)
	{
		Set<String> finished = new HashSet<>(Arrays.asList(done));
		return new ShopAccess.Player()
		{
			@Override
			public Boolean membersWorld()
			{
				return members;
			}

			@Override
			public List<String> unmetQuests(String text)
			{
				// Stand-in for the plugin's matcher: the whole text after an optional "Partial completion of"
				String quest = text.replace("Partial completion of ", "");
				return finished.contains(quest) ? Collections.emptyList() : Collections.singletonList(quest);
			}

			@Override
			public int realLevel(String skill)
			{
				return skill.equals("Crafting") ? craftingLevel : 99;
			}

			@Override
			public int[] location()
			{
				return at;
			}
		};
	}

	private static ShopSource shop(String name, String location, int price, boolean members, int x, int y)
	{
		ShopSource s = new ShopSource();
		s.item = "Mahogany logs";
		s.store = name;
		s.location = location;
		s.price = price;
		s.members = members;
		s.coords = new ArrayList<>();
		s.coords.add(new int[]{x, y, 0});
		return s;
	}

	@Test
	public void gatedAreasNeedTheirQuest()
	{
		ShopSource prif = shop("Prifddinas general store", "Prifddinas, Tirannwn", 10, true, 3260, 6080);
		assertEquals(Arrays.asList("Song of the Elves", "Underground Pass"),
			ShopAccess.blockers(prif, player(true, null, 99)));
		assertTrue(ShopAccess.blockers(prif, player(true, null, 99, "Song of the Elves", "Underground Pass")).isEmpty());
	}

	@Test
	public void membersShopsAreLockedOnFreeWorlds()
	{
		ShopSource s = shop("Lumber yard", "Varrock", 10, true, 3300, 3490);
		assertEquals(Collections.singletonList("members"), ShopAccess.blockers(s, player(false, null, 99)));
		assertTrue(ShopAccess.blockers(s, player(true, null, 99)).isEmpty());
	}

	@Test
	public void guildsNeedTheirLevel()
	{
		ShopSource s = shop("Crafting Guild store", "Crafting Guild", 10, true, 2933, 3285);
		assertEquals(Collections.singletonList("40 Crafting"), ShopAccess.blockers(s, player(true, null, 30)));
		assertTrue(ShopAccess.blockers(s, player(true, null, 40)).isEmpty());
	}

	@Test
	public void picksCheapestReachableAndNearest()
	{
		ShopSource cheapLocked = shop("Prif store", "Prifddinas", 5, true, 3260, 6080);
		ShopSource cheap = shop("Far store", "Ardougne", 10, true, 2660, 3300);
		ShopSource near = shop("Near store", "Varrock", 12, true, 3210, 3420);
		ShopAccess.Pick pick = ShopAccess.pick(Arrays.asList(cheapLocked, cheap, near), player(true, new int[]{3212, 3425, 0}, 99));
		assertSame(cheap, pick.cheapest);
		assertSame(near, pick.nearest);
		assertEquals(1, pick.locked.size());
		assertTrue(pick.locked.get(0).startsWith("Prif store (needs "));
	}

	@Test
	public void nearestIsHiddenWhenItIsAlsoCheapest()
	{
		ShopSource a = shop("Near store", "Varrock", 10, true, 3210, 3420);
		ShopSource b = shop("Far store", "Ardougne", 10, true, 2660, 3300);
		ShopAccess.Pick pick = ShopAccess.pick(Arrays.asList(b, a), player(true, new int[]{3212, 3425, 0}, 99));
		assertSame(a, pick.cheapest);
		assertNull(pick.nearest);
	}

	@Test
	public void undergroundSpotsFoldBackToTheSurface()
	{
		ShopSource dungeon = shop("Dungeon store", "Varrock Sewers", 10, false, 3210, 9820);
		assertEquals(5, ShopAccess.distance(dungeon, new int[]{3210, 3425, 0}));
	}

	@Test
	public void parsesWikiCoordinates()
	{
		List<int[]> kv = ShopAccess.parseCoordinates("desc:Zaff,x:3203,y:3434|x:3204,y:3435");
		assertEquals(2, kv.size());
		assertEquals(3203, kv.get(0)[0]);
		assertEquals(3434, kv.get(0)[1]);
		List<int[]> bare = ShopAccess.parseCoordinates("3203,3434");
		assertEquals(1, bare.size());
		assertEquals(3434, bare.get(0)[1]);
		assertTrue(ShopAccess.parseCoordinates("no spots here").isEmpty());
	}

	@Test
	public void escapesLuaStrings()
	{
		assertEquals("Aubury\\'s Rune Shop", WikiText.lua("Aubury's Rune Shop"));
	}
}
