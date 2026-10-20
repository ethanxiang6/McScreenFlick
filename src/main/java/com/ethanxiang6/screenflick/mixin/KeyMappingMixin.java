package com.ethanxiang6.screenflick.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.KeyMapping;

import com.ethanxiang6.screenflick.FlickController;

/**
 * Lets the tap force W up or S down for a couple of ticks without touching the real key state,
 * so key-repeat events can't interrupt it and your held keys come back on their own afterwards.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
	@Inject(method = "isDown()Z", at = @At("HEAD"), cancellable = true)
	private void screenflick$overrideIsDown(CallbackInfoReturnable<Boolean> cir) {
		Boolean override = FlickController.overrideFor((KeyMapping) (Object) this);
		if (override != null) {
			cir.setReturnValue(override);
		}
	}
}
