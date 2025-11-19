package rbd.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rbd.ReturnByDeath;
import net.minecraft.server.*;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat;

@Mixin(MinecraftServer.class)
public class ExampleMixin {
	public void ExampleMixin() {

	}
	@Inject(at = @At("HEAD"), method = "loadLevel")
	private void init(CallbackInfo info) {
		// This code is injected into the start of MinecraftServer.loadLevel()V
	}

	@Inject(at = @At("TAIL"), method = "<init>")
	private void init(Map<UUID, ReturnByDeath.RespawnLocation> map) {

		ServerPlayer player = (ServerPlayer)(Object)this;
		UUID id = player.getUUID();
		player.sendSystemMessage(Component.literal("player " + id + " respawn location is now: " + map.get(id)));

	}
}