package com.materialplanner.recipes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rough map positions for towns and areas, used to rank shops by distance when the wiki has no map
 * spots for the shop or its owner (most single-spawn shopkeepers).
 *
 * Positions are approximate centres on the surface. Places reached from somewhere else (Zanaris,
 * Keldagrim, Dorgesh-Kaan, Prifddinas) use the place you travel in from, so "nearest" reflects the trip.
 */
final class ShopPlaces
{
	private static final Map<String, int[]> PLACES = new LinkedHashMap<>();

	private static void put(String name, int x, int y)
	{
		PLACES.put(name, new int[]{x, y, 0});
	}

	static
	{
		// Misthalin and Asgarnia
		put("lumbridge swamp", 3200, 3170);
		put("lumbridge", 3222, 3218);
		put("draynor manor", 3108, 3352);
		put("draynor", 3093, 3244);
		put("wizards' tower", 3109, 3162);
		put("port sarim", 3023, 3208);
		put("rimmington", 2957, 3214);
		put("crafting guild", 2933, 3285);
		put("falador", 2965, 3380);
		put("mining guild", 3046, 3356);
		put("dwarven mine", 3040, 3370);
		put("ice mountain", 3008, 3480);
		put("goblin village", 2956, 3504);
		put("barbarian village", 3082, 3420);
		put("edgeville", 3094, 3491);
		put("grand exchange", 3164, 3487);
		put("cooks' guild", 3143, 3443);
		put("champions' guild", 3191, 3363);
		put("varrock", 3212, 3424);
		put("al kharid", 3293, 3174);
		put("pvp arena", 3360, 3240);
		put("emir's arena", 3360, 3240);
		put("duel arena", 3360, 3240);
		put("mage training arena", 3363, 3302);
		put("taverley", 2895, 3456);
		put("heroes' guild", 2900, 3510);
		put("burthorpe", 2899, 3544);
		put("warriors' guild", 2868, 3546);
		put("entrana", 2834, 3335);
		put("dorgesh-kaan", 3209, 3216);
		put("zanaris", 3202, 3169);

		// Kandarin and the north
		put("catherby", 2813, 3447);
		put("seers' village", 2725, 3485);
		put("camelot", 2757, 3477);
		put("ranging guild", 2657, 3439);
		put("legends' guild", 2729, 3349);
		put("fishing guild", 2611, 3393);
		put("east ardougne", 2662, 3305);
		put("west ardougne", 2530, 3305);
		put("ardougne", 2662, 3305);
		put("witchaven", 2720, 3285);
		put("port khazard", 2662, 3160);
		put("yanille", 2606, 3093);
		put("castle wars", 2442, 3090);
		put("tree gnome village", 2526, 3165);
		put("grand tree", 2465, 3495);
		put("gnome stronghold", 2461, 3444);
		put("barbarian outpost", 2530, 3570);
		put("lighthouse", 2510, 3635);
		put("piscatoris", 2340, 3650);
		put("keldagrim", 2730, 3713);
		put("rellekka", 2660, 3657);
		put("waterbirth", 2545, 3760);
		put("trollheim", 2890, 3675);
		put("weiss", 2860, 3940);
		put("neitiznot", 2335, 3800);
		put("jatizso", 2400, 3800);
		put("miscellania", 2535, 3860);
		put("etceteria", 2615, 3870);
		put("lunar isle", 2100, 3900);
		put("ourania", 2453, 3231);
		put("gu'tanoth", 2520, 3040);
		put("feldip hills", 2560, 2970);
		put("oo'glog", 2560, 2850);
		put("corsair cove", 2570, 2860);
		put("myths' guild", 2458, 2850);

		// Karamja
		put("brimhaven", 2760, 3178);
		put("musa point", 2919, 3170);
		put("tzhaar", 2860, 3168);
		put("mor ul rek", 2860, 3168);
		put("tai bwo wannai", 2790, 3065);
		put("shilo village", 2852, 2955);
		put("ape atoll", 2760, 2780);
		put("marim", 2760, 2780);
		put("karamja", 2850, 3100);

		// Desert
		put("shantay pass", 3304, 3117);
		put("bedabin", 3170, 3030);
		put("pollnivneach", 3359, 2970);
		put("nardah", 3425, 2915);
		put("uzer", 3490, 3090);
		put("ruins of unkah", 3170, 2840);
		put("sophanem", 3293, 2780);
		put("menaphos", 3233, 2770);
		put("kharidian desert", 3300, 2950);

		// Morytania and beyond
		put("canifis", 3496, 3488);
		put("port phasmatys", 3687, 3502);
		put("mort'ton", 3489, 3288);
		put("burgh de rott", 3496, 3210);
		put("darkmeyer", 3620, 3365);
		put("slepe", 3708, 3335);
		put("mos le'harmless", 3680, 2970);
		put("fossil island", 3730, 3800);
		put("morytania", 3500, 3400);

		// Tirannwn
		put("prifddinas", 2240, 3270);
		put("lletya", 2341, 3172);
		put("port tyras", 2150, 3120);
		put("elf camp", 2200, 3250);
		put("isafdar", 2250, 3200);
		put("tirannwn", 2250, 3200);

		// Wilderness
		put("ferox enclave", 3130, 3630);
		put("mage arena", 3105, 3935);
		put("wilderness", 3100, 3700);

		// Great Kourend and Kebos
		put("hosidius", 1744, 3517);
		put("shayzien", 1504, 3615);
		put("lovakengj", 1504, 3828);
		put("arceuus", 1690, 3742);
		put("port piscarilius", 1803, 3748);
		put("piscarilius", 1803, 3748);
		put("woodcutting guild", 1600, 3500);
		put("farming guild", 1250, 3740);
		put("mount karuulm", 1310, 3800);
		put("kourend", 1640, 3670);

		// Varlamore
		put("hunter guild", 1560, 3040);
		put("hunters' guild", 1560, 3040);
		put("civitas illa fortis", 1680, 3130);
		put("cam torum", 1440, 3160);
		put("aldarin", 1390, 2930);
		put("varlamore", 1680, 3130);
	}

	/** Longest names first, so "east ardougne" wins over "ardougne". */
	private static final List<String> BY_LENGTH = new ArrayList<>(PLACES.keySet());

	static
	{
		BY_LENGTH.sort(Comparator.comparingInt(String::length).reversed());
	}

	private ShopPlaces()
	{
	}

	/**
	 * The rough position of the first known place named in the texts (checked in order), or null.
	 */
	static int[] find(String... texts)
	{
		for (String text : texts)
		{
			if (text == null || text.isEmpty())
			{
				continue;
			}
			String lower = text.toLowerCase(Locale.ROOT);
			for (String place : BY_LENGTH)
			{
				if (lower.contains(place))
				{
					return PLACES.get(place);
				}
			}
		}
		return null;
	}
}
