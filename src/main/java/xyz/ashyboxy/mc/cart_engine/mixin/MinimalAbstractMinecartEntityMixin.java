package xyz.ashyboxy.mc.cart_engine.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Debug(export = true)
@Mixin(value = AbstractMinecartEntity.class, priority = 500)
public abstract class MinimalAbstractMinecartEntityMixin extends Entity {
    public MinimalAbstractMinecartEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @WrapWithCondition(method = "moveOnRail", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle" +
            "/AbstractMinecartEntity;setPosition(DDD)V", ordinal = 0))
    private boolean moveFirstSetPosition(AbstractMinecartEntity instance, double x, double y, double z) {
        return true;
    }

    @ModifyArg(method = "moveOnRail", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle" +
            "/AbstractMinecartEntity;move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V"),
            index = 1)
    private Vec3d doFirstSetPosition(Vec3d movement, @Share("setPosVec") LocalRef<Vec3d> setPosVec) {
        return movement;
    }
}
