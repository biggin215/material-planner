package com.materialplanner.recipes;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns wiki markup that leaks into bucket fields into plain text, e.g.
 * {@code <span class="plink-template">[[File:Fishing rod.png|link=Fishing rod]]</span>[[Fishing rod|Fishing rod]]}
 * becomes {@code Fishing rod}.
 */
public final class WikiText
{
	private static final Pattern FILE_LINK = Pattern.compile("\\[\\[(?:File|Image):[^\\]]*\\]\\]", Pattern.CASE_INSENSITIVE);
	private static final Pattern PIPED_LINK = Pattern.compile("\\[\\[[^\\]|]*\\|([^\\]]*)\\]\\]");
	private static final Pattern PLAIN_LINK = Pattern.compile("\\[\\[([^\\]]*)\\]\\]");
	private static final Pattern TEMPLATE = Pattern.compile("\\{\\{[^}]*\\}\\}");
	private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
	private static final Pattern REF = Pattern.compile("<ref[^>]*>.*?</ref>|<ref[^>]*/>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	private static final Pattern SPACES = Pattern.compile("\\s+");
	private static final Pattern OR_SPLIT = Pattern.compile("\\s+or\\s+|\\s*/\\s*", Pattern.CASE_INSENSITIVE);
	private static final Pattern OPTIONAL = Pattern.compile("\\(\\s*optional\\s*\\)", Pattern.CASE_INSENSITIVE);

	private WikiText()
	{
	}

	public static String clean(String s)
	{
		if (s == null)
		{
			return null;
		}
		String t = REF.matcher(s).replaceAll("");
		t = FILE_LINK.matcher(t).replaceAll("");
		t = replaceGroup(PIPED_LINK, t);
		t = replaceGroup(PLAIN_LINK, t);
		t = TEMPLATE.matcher(t).replaceAll("");
		t = HTML_TAG.matcher(t).replaceAll("");
		t = t.replace("&nbsp;", " ").replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"");
		t = t.replace("'''", "").replace("''", "");
		return SPACES.matcher(t).replaceAll(" ").trim();
	}

	private static String replaceGroup(Pattern p, String s)
	{
		Matcher m = p.matcher(s);
		StringBuffer sb = new StringBuffer();
		while (m.find())
		{
			m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1)));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	/**
	 * Parses a tools field like "Saw, Hammer" or "Butterfly net or Magic butterfly net" into requirements,
	 * each a list of acceptable alternatives. Optional tools and "None" are dropped.
	 */
	public static List<List<String>> toolRequirements(String raw)
	{
		List<List<String>> out = new ArrayList<>();
		if (raw == null)
		{
			return out;
		}
		// Split on commas outside of wiki links, then clean each piece
		for (String part : splitTopLevel(raw))
		{
			if (OPTIONAL.matcher(part).find())
			{
				continue;
			}
			String cleaned = clean(part);
			if (cleaned == null || cleaned.isEmpty() || cleaned.equalsIgnoreCase("none"))
			{
				continue;
			}
			List<String> alts = new ArrayList<>();
			for (String alt : OR_SPLIT.split(cleaned))
			{
				String a = alt.trim();
				if (!a.isEmpty() && !a.equalsIgnoreCase("none"))
				{
					alts.add(a);
				}
			}
			if (!alts.isEmpty())
			{
				out.add(alts);
			}
		}
		return out;
	}

	private static List<String> splitTopLevel(String s)
	{
		List<String> parts = new ArrayList<>();
		int depth = 0;
		StringBuilder cur = new StringBuilder();
		for (int i = 0; i < s.length(); i++)
		{
			char c = s.charAt(i);
			if (c == '[' || c == '{' || c == '<')
			{
				depth++;
			}
			else if ((c == ']' || c == '}' || c == '>') && depth > 0)
			{
				depth--;
			}
			if (c == ',' && depth == 0)
			{
				parts.add(cur.toString());
				cur.setLength(0);
			}
			else
			{
				cur.append(c);
			}
		}
		parts.add(cur.toString());
		return parts;
	}
}
