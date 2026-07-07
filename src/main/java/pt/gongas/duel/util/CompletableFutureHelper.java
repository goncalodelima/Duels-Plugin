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

package pt.gongas.duel.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CompletableFutureHelper {

    private CompletableFutureHelper() {}

    public static CompletableFuture<Void> fireAndForget(Executor executor, Logger logger, String errorMessage, Runnable task) {
        return CompletableFuture.runAsync(task, executor)
                .exceptionally(ex -> {
                    logger.log(Level.SEVERE, errorMessage, ex);
                    return null;
                });
    }

    public static CompletableFuture<Void> runAsync(Executor executor, Logger logger, Runnable task) {
        return CompletableFuture.runAsync(task, executor)
                .exceptionally(ex -> {
                    logger.log(Level.SEVERE, "Async run failed", ex);
                    return null;
                });
    }

    public static <T> CompletableFuture<T> supplyAsync(Executor executor, Logger logger, Supplier<T> task, T exceptionValue) {
        return CompletableFuture.supplyAsync(task, executor)
                .exceptionally(ex -> {
                    logger.log(Level.SEVERE, "Async run failed", ex);
                    return exceptionValue;
                });
    }

    public static <T> CompletableFuture<T> supplyAsync(Executor executor, Logger logger, String errorMessage, Supplier<T> task, T exceptionValue) {
        return CompletableFuture.supplyAsync(task, executor)
                .exceptionally(ex -> {
                    logger.log(Level.SEVERE, errorMessage, ex);
                    return exceptionValue;
                });
    }

}
