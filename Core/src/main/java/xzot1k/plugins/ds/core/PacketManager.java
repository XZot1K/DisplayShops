package xzot1k.plugins.ds.core;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.DisplayShops;
import xzot1k.plugins.ds.api.handlers.ActionBarHandler;
import xzot1k.plugins.ds.api.handlers.ParticleHandler;
import xzot1k.plugins.ds.api.handlers.SerializeUtil;

public class PacketManager implements xzot1k.plugins.ds.api.PacketManager {

    private final DisplayShops INSTANCE;

    private ParticleHandler particleHandler;
    private ActionBarHandler actionBarHandler;
    private SerializeUtil serializeUtil;

    public PacketManager(@NotNull DisplayShops instance) {
        this.INSTANCE = instance;

        setup();
    }

    private void setup() {
        // TODO add reflection backup

        if (INSTANCE.getServerVersion() == 1_20.1) {
            this.particleHandler = new xzot1k.plugins.ds.core.packets.v1_20_R1.PPacket();
            this.actionBarHandler = new xzot1k.plugins.ds.core.packets.v1_20_R1.ABPacket();
            this.serializeUtil = new xzot1k.plugins.ds.core.packets.v1_20_R1.SUtil(INSTANCE);
        } else if (INSTANCE.getServerVersion() == 1_19.3) {
            this.particleHandler = new xzot1k.plugins.ds.core.packets.v1_19_R3.PPacket();
            this.actionBarHandler = new xzot1k.plugins.ds.core.packets.v1_19_R3.ABPacket();
            this.serializeUtil = new xzot1k.plugins.ds.core.packets.v1_19_R3.SUtil(INSTANCE);
        } else if (INSTANCE.getServerVersion() == 1_19.2) {
            this.particleHandler = new xzot1k.plugins.ds.core.packets.v1_19_R2.PPacket();
            this.actionBarHandler = new xzot1k.plugins.ds.core.packets.v1_19_R2.ABPacket();
            this.serializeUtil = new xzot1k.plugins.ds.core.packets.v1_19_R2.SUtil(INSTANCE);
        } else if (INSTANCE.getServerVersion() == 1_19.1) {
            this.particleHandler = new xzot1k.plugins.ds.core.packets.v1_19_R1.PPacket();
            this.actionBarHandler = new xzot1k.plugins.ds.core.packets.v1_19_R1.ABPacket();
            this.serializeUtil = new xzot1k.plugins.ds.core.packets.v1_19_R1.SUtil(INSTANCE);
        } else if (INSTANCE.getServerVersion() == 1_18.2) {
            this.particleHandler = new xzot1k.plugins.ds.core.packets.v1_18_R2.PPacket();
            this.actionBarHandler = new xzot1k.plugins.ds.core.packets.v1_18_R2.ABPacket();
            this.serializeUtil = new xzot1k.plugins.ds.core.packets.v1_18_R2.SUtil(INSTANCE);
        } else if (INSTANCE.getServerVersion() == 1_18.1) {
            this.particleHandler = new xzot1k.plugins.ds.core.packets.v1_18_R1.PPacket();
            this.actionBarHandler = new xzot1k.plugins.ds.core.packets.v1_18_R1.ABPacket();
            this.serializeUtil = new xzot1k.plugins.ds.core.packets.v1_18_R1.SUtil(INSTANCE);
        } else if (INSTANCE.getServerVersion() == 1_17.1) {
            this.particleHandler = new xzot1k.plugins.ds.core.packets.v1_17_R1.PPacket();
            this.actionBarHandler = new xzot1k.plugins.ds.core.packets.v1_17_R1.ABPacket();
            this.serializeUtil = new xzot1k.plugins.ds.core.packets.v1_17_R1.SUtil(INSTANCE);
        } else if (INSTANCE.getServerVersion() == 1_16.3) {
            this.particleHandler = new xzot1k.plugins.ds.core.packets.v1_16_R3.PPacket();
            this.actionBarHandler = new xzot1k.plugins.ds.core.packets.v1_16_R3.ABPacket();
            this.serializeUtil = new xzot1k.plugins.ds.core.packets.v1_16_R3.SUtil(INSTANCE);
        } else if (INSTANCE.getServerVersion() == 1_16.2) {
            this.particleHandler = new xzot1k.plugins.ds.core.packets.v1_16_R2.PPacket();
            this.actionBarHandler = new xzot1k.plugins.ds.core.packets.v1_16_R2.ABPacket();
            this.serializeUtil = new xzot1k.plugins.ds.core.packets.v1_16_R2.SUtil(INSTANCE);
        } else if (INSTANCE.getServerVersion() == 1_16.1) {
            this.particleHandler = new xzot1k.plugins.ds.core.packets.v1_16_R1.PPacket();
            this.actionBarHandler = new xzot1k.plugins.ds.core.packets.v1_16_R1.ABPacket();
            this.serializeUtil = new xzot1k.plugins.ds.core.packets.v1_16_R1.SUtil(INSTANCE);
        } else if (INSTANCE.getServerVersion() == 1_15.1) {
            this.particleHandler = new xzot1k.plugins.ds.core.packets.v1_15_R1.PPacket();
            this.actionBarHandler = new xzot1k.plugins.ds.core.packets.v1_15_R1.ABPacket();
            this.serializeUtil = new xzot1k.plugins.ds.core.packets.v1_15_R1.SUtil(INSTANCE);
        } else if (INSTANCE.getServerVersion() == 1_14.1) {
            this.particleHandler = new xzot1k.plugins.ds.core.packets.v1_14_R1.PPacket();
            this.actionBarHandler = new xzot1k.plugins.ds.core.packets.v1_14_R1.ABPacket();
            this.serializeUtil = new xzot1k.plugins.ds.core.packets.v1_14_R1.SUtil(INSTANCE);
        } else if (INSTANCE.getServerVersion() == 1_13.2) {
            this.particleHandler = new xzot1k.plugins.ds.core.packets.v1_13_R2.PPacket();
            this.actionBarHandler = new xzot1k.plugins.ds.core.packets.v1_13_R2.ABPacket();
            this.serializeUtil = new xzot1k.plugins.ds.core.packets.v1_13_R2.SUtil(INSTANCE);
        } else if (INSTANCE.getServerVersion() == 1_13.1) {
            this.particleHandler = new xzot1k.plugins.ds.core.packets.v1_13_R1.PPacket();
            this.actionBarHandler = new xzot1k.plugins.ds.core.packets.v1_13_R1.ABPacket();
            this.serializeUtil = new xzot1k.plugins.ds.core.packets.v1_13_R1.SUtil(INSTANCE);
        } else if (INSTANCE.getServerVersion() == 1_12.1) {
            this.particleHandler = new xzot1k.plugins.ds.core.packets.v1_12_R1.PPacket();
            this.actionBarHandler = new xzot1k.plugins.ds.core.packets.v1_12_R1.ABPacket();
            this.serializeUtil = new xzot1k.plugins.ds.core.packets.v1_12_R1.SUtil(INSTANCE);
        } else if (INSTANCE.getServerVersion() == 1_11.1) {
            this.particleHandler = new xzot1k.plugins.ds.core.packets.v1_11_R1.PPacket();
            this.actionBarHandler = new xzot1k.plugins.ds.core.packets.v1_11_R1.ABPacket();
            this.serializeUtil = new xzot1k.plugins.ds.core.packets.v1_11_R1.SUtil(INSTANCE);
        } else if (INSTANCE.getServerVersion() == 1_10.1) {
            this.particleHandler = new xzot1k.plugins.ds.core.packets.v1_10_R1.PPacket();
            this.actionBarHandler = new xzot1k.plugins.ds.core.packets.v1_10_R1.ABPacket();
            this.serializeUtil = new xzot1k.plugins.ds.core.packets.v1_10_R1.SUtil(INSTANCE);
        } else if (INSTANCE.getServerVersion() == 1_9.2) {
            this.particleHandler = new xzot1k.plugins.ds.core.packets.v1_9_R2.PPacket();
            this.actionBarHandler = new xzot1k.plugins.ds.core.packets.v1_9_R2.ABPacket();
            this.serializeUtil = new xzot1k.plugins.ds.core.packets.v1_9_R2.SUtil(INSTANCE);
        } else if (INSTANCE.getServerVersion() == 1_9.1) {
            this.particleHandler = new xzot1k.plugins.ds.core.packets.v1_9_R1.PPacket();
            this.actionBarHandler = new xzot1k.plugins.ds.core.packets.v1_9_R1.ABPacket();
            this.serializeUtil = new xzot1k.plugins.ds.core.packets.v1_9_R1.SUtil(INSTANCE);
        } else if (INSTANCE.getServerVersion() == 1_8.3) {
            this.particleHandler = new xzot1k.plugins.ds.core.packets.v1_8_R3.PPacket();
            this.actionBarHandler = new xzot1k.plugins.ds.core.packets.v1_8_R3.ABPacket();
            this.serializeUtil = new xzot1k.plugins.ds.core.packets.v1_8_R3.SUtil(INSTANCE);
        }
    }

    public ParticleHandler getParticleHandler() {
        return particleHandler;
    }

    public ActionBarHandler getActionBarHandler() {
        return actionBarHandler;
    }

    public SerializeUtil getSerializeUtil() {
        return serializeUtil;
    }

    @Override
    public String toString(@NotNull ItemStack itemStack) {
        return getSerializeUtil().toString(itemStack);
    }

    @Override
    public ItemStack toItem(@NotNull String itemString) {
        return getSerializeUtil().toItem(itemString);
    }

    @Override
    public String toJSON(@NotNull ItemStack itemStack) {
        return getSerializeUtil().toJSON(itemStack);
    }

    @Override
    public String getNBT(@NotNull ItemStack itemStack, @NotNull String nbtTag) {
        return getSerializeUtil().getNBT(itemStack, nbtTag);
    }

    @Override
    public ItemStack updateNBT(@NotNull ItemStack itemStack, @NotNull String nbtTag, @NotNull String value) {
        return getSerializeUtil().updateNBT(itemStack, nbtTag, value);
    }

    @Override
    public void displayParticle(@NotNull Player player, @NotNull String particleName, @NotNull Location location,
                                double offsetX, double offsetY, double offsetZ, int speed, int amount) {
        getParticleHandler().displayParticle(player, particleName, location, offsetX, offsetY, offsetZ, speed, amount);
    }

    @Override
    public void sendActionBar(@NotNull Player player, @NotNull String message) {
        getActionBarHandler().sendActionBar(player, message);
    }

}
