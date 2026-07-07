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

package pt.gongas.duel.service;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.util.UUID;

public class PlayerNameService {

    private final RMap<String, String> nameToUuid; // put/remove on VelocityRedisBridge

    private final RMap<String, String> uuidToName; // put/remove on VelocityRedisBridge

    public PlayerNameService(RedissonClient redis) {
        this.nameToUuid = redis.getMap("player_name_to_uuid", StringCodec.INSTANCE);
        this.uuidToName = redis.getMap("player_uuid_to_name", StringCodec.INSTANCE);
    }

    public @Nullable String getName(@NotNull UUID uuid) {
        return uuidToName.get(uuid.toString());
    }

    public @Nullable UUID getUuid(@NotNull String name) {
        String uuid = nameToUuid.get(name.toLowerCase());
        return uuid != null ? UUID.fromString(uuid) : null;
    }

}
