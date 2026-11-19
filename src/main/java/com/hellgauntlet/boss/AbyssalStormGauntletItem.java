package com.hellgauntlet.boss;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * Fused Blue-Lightning Gauntlet. It never breaks, hits for 16 (8 hearts) and plays exactly like the Hellgauntlet:
 * charging, firing, orb modes and lasers are the Hellgauntlet's own code, run on this stack through the item registry
 * (so the two mods stay separate). Its Void Orbs, lasers and blasts are drawn in electric blue and blast 1.85x
 * bigger (see BossEnchantments and client/BlueOverrides). Sneak + right click cycles: orb, entity-only orb, laser,
 * continuous laser.
 */
public class AbyssalStormGauntletItem extends Item {
    private static final DustParticleOptions BLUE_SPARK = new DustParticleOptions(new Vector3f(0.22F, 0.74F, 0.97F), 0.7F);
    private static final String[] STATES = {"Orb mode: Normal sphere (hold to charge)", "Orb mode: Entity only",
            "Laser mode: Laser", "Laser mode: Continuous laser"};

    public AbyssalStormGauntletItem(Properties properties) {
        super(properties);
    }

    /** 16 attack damage (8 hearts) and 1.8 attack speed. */
    public static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 15.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -2.2, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    private static Item base() {
        return BuiltInRegistries.ITEM.containsKey(BossEnchantments.GAUNTLET_ID) ? BuiltInRegistries.ITEM.get(BossEnchantments.GAUNTLET_ID) : null;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 15;
    }

    /** Dropped copies survive explosions, like a nether star. */
    @Override
    public boolean canBeHurtBy(ItemStack stack, DamageSource source) {
        return super.canBeHurtBy(stack, source) && !source.is(DamageTypeTags.IS_EXPLOSION);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, net.minecraft.world.item.TooltipFlag flag) {
        lines.add(Component.translatable("item.hellgauntletboss.abyssal_storm_gauntlet.tooltip").withStyle(net.minecraft.ChatFormatting.AQUA));
        if (base() == null) {
            lines.add(Component.translatable("item.hellgauntletboss.abyssal_storm_gauntlet.needs_mod").withStyle(net.minecraft.ChatFormatting.RED));
        }
    }

    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static boolean isLaser(ItemStack stack) {
        return data(stack).getBoolean("laser");
    }

    private static boolean isContinuous(ItemStack stack) {
        return isLaser(stack) && Math.floorMod(data(stack).getInt("laserMode"), 2) == 1;
    }

    /** Same data keys the Hellgauntlet reads, so its firing code follows the chosen state. */
    private static void cycle(ItemStack stack, Player player) {
        int current = isLaser(stack) ? 2 + Math.floorMod(data(stack).getInt("laserMode"), 2) : Math.floorMod(data(stack).getInt("mode"), 2);
        int next = (current + 1) % STATES.length;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putBoolean("laser", next >= 2);
            if (next >= 2) {
                tag.putInt("laserMode", next - 2);
            } else {
                tag.putInt("mode", next);
            }
        });
        player.displayClientMessage(Component.literal(STATES[next]), true);
    }

    /** The Hellgauntlet puts its cooldown on its own item; move it onto this one. */
    private void moveCooldown(Player player, ItemStack stack) {
        Item base = base();
        ItemCooldowns cooldowns = player.getCooldowns();
        if (base != null && cooldowns.isOnCooldown(base)) {
            cooldowns.removeCooldown(base);
            cooldowns.addCooldown(this, isLaser(stack) && !isContinuous(stack) ? 40 : 20);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Item base = base();
        if (base == null) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                cycle(stack, player);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        InteractionResultHolder<ItemStack> result = base.use(level, player, hand);
        moveCooldown(player, stack);
        return result;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        Item base = base();
        if (base != null && entity instanceof Player player) {
            base.releaseUsing(stack, level, entity, timeLeft);
            moveCooldown(player, stack);
        }
    }

    /** The Hellgauntlet's charge sounds and sparks, plus blue sparks gathering at the hand. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        Item base = base();
        if (base != null) {
            base.onUseTick(level, entity, stack, remaining);
        }
        if (!level.isClientSide && level instanceof ServerLevel server && !isLaser(stack)) {
            Vec3 hand = entity.getEyePosition().add(entity.getLookAngle().scale(0.9)).add(0.0, -0.3, 0.0);
            for (int i = 0; i < 3; i++) {
                server.sendParticles(BLUE_SPARK, hand.x + (server.random.nextDouble() - 0.5) * 0.7,
                        hand.y + (server.random.nextDouble() - 0.5) * 0.7, hand.z + (server.random.nextDouble() - 0.5) * 0.7,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }
}
