package org.blackaddons.platform.mixin.input;

import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.api.event.input.MovementInputEvent;
import org.blackaddons.platform.impl.player.MinecraftMovement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {

    @Shadow
    private static float calculateImpulse(boolean positive, boolean negative) {
        throw new AssertionError();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void blackaddons$onTick(CallbackInfo ci) {
        if (this.keyPresses == null) {
            return;
        }
        float forwardImpulse = this.moveVector != null ? this.moveVector.y : 0f;
        float leftImpulse = this.moveVector != null ? this.moveVector.x : 0f;

        MovementInputEvent event = new MovementInputEvent(
            this.keyPresses.forward(),
            this.keyPresses.backward(),
            this.keyPresses.left(),
            this.keyPresses.right(),
            this.keyPresses.jump(),
            this.keyPresses.shift(),
            this.keyPresses.sprint(),
            forwardImpulse,
            leftImpulse
        );
        EventBus.INSTANCE.post(event);
        MinecraftMovement.INSTANCE.applyDrives(event);

        this.keyPresses = new Input(
            event.getForward(),
            event.getBackward(),
            event.getLeft(),
            event.getRight(),
            event.getJump(),
            event.getShift(),
            event.getSprint()
        );
        this.moveVector = new Vec2(event.getLeftImpulse(), event.getForwardImpulse()).normalized();

        Input sent = MinecraftMovement.takeEarlyInput();
        if (sent != null) {
            this.keyPresses = sent;
            this.moveVector = new Vec2(
                calculateImpulse(sent.left(), sent.right()),
                calculateImpulse(sent.forward(), sent.backward())
            ).normalized();
        }
    }
}
