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

import com.github.sirblobman.combatlogx.api.ICombatLogX;
import com.github.sirblobman.combatlogx.api.object.TagReason;
import com.github.sirblobman.combatlogx.api.object.TagType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pt.gongas.duel.DuelPlugin;
import pt.gongas.duel.model.duel.Duel;
import pt.gongas.duel.model.duel.DuelEndReason;
import pt.gongas.duel.model.duel.DuelStatus;
import pt.gongas.duel.service.duel.*;
import pt.gongas.duel.service.duel.network.DuelRedirectService;
import pt.gongas.duel.service.duel.state.DuelStateRegistry;
import pt.gongas.duel.service.duel.storage.DuelLocationService;
import pt.gongas.duel.service.duel.world.DuelWorldService;
import pt.gongas.duel.service.user.DuelUserStateService;
import pt.gongas.duel.util.UISoundUtil;
import pt.gongas.duel.util.config.Configuration;
import pt.gongas.economy.shared.api.EconomyApi;
import pt.gongas.economy.shared.user.User;

import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DuelListener implements Listener {

    private final DuelPlugin plugin;

    private final Logger logger;

    private final DuelStateRegistry duelStateRegistry;

    private final DuelRedirectService duelRedirectService;

    private final DuelWorldService duelWorldService;

    private final DuelLocationService duelLocationService;

    private final DuelUserStateService duelUserStateService;

    private final DuelAcceptanceService duelAcceptanceService;

    private final EconomyApi<Player> economyApi;

    private final ICombatLogX combatLogX;

    private final Component blockedPlayerMessage;

    private final Component blockedDuelMessage;

    private final Component blockedCollectDuelMessage;

    private final Component fighterLogoutMessage;

    private final Component fighterDeathMessage;

    private final Component duelFinishTitleMessage;

    private final Component duelFinishSubtitleMessage;

    private final Component challengerLogoutMessage;

    private static final String DUEL_EXIT_COMMAND = "/spawn";

    public DuelListener(DuelPlugin plugin, Logger logger, DuelStateRegistry duelStateRegistry, DuelRedirectService duelRedirectService, DuelWorldService duelWorldService, DuelLocationService duelLocationService, DuelUserStateService duelUserStateService, DuelAcceptanceService duelAcceptanceService, EconomyApi<Player> economyApi, ICombatLogX combatLogX, Configuration lang) {

        this.plugin = plugin;
        this.logger = logger;
        this.duelStateRegistry = duelStateRegistry;
        this.duelRedirectService = duelRedirectService;
        this.duelWorldService = duelWorldService;
        this.duelLocationService = duelLocationService;
        this.duelUserStateService = duelUserStateService;
        this.duelAcceptanceService = duelAcceptanceService;
        this.economyApi = economyApi;
        this.combatLogX = combatLogX;

        MiniMessage miniMessage = MiniMessage.miniMessage();

        this.blockedPlayerMessage = miniMessage.deserialize(lang.getString("blocked-player-message", "<red>You cannot execute commands while being teleported to the arena!"));
        this.blockedDuelMessage = miniMessage.deserialize(lang.getString("blocked-duel-message", "<red>You cannot type commands while in a duel!"));
        this.blockedCollectDuelMessage = miniMessage.deserialize(lang.getString("blocked-collect-duel-message", "<red>You can only type ‘/spawn’ during the item collection phase of a duel to leave the duel!"));
        this.fighterLogoutMessage = miniMessage.deserialize(lang.getString("collect-message", "<green>Your opponent has logged out, so you can collect their items for 2 minutes. Once you've collected them, you can leave the arena by typing '/spawn'."));
        this.fighterDeathMessage = miniMessage.deserialize(lang.getString("death-message", "<green>Your opponent has died, you can now collect their items for 2 minutes. Once you're done, use '/spawn' to leave the arena."));

        this.duelFinishTitleMessage = miniMessage.deserialize(lang.getString("duel-finish-title", "<green><bold>ᴅᴜᴇʟѕ"));
        this.duelFinishSubtitleMessage = miniMessage.deserialize(lang.getString("duel-finish-subtitle", "<red>Collect items from your opponent"));

        this.challengerLogoutMessage = miniMessage.deserialize(lang.getString("challenger-logout", "<red>The player who challenged you to a duel logged out in the meantime! :("));
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {

        plugin.getLogger().info("main thread player command event? " + Bukkit.isPrimaryThread());

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (duelStateRegistry.isBlockedPlayer(uuid)) {
            event.setCancelled(true);
            player.sendMessage(blockedPlayerMessage);
            UISoundUtil.playErrorSound(player);
        } else {

            Duel duel = duelStateRegistry.getDuel(uuid);

            if (duel != null) {

                plugin.getLogger().info("message: " + event.getMessage());

                if (duel.getStatus() != DuelStatus.COLLECT_ITEMS) {
                    event.setCancelled(true);
                    player.sendMessage(blockedDuelMessage);
                    UISoundUtil.playErrorSound(player);
                } else {

                    String message = event.getMessage().toLowerCase(Locale.ROOT);

                    if (message.equalsIgnoreCase(DUEL_EXIT_COMMAND) || message.startsWith(DUEL_EXIT_COMMAND + " ")) {

                        Location exitLocation = duelLocationService.getExitLocation();

                        event.setCancelled(true);

                        duelLocationService.teleportToSpawn(player, exitLocation)
                                .whenComplete((result, ex) -> {

                                    duelWorldService.attemptWorldDelete(duel.getChallengerUuid().toString(), () -> duelStateRegistry.removeDuel(uuid));

                                    if (ex != null) {
                                        plugin.getLogger().log(Level.SEVERE, "An exception occurred when teleporting the player after the player in a duel typed '/spawn' during item collection");
                                    }

                                });

                        if (exitLocation == null) {
                            throw new IllegalStateException("A duel has ended, and the exit location for teleporting the players to the spawn is not yet defined");
                        }

                    } else {
                        event.setCancelled(true);
                        player.sendMessage(blockedCollectDuelMessage);
                        UISoundUtil.playErrorSound(player);
                    }

                }

            }

        }

    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {

        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        Duel duel = duelRedirectService.removeRedirectingDuel(playerUuid);

        if (duel == null) {
            return;
        }

        Player challengerPlayer = Bukkit.getPlayer(duel.getChallengerUuid());

        if (challengerPlayer == null) {
            player.sendMessage(challengerLogoutMessage);
            UISoundUtil.playErrorSound(player);
            return;
        }

        // Add a delay because the loading of economy users is done asynchronously when a player joins.
        // This is only to save resources; it's not the absolute truth, as it's not a 100% secure solution
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> duelAcceptanceService.validateDuel(duel, challengerPlayer, player, null, duel.getCurrency(), duel.getCents()), 20
        );

    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerQuit(PlayerQuitEvent event) {

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Duel duel = duelStateRegistry.getDuel(uuid);

        if (duel == null || duel.getStatus() != DuelStatus.MATCHED) {
            return;
        }

        handleDuelEndByLoss(player, uuid, duel, DuelEndReason.LOGOUT);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {

        Entity damager = event.getDamager();
        Entity damaged = event.getEntity();

        if (!(damager instanceof Player damagerPlayer) || !(damaged instanceof Player damagedPlayer)) {
            return;
        }

        Duel duel = duelStateRegistry.getDuel(damagerPlayer.getUniqueId());

        if (duel == null || duel.getStatus() != DuelStatus.MATCHED) {
            event.setCancelled(true);
            return;
        }

        UUID opponentUuid = damagerPlayer.getUniqueId().equals(duel.getChallengerUuid()) ? duel.getTargetUuid() : duel.getChallengerUuid();

        if (opponentUuid == null || !damagedPlayer.getUniqueId().equals(opponentUuid)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Duel duel = duelStateRegistry.getDuel(uuid);

        if (duel == null || duel.getStatus() != DuelStatus.MATCHED) {
            return;
        }

        handleDuelEndByLoss(player, uuid, duel, DuelEndReason.DEATH);
    }

    private void handleDuelEndByLoss(Player loser, UUID loserUuid, Duel duel, DuelEndReason duelEndReason) {

        UUID challengerUuid = duel.getChallengerUuid();

        UUID opponentUuid = loserUuid.equals(challengerUuid)
                ? duel.getTargetUuid()
                : challengerUuid;

        if (opponentUuid == null) {
            throw new IllegalStateException("Opponent uuid is null in active duel");
        }

        Player opponent = Bukkit.getPlayer(opponentUuid);

        if (opponent == null) {
            throw new IllegalStateException("Opponent player is null in active duel");
        }

        if (duelEndReason == DuelEndReason.LOGOUT) {
            combatLogX.getCombatManager().tag(loser, null, TagType.UNKNOWN, TagReason.UNKNOWN);
            opponent.sendMessage(fighterLogoutMessage);
        } else {
            opponent.sendMessage(fighterDeathMessage);
        }

        opponent.sendTitlePart(TitlePart.TITLE, duelFinishTitleMessage);
        opponent.sendTitlePart(TitlePart.SUBTITLE, duelFinishSubtitleMessage);

        duel.setStatus(DuelStatus.COLLECT_ITEMS);
        duelStateRegistry.removePlayerDuel(loserUuid);

        duelUserStateService.applyDuelResultWithRetry(opponentUuid, loserUuid);

        if (duel.getCurrency() != null) {

            User loserUser = economyApi.getUserService().get(loserUuid);
            User opponentUser = economyApi.getUserService().get(opponentUuid);

            if (loserUser == null || opponentUser == null) {
                logger.log(Level.SEVERE, "An error occurred while removing balance from the loser and adding it to the winner. loserName=" + loser.getName() + ", opponentName=" + opponent.getName() + ". Is the loserUser null? " + (loserUser == null) + "; Is the opponentUser null? " + (opponentUser == null));
                return;
            }

            economyApi.payCurrencyAndNotifyIfNeeded(loser, opponent, loserUser, opponentUser, duel.getCurrency(), duel.getCents() / 100D, false)
                    .exceptionally(ex -> {
                        logger.log(Level.SEVERE, "An error occurred while removing balance from the loser and adding it to the winner", ex);
                        return null;
                    });

        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {

            if (!duel.isValid()) {
                return;
            }

            if (!opponent.isConnected()) {
                duelWorldService.attemptWorldDelete(challengerUuid.toString(), () -> duelStateRegistry.removeDuel(opponentUuid));
                return;
            }

            Location exitLocation = duelLocationService.getExitLocation();

//            if (exitLocation == null) {
//                duelWorldService.attemptWorldDelete(challengerUuid.toString(), () -> duelStateRegistry.removeDuel(opponentUuid));
//                return;
//            }

            duelLocationService.teleportToSpawn(opponent, exitLocation)
                    .whenComplete((result, ex) -> {

                        duelWorldService.attemptWorldDelete(challengerUuid.toString(), () -> duelStateRegistry.removeDuel(opponentUuid));

                        if (ex != null) {
                            plugin.getLogger().log(Level.SEVERE, "An exception occurred when teleporting player after duel end");
                        }

                    });

            if (exitLocation == null) {
                throw new IllegalStateException("A duel has ended, and the exit location for teleporting the players to the spawn is not yet defined");
            }

        }, 20 * 60 * 2);

    }

}
