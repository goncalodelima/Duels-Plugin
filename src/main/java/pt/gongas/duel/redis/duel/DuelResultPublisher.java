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

package pt.gongas.duel.redis.duel;

import pt.gongas.duel.model.duel.result.DuelResultSnapshot;

public interface DuelResultPublisher {

    /**
     * Publishes a duel result snapshot to an external distribution channel.
     * <p>
     * This method must be non-blocking and return immediately. No blocking I/O
     * operations are allowed on the calling thread, which is expected to be the
     * Bukkit main thread in typical usage.
     * <p>
     * Implementations are responsible for handling asynchronous execution and
     * thread management internally.
     */
    void publish(DuelResultSnapshot snapshot);

}