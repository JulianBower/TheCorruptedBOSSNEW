# The Corrupted (hellgauntletboss) - NeoForge 1.21.1

Open this folder in IntelliJ / VS Code as a Gradle project (ModDevGradle) or run `gradle build` with Gradle 8.10+ and JDK 21.
`gradle wrapper` creates the wrapper if you want one. Adjust `neo_version` in `gradle.properties` to the NeoForge 21.1.x you use.

Textures were generated with Pillow (see `tools/ (run `python3 paint.py` then `python3 misc.py` inside tools/; needs Pillow + numpy)`); the model quads in
`client/CorruptedMesh.java` are baked from `tools/prototype_the_corrupted.bbmodel`.

Spawn: `/summon hellgauntletboss:the_corrupted` or the spawn egg in the "The Corrupted" creative tab.

## Knight armor
Steel / Corrupted Knight armor is enchantable (enchantability 15 / 18; all 8 pieces are in the `minecraft:enchantable/*` item tags).
Worn pieces use the boss atlas through `KnightArmorItem#getArmorTexture` (`the_corrupted_normal` for Steel, `the_corrupted_awakened` for Corrupted; there are no `*_layer_N.png` files), and `KnightArmorLayer` adds the full-bright `the_corrupted_glow` pass on Corrupted pieces. Regenerate the armor mesh with `tools/gen_armor.py`.

## Little Demon and enchantments
`little_demon` is a Nether trader (spawns rarely in nether_wastes, crimson_forest, basalt_deltas via `neoforge/biome_modifier`) selling 4-5 of the 7 custom enchanted books for gold ingots, blaze rods, netherite scraps or steel ingots.
Enchantments are data-driven (`data/hellgauntletboss/enchantment`); effects are in `BossEnchantments`. The Hellgauntlet ones apply to `hellgauntlet:gauntlet_of_hell` through `tags/item/enchantable/hellgauntlet.json`. Texture script: `tools/paint_demon.py <outdir>`.

## Sovereign set, Abyssal Storm Gauntlet, Abyssal Corruption
- Sovereign Knight armor (+6 armor over Corrupted): smithing Corrupted piece + Nether Star + netherite upgrade template. Parries every 2nd hit; every 2nd melee hit applies Abyssal Corruption (3 hearts/sec true damage, blue lightning).
- Abyssal Storm Gauntlet: smith the Gauntlet of Hell (requires the hellgauntlet mod) with a Nether Star. 8 hearts melee, accepts the gauntlet enchantments, 2x blast, true-damage blue chain lightning.
