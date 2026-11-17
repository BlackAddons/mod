package org.blackaddons.platform.mixin.input;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** {@code keyPress} (private before 26.3), so {@code SyntheticKeys} takes the physical key path. */
@Mixin(KeyboardHandler.class)
public interface KeyboardHandlerInvoker {
    @Invoker("keyPress")
    void blackaddons$keyPress(long handle, int action, KeyEvent event);
}
