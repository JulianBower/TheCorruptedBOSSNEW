package com.hellgauntlet.boss;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Little Demon: a miniature horned imp that trades custom enchanted books in the Nether. 30 HP, immune to fire and
 * lava, wanders and looks around when idle, stands still while someone trades with it.
 */
public class LittleDemonEntity extends PathfinderMob implements Npc, Merchant {
    /** Enchantments it sells and their max levels. */
    private static final String[] BOOKS = {"abyssal_conduit", "cataclysmic_discharge", "soul_siphon", "crimson_retaliation",
            "plaguebearer", "obsidian_bulwark", "hellfire_stride"};
    private static final int[] BOOK_MAX = {3, 2, 2, 3, 2, 3, 2};

    @Nullable
    private Player tradingPlayer;
    @Nullable
    private MerchantOffers offers;

    public LittleDemonEntity(EntityType<? extends LittleDemonEntity> type, Level level) {
        super(type, level);
        this.setPathfindingMalus(PathType.LAVA, 8.0F);
        this.setPathfindingMalus(PathType.DANGER_FIRE, 0.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new StandStillWhileTradingGoal());
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    private class StandStillWhileTradingGoal extends Goal {
        StandStillWhileTradingGoal() {
            this.setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return LittleDemonEntity.this.tradingPlayer != null && LittleDemonEntity.this.isAlive();
        }

        @Override
        public void start() {
            LittleDemonEntity.this.getNavigation().stop();
        }

        @Override
        public void tick() {
            Player p = LittleDemonEntity.this.tradingPlayer;
            if (p != null) {
                LittleDemonEntity.this.getLookControl().setLookAt(p, 30.0F, 30.0F);
            }
        }
    }

    // ---------------------------------------------------------------- interaction / trading

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.isAlive() && this.tradingPlayer == null && hand == InteractionHand.MAIN_HAND) {
            if (!this.level().isClientSide) {
                this.setTradingPlayer(player);
                this.openTradingScreen(player, this.getDisplayName(), 1);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    public void setTradingPlayer(@Nullable Player player) {
        this.tradingPlayer = player;
    }

    @Nullable
    public Player getTradingPlayer() {
        return this.tradingPlayer;
    }

    public MerchantOffers getOffers() {
        if (this.offers == null) {
            this.offers = new MerchantOffers();
            if (!this.level().isClientSide) {
                this.fillOffers(this.offers);
            }
        }
        return this.offers;
    }

    /** 4 or 5 different randomized books from the 7 custom enchantments. */
    private void fillOffers(MerchantOffers out) {
        var enchantments = this.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < BOOKS.length; i++) {
            order.add(i);
        }
        Collections.shuffle(order, new java.util.Random(this.random.nextLong()));
        int count = 4 + this.random.nextInt(2);
        for (int n = 0; n < count; n++) {
            int idx = order.get(n);
            var key = net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(TheCorruptedMod.MODID, BOOKS[idx]));
            Holder<Enchantment> holder = enchantments.get(key).orElse(null);
            if (holder == null) {
                continue;
            }
            int level = 1 + this.random.nextInt(BOOK_MAX[idx]);
            ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(holder, level));
            out.add(new MerchantOffer(randomCost(level), book, 4, 8 + 4 * level, 0.05F));
        }
    }

    /** Gold ingots, blaze rods, netherite scraps or steel ingots; pricier for higher levels. */
    private ItemCost randomCost(int level) {
        return switch (this.random.nextInt(4)) {
            case 0 -> new ItemCost(Items.GOLD_INGOT, 10 + 6 * level + this.random.nextInt(5));
            case 1 -> new ItemCost(Items.BLAZE_ROD, 4 + 3 * level + this.random.nextInt(3));
            case 2 -> new ItemCost(Items.NETHERITE_SCRAP, 1 + level + this.random.nextInt(2));
            default -> new ItemCost((Item) ModItems.STEEL_INGOT.get(), 4 + 3 * level + this.random.nextInt(4));
        };
    }

    public void overrideOffers(@Nullable MerchantOffers newOffers) {
        this.offers = newOffers;
    }

    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        this.playSound(SoundEvents.VILLAGER_YES, this.getSoundVolume(), this.getVoicePitch());
    }

    public void notifyTradeUpdated(ItemStack stack) {
        if (!this.level().isClientSide) {
            this.playSound(stack.isEmpty() ? SoundEvents.VILLAGER_NO : SoundEvents.VILLAGER_YES,
                    this.getSoundVolume(), this.getVoicePitch());
        }
    }

    public int getVillagerXp() {
        return 0;
    }

    public void overrideXp(int xp) {
    }

    public boolean showProgressBar() {
        return false;
    }

    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    public boolean isClientSide() {
        return this.level().isClientSide;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.6F + this.random.nextFloat() * 0.2F;
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        super.die(source);
        this.setTradingPlayer(null);
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.offers != null && !this.level().isClientSide) {
            MerchantOffers.CODEC.encodeStart(this.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), this.offers)
                    .result().ifPresent(t -> tag.put("Offers", t));
        }
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Offers")) {
            MerchantOffers.CODEC.parse(this.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tag.get("Offers"))
                    .result().ifPresent(o -> this.offers = o);
        }
    }

}
