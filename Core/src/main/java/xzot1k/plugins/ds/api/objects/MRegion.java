/*
 * Copyright (c) 2021 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.api.objects;

import org.apache.commons.lang.WordUtils;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xzot1k.plugins.ds.DisplayShops;
import xzot1k.plugins.ds.api.enums.EconomyCallType;
import xzot1k.plugins.ds.api.events.EconomyCallEvent;
import xzot1k.plugins.ds.api.events.MarketRegionRentEvent;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class MRegion implements MarketRegion {
    private final DisplayShops pluginInstance;
    private String marketId, rentedTimeStamp;
    private Region region;
    private UUID renter;
    private int extendedDuration;
    private BigDecimal cost, renewCost;

    public MRegion(DisplayShops pluginInstance, String marketId, Region region) {
        this.pluginInstance = pluginInstance;
        this.setMarketId(marketId);
        this.setRegion(region);
        this.setExtendedDuration(0);
        this.setRenter(null);
        this.setRentedTimeStamp(null);
        setCost(getPluginInstance().getConfig().getDouble("rent-cost"));
        setRenewCost(getPluginInstance().getConfig().getDouble("rent-renew-cost"));
    }

    @Override
    public void reset() {
        getPluginInstance().getServer().getScheduler().runTask(this.getPluginInstance(), () -> {
            if (getRenter() != null) {
                Player renter = this.getPluginInstance().getServer().getPlayer(getRenter());
                if (renter != null) getPluginInstance().getManager().sendMessage(renter,
                        Objects.requireNonNull(getPluginInstance().getLangConfig().getString("rent-expired"))
                                .replace("{id}", WordUtils.capitalize(this.getMarketId())));
            }
            getPluginInstance().getServer().getOnlinePlayers().parallelStream().forEach(player -> getPluginInstance().clearDisplayPackets(player));
        });

        getPluginInstance().getManager().getShopMap().entrySet().parallelStream().forEach(entry -> {
            final Shop shop = entry.getValue();
            if (shop != null && getPluginInstance().getManager().getShopMap().containsKey(shop.getShopId()) && isInRegion(shop.getBaseLocation())) {
                getPluginInstance().getServer().getScheduler().runTask(this.getPluginInstance(), shop::dropStock);
                resetHelper(shop);
            }
        });

        setRenter(null);
        setRentedTimeStamp(null);
        setExtendedDuration(0);
    }

    @Override
    public void resetHelper(@NotNull Shop shop) {
        shop.reset();
        shop.setOwnerUniqueId(UUID.randomUUID());
    }

    @Override
    public void updateRentedTimeStamp() {
        this.setRentedTimeStamp(this.getPluginInstance().getDateFormat().format(new Date(System.currentTimeMillis())));
    }

    @Override
    public boolean extendRent(@NotNull Player player) {
        final EconomyCallEvent economyCallEvent = EconomyCallEvent.call(player, null, EconomyCallType.RENT_RENEW,
                getPluginInstance().getConfig().getDouble("rent-renew-cost"));
        if (economyCallEvent.failed()) return false;

        MarketRegionRentEvent rentEvent = new MarketRegionRentEvent(player, this, true);
        this.getPluginInstance().getServer().getPluginManager().callEvent(rentEvent);
        if (rentEvent.isCancelled()) return true;

        this.setExtendedDuration(getExtendedDuration() + getPluginInstance().getConfig().getInt("rent-extend-duration"));
        return true;
    }

    @Override
    public boolean rent(@NotNull Player player) {
        final EconomyCallEvent economyCallEvent = EconomyCallEvent.call(player, null, EconomyCallType.RENT, ((timeUntilExpire() > 0) ?
                getRenewCost() : getCost()));
        if (economyCallEvent.failed()) return false;

        MarketRegionRentEvent rentEvent = new MarketRegionRentEvent(player, this, false);
        this.getPluginInstance().getServer().getPluginManager().callEvent(rentEvent);
        if (rentEvent.isCancelled()) return true;

        this.updateRentedTimeStamp();
        this.setRenter(player.getUniqueId());
        for (Shop shop : this.getPluginInstance().getManager().getShopMap().values()) {
            if (shop == null || shop.getBaseLocation() == null || !isInRegion(shop.getBaseLocation())) continue;
            shop.setOwnerUniqueId(player.getUniqueId());
            shop.setStock(0);
            shop.setStoredBalance(0.0);
        }

        return true;
    }

    @Override
    public int timeUntilExpire() {
        int timeLeft = 0;
        if (this.getRentedTimeStamp() != null && !this.getRentedTimeStamp().isEmpty()) {
            try {
                Date rentedTime = this.getPluginInstance().getDateFormat().parse(this.getRentedTimeStamp());
                Date currentDate = new Date(System.currentTimeMillis());
                timeLeft = (int) ((long) (this.getPluginInstance().getConfig().getInt("rent-expire-duration")
                        + this.getExtendedDuration()) - (currentDate.getTime() - rentedTime.getTime()) / 1000L);
            } catch (Exception ignored) {}
        }
        return timeLeft;
    }

    @Override
    public String formattedTimeUntilExpire() {
        StringBuilder stringBuilder = new StringBuilder();
        long seconds = this.timeUntilExpire(),
                days = TimeUnit.SECONDS.toDays(seconds),
                hours = TimeUnit.SECONDS.toHours(seconds -= TimeUnit.DAYS.toSeconds(days)),
                minutes = TimeUnit.SECONDS.toMinutes(seconds -= TimeUnit.HOURS.toSeconds(hours));
        seconds -= TimeUnit.MINUTES.toSeconds(days);

        final boolean pluralize = getPluginInstance().getLangConfig().getBoolean("duration-format.pluralize");
        final String pluralizeLettering = getPluginInstance().getLangConfig().getString("duration-format.plural-lettering");

        if (days > 0L) {
            stringBuilder.append(Objects.requireNonNull(getPluginInstance().getLangConfig().getString("duration-format.days"))
                            .replace("{amount}", getPluginInstance().getManager().formatNumber(days, false)))
                    .append((days > 1L && pluralize) ? pluralizeLettering : "");
        }

        if (hours > 0L) {
            if (days > 0L) stringBuilder.append(" ");
            stringBuilder.append(Objects.requireNonNull(getPluginInstance().getLangConfig().getString("duration-format.hours"))
                            .replace("{amount}", getPluginInstance().getManager().formatNumber(hours, false)))
                    .append((hours > 1L && pluralize) ? pluralizeLettering : "");
        }

        if (minutes > 0L) {
            if (hours > 0L || days > 0L) stringBuilder.append(" ");
            stringBuilder.append(Objects.requireNonNull(getPluginInstance().getLangConfig().getString("duration-format.minutes"))
                            .replace("{amount}", getPluginInstance().getManager().formatNumber(minutes, false)))
                    .append((minutes > 1L && pluralize) ? pluralizeLettering : "");
        }

        if (seconds > 0L) {
            if (minutes > 0L || hours > 0L || days > 0L) stringBuilder.append(" ");
            stringBuilder.append(Objects.requireNonNull(getPluginInstance().getLangConfig().getString("duration-format.seconds"))
                            .replace("{amount}", getPluginInstance().getManager().formatNumber(seconds, false)))
                    .append((seconds > 1L && pluralize) ? pluralizeLettering : "");
        }

        return stringBuilder.toString();
    }

    @Override
    public void delete(boolean async) {
        if (async) {
            DisplayShops.getPluginInstance().getServer().getScheduler()
                    .runTaskAsynchronously(DisplayShops.getPluginInstance(), (Runnable) this::delete);
        } else delete();
    }

    private void delete() {
        try {
            PreparedStatement preparedStatement = DisplayShops.getPluginInstance().getDatabaseConnection()
                    .prepareStatement("delete from market_regions where id = '" + this.getMarketId() + "'");
            preparedStatement.executeUpdate();
            preparedStatement.close();
        } catch (SQLException e) {
            e.printStackTrace();
            DisplayShops.getPluginInstance().log(Level.WARNING, "There was an issue deleting the market region "
                    + this.getMarketId() + " from the database (" + e.getMessage() + ").");
        }
    }

    @Override
    public boolean isInRegion(@NotNull Location location) {
        if (this.getRegion().getPointOne() == null || this.getRegion().getPointTwo() == null) return false;

        return (location.getWorld() != null
                && (location.getWorld().getName().equalsIgnoreCase(this.getRegion().getPointOne().getWorldName())
                && (location.getX() >= this.getRegion().getPointOne().getX()
                && location.getX() <= this.getRegion().getPointTwo().getX()
                || location.getX() <= this.getRegion().getPointOne().getX()
                && location.getX() >= this.getRegion().getPointTwo().getX())
                && (location.getY() >= this.getRegion().getPointOne().getY()
                && location.getY() <= this.getRegion().getPointTwo().getY()
                || location.getY() <= this.getRegion().getPointOne().getY()
                && location.getY() >= this.getRegion().getPointTwo().getY())
                && (location.getZ() >= this.getRegion().getPointOne().getZ()
                && location.getZ() <= this.getRegion().getPointTwo().getZ()
                || location.getZ() <= this.getRegion().getPointOne().getZ()
                && location.getZ() >= this.getRegion().getPointTwo().getZ())));
    }

    @Override
    public boolean isInRegion(@NotNull LocationClone location) {
        if (this.getRegion().getPointOne() == null || this.getRegion().getPointTwo() == null) return false;

        return location.getWorldName().equalsIgnoreCase(this.getRegion().getPointOne().getWorldName())
                && (location.getX() >= this.getRegion().getPointOne().getX()
                && location.getX() <= this.getRegion().getPointTwo().getX()
                || location.getX() <= this.getRegion().getPointOne().getX()
                && location.getX() >= this.getRegion().getPointTwo().getX())
                && (location.getY() >= this.getRegion().getPointOne().getY()
                && location.getY() <= this.getRegion().getPointTwo().getY()
                || location.getY() <= this.getRegion().getPointOne().getY()
                && location.getY() >= this.getRegion().getPointTwo().getY())
                && (location.getZ() >= this.getRegion().getPointOne().getZ()
                && location.getZ() <= this.getRegion().getPointTwo().getZ()
                || location.getZ() <= this.getRegion().getPointOne().getZ()
                && location.getZ() >= this.getRegion().getPointTwo().getZ());
    }

    @Override
    public String getMarketId() {
        return this.marketId;
    }

    private void setMarketId(String marketId) {
        this.marketId = marketId;
    }

    @Override
    public Region getRegion() {
        return this.region;
    }

    private void setRegion(Region region) {
        this.region = region;
    }

    @Override
    public UUID getRenter() {
        return this.renter;
    }

    @Override
    public void setRenter(@Nullable UUID renter) {this.renter = renter;}

    @Override
    public String getRentedTimeStamp() {
        return this.rentedTimeStamp;
    }

    @Override
    public void setRentedTimeStamp(@Nullable String rentedTimeStamp) {
        this.rentedTimeStamp = rentedTimeStamp;
    }

    @Override
    public int getExtendedDuration() {
        return this.extendedDuration;
    }

    @Override
    public void setExtendedDuration(int extendedDuration) {
        this.extendedDuration = extendedDuration;
    }

    private DisplayShops getPluginInstance() {
        return this.pluginInstance;
    }

    public double getCost() {return cost.doubleValue();}

    public void setCost(double cost) {this.cost = new BigDecimal(cost);}

    public double getRenewCost() {return renewCost.doubleValue();}

    public void setRenewCost(double renewCost) {this.renewCost = new BigDecimal(renewCost);}

}