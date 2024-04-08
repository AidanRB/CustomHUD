package com.minenash.customhud.mixin;

import com.minenash.customhud.CustomHud;
import com.minenash.customhud.ProfileManager;
import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/DebugHud;shouldShowPacketSizeAndPingCharts()Z"))
    private boolean pingForMetricVariables(DebugHud hud) {
        return hud.shouldShowPacketSizeAndPingCharts() || (ProfileManager.getActive() != null && ProfileManager.getActive().enabled.pingMetrics);
    }

}
