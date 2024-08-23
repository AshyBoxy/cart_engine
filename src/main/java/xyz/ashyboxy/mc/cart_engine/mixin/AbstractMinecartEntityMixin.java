package xyz.ashyboxy.mc.cart_engine.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.datafixers.util.Pair;
import net.minecraft.block.*;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import xyz.ashyboxy.mc.cart_engine.CartEngine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

@Debug(export = true)
// TODO: refactor
// things that outright replace vanilla values should be high priority,
// things that just capture values should be low priority
@Mixin(value = AbstractMinecartEntity.class, priority = 500)
public abstract class AbstractMinecartEntityMixin extends Entity {
    public AbstractMinecartEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Shadow protected abstract double getMaxSpeed();

    @Shadow private static Pair<Vec3i, Vec3i> getAdjacentRailPositionsByShape(RailShape shape) { throw new IllegalStateException(); }

    @Unique private static final double basisAccelerationPerTick = 0.021D;

    // TODO: mod support
    // this could either be done using custom tags, or by enabling all modded rails by default
    // that could potentially break them, for the same reason activator/detector rails or not included here
    @Unique private static boolean isEligibleFastRail(BlockState state) {
        return state.isOf(Blocks.RAIL) || (state.isOf(Blocks.POWERED_RAIL) && state.get(PoweredRailBlock.POWERED));
    }

    @Unique private static RailShape getRailShape(BlockState state) {
        if (!(state.getBlock() instanceof AbstractRailBlock railBlock))
            throw new IllegalArgumentException("No rail shape found");
        return state.get(railBlock.getShapeProperty());
    }

    @Unique private static boolean isDiagonal (RailShape railShape) {
        return railShape == RailShape.SOUTH_WEST || railShape == RailShape.NORTH_EAST || railShape == RailShape.SOUTH_EAST || railShape == RailShape.NORTH_WEST;
    }
    @Unique private static boolean isSameDiagonal (RailShape railShape1, RailShape railShape2) {
        return railShape1 == RailShape.SOUTH_WEST && railShape2 == RailShape.NORTH_EAST
                || railShape1 == RailShape.NORTH_EAST && railShape2 == RailShape.SOUTH_WEST
                || railShape1 == RailShape.SOUTH_EAST && railShape2 == RailShape.NORTH_WEST
                || railShape1 == RailShape.NORTH_WEST && railShape2 == RailShape.SOUTH_EAST;
    }
    @Unique private boolean isRail(BlockPos pos) {
        return this.getWorld().getBlockState(pos).isIn(BlockTags.RAILS);
    }
    @Unique private BlockPos tryLowerRail(BlockPos pos) {
        if (isRail(pos.down())) return pos.down();
        return pos;
    }

    @Unique private BlockPos calculateExitPos(RailShape railShape, BlockPos startPos) {
        Pair<Vec3i, Vec3i> neighbors = getAdjacentRailPositionsByShape(railShape);

        BlockPos pos = railShape.isAscending() ? startPos.up() : startPos;

        BlockPos exitPos1 = tryLowerRail(pos.add(neighbors.getFirst()));
        BlockPos exitPos2 = tryLowerRail(pos.add(neighbors.getSecond()));

        Vec3d posCenter = Vec3d.ofCenter(pos);
        Vec3d exit1Center = Vec3d.ofCenter(exitPos1);
        Vec3d exit2Center = Vec3d.ofCenter(exitPos2);
        Vec3d momentumPos = posCenter.add(this.getVelocity()).multiply(1, 0, 1);
        return momentumPos.distanceTo(exit1Center.multiply(1, 0, 1))
                < momentumPos.distanceTo(exit2Center.multiply(1, 0, 1))
                ? exitPos1 : exitPos2;
    }

    // TODO: mod support
    // this would need to somehow take into account the original value to get the new max momentum
    // with our normal value of 8.5 that looks like (vanillaMaxMomentum * 4.25)
    // maybe (vanillaMaxMomentum * maxSpeed * 2.5)? this is actual maths so it needs more thought put in
    // setMaxSpeed too
    @ModifyExpressionValue(method = "moveOnRail", at = @At(value = "CONSTANT", args = "doubleValue=2.0", ordinal = 0))
    private double setMaxMomentum(double original) {
        return CartEngine.maxMomentum;
    }
    @ModifyExpressionValue(method = "moveOnRail", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity" +
            "/vehicle/AbstractMinecartEntity;getMaxSpeed()D"))
    private double setMaxSpeed(double original, @Share("maxSpeedForThisTick") LocalDoubleRef max) {
        return max.get();
    }

    // TODO: is this needed?
    // i think it ensures brake rails stop as fast as they do in vanilla?
    // TODO: mod support
    // something similar to this could be used to make the copper rail's rails as fast as they are for 8b/s minecarts
    @ModifyExpressionValue(method = "moveOnRail", at= @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Vec3d;multiply(DDD)Lnet/minecraft/util/math/Vec3d;", ordinal = 0))
    private Vec3d slowdownBrakeRail(Vec3d original) {
        Vec3d velocity = this.getVelocity();
        double momentum = velocity.horizontalLength();
        if (momentum > CartEngine.vanillaMaxSpeed) {
            double ratioToSlowDown = CartEngine.vanillaMaxSpeed / momentum;
            velocity = velocity.multiply(ratioToSlowDown, 1, ratioToSlowDown);
        }
        // TODO: mod support
        // theoretically, other mods could change this value (0.5 in vanilla)
        // do we even want to support that? it's a pretty small change
        double brakeFactor = 0.59;
        return velocity.multiply(brakeFactor, 0, brakeFactor);
    }

    // TODO: is this needed?
    @ModifyArgs(method = "moveOnRail", at= @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle" +
            "/AbstractMinecartEntity;setVelocity(DDD)V", ordinal = 0))
    private void clampVelocity(Args args, @Local(argsOnly = true) BlockPos pos) {
        double hVel = this.getVelocity().horizontalLength();
        args.set(0, hVel * MathHelper.clamp(MathHelper.floor(this.getX()) - pos.getX(), -1, 1));
        args.set(2, hVel * MathHelper.clamp(MathHelper.floor(this.getZ()) - pos.getZ(), -1, 1));
    }

    @WrapWithCondition(method = "moveOnRail", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle" +
            "/AbstractMinecartEntity;setPosition(DDD)V"), slice = @Slice(from = @At(value = "INVOKE", target = "Lnet" +
            "/minecraft/entity/vehicle/AbstractMinecartEntity;move(Lnet/minecraft/entity/MovementType;" +
            "Lnet/minecraft/util/math/Vec3d;)V"), to = @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;applySlowdown()V")))
    private boolean disableVanillaSnap(AbstractMinecartEntity instance, double x, double y, double z) {
        return false;
    }
    @Inject(method = "moveOnRail", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;applySlowdown()V"))
    private void snapDown(BlockPos pos, BlockState state, CallbackInfo ci, @Local RailShape railShape) {
        // looks like this: snaps minecarts down from on two block drops;
        // prevents some weird minecart rendering when going down slopes
        final BlockPos baseBlockPos = new BlockPos(MathHelper.floor(this.getX()), MathHelper.floor(this.getY()), MathHelper.floor(this.getZ()));
        if (railShape.isAscending() && !isRail(baseBlockPos) && !isRail(baseBlockPos.down())) {
            if (isRail(baseBlockPos.down(2))) {
                this.setPosition(this.getPos().add(0, -1, 0));
            } else if (isRail(baseBlockPos.down(3))) {
                this.setPosition(this.getPos().add(0, -2, 0));
            }
        }
    }

    // TODO: slice?
    @ModifyExpressionValue(method="moveOnRail", at=@At(value="INVOKE", target="Lnet/minecraft/util/math/Vec3d;add" +
            "(DDD)Lnet/minecraft/util/math/Vec3d;", ordinal=5))
    private Vec3d modifyPoweredRailAcceleration(Vec3d original, @Share(namespace = CartEngine.MOD_ID, value = "movement") LocalRef<Vec3d> movement) {
        if(!this.hasPassengers()) return original;

        Vec3d momentum = this.getVelocity();
        double horizontalMomentum = momentum.horizontalLength();

        // TODO: comprehend this
        // "I wanted the kickstart feel to be like a curve" - audaki
        // TODO: Rewrite the comment/naming so it makes more sense (very confusing since TPS is 20 and we can only skip 1 block with current speeds)
        // Based on a 10 ticks per second basis spent per powered block we calculate a fair acceleration per tick
        // due to spending less ticks per powered block on higher speeds (and even skipping blocks)
        final double basisTicksPerSecond = 10.0D;
        // Tps = Ticks per second
        final double tickMovementForBasisTps = 1.0D / basisTicksPerSecond;
        final double maxSkippedBlocksToConsider = 3.0D;
        double acceleration = basisAccelerationPerTick;
        final double distanceMovedHorizontally = movement.get().horizontalLength();
        if (distanceMovedHorizontally > tickMovementForBasisTps) {
            acceleration *= Math.min((1.0D + maxSkippedBlocksToConsider) * basisTicksPerSecond, distanceMovedHorizontally / tickMovementForBasisTps);
            // Add progressively slower (or faster) acceleration for higher speeds;
            double highspeedFactor =
                    1.0D + MathHelper.clamp(-0.45D * (distanceMovedHorizontally / tickMovementForBasisTps / basisTicksPerSecond), -0.7D, 2.0D);
            acceleration *= highspeedFactor;
        }
//        CartEngine.LOGGER.info("acceleration is {} for a max speed of {} (x)", acceleration, momentum.getX() + (acceleration * momentum.getX() / horizontalMomentum));
        return (momentum.add(acceleration * (momentum.x / horizontalMomentum), 0.0D,
                acceleration * (momentum.z / horizontalMomentum)));
    }

    // TODO: mod support
    // this could maybe be made to support other mods messing with this value
    // that'd be like (original * 16.8) for (0.02 * 16.8 = 0.336) which is what we use here
    // TODO: refactor
    // it may be better to target the setVelocity call that uses these values?
    // that would allow us to get modified values from mods that modify the arguments
    // but hinder mods that use the variables. this is really an either or situation
    @ModifyExpressionValue(method = "moveOnRail", at = @At(value = "CONSTANT", args = "doubleValue=0.02"))
    private double setSlowAccel(double original) {
        return basisAccelerationPerTick * 16;
    }
    @ModifyExpressionValue(method = "moveOnRail", at = @At(value = "CONSTANT", args = "doubleValue=-0.02"))
    private double setSlowAccelNegative(double original) {
        return -(basisAccelerationPerTick * 16);
    }

    @Inject(method = "moveOnRail", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;getFirstPassenger()Lnet/minecraft/entity/Entity;"))
    private void moveOnRail1(BlockPos startPos, BlockState state, CallbackInfo ci, @Local RailShape railShape,
                             @Share("maxSpeedForThisTick") LocalDoubleRef maxSpeedForThisTickShared,
                             @Share("exitPos") LocalRef<BlockPos> exitPos) {
        exitPos.set(calculateExitPos(railShape, startPos));

        ArrayList<BlockPos> adjRailPositions = new ArrayList<>();

        // TODO: mod support
        // this should take into account the minecart's max speed to support modded minecarts with higher max speeds
        // TODO: bug
        // this doesn't properly take into account being in water
        // implement the above (this.getMaxSpeed() takes it into account) or divide the result of this by 2 (to match vanilla)
        // currently, when our momentum gets above 8 b/s then the max speed suddenly jumps from 4b/s to 34b/s
        // see TestMixin
        // technically, this issue is actually not taking into account that getMaxSpeed having a dynamic return value
        Supplier<Double> calculateMaxSpeedForThisTick = () -> {
            double fallback = this.getMaxSpeed();

            if (!this.hasPassengers())
                return fallback;
            if (this.getVelocity().horizontalLength() < CartEngine.vanillaMaxSpeed)
                return fallback;
            if (!isEligibleFastRail(state))
                return fallback;

            // hashset prevents duplicate positions from being counted
            HashSet<BlockPos> checkedPositions = new HashSet<>();

            checkedPositions.add(startPos);
            BiFunction<BlockPos, RailShape, ArrayList<Pair<BlockPos, RailShape>>> checkNeighbors = (checkPos, checkRailShape) -> {
                Pair<Vec3i, Vec3i> nExitPair = getAdjacentRailPositionsByShape(checkRailShape);
                ArrayList<Pair<BlockPos, RailShape>> newNeighbors = new ArrayList<>();
                BlockPos sourcePos = checkRailShape.isAscending() ? checkPos.up() : checkPos;

                for (Vec3i nExitRelPos : List.of(nExitPair.getFirst(), nExitPair.getSecond())) {
                    BlockPos nPos = tryLowerRail(sourcePos.add(nExitRelPos));

                    if (checkedPositions.contains(nPos))
                        continue;

                    BlockState nState = this.getWorld().getBlockState(nPos);

                    if (!isEligibleFastRail(nState))
                        return new ArrayList<>();

                    RailShape nShape = getRailShape(nState);
                    if (nShape != railShape && !isSameDiagonal(railShape, nShape))
                        return new ArrayList<>();
                    checkedPositions.add(nPos);
                    adjRailPositions.add(nPos);
                    // Adding the neighbor rail shape currently has no use, since we abort on rail shape change anyway
                    // Code stays as is for now so we can differentiate between types of rail shape changes later
                    newNeighbors.add(Pair.of(nPos, nShape));
                }
                return newNeighbors;
            };

            ArrayList<Pair<BlockPos, RailShape>> newNeighbors = checkNeighbors.apply(startPos, railShape);

            double checkFactor = (isDiagonal(railShape) || railShape.isAscending()) ? 2 : 1;
            final int cutoffPoint = 3;
            int sizeToCheck = (int) (2 * (cutoffPoint + (checkFactor * CartEngine.maxSpeed)));
            sizeToCheck -= (sizeToCheck % 2);

            while (!newNeighbors.isEmpty() && adjRailPositions.size() < sizeToCheck) {
                ArrayList<Pair<BlockPos, RailShape>> tempNewNeighbors = new ArrayList<>(newNeighbors);
                newNeighbors.clear();
                for (Pair<BlockPos, RailShape> newNeighbor : tempNewNeighbors) {
                    ArrayList<Pair<BlockPos, RailShape>> result = checkNeighbors.apply(newNeighbor.getFirst(), newNeighbor.getSecond());
                    // Abort when one direction is empty
                    if (result.isEmpty()) {
                        newNeighbors.clear();
                        break;
                    }
                    newNeighbors.addAll(result);
                }
            }

            int railCountEachDirection = adjRailPositions.size() / 2;
            final double cutoffSpeedPerSec = 20;

            switch (railCountEachDirection) {
                case 0:
                case 1:
                    return fallback;
                case 2:
                    return 12 / CartEngine.tps;
                case 3:
                    return cutoffSpeedPerSec / CartEngine.tps;
                default:
            }
            int railCountPastBegin = railCountEachDirection - cutoffPoint;
            return (cutoffSpeedPerSec + ((20 / checkFactor) * railCountPastBegin)) / CartEngine.tps;
        };

        // TODO: mod support
        // like above this should take into account the minecarts max speed
        // maybe something like Math.max(AshyBoxyCartEngine.maxSpeed, this.getMaxSpeed())
        // better solution for these would be to scale the max speed based on the result of getMaxSpeed()
        // but then a modded cart with triple max speed would be triple our max speed which could be too op?
        double maxSpeedForThisTick = Math.min(calculateMaxSpeedForThisTick.get(), CartEngine.maxSpeed);
        if (isDiagonal(railShape) || railShape.isAscending()) {
            // Diagonal and Ascending/Descending is 1.4142 times faster, we correct this here
            maxSpeedForThisTick = Math.min(maxSpeedForThisTick, 0.7071 * CartEngine.maxSpeed);
        }
        maxSpeedForThisTickShared.set(maxSpeedForThisTick);
    }

    @WrapWithCondition(method = "moveOnRail", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle" +
            "/AbstractMinecartEntity;setPosition(DDD)V", ordinal = 0))
    private boolean moveFirstSetPosition(AbstractMinecartEntity instance, double x, double y, double z,
                                         @Share("setPosVec") LocalRef<Vec3d> setPosVec) {
        setPosVec.set(new Vec3d(x, y, z));
        return false;
    }
    // not low priority, we need to run this before linkart's modifiedMovement
    // TODO: refactor
    // could exitPos be calculated here instead? bearing in mind we have a different velocity here
    @ModifyArg(method = "moveOnRail", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle" +
            "/AbstractMinecartEntity;move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V"),
            index = 1)
    private Vec3d doFirstSetPosition(Vec3d movement, @Local(argsOnly = true) BlockPos startPos, @Local RailShape railShape,
                                     @Share("exitPos") LocalRef<BlockPos> exitPos,
                                     @Share("setPosVec") LocalRef<Vec3d> setPosVec) {
        double extraY = 0;
        if (railShape.isAscending() && exitPos.get().getY() > startPos.getY())
            // TODO: why is this rounded down?
            extraY = (int) (0.5 + movement.horizontalLength());
        this.setPosition(setPosVec.get().add(0, extraY, 0));
        return movement;
    }

    @Mixin(value = AbstractMinecartEntity.class, priority = 10000)
    public abstract static class LowPriority extends Entity {
        public LowPriority(EntityType<?> type, World world) {
            super(type, world);
        }

        // low priority, we want the actual final used value later
        @ModifyArg(method = "moveOnRail", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle" +
                "/AbstractMinecartEntity;move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V"),
                index = 1)
        private Vec3d catchMovement(Vec3d movement,
                        @Share(namespace = CartEngine.MOD_ID, value = "movement") LocalRef<Vec3d> movementShare) {
            // needed in modifyPoweredRailAcceleration
            movementShare.set(movement);
            return movement;
        }
    }
}
