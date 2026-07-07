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

package pt.gongas.duel.service.user;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pt.gongas.duel.model.user.DuelUser;
import pt.gongas.duel.repository.user.UserRepository;
import pt.gongas.duel.model.duel.result.DuelResultSnapshot;
import pt.gongas.duel.util.CompletableFutureHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Owns the single in-memory cache of online {@link DuelUser}s and is the sole
 * gateway to {@link UserRepository}. No other class should hold a direct
 * reference to the repository or maintain its own user cache.
 * <p>
 * Thread-safety: the in-memory {@code users} map is a plain {@link HashMap},
 * not thread-safe. {@link #putUser}, {@link #getUser}, and {@link #removeUser}
 * must only ever be called from the main (Bukkit) thread. The repository-backed
 * methods ({@link #getOrCreateDataAndUpdate}, {@link #applyDuelResult}) do not
 * touch this map and are safe to call from background threads.
 */
public class DuelUserService {

    private final UserRepository userRepository;

    private final ExecutorService databaseExecutor;

    private final Logger logger;

    private final Map<UUID, DuelUser> users = new HashMap<>();

    public DuelUserService(UserRepository userRepository, ExecutorService databaseExecutor, Logger logger) {
        this.userRepository = userRepository;
        this.databaseExecutor = databaseExecutor;
        this.logger = logger;
    }

    /**
     * Registers a user as online, making them retrievable via {@link #getUser}.
     * Bukkit Main thread only.
     */
    public void putUser(@NotNull DuelUser duelUser) {
        users.put(duelUser.getUuid(), duelUser);
    }

    /**
     * Returns the given user if they are currently online (cached in memory),
     * or {@code null} if they are offline / not loaded. Bukkit Main thread only.
     */
    public @Nullable DuelUser getUser(UUID userUuid) {
        return users.get(userUuid);
    }

    /**
     * Removes a user from the online cache (e.g. on disconnect). Bukkit Main thread only.
     *
     * @return the removed user, or {@code null} if they weren't cached
     */
    public @Nullable DuelUser removeUser(@NotNull UUID userUuid) {
        return users.remove(userUuid);
    }

    /**
     * Loads (or creates, if absent) the given user's data from storage and
     * updates their username, running on {@code databaseExecutor}.
     * <p>
     * This does not populate the in-memory cache — callers are responsible
     * for calling {@link #putUser} with the result on the main thread if the
     * user should be tracked as online.
     *
     * @return a future completing with the loaded/created user, or {@code null}
     *         if the operation failed (see {@link UserRepository#findOrCreateAndUpdate})
     */
    public @NotNull CompletableFuture<@Nullable DuelUser> getOrCreateDataAndUpdate(@NotNull UUID userUuid, @NotNull String username) {
        return CompletableFutureHelper.supplyAsync(
                databaseExecutor,
                logger,
                "Failed to load duel user " + userUuid,
                () -> userRepository.findOrCreateAndUpdate(userUuid, username),
                null
        );
    }

    /**
     * Blocking pass-through to {@link UserRepository#applyDuelResult}.
     * <p>
     * Intentionally synchronous (not wrapped in a {@link CompletableFuture}):
     * this is meant to be invoked by {@code DatabaseRetryService}, which owns
     * its own async execution and retry scheduling. Wrapping this in another
     * async layer would double-wrap the result and break the retry mechanism.
     * Must not be called directly from the Bukkit Main Thread.
     */
    public @Nullable DuelResultSnapshot applyDuelResult(UUID winnerUuid, UUID loserUuid) {
        return userRepository.applyDuelResult(winnerUuid, loserUuid);
    }

}
