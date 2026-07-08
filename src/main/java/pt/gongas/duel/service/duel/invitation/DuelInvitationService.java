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

package pt.gongas.duel.service.duel.invitation;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RMapCache;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import pt.gongas.duel.DuelPlugin;
import pt.gongas.duel.model.duel.CachedDuel;
import pt.gongas.duel.model.duel.Duel;
import pt.gongas.duel.model.duel.PendingDuel;
import pt.gongas.duel.util.CompletableFutureHelper;
import pt.gongas.duel.util.config.Configuration;
import pt.gongas.economy.platforms.paper.PaperEconomyPlugin;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Owns the lifecycle of a duel invitation, from creation to expiry: keeps
 * it in a fast local cache and a Redis-backed map (so it survives across the
 * whole network, not just this server), and notifies the challenged player
 * either directly (if they're on this server) or over Redis (if they're on
 * another one).
 * <p>
 * A "pending duel" and an "invitation" are treated as the same concept here
 * on purpose: a pending duel only exists because someone was invited to it,
 * and its TTL is the invitation's TTL.
 */
public class DuelInvitationService {

    private final DuelPlugin plugin;

    private final String serverId;

    private final Logger logger;

    private final ExecutorService redisExecutor;

    private final RTopic invitationTopic;

    private final int invitationTopicId;

    private final RMapCache<String, Duel> pendingDuels;

    private final Cache<UUID, CachedDuel> localPendingDuels =
            Caffeine.newBuilder()
                    .expireAfter(new Expiry<UUID, CachedDuel>() {
                        @Override
                        public long expireAfterCreate(UUID key, CachedDuel value, long currentTime) {
                            return TimeUnit.MILLISECONDS.toNanos(value.ttlMillis());
                        }

                        @Override
                        public long expireAfterUpdate(UUID key, CachedDuel value,
                                                      long currentTime, long currentDuration) {
                            return currentDuration;
                        }

                        @Override
                        public long expireAfterRead(UUID key, CachedDuel value,
                                                    long currentTime, long currentDuration) {
                            return currentDuration;
                        }
                    })
                    .build();

    private final List<Component> duelRequestNormal;

    private final List<Component> duelRequestBet;

    private static final Long DUEL_REQUEST_MILLIS_TTL = 2 * 60 * 1_000L;

    private static final long DUEL_MIN_TIME_LEFT_TO_ACCEPT_MILLIS = 1_000L;

    public DuelInvitationService(DuelPlugin plugin, String serverId, Logger logger, ExecutorService redisExecutor, RedissonClient redis, Configuration lang) {

        this.plugin = plugin;
        this.serverId = serverId;
        this.logger = logger;
        this.redisExecutor = redisExecutor;

        this.invitationTopic = redis.getTopic("duels:invitation");
        this.pendingDuels = redis.getMapCache("duels:pending");

        MiniMessage miniMessage = MiniMessage.miniMessage();

        this.duelRequestNormal = lang.getStringList("duel-request.normal")
                .stream()
                .map(miniMessage::deserialize)
                .toList();

        this.duelRequestBet = lang.getStringList("duel-request.bet")
                .stream()
                .map(miniMessage::deserialize)
                .toList();

        this.invitationTopicId = invitationTopic.addListener(Duel.class, (channel, duel) -> handleInvitation(duel));
    }

    public void shutdown() {

        invitationTopic.removeListener(invitationTopicId);

        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        String[] uuids = new String[players.size()];

        int i = 0;

        for (Player player : players) {
            uuids[i++] = player.getUniqueId().toString();
        }

        pendingDuels.fastRemove(uuids);
    }

    public void addPendingDuel(UUID uuid, Duel duel) {
        pendingDuels.put(uuid.toString(), duel, DUEL_REQUEST_MILLIS_TTL, TimeUnit.MILLISECONDS);
        localPendingDuels.put(uuid, new CachedDuel(duel, DUEL_REQUEST_MILLIS_TTL));
    }

    public @NotNull PendingDuel getPendingDuel(UUID uuid) {

        CachedDuel cachedDuel = localPendingDuels.getIfPresent(uuid);

        if (cachedDuel != null) {
            return new PendingDuel(cachedDuel.duel(), true);
        }

        String uuidString = uuid.toString();
        Duel duel = pendingDuels.get(uuidString);

        if (duel != null) {

            long now = System.currentTimeMillis();
            long expireAt = duel.getExpireAt();
            long ttlMillis = expireAt - now;

            if (ttlMillis > 0) {
                localPendingDuels.put(uuid, new CachedDuel(duel, ttlMillis));
            }

        }

        return new PendingDuel(duel, false);
    }

    public long getPendingDuelsEstimatedSize() {
        return localPendingDuels.estimatedSize();
    }

    public Duel removeRedisPendingDuel(UUID uuid) {
        return pendingDuels.remove(uuid.toString());
    }

    public void removeCachedPendingDuel(UUID targetUuid) {
        localPendingDuels.invalidate(targetUuid);
    }

    public long getDuelRequestMillisTtl() {
        return DUEL_REQUEST_MILLIS_TTL;
    }

    /**
     * Notifies the challenged player of a new invitation, either directly if
     * they're online on this server, or over Redis for whichever server
     * they're actually connected to.
     */
    public void sendInvitationToTarget(@NotNull Duel duel, @NotNull UUID targetUuid) {

        Bukkit.getScheduler().runTask(plugin, () -> {

            Player targetPlayer = Bukkit.getPlayer(targetUuid);

            if (targetPlayer != null) {

                sendInvitation(targetPlayer, duel);

            } else {

                CompletableFutureHelper.runAsync(redisExecutor, logger,
                        () -> invitationTopic.publish(duel)
                );

            }

        });

    }

    public long getDuelMinTimeLeftToAcceptMillis() {
        return DUEL_MIN_TIME_LEFT_TO_ACCEPT_MILLIS;
    }

    private void handleInvitation(@NotNull Duel duel) {

        if (duel.getServerId().equals(serverId)) {
            return;
        }

        UUID targetUuid = duel.getTargetUuid();

        if (targetUuid == null) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {

            Player targetPlayer = Bukkit.getPlayer(targetUuid);

            if (targetPlayer == null) {
                return;
            }

            sendInvitation(targetPlayer, duel);
        });

    }

    private void sendInvitation(@NotNull Player targetPlayer, @NotNull Duel duel) {

        String formattedAmount = PaperEconomyPlugin.plugin.formatter.formatNumber(duel.getCents() / 100D);

        List<Component> messages =
                duel.getCents() > 0
                        ? duelRequestBet
                        : duelRequestNormal;

        for (Component component : messages) {
            targetPlayer.sendMessage(
                    component
                            .replaceText(
                                    TextReplacementConfig
                                            .builder()
                                            .matchLiteral("<challenger>")
                                            .replacement(duel.getChallengerName())
                                            .build()
                            )
                            .replaceText(
                                    TextReplacementConfig
                                            .builder()
                                            .matchLiteral("<amount>")
                                            .replacement(formattedAmount)
                                            .build()
                            )
                            .replaceText(
                                    TextReplacementConfig
                                            .builder()
                                            .matchLiteral("<currency>")
                                            .replacement(duel.getCurrency() != null ? duel.getCurrency().name() : "")
                                            .build()
                            )

            );
        }

    }

}