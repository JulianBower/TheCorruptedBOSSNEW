package com.hellgauntlet.boss;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@Mod(TheCorruptedMod.MODID)
public class TheCorruptedMod {
    public static final String MODID = "hellgauntletboss";

    public TheCorruptedMod(IEventBus modBus) {
        ModEffects.EFFECTS.register(modBus);
        ModEntities.ENTITY_TYPES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModParticles.PARTICLES.register(modBus);
        ModArmorMaterials.ARMOR_MATERIALS.register(modBus);

        modBus.addListener(TheCorruptedMod::onAttributes);
        modBus.addListener(TheCorruptedMod::onSpawnPlacements);

        NeoForge.EVENT_BUS.addListener(CorruptedBlasts::onLevelTick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> CorruptedBlasts.clear());
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.THE_CORRUPTED.get(), TheCorruptedBossEntity.createAttributes().build());
        event.put(ModEntities.LITTLE_DEMON.get(), LittleDemonEntity.createAttributes().build());
    }

    /** Little Demons spawn on solid ground (not in lava) in the Nether biomes listed by the biome modifier. */
    private static void onSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(ModEntities.LITTLE_DEMON.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> level.getBlockState(pos.below()).isValidSpawn(level, pos.below(), type)
                        && level.getBlockState(pos).getFluidState().isEmpty(),
                RegisterSpawnPlacementsEvent.Operation.OR);
    }
}
