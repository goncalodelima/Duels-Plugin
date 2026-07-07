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
import pt.gongas.database.Database;
import pt.gongas.database.executor.DatabaseExecutor;
import pt.gongas.duel.exception.DataAccessException;
import pt.gongas.duel.exception.InvalidDuelStateException;
import pt.gongas.duel.model.user.DuelUser;
import pt.gongas.duel.repository.user.adapter.UserAdapter;
import pt.gongas.duel.model.duel.result.DuelResultSnapshot;
import pt.gongas.duel.model.duel.result.DuelUserSnapshot;
import pt.gongas.economy.shared.util.UUIDConverter;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MySqlUserRepository implements UserRepository {

    private final Logger logger;

    private final Database database;

    private final UserAdapter adapter = new UserAdapter();

    public MySqlUserRepository(Logger logger, Database database) {
        this.logger = logger;
        this.database = database;
        setup();
    }

    @Override
    public void setup() {

        try (DatabaseExecutor executor = database.execute()) {

            executor.query("""
                            CREATE TABLE IF NOT EXISTS duel_user_account(
                                uuid BINARY(16) NOT NULL,
                                username VARCHAR(16) NOT NULL,
                                last_login_date DATETIME NOT NULL,
                                wins INT NOT NULL DEFAULT 0,
                                losses INT NOT NULL DEFAULT 0,
                                streak INT NOT NULL DEFAULT 0,
                                max_streak INT NOT NULL DEFAULT 0,
                                version BIGINT NOT NULL DEFAULT 0,
                                PRIMARY KEY (uuid)
                            );
                            """)
                    .write();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error creating tables", e);
        }

    }

    @Override
    public @Nullable DuelUser findOrCreateAndUpdate(@NonNull UUID userUuid, @NonNull String username) {

        try (DatabaseExecutor executor = database.execute();
             Connection connection = executor.getHikariConnection().getConnection()) {

            byte[] uuidBytes = UUIDConverter.convert(userUuid);

            executor.query("""
                            INSERT INTO duel_user_account (uuid, username, last_login_date)
                            VALUES (?, ?, ?)
                            ON DUPLICATE KEY UPDATE
                                username = VALUES(username),
                                last_login_date = VALUES(last_login_date)
                            """)
                    .write(statement -> {
                        statement.set(1, uuidBytes);
                        statement.set(2, username);
                        statement.set(3, LocalDateTime.now());
                    }, connection);

            return executor.query("""
                            SELECT * FROM duel_user_account
                            WHERE uuid = ?
                            """)
                    .readOne(statement -> statement.set(1, uuidBytes), adapter)
                    .orElse(null);

        } catch (Exception e) {
            throw new DataAccessException(e);
        }

    }

    @Override
    public @NotNull DuelResultSnapshot applyDuelResult(@NonNull UUID winnerUuid, @NonNull UUID loserUuid) {

        byte[] winnerBytes = UUIDConverter.convert(winnerUuid);
        byte[] loserBytes = UUIDConverter.convert(loserUuid);

        try (DatabaseExecutor executor = database.execute();
             Connection connection = executor.getHikariConnection().getConnection()) {

            executor.startTransaction(connection);

            try {

                // Winner
                executor.query("""
                                UPDATE duel_user_account
                                SET wins = wins + 1,
                                    max_streak = GREATEST(max_streak, streak + 1),
                                    streak = streak + 1,
                                    version = version + 1
                                WHERE uuid = ?
                                """)
                        .write(statement -> statement.set(1, winnerBytes), connection);

                DuelUserSnapshot winnerSnapshot = executor.query("""
                                SELECT *
                                FROM duel_user_account
                                WHERE uuid = ?
                                """)
                        .readOne(statement -> statement.set(1, winnerBytes),
                                query -> {

                                    long version = query.getLong("version");
                                    int wins = query.getInt("wins");
                                    int streak = query.getInt("streak");
                                    int maxStreak = query.getInt("max_streak");
                                    int losses = query.getInt("losses");

                                    return new DuelUserSnapshot(winnerUuid, version, wins, streak, maxStreak, losses);

                                }, connection)
                        .orElseThrow(() ->
                                new InvalidDuelStateException("Winner version not found for UUID " + winnerUuid)
                        );

                // Loser
                executor.query("""
                                UPDATE duel_user_account
                                SET losses = losses + 1,
                                    streak = 0,
                                    version = version + 1
                                WHERE uuid = ?
                                """)
                        .write(stmt -> stmt.set(1, loserBytes), connection);

                DuelUserSnapshot loserSnapshot = executor.query("""
                                SELECT *
                                FROM duel_user_account
                                WHERE uuid = ?
                                """)
                        .readOne(statement -> statement.set(1, loserBytes),
                                query -> {

                                    long version = query.getLong("version");
                                    int wins = query.getInt("wins");
                                    int streak = query.getInt("streak");
                                    int maxStreak = query.getInt("max_streak");
                                    int losses = query.getInt("losses");

                                    return new DuelUserSnapshot(loserUuid, version, wins, streak, maxStreak, losses);

                                }, connection)
                        .orElseThrow(() ->
                                new InvalidDuelStateException("Winner version not found for UUID " + loserUuid)
                        );

                connection.commit();

                return new DuelResultSnapshot(winnerSnapshot, loserSnapshot);

            } catch (Exception e) {
                connection.rollback();
                throw new DataAccessException(e);
            }

        } catch (Exception e) {
            throw new DataAccessException(e);
        }

    }

}
