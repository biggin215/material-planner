# Material Planner (RuneLite plugin)

Plan what you need for anything you can make in OSRS, counting what you already own across your bank and other storage.

## Recipes tab

- Type in the search box for a live dropdown, or choose a skill to browse everything in that skill.
- Click a recipe to add it, and set how many times you'll make it. Add as many recipes as you like; materials are combined.
- **Sub-recipes are planned automatically.** If you're missing an ingredient that can be crafted, the plugin plans its ingredients too, all the way down to raw materials. Stock you already have is used first at every level, and is shared across the whole plan so nothing is counted twice.
- **Choose how things are made.** Right-click any material to pick a different method (for example sawmill vs. Plank Make), or to say you'll gather or buy it instead. Your choices are remembered.
- Each material is color-coded:
  - **green** – enough on you now
  - **orange** – enough once you withdraw from the bank or other storage
  - **blue** – will be crafted (expand the arrow to see its ingredients)
  - **red** – short, with the exact amount still needed
- **Level checks** flag recipes and crafting steps your levels are too low for. Optionally hide those recipes from search.
- **Costs**: coins the plan needs (sawmill fees and similar) plus, optionally, the GE price of everything you're short. "Auto" shows coins only on ironman accounts.
- **Bank highlighting**: items the plan needs from the bank are outlined when you open it.

## Storage tab

The plugin records each of these whenever you open or change it, per account:

| Storage | Recorded when |
|---|---|
| Bank | You open the bank |
| Seed vault | You open the seed vault |
| Looting bag | You check or use the looting bag |
| Seed box | You check or use the seed box |
| Group storage | You open group storage (group ironman) |
| Boat cargo holds 1–5 | You open a boat's cargo hold |
| Rune pouch | Always, while the pouch is in your inventory |

View one storage at a time or all together, and search across them. Right-click any item in your inventory or on the ground and choose **Check bank** to see how many you have stored.

## Where the recipes come from

On first start the plugin downloads the OSRS Wiki's recipe database (the data behind the wiki's skill calculators) and caches it in the plugin's data folder (`~/.runelite/plugin-data/material-planner/`). It re-downloads every 7 days by default, or on demand with the ↻ button. Recipe data © OSRS Wiki contributors, CC BY-NC-SA 3.0.

The plugin also ships built-in Sailing shipyard recipes (all hulls and mast-and-sails for raft, skiff and sloop), since those vary by boat type.

## Running it

Requires JDK 11+.

```
./gradlew run          (Windows PowerShell: .\gradlew.bat run)
```

## Settings

| Section | Setting | Default |
|---|---|---|
| Planner | Expand sub-recipes | on |
| Planner | Show costs | Auto (ironman: coins only) |
| Planner | Check levels | on |
| Planner | Hide recipes above my level | off |
| Planner | Highlight in bank / Highlight color | on / orange |
| Storage | Count equipped items | on |
| Storage | Count other storage | on |
| Storage | 'Check bank' menu option | on |
| Recipe data | Download wiki recipes | on |
| Recipe data | Recipe refresh (days) | 7 |

## Known limits

- Stored counts are only as fresh as the last time you opened that storage.
- Materials are matched by item name, so an item whose wiki name differs from its in-game name won't match.
- When the wiki lists three same-named Sailing recipes, they're labelled Raft/Skiff/Sloop by material count. That's inferred, not read from the data.
- POH costume room storage isn't tracked.
