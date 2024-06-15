/*
 * Copyright (c) 2021 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds.nms.v1_20_R3;

import com.mojang.datafixers.util.Pair;
import io.netty.buffer.Unpooled;
import net.md_5.bungee.api.ChatColor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R3.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_20_R3.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.DisplayShops;
import xzot1k.plugins.ds.api.handlers.DisplayPacket;
import xzot1k.plugins.ds.api.objects.Appearance;
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

        final ServerGamePacketListenerImpl playerConnection = getPlayerConnection(player);
        if (playerConnection == null) return;

        Appearance appearance = Appearance.getAppearance(shop.getAppearanceId());
        if (appearance == null) return;

        final double[] offsets = appearance.getOffset();
        final double offsetX = offsets[0], offsetY = offsets[1], offsetZ = offsets[2];

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

                    FriendlyByteBuf pds = buildSerializer(id, true, (shop.getBaseLocation().getX() + offsetX),
                            ((shop.getBaseLocation().getY() + 1.325) + offsetY), (shop.getBaseLocation().getZ() + offsetZ));

                    final ClientboundAddEntityPacket itemPacket = new ClientboundAddEntityPacket(pds);
                    sendPacket(playerConnection, itemPacket);

                    FriendlyByteBuf metaData = new FriendlyByteBuf(Unpooled.buffer());
                    metaData.writeVarInt(id);

                    writeEntry(metaData, 8, itemStack); // add itemstack data

                    metaData.writeVarInt(0xFF);

                    ClientboundSetEntityDataPacket md = new ClientboundSetEntityDataPacket(metaData);
                    sendPacket(playerConnection, md);
                    //</editor-fold>

                    //<editor-fold desc="Vehicle Mount Packets">
                    final int vehicleId = idGenerator.get();
                    getEntityIds().add(vehicleId);
                    FriendlyByteBuf vehicleData = buildSerializer(vehicleId, false, (shop.getBaseLocation().getX() + offsetX),
                            ((shop.getBaseLocation().getY() + 1.325) + offsetY), (shop.getBaseLocation().getZ() + offsetZ));
                    final ClientboundAddEntityPacket vehiclePacket = new ClientboundAddEntityPacket(vehicleData);
                    sendPacket(playerConnection, vehiclePacket);

                    FriendlyByteBuf vehiclePDS = new FriendlyByteBuf(Unpooled.buffer());
                    vehiclePDS.writeVarInt(vehicleId);

                    writeEntry(vehiclePDS, 0, (byte) 0x20); // invisibility
                    writeEntry(vehiclePDS, 15, (byte) (0x01 | 0x02 | 0x08 | 0x10));  // small, no gravity, no base-plate marker, etc.

                    vehiclePDS.writeVarInt(0xFF);

                    ClientboundSetEntityDataPacket vehicleMD = new ClientboundSetEntityDataPacket(vehiclePDS);
                    sendPacket(playerConnection, vehicleMD);

                    FriendlyByteBuf mountData = new FriendlyByteBuf(Unpooled.buffer());
                    mountData.writeVarInt(vehicleId);
                    mountData.writeVarInt(1);
                    mountData.writeVarInt(id);

                    ClientboundSetPassengersPacket mountPacket = new ClientboundSetPassengersPacket(mountData);
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

        final String colorCode = INSTANCE.getConfig().getString("default-description-color");
        final boolean hidePriceLine = INSTANCE.getConfig().getBoolean("price-disabled-hide");
        double x = (shop.getBaseLocation().getX() + offsetX), y = (shop.getBaseLocation().getY() + (1.9 + offsetY)), z = (shop.getBaseLocation().getZ() + offsetZ);
        for (int i = hologramFormat.size(); --i >= 0; ) {
            String line = hologramFormat.get(i);

            if ((hidePriceLine && ((line.contains("buy-price") && shop.getBuyPrice(true) < 0)
                    || (line.contains("sell-price") && shop.getSellPrice(true) < 0)))
                    || ((line.contains("{description}") && (shop.getDescription() == null || shop.getDescription().equalsIgnoreCase("")))))
                continue;

            if (line.contains("{description}") && !(shop.getDescription() == null || shop.getDescription().equalsIgnoreCase(""))) {
                final String[] otherContents = line.split("\\{description}");
                final String prefix = (otherContents.length >= 1 ? otherContents[0] : ""),
                        suffix = (otherContents.length >= 2 ? otherContents[1] : "");

                List<String> descriptionLines = INSTANCE.getManager().wrapString(shop.getDescription());
                Collections.reverse(descriptionLines);
                for (int j = -1; ++j < descriptionLines.size(); ) {
                    String descriptionLine = INSTANCE.getManager().color(descriptionLines.get(j));
                    descriptionLine = (descriptionLine.contains(ChatColor.COLOR_CHAR + "") ? descriptionLine : (INSTANCE.getManager().color(colorCode + descriptionLine)));
                    createStand(playerConnection, x, y, z, (prefix + descriptionLine + suffix), false);
                    y += 0.3;
                }
                continue;
            }

            createStand(playerConnection, x, y, z, INSTANCE.getManager().applyShopBasedPlaceholders(line, shop), false);
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

    private ServerGamePacketListenerImpl getPlayerConnection(@NotNull Player player) {
        return ((CraftPlayer) player).getHandle().connection;
    }

    private FriendlyByteBuf buildSerializer(int id, boolean isItem, double x, double y, double z) {
        FriendlyByteBuf pds = new FriendlyByteBuf(Unpooled.buffer());

        pds.writeVarInt(id);
        pds.writeUUID(UUID.randomUUID());
        pds.writeVarInt(isItem ? 55 : 2); // (item is 54 , armor stand is 2, and slime is 83)

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

    private void createStand(@NotNull ServerGamePacketListenerImpl playerConnection, double x, double y, double z,
                             @NotNull String name, boolean glassHead) {
        final int id = idGenerator.get();
        getEntityIds().add(id);

        final FriendlyByteBuf pds = buildSerializer(id, false, x, y, z);
        final ClientboundAddEntityPacket spawnPacket = new ClientboundAddEntityPacket(pds);
        sendPacket(playerConnection, spawnPacket);

        if (glassHead) {
            itemStack = CraftItemStack.asNMSCopy(new ItemStack(Material.GLASS));
            if (itemStack != null) {
                List<Pair<EquipmentSlot, net.minecraft.world.item.ItemStack>> list = new ArrayList<>();
                list.add(new Pair<>(EquipmentSlot.HEAD /*HEAD or HELMET*/, itemStack));
                ClientboundSetEquipmentPacket packet = new ClientboundSetEquipmentPacket(id, list);
                sendPacket(playerConnection, packet);
            }
        }

        FriendlyByteBuf metaData = new FriendlyByteBuf(Unpooled.buffer());
        metaData.writeVarInt(id);

        writeEntry(metaData, 0, (byte) 0x20); // invisibility
        writeEntry(metaData, 15, (glassHead ? (byte) (0x02 | 0x08 | 0x10) : (byte) (0x01 | 0x02 | 0x08 | 0x10)));  // small, no gravity, no base-plate marker, etc.

        if (!name.isEmpty()) {
            name = name.substring(0, Math.min(name.length(), 5000)); // set name limit

            // set custom name
            writeEntry(metaData, 2, Optional.of(CraftChatMessage.fromString(DisplayShops.getPluginInstance().getManager().color(name), false, true)[0])); // set name
            writeEntry(metaData, 3, true); // set name visibility
        }

        metaData.writeByte(0xFF);

        ClientboundSetEntityDataPacket md = new ClientboundSetEntityDataPacket(metaData);
        sendPacket(playerConnection, md);
    }

    @SuppressWarnings("unchecked")
    private void writeEntry(@NotNull FriendlyByteBuf serializer, int id, Object value) { // data watcher entry
        serializer.writeByte(id);

        final EntityDataSerializer<net.minecraft.world.item.ItemStack> ITEM_STACK_SERIALIZER = EntityDataSerializers.ITEM_STACK;
        final EntityDataSerializer<Byte> BYTE_SERIALIZER = EntityDataSerializers.BYTE;
        final EntityDataSerializer<Boolean> BOOLEAN_SERIALIZER = EntityDataSerializers.BOOLEAN;
        final EntityDataSerializer<Optional<Component>> OPTIONAL_CHAT_COMPONENT_SERIALIZER = EntityDataSerializers.OPTIONAL_COMPONENT;

        EntityDataSerializer<?> dataWatcherRegistry = (id == 2 ? OPTIONAL_CHAT_COMPONENT_SERIALIZER : id == 3 ? BOOLEAN_SERIALIZER
                : id == 8 ? ITEM_STACK_SERIALIZER : BYTE_SERIALIZER);
        int serializerTypeID = EntityDataSerializers.getSerializedId(dataWatcherRegistry); // BYTE_SERIALIZER is 0 or 15

        if (serializerTypeID >= 0) {
            serializer.writeVarInt(serializerTypeID);

            if (id == 2) OPTIONAL_CHAT_COMPONENT_SERIALIZER.write(serializer, (Optional<Component>) value);
            else if (id == 3) BOOLEAN_SERIALIZER.write(serializer, (Boolean) value);
            else if (id == 8) ITEM_STACK_SERIALIZER.write(serializer, (net.minecraft.world.item.ItemStack) value);
            else BYTE_SERIALIZER.write(serializer, (Byte) value);
        }
    }

    public void hide(@NotNull Player player) {
        if (getEntityIds() != null && !getEntityIds().isEmpty()) {
            final ServerGamePacketListenerImpl playerConnection = getPlayerConnection(player);
            for (int entityId : getEntityIds()) {
                final ClientboundRemoveEntitiesPacket standPacket = new ClientboundRemoveEntitiesPacket(entityId);
                sendPacket(playerConnection, standPacket);
            }
        }
    }

    public void sendPacket(@NotNull ServerGamePacketListenerImpl playerConnection, @NotNull Packet<?> packet) {playerConnection.send(packet);}

    public Collection<Integer> getEntityIds() {return entityIds;}
}