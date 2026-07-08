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

package pt.gongas.duel.redis.duel;

import org.bukkit.Bukkit;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import pt.gongas.duel.DuelPlugin;
import pt.gongas.duel.model.user.DuelUserSnapshotApplier;
import pt.gongas.duel.model.duel.result.DuelResultSnapshot;
import pt.gongas.duel.util.CompletableFutureHelper;

import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

public class DuelResultRedisService implements DuelResultPublisher {

    private final DuelPlugin plugin;

    private final Logger logger;

    private final DuelUserSnapshotApplier duelUserSnapshotApplier;

    private final ExecutorService redisExecutor;

    private final RTopic topic;

    private final int topicId;

    public DuelResultRedisService(DuelPlugin plugin, Logger logger, DuelUserSnapshotApplier duelUserSnapshotApplier, RedissonClient redis, ExecutorService redisExecutor) {

        this.plugin = plugin;
        this.logger = logger;
        this.duelUserSnapshotApplier = duelUserSnapshotApplier;
        this.redisExecutor = redisExecutor;
        this.topic = redis.getTopic("duel:users");

        this.topicId = topic.addListener(DuelResultSnapshot.class,
                (ignored, message) ->
                        Bukkit.getScheduler().runTask(plugin,
                                () -> duelUserSnapshotApplier.applyRaw(message)
                        )
        );

    }

    public void shutdown() {
        topic.removeListener(topicId);
    }

    @Override
    public void publish(DuelResultSnapshot message) {
        CompletableFutureHelper.runAsync(redisExecutor, logger, () -> topic.publish(message));
    }

}
