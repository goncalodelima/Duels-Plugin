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

package pt.gongas.duel.service.duel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pt.gongas.duel.DuelPlugin;
import pt.gongas.duel.model.duel.Duel;
import pt.gongas.duel.model.duel.DuelStatus;
import pt.gongas.duel.model.duel.result.DuelPlayerMatch;
import pt.gongas.duel.service.PlayerNameService;
import pt.gongas.duel.service.duel.invitation.DuelInvitationService;
import pt.gongas.duel.service.duel.matchmaking.DuelMatchmakingService;
import pt.gongas.duel.service.duel.network.DuelRedirectService;
import pt.gongas.duel.service.duel.state.DuelStateRegistry;
import pt.gongas.duel.service.duel.storage.DuelLocationService;
import pt.gongas.duel.service.duel.world.DuelWorldService;
import pt.gongas.duel.util.CompletableFutureHelper;
import pt.gongas.duel.util.UISoundUtil;
import pt.gongas.duel.util.config.Configuration;
import pt.gongas.economy.shared.api.EconomyApi;
import pt.gongas.economy.shared.currency.Currency;
import pt.gongas.economy.shared.user.User;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestrates the creation of a new duel: validates the request (self-duel,
 * bet balance, target reachability), then either sends an invitation to a
 * specific target or enters matchmaking for a random opponent.
 * <p>
 * This class intentionally does not own persistence, world management, or
 * cross-server messaging itself — those are delegated to
 * {@link DuelInvitationService}, {@link DuelRedirectService},
 * {@link DuelWorldService}, {@link DuelStateRegistry}, and
 * {@link DuelLocationService} respectively. This keeps {@code DuelService}
 * focused on the single question of "should this duel request go through,
 * and if so, who does it notify".
 */
public class DuelService {

    private final DuelPlugin plugin;

    private final String serverId;

    private final Logger logger;

    private final ExecutorService redisExecutor;

    private final PlayerNameService playerNameService;

    private final DuelAcceptanceService duelAcceptanceService;

    private final DuelMatchmakingService duelMatchmakingService;

    private final DuelInvitationService duelInvitationService;

    private final EconomyApi<Player> economyApi;

    private final Component errorMessage;

    private final Component waitMessage;

    private final Component offlinePlayerMessage;

    private final Component fightYourselfMessage;

    private final Component invalidBetMessage;

    private final Component foundAndRedirectingMessage;

    private final Component noEnoughBalanceMessage;

    private static final long LOCAL_CACHE_GRACE_PERIOD_MILLIS = 1_000L;

    public DuelService(DuelPlugin plugin, String serverId, Logger logger, ExecutorService redisExecutor, PlayerNameService playerNameService, DuelAcceptanceService duelAcceptanceService, DuelMatchmakingService duelMatchmakingService, DuelInvitationService duelInvitationService, EconomyApi<Player> economyApi, Configuration lang) {

        this.plugin = plugin;
        this.serverId = serverId;
        this.logger = logger;
        this.redisExecutor = redisExecutor;
        this.playerNameService = playerNameService;
        this.duelAcceptanceService = duelAcceptanceService;
        this.duelMatchmakingService = duelMatchmakingService;
        this.duelInvitationService = duelInvitationService;
        this.economyApi = economyApi;

        MiniMessage miniMessage = MiniMessage.miniMessage();

        this.errorMessage = miniMessage.deserialize(lang.getString("error-message", "<red>Something strange happened. Try again."));
        this.waitMessage = miniMessage.deserialize(lang.getString("wait-message", "<red>You need to wait for your last duel invitation to expire before sending another duel!"));
        this.offlinePlayerMessage = miniMessage.deserialize(lang.getString("offline-player-duel-request", "<red>The player you entered is not online across the entire network!"));
        this.fightYourselfMessage = miniMessage.deserialize(lang.getString("fight-yourself-message", "<red>You can't challenge yourself to a duel."));
        this.invalidBetMessage = miniMessage.deserialize(lang.getString("invalid-bet-message", "<red>You cannot place a bet without selecting a valid player."));
        this.foundAndRedirectingMessage = miniMessage.deserialize(lang.getString("found-and-redirecting", "<green>You were matched with a player on a different server. You are being redirected..."));
        this.noEnoughBalanceMessage = miniMessage.deserialize(lang.getString("no-enough-balance-challenger", "<red>You don't have enough money for the selected amount!"));

    }

    public void createDuel(
            @NotNull Player challengerPlayer,
            @NotNull UUID challengerUuid,
            @NotNull String challengerName,
            @Nullable String targetName,
            long createdAt,
            @Nullable Currency currency,
            long cents
    ) {

        CompletableFutureHelper.runAsync(redisExecutor, logger, () -> {

            if (duelInvitationService.getPendingDuel(challengerUuid).data() != null) {

                Bukkit.getScheduler().runTask(plugin, () -> {
                    challengerPlayer.sendMessage(waitMessage);
                    UISoundUtil.playErrorSound(challengerPlayer);
                });

                return;
            }

            UUID targetUuid;

            if (targetName == null) {
                targetUuid = null;
            } else {

                targetUuid = playerNameService.getUuid(targetName);

                if (targetUuid == null) {

                    Bukkit.getScheduler().runTask(plugin, () -> {

                        if (challengerPlayer.isConnected()) {
                            challengerPlayer.sendMessage(offlinePlayerMessage);
                            UISoundUtil.playErrorSound(challengerPlayer);
                        }

                    });

                    return;
                }

                if (challengerUuid.equals(targetUuid)) {

                    Bukkit.getScheduler().runTask(plugin, () -> {

                        if (challengerPlayer.isConnected()) {
                            challengerPlayer.sendMessage(fightYourselfMessage);
                            UISoundUtil.playErrorSound(challengerPlayer);
                        }

                    });

                    return;
                }

            }

            if (targetUuid != null) {
                createTargetedDuel(challengerPlayer, challengerUuid, challengerName, targetUuid, targetName, createdAt, currency, cents);
            } else {
                createMatchmakingDuel(challengerPlayer, challengerUuid, challengerName, createdAt, currency, cents);
            }

        });

    }

    private void createTargetedDuel(
            @NotNull Player challengerPlayer,
            @NotNull UUID challengerUuid,
            @NotNull String challengerName,
            @NotNull UUID targetUuid,
            @NotNull String targetName,
            long createdAt,
            @Nullable Currency currency,
            long cents
    ) {

        if (currency != null) {

            User challengerUser = economyApi.getUserService().get(challengerUuid);

            if (challengerUser == null) {

                Bukkit.getScheduler().runTask(plugin, () -> {
                    challengerPlayer.sendMessage(errorMessage);
                    UISoundUtil.playErrorSound(challengerPlayer);
                });

                return;
            }

            long challengerCents = challengerUser.get(currency);

            if (challengerCents < cents) {

                Bukkit.getScheduler().runTask(plugin, () -> {
                    challengerPlayer.sendMessage(noEnoughBalanceMessage);
                    UISoundUtil.playErrorSound(challengerPlayer);
                });

                return;
            }

        }

        Duel duel = new Duel(
                serverId,
                challengerUuid,
                challengerName,
                targetUuid,
                targetName,
                cents,
                currency,
                createdAt,
                System.currentTimeMillis() + duelInvitationService.getDuelRequestMillisTtl() + LOCAL_CACHE_GRACE_PERIOD_MILLIS,
                DuelStatus.WAITING
        );

        duelInvitationService.addPendingDuel(challengerUuid, duel);
        duelInvitationService.sendInvitationToTarget(duel, targetUuid);

    }

    private void createMatchmakingDuel(
            @NotNull Player challengerPlayer,
            @NotNull UUID challengerUuid,
            @NotNull String challengerName,
            long createdAt,
            @Nullable Currency currency,
            long cents
    ) {

        // Random matchmaking duels are always cent-less and currency-less;
        // any other combination here means the caller built the request wrong.
        if (currency != null || cents >= 0) {

            Bukkit.getScheduler().runTask(plugin, () -> {

                if (challengerPlayer.isConnected()) {
                    challengerPlayer.sendMessage(invalidBetMessage);
                    UISoundUtil.playErrorSound(challengerPlayer);
                }

            });

            return;
        }

        duelMatchmakingService.tryMatch(challengerUuid)
                .thenCompose(opponent -> {

                    // If this is true, I can guarantee that the 'opponent' player is online
                    if (opponent != null && opponent != DuelPlayerMatch.EMPTY) {

                        UUID opponentUuid = opponent.matchedUuid();
                        String opponentName = opponent.matchedName();

                        Duel duel = new Duel(
                                serverId,
                                challengerUuid,
                                challengerName,
                                opponentUuid,
                                opponentName,
                                0,
                                null,
                                createdAt,
                                System.currentTimeMillis() + duelInvitationService.getDuelRequestMillisTtl() + LOCAL_CACHE_GRACE_PERIOD_MILLIS,
                                DuelStatus.WAITING
                        );

                        Player opponentPlayer = Bukkit.getPlayer(opponentUuid);

                        if (opponentPlayer == null) {
                            return CompletableFuture.failedFuture(
                                    new IllegalStateException("Invariant broken: opponent was resolved but player is null (should always be online)")
                            );
                        }

                        duelAcceptanceService.validateDuel(duel, challengerPlayer, opponentPlayer, null, null, null);
                        return CompletableFuture.completedFuture(null);
                    }

                    if (opponent == null) {
                        return CompletableFuture.supplyAsync(() -> {
                            duelMatchmakingService.joinQueue(challengerUuid);
                            return null;
                        }, redisExecutor);
                    }

                    challengerPlayer.sendMessage(foundAndRedirectingMessage);
                    return CompletableFuture.completedFuture(null);

                })
                .exceptionally(ex -> {
                    logger.log(Level.SEVERE, "Failed to process the matchmaking request", ex);
                    return null;
                });

    }

}