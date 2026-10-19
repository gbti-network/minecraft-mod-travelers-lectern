package com.gbti.travelerslectern;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TravelersLectern implements ModInitializer {
    public static final String MOD_ID = "travelers_lectern";
    private static final Logger LOGGER = LogManager.getLogger("TravelersLectern");
    public static boolean debugLoggingEnabled = false;
    // Break attempts per player, so the protection message shows on every third try, not every hit
    private static final Map<UUID, Integer> BREAK_TRIES = new HashMap<>();

    public static void logDebug(String message, Object... params) {
        if (debugLoggingEnabled) {
            LOGGER.info("[TL Debug] " + message, params);
        }
    }

    public static void logError(String message, Object... params) {
        LOGGER.error(message, params);
    }

    @Override
    public void onInitialize() {
        LecternStore.readConfig();
        LOGGER.info("[TL] Debug logging status: {}", debugLoggingEnabled);

        ServerLifecycleEvents.SERVER_STARTED.register(LecternStore::load);
        ServerTickEvents.END_SERVER_TICK.register(Lecterns::tick);

        // Only operators can break a Traveler's Lectern; breaking one makes the place plain
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (!state.is(Blocks.LECTERN) || !(player instanceof ServerPlayer serverPlayer)) {
                return true;
            }
            Lectern lectern = LecternStore.get(Lecterns.dimensionId(level.dimension()), pos);
            if (lectern == null) {
                return true;
            }
            if (!LecternCommands.allowed(serverPlayer.createCommandSourceStack())) {
                UUID id = serverPlayer.getUUID();
                if (BREAK_TRIES.getOrDefault(id, 1) >= 3) {
                    BREAK_TRIES.put(id, 0);
                    serverPlayer.sendSystemMessage(Component.literal("This lectern is protected by a mysterious force."));
                } else {
                    BREAK_TRIES.put(id, BREAK_TRIES.getOrDefault(id, 0) + 1);
                }
                return false;
            }
            LecternStore.remove(lectern);
            return true;
        });

        LecternCommands.register();
        logDebug("Travelers Lectern initialized");
    }
}
