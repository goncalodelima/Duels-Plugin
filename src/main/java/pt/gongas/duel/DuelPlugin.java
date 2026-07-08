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

package pt.gongas.duel;

import co.aikar.commands.BukkitCommandManager;
import com.github.sirblobman.combatlogx.api.ICombatLogX;
import com.infernalsuite.asp.api.exceptions.*;
import com.infernalsuite.asp.api.loaders.SlimeLoader;
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap;
import org.bukkit.Bukkit;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.redisson.api.RedissonClient;
import pt.gongas.database.Database;
import pt.gongas.database.DatabaseType;
import pt.gongas.database.connection.CustomDatabaseConnection;
import pt.gongas.database.credentials.impl.DatabaseCredentialsImpl;
import pt.gongas.duel.command.DuelCommand;
import pt.gongas.duel.inventory.DuelInventory;
import pt.gongas.duel.listener.DuelListener;
import pt.gongas.duel.listener.DuelMatchmakingListener;
import pt.gongas.duel.listener.UserListener;
import pt.gongas.duel.model.duel.DuelLocation;
import pt.gongas.duel.redis.duel.DuelResultRedisService;
import pt.gongas.duel.repository.user.MySqlUserRepository;
import pt.gongas.duel.repository.user.UserRepository;
import pt.gongas.duel.runnable.DuelQueueRunnable;
import pt.gongas.duel.runnable.DuelTimeoutRunnable;
import pt.gongas.duel.service.duel.*;
import pt.gongas.duel.service.PlayerNameService;
import pt.gongas.duel.service.duel.invitation.DuelInvitationService;
import pt.gongas.duel.service.duel.matchmaking.DuelMatchmakingService;
import pt.gongas.duel.service.duel.network.DuelRedirectService;
import pt.gongas.duel.service.duel.state.DuelStateRegistry;
import pt.gongas.duel.service.duel.storage.DuelLocationService;
import pt.gongas.duel.service.duel.world.DuelWorldService;
import pt.gongas.duel.service.retry.RetryService;
import pt.gongas.duel.model.user.DuelUserSnapshotApplier;
import pt.gongas.duel.service.user.DuelUserStateService;
import pt.gongas.duel.service.user.DuelUserService;
import pt.gongas.duel.util.config.Configuration;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import pt.gongas.duel.world.loaders.file.SafeFileLoader;
import pt.gongas.economy.platforms.paper.PaperEconomyPlugin;
import pt.gongas.economy.shared.api.EconomyApi;
import pt.gongas.redis.redis.RedisManager;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DuelPlugin extends JavaPlugin {

    private RetryService retryService;

    private DuelRedirectService duelRedirectService;

    private DuelResultRedisService duelResultRedisService;

    private DuelInvitationService duelInvitationService;

    private ExecutorService redisExecutor;

    private ExecutorService worldExecutor;

    private ExecutorService databaseExecutor;

    @Override
    public void onEnable() {

        saveDefaultConfig();
        ConfigurationSerialization.registerClass(DuelLocation.class);

        String serverId = getConfig().getString("server_id", "unknown");
        UUID sessionUuid = UUID.randomUUID();

        Configuration lang = new Configuration(this, "lang", "lang.yml");
        lang.saveDefaultConfig();

        Configuration inventory = new Configuration(this, "inventory", "inventory.yml");
        inventory.saveDefaultConfig();

        Configuration locations = new Configuration(this, "locations", "locations.yml");
        locations.saveDefaultConfig();

        RedissonClient redissonClient = RedisManager.getClient();
        redisExecutor = Executors.newSingleThreadScheduledExecutor();

        worldExecutor = Executors.newVirtualThreadPerTaskExecutor();
        databaseExecutor = Executors.newVirtualThreadPerTaskExecutor();

        File serverFolder;

        try {
            serverFolder = new File(".").getCanonicalFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        File parentFolder = serverFolder.getParentFile();

        AdvancedSlimePaperAPI advancedSlimePaperAPI = AdvancedSlimePaperAPI.instance();
        SlimeLoader slimeLoader = new SafeFileLoader(new File(parentFolder, "slime_worlds"));

        SlimeWorld duelTemplateWorld;
        try {
//            Bukkit.unloadWorld("arena", true);
//            SlimeWorld world = advancedSlimePaperAPI.readVanillaWorld(new File(serverFolder, "arena"), "inventory-template", slimeLoader);
//            advancedSlimePaperAPI.saveWorld(world);
            duelTemplateWorld = advancedSlimePaperAPI.readWorld(slimeLoader, "duel-template", true, new SlimePropertyMap());
        } catch (UnknownWorldException | IOException | CorruptedWorldException | NewerFormatException e) {
            throw new IllegalStateException(e);
        }

        int hikariMaxPoolSize = getConfig().getInt("hikari.maximumPoolSize", 10);
        long hikariConnectionTimeout = getConfig().getLong("hikari.connectionTimeout", 5_000);
        int hikariMinimumIdle = getConfig().getInt("hikari.minimumIdle", 10);
        long hikariMaximumLifeTime = getConfig().getLong("hikari.maximumLifetime", 1_800_000);
        long hikariKeepaliveTime = getConfig().getLong("hikari.keepaliveTime", 30_000);

        Database datacenter = new CustomDatabaseConnection(
                new DatabaseCredentialsImpl(DatabaseType.MYSQL,
                        getConfig().getString("database.host"),
                        getConfig().getString("database.port"),
                        getConfig().getString("database.database"),
                        getConfig().getString("database.user"),
                        getConfig().getString("database.password"),
                        getConfig().getString("database.file"))
        ).setup(
                hikariMaxPoolSize,
                hikariConnectionTimeout,
                hikariMinimumIdle,
                hikariMaximumLifeTime,
                hikariKeepaliveTime
        );

        EconomyApi<Player> economyApi = PaperEconomyPlugin.plugin.getEconomyApi();

        UserRepository userRepository = new MySqlUserRepository(getLogger(), datacenter);
        DuelUserService duelUserService = new DuelUserService(userRepository, databaseExecutor, getLogger());

        DuelUserSnapshotApplier userSnapshotApplier = new DuelUserSnapshotApplier(duelUserService);

        retryService = new RetryService(getLogger());
        duelResultRedisService = new DuelResultRedisService(this, getLogger(), userSnapshotApplier, redissonClient, redisExecutor);

        DuelUserStateService duelUserStateService = new DuelUserStateService(this, duelUserService, retryService, duelResultRedisService, userSnapshotApplier, databaseExecutor);

        PlayerNameService playerNameService = new PlayerNameService(redissonClient);
        DuelMatchmakingService duelMatchmakingService = new DuelMatchmakingService(this, serverId, getLogger(), retryService, redisExecutor, redissonClient);

        duelInvitationService = new DuelInvitationService(this, serverId, getLogger(), redisExecutor, redissonClient, lang);
        duelRedirectService = new DuelRedirectService(this, serverId, sessionUuid, redissonClient, economyApi, duelInvitationService);

        DuelWorldService duelWorldService = new DuelWorldService(this, getLogger(), worldExecutor, advancedSlimePaperAPI, duelTemplateWorld);
        DuelLocationService duelLocationService = new DuelLocationService(this, serverId, getLogger(), locations);

        DuelStateRegistry duelStateRegistry = new DuelStateRegistry();

        DuelAcceptanceService duelAcceptanceService = new DuelAcceptanceService(this, serverId, sessionUuid, getLogger(), datacenter, playerNameService, duelStateRegistry, duelInvitationService, duelRedirectService, duelWorldService, duelLocationService, duelMatchmakingService, economyApi, databaseExecutor, redisExecutor, advancedSlimePaperAPI, lang);
        DuelService duelService = new DuelService(this, serverId, sessionUuid, getLogger(), redisExecutor, playerNameService, duelStateRegistry, duelAcceptanceService, duelMatchmakingService, duelInvitationService, economyApi, lang);

        DuelInventory duelInventory = new DuelInventory(this, duelService, duelMatchmakingService, inventory, lang);
        getServer().getPluginManager().registerEvents(duelInventory, this);

        getServer().getPluginManager().registerEvents(new DuelListener(this, getLogger(), duelStateRegistry, duelRedirectService, duelWorldService, duelLocationService, duelUserStateService, duelAcceptanceService, economyApi, getCombatLog(), lang), this);
        getServer().getPluginManager().registerEvents(new UserListener(this, duelUserService, lang), this);
        getServer().getPluginManager().registerEvents(new DuelMatchmakingListener(serverId, sessionUuid, duelAcceptanceService, duelMatchmakingService, lang), this);

        BukkitCommandManager commandManager = new BukkitCommandManager(this);
        commandManager.enableUnstableAPI("help");
        commandManager.registerCommand(new DuelCommand(this, duelUserService, duelAcceptanceService, duelLocationService, duelInventory, lang));

        new DuelQueueRunnable(duelMatchmakingService, lang).runTaskTimer(this, 20, 20);
        new DuelTimeoutRunnable(this, advancedSlimePaperAPI, duelStateRegistry, duelWorldService, duelLocationService, lang).runTaskTimer(this, 20 * 60 * 5, 20 * 60 * 5);

        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

    }

    @Override
    public void onDisable() {

        // Shut down the retry service before the other executors to prevent
        // scheduled retry tasks from submitting new work during shutdown.
        retryService.shutdown();

        // Redis only stores information that is useful during the lifetime of a process.
        // Therefore, we can safely force a shutdown without waiting for the tasks scheduled on the redisExecutor to execute
        redisExecutor.shutdownNow();

        try {

            if (!redisExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                getLogger().warning("Redis executor did not terminate in time.");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Data cleanup must be performed after shutting down the Redis executor
        // to ensure that no tasks are executed after the data is cleared.
        duelRedirectService.shutdown();
        duelResultRedisService.shutdown();
        duelInvitationService.shutdown();

        databaseExecutor.shutdown();

        try {
            // Wait for currently executing tasks to finish
            if (!databaseExecutor.awaitTermination(36, TimeUnit.SECONDS)) {
                // Force shutdown if tasks are not finished in the given time
                databaseExecutor.shutdownNow();
                // Wait for tasks to respond to being cancelled
                if (!databaseExecutor.awaitTermination(36, TimeUnit.SECONDS)) {
                    System.err.println("Database Executor did not terminate in the specified time.");
                }
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            databaseExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // The threads in this pool are daemon threads used only to create non-persistent worlds.
        // Therefore, it is safe to force the executor to shut down.
        worldExecutor.shutdownNow();

        try {

            if (!worldExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                getLogger().warning("World executor did not terminate in time.");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

    }

    public ICombatLogX getCombatLog() {
        PluginManager pluginManager = Bukkit.getPluginManager();
        Plugin plugin = pluginManager.getPlugin("CombatLogX");
        return (ICombatLogX) plugin;
    }

}
