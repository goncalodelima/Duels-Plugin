/*
 *
 *  * This file is part of Duels-Plugin - https://github.com/goncalodelima/Duels-Plugin
 *  * Copyright (c) 2026 goncalodelima and contributors
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 */

package pt.gongas.duel.service.duel.world;

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.exceptions.WorldAlreadyExistsException;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.infernalsuite.asp.api.world.SlimeWorldInstance;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pt.gongas.duel.DuelPlugin;
import pt.gongas.duel.util.CompletableFutureHelper;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the lifecycle of per-duel SlimeWorlds: cloning a fresh instance from
 * the shared duel template when a duel starts, and safely unloading/deleting
 * it once the duel is fully over.
 * <p>
 * Worlds are keyed by the challenger's UUID (prefixed with {@link #WORLD_PREFIX}),
 * consistent with how the rest of the duel system identifies a duel by its
 * challenger.
 */
public class DuelWorldService {

    private final DuelPlugin plugin;

    private final Logger logger;

    private final ExecutorService worldExecutor;

    private final AdvancedSlimePaperAPI advancedSlimePaperAPI;

    private final SlimeWorld duelTemplateWorld;

    private static final String WORLD_PREFIX = "duel-";

    public DuelWorldService(DuelPlugin plugin, Logger logger, ExecutorService worldExecutor, AdvancedSlimePaperAPI advancedSlimePaperAPI, SlimeWorld duelTemplateWorld) {
        this.plugin = plugin;
        this.logger = logger;
        this.worldExecutor = worldExecutor;
        this.advancedSlimePaperAPI = advancedSlimePaperAPI;
        this.duelTemplateWorld = duelTemplateWorld;
    }

    /**
     * Returns the already-loaded duel world for this challenger if one exists.
     * Otherwise, clones a fresh world from the shared duel template.
     * <p>
     * The cloning operation is executed asynchronously on
     * {@link #worldExecutor} because it performs blocking I/O and should not
     * run on the main server thread.
     */
    public @NotNull CompletableFuture<@Nullable SlimeWorld> createDuelWorld(@NotNull UUID challengerUuid) {

        SlimeWorldInstance duelWorld = advancedSlimePaperAPI.getLoadedWorld(WORLD_PREFIX + challengerUuid);

        if (duelWorld != null) {
            return CompletableFuture.completedFuture(duelWorld);
        }

        return CompletableFutureHelper.supplyAsync(worldExecutor,
                logger,
                () -> cloneTemplateWorld(challengerUuid), null
        );

    }

    /**
     * Attempts to unload and delete a duel's world, deferring (via retry with
     * a fixed delay) while players are still inside or while worlds are mid-tick,
     * since unloading in either case is unsafe.
     * <p>
     * On success (or if the world was already gone), invokes
     * {@code afterDelete} so callers can clean up their own duel state.
     *
     * @param worldName   the world name, without {@link #WORLD_PREFIX}
     * @param afterDelete callback invoked once the world has been removed
     *                    (or found to already not exist)
     */
    public void attemptWorldDelete(@NotNull String worldName, @NotNull Runnable afterDelete) {

        SlimeWorldInstance slimeWorldInstance = advancedSlimePaperAPI.getLoadedWorld(WORLD_PREFIX + worldName);

        if (slimeWorldInstance == null) {
            afterDelete.run();
            return;
        }

        World world = slimeWorldInstance.getBukkitWorld();

        if (world.getPlayerCount() > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> attemptWorldDelete(worldName, afterDelete), 20 * 15);
            return;
        }

        if (!Bukkit.isTickingWorlds()) {

            if (Bukkit.unloadWorld(world, true)) {
                afterDelete.run();
            } else {
                logger.log(Level.INFO, "The world was not unloaded successfully for some reason. DuelWorldService#attemptWorldDelete call failed");
            }

        } else {
            Bukkit.getScheduler().runTaskLater(plugin, () -> attemptWorldDelete(worldName, afterDelete), 20 * 15);
        }

    }

    private SlimeWorld cloneTemplateWorld(UUID newWorldUuid) {

        String worldUuidString = newWorldUuid.toString();

        try {
            return duelTemplateWorld.clone(WORLD_PREFIX + worldUuidString, null);
        } catch (IOException | IllegalArgumentException | WorldAlreadyExistsException e) {
            throw new IllegalStateException(e);
        }

    }

}