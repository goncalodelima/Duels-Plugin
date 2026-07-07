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

package pt.gongas.duel.model.user;

import org.jetbrains.annotations.Nullable;
import pt.gongas.duel.model.duel.result.DuelResultSnapshot;
import pt.gongas.duel.service.user.DuelUserService;

public class DuelUserSnapshotApplier {

    private final DuelUserService duelUserService;

    public DuelUserSnapshotApplier(DuelUserService duelUserService) {
        this.duelUserService = duelUserService;
    }

    /**
     * Resolves the winner/loser {@link DuelUser} instances from the snapshot and applies it.
     * <p>
     * Must be called on the main (Bukkit) thread, since it reads from the in-memory
     * user cache and mutates {@link DuelUser} state, which is not thread-safe.
     */
    public boolean applyRaw(DuelResultSnapshot result) {

        DuelUser winner = duelUserService.getUser(result.winner().uuid());
        DuelUser loser = duelUserService.getUser(result.loser().uuid());

        return apply(winner, loser, result);
    }

    /**
     * Applies the snapshot to the given (already resolved) users, if present.
     * <p>
     * Must be called on the main (Bukkit) thread, since it mutates {@link DuelUser}
     * state, which is not thread-safe.
     */
    public boolean apply(@Nullable DuelUser winner, @Nullable DuelUser loser, DuelResultSnapshot result) {

        if (winner != null) {
            winner.applySnapshotIfNewer(result.winner());
        }

        if (loser != null) {
            loser.applySnapshotIfNewer(result.loser());
        }

        return winner != null || loser != null;
    }

}
