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

package pt.gongas.duel.service.duel.matchmaking;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.redisson.api.*;
import org.redisson.client.codec.StringCodec;
import pt.gongas.duel.DuelPlugin;
import pt.gongas.duel.model.queue.QueuePlayer;
import pt.gongas.duel.model.duel.result.DuelPlayerMatch;
import pt.gongas.duel.model.queue.WaitingPlayer;
import pt.gongas.duel.service.retry.RetryService;
import pt.gongas.duel.util.BungeeRedirect;
import pt.gongas.duel.util.CompletableFutureHelper;
import pt.gongas.duel.util.ExpiringMap;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DuelMatchmakingService {

    private final DuelPlugin plugin;

    private final String serverId;

    private final Logger logger;

    private final RetryService retryService;

    private final ExecutorService redisExecutor;

    private final RDeque<String> queue;

    private final RSet<String> players;

    private final RTopic duelsTopic;

    private final RTopic duelsTopicAck;

    private final RTopic emaWaitTopic;

    private final RScript script;

    private final RScript stringScript;

    private final ExpiringMap<UUID, WaitingPlayer> waitingDuels;

    private final Map<UUID, QueuePlayer> localMap = new HashMap<>();

    private volatile double emaWaitMillis;

    private volatile long lastAppliedVersion = -1;

    private static final String EMA_KEY = "duels:ema_wait";

    private static final String EMA_VERSION_KEY = "duels:ema_wait:version";

    private static final double EMA_ALPHA = 0.2;

    public DuelMatchmakingService(DuelPlugin plugin, String serverId, Logger logger, RetryService retryService, ExecutorService redisExecutor, RedissonClient redis) {

        this.plugin = plugin;
        this.serverId = serverId;
        this.logger = logger;
        this.retryService = retryService;
        this.redisExecutor = redisExecutor;
        this.queue = redis.getDeque("duels:queue");
        this.players = redis.getSet("duels:players");
        this.duelsTopic = redis.getTopic("duels:topic");
        this.duelsTopicAck = redis.getTopic("duels:topic-ack");
        this.emaWaitTopic = redis.getTopic("duels:ema_wait");
        this.script = redis.getScript();
        this.stringScript = redis.getScript(StringCodec.INSTANCE);

        this.waitingDuels = new ExpiringMap<>(plugin, 20 * 10, (ignored, value) -> {

            Player player = Bukkit.getPlayer(value.playerUuid());

            if (player != null) {
                CompletableFuture.runAsync(() -> joinQueue(value.playerUuid()), redisExecutor)
                        .exceptionally(ex -> {
                            logger.log(Level.SEVERE, "An error occurred while queuing the player for duels", ex);
                            return null;
                        });
            }

        });

        subscribe();
    }

    public void subscribe() {

        duelsTopic.addListener(String.class,
                (ignored, message) -> handle(message)
        );

        duelsTopicAck.addListener(String.class,
                (ignored, message) -> handleAck(message)
        );

        emaWaitTopic.addListener(String.class,
                (ignored, message) -> {

                    String[] parts = message.split(":");
                    double value = Double.parseDouble(parts[0]);
                    long version = Long.parseLong(parts[1]);

                    applyEmaUpdate(value, version);
                });

    }

    private synchronized void applyEmaUpdate(double value, long version) {
        if (version > lastAppliedVersion) {
            lastAppliedVersion = version;
            emaWaitMillis = value;
        }
    }

    private void handle(String message) {

        String[] split = message.split(":");
        UUID challengerUuid = UUID.fromString(split[0]);
        UUID toRedirect = UUID.fromString(split[1]);

        Bukkit.getScheduler().runTask(plugin, () -> {

            Player player = Bukkit.getPlayer(challengerUuid);

            if (player != null) {

                QueuePlayer removed = leaveQueue(challengerUuid);

                if (removed != null) {

                    putOpponent(toRedirect, challengerUuid, removed.insertedAt());

                    CompletableFutureHelper.runAsync(redisExecutor, logger,
                            () -> duelsTopicAck.publish(toRedirect + ":" + serverId)
                    );

                }

            }

        });

    }

    private void handleAck(String message) {

        String[] split = message.split(":");
        UUID uuid = UUID.fromString(split[0]);
        String serverId = split[1];

        Bukkit.getScheduler().runTask(plugin, () -> {

            Player player = Bukkit.getPlayer(uuid);

            if (player != null) {
                BungeeRedirect.connect(plugin, player, serverId);
            }

        });

    }

    public void joinQueue(UUID playerUuid) {

        script.eval(
                RScript.Mode.READ_WRITE,
                """
                        if redis.call('SADD', KEYS[1], ARGV[1]) == 1 then
                            redis.call('RPUSH', KEYS[2], ARGV[1])
                            return 1
                        end
                        return 0
                        """,
                RScript.ReturnType.INTEGER,
                Arrays.asList(
                        players.getName(),
                        queue.getName()
                ),
                playerUuid
        );

        Bukkit.getScheduler().runTask(plugin, () -> {

            Player player = Bukkit.getPlayer(playerUuid);

            if (player != null) {
                localMap.put(playerUuid, new QueuePlayer(player, player.getName(), System.currentTimeMillis()));
            } else {

                int maxAttempts = 7;

                retryService
                        .executeRetryAsync(
                                maxAttempts,
                                () -> removeRedisQueue(playerUuid)
                        );

                CompletableFutureHelper.fireAndForget(
                        redisExecutor,
                        logger,
                        "An error occurred while removing the player from the queue",
                        () -> removeRedisQueue(playerUuid)
                );
            }

        });

    }

    public QueuePlayer leaveQueue(UUID playerUuid) {

        QueuePlayer removedPlayer = localMap.remove(playerUuid);

        if (removedPlayer != null) {

            int maxRetries = 7;

            CompletableFuture<Boolean> future = retryService.executeRetryAsync(
                    maxRetries,
                    () -> removeRedisQueue(playerUuid)
            );

            future.whenCompleteAsync((result, ignored) -> {

                if (result == null) {
                    // If Redis fails, then we'll add it back to the cache to keep the cache and Redis synchronized
                    localMap.put(playerUuid, removedPlayer);
                }

            }, Bukkit.getScheduler().getMainThreadExecutor(plugin));

        }

        return removedPlayer;
    }

    public void removeLocalQueue(UUID playerUuid) {
        localMap.remove(playerUuid);
    }

    public CompletableFuture<Boolean> removeRedisQueue(UUID playerUuid) {

        return CompletableFuture.supplyAsync(() -> {

            Long result = script.eval(
                    RScript.Mode.READ_WRITE,
                    """
                            redis.call('SREM', KEYS[1], ARGV[1])
                            redis.call('LREM', KEYS[2], 0, ARGV[1])
                            return 1
                            """,
                    RScript.ReturnType.INTEGER,
                    Arrays.asList(
                            players.getName(),
                            queue.getName()
                    ),
                    playerUuid);

            return result != null && result == 1;
        }, redisExecutor);

    }

    public @NotNull CompletableFuture<@Nullable DuelPlayerMatch> tryMatch(UUID challengerUuid) {

        String targetString;

        try {

            targetString = script.eval(
                    RScript.Mode.READ_WRITE,
                    """
                            local value = redis.call('LPOP', KEYS[1])
                            if value then
                                redis.call('SREM', KEYS[2], value)
                            end
                            return value
                            """,
                    RScript.ReturnType.VALUE,
                    Arrays.asList(
                            queue.getName(),
                            players.getName()
                    )
            );

        } catch (NoSuchElementException ex) {
            return CompletableFuture.completedFuture(null);
        }

        if (targetString == null) {
            return CompletableFuture.completedFuture(null);
        }

        UUID target = UUID.fromString(targetString);

        return CompletableFuture.supplyAsync(() -> {

                    QueuePlayer queuePlayer = localMap.remove(target);

                    // If this is true, I can guarantee that the 'target' player is online
                    if (queuePlayer != null) {

                        recordWaitSample(System.currentTimeMillis() - queuePlayer.insertedAt())
                                .exceptionally(ex -> {
                                    logger.log(Level.SEVERE, "An error occurred while recording the queue wait sample", ex);
                                    return null;
                                });

                        return DuelPlayerMatch.from(target, queuePlayer.playerName());
                    }

                    return null;

                }, Bukkit.getScheduler().getMainThreadExecutor(plugin))
                .thenCompose(result -> {

                    if (result != null) {
                        return CompletableFuture.completedFuture(result);
                    }

                    return CompletableFuture.supplyAsync(() -> {
                                duelsTopic.publish(target + ":" + challengerUuid);
                                return DuelPlayerMatch.EMPTY;
                            },
                            redisExecutor);
                });

    }

    public void putOpponent(UUID redirectingPlayerUuid, UUID challengerUuid, long insertedAt) {
        waitingDuels.put(redirectingPlayerUuid, new WaitingPlayer(challengerUuid, insertedAt));
    }

    public @Nullable WaitingPlayer removeOpponent(@NotNull UUID playerUuid) {
        return waitingDuels.remove(playerUuid);
    }

    public Map<UUID, QueuePlayer> getLocalMap() {
        return localMap;
    }

    /**
     * Records a real observed wait time and folds it into the running EMA.
     */
    public CompletableFuture<Void> recordWaitSample(long waitMillis) {

        return CompletableFuture.runAsync(() -> {

            String newValue = stringScript.eval(
                    RScript.Mode.READ_WRITE,
                    """
                            local current = redis.call('GET', KEYS[1])
                            local version = redis.call('INCR', KEYS[2])
                            
                            local result
                            local ema = current and tonumber(current) or nil
                            
                            if not ema then
                                result = tonumber(ARGV[1])
                            else
                                local sample = tonumber(ARGV[1])
                                local alpha = tonumber(ARGV[2])
                                result = alpha * sample + (1 - alpha) * ema
                            end
                            
                            result = tostring(result)
                            redis.call('SET', KEYS[1], result)
                            
                            return result .. ":" .. version
                            """,
                    RScript.ReturnType.VALUE,
                    Arrays.asList(EMA_KEY, EMA_VERSION_KEY),
                    String.valueOf(waitMillis),
                    String.valueOf(EMA_ALPHA)
            );

            if (newValue != null) {

                String[] parts = newValue.split(":");
                double value = Double.parseDouble(parts[0]);
                long version = Long.parseLong(parts[1]);

                applyEmaUpdate(value, version);

                emaWaitTopic.publish(newValue);
            }

        }, redisExecutor);

    }

    /**
     * Returns the current estimated queue wait time.
     * This method is thread-safe and may be called from any thread.
     */
    public long getEstimatedWaitMillis() {
        return (long) emaWaitMillis;
    }

}
