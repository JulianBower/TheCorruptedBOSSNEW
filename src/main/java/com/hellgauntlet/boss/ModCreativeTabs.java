package com.hellgauntlet.boss;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TheCorruptedMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + TheCorruptedMod.MODID))
                    .icon(() -> new ItemStack(ModItems.THE_CORRUPTED_SPAWN_EGG.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.THE_CORRUPTED_SPAWN_EGG.get());
                        output.accept(ModItems.LITTLE_DEMON_SPAWN_EGG.get());
                        output.accept(ModItems.CORRUPTED_CORE.get());
                        output.accept(ModItems.STEEL_INGOT.get());
                        output.accept(ModItems.STEEL_HELMET.get());
                        output.accept(ModItems.STEEL_CHESTPLATE.get());
                        output.accept(ModItems.STEEL_LEGGINGS.get());
                        output.accept(ModItems.STEEL_BOOTS.get());
                        output.accept(ModItems.CORRUPTED_UPGRADE_TEMPLATE.get());
                        output.accept(ModItems.CORRUPTED_HELMET.get());
                        output.accept(ModItems.CORRUPTED_CHESTPLATE.get());
                        output.accept(ModItems.CORRUPTED_LEGGINGS.get());
                        output.accept(ModItems.CORRUPTED_BOOTS.get());
                        output.accept(ModItems.SOVEREIGN_HELMET.get());
                        output.accept(ModItems.SOVEREIGN_CHESTPLATE.get());
                        output.accept(ModItems.SOVEREIGN_LEGGINGS.get());
                        output.accept(ModItems.SOVEREIGN_BOOTS.get());
                        output.accept(ModItems.ABYSSAL_STORM_GAUNTLET.get());
                    })
                    .build());

    private ModCreativeTabs() {}
}
