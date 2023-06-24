/*
 * Copyright (c) 2021 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.core;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.DisplayShops;
import xzot1k.plugins.ds.api.enums.EconomyCallType;
import xzot1k.plugins.ds.api.events.EconomyCallEvent;
import xzot1k.plugins.ds.api.objects.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

public class Commands implements CommandExecutor {

    private DisplayShops pluginInstance;

    public Commands(DisplayShops pluginInstance) {
        setPluginInstance(pluginInstance);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("displayshops")) {

            if (args.length >= 2 && (args[0].equalsIgnoreCase("addcommand") || args[0].equalsIgnoreCase("addcmd"))) {
                runAddCommand(commandSender, args);
                return true;
            }


            switch (args.length) {
                case 1:
                    if (args[0].equalsIgnoreCase("block")) {
                        runBlock(commandSender);
                        return true;
                    } else if (args[0].equalsIgnoreCase("unblock")) {
                        runUnBlock(commandSender);
                        return true;
                    } else if (args[0].equalsIgnoreCase("cdb")) {
                        runCDB(commandSender);
                        return true;
                    } else if (args[0].equalsIgnoreCase("cleanup")) {
                        runCleanUp(commandSender);
                        return true;
                    } else if (args[0].equalsIgnoreCase("copy")) {
                        runCopy(commandSender);
                        return true;
                    } else if (args[0].equalsIgnoreCase("info")) {
                        runInfo(commandSender);
                        return true;
                    } else if (args[0].equalsIgnoreCase("marketregionlist") || args[0].equalsIgnoreCase("mrl")) {
                        runMarketList(commandSender);
                        return true;
                    } else if (args[0].equalsIgnoreCase("selectionmode") || args[0].equalsIgnoreCase("sm")) {
                        runSelectionMode(commandSender);
                        return true;
                    } else if (args[0].equalsIgnoreCase("admin")) {
                        runAdmin(commandSender);
                        return true;
                    } else if (args[0].equalsIgnoreCase("commandmode") || args[0].equalsIgnoreCase("cm")) {
                        runCommandMode(commandSender);
                        return true;
                    } else if (args[0].equalsIgnoreCase("commands")) {
                        runCommands(commandSender);
                        return true;
                    } else if (args[0].equalsIgnoreCase("reload")) {
                        runReload(commandSender);
                        return true;
                    } else if (args[0].equalsIgnoreCase("buy")) {
                        runBuy(commandSender, "1");
                        return true;
                    } else if (args[0].equalsIgnoreCase("delete")) {
                        runDelete(commandSender);
                        return true;
                    } else if (args[0].equalsIgnoreCase("rent") || args[0].equalsIgnoreCase("renew")) {
                        runRent(commandSender, null);
                        return true;
                    } else if (args[0].equalsIgnoreCase("reset")) {
                        runReset(commandSender, null);
                        return true;
                    } else if (args[0].equalsIgnoreCase("time")) {
                        runTime(commandSender, null);
                        return true;
                    } else if (args[0].equalsIgnoreCase("visit")) {
                        runVisit(commandSender, null, null);
                        return true;
                    } else if (args[0].equalsIgnoreCase("advertise")
                            || args[0].equalsIgnoreCase("duyuru") || args[0].equalsIgnoreCase("reklam")) {
                        runAdvertise(commandSender);
                        return true;
                    } else if (args[0].equalsIgnoreCase("notify")) {
                        runNotify(commandSender);
                        return true;
                    }

                    break;
                case 2:
                    if (args[0].equalsIgnoreCase("buyprice") || args[0].equalsIgnoreCase("bp")) {
                        runBuyPrice(commandSender, args);
                        return true;
                    } else if (args[0].equalsIgnoreCase("sellprice") || args[0].equalsIgnoreCase("sp")) {
                        runSellPrice(commandSender, args);
                        return true;
                    } else if (args[0].equalsIgnoreCase("withdraw")) {
                        runWithdraw(commandSender, args);
                        return true;
                    } else if (args[0].equalsIgnoreCase("deposit")) {
                        runDeposit(commandSender, args);
                        return true;
                    } else if (args[0].equalsIgnoreCase("clear")) {
                        runClear(commandSender, args[1]);
                        return true;
                    } else if (args[0].equalsIgnoreCase("buy")) {
                        runBuy(commandSender, args[1]);
                        return true;
                    } else if (args[0].equalsIgnoreCase("removecommand") || args[0].equalsIgnoreCase("removecmd")) {
                        runRemoveCommand(commandSender, args);
                        return true;
                    } else if (args[0].equalsIgnoreCase("deletemarketregion") || args[0].equalsIgnoreCase("dmr")) {
                        runDeleteMarket(commandSender, args);
                        return true;
                    } else if (args[0].equalsIgnoreCase("createmarketregion") || args[0].equalsIgnoreCase("cmr")) {
                        runCreateMarket(commandSender, args);
                        return true;
                    } else if (args[0].equalsIgnoreCase("owner")) {
                        runOwner(commandSender, args);
                        return true;
                    } else if (args[0].equalsIgnoreCase("stock")) {
                        runStock(commandSender, args);
                        return true;
                    } else if (args[0].equalsIgnoreCase("unlock")) {
                        runBBMAccess(commandSender, true, args);
                        return true;
                    } else if (args[0].equalsIgnoreCase("lock")) {
                        runBBMAccess(commandSender, false, args);
                        return true;
                    } else if (args[0].equalsIgnoreCase("rent") || args[0].equalsIgnoreCase("renew")) {
                        runRent(commandSender, args[1]);
                        return true;
                    } else if (args[0].equalsIgnoreCase("reset")) {
                        runReset(commandSender, args[1]);
                        return true;
                    } else if (args[0].equalsIgnoreCase("time")) {
                        runTime(commandSender, args[1]);
                        return true;
                    } else if (args[0].equalsIgnoreCase("visit")) {
                        runVisit(commandSender, args[1], null);
                        return true;
                    } else if (args[0].equalsIgnoreCase("cost")) {
                        runCost(commandSender, null, args[1]);
                        return true;
                    } else if (args[0].equalsIgnoreCase("renewcost") || args[0].equalsIgnoreCase("rcost")) {
                        runRenewCost(commandSender, null, args[1]);
                        return true;
                    }

                    break;
                case 3:
                    if (args[0].equalsIgnoreCase("balance") || args[0].equalsIgnoreCase("bal")) {
                        runBalance(commandSender, args);
                        return true;
                    } else if (args[0].equalsIgnoreCase("give")) {
                        runGive(commandSender, args);
                        return true;
                    } else if (args[1].equalsIgnoreCase("filter")) {
                        runVisit(commandSender, null, args[2]);
                        return true;
                    } else if (args[0].equalsIgnoreCase("cost")) {
                        runCost(commandSender, args[1], args[2]);
                        return true;
                    } else if (args[0].equalsIgnoreCase("renewcost") || args[0].equalsIgnoreCase("rcost")) {
                        runRenewCost(commandSender, args[1], args[2]);
                        return true;
                    }
                    break;
            }

            sendHelp(commandSender);
            return true;
        } else
            return false;
    }

    // command helpers
    private void runBalance(CommandSender commandSender, String[] args) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        final boolean isWithdraw = (args[1].equalsIgnoreCase("withdraw") || args[1].equalsIgnoreCase("w")),
                isDeposit = (!isWithdraw && (args[1].equalsIgnoreCase("deposit") || args[1].equalsIgnoreCase("d")));

        if (!isWithdraw && !isDeposit) {
            String message = getPluginInstance().getLangConfig().getString("balance-command-usage");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.bal" + (isWithdraw ? "withdraw" : "deposit"))) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        Block block = player.getTargetBlock(null, 10);
        Shop shop = getPluginInstance().getManager().getShop(block.getLocation());
        if (shop == null) {
            String message = getPluginInstance().getLangConfig().getString("shop-invalid");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        if (!player.hasPermission("displayshops.admin") && (shop.isAdminShop()
                || (shop.getOwnerUniqueId() != null && !shop.getOwnerUniqueId().toString().equals(player.getUniqueId().toString())))) {
            String message = getPluginInstance().getLangConfig().getString("not-shop-owner");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        boolean isAll = args[1].equalsIgnoreCase("all"),
                useVault = getPluginInstance().getConfig().getBoolean("use-vault");

        if (!isAll && getPluginInstance().getManager().isNotNumeric(args[2].replace(",", "."))) {
            String message = getPluginInstance().getLangConfig().getString("invalid-amount");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        double amount = (isAll ? (isWithdraw ? shop.getStoredBalance() : getPluginInstance().getVaultEconomy().getBalance(player))
                : Double.parseDouble(args[2].replace(",", ".")));
        if (amount < 0) {
            getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("negative-entry"));
            return;
        }

        String tradeItemName = "";
        ItemStack tradeItem = null;
        if (!useVault) {
            tradeItem = (getPluginInstance().getConfig().getBoolean("shop-currency-item.force-use")
                    ? getPluginInstance().getManager().buildShopCurrencyItem(1) : (shop.getTradeItem() != null
                    ? shop.getTradeItem() : getPluginInstance().getManager().buildShopCurrencyItem(1)));
            if (tradeItem != null) tradeItemName = getPluginInstance().getManager().getItemName(tradeItem);
        }

        if (isWithdraw) {
            if ((shop.getStoredBalance() - amount) < 0 && shop.getStoredBalance() != amount) {
                getPluginInstance().getManager().sendMessage(player, Objects.requireNonNull(getPluginInstance().getLangConfig().getString("balance-withdraw-fail"))
                        .replace("{trade-item}", tradeItemName)
                        .replace("{balance}", getPluginInstance().getManager().formatNumber(shop.getStoredBalance(), true)));
                return;
            }
        } else {
            if (useVault ? !getPluginInstance().getVaultEconomy().has(player, amount) : getPluginInstance().getManager().getItemAmount(player.getInventory(), tradeItem) < amount) {
                getPluginInstance().getManager().sendMessage(player, Objects.requireNonNull(getPluginInstance().getLangConfig().getString("insufficient-funds"))
                        .replace("{trade-item}", tradeItemName).replace("{price}", getPluginInstance().getManager().formatNumber(amount, true)));
                return;
            }
        }

        if (isDeposit && (shop.getStoredBalance() + amount) >= getPluginInstance().getConfig().getLong("max-stored-currency")) {
            getPluginInstance().getManager().sendMessage(player, Objects.requireNonNull(getPluginInstance().getLangConfig().getString("max-stored-currency"))
                    .replace("{trade-item}", tradeItemName).replace("{amount}", getPluginInstance().getManager().formatNumber(amount, true)));
            return;
        }

        EconomyCallEvent economyCallEvent = getPluginInstance().getManager().initiateShopEconomyTransaction(player, null, shop,
                EconomyCallType.EDIT_ACTION, getPluginInstance().getConfig().getDouble("prices.balance"));
        if (economyCallEvent == null || !economyCallEvent.willSucceed()) return;

        if (useVault) {
            if (isWithdraw) getPluginInstance().getVaultEconomy().depositPlayer(player, amount);
            else getPluginInstance().getVaultEconomy().withdrawPlayer(player, amount);
        } else {
            if (isWithdraw) {
                if (tradeItem != null) {
                    int stackCount = (int) (amount / tradeItem.getType().getMaxStackSize()),
                            remainder = (int) (amount % tradeItem.getType().getMaxStackSize());

                    tradeItem.setAmount(tradeItem.getType().getMaxStackSize());
                    for (int i = -1; ++i < stackCount; )
                        if (player.getInventory().firstEmpty() == -1)
                            player.getWorld().dropItemNaturally(player.getLocation(), tradeItem);
                        else player.getInventory().addItem(tradeItem);

                    if (remainder > 0) {
                        tradeItem.setAmount(remainder);
                        if (player.getInventory().firstEmpty() == -1)
                            player.getWorld().dropItemNaturally(player.getLocation(), tradeItem);
                        else player.getInventory().addItem(tradeItem);
                    }
                }
            } else getPluginInstance().getManager().removeItem(player.getInventory(), tradeItem, (int) amount);
        }

        shop.setStoredBalance(shop.getStoredBalance() + (isWithdraw ? -amount : amount));
        shop.updateTimeStamp();
        getPluginInstance().getManager().sendMessage(player, Objects.requireNonNull(getPluginInstance().getLangConfig().getString("balance-"
                        + (isWithdraw ? "withdrawn" : "deposited"))).replace("{trade-item}", tradeItemName)
                .replace("{amount}", getPluginInstance().getManager().formatNumber(amount, true)));
    }

    private void runWithdraw(CommandSender commandSender, String[] args) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.withdraw")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        Block block = player.getTargetBlock(null, 10);
        Shop shop = getPluginInstance().getManager().getShop(block.getLocation());
        if (shop == null) {
            String message = getPluginInstance().getLangConfig().getString("shop-invalid");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        if (!player.hasPermission("displayshops.admin") && (shop.isAdminShop()
                || (shop.getOwnerUniqueId() != null && !shop.getOwnerUniqueId().toString().equals(player.getUniqueId().toString())))) {
            String message = getPluginInstance().getLangConfig().getString("not-shop-owner");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        if (getPluginInstance().getManager().isNotNumeric(args[1])) {
            String message = getPluginInstance().getLangConfig().getString("invalid-amount");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        int amount = Math.max(1, Integer.parseInt(args[1]));

        String message;
        if (amount > shop.getStock()) {
            message = getPluginInstance().getLangConfig().getString("stock-withdraw-fail");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message
                        .replace("{amount}", getPluginInstance().getManager().formatNumber(amount, false)));
            return;
        }

        final int availableSpace = Math.min(getPluginInstance().getManager().getInventorySpaceForItem(player,
                shop.getShopItem()), (36 * shop.getShopItem().getMaxStackSize()));
        amount = Math.min(amount, availableSpace);

        if (player.getInventory().firstEmpty() == -1) {
            message = getPluginInstance().getLangConfig().getString("insufficient-space");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message
                        .replace("{space}", getPluginInstance().getManager().formatNumber(availableSpace, false)));
            return;
        }

        EconomyCallEvent economyCallEvent = getPluginInstance().getManager().initiateShopEconomyTransaction(player, null, shop,
                EconomyCallType.EDIT_ACTION, getPluginInstance().getConfig().getDouble("prices.withdraw-stock"));
        if (economyCallEvent == null || !economyCallEvent.willSucceed()) return;

        shop.setStock(shop.getStock() - amount);
        shop.updateTimeStamp();

        final ItemStack itemStack = shop.getShopItem().clone();
        final double finalAmount = amount;
        getPluginInstance().getServer().getScheduler().runTask(getPluginInstance(), () ->
                getPluginInstance().getManager().giveItemStacks(player, itemStack, (int) finalAmount));

        message = getPluginInstance().getLangConfig().getString("withdrawn-stock");
        if (message != null && !message.equalsIgnoreCase(""))
            getPluginInstance().getManager().sendMessage(player, message
                    .replace("{amount}", getPluginInstance().getManager().formatNumber(amount, false)));
        getPluginInstance().runEventCommands("shop-withdraw", player);
    }

    private void runDeposit(CommandSender commandSender, String[] args) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.deposit")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        Block block = player.getTargetBlock(null, 10);
        Shop shop = getPluginInstance().getManager().getShop(block.getLocation());
        if (shop == null) {
            String message = getPluginInstance().getLangConfig().getString("shop-invalid");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        if (!player.hasPermission("displayshops.admin") && (shop.isAdminShop()
                || (shop.getOwnerUniqueId() != null && !shop.getOwnerUniqueId().toString().equals(player.getUniqueId().toString())))) {
            String message = getPluginInstance().getLangConfig().getString("not-shop-owner");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        if (getPluginInstance().getManager().isNotNumeric(args[1])) {
            String message = getPluginInstance().getLangConfig().getString("invalid-amount");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        int amount = Math.max(1, Integer.parseInt(args[1]));

        String message;
        final int maxStock = getPluginInstance().getManager().getMaxStock(shop);
        int totalItemCount = getPluginInstance().getManager().getItemAmount(player.getInventory(), shop.getShopItem());
        if (totalItemCount <= 0 || totalItemCount < amount) {
            message = getPluginInstance().getLangConfig().getString("insufficient-items");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        EconomyCallEvent economyCallEvent = getPluginInstance().getManager().initiateShopEconomyTransaction(player, null, shop,
                EconomyCallType.EDIT_ACTION, getPluginInstance().getConfig().getDouble("prices.deposit-stock"));
        if (economyCallEvent == null || !economyCallEvent.willSucceed()) return;

        int difference = (maxStock - shop.getStock()), amountToRemove = (difference > 0 && difference >= amount ? amount : Math.max(difference, 0));
        if (amountToRemove == 0 || shop.getStock() >= maxStock) {
            message = getPluginInstance().getLangConfig().getString("stock-deposit-fail");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message
                        .replace("{amount}", getPluginInstance().getManager().formatNumber(amount, false)));
            return;
        }

        getPluginInstance().getManager().removeItem(player.getInventory(), shop.getShopItem(), amountToRemove);
        shop.setStock(shop.getStock() + amountToRemove);
        shop.updateTimeStamp();

        message = getPluginInstance().getLangConfig().getString("deposited-stock");
        if (message != null && !message.equalsIgnoreCase(""))
            getPluginInstance().getManager().sendMessage(player, message
                    .replace("{amount}", getPluginInstance().getManager().formatNumber(amountToRemove, false)));
        getPluginInstance().runEventCommands("shop-deposit", player);
    }

    private void runSellPrice(CommandSender commandSender, String[] args) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.sellprice")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        Block block = player.getTargetBlock(null, 10);
        Shop shop = getPluginInstance().getManager().getShop(block.getLocation());
        if (shop == null) {
            String message = getPluginInstance().getLangConfig().getString("shop-invalid");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        if (!player.hasPermission("displayshops.admin") && (shop.isAdminShop()
                || (shop.getOwnerUniqueId() != null && !shop.getOwnerUniqueId().toString().equals(player.getUniqueId().toString())))) {
            String message = getPluginInstance().getLangConfig().getString("not-shop-owner");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        if (getPluginInstance().getManager().isNotNumeric(args[1])) {
            String message = getPluginInstance().getLangConfig().getString("invalid-amount");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        final int price = Math.max(-1, Integer.parseInt(args[1]));

        if (price > -1) {
            double foundSellPriceMax = (shop.getShopItem() != null ? getPluginInstance().getManager().getMaterialMaxPrice(shop.getShopItem(), false) : 0),
                    foundSellPriceMin = shop.getShopItem() != null ? getPluginInstance().getManager().getMaterialMinPrice(shop.getShopItem(), false) : 0;
            if (price < (foundSellPriceMin * shop.getShopItemAmount()) || price > (foundSellPriceMax * shop.getShopItemAmount())) {
                getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("sell-price-limit"));
                return;
            }

            if (price > shop.getBuyPrice(false) && shop.getBuyPrice(false) >= 0) {
                String message = getPluginInstance().getLangConfig().getString("sell-price-greater");
                if (message != null && !message.equalsIgnoreCase(""))
                    getPluginInstance().getManager().sendMessage(player, message);
                return;
            }
        }

        EconomyCallEvent economyCallEvent = getPluginInstance().getManager().initiateShopEconomyTransaction(player, null, shop,
                EconomyCallType.EDIT_ACTION, getPluginInstance().getConfig().getDouble("prices.sale-item-change"));
        if (economyCallEvent == null || !economyCallEvent.willSucceed()) return;

        shop.setSellPrice(price);
        shop.updateTimeStamp();
        shop.save(true);

        getPluginInstance().getInSightTask().refreshShop(shop);

        String message;
        if (price <= -1) message = getPluginInstance().getLangConfig().getString("selling-disabled");
        else message = getPluginInstance().getLangConfig().getString("sell-price-set");
        getPluginInstance().getManager().sendMessage(player, Objects.requireNonNull(message)
                .replace("{price}", getPluginInstance().getManager().formatNumber(price, true)));
        getPluginInstance().runEventCommands("shop-sell-price", player);
    }

    private void runBuyPrice(CommandSender commandSender, String[] args) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.buyprice")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        Block block = player.getTargetBlock(null, 10);
        Shop shop = getPluginInstance().getManager().getShop(block.getLocation());
        if (shop == null) {
            String message = getPluginInstance().getLangConfig().getString("shop-invalid");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        if (!player.hasPermission("displayshops.admin") && (shop.isAdminShop()
                || (shop.getOwnerUniqueId() != null && !shop.getOwnerUniqueId().toString().equals(player.getUniqueId().toString())))) {
            String message = getPluginInstance().getLangConfig().getString("not-shop-owner");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        if (getPluginInstance().getManager().isNotNumeric(args[1])) {
            String message = getPluginInstance().getLangConfig().getString("invalid-amount");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        final int price = Math.max(-1, Integer.parseInt(args[1]));

        if (price > -1) {
            double foundBuyPriceMax = (shop.getShopItem() != null ? getPluginInstance().getManager().getMaterialMaxPrice(shop.getShopItem(), true) : 0),
                    foundBuyPriceMin = shop.getShopItem() != null ? getPluginInstance().getManager().getMaterialMinPrice(shop.getShopItem(), true) : 0;
            if (price < (foundBuyPriceMin * shop.getShopItemAmount()) || price > (foundBuyPriceMax * shop.getShopItemAmount())) {
                getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("buy-price-limit"));
                return;
            }

            if (price < shop.getSellPrice(false) && shop.getSellPrice(false) >= 0) {
                getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("buy-price-less"));
                return;
            }
        }

        EconomyCallEvent economyCallEvent = getPluginInstance().getManager().initiateShopEconomyTransaction(player, null, shop,
                EconomyCallType.EDIT_ACTION, getPluginInstance().getConfig().getDouble("prices.buy-price"));
        if (economyCallEvent == null || !economyCallEvent.willSucceed()) return;

        shop.setBuyPrice(price);
        shop.updateTimeStamp();
        shop.save(true);

        getPluginInstance().getInSightTask().refreshShop(shop);

        String message;
        if (price == -1) message = getPluginInstance().getLangConfig().getString("buying-disabled");
        else message = getPluginInstance().getLangConfig().getString("buy-price-set");
        getPluginInstance().getManager().sendMessage(player, Objects.requireNonNull(message)
                .replace("{price}", getPluginInstance().getManager().formatNumber(price, true)));
        getPluginInstance().runEventCommands("shop-buy-price", player);
    }

    private void runNotify(CommandSender commandSender) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        final Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.notify")) {
            getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("no-permission"));
            return;
        }

        final DataPack dataPack = getPluginInstance().getManager().getDataPack(player);
        dataPack.setTransactionNotify(!dataPack.isTransactionNotify());
        getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("notify-toggle-"
                + (dataPack.isTransactionNotify() ? "on" : "off")));
    }

    private void runBlock(CommandSender commandSender) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        final Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.block")) {
            getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("no-permission"));
            return;
        }

        final ItemStack handItem = (Math.floor(this.getPluginInstance().getServerVersion()) >= 1_9
                ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInHand());
        if (handItem == null || handItem.getType().name().contains("AIR")) {
            getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("no-hand-item"));
            return;
        }

        File file = new File(getPluginInstance().getDataFolder(), "blocked-items.yml");
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        try {
            long id = 0;
            ConfigurationSection cs = yaml.getConfigurationSection("");
            if (cs != null) for (String key : cs.getKeys(false)) if (Long.parseLong(key) == id) id++;
            yaml.set(String.valueOf(id), getPluginInstance().getPacketManager().toString(handItem));
            yaml.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("item-blocked"));
    }

    private void runUnBlock(CommandSender commandSender) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        final Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.block")) {
            getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("no-permission"));
            return;
        }

        final ItemStack handItem = (Math.floor(this.getPluginInstance().getServerVersion()) >= 1_9
                ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInHand());
        if (handItem == null || handItem.getType().name().contains("AIR")) {
            getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("no-hand-item"));
            return;
        }

        final long foundId = getPluginInstance().getBlockedItemId(handItem);
        if (foundId < 0) {
            getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("item-not-blocked"));
            return;
        }

        File file = new File(getPluginInstance().getDataFolder(), "blocked-items.yml");
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        try {
            yaml.set(String.valueOf(foundId), getPluginInstance().getPacketManager().toString(handItem));
            yaml.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("item-unblocked"));
    }

    private void runAdvertise(CommandSender commandSender) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        final Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.advertise")) {
            getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("no-permission"));
            return;
        }

        final Block block = player.getTargetBlock(null, 10);
        final Shop shop = getPluginInstance().getManager().getShop(block.getLocation());
        if (shop == null) {
            String message = getPluginInstance().getLangConfig().getString("shop-invalid");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        final DataPack dataPack = getPluginInstance().getManager().getDataPack(player);
        final int cooldown = dataPack.getCooldown(player, "advertise", getPluginInstance().getConfig().getInt("shop-advertise-cooldown"));
        if (cooldown > 0) {
            String message = getPluginInstance().getLangConfig().getString("cooldown");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message.replace("{cooldown}", String.valueOf(cooldown)));
            return;
        }

        final double amount = getPluginInstance().getConfig().getDouble("shop-advertise-cost");
        if (amount > 0) {
            EconomyCallEvent economyCallEvent = new EconomyCallEvent(player, null, EconomyCallType.EDIT_ACTION, shop, amount);
            if (economyCallEvent.isCancelled() || !economyCallEvent.willSucceed()) {
                dataPack.resetEditData();
                return;
            }
        }

        String message = getPluginInstance().getLangConfig().getString("shop-advertise");
        if (message != null && !message.isEmpty()) {
            TextComponent textComponent = new TextComponent(getPluginInstance().getManager().color(message.replace("{player}", player.getName())
                    .replace("{item}", getPluginInstance().getManager().getItemName(shop.getShopItem()))
                    .replace("{buy}", getPluginInstance().getManager().formatNumber(shop.getBuyPrice(true), true))
                    .replace("{sell}", getPluginInstance().getManager().formatNumber(shop.getSellPrice(true), true))));
            if (shop.getShopItem() != null)
                textComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM,
                        new BaseComponent[]{new TextComponent(getPluginInstance().getPacketManager().toJSON(shop.getShopItem()))}));
            textComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/displayshops visit " + shop.getShopId()));
            getPluginInstance().getServer().getOnlinePlayers().forEach(p -> p.spigot().sendMessage(textComponent));
        }

        dataPack.updateCooldown("advertise");
    }

    private void runTime(CommandSender commandSender, String marketRegionId) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        final Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.rent")) {
            getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("no-permission"));
            return;
        }

        MarketRegion marketRegion = ((marketRegionId != null && !marketRegionId.isEmpty()) ? getPluginInstance().getManager().getMarketRegion(marketRegionId)
                : getPluginInstance().getManager().getMarketRegion(player.getLocation()));
        if (marketRegion == null) {
            getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("region-invalid")
                    .replace("{id}", (marketRegionId != null ? marketRegionId : "<invalid-id>")));
            return;
        }

        if (marketRegion.getRenter() != null && marketRegion.timeUntilExpire() > 0) {
            if (!marketRegion.getRenter().toString().equals(player.getUniqueId().toString()) && !player.hasPermission("displayshops.rentadmin")) {
                getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("not-the-renter")
                        .replace("{id}", marketRegion.getMarketId()));
                return;
            }

            getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("rent-time")
                    .replace("{cost}", getPluginInstance().getManager().formatNumber(getPluginInstance().getConfig().getDouble("rent-renew-cost"), true))
                    .replace("{id}", marketRegion.getMarketId()).replace("{duration}", marketRegion.formattedTimeUntilExpire()));
            return;
        }

        getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("region-rent-invalid")
                .replace("{id}", marketRegion.getMarketId()));
    }

    private void runRent(CommandSender commandSender, String marketRegionId) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        final Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.rent")) {
            getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("no-permission"));
            return;
        }

        MarketRegion marketRegion = ((marketRegionId != null && !marketRegionId.isEmpty()) ? getPluginInstance().getManager().getMarketRegion(marketRegionId)
                : getPluginInstance().getManager().getMarketRegion(player.getLocation()));
        if (marketRegion == null) {
            getPluginInstance().getManager().sendMessage(player, Objects.requireNonNull(getPluginInstance().getLangConfig().getString("region-invalid"))
                    .replace("{id}", (marketRegionId != null ? marketRegionId : "<invalid-id>")));
            return;
        }

        if (marketRegion.getRenter() != null && marketRegion.timeUntilExpire() > 0) {
            if (!marketRegion.getRenter().toString().equals(player.getUniqueId().toString()) && !player.hasPermission("displayshops.rentadmin")) {
                getPluginInstance().getManager().sendMessage(player, Objects.requireNonNull(getPluginInstance().getLangConfig().getString("not-the-renter"))
                        .replace("{id}", marketRegion.getMarketId()));
                return;
            }

            if (marketRegion.extendRent(player))
                getPluginInstance().getManager().sendMessage(player, Objects.requireNonNull(getPluginInstance().getLangConfig().getString("rent-extended"))
                        .replace("{cost}", getPluginInstance().getManager().formatNumber(getPluginInstance().getConfig().getDouble("rent-renew-cost"), true))
                        .replace("{id}", marketRegion.getMarketId()).replace("{duration}", marketRegion.formattedTimeUntilExpire()));
            else
                getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("insufficient-funds"));
            return;
        }

        if (getPluginInstance().getManager().exceededMarketRegionLimit(player)) {
            getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("region-limit-exceeded"));
            return;
        }

        if (marketRegion.rent(player))
            getPluginInstance().getManager().sendMessage(player, Objects.requireNonNull(getPluginInstance().getLangConfig().getString("rented"))
                    .replace("{cost}", getPluginInstance().getManager().formatNumber(getPluginInstance().getConfig().getDouble("rent-cost"), true))
                    .replace("{id}", marketRegion.getMarketId()).replace("{duration}", marketRegion.formattedTimeUntilExpire()));
        else
            getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("insufficient-funds"));
    }

    private void runReset(CommandSender commandSender, String marketRegionId) {
        if ((marketRegionId == null || marketRegionId.isEmpty()) && !(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        if (!commandSender.hasPermission("displayshops.reset")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        if (marketRegionId == null || marketRegionId.isEmpty()) {
            final Player player = (Player) commandSender;
            final MarketRegion marketRegion = getPluginInstance().getManager().getMarketRegion(player.getLocation());
            if (marketRegion == null) {
                getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("region-location-fail"));
                return;
            }

            marketRegion.reset();
            getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("market-region-reset")
                    .replace("{id}", marketRegion.getMarketId()));
            return;
        }

        final MarketRegion marketRegion = getPluginInstance().getManager().getMarketRegion(marketRegionId);
        marketRegion.reset();
        String message = getPluginInstance().getLangConfig().getString("market-region-reset");
        if (message != null && !message.equalsIgnoreCase(""))
            commandSender.sendMessage(getPluginInstance().getManager().color(message.replace("{id}", marketRegion.getMarketId())));
    }

    private void runClear(CommandSender commandSender, String worldName) {
        if (!commandSender.hasPermission("displayshops.clear")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                if (commandSender instanceof Player)
                    getPluginInstance().getManager().sendMessage((Player) commandSender, message);
                else commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        World world = getPluginInstance().getServer().getWorld(worldName);
        if (world == null) {
            String message = getPluginInstance().getLangConfig().getString("world-invalid");
            if (message != null && !message.equalsIgnoreCase(""))
                if (commandSender instanceof Player)
                    getPluginInstance().getManager().sendMessage((Player) commandSender, message.replace("{world}", worldName));
                else
                    commandSender.sendMessage(getPluginInstance().getManager().color(message.replace("{world}", worldName)));
            return;
        }


        getPluginInstance().getServer().getScheduler().runTaskAsynchronously(getPluginInstance(), () -> {
            List<Shop> shopList = new ArrayList<>(getPluginInstance().getManager().getShopMap().values());
            for (Shop shop : shopList) {
                if (shop != null || shop.getBaseLocation() == null || !shop.getBaseLocation().getWorldName().equalsIgnoreCase(worldName))
                    continue;
                try {
                    shop.delete(false);
                    for (Player player : getPluginInstance().getServer().getOnlinePlayers()) {
                        if (player == null || !player.isOnline() || !player.getLocation().getWorld().getName().equalsIgnoreCase(worldName))
                            continue;
                        shop.kill(player);
                        if (getPluginInstance().getShopMemory().containsKey(player.getUniqueId())) {
                            final UUID shopId = getPluginInstance().getShopMemory().get(player.getUniqueId());
                            if (shopId.toString().equals(shop.getShopId().toString()))
                                getPluginInstance().getShopMemory().remove(player.getUniqueId());
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        String message = getPluginInstance().getLangConfig().getString("world-cleared");
        if (message != null && !message.equalsIgnoreCase(""))
            if (commandSender instanceof Player)
                getPluginInstance().getManager().sendMessage((Player) commandSender, message.replace("{world}", world.getName()));
            else
                commandSender.sendMessage(getPluginInstance().getManager().color(message.replace("{world}", world.getName())));
    }

    private void runBBMAccess(CommandSender commandSender, boolean isUnlock, String[] args) {
        boolean isPlayer = (commandSender instanceof Player);
        if (!commandSender.hasPermission("displayshops.bbmaccess")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                if (isPlayer) getPluginInstance().getManager().sendMessage((Player) commandSender, message);
                else commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        final Player player = getPluginInstance().getServer().getPlayer(args[1]);
        if (player == null) {
            String message = getPluginInstance().getLangConfig().getString("player-invalid");
            if (message != null && !message.equalsIgnoreCase(""))
                if (isPlayer) getPluginInstance().getManager().sendMessage((Player) commandSender, message);
                else commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        final DataPack dataPack = getPluginInstance().getManager().getDataPack(player);
        dataPack.getBaseBlockUnlocks().clear();
        dataPack.updateAllBaseBlockAccess(isUnlock);

        String message = getPluginInstance().getLangConfig().getString(isUnlock ? "bbm-unlocked" : "bbm-locked");
        if (message != null && !message.equalsIgnoreCase(""))
            if (isPlayer)
                getPluginInstance().getManager().sendMessage((Player) commandSender, message.replace("{player}", player.getName()));
            else
                commandSender.sendMessage(getPluginInstance().getManager().color(message.replace("{player}", player.getName())));
    }

    private void runAddCommand(CommandSender commandSender, String[] args) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.commands")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        Block block = player.getTargetBlock(null, 10);
        Shop shop = getPluginInstance().getManager().getShop(block.getLocation());
        if (shop == null) {
            String message = getPluginInstance().getLangConfig().getString("shop-invalid");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        if (!shop.isAdminShop()) {
            String message = getPluginInstance().getLangConfig().getString("shop-not-admin");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        StringBuilder enteredCommand = new StringBuilder(args[1]);
        for (int i = 1; ++i < args.length; ) enteredCommand.append(" ").append(args[i]);
        shop.getCommands().add(enteredCommand.toString());

        if (!shop.getCommands().isEmpty() && shop.getSellPrice(false) >= 0) {
            shop.setSellPrice(-1);
            getPluginInstance().getInSightTask().refreshShop(shop);
        }

        String fixedCommand = enteredCommand.toString().replaceAll("(?i):PLAYER", "").replaceAll("(?i):CONSOLE", "");
        getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("command-added").replace("{command}", fixedCommand));
    }

    private void runRemoveCommand(CommandSender commandSender, String[] args) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.commands")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        Block block = player.getTargetBlock(null, 10);
        Shop shop = getPluginInstance().getManager().getShop(block.getLocation());
        if (shop == null) {
            String message = getPluginInstance().getLangConfig().getString("shop-invalid");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        String message = getPluginInstance().getLangConfig().getString("invalid-command-index");
        if (getPluginInstance().getManager().isNotNumeric(args[1])) {
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        int index = Integer.parseInt(args[1]);
        if (index < 0 || index > (shop.getCommands().size() - 1)) {
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        shop.getCommands().remove(index);
        if (!shop.getCommands().isEmpty() && shop.getSellPrice(false) >= 0) {
            shop.setSellPrice(-1);
            getPluginInstance().getInSightTask().refreshShop(shop);
        }

        String completeMessage = getPluginInstance().getLangConfig().getString("command-removed");
        if (completeMessage != null && !completeMessage.equalsIgnoreCase(""))
            getPluginInstance().getManager().sendMessage(player, completeMessage.replace("{index}",
                    getPluginInstance().getManager().formatNumber(index, false)));
    }

    private void runCommands(CommandSender commandSender) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.commands")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        Block block = player.getTargetBlock(null, 10);
        Shop shop = getPluginInstance().getManager().getShop(block.getLocation());
        if (shop == null) {
            String message = getPluginInstance().getLangConfig().getString("shop-invalid");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = -1; ++i < shop.getCommands().size(); ) {
            String commandLine = shop.getCommands().get(i);
            sb.append("(").append(i).append(") ").append(commandLine);
            if (i != (shop.getCommands().size() - 1)) sb.append(", ");
        }

        String message = getPluginInstance().getLangConfig().getString("shop-commands");
        if (message != null && !message.equalsIgnoreCase(""))
            getPluginInstance().getManager().sendMessage(player, message.replace("{commands}", sb.toString()));
    }

    private void runCommandMode(CommandSender commandSender) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.commands")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        Block block = player.getTargetBlock(null, 10);
        Shop shop = getPluginInstance().getManager().getShop(block.getLocation());
        if (shop == null) {
            String message = getPluginInstance().getLangConfig().getString("shop-invalid");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        shop.setCommandOnlyMode(!shop.isCommandOnlyMode());
        String message = getPluginInstance().getLangConfig().getString("command-only");
        if (message != null && !message.equalsIgnoreCase(""))
            getPluginInstance().getManager().sendMessage(player, message.replace("{status}", shop.isCommandOnlyMode() ? "True" : "False"));
    }

    private void runCDB(CommandSender commandSender) {

        if (!commandSender.hasPermission("displayshops.cdb")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        // cancel tasks
        if (getPluginInstance().getManagementTask() != null) getPluginInstance().getManagementTask().cancel();
        if (getPluginInstance().getInSightTask() != null) getPluginInstance().getInSightTask().cancel();
        if (getPluginInstance().getCleanupTask() != null) getPluginInstance().getCleanupTask().cancel();

        // reload configs
        getPluginInstance().reloadConfigs();

        // reload global variables
        getPluginInstance().getListeners().creationItem = getPluginInstance().getManager().buildShopCreationItem(null, 1);

        // handle market regions
        getPluginInstance().getServer().getScheduler().runTaskAsynchronously(getPluginInstance(), () -> {
            getPluginInstance().getManager().saveMarketRegions();
            getPluginInstance().getManager().getMarketRegions().clear();
            getPluginInstance().getManager().loadMarketRegions(true);
            getPluginInstance().getManager().cleanUpDataPacks();

            List<Shop> shopList = new ArrayList<>(getPluginInstance().getManager().getShopMap().values());
            for (Shop shop : shopList) {
                if (getPluginInstance().getManager().getShopMap().containsKey(shop.getShopId())) {
                    for (Player p : getPluginInstance().getServer().getOnlinePlayers())
                        shop.kill(p);
                    shop.save(false);
                }
            }

            getPluginInstance().getManager().getShopMap().clear();
            getPluginInstance().getManager().loadShops(true, true);
        });

        // restart tasks
        getPluginInstance().setupTasks();

        String message = getPluginInstance().getLangConfig().getString("clean-database");
        if (message != null && !message.equalsIgnoreCase(""))
            commandSender.sendMessage(getPluginInstance().getManager().color(message));
    }

    private void runCleanUp(CommandSender commandSender) {
        if (!commandSender.hasPermission("displayshops.cleanup")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        final int cleanInactiveTime = getPluginInstance().getConfig().getInt("clean-inactive-duration");

        int cleanCount = 0;
        final List<Shop> shopList = new ArrayList<>(getPluginInstance().getManager().getShopMap().values());
        for (Shop shop : shopList) {
            if (shop == null || shop.getBaseLocation() == null) {
                if (shop != null) {
                    shop.killAll();
                    shop.delete(true);
                    shop.unRegister();
                    cleanCount++;
                }
                continue;
            }

            if (cleanInactiveTime >= 0 && shop.getOwnerUniqueId() != null) {
                OfflinePlayer offlinePlayer = getPluginInstance().getServer().getOfflinePlayer(shop.getOwnerUniqueId());
                final long lastOnline = (((System.currentTimeMillis() - offlinePlayer.getLastPlayed()) / 1000) % 60);

                if (lastOnline >= cleanInactiveTime) {
                    shop.killAll();
                    shop.delete(true);
                    shop.unRegister();
                    cleanCount++;
                    continue;
                }
            }

            if (shop.getBaseLocation() != null) {
                World world = getPluginInstance().getServer().getWorld(shop.getBaseLocation().getWorldName());
                if (world == null) {
                    getPluginInstance().log(Level.WARNING, "The shop '" + shop.getShopId() + "' has a world named '"
                            + shop.getBaseLocation().getWorldName() + "' which was unable to be found.");
                    continue;
                }

                Location location = shop.getBaseLocation().asBukkitLocation();
                if (location != null) {
                    Block block = location.getBlock();
                    if (block == null) continue;

                    if (location.getBlock().getType().name().contains("AIR")) {
                        shop.killAll();
                        shop.delete(true);
                        shop.unRegister();
                        cleanCount++;
                        continue;
                    }

                    if (!shop.isAdminShop() && shop.getStock() < 0) shop.setStock(0);
                }
            } else {
                shop.unRegister();
                cleanCount++;
                getPluginInstance().log(Level.WARNING, "The shop '" + shop.getShopId() + "' had no base location and was therefore un-registered.");
            }
        }

        String message = getPluginInstance().getLangConfig().getString("cleanup-complete");
        if (message != null && !message.equalsIgnoreCase(""))
            commandSender.sendMessage(getPluginInstance().getManager().color(message
                    .replace("{count}", getPluginInstance().getManager().formatNumber(cleanCount, false))));
    }

    private void runCopy(CommandSender commandSender) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.copy")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        Block block = player.getTargetBlock(null, 10);
        Shop shop = getPluginInstance().getManager().getShop(block.getLocation());
        if (shop == null) {
            String message = getPluginInstance().getLangConfig().getString("shop-invalid");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        String message = getPluginInstance().getLangConfig().getString("shop-id-copy");
        if (message != null && !message.equalsIgnoreCase("")) {
            final boolean isCopyVersion = (Math.floor(this.getPluginInstance().getServerVersion()) >= 1_15);

            BaseComponent jsonMessage = new TextComponent(getPluginInstance().getManager().color(message.replace("{id}", shop.getShopId().toString())));
            jsonMessage.setClickEvent(new ClickEvent(!isCopyVersion ? ClickEvent.Action.SUGGEST_COMMAND : ClickEvent.Action.COPY_TO_CLIPBOARD, shop.getShopId().toString()));
            jsonMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new BaseComponent[]{
                    new TextComponent(shop.getShopId().toString())
            }));
            player.spigot().sendMessage(jsonMessage);
        }
    }

    private void runInfo(CommandSender commandSender) {
        if (!commandSender.hasPermission("displayshops.info")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        commandSender.sendMessage(getPluginInstance().getManager().color("\n&e<&m------------&r&e[ &bDisplayShops &e]&m------------&r&e>\n" +
                "&7Current Plugin Version: " + (getPluginInstance().getDescription().getVersion().toLowerCase().contains("build") ? "&c" : "&a") + getPluginInstance().getDescription().getVersion() + "\n" +
                "&7Latest Release Plugin Version: " + (getPluginInstance().getDescription().getVersion().toLowerCase().contains("snapshot") ? "&1" : "&a") + getPluginInstance().getLatestVersion() + "\n" +
                "&7Author(s): &bXZot1K\n" +
                "&e<&m-------------------------------------&r&e>\n"));
    }

    private void runCreateMarket(CommandSender commandSender, String[] args) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.cmr")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        if (getPluginInstance().getManager().doesMarketRegionExist(args[1])) {
            String message = getPluginInstance().getLangConfig().getString("region-exists");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        final DataPack dataPack = getPluginInstance().getManager().getDataPack(player);
        dataPack.setInSelectionMode(false);
        if (dataPack.getSelectedRegion() == null) {
            String message = getPluginInstance().getLangConfig().getString("no-selection");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        Region regionSelection = dataPack.getSelectedRegion();
        MarketRegion marketRegion = new MRegion(getPluginInstance(), args[1], regionSelection);
        getPluginInstance().getManager().getMarketRegions().add(marketRegion);
        dataPack.setSelectedRegion(null);

        String message = getPluginInstance().getLangConfig().getString("market-region-created");
        if (message != null && !message.equalsIgnoreCase(""))
            getPluginInstance().getManager().sendMessage(player, message.replace("{id}", marketRegion.getMarketId()));
    }

    private void runDeleteMarket(CommandSender commandSender, String[] args) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.dmr")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        MarketRegion marketRegion = getPluginInstance().getManager().getMarketRegion(args[1]);
        if (marketRegion == null) {
            String message = getPluginInstance().getLangConfig().getString("region-invalid");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        getPluginInstance().getManager().getMarketRegions().remove(marketRegion);
        marketRegion.delete(true);
        String message = getPluginInstance().getLangConfig().getString("market-region-deleted");
        if (message != null && !message.equalsIgnoreCase(""))
            getPluginInstance().getManager().sendMessage(player, message.replace("{id}", marketRegion.getMarketId()));
    }

    private void runMarketList(CommandSender commandSender) {
        if (!commandSender.hasPermission("displayshops.mrl")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        List<String> regionIds = new ArrayList<>();
        for (MarketRegion marketRegion : getPluginInstance().getManager().getMarketRegions())
            regionIds.add(marketRegion.getMarketId());

        String message = getPluginInstance().getLangConfig().getString("market-region-list");
        if (message != null && !message.equalsIgnoreCase(""))
            commandSender.sendMessage(getPluginInstance().getManager()
                    .color(message.replace("{list}", regionIds.toString())));
    }

    private void runCost(CommandSender commandSender, String region, String cost) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.cost")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        MarketRegion marketRegion = ((region != null && !region.isEmpty()) ? getPluginInstance().getManager().getMarketRegion(region) :
                getPluginInstance().getManager().getMarketRegion(player.getLocation()));
        if (marketRegion == null) {
            String message = getPluginInstance().getLangConfig().getString("region-invalid");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        if (getPluginInstance().getManager().isNotNumeric(cost)) {
            String message = getPluginInstance().getLangConfig().getString("invalid-amount");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        marketRegion.setCost(Double.parseDouble(cost));
        String message = getPluginInstance().getLangConfig().getString("market-region-cost");
        if (message != null && !message.equalsIgnoreCase(""))
            getPluginInstance().getManager().sendMessage(player, message
                    .replace("{id}", marketRegion.getMarketId())
                    .replace("{cost}", String.valueOf(marketRegion.getCost())));
    }

    private void runRenewCost(CommandSender commandSender, String region, String cost) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.rcost")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        MarketRegion marketRegion = ((region != null && !region.isEmpty()) ? getPluginInstance().getManager().getMarketRegion(region)
                : getPluginInstance().getManager().getMarketRegion(player.getLocation()));
        if (marketRegion == null) {
            String message = getPluginInstance().getLangConfig().getString("region-invalid");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        if (getPluginInstance().getManager().isNotNumeric(cost)) {
            String message = getPluginInstance().getLangConfig().getString("invalid-amount");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        marketRegion.setRenewCost(Double.parseDouble(cost));
        String message = getPluginInstance().getLangConfig().getString("market-region-rcost");
        if (message != null && !message.equalsIgnoreCase(""))
            getPluginInstance().getManager().sendMessage(player, message
                    .replace("{id}", marketRegion.getMarketId())
                    .replace("{cost}", String.valueOf(marketRegion.getRenewCost())));
    }

    private void runSelectionMode(CommandSender commandSender) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.sm")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        final DataPack dataPack = getPluginInstance().getManager().getDataPack(player);
        dataPack.setInSelectionMode(!dataPack.isInSelectionMode());
        dataPack.setSelectedRegion(null);
        String message = getPluginInstance().getLangConfig().getString("selection-mode-" + (dataPack.isInSelectionMode() ? "enabled" : "disabled"));
        if (message != null && !message.equalsIgnoreCase(""))
            getPluginInstance().getManager().sendMessage(player, message);
    }

    private void runAdmin(CommandSender commandSender) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.admin")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        Block block = player.getTargetBlock(null, 10);
        Shop shop = getPluginInstance().getManager().getShop(block.getLocation());
        if (shop == null) {
            String message = getPluginInstance().getLangConfig().getString("shop-invalid");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        if (shop.isAdminShop()) {
            shop.setOwnerUniqueId(player.getUniqueId());
            shop.setStock(shop.getShopItemAmount());
            getPluginInstance().getInSightTask().refreshShop(shop);

            String message = getPluginInstance().getLangConfig().getString("shop-admin-revert");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        shop.makeAdminShop();
        getPluginInstance().getInSightTask().refreshShop(shop);

        String message = getPluginInstance().getLangConfig().getString("shop-admin");
        if (message != null && !message.equalsIgnoreCase(""))
            getPluginInstance().getManager().sendMessage(player, message);
    }

    private void runReload(CommandSender commandSender) {
        if (!commandSender.hasPermission("displayshops.reload")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                if (commandSender instanceof Player)
                    getPluginInstance().getManager().sendMessage((Player) commandSender, message);
                else commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        if (getPluginInstance().getManagementTask() != null) getPluginInstance().getManagementTask().cancel();
        if (getPluginInstance().getInSightTask() != null) getPluginInstance().getInSightTask().cancel();
        if (getPluginInstance().getCleanupTask() != null) getPluginInstance().getCleanupTask().cancel();
        getPluginInstance().reloadConfigs();

        if (getPluginInstance().getConfig().getBoolean("use-vault")) {
            getPluginInstance().setupVaultEconomy();
            if (getPluginInstance().getVaultEconomy() == null)
                getPluginInstance().log(Level.WARNING, "Vault is either missing or has no economy counterpart. Now using integrated economy solution.");
        } else getPluginInstance().setVaultEconomy(null);

        // reload global variables
        getPluginInstance().getListeners().creationItem = getPluginInstance().getManager().buildShopCreationItem(null, 1);

        getPluginInstance().getServer().getScheduler().runTaskAsynchronously(getPluginInstance(), () -> {
            getPluginInstance().getManager().saveMarketRegions();
            getPluginInstance().getManager().getMarketRegions().clear();
            getPluginInstance().getManager().loadMarketRegions(false);

            List<Shop> shopList = new ArrayList<>(getPluginInstance().getManager().getShopMap().values());
            for (Shop shop : shopList) {
                if (getPluginInstance().getManager().getShopMap().containsKey(shop.getShopId())) {
                    shop.killAll();
                    shop.save(false);
                }
            }

            getPluginInstance().getManager().getShopMap().clear();
            getPluginInstance().getManager().loadShops(true, false);
        });
        getPluginInstance().setupTasks();

        String message = getPluginInstance().getLangConfig().getString("reload");
        if (message != null && !message.equalsIgnoreCase(""))
            commandSender.sendMessage(getPluginInstance().getManager().color(message));
    }

    private void runBuy(CommandSender commandSender, String amountValue) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("muse-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.buy")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        if (getPluginInstance().getManager().isNotNumeric(amountValue)) {
            String message = getPluginInstance().getLangConfig().getString("invalid-amount");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        int amount = Integer.parseInt(amountValue);
        if (amount < 0) {
            getPluginInstance().getManager().sendMessage(player, getPluginInstance().getLangConfig().getString("negative-entry"));
            return;
        }

        final ItemStack itemStack = getPluginInstance().getManager().buildShopCreationItem(player, amount);
        if (amount <= 0) amount = 1;
        else if (amount >= itemStack.getType().getMaxStackSize())
            amount = itemStack.getType().getMaxStackSize();

        if (getPluginInstance().getVaultEconomy() != null) {
            double price = getPluginInstance().getConfig().getDouble("creation-item-price");
            EconomyResponse economyResponse = getPluginInstance().getVaultEconomy().withdrawPlayer(player, (price * amount));
            if (!economyResponse.transactionSuccess()) {
                String message = getPluginInstance().getLangConfig().getString("insufficient-funds");
                if (message != null && !message.equalsIgnoreCase(""))
                    getPluginInstance().getManager().sendMessage(player, message.replace("{price}",
                            getPluginInstance().getManager().formatNumber(price, true)));
                return;
            } else {
                String message = getPluginInstance().getLangConfig().getString("purchased-creation-item");
                if (message != null && !message.equalsIgnoreCase(""))
                    getPluginInstance().getManager().sendMessage(player, message
                            .replace("{amount}", getPluginInstance().getManager().formatNumber(amount, false))
                            .replace("{price}", getPluginInstance().getManager().formatNumber((price * amount), true)));
            }
        }

        if (player.getInventory().firstEmpty() == -1)
            player.getWorld().dropItemNaturally(player.getLocation(), itemStack);
        else player.getInventory().addItem(itemStack);
    }

    private void runDelete(CommandSender commandSender) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.delete")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        Shop shop = getPluginInstance().getManager().getShopRayTraced(player.getWorld().getName(),
                player.getEyeLocation().toVector(), player.getEyeLocation().getDirection(), 10);
        if (shop == null) {
            String message = getPluginInstance().getLangConfig().getString("shop-invalid");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        if ((shop.getOwnerUniqueId() == null || (shop.getOwnerUniqueId() != null && !shop.getOwnerUniqueId().toString().equals(player.getUniqueId().toString())))
                && !player.hasPermission("displayshops.admindelete")) {
            String message = getPluginInstance().getLangConfig().getString("shop-delete-fail");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        shop.purge(player, true);
        String message = getPluginInstance().getLangConfig().getString("shop-deleted");
        if (message != null && !message.equalsIgnoreCase(""))
            getPluginInstance().getManager().sendMessage(player, message);
    }

    private void runOwner(CommandSender commandSender, String[] args) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.owner")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        Block block = player.getTargetBlock(null, 10);
        Shop shop = getPluginInstance().getManager().getShop(block.getLocation());
        if (shop == null) {
            String message = getPluginInstance().getLangConfig().getString("shop-invalid");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        Player enteredPlayer = getPluginInstance().getServer().getPlayer(args[1]);
        if (enteredPlayer == null || !player.isOnline()) {
            String message = getPluginInstance().getLangConfig().getString("player-invalid");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        shop.setOwnerUniqueId(enteredPlayer.getUniqueId());
        getPluginInstance().getInSightTask().refreshShop(shop);

        String message = getPluginInstance().getLangConfig().getString("shop-set-owner");
        if (message != null && !message.equalsIgnoreCase(""))
            getPluginInstance().getManager().sendMessage(player, message.replace("{player}", enteredPlayer.getName()));
    }

    private void runStock(CommandSender commandSender, String[] args) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.stock")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        Block block = player.getTargetBlock(null, 10);
        Shop shop = getPluginInstance().getManager().getShop(block.getLocation());
        if (shop == null) {
            String message = getPluginInstance().getLangConfig().getString("shop-invalid");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        int enteredAmount;
        if (getPluginInstance().getManager().isNotNumeric(args[1])) {
            String message = getPluginInstance().getLangConfig().getString("invalid-amount");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }
        enteredAmount = Integer.parseInt(args[1]);

        if (enteredAmount > getPluginInstance().getManager().getMaxStock(shop)) {
            String message = getPluginInstance().getLangConfig().getString("invalid-stock");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        shop.setStock(enteredAmount);
        getPluginInstance().getInSightTask().refreshShop(shop);

        String message = getPluginInstance().getLangConfig().getString("stock-set");
        if (message != null && !message.equalsIgnoreCase(""))
            getPluginInstance().getManager().sendMessage(player, message.replace("{amount}",
                    getPluginInstance().getManager().formatNumber(enteredAmount, false)));
    }

    private void runVisit(CommandSender commandSender, String id, String filter) {
        if (!(commandSender instanceof Player)) {
            String message = getPluginInstance().getLangConfig().getString("must-be-player");
            if (message != null && !message.equalsIgnoreCase(""))
                commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        Player player = (Player) commandSender;
        if (!player.hasPermission("displayshops.visit")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        if (!getPluginInstance().getConfig().getBoolean("shop-visiting")) {
            String message = getPluginInstance().getLangConfig().getString("shop-visiting-disabled");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        if (getPluginInstance().getTeleportingPlayers().contains(player.getUniqueId())) {
            String message = getPluginInstance().getLangConfig().getString("visit-in-process");
            if (message != null && !message.equalsIgnoreCase(""))
                getPluginInstance().getManager().sendMessage(player, message);
            return;
        }

        if (id != null && !id.isEmpty()) {
            try {
                Shop shop = getPluginInstance().getManager().getShopById(UUID.fromString(id));
                if (shop != null) {
                    DDataPack dataPack = (DDataPack) getPluginInstance().getManager().getDataPack(player);
                    double visitCost = getPluginInstance().getConfig().getDouble("visit-charge");
                    EconomyCallEvent economyCallEvent = getPluginInstance().getManager().initiateShopEconomyTransaction(player,
                            null, shop, EconomyCallType.VISIT, visitCost);
                    if (economyCallEvent == null || economyCallEvent.isCancelled() || !economyCallEvent.willSucceed()) {
                        dataPack.resetEditData();
                        player.closeInventory();
                        return;
                    }

                    shop.visit(player, true);
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        Menu visitMenu = getPluginInstance().getMenu("visit");
        if (visitMenu != null) player.openInventory(visitMenu.build(player, filter));
    }

    private void runGive(CommandSender commandSender, String[] args) {
        if (!commandSender.hasPermission("displayshops.give")) {
            String message = getPluginInstance().getLangConfig().getString("no-permission");
            if (message != null && !message.equalsIgnoreCase(""))
                if (commandSender instanceof Player)
                    getPluginInstance().getManager().sendMessage((Player) commandSender, message);
                else commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        Player player = getPluginInstance().getServer().getPlayer(args[1]);
        if (player == null || !player.isOnline()) {
            String message = getPluginInstance().getLangConfig().getString("player-invalid");
            if (message != null && !message.equalsIgnoreCase(""))
                if (commandSender instanceof Player)
                    getPluginInstance().getManager().sendMessage((Player) commandSender, message);
                else commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        int enteredAmount;
        if (getPluginInstance().getManager().isNotNumeric(args[2])) {
            String message = getPluginInstance().getLangConfig().getString("invalid-amount");
            if (message != null && !message.equalsIgnoreCase(""))
                if (commandSender instanceof Player)
                    getPluginInstance().getManager().sendMessage((Player) commandSender, message);
                else commandSender.sendMessage(getPluginInstance().getManager().color(message));
            return;
        }

        enteredAmount = Integer.parseInt(args[2]);

        ItemStack itemStack = getPluginInstance().getManager().buildShopCreationItem(player, enteredAmount);
        if (player.getInventory().firstEmpty() == -1)
            player.getWorld().dropItemNaturally(player.getLocation(), itemStack);
        else
            player.getInventory().addItem(itemStack);

        String message1 = getPluginInstance().getLangConfig().getString("received-creation-item");
        if (message1 != null && !message1.equalsIgnoreCase(""))
            getPluginInstance().getManager().sendMessage(player, message1
                    .replace("{amount}", getPluginInstance().getManager().formatNumber(enteredAmount, false)));

        if (!commandSender.getName().equalsIgnoreCase(player.getName())) {
            String message2 = getPluginInstance().getLangConfig().getString("given-creation-item");
            if (message2 != null && !message2.equalsIgnoreCase(""))
                if (commandSender instanceof Player)
                    getPluginInstance().getManager().sendMessage((Player) commandSender, message2
                            .replace("{amount}", getPluginInstance().getManager().formatNumber(enteredAmount, false))
                            .replace("{player}", player.getName()));
                else commandSender.sendMessage(getPluginInstance().getManager().color(message2
                        .replace("{amount}", getPluginInstance().getManager().formatNumber(enteredAmount, false))
                        .replace("{player}", player.getName())));
        }
    }

    // other helpers
    private void sendHelp(CommandSender commandSender) {
        final String helpMessage = getPluginInstance().getLangConfig().getString(commandSender.hasPermission("displayshops.adminhelp")
                ? "admin-help-message" : "user-help-message");
        if (helpMessage == null || helpMessage.isEmpty()) return;

        final String helpLink = getPluginInstance().getConfig().getString("help-command-link");
        if (commandSender instanceof Player) {
            Player player = (Player) commandSender;

            if (helpLink != null && !helpLink.isEmpty()) {
                TextComponent textComponent = new TextComponent(getPluginInstance().getManager().color(helpMessage));
                textComponent.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, helpLink.equalsIgnoreCase("wiki")
                        ? "https://github.com/XZot1K/DisplayShopsAPI/wiki/Commands" : helpLink));
                player.spigot().sendMessage(textComponent);
                return;
            }

            getPluginInstance().getManager().sendMessage(player, helpMessage);
            return;
        }

        commandSender.sendMessage(getPluginInstance().getManager().color(helpMessage));
        if (helpLink != null && !helpLink.isEmpty())
            commandSender.sendMessage(helpLink.equalsIgnoreCase("wiki") ? "https://github.com/XZot1K/DisplayShopsAPI/wiki/Commands" : helpLink);
    }

    // getters & setters
    private DisplayShops getPluginInstance() {
        return pluginInstance;
    }

    private void setPluginInstance(DisplayShops pluginInstance) {
        this.pluginInstance = pluginInstance;
    }
}