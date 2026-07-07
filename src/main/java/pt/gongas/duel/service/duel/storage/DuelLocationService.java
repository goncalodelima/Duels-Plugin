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

package pt.gongas.duel.service.duel.storage;

import net.william278.huskhomes.BukkitHuskHomes;
import net.william278.huskhomes.api.HuskHomesAPI;
import net.william278.huskhomes.position.Position;
import net.william278.huskhomes.teleport.Teleport;
import net.william278.huskhomes.teleport.Teleportable;
import net.william278.huskhomes.util.TransactionResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pt.gongas.duel.DuelPlugin;
import pt.gongas.duel.model.duel.DuelLocation;
import pt.gongas.duel.util.config.Configuration;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the three configurable locations involved in a duel: where the
 * challenger and challenged player are teleported to when a duel starts,
 * and where both are teleported back to once it ends.
 * <p>
 * Locations are loaded from {@code locations.yml} on construction and
 * persisted back to it whenever they're set via one of the {@code set*}
 * methods (typically from an in-game "set location" command).
 */
public class DuelLocationService {

    private final DuelPlugin plugin;

    private final BukkitHuskHomes huskPlugin;

    private final String serverId;

    private final Logger logger;

    private final Configuration locations;

    private Location exitLocation;

    private DuelLocation challengerLocation;

    private DuelLocation challengedLocation;

    public DuelLocationService(DuelPlugin plugin, String serverId, Logger logger, Configuration locations) {
        this.plugin = plugin;
        this.huskPlugin = BukkitHuskHomes.getPlugin(BukkitHuskHomes.class);
        this.serverId = serverId;
        this.logger = logger;

        this.locations = locations;

        this.exitLocation = locations.getSerializable("exit-spawn", Location.class);
        this.challengerLocation = locations.getSerializable("challenger-location", DuelLocation.class);
        this.challengedLocation = locations.getSerializable("challenged-location", DuelLocation.class);
    }

    public CompletableFuture<Boolean> teleportToSpawn(@NotNull Player player, @NotNull Location exitLocation) {

        if (serverId.contains("smp")) {
            return player.teleportAsync(exitLocation);
        }

        return HuskHomesAPI.getInstance().getSpawn().thenApplyAsync(optionalPosition -> {

            if (optionalPosition.isEmpty()) {
                logger.log(Level.SEVERE, "An error occurred while teleporting a player to the spawn because the spawn location is not set... Set the spawn location as soon as possible");
                return false;
            }

            if (!player.isConnected()) {
                return true;
            }

            Position spawn = optionalPosition.get();

            Teleportable teleporter = Teleportable.username(player.getName());

            return Teleport.builder(huskPlugin)
                    .teleporter(teleporter)
                    .actions(TransactionResolver.Action.SPAWN_TELEPORT)
                    .target(spawn)
                    .buildAndComplete(false, "");

        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));

    }

    public @Nullable Location getExitLocation() {
        return exitLocation;
    }

    public void setExitLocation(@Nullable Location exitLocation) {
        this.exitLocation = exitLocation;
        locations.set("exit-spawn", exitLocation);
        locations.saveConfig();
    }

    public @Nullable DuelLocation getChallengerLocation() {
        return challengerLocation;
    }

    public void setChallengerLocation(@Nullable DuelLocation challengerLocation) {
        this.challengerLocation = challengerLocation;
        locations.set("challenger-location", challengerLocation);
        locations.saveConfig();
    }

    public @Nullable DuelLocation getChallengedLocation() {
        return challengedLocation;
    }

    public void setChallengedLocation(@Nullable DuelLocation challengedLocation) {
        this.challengedLocation = challengedLocation;
        locations.set("challenged-location", challengedLocation);
        locations.saveConfig();
    }

}