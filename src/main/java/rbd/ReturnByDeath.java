package rbd;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

public class ReturnByDeath implements ModInitializer {
	public static final String MOD_ID = "return-by-death";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// player spawn data captured from setSpawnPoint
	private static final Map<UUID, RespawnLocation> LAST_SPAWN = new ConcurrentHashMap<>();
	public record RespawnLocation(
			BlockPos pos,
			ResourceKey<Level> dimension,
			long timeOfDay,
			float angle,
			boolean forced) {}
	private static final Set<UUID> HAS_SLEPT = ConcurrentHashMap.newKeySet();
	private static final float RESPAWN_UPDATE_CHANCE = 1f / 50000f; // will trigger around every 40ish mins
	@Override
	public void onInitialize() {
		ServerWorldEvents.LOAD.register((server, world) -> {
			server.getGameRules().getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(true, server);
		});

		ServerPlayerEvents.AFTER_RESPAWN.register((oldplayer, newplayer, alive) -> {
			if (!alive) {
				UUID id = oldplayer.getUUID();
				RespawnLocation spawn = LAST_SPAWN.get(id);
				newplayer.sendSystemMessage(
						Component.literal("Player " + newplayer.getUUID()
								+ " spawn is: " + LAST_SPAWN.get(newplayer.getUUID()))
				);
				BlockPos checkBed = spawn.pos;
				var state = newplayer.server.getLevel(spawn.dimension).getBlockState(spawn.pos);
				var block = state.getBlock();
				boolean stillValid = !HAS_SLEPT.contains(id) || block instanceof BedBlock || block instanceof RespawnAnchorBlock;
				System.out.println(stillValid);
				double x = 0;
				double y = 0;
				double z = 0;
				if (stillValid) {
					x = spawn.pos().getX();
					y = spawn.pos().getY();
					z = spawn.pos().getZ();
				} else {
					x = newplayer.server.overworld().getSharedSpawnPos().getX();
					y = newplayer.server.overworld().getSharedSpawnPos().getY();
					z = newplayer.server.overworld().getSharedSpawnPos().getZ();
				}
				newplayer.teleportTo(
					newplayer.server.getLevel(spawn.dimension),
					x, y, z,
					spawn.angle,
					newplayer.getXRot()
				);
				newplayer.setRespawnPosition(
						spawn.dimension(),
						new BlockPos((int) x, (int) y, (int) z),
						spawn.angle(),
						false,  // forced
						true
				);
			}
		});
		//default spawn recording that will overwrite your bed spawn on login.
		// I think this is more like the show honestly, if you're out on some
		// adventure and log in away from home, getting back and dying could randomly
		// set you back somewhere along your journey back home
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			var player = handler.getPlayer();
			var level = player.serverLevel();
			LAST_SPAWN.put(
				player.getUUID(),
				new RespawnLocation(
					player.blockPosition(),
					player.getRespawnDimension(),
					level.getDayTime(),
					player.getYRot(),
					false
				)
			);
			test(player);
		});
		// if a player has set their spawn with a bed recently then make that their respawn
		// not permanent though
		EntitySleepEvents.START_SLEEPING.register((entity, sleepingPos) -> {
			if (entity instanceof ServerPlayer player) {
				Level level = player.level();
				LAST_SPAWN.put(
					player.getUUID(),
					new RespawnLocation(
						player.blockPosition(),
						level.dimension(),
						level.getDayTime(),
						player.getYRot(),
						false
					)
				);
				HAS_SLEPT.add(player.getUUID());
				// test(player);
			}
		});
		// if a player hasn't set their spawn yet, every tick can have a random chance to trigger
		// the respawn setting
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				UUID id = player.getUUID();
				RespawnLocation saved = LAST_SPAWN.get(id);
				if (!HAS_SLEPT.contains(id) && server.getLevel(saved.dimension).getRandom().nextFloat() < RESPAWN_UPDATE_CHANCE) {
					Level level = player.level();
					LAST_SPAWN.put(
						id,
						new RespawnLocation(
							player.blockPosition(),
							level.dimension(),
							level.getDayTime(),
							player.getYRot(),
							false
						)
					);
					test(player);
				}
			}
		});

		//sounds
		Registry.register(BuiltInRegistries.SOUND_EVENT, ResourceLocation.fromNamespaceAndPath(MOD_ID, "respawn"),
				SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "respawn")));
	}

	public void test(ServerPlayer player) {
		player.sendSystemMessage(
				Component.literal("Player " + player.getUUID()
						+ " spawn set to: " + LAST_SPAWN.get(player.getUUID()))
		);
	}
}