/*
 * Copyright (c) 2021 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.core.packets.v1_20_R1;

import com.mojang.datafixers.util.Pair;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketDataSerializer;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.network.syncher.DataWatcherSerializer;
import net.minecraft.server.network.PlayerConnection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EnumItemSlot;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.DisplayShops;
import xzot1k.plugins.ds.api.handlers.DisplayPacket;
import xzot1k.plugins.ds.api.objects.Shop;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class DPacket implements DisplayPacket {

    private static final AtomicInteger ENTITY_ID_COUNTER_FIELD = getAI();
    private static final Supplier<Integer> idGenerator = setGenerator();
    private final DisplayShops INSTANCE;
    private final Collection<Integer> entityIds = new ArrayList<>();
    private net.minecraft.world.item.ItemStack itemStack;

    public DPacket(@NotNull DisplayShops instance, @NotNull Player player, @NotNull Shop shop, boolean showHolograms) {
        this.INSTANCE = instance;
        if (!player.isOnline()) return;

        final Double[] offsets = INSTANCE.getManager().getBaseBlockOffsets(shop);
        final double offsetX = offsets[0], offsetY = offsets[1], offsetZ = offsets[2];

        final PlayerConnection playerConnection = getPlayerConnection(player);
        if (playerConnection == null) return;

        if (!INSTANCE.getConfig().getBoolean("hide-glass")) {
            double x = (shop.getBaseLocation().getX() + offsetX),
                    y = (shop.getBaseLocation().getY() + offsetY),
                    z = (shop.getBaseLocation().getZ() + offsetZ);
            createStand(playerConnection, x, y, z, "", true);
        }

        ItemStack item = (shop.getShopItem() != null) ? shop.getShopItem().clone() : (INSTANCE.getConfig().getBoolean("empty-shop-item")
                ? new ItemStack(Material.BARRIER) : null);
        if (item != null) {
            if (INSTANCE.getConfig().getBoolean("force-single-stack")) item.setAmount(1);

            if (item.getType() != Material.AIR) {
                itemStack = CraftItemStack.asNMSCopy(item);
                if (itemStack != null) {
                    //<editor-fold desc="Item Packet">
                    final int id = idGenerator.get();
                    getEntityIds().add(id);

                    PacketDataSerializer pds = buildSerializer(id, true, (shop.getBaseLocation().getX() + offsetX),
                            ((shop.getBaseLocation().getY() + 1.325) + offsetY), (shop.getBaseLocation().getZ() + offsetZ));

                    final PacketPlayOutSpawnEntity itemPacket = new PacketPlayOutSpawnEntity(pds);
                    sendPacket(playerConnection, itemPacket);

                    final DataWatcherSerializer<net.minecraft.world.item.ItemStack> ITEM_STACK_SERIALIZER = DataWatcherRegistry.h;
                    final DataWatcherSerializer<Byte> BYTE_SERIALIZER = DataWatcherRegistry.a;

                    PacketDataSerializer metaData = new PacketDataSerializer(Unpooled.buffer());
                    metaData.d(id);
                    metaData.writeByte(8); // key index

                    int serializerTypeID = DataWatcherRegistry.b(ITEM_STACK_SERIALIZER);
                    if (serializerTypeID < 0) return;

                    metaData.d(serializerTypeID); // key serializer type id
                    ITEM_STACK_SERIALIZER.a(metaData, itemStack);
                    metaData.d(0xFF);

                    PacketPlayOutEntityMetadata md = new PacketPlayOutEntityMetadata(metaData);
                    sendPacket(playerConnection, md);
                    //</editor-fold>

                    //<editor-fold desc="Vehicle Mount Packets">
                    final int vehicleId = idGenerator.get();
                    getEntityIds().add(vehicleId);
                    PacketDataSerializer vehicleData = buildSerializer(vehicleId, false, (shop.getBaseLocation().getX() + offsetX),
                            ((shop.getBaseLocation().getY() + 1.325) + offsetY), (shop.getBaseLocation().getZ() + offsetZ));
                    final PacketPlayOutSpawnEntity vehiclePacket = new PacketPlayOutSpawnEntity(vehicleData);
                    sendPacket(playerConnection, vehiclePacket);

                    PacketDataSerializer vehiclePDS = new PacketDataSerializer(Unpooled.buffer());
                    vehiclePDS.d(vehicleId);

                    // invisibility
                    vehiclePDS.writeByte(0); // key index
                    serializerTypeID = DataWatcherRegistry.b(BYTE_SERIALIZER);
                    if (serializerTypeID < 0) return;

                    vehiclePDS.d(serializerTypeID); // key serializer type id
                    BYTE_SERIALIZER.a(vehiclePDS, (byte) 0x20);

                    // small, no gravity, no base-plate marker, etc.
                    vehiclePDS.writeByte(15); // key index
                    serializerTypeID = DataWatcherRegistry.b(BYTE_SERIALIZER);
                    if (serializerTypeID < 0) return;

                    vehiclePDS.d(serializerTypeID); // key serializer type id
                    BYTE_SERIALIZER.a(vehiclePDS, (byte) (0x01 | 0x02 | 0x08 | 0x10));
                    vehiclePDS.d(0xFF);

                    PacketPlayOutEntityMetadata vehicleMD = new PacketPlayOutEntityMetadata(vehiclePDS);
                    sendPacket(playerConnection, vehicleMD);


                    PacketDataSerializer mountData = new PacketDataSerializer(Unpooled.buffer());
                    mountData.d(vehicleId);
                    mountData.d(1);
                    mountData.d(id);

                    PacketPlayOutMount mountPacket = new PacketPlayOutMount(mountData);
                    sendPacket(playerConnection, mountPacket);
                    //</editor-fold>
                }
            }
        }

        if (!showHolograms) return;

        List<String> hologramFormat;
        if (shop.getShopItem() != null) {
            if (shop.getOwnerUniqueId() == null)
                hologramFormat = INSTANCE.getConfig().getStringList("admin-shop-format");
            else hologramFormat = INSTANCE.getConfig().getStringList("valid-item-format");
        } else {
            if (shop.getOwnerUniqueId() == null)
                hologramFormat = INSTANCE.getConfig().getStringList("admin-invalid-item-format");
            else hologramFormat = INSTANCE.getConfig().getStringList("invalid-item-format");
        }

        boolean useVault = (INSTANCE.getConfig().getBoolean("use-vault") && INSTANCE.getVaultEconomy() != null),
                forceUse = INSTANCE.getConfig().getBoolean("shop-currency-item.force-use");

        String itemName, tradeItemName;
        itemName = tradeItemName = "";

        if (shop.getShopItem() != null)
            itemName = INSTANCE.getManager().getItemName(shop.getShopItem());

        if (!useVault)
            if (!forceUse && shop.getTradeItem() != null)
                tradeItemName = INSTANCE.getManager().getItemName(shop.getTradeItem());
            else {
                ItemStack currencyItem = INSTANCE.getManager().buildShopCurrencyItem(1);
                if (currencyItem != null) tradeItemName = INSTANCE.getManager().getItemName(currencyItem);
            }

        final int wordCount = INSTANCE.getConfig().getInt("description-word-line-limit");
        String ownerName = shop.getOwnerUniqueId() == null ? "" : INSTANCE.getServer().getOfflinePlayer(shop.getOwnerUniqueId()).getName();
        double x = (shop.getBaseLocation().getX() + offsetX), y = (shop.getBaseLocation().getY() + (1.9 + offsetY)), z = (shop.getBaseLocation().getZ() + offsetZ),
                tax = INSTANCE.getConfig().getDouble("transaction-tax");
        for (int i = hologramFormat.size(); --i >= 0; ) {
            String line = hologramFormat.get(i);

            if ((line.contains("{buy-price}") && shop.getBuyPrice(true) < 0)
                    || (line.contains("{sell-price}") && shop.getSellPrice(true) < 0)
                    || ((line.contains("{description}") && (shop.getDescription() == null
                    || shop.getDescription().equalsIgnoreCase("")))))
                continue;

            if (line.contains("{description}") && !(shop.getDescription() == null || shop.getDescription().equalsIgnoreCase(""))) {

                final String[] otherContents = line.split("\\{description}");
                final String prefix = (otherContents.length >= 1 ? otherContents[0] : ""),
                        suffix = (otherContents.length >= 2 ? otherContents[1] : "");

                List<String> descriptionLines = INSTANCE.getManager().wrapString(shop.getDescription(), wordCount);
                Collections.reverse(descriptionLines);
                for (String descriptionLine : descriptionLines) {
                    createStand(playerConnection, x, y, z, (prefix + descriptionLine + suffix), false);
                    y += 0.3;
                }

                continue;
            }

            double buyPrice = shop.getBuyPrice(true), sellPrice = shop.getBuyPrice(true);
            createStand(playerConnection, x, y, z, line
                    .replace("{buy-price}", INSTANCE.getManager().formatNumber(shop.getBuyPrice(true), true))
                    .replace("{sell-price}", INSTANCE.getManager().formatNumber(shop.getSellPrice(true), true))
                    .replace("{taxed-buy-price}", INSTANCE.getManager().formatNumber((buyPrice + (buyPrice * tax)), true))
                    .replace("{taxed-sell-price}", INSTANCE.getManager().formatNumber((sellPrice + (sellPrice * tax)), true))
                    .replace("{stock}", (shop.getStock() < 0) ? "\u221E" : INSTANCE.getManager().formatNumber(shop.getStock(), false))
                    .replace("{amount}", String.valueOf(shop.getShopItem() != null ?
                            INSTANCE.getManager().formatNumber(shop.getShopItemAmount(), false) : 0))
                    .replace("{trade-item}", tradeItemName).replace("{item}", itemName)
                    .replace("{owner}", ownerName == null ? "---" : ownerName), false);
            y += 0.3;
        }
    }

    private static AtomicInteger getAI() {
        try {
            for (Field field : Entity.class.getDeclaredFields()) {
                if (field.getType() == AtomicInteger.class) {
                    field.setAccessible(true);
                    return ((AtomicInteger) field.get(null));
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return new AtomicInteger();
    }

    private static Supplier<Integer> setGenerator() {
        return Objects.requireNonNull(ENTITY_ID_COUNTER_FIELD)::incrementAndGet;
    }

    private PlayerConnection getPlayerConnection(@NotNull Player player) {
        return ((CraftPlayer) player).getHandle().c;
    }

    private PacketDataSerializer buildSerializer(int id, boolean isItem, double x, double y, double z) {
        PacketDataSerializer pds = new PacketDataSerializer(Unpooled.buffer());

        pds.d(id);
        pds.a(UUID.randomUUID());
        pds.d(isItem ? 54 : 2); // (item is 54, armor stand is 2, and slime is 83)

        // Position
        pds.writeDouble(x);
        pds.writeDouble(y);
        pds.writeDouble(z);

        // Rotation
        pds.writeByte(0);
        pds.writeByte(0);

        // Object Data
        pds.writeInt(isItem ? 1 : 0);

        // Velocity
        pds.writeShort(0);
        pds.writeShort(0);
        pds.writeShort(0);

        return pds;
    }

    private void createStand(@NotNull PlayerConnection playerConnection, double x, double y, double z,
                             @NotNull String name, boolean glassHead) {

        final int id = idGenerator.get();
        getEntityIds().add(id);

        final PacketDataSerializer pds = buildSerializer(id, false, x, y, z);
        final PacketPlayOutSpawnEntity spawnPacket = new PacketPlayOutSpawnEntity(pds);
        sendPacket(playerConnection, spawnPacket);

        final DataWatcherSerializer<Byte> BYTE_SERIALIZER = DataWatcherRegistry.a;
        final DataWatcherSerializer<Boolean> BOOLEAN_SERIALIZER = DataWatcherRegistry.k;
        final DataWatcherSerializer<Optional<IChatBaseComponent>> OPTIONAL_CHAT_COMPONENT_SERIALIZER = DataWatcherRegistry.g;

        if (glassHead) {
            itemStack = CraftItemStack.asNMSCopy(new ItemStack(Material.GLASS));
            if (itemStack != null) {
                List<Pair<EnumItemSlot, net.minecraft.world.item.ItemStack>> list = new ArrayList<>();
                list.add(new Pair<>(EnumItemSlot.f /*HEAD or HELMET*/, itemStack));
                PacketPlayOutEntityEquipment packet = new PacketPlayOutEntityEquipment(id, list);
                sendPacket(playerConnection, packet);
            }
        }

        PacketDataSerializer metaData = new PacketDataSerializer(Unpooled.buffer());
        metaData.d(id);

        // invisibility
        metaData.writeByte(0); // entity status index (BYTE_SERIALIZER)
        int serializerTypeID = DataWatcherRegistry.b(BYTE_SERIALIZER);
        if (serializerTypeID >= 0) {
            metaData.d(serializerTypeID);
            BYTE_SERIALIZER.a(metaData, (byte) 0x20);
        }

        // small, no gravity, no base-plate marker, etc.
        metaData.writeByte(15); // armor stand status index (BYTE_SERIALIZER)
        serializerTypeID = DataWatcherRegistry.b(BYTE_SERIALIZER);
        if (serializerTypeID >= 0) {
            metaData.d(serializerTypeID);
            BYTE_SERIALIZER.a(metaData, (glassHead ? (byte) (0x02 | 0x08 | 0x10) : (byte) (0x01 | 0x02 | 0x08 | 0x10)));
        }

        if (!name.isEmpty()) {
            name = name.substring(0, Math.min(name.length(), 5000));

            // set custom name
            metaData.writeByte(2); // custom name index (OPTIONAL_CHAT_COMPONENT_SERIALIZER)
            serializerTypeID = DataWatcherRegistry.b(OPTIONAL_CHAT_COMPONENT_SERIALIZER);
            if (serializerTypeID >= 0) {
                metaData.d(serializerTypeID);
                OPTIONAL_CHAT_COMPONENT_SERIALIZER.a(metaData, Optional.of(CraftChatMessage.fromString(DisplayShops
                        .getPluginInstance().getManager().color(name), false, true)[0]));
            }

            // set name visibility
            metaData.writeByte(3); // custom name visibility index (BOOLEAN_SERIALIZER)
            serializerTypeID = DataWatcherRegistry.b(BOOLEAN_SERIALIZER);
            if (serializerTypeID >= 0) {
                metaData.d(serializerTypeID); // key serializer type id
                BOOLEAN_SERIALIZER.a(metaData, true);
            }
        }

        metaData.d(0xFF);

        PacketPlayOutEntityMetadata md = new PacketPlayOutEntityMetadata(metaData);
        sendPacket(playerConnection, md);
    }

    public void hide(@NotNull Player player) {
        if (getEntityIds() != null && !getEntityIds().isEmpty()) {
            final PlayerConnection playerConnection = getPlayerConnection(player);
            for (int entityId : getEntityIds()) {
                final PacketPlayOutEntityDestroy standPacket = new PacketPlayOutEntityDestroy(entityId);
                sendPacket(playerConnection, standPacket);
            }
        }
    }

    public void sendPacket(@NotNull PlayerConnection playerConnection, @NotNull Packet<?> packet) {
        playerConnection.a(packet);
    }

    public Collection<Integer> getEntityIds() {
        return entityIds;
    }

}