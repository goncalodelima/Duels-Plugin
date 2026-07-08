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

package pt.gongas.duel.service.duel.state;

import org.jetbrains.annotations.NotNull;
import pt.gongas.duel.model.duel.Duel;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime state registry for duels currently active on this server.
 * <p>
 * Stores active duels by player UUID and keeps track of players temporarily
 * blocked from executing actions while they are involved in duel transitions.
 * <p>
 * This class does not persist any data. All state is lost when the server
 * instance shuts down.
 */
public class DuelStateRegistry {

    /**
     * Players temporarily blocked from interacting with duel commands.
     * <p>
     * This collection may be accessed from multiple threads, therefore it uses
     * a concurrent set implementation.
     */
    private final Set<UUID> blockedPlayers = ConcurrentHashMap.newKeySet();

    /**
     * Active duels indexed by participant UUID.
     * <p>
     * This map is only accessed from the Bukkit main thread.
     */
    private final Map<UUID, Duel> occurringDuels = new HashMap<>();

    /**
     * Registers an active duel for both participants.
     * <p>
     * This method must only be called from the Bukkit main thread because the
     * underlying registry is not thread-safe.
     *
     * @param challengerUuid challenger UUID
     * @param challengedUuid opponent UUID
     * @param duel duel instance to register
     */
    public void addPlayerDuel(@NotNull UUID challengerUuid, @NotNull UUID challengedUuid, @NotNull Duel duel) {
        occurringDuels.put(challengerUuid, duel);
        occurringDuels.put(challengedUuid, duel);
    }

    /**
     * Removes the duel associated with a player from the registry.
     * <p>
     * This method must only be called from the Bukkit main thread because the
     * underlying registry is not thread-safe.
     *
     * @param playerUuid participant UUID
     * @return the removed duel, or {@code null} if the player was not in a duel
     */
    public Duel removePlayerDuel(@NotNull UUID playerUuid) {
        return occurringDuels.remove(playerUuid);
    }

    public int getOccurringDuelsSize() {
        return occurringDuels.size();
    }

    /**
     * Removes an active duel from the registry for both participants.
     * <p>
     * The duel is marked as invalid before being removed so any existing
     * references know that the duel has already ended.
     */
    public Duel removeDuel(@NotNull UUID playerUuid) {

        Duel duel = occurringDuels.remove(playerUuid);

        if (duel != null) {

            duel.setValid(false);

            UUID targetUuid = duel.getTargetUuid();

            if (targetUuid != null && !playerUuid.equals(targetUuid)) {
                occurringDuels.remove(targetUuid);
            } else {
                occurringDuels.remove(duel.getChallengerUuid());
            }

        }

        return duel;
    }

    /**
     * Returns the active duel associated with a player.
     * <p>
     * This method must only be called from the Bukkit main thread because the
     * underlying registry is not thread-safe.
     *
     * @param playerUuid participant UUID
     * @return the current duel, or {@code null} if the player is not in one
     */
    public Duel getDuel(@NotNull UUID playerUuid) {
        return occurringDuels.get(playerUuid);
    }

    /**
     * Adds players to the temporary blocked list.
     * <p>
     * This method is thread-safe and may be called from any thread.
     *
     * @param challengerUuid challenger UUID
     * @param challengedUuid opponent UUID
     */
    public void addBlockedPlayers(@NotNull UUID challengerUuid, @NotNull UUID challengedUuid) {
        blockedPlayers.add(challengerUuid);
        blockedPlayers.add(challengedUuid);
    }

    /**
     * Removes players from the temporary blocked list.
     * <p>
     * This method is thread-safe and may be called from any thread.
     *
     * @param challengerUuid challenger UUID
     * @param challengedUuid opponent UUID
     */
    public void removeBlockedPlayers(@NotNull UUID challengerUuid, @NotNull UUID challengedUuid) {
        blockedPlayers.remove(challengerUuid);
        blockedPlayers.remove(challengedUuid);
    }

    /**
     * Checks whether a player is temporarily blocked.
     * <p>
     * This method is thread-safe and may be called from any thread.
     *
     * @param playerUuid player UUID
     * @return {@code true} if the player is blocked
     */
    public boolean isBlockedPlayer(@NotNull UUID playerUuid) {
        return blockedPlayers.contains(playerUuid);
    }

}