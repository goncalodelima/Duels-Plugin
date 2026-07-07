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

package pt.gongas.duel.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pt.gongas.duel.DuelPlugin;
import pt.gongas.duel.inventory.DuelInventory;
import pt.gongas.duel.model.duel.Duel;
import pt.gongas.duel.model.duel.DuelLocation;
import pt.gongas.duel.model.user.DuelUser;
import pt.gongas.duel.service.duel.DuelAcceptanceService;
import pt.gongas.duel.service.duel.storage.DuelLocationService;
import pt.gongas.duel.service.user.DuelUserService;
import pt.gongas.duel.util.UISoundUtil;
import pt.gongas.duel.util.config.Configuration;
import pt.gongas.economy.platforms.paper.PaperEconomyPlugin;
import pt.gongas.economy.shared.user.User;

import java.util.UUID;
import java.util.logging.Level;

@CommandAlias("duel|duels|x1")
public class DuelCommand extends BaseCommand {

    private final DuelPlugin plugin;

    private final DuelUserService duelUserService;

    private final DuelAcceptanceService duelAcceptanceService;

    private final DuelLocationService duelLocationService;

    private final DuelInventory duelInventory;

    private final Component errorMessage;

    private final Component confirmDuelBetMessage;

    private final Component noDuelInviteMessage;

    private final Component notChallengedMessage;

    private final Component noEnoughBalanceMessage;

    private final Component redirectingMessage;

    private final Component challengerOfflineMessage;

    private final Component challengerRedirectingMessage;

    private final Component challengedRedirectingMessage;

    private final Component setSpawnMessage;

    private final Component setChallengerMessage;

    private final Component setChallengedMessage;

    private final Component wrongInsertedAmount;

    public DuelCommand(DuelPlugin plugin, DuelUserService duelUserService, DuelAcceptanceService duelAcceptanceService, DuelLocationService duelLocationService, DuelInventory duelInventory, Configuration lang) {

        this.plugin = plugin;
        this.duelUserService = duelUserService;
        this.duelAcceptanceService = duelAcceptanceService;
        this.duelLocationService = duelLocationService;
        this.duelInventory = duelInventory;

        MiniMessage miniMessage = MiniMessage.miniMessage();

        this.errorMessage = miniMessage.deserialize(lang.getString("error-message", "<red>Something strange happened. Try again."));
        this.confirmDuelBetMessage = miniMessage.deserialize(lang.getString("confirm-duel-bet-message", "<red>You need to confirm the bet amount for the duel. To do this, accept the duel by typing '/duel accept <target> <amount>'."));
        this.noDuelInviteMessage = miniMessage.deserialize(lang.getString("no-duel-invite-message", "<red>You did not receive a duel from the inserted user."));
        this.notChallengedMessage = miniMessage.deserialize(lang.getString("player-did-not-challenged-you", "<red>The player you entered did not challenge you to a duel."));
        this.noEnoughBalanceMessage = miniMessage.deserialize(lang.getString("no-enough-balance-message", "<red>You don't have enough money to accept this duel!"));
        this.redirectingMessage = miniMessage.deserialize(lang.getString("redirecting-message", "<green>You are being redirected to the server where you accepted the duel!"));
        this.challengerOfflineMessage = miniMessage.deserialize(lang.getString("challenger-offline-message", "<red>The player who challenged you is offline!"));
        this.challengerRedirectingMessage = miniMessage.deserialize(lang.getString("challenger-redirecting-message", "<green>You are being redirected to the duel..."));
        this.challengedRedirectingMessage = miniMessage.deserialize(lang.getString("challenged-redirecting-message", "<green>You are being redirected to the duel..."));
        this.setSpawnMessage = miniMessage.deserialize(lang.getString("set-spawn-message", "<green>You have successfully set the duel exit location."));
        this.setChallengerMessage = miniMessage.deserialize(lang.getString("set-challenger-message", "<green>You have successfully set the duel challenger's location."));
        this.setChallengedMessage = miniMessage.deserialize(lang.getString("set-challenged-message", "<green>You have successfully set the duel opponent's location."));
        this.wrongInsertedAmount = miniMessage.deserialize(lang.getString("wrong-inserted-amount", "<red>You entered an invalid bet amount when accepting the duel."));
    }

    @Default
    @CommandPermission("duel.command")
    @Description("Opens the Duels Main Menu")
    public void mainMenu(Player player) {

        DuelUser duelUser = duelUserService.getUser(player.getUniqueId());

        if (duelUser == null) {
            player.sendMessage(errorMessage);
            return;
        }

        duelInventory.open(player, duelUser);
    }

    @Subcommand("accept")
    @Description("Accept a duel request")
    @Syntax("<target>")
    @CommandCompletion("@players")
    public void acceptDuel(Player player, String target) {

        duelAcceptanceService.acceptDuel(player, null, target, false, null)
                .thenAcceptAsync(response -> {

                    switch (response.result()) {

                        case ERROR -> player.sendMessage(errorMessage);

                        case NEED_CONFIRM_BET_DUEL -> player.sendMessage(confirmDuelBetMessage);

                        case NO_DUEL -> player.sendMessage(noDuelInviteMessage);

                        case PLAYER_DID_NOT_CHALLENGE -> player.sendMessage(notChallengedMessage);

                        case NO_ENOUGH_BALANCE -> player.sendMessage(noEnoughBalanceMessage);

                        case REDIRECT_SUCCESS -> player.sendMessage(redirectingMessage);

                        case NO_REDIRECT_SUCCESS -> {

                            Duel duel = response.duel();

                            if (duel == null) {
                                throw new IllegalStateException("The duel in this response is null, but it cannot be in this type of response");
                            }

                            if (!player.isConnected()) {
                                return;
                            }

                            Player challenger = Bukkit.getPlayer(target);

                            if (challenger == null) {
                                player.sendMessage(challengerOfflineMessage);
                                return;
                            }

                            challenger.sendMessage(challengerRedirectingMessage);
                            player.sendMessage(challengedRedirectingMessage);

                            duelAcceptanceService.validateDuel(duel, challenger, player, null, null, null);
                        }

                    }

                }, Bukkit.getScheduler().getMainThreadExecutor(plugin))
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE, "Duel accept failed", ex);
                    return null;
                });

    }

    @Subcommand("accept")
    @Description("Accept a duel request with a bet")
    @Syntax("<target> <amount>")
    @CommandCompletion("@players")
    public void acceptDuel(Player player, String target, String amount) {

        UUID playerUuid = player.getUniqueId();
        User economyUser = PaperEconomyPlugin.plugin.getEconomyApi().getUserService().get(playerUuid);

        if (economyUser == null) {
            player.sendMessage(errorMessage);
            return;
        }

        duelAcceptanceService.acceptDuel(player, economyUser, target, true, amount)
                .thenAcceptAsync(response -> {

                    switch (response.result()) {

                        case ERROR -> player.sendMessage(errorMessage);

                        case NO_DUEL -> player.sendMessage(noDuelInviteMessage);

                        case PLAYER_DID_NOT_CHALLENGE -> player.sendMessage(notChallengedMessage);

                        case NO_ENOUGH_BALANCE -> player.sendMessage(noEnoughBalanceMessage);

                        case REDIRECT_SUCCESS -> player.sendMessage(redirectingMessage);

                        case NO_REDIRECT_SUCCESS -> {

                            Duel duel = response.duel();

                            if (duel == null) {
                                throw new IllegalStateException("The duel in this response is null, but it cannot be in this type of response");
                            }

                            if (!player.isConnected()) {
                                return;
                            }

                            Player challenger = Bukkit.getPlayer(target);

                            if (challenger == null) {
                                player.sendMessage(challengerOfflineMessage);
                                return;
                            }

                            challenger.sendMessage(challengerRedirectingMessage);
                            player.sendMessage(challengedRedirectingMessage);

                            duelAcceptanceService.validateDuel(duel, challenger, player, null, null, null);
                        }

                        case WRONG_INSERTED_AMOUNT -> player.sendMessage(wrongInsertedAmount);

                    }

                }, Bukkit.getScheduler().getMainThreadExecutor(plugin))
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE, "Duel accept failed", ex);
                    return null;
                });

    }

    @Subcommand("setspawn")
    @Description("Set the exit spawn of a duel")
    public void setSpawn(Player player) {

        Location newSpawnLocation = player.getLocation();
        duelLocationService.setExitLocation(newSpawnLocation);

        player.sendMessage(setSpawnMessage);
        UISoundUtil.playSuccessSound(player);
    }

    @Subcommand("setchallenger")
    @Description("Set the challenger's location in the duel")
    public void setChallengerLocation(Player player) {

        DuelLocation challengerLocation = DuelLocation.from(player.getLocation());
        duelLocationService.setChallengerLocation(challengerLocation);

        player.sendMessage(setChallengerMessage);
        UISoundUtil.playSuccessSound(player);
    }

    @Subcommand("setchallenged")
    @Description("Set the challenged player's location in the duel")
    public void setChallengedLocation(Player player) {

        DuelLocation challengedLocation = DuelLocation.from(player.getLocation());
        duelLocationService.setChallengedLocation(challengedLocation);

        player.sendMessage(setChallengedMessage);
        UISoundUtil.playSuccessSound(player);
    }

    @HelpCommand
    public static void onHelp(CommandSender sender, CommandHelp help) {
        help.showHelp();
    }

}
