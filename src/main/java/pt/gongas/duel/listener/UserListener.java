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
import pt.gongas.duel.DuelPlugin;
import pt.gongas.duel.service.user.DuelUserService;
import pt.gongas.duel.util.config.Configuration;

import java.util.UUID;

public class UserListener implements Listener {

    private final DuelPlugin plugin;

    private final DuelUserService duelUserService;

    private final Component loginErrorMessage;

    public UserListener(DuelPlugin plugin, DuelUserService duelUserService, Configuration lang) {
        this.plugin = plugin;
        this.duelUserService = duelUserService;
        this.loginErrorMessage = MiniMessage.miniMessage().deserialize(lang.getString("login-error", "<red>An unexpected error occurred. Please try reconnecting to the server"));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {

        Player player = event.getPlayer();
        UUID userUuid = player.getUniqueId();
        String username = player.getName();

        duelUserService.getOrCreateDataAndUpdate(userUuid, username)
                .thenAcceptAsync(duelUser -> {

                    if (!player.isConnected()) {
                        return;
                    }

                    if (duelUser == null) {
                        player.kick(loginErrorMessage);
                        return;
                    }

                    duelUserService.putUser(duelUser);

                }, Bukkit.getScheduler().getMainThreadExecutor(plugin));

    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {

        Player player = event.getPlayer();
        UUID userUuid = player.getUniqueId();

        duelUserService.removeUser(userUuid);
    }

}
