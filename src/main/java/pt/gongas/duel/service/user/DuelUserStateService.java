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

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pt.gongas.duel.DuelPlugin;
import pt.gongas.duel.model.user.DuelUserSnapshotApplier;
import pt.gongas.duel.redis.duel.DuelResultPublisher;
import pt.gongas.duel.model.duel.result.DuelResultSnapshot;
import pt.gongas.duel.service.retry.RetryService;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Orchestrates persisting a duel's result and propagating it to whichever
 * server(s) need to know about it (the winner's and loser's servers, which
 * may or may not be this one).
 * <p>
 * This class is intentionally thin: it wires {@link RetryService} (for
 * resilience against transient database failures), {@link DuelUserService}
 * (the actual persistence call), and {@link DuelResultPublisher}/
 * {@link DuelUserSnapshotApplier} (for cross-server propagation) together,
 * without owning any of that logic itself.
 */
public class DuelUserStateService {

    private final DuelPlugin plugin;

    private final DuelUserService duelUserService;

    private final RetryService retryService;

    private final DuelResultPublisher duelResultPublisher;

    private final DuelUserSnapshotApplier duelUserSnapshotApplier;

    private final ExecutorService databaseExecutor;

    private static final int MAX_RETRIES = 7;

    public DuelUserStateService(DuelPlugin plugin, DuelUserService duelUserService, RetryService retryService, DuelResultPublisher duelResultPublisher, DuelUserSnapshotApplier duelUserSnapshotApplier, ExecutorService databaseExecutor) {
        this.plugin = plugin;
        this.duelUserService = duelUserService;
        this.retryService = retryService;
        this.duelResultPublisher = duelResultPublisher;
        this.duelUserSnapshotApplier = duelUserSnapshotApplier;
        this.databaseExecutor = databaseExecutor;
    }

    /**
     * Persists the outcome of a duel (win/loss stats, etc.) with automatic
     * retries on transient failure, then propagates the result once persisted.
     * <p>
     * The actual database write ({@link DuelUserService#applyDuelResult}) is
     * a blocking call by design — it's meant to be driven entirely by
     * {@link RetryService}, which owns the async execution and backoff
     * scheduling. See {@link DuelUserService#applyDuelResult} for why it must
     * not be wrapped in its own {@code CompletableFuture}.
     *
     * @param winnerUuid the UUID of the duel's winner
     * @param loserUuid  the UUID of the duel's loser
     * @return a future completing with the persisted result snapshot, or
     *         {@code null} if all retry attempts were exhausted (already
     *         logged by {@link RetryService} in that case)
     */
    public @NotNull CompletableFuture<@Nullable DuelResultSnapshot> applyDuelResultWithRetry(@NotNull UUID winnerUuid, @NotNull UUID loserUuid) {

        CompletableFuture<DuelResultSnapshot> future = new CompletableFuture<>();

        // RetryService takes ownership of future: it will complete it either
        // with the result on success, or with null once MAX_RETRIES is exhausted.
        retryService.executeRetry(
                future,
                databaseExecutor,
                MAX_RETRIES,
                () -> duelUserService.applyDuelResult(winnerUuid, loserUuid)
        );

        future.whenComplete((result, ignored) -> {

            if (result == null) {
                // Already logged by RetryService; nothing more to do here
                return;
            }

            // Propagation touches other services/state that expect to be
            // accessed from the main thread, so hop back onto it here.
            Bukkit.getScheduler().runTask(plugin, () -> handle(result));

        });

        return future;
    }

    /**
     * Applies a persisted duel result locally if the affected player(s) are
     * on this server, or publishes it over Redis for the correct server(s)
     * to pick up otherwise.
     * <p>
     * Must be called on the main thread, since {@link DuelUserSnapshotApplier}
     * is expected to touch online-player/cache state that isn't thread-safe.
     *
     * @param result the duel result to apply/propagate
     */
    public void handle(@NotNull DuelResultSnapshot result) {

        boolean handledLocally = duelUserSnapshotApplier.applyRaw(result);

        if (!handledLocally) {
            duelResultPublisher.publish(result);
        }

    }

}
