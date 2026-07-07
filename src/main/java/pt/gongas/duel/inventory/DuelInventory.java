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

package pt.gongas.duel.inventory;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import pt.gongas.duel.DuelPlugin;
import pt.gongas.duel.model.user.DuelUser;
import pt.gongas.duel.service.duel.matchmaking.DuelMatchmakingService;
import pt.gongas.duel.service.duel.DuelService;
import pt.gongas.duel.util.TimeUtil;
import pt.gongas.duel.util.UISoundUtil;
import pt.gongas.duel.util.config.Configuration;
import pt.gongas.economy.platforms.paper.PaperEconomyPlugin;
import pt.gongas.economy.shared.currency.Currency;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DuelInventory implements Listener {

    private final DuelPlugin plugin;

    private final DuelService duelService;

    private final DuelMatchmakingService duelMatchmakingService;

    private final int size;

    private final Component title;

    private final Material cancelMaterial;

    private final int cancelSlot;

    private final Component cancelName;

    private final List<Component> cancelLore;

    private final Material waitMaterial;

    private final int waitSlot;

    private final Component waitName;

    private final List<Component> waitLore;

    private final Material statisticsMaterial;

    private final int statisticsSlot;

    private final Component statisticsName;

    private final List<Component> statisticsLore;

    private final Material searchMaterial;

    private final int searchSlot;

    private final Component searchName;

    private final List<Component> searchLore;

    private final Component dialogTitle;

    private final Component dialogConfirmLabel;

    private final Component dialogConfirmTooltip;

    private final Component dialogCancelLabel;

    private final Component dialogCancelTooltip;

    private final Component dialogCurrencyAmountName;

    private final Component dialogCurrencyName;

    private final Component dialogTarget;

    private final Component invalidCurrencyMessage;

    private final Component alreadyOnQueueMessage;

    private final Component blacklistedServer;

    private final List<SingleOptionDialogInput.OptionEntry> dialogCurrencyOptions = new ArrayList<>();

    private static final String DIALOG_CURRENCY_AMOUNT = "amount";

    private static final String DIALOG_CURRENCY = "currency";

    private static final String DIALOG_TARGET = "target";

    public DuelInventory(DuelPlugin plugin, DuelService duelService, DuelMatchmakingService duelMatchmakingService, Configuration inventory, Configuration lang) {

        this.plugin = plugin;
        this.duelService = duelService;
        this.duelMatchmakingService = duelMatchmakingService;

        MiniMessage miniMessage = MiniMessage.miniMessage();

        this.size = inventory.getInt("duel.size");
        this.title = miniMessage.deserialize(inventory.getString("duel.title", "ᴅᴜᴇʟѕ"));

        this.cancelMaterial = Material.getMaterial(inventory.getString("duel.cancel.material", "RED_STAINED_GLASS_PANE"));
        this.cancelSlot = inventory.getInt("duel.cancel.slot", 10);
        this.cancelName = miniMessage.deserialize(inventory.getString("duel.cancel.name", "<red>ᴄᴀɴᴄᴇʟ"));
        this.cancelLore = inventory.getStringList("duel.cancel.lore").stream().map(miniMessage::deserialize).toList();

        this.waitMaterial = Material.getMaterial(inventory.getString("duel.wait.material", "COMPASS"));
        this.waitSlot = inventory.getInt("duel.wait.slot", 12);
        this.waitName = miniMessage.deserialize(inventory.getString("duel.wait.name", "<yellow>ᴡᴀɪᴛ ᴛɪᴍᴇ"));
        this.waitLore = inventory.getStringList("duel.wait.lore").stream().map(miniMessage::deserialize).toList();

        this.statisticsMaterial = Material.getMaterial(inventory.getString("duel.statistics.material", "GRAY_DYE"));
        this.statisticsSlot = inventory.getInt("duel.statistics.slot", 14);
        this.statisticsName = miniMessage.deserialize(inventory.getString("duel.statistics.name", "<yellow>ѕᴛᴀᴛɪѕᴛɪᴄѕ"));
        this.statisticsLore = inventory.getStringList("duel.statistics.lore").stream().map(miniMessage::deserialize).toList();

        this.searchMaterial = Material.getMaterial(inventory.getString("duel.search.material", "LIME_STAINED_GLASS_PANE"));
        this.searchSlot = inventory.getInt("duel.search.slot", 16);
        this.searchName = miniMessage.deserialize(inventory.getString("duel.search.name", "<green>ѕᴇᴀʀᴄʜ"));
        this.searchLore = inventory.getStringList("duel.search.lore").stream().map(miniMessage::deserialize).toList();

        this.dialogTitle = miniMessage.deserialize(inventory.getString("duel.dialog.title", "ᴅᴜᴇʟѕ"));

        this.dialogConfirmLabel = miniMessage.deserialize(inventory.getString("duel.dialog.inputs.confirm.label", "<color:#AEFFC1>Confirm"));
        this.dialogConfirmTooltip = miniMessage.deserialize(inventory.getString("duel.dialog.inputs.confirm.tooltip", "Click to confirm your input."));
        this.dialogCancelLabel = miniMessage.deserialize(inventory.getString("duel.dialog.inputs.cancel.label", "<color:#FFA0B1>Discard"));
        this.dialogCancelTooltip = miniMessage.deserialize(inventory.getString("duel.dialog.inputs.cancel.tooltip", "Click to discard your input."));

        this.dialogCurrencyAmountName = miniMessage.deserialize(inventory.getString("duel.dialog.inputs.currency.amount", "How much should the inventory be worth? Leave empty for none (items only)"));
        this.dialogCurrencyName = miniMessage.deserialize(inventory.getString("duel.dialog.inputs.currency.select", "Select the inventory currency"));

        this.invalidCurrencyMessage = miniMessage.deserialize(lang.getString("invalid-currency", "<red>You need to choose a currency you want to bet something on!"));
        this.alreadyOnQueueMessage = miniMessage.deserialize(lang.getString("already-on-queue", "<red>You cannot duel because you are already in the queue waiting for someone. You can cancel it by clicking the button on the left."));
        this.blacklistedServer = miniMessage.deserialize(lang.getString("blacklisted-server", "<red>You cannot create a duel on a box server. Type '/spawn' and try again."));

        for (Currency currency : PaperEconomyPlugin.plugin.getEconomyApi().getCurrencyService().getAll()) {
            this.dialogCurrencyOptions.add(SingleOptionDialogInput.OptionEntry.create(currency.name(), miniMessage.deserialize(currency.name()), false));
        }

        this.dialogTarget = miniMessage.deserialize(inventory.getString("duel.dialog.inputs.target.label", "Enter your opponent's name. Leave empty for open combat"));

        this.dialogCurrencyOptions.add(SingleOptionDialogInput.OptionEntry.create("none", miniMessage.deserialize("None"), true));
    }

    public void open(@NotNull Player player, @NotNull DuelUser duelUser) {

        DuelInventoryHolder holder = new DuelInventoryHolder(size, title, duelUser);
        Inventory inventory = holder.getInventory();

        ItemStack cancelItem = ItemStack.of(cancelMaterial);
        cancelItem.setData(DataComponentTypes.ITEM_NAME, cancelName);
        cancelItem.setData(DataComponentTypes.LORE, ItemLore.lore(cancelLore));

        inventory.setItem(cancelSlot, cancelItem);

        ItemStack waitItem = ItemStack.of(waitMaterial);
        waitItem.setData(DataComponentTypes.ITEM_NAME, waitName);
        waitItem.setData(DataComponentTypes.LORE, ItemLore.lore(
                waitLore.stream().map(component -> component
                        .replaceText(
                                TextReplacementConfig.builder()
                                        .matchLiteral("<time>")
                                        .replacement(TimeUtil.formatTime(duelMatchmakingService.getEstimatedWaitMillis()))
                                        .build()
                        )
                ).toList()
        ));

        inventory.setItem(waitSlot, waitItem);

        ItemStack statisticsItem = ItemStack.of(statisticsMaterial);

        statisticsItem.setData(DataComponentTypes.ITEM_NAME, statisticsName);
        statisticsItem.setData(DataComponentTypes.LORE, ItemLore.lore(
                statisticsLore.stream().map(component -> component
                        .replaceText(
                                TextReplacementConfig.builder()
                                        .matchLiteral("<wins>")
                                        .replacement(String.valueOf(duelUser.getWins()))
                                        .build()
                        )
                        .replaceText(
                                TextReplacementConfig.builder()
                                        .matchLiteral("<losses>")
                                        .replacement(String.valueOf(duelUser.getLosses()))
                                        .build()
                        )
                        .replaceText(
                                TextReplacementConfig.builder()
                                        .matchLiteral("<streak>")
                                        .replacement(String.valueOf(duelUser.getStreak()))
                                        .build()
                        )
                        .replaceText(
                                TextReplacementConfig.builder()
                                        .matchLiteral("<max_streak>")
                                        .replacement(String.valueOf(duelUser.getMaxStreak()))
                                        .build()
                        )
                ).toList()
        ));

        inventory.setItem(statisticsSlot, statisticsItem);

        ItemStack searchItem = ItemStack.of(searchMaterial);
        searchItem.setData(DataComponentTypes.ITEM_NAME, searchName);
        searchItem.setData(DataComponentTypes.LORE, ItemLore.lore(searchLore));

        inventory.setItem(searchSlot, searchItem);

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {

        if (!(event.getInventory().getHolder(false) instanceof DuelInventoryHolder holder)) {
            return;
        }

        HumanEntity humanEntity = event.getWhoClicked();

        event.setCancelled(true);

        int rawSlot = event.getRawSlot();

        if (rawSlot == cancelSlot) {

            return;
        }

        if (rawSlot == searchSlot) {

//            if (!serverId.contains("smp")) {
//                humanEntity.sendMessage(blacklistedServer);
//                UISoundUtil.playErrorSound(humanEntity);
//                return;
//            }

            DialogInput currencyOption = DialogInput.singleOption(DIALOG_CURRENCY, dialogCurrencyName, dialogCurrencyOptions).build();
            DialogInput currencyAmountInput = DialogInput.text(DIALOG_CURRENCY_AMOUNT, dialogCurrencyAmountName).build();
            DialogInput dialogTargetInput = DialogInput.text(DIALOG_TARGET, dialogTarget).build();

            List<DialogInput> inputs = List.of(currencyOption, currencyAmountInput, dialogTargetInput);

            Dialog dialog = Dialog.create(builder -> builder.empty()
                    .base(DialogBase.builder(dialogTitle).inputs(inputs).build())
                    .type(DialogType.confirmation(
                            ActionButton.create(dialogConfirmLabel, dialogConfirmTooltip, 100, DialogAction.customClick((view, audience) -> {

                                if (!(audience instanceof Player player)) {
                                    return;
                                }

                                if (player.getOpenInventory().getTopInventory() != holder.getInventory()) {
                                    return;
                                }

                                UUID challengerUuid = player.getUniqueId();

                                if (duelMatchmakingService.getLocalMap().containsKey(challengerUuid)) {
                                    player.sendMessage(alreadyOnQueueMessage);
                                    UISoundUtil.playErrorSound(player);
                                    player.closeInventory();
                                    return;
                                }

                                String currencyAmountText = view.getText(DIALOG_CURRENCY_AMOUNT);
                                double amount = PaperEconomyPlugin.plugin.formatter.parseFormattedNumber(currencyAmountText);

                                if (!currencyAmountText.isEmpty() && amount < 0) {
                                    UISoundUtil.playErrorSound(player);
                                    return;
                                }

                                String targetName = view.getText(DIALOG_TARGET);

                                String challengerName = player.getName();

                                if (challengerName.equalsIgnoreCase(targetName)) {
                                    UISoundUtil.playErrorSound(player);
                                    return;
                                }

                                long now = System.currentTimeMillis();

                                String currencyName = view.getText(DIALOG_CURRENCY);
                                long cents = (long) amount * 100;
                                Currency currency = cents <= 0 ? null : PaperEconomyPlugin.plugin.getEconomyApi().getCurrencyService().get(currencyName);

                                if (currency == null && cents > 0) {
                                    player.sendMessage(invalidCurrencyMessage);
                                    UISoundUtil.playErrorSound(player);
                                    player.closeInventory();
                                    return;
                                }

                                duelService.createDuel(player, challengerUuid, challengerName, targetName == null || targetName.isEmpty() ? null : targetName, now, currency, cents);

                                UISoundUtil.playUISound(player);
                                player.closeInventory();

                            }, ClickCallback.Options.builder().uses(1).lifetime(ClickCallback.DEFAULT_LIFETIME).build())),
                            ActionButton.create(dialogCancelLabel, dialogCancelTooltip, 100, DialogAction.customClick((view, audience) -> {

                                if (!(audience instanceof Player player)) {
                                    return;
                                }

                                if (player.getOpenInventory().getTopInventory() != holder.getInventory()) {
                                    return;
                                }

                                UISoundUtil.playUISound(player);

                            }, ClickCallback.Options.builder().uses(1).lifetime(ClickCallback.DEFAULT_LIFETIME).build()))
                    ))
            );

            humanEntity.showDialog(dialog);
        }

    }

}
