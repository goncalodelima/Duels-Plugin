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

package pt.gongas.duel.repository.user.adapter;

import pt.gongas.database.adapter.DatabaseAdapter;
import pt.gongas.database.executor.DatabaseQuery;
import pt.gongas.duel.model.user.DuelUser;
import pt.gongas.duel.util.UUIDConverter;

import java.sql.SQLException;
import java.util.UUID;

public class UserAdapter implements DatabaseAdapter<DuelUser> {

    @Override
    public DuelUser adapt(DatabaseQuery query) throws SQLException {

        UUID uuid = UUIDConverter.convert(query.getBytes("uuid"));
        String username = query.getString("username");
        int wins = query.getInt("wins");
        int losses = query.getInt("losses");
        int streak = query.getInt("streak");
        int maxStreak = query.getInt("max_streak");

        return new DuelUser(
                uuid,
                username,
                wins,
                losses,
                streak,
                maxStreak
        );

    }

}
