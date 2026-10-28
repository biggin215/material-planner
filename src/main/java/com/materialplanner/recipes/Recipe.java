package com.materialplanner.recipes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A single way to make something: what it produces, what it needs, and which skills.
 * Field names are the on-disk cache / seed file format.
 */
public class Recipe
{
	public static class Material
	{
		public String name;
		public int quantity;

		public Material()
		{
		}

		public Material(String name, int quantity)
		{
			this.name = name;
			this.quantity = quantity;
		}
	}

	public static class SkillReq
	{
		public String name;
		public int level;
		/** XP gained per action, 0 when the wiki has none. */
		public double experience;

		public SkillReq()
		{
		}

		public SkillReq(String name, int level)
		{
			this.name = name;
			this.level = level;
		}

		public SkillReq(String name, int level, double experience)
		{
			this(name, level);
			this.experience = experience;
		}
	}

	public String name;
	public int outputQuantity = 1;
	public List<SkillReq> skills = new ArrayList<>();
	public List<Material> materials = new ArrayList<>();
	public String facility;
	/** Tools as shown to the player, e.g. "Saw, Hammer". */
	public String tools;
	/** Each required tool, as a list of acceptable alternatives. */
	public List<List<String>> toolReqs = new ArrayList<>();
	/** Quest or other unlock text from the wiki, or null. */
	public String quest;
	public boolean members;
	public String source;

	/**
	 * Display-only, set at load time (transient: not written to the cache).
	 * variant: e.g. "Sloop" when the wiki lists several same-named recipes.
	 * showMaterials: same-named recipes we couldn't label; show their materials so they can be told apart.
	 */
	public transient String variant;
	public transient boolean showMaterials;

	public String displayName()
	{
		return variant == null ? name : name + " (" + variant + ")";
	}

	public int totalMaterialQuantity()
	{
		int sum = 0;
		for (Material m : materials)
		{
			sum += m.quantity;
		}
		return sum;
	}

	public String materialsText()
	{
		StringBuilder sb = new StringBuilder();
		for (Material m : materials)
		{
			if (sb.length() > 0)
			{
				sb.append(", ");
			}
			sb.append(m.name).append(" x").append(m.quantity);
		}
		return sb.toString();
	}

	/**
	 * Second line shown under the name in lists.
	 */
	public String subtitle()
	{
		return showMaterials ? materialsText() : requirementsText();
	}

	/**
	 * Stable identity used to persist the user's selections across restarts and data refreshes.
	 */
	public String key()
	{
		StringBuilder sb = new StringBuilder(name == null ? "" : name).append('|');
		if (facility != null)
		{
			sb.append(facility);
		}
		for (Material m : materials)
		{
			sb.append('|').append(m.name).append('x').append(m.quantity);
		}
		return sb.toString();
	}

	/**
	 * The skill this recipe trains, for level goals: the first skill that gives XP.
	 */
	public SkillReq xpSkill()
	{
		for (SkillReq s : skills)
		{
			if (s.experience > 0)
			{
				return s;
			}
		}
		return null;
	}

	public boolean usesSkill(String skill)
	{
		for (SkillReq s : skills)
		{
			if (s.name != null && s.name.equalsIgnoreCase(skill))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * One-line summary of requirements, e.g. "Sailing 50, Construction 26 · Shipyard".
	 */
	public String requirementsText()
	{
		StringBuilder sb = new StringBuilder();
		for (SkillReq s : skills)
		{
			if (sb.length() > 0)
			{
				sb.append(", ");
			}
			sb.append(s.name);
			if (s.level > 0)
			{
				sb.append(' ').append(s.level);
			}
		}
		if (facility != null && !facility.isEmpty())
		{
			if (sb.length() > 0)
			{
				sb.append(" · ");
			}
			sb.append(facility);
		}
		return sb.toString();
	}

	@Override
	public String toString()
	{
		return outputQuantity > 1 ? displayName() + " (x" + outputQuantity + ")" : displayName();
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof Recipe && Objects.equals(key(), ((Recipe) o).key());
	}

	@Override
	public int hashCode()
	{
		return key().hashCode();
	}
}
