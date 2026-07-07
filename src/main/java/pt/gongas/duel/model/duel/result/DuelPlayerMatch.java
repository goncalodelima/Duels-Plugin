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

package pt.gongas.duel.model.duel.result;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record DuelPlayerMatch(@NotNull UUID matchedUuid, @NotNull String matchedName) {

    public static final DuelPlayerMatch EMPTY =
            new DuelPlayerMatch(new UUID(0L, 0L), "");

    public static @Nullable DuelPlayerMatch from(@NotNull String serialized) {

        String[] split = serialized.split("\\|");

        if (split.length != 2) {
            return null;
        }

        UUID matchedUuid = UUID.fromString(split[0]);
        String matchedName = split[1];

        return new DuelPlayerMatch(matchedUuid, matchedName);
    }

    public static @NotNull DuelPlayerMatch from(@NotNull UUID uuid, @NotNull String name) {
        return new DuelPlayerMatch(uuid, name);
    }

}
