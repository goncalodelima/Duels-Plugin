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

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.world.SlimeWorldInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pt.gongas.database.Database;
import pt.gongas.duel.DuelPlugin;
import pt.gongas.duel.model.duel.Duel;
import pt.gongas.duel.model.duel.DuelLocation;
import pt.gongas.duel.model.duel.DuelStatus;
import pt.gongas.duel.model.duel.PendingDuel;
import pt.gongas.duel.model.duel.result.DuelAcceptResult;
import pt.gongas.duel.model.duel.result.DuelAcceptResultResponse;
import pt.gongas.duel.model.queue.WaitingPlayer;
import pt.gongas.duel.service.PlayerNameService;
import pt.gongas.duel.service.duel.invitation.DuelInvitationService;
import pt.gongas.duel.service.duel.matchmaking.DuelMatchmakingService;
import pt.gongas.duel.service.duel.network.DuelRedirectService;
import pt.gongas.duel.service.duel.state.DuelStateRegistry;
import pt.gongas.duel.service.duel.storage.DuelLocationService;
import pt.gongas.duel.service.duel.world.DuelWorldService;
import pt.gongas.duel.util.BungeeRedirect;
import pt.gongas.duel.util.CompletableFutureHelper;
import pt.gongas.duel.util.UISoundUtil;
import pt.gongas.duel.util.config.Configuration;
import pt.gongas.economy.platforms.paper.PaperEconomyPlugin;
import pt.gongas.economy.shared.api.EconomyApi;
import pt.gongas.economy.shared.currency.Currency;
import pt.gongas.economy.shared.user.ErrorType;
import pt.gongas.economy.shared.user.QueryUserResult;
import pt.gongas.economy.shared.user.User;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DuelAcceptanceService {

    private final DuelPlugin plugin;

    private final String serverId;

    private final UUID sessionUuid;

    private final Logger logger;

    private final Database datacenter;

    private final PlayerNameService playerNameService;

    private final DuelStateRegistry duelStateRegistry;

    private final DuelInvitationService duelInvitationService;

    private final DuelMatchmakingService duelMatchmakingService;

    private final DuelRedirectService duelRedirectService;

    private final DuelWorldService duelWorldService;

    private final DuelLocationService duelLocationService;

    private final EconomyApi<Player> economyApi;

    private final ExecutorService databaseExecutor;

    private final ExecutorService redisExecutor;

    private final AdvancedSlimePaperAPI advancedSlimePaperAPI;

    private final Component errorMessage;

    private final Component someoneWithoutEnoughBalance;

    private final Component noEnoughBalanceMessage;

    private final Component opponentNoEnoughBalance;

    private final Component challengedOfflineMessage;

    private final Component challengerOfflineMessage;

    private final Component duelStartMessage;

    private final Component duelTitleMessage;

    private final Component duelSubtitleMessage;

    private static final String WORLD_PREFIX = "duel-";

    public DuelAcceptanceService(DuelPlugin plugin, String serverId, UUID sessionUuid, Logger logger, Database datacenter, PlayerNameService playerNameService, DuelStateRegistry duelStateRegistry, DuelInvitationService duelInvitationService, DuelRedirectService duelRedirectService, DuelWorldService duelWorldService, DuelLocationService duelLocationService, DuelMatchmakingService duelMatchmakingService, EconomyApi<Player> economyApi, ExecutorService databaseExecutor, ExecutorService redisExecutor, AdvancedSlimePaperAPI advancedSlimePaperAPI, Configuration lang) {

        this.plugin = plugin;
        this.serverId = serverId;
        this.sessionUuid = sessionUuid;
        this.logger = logger;
        this.datacenter = datacenter;
        this.playerNameService = playerNameService;
        this.duelStateRegistry = duelStateRegistry;
        this.duelInvitationService = duelInvitationService;
        this.duelRedirectService = duelRedirectService;
        this.duelWorldService = duelWorldService;
        this.duelLocationService = duelLocationService;
        this.duelMatchmakingService = duelMatchmakingService;
        this.economyApi = economyApi;
        this.databaseExecutor = databaseExecutor;
        this.redisExecutor = redisExecutor;
        this.advancedSlimePaperAPI = advancedSlimePaperAPI;

        MiniMessage miniMessage = MiniMessage.miniMessage();

        errorMessage = miniMessage.deserialize(lang.getString("error-message", "<red>Something strange happened. Try again."));
        someoneWithoutEnoughBalance = miniMessage.deserialize(lang.getString("someone-without-enough-balance", "<red>One of the fighters in this duel doesn't have enough money for the duel's bet.>"));
        noEnoughBalanceMessage = miniMessage.deserialize(lang.getString("no-enough-balance-message", "<red>You don't have enough money to accept this duel!"));
        opponentNoEnoughBalance = miniMessage.deserialize(lang.getString("opponent-no-enough-balance", "<red>Your opponent doesn't have enough money for this duel"));
        challengedOfflineMessage = miniMessage.deserialize(lang.getString("challenged-offline-on-duel-redirect", "<red>The challenged player has logged out in the meantime... Cancelling the duel."));
        challengerOfflineMessage = miniMessage.deserialize(lang.getString("challenger-offline-on-duel-redirect", "<red>The challenger player has logged out in the meantime... Cancelling the duel."));
        duelStartMessage = miniMessage.deserialize(lang.getString("duel-start-message", "<green>The duel has started, may the best one win!"));
        duelTitleMessage = miniMessage.deserialize(lang.getString("duel-title", "<green><bold>ᴅᴜᴇʟѕ"));
        duelSubtitleMessage = miniMessage.deserialize(lang.getString("duel-subtitle", "<red> Against: <white><opponent>"));

    }

    public CompletableFuture<@NotNull DuelAcceptResultResponse> acceptDuel(@NotNull Player player, @Nullable User economyUser, @NotNull String challengerName, boolean isBetDuel, @Nullable String amount) {

        String accepterName = player.getName();
        UUID accepterUuid = player.getUniqueId();

        return CompletableFutureHelper.supplyAsync(redisExecutor, logger, () -> {

                    UUID challengerUuid = playerNameService.getUuid(challengerName);

                    if (challengerUuid == null) {
                        return new DuelAcceptResultResponse(DuelAcceptResult.NO_DUEL, null);
                    }

                    PendingDuel pendingDuel = duelInvitationService.getPendingDuel(challengerUuid);

                    if (pendingDuel.data() == null) {
                        return new DuelAcceptResultResponse(DuelAcceptResult.NO_DUEL, null);
                    }

                    if (!accepterUuid.equals(pendingDuel.data().getTargetUuid())) {
                        return new DuelAcceptResultResponse(DuelAcceptResult.PLAYER_DID_NOT_CHALLENGE, null);
                    }

                    DuelAcceptResultResponse earlyResponse = validateBetDuel(pendingDuel.data(), economyUser, isBetDuel, amount);

                    if (earlyResponse != null) {
                        return earlyResponse;
                    }

                    duelMatchmakingService.removeRedisQueue(accepterUuid)
                            .thenAcceptAsync(result -> {

                                if (Boolean.TRUE.equals(result)) {
                                    duelMatchmakingService.removeLocalQueue(accepterUuid);
                                }

                            }, Bukkit.getScheduler().getMainThreadExecutor(plugin))
                            .exceptionally(ex -> {
                                logger.log(Level.SEVERE, "An error occurred while removing the player from the queue", ex);
                                return null;
                            });

                    Duel duel = duelInvitationService.removeRedisPendingDuel(challengerUuid);
                    duelInvitationService.removeCachedPendingDuel(challengerUuid);

                    if (duel == null) {
                        return new DuelAcceptResultResponse(DuelAcceptResult.NO_DUEL, null);
                    }

                    if (!accepterUuid.equals(duel.getTargetUuid())) {
                        return new DuelAcceptResultResponse(DuelAcceptResult.PLAYER_DID_NOT_CHALLENGE, null);
                    }

                    DuelAcceptResultResponse response = validateBetDuel(duel, economyUser, isBetDuel, amount);

                    if (response != null) {
                        return response;
                    }

                    long now = System.currentTimeMillis();
                    long expiresAt = duel.getCreatedAt() + duelInvitationService.getDuelRequestMillisTtl();
                    long remaining = expiresAt - now;

                    if (remaining < duelInvitationService.getDuelMinTimeLeftToAcceptMillis()) {
                        return new DuelAcceptResultResponse(DuelAcceptResult.NO_DUEL, duel);
                    }

                    if (!duel.getServerId().equals(serverId)) {
                        String currencyName = duel.getCurrency() != null ? duel.getCurrency().name() : "";
                        duelRedirectService.publishRedirectMessage(accepterUuid, accepterName, currencyName, duel);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> BungeeRedirect.connect(plugin, player, duel.getServerId()), 20L);
                        return new DuelAcceptResultResponse(DuelAcceptResult.REDIRECT_SUCCESS, duel);
                    }

                    // This is a safety mechanism to handle unexpected server termination. If the server process crashes,
                    // the shutdown logic is never executed, meaning the Redis data for players who were online at the time is not cleaned up.
                    // This mechanism invalidates that stale data.
                    if (!sessionUuid.equals(duel.getSessionUuid())) {
                        return new DuelAcceptResultResponse(DuelAcceptResult.INVALID_REDIS_DATA, duel);
                    }

                    return new DuelAcceptResultResponse(DuelAcceptResult.NO_REDIRECT_SUCCESS, duel);

                }, new DuelAcceptResultResponse(DuelAcceptResult.ERROR, null))
                .exceptionally(ex -> {
                    logger.log(Level.SEVERE, "An error occurred while processing the duel request", ex);
                    return null;
                });

    }

    private @Nullable DuelAcceptResultResponse validateBetDuel(Duel duel, User economyUser, boolean isBetDuel, String amount) {

        if (isBetDuel) {

            if (amount == null) {
                throw new IllegalStateException("Amount can't be null if isBetDuel=true on a duel accept request");
            }

            String inserted = PaperEconomyPlugin.plugin.formatter.formatNumber(PaperEconomyPlugin.plugin.formatter.parseFormattedNumber(amount));
            String expected = PaperEconomyPlugin.plugin.formatter.formatNumber(duel.getCents() / 100D);

            if (!inserted.equals(expected)) {
                return new DuelAcceptResultResponse(DuelAcceptResult.WRONG_INSERTED_AMOUNT, null);
            }

            long userCents = economyUser == null ? 0 : economyUser.get(duel.getCurrency());

            if (userCents < duel.getCents()) {
                return new DuelAcceptResultResponse(DuelAcceptResult.NO_ENOUGH_BALANCE, duel);
            }

        } else {

            if (duel.getCents() > 0) {
                return new DuelAcceptResultResponse(DuelAcceptResult.NEED_CONFIRM_BET_DUEL, duel);
            }

        }

        return null;
    }

    public void validateDuel(@NotNull Duel duel, @NotNull Player challenger, @NotNull Player challenged, @Nullable WaitingPlayer challengerWaitingPlayer, @Nullable Currency currency, @Nullable Long cents) {

        DuelLocation challengerLocation = duelLocationService.getChallengerLocation();
        DuelLocation challengedLocation = duelLocationService.getChallengedLocation();
        Location exitLocation = duelLocationService.getExitLocation();

        if (challengerLocation == null || challengedLocation == null || exitLocation == null) {
            throw new IllegalStateException(
                    "Some duel locations are missing. challengerLocation=" + challengerLocation +
                            ", challengedLocation=" + challengedLocation +
                            ", exitLocation=" + exitLocation
            );
        }

        UUID challengerUuid = challenger.getUniqueId();
        UUID challengedUuid = challenged.getUniqueId();

        if (currency != null && cents != null) {

            User challengerUser = economyApi.getUserService().get(challengerUuid);
            User challengedUser = economyApi.getUserService().get(challengedUuid);

            if (challengerUser != null) {

                long challengerCents = challengerUser.get(currency);

                if (challengerCents < cents) {
                    challenger.sendMessage(noEnoughBalanceMessage);
                    challenged.sendMessage(opponentNoEnoughBalance);
                    UISoundUtil.playErrorSound(challenger);
                    UISoundUtil.playErrorSound(challenged);
                    return;
                }

            }

            if (challengedUser != null) {

                long challengedCents = challengedUser.get(currency);

                if (challengedCents < cents) {
                    challenger.sendMessage(opponentNoEnoughBalance);
                    challenged.sendMessage(noEnoughBalanceMessage);
                    UISoundUtil.playErrorSound(challenger);
                    UISoundUtil.playErrorSound(challenged);
                    return;
                }

            }

        }

        duelWorldService.createDuelWorld(challengerUuid)
                .thenApply(world -> {

                    if (world == null) {
                        throw new IllegalStateException("World could not be created");
                    }

                    return world;

                })
                .thenComposeAsync(slimeWorld -> {

                    if (!validatePlayers(challenger, challenged, null)) {
                        duelWorldService.attemptWorldDelete(challengerUuid.toString(), () -> duelStateRegistry.removeDuel(challengerUuid));
                        return CompletableFuture.completedFuture(null);
                    }

                    SlimeWorldInstance slimeWorldInstance = advancedSlimePaperAPI.getLoadedWorld(WORLD_PREFIX + duel.getChallengerUuid());

                    if (slimeWorldInstance == null) {

                        slimeWorldInstance = advancedSlimePaperAPI.loadWorld(slimeWorld, true);

                        if (slimeWorldInstance == null) {
                            throw new IllegalStateException("An error occurred while loading the world");
                        }

                    }

                    World world = slimeWorldInstance.getBukkitWorld();

                    world.setGameRule(GameRules.KEEP_INVENTORY, false);

                    CompletableFuture<Boolean> t1 = challenger.teleportAsync(challengerLocation.toLocation(world));
                    CompletableFuture<Boolean> t2 = challenged.teleportAsync(challengedLocation.toLocation(world));

                    duelStateRegistry.addBlockedPlayers(challengerUuid, challengedUuid);

                    return CompletableFuture.allOf(t1, t2)
                            .thenCompose(ignored -> {

                                // This is not a blocking call because all CompletableFutures have already completed
                                if (!t1.join() || !t2.join()) {
                                    return CompletableFuture.completedFuture(null);
                                }

                                if (!validatePlayers(challenger, challenged, exitLocation)) {
                                    duelWorldService.attemptWorldDelete(challengerUuid.toString(), () -> duelStateRegistry.removeDuel(challengerUuid));
                                    return CompletableFuture.completedFuture(null);
                                }

                                if (challengerWaitingPlayer != null) {
                                    duelMatchmakingService.recordWaitSample(System.currentTimeMillis() - challengerWaitingPlayer.insertedAt())
                                            .exceptionally(ex -> {
                                                logger.log(Level.SEVERE, "An error occurred while recording the queue wait sample", ex);
                                                return null;
                                            });
                                }

                                if (currency != null && cents != null) {
                                    return validatePlayerBalances(challengerUuid, challengedUuid, currency, cents);
                                }

                                return CompletableFuture.completedFuture(null);

                            })
                            .whenCompleteAsync((result, throwable) -> {

                                if (result instanceof QueryUserResult.Error(ErrorType type)) {

                                    boolean isChallengerOnline = challenger.isOnline();
                                    boolean isChallengedOnline = challenged.isOnline();

                                    switch (type) {

                                        case NOT_ENOUGH_BALANCE -> {

                                            if (isChallengerOnline) {
                                                challenger.sendMessage(someoneWithoutEnoughBalance);
                                            }

                                            if (isChallengedOnline) {
                                                challenged.sendMessage(someoneWithoutEnoughBalance);
                                            }

                                        }

                                        case EXCEPTION -> {

                                            if (isChallengedOnline) {
                                                challenged.sendMessage(errorMessage);
                                            }

                                            logger.log(Level.SEVERE, "An exception occurred when validating the duel");
                                        }

                                        case NOT_FOUND, EXTERNAL_PLUGIN -> {

                                            if (isChallengedOnline) {
                                                challenged.sendMessage(errorMessage);
                                            }

                                        }

                                    }

                                    if (isChallengerOnline) {
                                        duelLocationService.teleportToSpawn(challenger, exitLocation);
                                    }

                                    if (isChallengedOnline) {
                                        duelLocationService.teleportToSpawn(challenged, exitLocation);
                                    }

                                    duelWorldService.attemptWorldDelete(challengerUuid.toString(), () -> duelStateRegistry.removeDuel(challengerUuid));
                                    return;
                                }

                                if (!validatePlayers(challenger, challenged, exitLocation)) {
                                    duelWorldService.attemptWorldDelete(challengerUuid.toString(), () -> duelStateRegistry.removeDuel(challengerUuid));
                                    return;
                                }

                                handlePostDuel(duel, challengerUuid, challengedUuid, challenger, challenged);

                            }, Bukkit.getScheduler().getMainThreadExecutor(plugin))
                            .whenComplete((ignored, ex) -> duelStateRegistry.removeBlockedPlayers(challengerUuid, challengedUuid));

                }, Bukkit.getScheduler().getMainThreadExecutor(plugin))
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE, "An unexpected error occurred while validating a duel", ex);
                    return null;
                });

    }

    private CompletableFuture<QueryUserResult> validatePlayerBalances(UUID challengerUuid, UUID challengedUuid, Currency currency, long cents) {

        return CompletableFuture.supplyAsync(() -> {

            QueryUserResult result;

            try {

                result = economyApi.getEconomyTransactionalApi().executeInEconomyTransaction(datacenter,
                        (userService, databaseExecutor, connection) -> {

                            QueryUserResult challengerResult = userService.getCurrencyLowLevel(challengerUuid, currency, databaseExecutor, connection);

                            if (!(challengerResult instanceof QueryUserResult.Success challengerSuccess)) {
                                return challengerResult;
                            }

                            if (challengerSuccess.cents() < cents) {
                                return new QueryUserResult.Error(ErrorType.NOT_ENOUGH_BALANCE);
                            }

                            QueryUserResult challengedResult = userService.getCurrencyLowLevel(challengedUuid, currency, databaseExecutor, connection);

                            if (!(challengedResult instanceof QueryUserResult.Success challengedSuccess)) {
                                return challengedResult;
                            }

                            if (challengedSuccess.cents() < cents) {
                                return new QueryUserResult.Error(ErrorType.NOT_ENOUGH_BALANCE);
                            }

                            return challengerResult;
                        });
            } catch (SQLException e) {
                result = new QueryUserResult.Error(ErrorType.EXCEPTION);
                logger.log(Level.SEVERE, "An exception occurred when validating the player balances");
            }

            return result;

        }, databaseExecutor);

    }

    private void handlePostDuel(Duel duel, UUID challengerUuid, UUID challengedUuid, Player challenger, Player challenged) {

        duel.setStatus(DuelStatus.MATCHED);
        duelStateRegistry.addPlayerDuel(challengerUuid, challengedUuid, duel);

        challenger.sendMessage(duelStartMessage);
        challenged.sendMessage(duelStartMessage);

        challenger.sendTitlePart(TitlePart.TITLE, applyOpponent(duelTitleMessage, challenged.getName()));
        challenger.sendTitlePart(TitlePart.SUBTITLE, applyOpponent(duelSubtitleMessage, challenged.getName()));

        challenged.sendTitlePart(TitlePart.TITLE, applyOpponent(duelTitleMessage, challenger.getName()));
        challenged.sendTitlePart(TitlePart.SUBTITLE, applyOpponent(duelSubtitleMessage, challenger.getName()));
    }

    private boolean validatePlayers(@NotNull Player challenger, @NotNull Player challenged, @Nullable Location exitLocation) {

        boolean challengerOnline = challenger.isConnected();
        boolean challengedOnline = challenged.isConnected();

        if (challengerOnline && challengedOnline) {
            return true;
        }

        if (challengerOnline) {

            challenger.sendMessage(challengedOfflineMessage);

            if (exitLocation != null) {
                challenger.teleportAsync(exitLocation);
            }

        }

        if (challengedOnline) {

            challenged.sendMessage(challengerOfflineMessage);

            if (exitLocation != null) {
                challenged.teleportAsync(exitLocation);
            }

        }

        return false;
    }

    private Component applyOpponent(Component component, String opponent) {
        return component.replaceText(TextReplacementConfig.builder()
                .matchLiteral("<opponent>")
                .replacement(opponent)
                .build());
    }

}
