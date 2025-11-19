package rbd;

import net.fabricmc.api.ModInitializer;
import rbd.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.PlayerRespawnLogic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;


import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class ReturnByDeath implements ModInitializer {
	public static final String MOD_ID = "return-by-death";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// player spawn data captured from setSpawnPoint
	private static final Map<UUID, RespawnLocation> LAST_SPAWN = new ConcurrentHashMap<>();
	public record RespawnLocation(BlockPos pos, ResourceKey<Level> dimension, float angle, boolean forced) {}
	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution

		ExampleMixin.loadMap(LAST_SPAWN);
//		LOGGER.info("Hello Fabic world!");
	}
}