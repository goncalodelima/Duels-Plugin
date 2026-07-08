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

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.world.SlimeWorldInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import pt.gongas.duel.DuelPlugin;
import pt.gongas.duel.model.duel.Duel;
import pt.gongas.duel.model.duel.DuelStatus;
import pt.gongas.duel.service.duel.state.DuelStateRegistry;
import pt.gongas.duel.service.duel.storage.DuelLocationService;
import pt.gongas.duel.service.duel.world.DuelWorldService;
import pt.gongas.duel.util.config.Configuration;

import java.util.UUID;

public class DuelTimeoutRunnable extends BukkitRunnable {

    private final DuelPlugin plugin;

    private final AdvancedSlimePaperAPI advancedSlimePaperAPI;

    private final DuelStateRegistry duelStateRegistry;

    private final DuelWorldService duelWorldService;

    private final DuelLocationService duelLocationService;

    private final Component timeoutMessage;

    private static final long DUEL_WORLD_TIMEOUT = 20 * 60 * 60;

    private static final String WORLD_PREFIX = "duel-";

    public DuelTimeoutRunnable(DuelPlugin plugin, AdvancedSlimePaperAPI advancedSlimePaperAPI, DuelStateRegistry duelStateRegistry, DuelWorldService duelWorldService, DuelLocationService duelLocationService, Configuration lang) {
        this.plugin = plugin;
        this.advancedSlimePaperAPI = advancedSlimePaperAPI;
        this.duelStateRegistry = duelStateRegistry;
        this.duelWorldService = duelWorldService;
        this.duelLocationService = duelLocationService;
        this.timeoutMessage = MiniMessage.miniMessage().deserialize(lang.getString("timeout", "<red>Your duel exceeded the 1-hour time limit, so it has been declared a draw."));
    }

    @Override
    public void run() {

        for (SlimeWorldInstance slimeWorldInstance : advancedSlimePaperAPI.getLoadedWorlds()) {

            World bukkitWorld = slimeWorldInstance.getBukkitWorld();
            String worldName = bukkitWorld.getName();

            if (!worldName.startsWith(WORLD_PREFIX)) {
                continue;
            }

           if (bukkitWorld.getFullTime() <= DUEL_WORLD_TIMEOUT) {
               continue;
           }

           String worldNameUuid = worldName.substring(WORLD_PREFIX.length());
           UUID uuid = UUID.fromString(worldNameUuid);

            Duel duel = duelStateRegistry.getDuel(uuid);

            if (duel == null) {
                cleanupWorld(bukkitWorld, worldName, () -> {});
                continue;
            }

            if (duel.getStatus() != DuelStatus.MATCHED) {
                continue;
            }

            cleanupWorld(bukkitWorld, worldName, () -> duelStateRegistry.removeDuel(uuid));
        }

    }

    private void cleanupWorld(World world, String worldName, Runnable callback) {

        if (world.getPlayerCount() > 0) {

            for (Player player : world.getPlayers()) {
                player.sendMessage(timeoutMessage);
                duelLocationService.teleportToSpawn(player, duelLocationService.getExitLocation());
            }

            // Give players enough time to teleport to the spawn
            Bukkit.getScheduler().runTaskLater(
                    plugin,
                    () -> duelWorldService.attemptWorldDelete(worldName, callback),
                    20 * 5
            );

        } else {
            duelWorldService.attemptWorldDelete(worldName, callback);
        }
    }

}
