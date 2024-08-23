package xyz.ashyboxy.mc.cart_engine.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractMinecartEntity.class)
public abstract class TestMixin extends Entity {
    public TestMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    // for testing an issue with modifyPoweredRailAcceleration, where it clamps weirdly
//    @Overwrite
//    public double getMaxSpeed() {
//        return (this.isTouchingWater() ? 1.0 : 8.0) / 20.0;
//    }

    // this is a partial fix for the above issue, which allows extreme cases such as above to still accelerate
//    @WrapWithCondition(method = "moveOnRail", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle" +
//            "/AbstractMinecartEntity;applySlowdown()V"))
//    private boolean cancelSlowdown(AbstractMinecartEntity instance) {
//        return false;
//    }
}
