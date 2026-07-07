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

package pt.gongas.duel.repository.user;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import pt.gongas.duel.exception.DataAccessException;
import pt.gongas.duel.model.user.DuelUser;
import pt.gongas.duel.model.duel.result.DuelResultSnapshot;

import java.util.UUID;

/**
 * Provides persistence and retrieval for {@link DuelUser} data.
 * <p>
 * All methods in this interface perform blocking I/O and must never be called
 * on the main (Bukkit) thread. Callers are responsible for dispatching these
 * calls to an appropriate background executor.
 */
public interface UserRepository {

    /**
     * Prepares the underlying storage for use (e.g. creating tables/indexes).
     * <p>
     * Must be called once during plugin startup, before any other method here.
     */
    void setup();

    /**
     * Loads the user with the given {@code userUuid}, creating a new record
     * (using {@code username}) if none exists yet, and updates the stored
     * username if it has changed.
     * <p>
     * Internally this performs an upsert followed by a separate {@code SELECT}.
     * These two statements are not wrapped in a single transaction, so there is a
     * theoretical race window between them: if the row is deleted between the
     * upsert and the read, this method returns {@code null} even though the
     * upsert itself succeeded.
     * <p>
     * This is intentional and not treated as an error. In practice, rows are only
     * ever deleted via manual/direct database intervention (e.g. an admin running
     * a query against the MySQL client) — there is no in-plugin delete feature —
     * making this race effectively impossible during normal operation. Wrapping
     * both statements in an explicit transaction would close this window, but at
     * the cost of holding a row lock for longer and adding overhead to every
     * single login, to guard against a scenario that requires manual DB access to
     * trigger. That trade-off was judged not worth it; if a delete feature is ever
     * added to the plugin, this decision should be revisited.
     *
     * @return the loaded/created user, or {@code null} if no matching row was
     *         found on the follow-up read (e.g. due to a concurrent external deletion)
     * @throws DataAccessException if the underlying storage operation fails
     */
    @Nullable DuelUser findOrCreateAndUpdate(@NotNull UUID userUuid, @NotNull String username) throws DataAccessException;

    /**
     * Atomically applies the result of a duel between the given users: increments
     * the winner's wins/streak (and max streak if exceeded) and the loser's
     * losses (resetting their streak), persisting both changes as a single
     * database operation.
     * <p>
     * Unlike {@link #findOrCreateAndUpdate}, both updates here must happen within
     * the same transaction — a partial update (e.g. winner updated but loser not)
     * would leave the two users' stats permanently inconsistent with each other,
     * which is a real and meaningful failure mode, not just a rare edge case.
     * <p>
     * This method is expected to be called by a retry mechanism (see
     * {@code DatabaseRetryService}), since it may fail due to transient storage
     * errors (e.g. connection drops, lock timeouts, deadlocks between concurrent
     * duels sharing a user).
     *
     * @return a snapshot of both users' resulting state after the update
     * @throws DataAccessException if the underlying storage operation fails
     */
    @NotNull DuelResultSnapshot applyDuelResult(@NonNull UUID winnerUuid, @NonNull UUID loserUuid);

}
