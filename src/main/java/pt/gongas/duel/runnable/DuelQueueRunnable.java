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

package pt.gongas.duel.runnable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import pt.gongas.duel.model.queue.QueuePlayer;
import pt.gongas.duel.service.duel.matchmaking.DuelMatchmakingService;
import pt.gongas.duel.util.TimeUtil;
import pt.gongas.duel.util.config.Configuration;

public class DuelQueueRunnable extends BukkitRunnable {

    private final DuelMatchmakingService duelMatchmakingService;

    private final Component duel;

    public DuelQueueRunnable(DuelMatchmakingService duelMatchmakingService, Configuration lang) {

        this.duelMatchmakingService = duelMatchmakingService;

        MiniMessage miniMessage = MiniMessage.miniMessage();
        this.duel = miniMessage.deserialize(lang.getString("duel-queue-actionbar", "<yellow>Queue wait time: <aqua><queue_time>"));
    }

    @Override
    public void run() {

        for (QueuePlayer queuePlayer : duelMatchmakingService.getLocalMap().values()) {

            Player player = queuePlayer.player();
            long now = System.currentTimeMillis();
            long difference = now - queuePlayer.insertedAt();

            player.sendActionBar(duel.replaceText(
                    TextReplacementConfig
                            .builder()
                            .matchLiteral("<queue_time>")
                            .replacement(TimeUtil.formatTime(difference))
                            .build()
            ));

        }

    }

}
