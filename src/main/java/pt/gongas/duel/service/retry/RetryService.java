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

package pt.gongas.duel.service.retry;

import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Retries a blocking operation with exponential backoff, completing
 * a {@link CompletableFuture} once the operation succeeds or all
 * retries are exhausted.
 * <p>
 * Retries are scheduled on a dedicated single-threaded scheduler,
 * separate from the provided executor, so scheduling retries never
 * competes with or blocks the execution of the actual task.
 */
public class RetryService {

    private final Logger logger;

    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor();

    private static final int MAX_RETRIES = 7;

    public RetryService(Logger logger) {
        this.logger = logger;
    }

    /**
     * Executes {@code task} on the provided executor, retrying with
     * exponential backoff (8s, 16s, 32s, ... doubling each attempt)
     * if it throws.
     * <p>
     * On success, completes {@code future} with the result. On final
     * failure (attempts exhausted), logs the error and completes
     * {@code future} with {@code null} rather than exceptionally —
     * callers must treat a {@code null} result as "failed after all
     * retries", not as a legitimate empty result.
     * <p>
     * <b>Important:</b> {@code attempts} is expected to start at
     * {@link #MAX_RETRIES} on the initial call. The backoff delay is
     * computed as {@code MAX_RETRIES - attempts}, so calling this with
     * an initial {@code attempts} value different from
     * {@link #MAX_RETRIES} will produce an incorrect or inconsistent
     * backoff schedule.
     *
     * @param future
     *         the future to complete with the eventual result
     *         (or {@code null} if all retries are exhausted)
     * @param executor
     *         the executor on which the task will be executed
     * @param attempts
     *         remaining retry attempts; should be
     *         {@link #MAX_RETRIES} on the first call
     * @param task
     *         the blocking operation to attempt
     */
    public <T> void executeRetry(
            CompletableFuture<T> future,
            ExecutorService executor,
            int attempts,
            Supplier<T> task
    ) {

        CompletableFuture
                .supplyAsync(task, executor)
                .whenComplete((result, ex) -> {

                    if (ex == null) {
                        future.complete(result);
                        return;
                    }

                    if (attempts <= 0) {
                        logger.log(Level.SEVERE, "Retry operation failed after retries", ex);
                        future.complete(null);
                        return;
                    }

                    logger.log(Level.WARNING, "Retry failed, retrying... attempts left: " + (attempts - 1), ex);

                    int attemptIndex = MAX_RETRIES - attempts;
                    long delay = 8L << attemptIndex;

                    retryExecutor.schedule(
                            () -> executeRetry(future, executor, attempts - 1, task),
                            delay,
                            TimeUnit.SECONDS
                    );

                });
    }

    /**
     * Retries an already-asynchronous operation with exponential backoff.
     * <p>
     * This method is intended for operations that already control their own
     * asynchronous execution and return a {@link CompletableFuture}.
     * <p>
     * The {@link Supplier} is used instead of receiving a {@link CompletableFuture}
     * directly because a future represents a single execution result and cannot be
     * started again after completion. The supplier creates a new asynchronous
     * operation for each retry attempt.
     * <p>
     * Each retry waits for an increasing delay before executing the operation again.
     * If any attempt succeeds, the returned future is completed with its result.
     * If all attempts fail, the returned future is completed exceptionally with the
     * last error.
     *
     * @param attempts remaining retry attempts; should be {@link #MAX_RETRIES} on
     *                 the first call
     * @param task     supplier that creates a new asynchronous operation for each
     *                 attempt
     * @param <T>      the result type of the asynchronous operation
     * @return a future that completes with the operation result or exceptionally
     *         after all retries are exhausted
     */
    public <T> CompletableFuture<T> executeRetryAsync(
            int attempts,
            Supplier<CompletableFuture<T>> task
    ) {

        CompletableFuture<T> future = new CompletableFuture<>();

        task.get().whenComplete((result, ex) -> {

            if (ex == null) {
                future.complete(result);
                return;
            }

            if (attempts <= 0) {
                future.completeExceptionally(ex);
                return;
            }

            int attemptIndex = MAX_RETRIES - attempts;
            long delay = 8L << attemptIndex;

            retryExecutor.schedule(
                    () -> executeRetryAsync(attempts - 1, task)
                            .whenComplete((r, e) -> {
                                if (e != null) {
                                    future.completeExceptionally(e);
                                } else {
                                    future.complete(r);
                                }
                            }),
                    delay,
                    TimeUnit.SECONDS
            );

        });

        return future;
    }

}
