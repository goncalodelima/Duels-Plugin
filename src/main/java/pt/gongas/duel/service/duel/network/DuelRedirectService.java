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

package pt.gongas.duel.service.duel.network;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import pt.gongas.duel.DuelPlugin;
import pt.gongas.duel.model.duel.Duel;
import pt.gongas.duel.model.duel.DuelStatus;
import pt.gongas.duel.service.duel.invitation.DuelInvitationService;
import pt.gongas.duel.util.ExpiringMap;
import pt.gongas.economy.shared.api.EconomyApi;
import pt.gongas.economy.shared.currency.Currency;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handles the cross-server leg of duel acceptance: once a duel is accepted
 * on a different server than the challenger's, this publishes a redirect
 * message so the challenger's server knows to expect the accepting player
 * and can validate/start the duel once they join.
 * <p>
 * This is a distinct phase from {@link DuelInvitationService} — invitations
 * happen before a duel is accepted, redirects happen after.
 */
public class DuelRedirectService {

    private final DuelPlugin plugin;

    private final String serverId;

    private final UUID sessionUuid;

    private final EconomyApi<Player> economyApi;

    private final RTopic redirectTopic;

    private final int redirectTopicId;

    private final ExpiringMap<UUID, Duel> redirectingDuels;

    public DuelRedirectService(DuelPlugin plugin, String serverId, UUID sessionUuid, RedissonClient redis, EconomyApi<Player> economyApi, DuelInvitationService duelInvitationService) {

        this.plugin = plugin;
        this.serverId = serverId;
        this.sessionUuid = sessionUuid;
        this.economyApi = economyApi;

        this.redirectTopic = redis.getTopic("duels:redirect");

        this.redirectingDuels = new ExpiringMap<>(plugin, 20 * 10, (key, value) -> {
            // Nothing to do
        });

        this.redirectTopicId = this.redirectTopic.addListener(String.class, (channel, message) -> handleRedirectMessage(message, duelInvitationService));
    }

    public void shutdown() {
        redirectTopic.removeListener(redirectTopicId);
    }

    private void handleRedirectMessage(String message, DuelInvitationService duelInvitationService) {

        String[] split = message.split(":");
        String targetServerId = split[8];

        if (!this.serverId.equals(targetServerId)) {
            return;
        }

        UUID sessionUUID = UUID.fromString(split[9]);

        // Ignore messages associated with an outdated Redis session.
        // This can happen if stale Redis data remains after an unexpected server shutdown.
        if (!sessionUUID.equals(this.sessionUuid)) {
            return;
        }

        UUID toRedirect = UUID.fromString(split[0]);
        String toRedirectName = split[1];

        UUID challengerUuid = UUID.fromString(split[2]);
        String challengerName = split[3];

        String currencyName = split[4];
        long cents = Long.parseLong(split[5]);

        long createdAt = Long.parseLong(split[6]);
        long expireAt = Long.parseLong(split[7]);

        Currency currency = economyApi.getCurrencyService().get(currencyName);

        Duel duel = new Duel(
                targetServerId,
                sessionUuid,
                challengerUuid,
                challengerName,
                toRedirect,
                toRedirectName,
                cents,
                currency,
                createdAt,
                expireAt,
                DuelStatus.WAITING
        );

        Bukkit.getScheduler().runTask(plugin, () -> {

            // This is necessary in case the player accepts the duel on a different server than the challenger
            duelInvitationService.removeCachedPendingDuel(challengerUuid);

            redirectingDuels.put(toRedirect, duel);
        });

    }

    /**
     * Publishes a redirect message so the challenger's server knows to
     * expect {@code accepterUuid} and can start the duel once they join.
     *
     * @param currencyName the bet currency's name, or an empty string if this
     *                     isn't a bet duel — never {@code null}, since {@code null}
     *                     would be serialized as the literal text "null"
     */
    public void publishRedirectMessage(@NotNull UUID accepterUuid, @NotNull String accepterName, @NotNull String currencyName, @NotNull Duel duel) {
        redirectTopic.publish(accepterUuid + ":" + accepterName + ":" + duel.getChallengerUuid() + ":" + duel.getChallengerName() + ":" + currencyName + ":" + duel.getCents() + ":" + duel.getCreatedAt() + ":" + duel.getExpireAt() + ":" + duel.getServerId() + ":" + duel.getSessionUuid());
    }

    public Duel removeRedirectingDuel(UUID challengedUuid) {
        return redirectingDuels.remove(challengedUuid);
    }

}