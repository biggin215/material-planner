package com.materialplanner.recipes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts one row of the OSRS Wiki "recipe" bucket (its production_json field) into a {@link Recipe}.
 * <p>
 * The wiki's format is loosely typed (numbers are often strings, single values are sometimes not
 * wrapped in arrays), so every field is read defensively and anything unexpected is skipped.
 */
final class WikiRecipeParser
{
	private static final Pattern LEADING_INT = Pattern.compile("(\\d[\\d,]*)");

	private WikiRecipeParser()
	{
	}

	static Recipe parse(JsonElement productionJson, String pageName)
	{
		JsonObject o = asObject(productionJson);
		if (o == null)
		{
			return null;
		}

		Recipe r = new Recipe();
		r.source = "wiki";

		JsonObject output = asObject(o.get("output"));
		if (output != null)
		{
			r.name = str(output.get("name"));
			r.outputQuantity = Math.max(1, num(output.get("quantity"), 1));
		}
		if (r.name == null || r.name.isEmpty())
		{
			r.name = pageName;
		}
		if (r.name == null || r.name.isEmpty())
		{
			return null;
		}
		// Wiki page names can carry a variant suffix after '#'
		r.name = r.name.replace('#', ' ').trim();

		for (JsonElement m : asArray(o.get("materials")))
		{
			JsonObject mo = asObject(m);
			if (mo == null)
			{
				continue;
			}
			String name = str(mo.get("name"));
			if (name == null || name.isEmpty())
			{
				continue;
			}
			r.materials.add(new Recipe.Material(name, Math.max(1, num(mo.get("quantity"), 1))));
		}
		if (r.materials.isEmpty())
		{
			// Nothing to plan for (e.g. pure gathering actions)
			return null;
		}

		for (JsonElement s : asArray(o.get("skills")))
		{
			JsonObject so = asObject(s);
			if (so == null)
			{
				continue;
			}
			String name = str(so.get("name"));
			if (name != null && !name.isEmpty())
			{
				r.skills.add(new Recipe.SkillReq(capitalize(name), num(so.get("level"), 0)));
			}
		}

		r.facility = joinish(o.get("facilities"));
		r.tools = joinish(o.get("tools"));
		String members = str(o.get("members"));
		r.members = members == null || !members.equalsIgnoreCase("no");
		return r;
	}

	private static JsonObject asObject(JsonElement e)
	{
		if (e == null || e.isJsonNull())
		{
			return null;
		}
		if (e.isJsonObject())
		{
			return e.getAsJsonObject();
		}
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString())
		{
			try
			{
				// RuneLite ships Gson 2.8.5, which predates the static JsonParser.parseString
				@SuppressWarnings("deprecation")
				JsonElement p = new JsonParser().parse(e.getAsString());
				return p.isJsonObject() ? p.getAsJsonObject() : null;
			}
			catch (RuntimeException ex)
			{
				return null;
			}
		}
		return null;
	}

	private static JsonArray asArray(JsonElement e)
	{
		JsonArray a = new JsonArray();
		if (e == null || e.isJsonNull())
		{
			return a;
		}
		if (e.isJsonArray())
		{
			return e.getAsJsonArray();
		}
		a.add(e);
		return a;
	}

	private static String str(JsonElement e)
	{
		if (e == null || e.isJsonNull())
		{
			return null;
		}
		if (e.isJsonPrimitive())
		{
			return e.getAsString().trim();
		}
		return null;
	}

	private static String joinish(JsonElement e)
	{
		StringBuilder sb = new StringBuilder();
		for (JsonElement x : asArray(e))
		{
			String s = str(x);
			if (s != null && !s.isEmpty())
			{
				if (sb.length() > 0)
				{
					sb.append(", ");
				}
				sb.append(s);
			}
		}
		return sb.length() == 0 ? null : sb.toString();
	}

	static int num(JsonElement e, int def)
	{
		String s = str(e);
		if (s == null)
		{
			return def;
		}
		Matcher m = LEADING_INT.matcher(s);
		if (!m.find())
		{
			return def;
		}
		try
		{
			return Integer.parseInt(m.group(1).replace(",", ""));
		}
		catch (NumberFormatException ex)
		{
			return def;
		}
	}

	private static String capitalize(String s)
	{
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
