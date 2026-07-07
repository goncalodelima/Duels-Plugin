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

package pt.gongas.duel.model.duel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pt.gongas.economy.shared.currency.Currency;

import java.util.UUID;

public class Duel {

    @NotNull
    private final String serverId;

    @NotNull
    private final UUID challengerUuid;

    @NotNull
    private final String challengerName;

    @Nullable
    private final UUID targetUuid;

    @Nullable
    private final String targetName;

    private final long cents;

    @Nullable
    private final Currency currency;

    private final long createdAt;

    private final long expireAt;

    @NotNull
    private DuelStatus status;

    private boolean valid = true;

    @JsonCreator
    public Duel(
            @JsonProperty("serverId") @NotNull String serverId,
            @JsonProperty("challengerUuid") @NotNull UUID challengerUuid,
            @JsonProperty("challengerName") @NotNull String challengerName,
            @JsonProperty("targetUuid") @Nullable UUID targetUuid,
            @JsonProperty("targetName") @Nullable String targetName,
            @JsonProperty("cents") long cents,
            @JsonProperty("currency") @Nullable Currency currency,
            @JsonProperty("createdAt") long createdAt,
            @JsonProperty("expireAt") long expireAt,
            @JsonProperty("status") @NotNull DuelStatus status
    ) {
        this.serverId = serverId;
        this.challengerUuid = challengerUuid;
        this.challengerName = challengerName;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.cents = cents;
        this.currency = currency;
        this.createdAt = createdAt;
        this.expireAt = expireAt;
        this.status = status;
    }

    public @NotNull String getServerId() {
        return serverId;
    }

    public @NotNull UUID getChallengerUuid() {
        return challengerUuid;
    }

    public @NotNull String getChallengerName() {
        return challengerName;
    }

    public @Nullable UUID getTargetUuid() {
        return targetUuid;
    }

    public @Nullable String getTargetName() {
        return targetName;
    }

    public long getCents() {
        return cents;
    }

    public @Nullable Currency getCurrency() {
        return currency;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpireAt() {
        return expireAt;
    }

    public @NotNull DuelStatus getStatus() {
        return status;
    }

    public void setStatus(@NotNull DuelStatus status) {
        this.status = status;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

}