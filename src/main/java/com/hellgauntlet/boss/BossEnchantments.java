package com.hellgauntlet.boss;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Npc;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Behaviour of the 7 data-driven enchantments (data/hellgauntletboss/enchantment/*.json). The JSONs only define
 * costs, slots and which items accept them; the effects live here so they also work with the separate Hellgauntlet
 * mod (hellgauntlet:gauntlet_of_hell), which is only touched through its registry id, entity ids and NBT-free reflection.
 */
@EventBusSubscriber(modid = TheCorruptedMod.MODID)
public final class BossEnchantments {
    public static final ResourceLocation GAUNTLET_ID = ResourceLocation.fromNamespaceAndPath("hellgauntlet", "gauntlet_of_hell");
    private static final ResourceLocation VOID_ORB_ID = ResourceLocation.fromNamespaceAndPath("hellgauntlet", "void_orb");

    public static final ResourceKey<Enchantment> ABYSSAL_CONDUIT = key("abyssal_conduit");
    public static final ResourceKey<Enchantment> CATACLYSMIC_DISCHARGE = key("cataclysmic_discharge");
    public static final ResourceKey<Enchantment> SOUL_SIPHON = key("soul_siphon");
    public static final ResourceKey<Enchantment> CRIMSON_RETALIATION = key("crimson_retaliation");
    public static final ResourceKey<Enchantment> PLAGUEBEARER = key("plaguebearer");
    public static final ResourceKey<Enchantment> OBSIDIAN_BULWARK = key("obsidian_bulwark");
    public static final ResourceKey<Enchantment> HELLFIRE_STRIDE = key("hellfire_stride");

    private static final ResourceLocation SPEED_ID = ResourceLocation.fromNamespaceAndPath(TheCorruptedMod.MODID, "hellfire_stride_speed");
    private static final ResourceLocation EFFICIENCY_ID = ResourceLocation.fromNamespaceAndPath(TheCorruptedMod.MODID, "hellfire_stride_efficiency");
    private static final String CONDUIT_TAG = "hgb_conduit_acc";
    private static final String COOLDOWN_TAG = "hgb_conduit_cd";
    private static final String CATACLYSM_TAG = "hgb_cataclysm";
    private static final String CATACLYSM_RADIUS_TAG = "hgb_cataclysm_radius";
    private static final String CORRUPTION_SOURCE_TAG = "hgb_corruption_src";
    private static final DustParticleOptions SPARK = new DustParticleOptions(new Vector3f(0.85F, 0.08F, 0.06F), 0.8F);
    private static final Set<Block> NETHER_BLOCKS = Set.of(Blocks.NETHERRACK, Blocks.SOUL_SAND, Blocks.SOUL_SOIL,
            Blocks.BLACKSTONE, Blocks.BASALT);

    private BossEnchantments() {}

    private static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath(TheCorruptedMod.MODID, name));
    }

    // ---------------------------------------------------------------- lookup helpers

    public static int level(Level level, ResourceKey<Enchantment> key, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key)
                .map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack)).orElse(0);
    }

    private static int armorLevel(LivingEntity entity, ResourceKey<Enchantment> key, EquipmentSlot slot) {
        return level(entity.level(), key, entity.getItemBySlot(slot));
    }

    public static boolean isGauntlet(ItemStack stack) {
        return !stack.isEmpty() && GAUNTLET_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** Highest level of the enchantment on a Hellgauntlet in either hand (or, if asked, anywhere in the inventory). */
    private static int gauntletLevel(Player player, ResourceKey<Enchantment> key, boolean searchInventory) {
        int best = Math.max(isGauntlet(player.getMainHandItem()) ? level(player.level(), key, player.getMainHandItem()) : 0,
                isGauntlet(player.getOffhandItem()) ? level(player.level(), key, player.getOffhandItem()) : 0);
        if (searchInventory) {
            for (ItemStack stack : player.getInventory().items) {
                if (isGauntlet(stack)) {
                    best = Math.max(best, level(player.level(), key, stack));
                }
            }
        }
        return best;
    }

    private static boolean isChargeableOrbMode(ItemStack stack) {
        var data = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        return !data.getBoolean("laser") && Math.floorMod(data.getInt("mode"), 2) == 0;
    }

    private static boolean isEnemy(Player owner, LivingEntity e) {
        return e != owner && e.isAlive() && !(e instanceof Player) && !(e instanceof Npc)
                && !(e instanceof TheCorruptedBossEntity) && !(e instanceof net.minecraft.world.entity.TamableAnimal t && t.isTame());
    }

    // ---------------------------------------------------------------- 1. Abyssal Conduit

    /** Charging runs faster: the remaining use time drops 12% per level quicker (charge time -12% per level). */
    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player player) || !isGauntlet(event.getItem()) || !isChargeableOrbMode(event.getItem())) {
            return;
        }
        int lvl = level(player.level(), ABYSSAL_CONDUIT, event.getItem());
        if (lvl <= 0) {
            return;
        }
        float acc = player.getPersistentData().getFloat(CONDUIT_TAG) + (1.0F / (1.0F - 0.12F * lvl) - 1.0F);
        int whole = (int) acc;
        player.getPersistentData().putFloat(CONDUIT_TAG, acc - whole);
        if (whole > 0) {
            event.setDuration(Math.max(1, event.getDuration() - whole));
        }
    }

    /** Cooldowns after a shot are 12% shorter per level (re-applied once per cooldown, measured from its decay). */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        gauntletCooldown(player);
        hellfireStride(player);
    }

    private static void gauntletCooldown(Player player) {
        var item = BuiltInRegistries.ITEM.get(GAUNTLET_ID);
        if (!BuiltInRegistries.ITEM.containsKey(GAUNTLET_ID) || !player.getCooldowns().isOnCooldown(item)) {
            player.getPersistentData().putBoolean(COOLDOWN_TAG, false);
            return;
        }
        int lvl = gauntletLevel(player, ABYSSAL_CONDUIT, false);
        float p = player.getCooldowns().getCooldownPercent(item, 0.0F);
        if (lvl <= 0 || player.getPersistentData().getBoolean(COOLDOWN_TAG) || p >= 0.999F || p <= 0.0F) {
            return;
        }
        player.getPersistentData().putBoolean(COOLDOWN_TAG, true);
        int total = Math.round(1.0F / (1.0F - p));
        int shorter = Math.max(1, Math.round(total * (1.0F - 0.12F * lvl)));
        player.getCooldowns().addCooldown(item, Math.max(1, shorter - 1));
    }

    // ---------------------------------------------------------------- 2. Cataclysmic Discharge

    private static Field field(Class<?> c, String name) {
        try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** A fresh Void Orb of an enchanted gauntlet gets a bigger blast radius (+1.5 per level, rounded up to whole blocks). */
    @SubscribeEvent
    public static void onOrbJoin(EntityJoinLevelEvent event) {
        Entity orb = event.getEntity();
        if (orb.level().isClientSide || !VOID_ORB_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(orb.getType()))) {
            return;
        }
        if (!(orb instanceof net.minecraft.world.entity.projectile.Projectile projectile)
                || !(projectile.getOwner() instanceof Player player)) {
            return;
        }
        int lvl = gauntletLevel(player, CATACLYSMIC_DISCHARGE, false);
        if (lvl <= 0) {
            return;
        }
        Field radius = field(orb.getClass(), "chargedRadius");
        Field mode = field(orb.getClass(), "mode");
        int newRadius = 0;
        try {
            if (radius != null && mode != null && "NORMAL".equals(String.valueOf(mode.get(orb)))) {
                newRadius = radius.getInt(orb) + (int) Math.ceil(1.5 * lvl);
                radius.setInt(orb, newRadius);
            }
        } catch (ReflectiveOperationException ignored) {
            // blast stays at its normal size, the lightning below still works
        }
        orb.getPersistentData().putInt(CATACLYSM_TAG, lvl);
        orb.getPersistentData().putInt(CATACLYSM_RADIUS_TAG, newRadius);
    }

    /** When the orb detonates (removed by a hit, not by timing out) red lightning arcs to up to 3 enemies nearby. */
    @SubscribeEvent
    public static void onOrbLeave(EntityLeaveLevelEvent event) {
        Entity orb = event.getEntity();
        int lvl = orb.getPersistentData().getInt(CATACLYSM_TAG);
        if (lvl <= 0 || !(orb.level() instanceof ServerLevel level) || orb.getRemovalReason() != Entity.RemovalReason.DISCARDED
                || orb.tickCount > 200 || !(orb instanceof net.minecraft.world.entity.projectile.Projectile projectile)) {
            return;
        }
        Entity owner = projectile.getOwner();
        Player player = owner instanceof Player p ? p : null;
        double radius = orb.getPersistentData().getInt(CATACLYSM_RADIUS_TAG);
        Vec3 center = orb.position();
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius + 12.0),
                        e -> e.isAlive() && e != owner && !(e instanceof Player) && !(e instanceof Npc)
                                && !(e instanceof TheCorruptedBossEntity) && e.position().distanceTo(center) >= radius)
                .stream().sorted(Comparator.comparingDouble(e -> e.position().distanceToSqr(center))).limit(3).toList();
        for (LivingEntity target : targets) {
            BossCombat.boltBetween(level, center.add(0.0, 0.3, 0.0), target.getEyePosition());
            DamageSource source = player != null ? level.damageSources().playerAttack(player) : level.damageSources().magic();
            target.invulnerableTime = 0;
            target.hurt(source, 5.0F * lvl);
        }
    }

    // ---------------------------------------------------------------- 3. Soul Siphon

    @SubscribeEvent
    public static void onCorruptionAdded(MobEffectEvent.Added event) {
        if (event.getEffectInstance() != null && event.getEffectInstance().getEffect().is(ModEffects.CORRUPTION.getId())
                && !event.getEntity().level().isClientSide) {
            if (event.getEffectSource() instanceof Player player) {
                event.getEntity().getPersistentData().putUUID(CORRUPTION_SOURCE_TAG, player.getUUID());
            } else {
                event.getEntity().getPersistentData().remove(CORRUPTION_SOURCE_TAG);
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || victim instanceof Player) {
            return;
        }
        DamageSource source = event.getSource();
        Player killer = null;
        if (source.getEntity() instanceof Player p) {
            Entity direct = source.getDirectEntity();
            boolean melee = direct == p && isGauntlet(p.getMainHandItem());
            boolean gauntletProjectile = direct != null && direct != p
                    && "hellgauntlet".equals(BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()).getNamespace());
            if (melee || gauntletProjectile) {
                killer = p;
            }
        }
        if (killer == null && source.is(DamageTypes.GENERIC_KILL) && victim.hasEffect(ModEffects.CORRUPTION)
                && victim.getPersistentData().hasUUID(CORRUPTION_SOURCE_TAG)) {
            killer = victim.level().getPlayerByUUID(victim.getPersistentData().getUUID(CORRUPTION_SOURCE_TAG));
        }
        if (killer == null) {
            return;
        }
        int lvl = gauntletLevel(killer, SOUL_SIPHON, true);
        if (lvl > 0) {
            killer.heal(3.0F * lvl);
            if (victim.level() instanceof ServerLevel level) {
                level.sendParticles(SPARK, killer.getX(), killer.getY() + 1.0, killer.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
            }
        }
    }

    // ---------------------------------------------------------------- 4. Crimson Retaliation / 5. Plaguebearer (called from ArmorSetBonuses)

    /** The set bonus just blocked a hit: strike the attacker with red lightning, 4 true damage per level, and knock it back. */
    public static void retaliate(Player player, Entity attacker) {
        int lvl = armorLevel(player, CRIMSON_RETALIATION, EquipmentSlot.CHEST);
        if (lvl <= 0 || !(attacker instanceof LivingEntity target) || target == player || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        BossCombat.boltBetween(level, player.getEyePosition(), target.getEyePosition());
        if (target instanceof Player other && (other.isCreative() || other.isSpectator())) {
            return;
        }
        target.invulnerableTime = 0;
        target.hurt(level.damageSources().genericKill(), 4.0F * lvl);
        BossCombat.pushAway(player, target, 0.9 + 0.2 * lvl, 0.3);
    }

    /** Corrupted set bonus inflicts Corruption: +2 s per level, and it spreads to 1 extra enemy within 4 blocks per level. */
    public static void applyCorruption(Player player, LivingEntity victim) {
        int lvl = armorLevel(player, PLAGUEBEARER, EquipmentSlot.HEAD);
        int duration = CorruptionEffect.DURATION_TICKS + 40 * lvl;
        victim.addEffect(new MobEffectInstance(ModEffects.CORRUPTION, duration, 0), player);
        if (lvl <= 0) {
            return;
        }
        List<LivingEntity> near = victim.level().getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(4.0),
                        e -> e != victim && isEnemy(player, e)).stream()
                .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(victim))).limit(lvl).toList();
        for (LivingEntity e : near) {
            e.addEffect(new MobEffectInstance(ModEffects.CORRUPTION, duration, 0), player);
            if (victim.level() instanceof ServerLevel level) {
                BossCombat.boltBetween(level, victim.getEyePosition(), e.getEyePosition());
            }
        }
    }

    // ---------------------------------------------------------------- 6. Obsidian Bulwark

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        DamageSource source = event.getSource();
        if (entity.level().isClientSide || !(source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.LAVA)
                || source.is(DamageTypeTags.IS_EXPLOSION))) {
            return;
        }
        int lvl = armorLevel(entity, OBSIDIAN_BULWARK, EquipmentSlot.LEGS);
        if (lvl > 0) {
            event.setAmount(event.getAmount() * (1.0F - 0.15F * lvl));
        }
    }

    /** Full knockback immunity while standing on netherrack, soul sand, soul soil, blackstone or basalt. */
    @SubscribeEvent
    public static void onKnockBack(LivingKnockBackEvent event) {
        LivingEntity entity = event.getEntity();
        if (armorLevel(entity, OBSIDIAN_BULWARK, EquipmentSlot.LEGS) > 0
                && NETHER_BLOCKS.contains(entity.getBlockStateOn().getBlock())) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- 7. Hellfire Stride

    private static void hellfireStride(Player player) {
        int lvl = armorLevel(player, HELLFIRE_STRIDE, EquipmentSlot.FEET);
        setModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_ID,
                lvl > 0 && player.level().dimension() == Level.NETHER ? 0.2 * lvl : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setModifier(player.getAttribute(Attributes.MOVEMENT_EFFICIENCY), EFFICIENCY_ID, lvl > 0 ? 1.0 : 0.0,
                AttributeModifier.Operation.ADD_VALUE);
        if (lvl > 0 && player.isSprinting() && player.onGround() && player.tickCount % 2 == 0
                && player.level() instanceof ServerLevel level) {
            level.sendParticles(SPARK, player.getX(), player.getY() + 0.1, player.getZ(), 2, 0.18, 0.04, 0.18, 0.01);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 0.1,
                    player.getZ(), 1, 0.15, 0.03, 0.15, 0.02);
        }
    }

    private static void setModifier(AttributeInstance attribute, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        if (attribute == null) {
            return;
        }
        AttributeModifier current = attribute.getModifier(id);
        if (amount == 0.0) {
            if (current != null) {
                attribute.removeModifier(id);
            }
        } else if (current == null || current.amount() != amount) {
            attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
        }
    }
}
