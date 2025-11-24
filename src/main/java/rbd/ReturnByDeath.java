package rbd;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;

import javax.swing.text.html.Option;
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
			boolean forced,
			boolean isBedLocation) {}
	private static final Set<UUID> HAS_SLEPT = ConcurrentHashMap.newKeySet();
	private static final float RESPAWN_UPDATE_CHANCE = 1f / 50000f; // will trigger around every 40ish mins
	@Override
	public void onInitialize() {
		CustomSounds.initialize();
		ServerWorldEvents.LOAD.register((server, world) -> {
			server.getGameRules().getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(true, server);
			server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (alive) return; // only care about death respawns

			UUID id = newPlayer.getUUID();
			RespawnLocation spawn = LAST_SPAWN.get(id);
			if (spawn == null) {
				// no record for this player -> pure vanilla behavior
				playMySound(newPlayer);
				return;
			}

			var server = newPlayer.server;
			var targetLevel = server.getLevel(spawn.dimension());
			if (targetLevel == null) {
				// dimension no longer exists / not loaded -> let vanilla handle it
				playMySound(newPlayer);
				return;
			}

			BlockPos pos = spawn.pos();

			if (spawn.isBedLocation()) {
				// This location was set by a bed or respawn anchor
				var state = targetLevel.getBlockState(pos);
				var block = state.getBlock();
				boolean bedOrAnchorStillThere =
						(block instanceof BedBlock || block instanceof RespawnAnchorBlock);

				if (bedOrAnchorStillThere) {
					// Bed/anchor still exists → overwrite vanilla and force them here
					if (block instanceof BedBlock) {
						Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
						Optional<Vec3> standPos = BedBlock.findStandUpPosition(EntityType.PLAYER, newPlayer.serverLevel(), pos, facing, newPlayer.getYRot());
						if (standPos.isPresent()) pos = BlockPos.containing(standPos.get());
						teleport(newPlayer, targetLevel, pos);
						playMySound(newPlayer);
					} else {
						int charge = state.getValue(RespawnAnchorBlock.CHARGE);
						boolean anchorIsActive = charge > 0;
						if (anchorIsActive) {
							teleport(newPlayer, targetLevel, pos);
							playMySound(newPlayer);
						}
						playMySound(newPlayer);
                        return;
                    }
				} else {
					// Bed/anchor gone → optional: clear our record, then let vanilla handle it
					// LAST_SPAWN.remove(id);
					HAS_SLEPT.remove(newPlayer.getUUID());
					playMySound(newPlayer);
				}

				// Either way, we’re done handling bed-based spawns
				playMySound(newPlayer);
				return;
			}

			// Not a bed/anchor location: this is a custom “travel overwrite”
			teleport(newPlayer, targetLevel, pos);
			playMySound(newPlayer);
		});
		//default spawn recording that will overwrite your bed spawn on login.
		// I think this is more like the show honestly, if you're out on some
		// adventure and log in away from home, getting back and dying could randomly
		// set you back somewhere along your journey back home
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			var player = handler.getPlayer();
			var level = player.serverLevel();
			addToMap(player, level);
			playMySound(player);
			//test(player);
		});
		// if a player has set their spawn with a bed recently then make that their respawn
		// not permanent though
		EntitySleepEvents.START_SLEEPING.register((entity, sleepingPos) -> {
			if (entity instanceof ServerPlayer player) {
				addToMap(player, sleepingPos, player.level());
				HAS_SLEPT.add(player.getUUID());
				// test(player);
			}
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS; // prevent crashes on client-side
			}
			BlockState block = level.getBlockState(hit.getBlockPos());
			if (block.getBlock() instanceof BedBlock || block.getBlock() instanceof RespawnAnchorBlock) {
				HAS_SLEPT.add(player.getUUID());
				addToMap(serverPlayer, hit.getBlockPos(), level);
			}
			return InteractionResult.PASS;
		});
		// if a player hasn't set their spawn yet, every tick can have a random chance to trigger
		// the respawn setting
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				UUID id = player.getUUID();
				RespawnLocation saved = LAST_SPAWN.get(id);
				if (server.getLevel(saved.dimension()).getRandom().nextFloat() < RESPAWN_UPDATE_CHANCE) {
					Level level = player.level();
					addToMap(player, level);
					//test(player);
				}
			}
		});
	}
	public void teleport(ServerPlayer newplayer, ServerLevel level, BlockPos pos) {
		newplayer.teleportTo(
				level,
				pos.getX() + 0.5,
				pos.getY() + 0.5,
				pos.getZ() + 0.5,
				newplayer.getYRot(),
				newplayer.getXRot());
	}
	public void playMySound(ServerPlayer newPlayer) {
		newPlayer.level().playSound(
				null,                           // null = audible to all nearby players
				newPlayer.getX(),
				newPlayer.getY(),
				newPlayer.getZ(),
				CustomSounds.RESPAWN,
				newPlayer.getSoundSource(),     // or SoundSource.PLAYERS
				1.0F,
				1.0F
		);
	}
	public void addToMap(ServerPlayer player, Level level) {
		LAST_SPAWN.put(
				player.getUUID(),
				new RespawnLocation(
						player.blockPosition(),
						level.dimension(),
						level.getDayTime(),
						player.getYRot(),
						false,
						false
				)
		);
	}
	// overloading to add the bed position instead of the player position, will be called if a bed is
	// right clicked or slept in
	public void addToMap(ServerPlayer player, BlockPos bedLocation, Level level) {
		LAST_SPAWN.put(
				player.getUUID(),
				new RespawnLocation(
						bedLocation,
						level.dimension(),
						level.getDayTime(),
						player.getYRot(),
						false,
						true
				)
		);
	}

	public void test(ServerPlayer player) {
		player.sendSystemMessage(
				Component.literal("Player " + player.getUUID()
						+ " spawn set to: " + LAST_SPAWN.get(player.getUUID()))
		);
	}
}