package mod.crend.dynamiccrosshair.handler;

import mod.crend.dynamiccrosshair.DynamicCrosshair;
import mod.crend.dynamiccrosshair.api.CrosshairContext;
import mod.crend.dynamiccrosshair.component.Crosshair;
import mod.crend.dynamiccrosshair.component.ModifierUse;
import mod.crend.dynamiccrosshair.component.CrosshairVariant;
import mod.crend.dynamiccrosshair.config.BlockCrosshairPolicy;
import mod.crend.dynamiccrosshair.mixin.IAxeItemMixin;
import mod.crend.dynamiccrosshair.mixin.IBucketItemMixin;
import mod.crend.dynamiccrosshair.mixin.IHoeItemMixin;
import mod.crend.dynamiccrosshair.mixin.IShovelItemMixin;
import net.minecraft.block.*;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.*;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.state.property.Properties;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;

import java.util.List;

public class VanillaUsableItemHandler {

    public static boolean isAlwaysUsableItem(ItemStack handItemStack) {
        Item handItem = handItemStack.getItem();
        return handItem.getUseAction(handItemStack) == UseAction.DRINK;
    }

    public static boolean isUsableItem(ItemStack handItemStack) {
        Item handItem = handItemStack.getItem();
        return (handItem.isFood()
                || handItem instanceof ArmorItem
                || handItem instanceof ElytraItem
                || handItem instanceof FireworkRocketItem
                || handItem instanceof SpawnEggItem
                || handItem instanceof FireChargeItem
                || handItem instanceof MusicDiscItem
                || handItem instanceof HoneycombItem
                || handItem instanceof EnderEyeItem
                || handItem instanceof GlassBottleItem
                || handItem instanceof PotionItem
                || handItem instanceof BucketItem
                || handItem instanceof BoneMealItem
                || handItem instanceof WritableBookItem
                || handItem instanceof WrittenBookItem
                || handItem instanceof GoatHornItem
        );
    }

    public static Crosshair checkUsableItem(CrosshairContext context) {
        Item handItem = context.getItem();
        // Enable crosshair on food and drinks also when not targeting if "when interactable" is chosen
        if (handItem.isFood()) {
            // Special case: sweet and glow berries can sometimes be placed
            if (DynamicCrosshair.config.dynamicCrosshairHoldingBlock() != BlockCrosshairPolicy.Disabled
                    && (handItem == Items.SWEET_BERRIES || handItem == Items.GLOW_BERRIES)) {
                if (context.isWithBlock()) {
                    if (context.canPlaceItemAsBlock()) return Crosshair.HOLDING_BLOCK;
                }
            }
            if (context.player.canConsume(false) || handItem.getFoodComponent().isAlwaysEdible()) {
                return Crosshair.USE_ITEM;
            }
        }
        if (handItem instanceof ArmorItem armorItem) {
            EquipmentSlot slot = armorItem.getSlotType();
            if (context.player.hasStackEquipped(slot)) {
                return null;
            }
            return Crosshair.USE_ITEM;
        }
        if (handItem instanceof ElytraItem) {
            if (context.player.hasStackEquipped(EquipmentSlot.CHEST)) {
                return null;
            }
            return Crosshair.USE_ITEM;
        }

        if (handItem instanceof FireworkRocketItem) {
            if (context.isWithBlock() || context.player.isFallFlying()) {
                return Crosshair.USE_ITEM;
            }
        }

        if (handItem instanceof GoatHornItem) {
            if (context.player.getItemCooldownManager().isCoolingDown(handItem)) {
                return null;
            }
            return Crosshair.USE_ITEM;
        }

        // Liquid interactions ignore block hit, cast extra rays
        // This getting called for entity hits is on purpose, as liquid interactions overwrite entity interactions
        if (handItem instanceof GlassBottleItem) {
            // Dragon's breath
            List<AreaEffectCloudEntity> list = context.world.getEntitiesByClass(AreaEffectCloudEntity.class, context.player.getBoundingBox().expand(2.0), entity -> {
                return entity != null && entity.isAlive() && entity.getParticleType().getType() == ParticleTypes.DRAGON_BREATH;
            });
            if (!list.isEmpty()) {
                return Crosshair.USE_ITEM;
            }

            BlockHitResult blockHitResult = context.raycastWithFluid(RaycastContext.FluidHandling.ANY);
            if (context.world.getFluidState(blockHitResult.getBlockPos()).isIn(FluidTags.WATER))
                return Crosshair.USE_ITEM;
        }
        if (handItem == Items.BUCKET) {
            BlockHitResult blockHitResult = context.raycastWithFluid(RaycastContext.FluidHandling.SOURCE_ONLY);
            if (blockHitResult.getType() == HitResult.Type.BLOCK) {
                FluidState bucketFluidState = context.world.getFluidState(blockHitResult.getBlockPos());
                if (!bucketFluidState.isEmpty() && bucketFluidState.isStill()) {
                    return Crosshair.USE_ITEM;
                }
                Block bucketBlock = context.world.getBlockState(blockHitResult.getBlockPos()).getBlock();
                if (bucketBlock instanceof FluidDrainable) {
                    if (!(bucketBlock instanceof Waterloggable || bucketBlock instanceof FluidBlock)) {
                        return Crosshair.USE_ITEM;
                    }
                }
            }
        }

        if (context.isWithBlock()) {
            return checkUsableItemOnBlock(context);
        }
        if (!context.isTargeting()) {
            return checkUsableItemOnMiss(context);
        }
        return null;
    }

    public static Crosshair checkUsableItemOnBlock(CrosshairContext context) {
        Item handItem = context.getItem();

        if (handItem instanceof SpawnEggItem) return Crosshair.USE_ITEM;

        BlockState blockState = context.getBlockState();
        Block block = blockState.getBlock();
        if (handItem instanceof ToolItem) {
            if (handItem instanceof AxeItem) {
                if (IAxeItemMixin.getSTRIPPED_BLOCKS().get(block) != null
                        || Oxidizable.getDecreasedOxidationBlock(block).isPresent()
                        || HoneycombItem.WAXED_TO_UNWAXED_BLOCKS.get().get(block) != null) {
                    return Crosshair.USE_ITEM;
                }
            } else if (handItem instanceof ShovelItem) {
                if (IShovelItemMixin.getPATH_STATES().get(block) != null) {
                    return Crosshair.USE_ITEM;
                }
            } else if (handItem instanceof HoeItem) {
                if (IHoeItemMixin.getTILLING_ACTIONS().get(block) != null) {
                    return Crosshair.USE_ITEM;
                }
            }
            return null;
        }
        if (handItem instanceof ShearsItem) {
            if (block instanceof AbstractPlantStemBlock plantStemBlock && !plantStemBlock.hasMaxAge(blockState)) {
                return Crosshair.USE_ITEM;
            }
            if (!context.player.shouldCancelInteraction() && block instanceof BeehiveBlock && blockState.get(BeehiveBlock.HONEY_LEVEL) >= 5) {
                return Crosshair.USE_ITEM;
            }
            return null;
        }

        if (handItem instanceof FlintAndSteelItem || handItem instanceof FireChargeItem) {
            if (CampfireBlock.canBeLit(blockState) || CandleBlock.canBeLit(blockState) || CandleCakeBlock.canBeLit(blockState)) {
                return Crosshair.USE_ITEM;
            }
            BlockPos firePos = context.getBlockPos().offset(((BlockHitResult) context.hitResult).getSide());
            if (AbstractFireBlock.canPlaceAt(context.world, firePos, context.player.getHorizontalFacing())) {
                if (DynamicCrosshair.config.dynamicCrosshairHoldingBlock() != BlockCrosshairPolicy.Disabled) {
                    return Crosshair.HOLDING_BLOCK;
                }
                return Crosshair.USE_ITEM;
            }
        }
        if (handItem instanceof MusicDiscItem && block instanceof JukeboxBlock) return Crosshair.USE_ITEM;
        if (handItem instanceof HoneycombItem && HoneycombItem.UNWAXED_TO_WAXED_BLOCKS.get().get(block) != null)
            return Crosshair.USE_ITEM;
        if (handItem instanceof EnderEyeItem) {
            if (!block.equals(Blocks.END_PORTAL_FRAME) || !blockState.get(Properties.EYE)) {
                return Crosshair.USE_ITEM;
            }
        }
        if (handItem instanceof EntityBucketItem) {
            if (DynamicCrosshair.config.dynamicCrosshairHoldingBlock() != BlockCrosshairPolicy.Disabled) {
                return new Crosshair(CrosshairVariant.HoldingBlock, ModifierUse.USE_ITEM);
            }
            return Crosshair.USE_ITEM;
        }
        if (block instanceof AbstractCauldronBlock cauldron && !context.player.shouldCancelInteraction()) {
            if (handItem instanceof BucketItem bucketItem) {
                Fluid fluid = ((IBucketItemMixin) bucketItem).getFluid();
                if (fluid == Fluids.WATER || fluid == Fluids.LAVA) {
                    return Crosshair.USE_ITEM;
                }
                if (fluid == Fluids.EMPTY && cauldron.isFull(blockState)) {
                    return Crosshair.USE_ITEM;
                }
            }
            if (handItem instanceof PowderSnowBucketItem) {
                return Crosshair.USE_ITEM;
            }
            if (block.equals(Blocks.WATER_CAULDRON)) {
                if (handItem instanceof GlassBottleItem) {
                    return Crosshair.USE_ITEM;
                }
                if (handItem instanceof PotionItem && PotionUtil.getPotion(context.getItemStack()) == Potions.WATER) {
                    return Crosshair.USE_ITEM;
                }
                if (handItem instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock sbb && sbb.getColor() != null) {
                    return Crosshair.USE_ITEM;
                }
                if (handItem instanceof DyeableItem dyeableItem && dyeableItem.hasColor(context.getItemStack())) {
                    return Crosshair.USE_ITEM;
                }
                if (handItem instanceof BannerItem && BannerBlockEntity.getPatternCount(context.getItemStack()) > 0) {
                    return Crosshair.USE_ITEM;
                }
            }
        }
        if (handItem instanceof GlassBottleItem) {
            if (block instanceof BeehiveBlock && blockState.get(BeehiveBlock.HONEY_LEVEL) >= 5 && !context.player.shouldCancelInteraction()) return Crosshair.USE_ITEM;
            return null;
        }
        if (handItem instanceof BucketItem bucketItem) {
            Fluid fluid = ((IBucketItemMixin) bucketItem).getFluid();
            if (fluid != Fluids.EMPTY) {
                if (DynamicCrosshair.config.dynamicCrosshairHoldingBlock() != BlockCrosshairPolicy.Disabled) {
                    return Crosshair.HOLDING_BLOCK;
                }
                return Crosshair.USE_ITEM;
            }
            else {
                if (block instanceof Waterloggable) {
                    if (blockState.contains(Properties.WATERLOGGED) && blockState.get(Properties.WATERLOGGED)) return Crosshair.USE_ITEM;
                } else if (block instanceof FluidDrainable) return Crosshair.USE_ITEM;
            }
        }
        if (handItem instanceof BoneMealItem) {
            if (block instanceof Fertilizable fertilizable && fertilizable.isFertilizable(context.world, context.getBlockPos(), blockState, true)) {
                return Crosshair.USE_ITEM;
            }
            if (context.getBlockState().isOf(Blocks.WATER) && context.getFluidState().getLevel() == 8) {
                return Crosshair.USE_ITEM;
            }
            return null;
        }
        if (handItem instanceof CompassItem) {
            if (block == Blocks.LODESTONE) {
                return Crosshair.USE_ITEM;
            }
        }
        if (handItem instanceof WritableBookItem || handItem instanceof WrittenBookItem) return Crosshair.USE_ITEM;

        return null;
    }

    public static Crosshair checkUsableItemOnMiss(CrosshairContext context) {
        Item handItem = context.getItem();
        if (DynamicCrosshair.config.dynamicCrosshairHoldingBlock() == BlockCrosshairPolicy.Always) {
            if (handItem instanceof EntityBucketItem) {
                return new Crosshair(CrosshairVariant.HoldingBlock, ModifierUse.USE_ITEM);
            }
            if (handItem instanceof BucketItem bucketItem && ((IBucketItemMixin) bucketItem).getFluid() != Fluids.EMPTY) {
                return Crosshair.HOLDING_BLOCK;
            }
        }
        return null;
    }
}
