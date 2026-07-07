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

package pt.gongas.duel.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pt.gongas.duel.model.duel.Duel;
import pt.gongas.duel.model.duel.DuelStatus;
import pt.gongas.duel.model.queue.WaitingPlayer;
import pt.gongas.duel.service.duel.DuelAcceptanceService;
import pt.gongas.duel.service.duel.matchmaking.DuelMatchmakingService;
import pt.gongas.duel.util.config.Configuration;

import java.util.UUID;

public class DuelMatchmakingListener implements Listener {

    private final String serverId;

    private final DuelAcceptanceService duelAcceptanceService;

    private final DuelMatchmakingService duelMatchmakingService;

    private final Component opponentOfflineMessage;

    public DuelMatchmakingListener(String serverId, DuelAcceptanceService duelAcceptanceService, DuelMatchmakingService duelMatchmakingService, Configuration lang) {

        this.serverId = serverId;
        this.duelAcceptanceService = duelAcceptanceService;
        this.duelMatchmakingService = duelMatchmakingService;

        MiniMessage miniMessage = MiniMessage.miniMessage();

        opponentOfflineMessage = miniMessage.deserialize(lang.getString("opponent-offline-message", "<red>Your opponent went offline in the meantime! Cancelling the duel :("));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {

        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        WaitingPlayer opponentPlayer = duelMatchmakingService.removeOpponent(playerUuid);

        if (opponentPlayer == null) {
            return;
        }

        Player opponent = Bukkit.getPlayer(opponentPlayer.playerUuid());

        if (opponent == null) {
            player.sendMessage(opponentOfflineMessage);
            return;
        }

        Duel duel = new Duel(
                serverId,
                opponentPlayer.playerUuid(),
                opponent.getName(),
                playerUuid,
                player.getName(),
                0,
                null,
                System.currentTimeMillis(),
                -1,
                DuelStatus.WAITING
        );

        duelAcceptanceService.validateDuel(duel, opponent, player, opponentPlayer, null, null);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {

        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        duelMatchmakingService.leaveQueue(playerUuid);
    }

}
