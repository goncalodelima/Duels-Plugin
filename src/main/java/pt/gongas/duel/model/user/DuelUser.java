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

import org.jetbrains.annotations.NotNull;
import pt.gongas.duel.model.duel.result.DuelUserSnapshot;

import java.util.UUID;

public class DuelUser {

    private final UUID uuid;

    private final String name;

    private int wins;

    private int losses;

    private int streak;

    private int maxStreak;

    private long version;

    public DuelUser(UUID uuid, String name, int wins, int losses, int streak, int maxStreak) {
        this.uuid = uuid;
        this.name = name;
        this.wins = wins;
        this.losses = losses;
        this.streak = streak;
        this.maxStreak = maxStreak;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public int getWins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }

    public int getLosses() {
        return losses;
    }

    public void setLosses(int losses) {
        this.losses = losses;
    }

    public int getStreak() {
        return streak;
    }

    public void setStreak(int streak) {
        this.streak = streak;
    }

    public int getMaxStreak() {
        return maxStreak;
    }

    public void setMaxStreak(int maxStreak) {
        this.maxStreak = maxStreak;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public void applySnapshotIfNewer(@NotNull DuelUserSnapshot snapshot) {

        if (snapshot.version() <= version) {
            return;
        }

        version = snapshot.version();
        wins = snapshot.wins();
        losses = snapshot.losses();
        streak = snapshot.streak();
        maxStreak = snapshot.maxStreak();
    }

}
