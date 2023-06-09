/*
 * Copyright (c) 2021 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds;

import me.arcaniax.hdb.api.HeadDatabaseAPI;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import xzot1k.plugins.ds.api.DManager;
import xzot1k.plugins.ds.api.PacketManager;
import xzot1k.plugins.ds.api.handlers.DisplayPacket;
import xzot1k.plugins.ds.api.objects.DataPack;
import xzot1k.plugins.ds.api.objects.Shop;
import xzot1k.plugins.ds.core.Commands;
import xzot1k.plugins.ds.core.Listeners;
import xzot1k.plugins.ds.core.TabCompleter;
import xzot1k.plugins.ds.core.hooks.PapiHelper;
import xzot1k.plugins.ds.core.hooks.PlotSquaredListener;
import xzot1k.plugins.ds.core.hooks.SkyBlockListener;
import xzot1k.plugins.ds.core.hooks.WorldGuardHandler;
import xzot1k.plugins.ds.core.tasks.CleanupTask;
import xzot1k.plugins.ds.core.tasks.ManagementTask;
import xzot1k.plugins.ds.core.tasks.ShopVisitItemTask;
import xzot1k.plugins.ds.core.tasks.VisualTask;

import java.io.*;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.*;
import java.util.logging.Level;

public class DisplayShops extends JavaPlugin implements DisplayShopsAPI {

    // Main handlers
    private static DisplayShops pluginInstance;
    private DManager manager;
    private SimpleDateFormat dateFormat;
    private PacketManager packetManager;

    // Version handlers
    private double serverVersion;
    private boolean paperSpigot, prismaInstalled, townyInstalled;

    // Virtual data handlers
    private HashMap<UUID, UUID> shopMemory;
    private HashMap<UUID, HashMap<UUID, DisplayPacket>> displayPacketMap;
    private List<UUID> teleportingPlayers;

    // hook handlers
    private Listeners listeners;
    private Economy vaultEconomy;
    private HeadDatabaseAPI headDatabaseAPI;
    private PapiHelper papiHelper;
    private boolean isItemAdderInstalled;

    // Task handlers
    private VisualTask inSightTask;
    private ManagementTask managementTask;
    private CleanupTask cleanupTask;
    private ShopVisitItemTask shopVisitItemTask;

    // Data handlers
    private Connection databaseConnection;
    private FileConfiguration langConfig, menusConfig;
    private File langFile, menusFile, loggingFile;

    /**
     * @return Returns the plugin's instance.
     */
    public static DisplayShops getPluginInstance() {
        return pluginInstance;
    }

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();
        DisplayShops.pluginInstance = this;
        saveDefaultConfigs();

        setServerVersion(Double.parseDouble(getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3]
                .replace("_R", ".").replaceAll("[rvV_]*", "")));
        updateConfigs();

        setShopMemory(new HashMap<>());
        setDisplayPacketMap(new HashMap<>());
        setTeleportingPlayers(new ArrayList<>());
        this.dateFormat = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");

        this.packetManager = new xzot1k.plugins.ds.core.PacketManager(this);

        setPaperSpigot(false);
        Method[] methods = World.class.getMethods();
        if (methods.length > 0)
            for (int i = -1; ++i < methods.length; ) {
                final Method method = methods[i];
                if (method == null || !method.getName().equalsIgnoreCase("getChunkAtAsync")) continue;
                setPaperSpigot(true);
            }

        setPrismaInstalled(getServer().getPluginManager().getPlugin("Prisma") != null);

        setupVaultEconomy();
        if (getConfig().getBoolean("use-vault") && getVaultEconomy() == null) {
            log(Level.WARNING, "Vault is either missing or has no economy counterpart. Now using integrated economy solution.");
        }

        new WorldGuardHandler(this);
        registerWorldEditEvents();

        Plugin hdb = getServer().getPluginManager().getPlugin("HeadDatabase");
        if (hdb != null) setHeadDatabaseAPI(new HeadDatabaseAPI());

        Plugin papi = getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (papi != null) {
            setPapiHelper(new PapiHelper(this));
            getPapiHelper().register();
        }

        setManager(new DManager(this));

        long databaseStartTime = System.currentTimeMillis();
        final boolean fixedTables = setupDatabase();
        if (getDatabaseConnection() != null)
            log(Level.INFO, "Communication to the database was successful. (Took " + (System.currentTimeMillis() - databaseStartTime) + "ms)");
        else {
            log(Level.WARNING, "Communication to the database failed. (Took " + (System.currentTimeMillis() - databaseStartTime) + "ms)");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.townyInstalled = (getServer().getPluginManager().getPlugin("Towny") != null);
        this.isItemAdderInstalled = (getServer().getPluginManager().getPlugin("ItemsAdder") != null);
        new SkyBlockListener(this);

        getServer().getPluginManager().registerEvents(listeners = new Listeners(this), this);

        Plugin ps = getServer().getPluginManager().getPlugin("PlotSquared");
        if (ps != null && ps.getDescription().getVersion().startsWith("5")) try {
            new PlotSquaredListener(this);
        } catch (Exception e) {
            e.printStackTrace();
            log(Level.WARNING, "PlotSquared v5 was unable to be hooked into due mismatching classes or "
                    + "incompatibilities. Make sure you are on the correct version of PlotSquared.");
        }

        PluginCommand command = getCommand("displayshops");
        if (command != null) {
            command.setExecutor(new Commands(this));

            TabCompleter tabCompleter = new TabCompleter(this);
            command.setTabCompleter(tabCompleter);
        }


        if (getConfig().getBoolean("shop-creation-item.craftable")) {
            try {
                ShapedRecipe shapedRecipe;

                if (Math.floor(getServerVersion()) > 1_10) {
                    org.bukkit.NamespacedKey namespacedKey = new org.bukkit.NamespacedKey(this, "shop");
                    shapedRecipe = new ShapedRecipe(namespacedKey, getManager().buildShopCreationItem(null, 1));
                    if (Math.floor(getServerVersion()) >= 1_16 && getServer().getRecipe(namespacedKey) != null) return;
                } else shapedRecipe = new ShapedRecipe(getManager().buildShopCreationItem(null, 1));
                shapedRecipe.shape("abc", "def", "ghi");

                String lineOne = getConfig().getString("shop-creation-item.recipe.line-one"),
                        lineTwo = getConfig().getString("shop-creation-item.recipe.line-two"),
                        lineThree = getConfig().getString("shop-creation-item.recipe.line-three");
                if (lineOne != null && lineOne.contains(",")) {
                    String[] lineSplit = lineOne.split(",");
                    for (int i = -1; ++i < 3; ) {
                        String materialLine = lineSplit[i];
                        char recipeChar = ((i == 0) ? 'a' : (i == 1) ? 'b' : 'c');

                        if (materialLine.contains(":")) {
                            String[] materialSplit = materialLine.split(":");
                            Material material = Material.getMaterial(materialSplit[0].toUpperCase().replace(" ", "_").replace("-", "_"));
                            int durability = Integer.parseInt(materialSplit[1]);
                            if (material != null) shapedRecipe.setIngredient(recipeChar, material, durability);
                        } else {
                            Material material = Material.getMaterial(materialLine.toUpperCase().replace(" ", "_").replace("-", "_"));
                            if (material != null) shapedRecipe.setIngredient(recipeChar, material);
                        }
                    }
                }

                if (lineTwo != null && lineTwo.contains(",")) {
                    String[] lineSplit = lineTwo.split(",");
                    for (int i = -1; ++i < 3; ) {
                        String materialLine = lineSplit[i];
                        char recipeChar = ((i == 0) ? 'd' : (i == 1) ? 'e' : 'f');

                        if (materialLine.contains(":")) {
                            String[] materialSplit = materialLine.split(":");
                            Material material = Material.getMaterial(materialSplit[0].toUpperCase().replace(" ", "_").replace("-", "_"));
                            int durability = Integer.parseInt(materialSplit[1]);
                            if (material != null) shapedRecipe.setIngredient(recipeChar, material, durability);
                        } else {
                            Material material = Material.getMaterial(materialLine.toUpperCase().replace(" ", "_").replace("-", "_"));
                            if (material != null) shapedRecipe.setIngredient(recipeChar, material);
                        }
                    }
                }

                if (lineThree != null && lineThree.contains(",")) {
                    String[] lineSplit = lineThree.split(",");
                    for (int i = -1; ++i < 3; ) {
                        String materialLine = lineSplit[i];
                        char recipeChar = ((i == 0) ? 'g' : (i == 1) ? 'h' : 'i');

                        if (materialLine.contains(":")) {
                            String[] materialSplit = materialLine.split(":");
                            Material material = Material.getMaterial(materialSplit[0].toUpperCase().replace(" ", "_").replace("-", "_"));
                            if (material != null) {
                                int durability = Integer.parseInt(materialSplit[1]);
                                shapedRecipe.setIngredient(recipeChar, material, durability);
                            }
                        } else {
                            Material material = Material.getMaterial(materialLine.toUpperCase().replace(" ", "_").replace("-", "_"));
                            if (material != null) shapedRecipe.setIngredient(recipeChar, material);
                        }
                    }
                }

                getServer().addRecipe(shapedRecipe);
            } catch (Exception e) {
                log(Level.WARNING, "Unable to create the custom recipe for the shop creation item. This is normally due to the version of Minecraft not supporting the new 'NamespacedKey' API values. "
                        + "To avoid this issue entirely -> DISABLE THE 'craftable' OPTION IN THE 'shop-creation-item' SECTION LOCATED IN THE 'config.yml' file.");
            }
        }

        if (!fixedTables) getManager().loadShops(false, false);
        getManager().loadMarketRegions(false);
        for (Player player : getServer().getOnlinePlayers()) getManager().loadDataPack(player);

        setupTasks();
        double version = Math.floor(getServerVersion());
        log(Level.INFO, "Fully loaded and enabled with " + (version / (version >= 100 ? 100 : 10)) + (version >= 100 ? "0" : "")
                + " packets (Took " + (System.currentTimeMillis() - startTime) + "ms).");

        if (getDescription().getVersion().toLowerCase().contains("build") || getDescription().getVersion().toLowerCase().contains("snapshot"))
            log(Level.WARNING, "You are currently running an 'EXPERIMENTAL' build. Please ensure to watch your "
                    + "data carefully, create backups, and use with caution.");
        else if (isOutdated())
            log(Level.INFO, "There seems to be a different version on the Spigot resource page '"
                    + getLatestVersion() + "'. You are currently running '" + getDescription().getVersion() + "'.");
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);

        if (getManager() != null) {
            int shopSaveCount = 0;

            try {
                final long shopCount = getManager().getShopMap().size(), shopCountPercentage = ((long) (shopCount * 0.15));
                long current = 0;

                for (Shop shop : getManager().getShopMap().values()) {
                    shop.save(false);
                    shopSaveCount++;
                    current++;

                    if ((shopCountPercentage <= 0 || (current % shopCountPercentage) == 0 || current == shopCount))
                        log(Level.INFO, "Saving shops " + current + "/" + shopCount + " (" + Math.min(100, (int) (((double) current / (double) shopCount) * 100)) + "%)...");
                }

                Statement statement = getDatabaseConnection().createStatement();
                getServer().getOnlinePlayers().forEach(player -> {
                    DataPack dataPack = getManager().getDataPack(player);
                    getManager().saveDataPack(statement, player.getUniqueId(), dataPack, false, false);
                    clearDisplayPackets(player);
                });
                statement.close();
            } catch (Exception e) {
                e.printStackTrace();
                log(Level.WARNING, "There was an issue saving the shops.");
            }

            getManager().saveMarketRegions();
            log(Level.INFO, "Successfully saved all data, including " + shopSaveCount + " shops!");
        }

        if (getDatabaseConnection() != null)
            try {
                getDatabaseConnection().close();
                log(Level.WARNING, "The database has been successfully closed!");
            } catch (SQLException e) {
                e.printStackTrace();
                log(Level.WARNING, "The database had an issue closing.");
            }


        if (Math.floor(getServerVersion()) >= 1.9)
            try {
                getServer().removeRecipe(new org.bukkit.NamespacedKey(this, "display-shop"));
            } catch (NoClassDefFoundError e) {
                log(Level.WARNING, "The recipe removal method could not be found in your version of Minecraft, Skipping...");
            }
    }

    // returns whether it had to fix the tables.
    private synchronized boolean setupDatabase() {
        boolean fixedTables = false;
        if (getDatabaseConnection() != null)
            try {
                getDatabaseConnection().close();
            } catch (SQLException e) {
                e.printStackTrace();
            }

        try {
            final String host = getConfig().getString("mysql.host");
            if (host == null || host.isEmpty()) {
                Class.forName("org.sqlite.JDBC");
                setDatabaseConnection(DriverManager.getConnection("jdbc:sqlite:" + getDataFolder() + "/data.db"));
                Statement statement = getDatabaseConnection().createStatement();

                try {
                    statement.executeUpdate("BACKUP TO '" + new File(getDataFolder(), "/auto-backup.db").getPath()
                            .replace("'", "").replace("\"", "") + "';");
                } catch (SQLException ignored) {
                }

                statement.executeUpdate("PRAGMA integrity_check;");
                String shopParameters = "(id TEXT PRIMARY KEY NOT NULL, location TEXT NOT NULL, owner TEXT, assistants TEXT, buy_price REAL,"
                        + " sell_price REAL, stock INTEGER, shop_item TEXT, trade_item TEXT, buy_limit INTEGER, sell_limit INTEGER,"
                        + " shop_item_amount INTEGER, buy_counter INTEGER, sell_counter INTEGER, balance REAL, command_only_mode NUMERIC,"
                        + " commands TEXT, change_time_stamp TEXT, description TEXT, base_material TEXT, extra_data TEXT)",
                        markRegionParameters = "(id TEXT PRIMARY KEY NOT NULL, point_one TEXT, point_two TEXT, renter TEXT, rent_time_stamp TEXT,"
                                + " extended_duration INTEGER, extra_data TEXT)",
                        playerDataParameters = "(uuid TEXT PRIMARY KEY NOT NULL, bbm_unlocks TEXT, cooldowns TEXT, transaction_limits TEXT, notify TEXT)",
                        recoveryParameters = "(uuid VARCHAR(100) PRIMARY KEY NOT NULL, currency REAL, item_amount INTEGER, item TEXT)";

                statement.execute("CREATE TABLE IF NOT EXISTS shops " + shopParameters + ";");
                if (!tableExists("shops")) {
                    getServer().getLogger().warning("There was an issue creating the \"shops\" table. This could be related to user permissions via SQL.");
                    return false;
                }

                statement.execute("CREATE TABLE IF NOT EXISTS market_regions " + markRegionParameters + ";");
                if (!tableExists("market_regions")) {
                    getServer().getLogger().warning("There was an issue creating the \"market_regions\" table. This could be related to user permissions via SQL.");
                    return false;
                }

                statement.execute("CREATE TABLE IF NOT EXISTS player_data " + playerDataParameters + ";");
                if (!tableExists("player_data")) {
                    getServer().getLogger().warning("There was an issue creating the \"player_data\" table. This could be related to user permissions via SQL.");
                    return false;
                }

                statement.execute("CREATE TABLE IF NOT EXISTS recovery " + recoveryParameters + ";");
                if (!tableExists("recovery")) {
                    getServer().getLogger().warning("There was an issue creating the \"recovery\" table. This could be related to user permissions via SQL.");
                    return false;
                }

                ResultSet rs = statement.executeQuery("SELECT * FROM player_data;");
                boolean hasTL = hasColumn(rs, "transaction_limits"), hasNotify = hasColumn(rs, "notify");
                if (!hasTL && !hasNotify) {
                    statement.execute("CREATE TABLE IF NOT EXISTS temp_player_data " + playerDataParameters + ";");
                    statement.execute("INSERT INTO temp_player_data (uuid, bbm_unlocks, cooldowns) SELECT uuid, bbm_unlocks, cooldowns FROM player_data;");
                    statement.execute("DROP TABLE IF EXISTS player_data;");
                    statement.execute("ALTER TABLE temp_player_data RENAME TO player_data;");
                } else if (hasTL && !hasNotify) {
                    statement.execute("CREATE TABLE IF NOT EXISTS temp_player_data " + playerDataParameters + ";");
                    statement.execute("INSERT INTO temp_player_data (uuid, bbm_unlocks, cooldowns, transaction_limits) SELECT uuid, bbm_unlocks, "
                            + "cooldowns, transaction_limits FROM player_data;");
                    statement.execute("DROP TABLE IF EXISTS player_data;");
                    statement.execute("ALTER TABLE temp_player_data RENAME TO player_data;");
                }

                rs.close();

                ResultSet resultSet = statement.executeQuery("SELECT * FROM shops;");
                if (!hasColumn(resultSet, "base_material") || hasColumn(resultSet, "base_location")) {
                    resultSet.close();
                    try {
                        statement.executeUpdate("BACKUP TO '" + new File(getDataFolder(), "/table-fix-backup.db").getPath()
                                .replace("'", "").replace("\"", "") + "';");
                    } catch (SQLException e) {
                        log(Level.WARNING, "Unable to backup the 'table-fix-backup.db' file.");
                    }

                    for (String tableName : new String[]{"market_regions", "player_data"}) {
                        statement.execute("CREATE TABLE IF NOT EXISTS temp_" + tableName + " "
                                + (tableName.equals("market_regions") ? markRegionParameters : playerDataParameters) + ";");
                        statement.execute("INSERT INTO temp_" + tableName + " SELECT * FROM " + tableName + ";");
                        statement.execute("DROP TABLE IF EXISTS " + tableName + ";");
                        statement.execute("ALTER TABLE temp_" + tableName + " RENAME TO " + tableName + ";");
                    }

                    getManager().loadShops(false, false);
                    statement.execute("DROP TABLE IF EXISTS shops;");

                    statement.execute("CREATE TABLE IF NOT EXISTS shops " + shopParameters + ";");
                    for (Shop shop : getManager().getShopMap().values()) shop.save(false);
                    fixedTables = true;
                }

                resultSet.close();
                statement.close();
            } else {
                try {
                    Class.forName("com.mysql.cj.jdbc.Driver");
                } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                    Class.forName("com.mysql.jdbc.Driver");
                }
                final boolean useSSL = getConfig().getBoolean("mysql.use-ssl");
                final String databaseName = getConfig().getString("mysql.database"), port = getConfig().getString("mysql.port"),
                        username = getConfig().getString("mysql.username"), password = getConfig().getString("mysql.password"),
                        syntax = "jdbc:mysql://" + host + ":" + port + "/" + databaseName + "?useSSL=" + (useSSL ? "true" : "false")
                                + "&autoReconnect=true&useUnicode=yes";

                setDatabaseConnection(DriverManager.getConnection(syntax, username, password));
                Statement statement = getDatabaseConnection().createStatement();

                final String shopParameters = "(id VARCHAR(100) PRIMARY KEY NOT NULL, location LONGTEXT NOT NULL, owner LONGTEXT, assistants LONGTEXT, buy_price DOUBLE,"
                        + " sell_price DOUBLE, stock INT, shop_item LONGTEXT, trade_item LONGTEXT, buy_limit INT, sell_limit INT,"
                        + " shop_item_amount INT, buy_counter INT, sell_counter INT, balance DOUBLE, command_only_mode BOOLEAN,"
                        + " commands LONGTEXT, change_time_stamp LONGTEXT, description LONGTEXT, base_material LONGTEXT, extra_data LONGTEXT)",
                        markRegionParameters = "(id VARCHAR(100) PRIMARY KEY NOT NULL, point_one LONGTEXT, point_two LONGTEXT, renter LONGTEXT, rent_time_stamp LONGTEXT,"
                                + " extended_duration INT, extra_data LONGTEXT)",
                        playerDataParameters = "(uuid VARCHAR(100) PRIMARY KEY NOT NULL, bbm_unlocks LONGTEXT, cooldowns LONGTEXT, transaction_limits LONGTEXT, notify LONGTEXT)",
                        recoveryParameters = "(uuid VARCHAR(100) PRIMARY KEY NOT NULL, currency DOUBLE, item_amount INT, item LONGTEXT)";

                statement.execute("CREATE TABLE IF NOT EXISTS shops " + shopParameters + ";");
                if (!tableExists("shops")) {
                    getServer().getLogger().warning("There was an issue creating the \"shops\" table. This could be related to user permissions via SQL.");
                    return false;
                }

                statement.execute("CREATE TABLE IF NOT EXISTS market_regions " + markRegionParameters + ";");
                if (!tableExists("market_regions")) {
                    getServer().getLogger().warning("There was an issue creating the \"market_regions\" table. This could be related to user permissions via SQL.");
                    return false;
                }

                statement.execute("CREATE TABLE IF NOT EXISTS player_data " + playerDataParameters + ";");
                if (!tableExists("player_data")) {
                    getServer().getLogger().warning("There was an issue creating the \"player_data\" table. This could be related to user permissions via SQL.");
                    return false;
                }

                statement.execute("CREATE TABLE IF NOT EXISTS recovery " + recoveryParameters + ";");
                if (!tableExists("recovery")) {
                    getServer().getLogger().warning("There was an issue creating the \"recovery\" table. This could be related to user permissions via SQL.");
                    return false;
                }

                ResultSet rs = statement.executeQuery("SELECT * FROM player_data;");
                boolean hasTL = hasColumn(rs, "transaction_limits"), hasNotify = hasColumn(rs, "notify");
                if (!hasTL && !hasNotify) {
                    statement.execute("CREATE TABLE IF NOT EXISTS temp_player_data " + playerDataParameters + ";");
                    statement.execute("INSERT INTO temp_player_data (uuid, bbm_unlocks, cooldowns) SELECT uuid, bbm_unlocks, cooldowns FROM player_data;");
                    statement.execute("DROP TABLE IF EXISTS player_data;");
                    statement.execute("ALTER TABLE temp_player_data RENAME TO player_data;");
                } else if (hasTL && !hasNotify) {
                    statement.execute("CREATE TABLE IF NOT EXISTS temp_player_data " + playerDataParameters + ";");
                    statement.execute("INSERT INTO temp_player_data (uuid, bbm_unlocks, cooldowns, transaction_limits) SELECT uuid, bbm_unlocks, "
                            + "cooldowns, transaction_limits FROM player_data;");
                    statement.execute("DROP TABLE IF EXISTS player_data;");
                    statement.execute("ALTER TABLE temp_player_data RENAME TO player_data;");
                }

                rs.close();

                ResultSet resultSet = statement.executeQuery("SELECT * FROM shops;");
                if (hasColumn(resultSet, "base_location")) {
                    for (String tableName : new String[]{"market_regions", "player_data"}) {
                        statement.execute("CREATE TABLE IF NOT EXISTS temp_" + tableName + " "
                                + (tableName.equals("market_regions") ? markRegionParameters : playerDataParameters) + ";");
                        statement.execute("INSERT INTO temp_" + tableName + " SELECT * FROM " + tableName + ";");
                        statement.execute("DROP TABLE IF EXISTS " + tableName + ";");
                        statement.execute("ALTER TABLE temp_" + tableName + " RENAME TO " + tableName + ";");
                    }

                    getManager().loadShops(false, false);
                    statement.execute("DROP TABLE IF EXISTS shops;");
                    statement.execute("CREATE TABLE IF NOT EXISTS shops " + shopParameters + ";");
                    for (Shop shop : getManager().getShopMap().values()) shop.save(false);
                    fixedTables = true;
                }

                resultSet.close();
                statement.close();
                exportMySQLDatabase();
            }
        } catch (ClassNotFoundException | SQLException | IOException e) {
            e.printStackTrace();
            log(Level.WARNING, e.getMessage());
        }

        return fixedTables;
    }

    public boolean hasColumn(ResultSet rs, String columnName) throws SQLException {
        ResultSetMetaData rsmd = rs.getMetaData();
        int columns = rsmd.getColumnCount();
        for (int x = 0; ++x <= columns; ) if (rsmd.getColumnName(x).equalsIgnoreCase(columnName)) return true;
        return false;
    }

    public boolean tableExists(@NotNull String tableName) {
        try {
            final DatabaseMetaData md = getDatabaseConnection().getMetaData();
            final ResultSet rs = md.getTables(null, null, tableName, null);
            final boolean exists = rs.next();
            rs.close();
            return exists;
        } catch (SQLException ex) {
            getServer().getLogger().warning(ex.getMessage());
        }

        return false;
    }

    public void exportMySQLDatabase() throws IOException {
        File dir = new File(getDataFolder(), "/mysql-backups");
        dir.mkdirs();

        File[] files = dir.listFiles();
        if (files == null || files.length < 18) {
            File saveFile = new File(getDataFolder(), "/mysql-backups/backup-" + dateFormat.format(new Date()) + ".sql");
            if (!saveFile.exists()) {
                try {

                    PreparedStatement statement = getDatabaseConnection().prepareStatement("BACKUP DATABASE shops TO DISK "
                            + "= '/mysql-backups/shops-" + dateFormat.format(new Date()) + ".sql';");
                    statement.executeUpdate();
                    statement.close();

                    statement = getDatabaseConnection().prepareStatement("BACKUP DATABASE market_regions TO DISK = "
                            + "'/mysql-backups/market-regions-" + dateFormat.format(new Date()) + ".sql';");
                    statement.executeUpdate();
                    statement.close();

                    statement = getDatabaseConnection().prepareStatement("BACKUP DATABASE player_data TO DISK = "
                            + "'/mysql-backups/player-data-" + dateFormat.format(new Date()) + ".sql';");
                    statement.executeUpdate();
                    statement.close();

                } catch (SQLException ignored) {
                }
            }
        }
    }

    // PlaceholderAPI helpers.
    public String papiText(Player player, String text) {
        if (getPapiHelper() == null) return text;
        return getPapiHelper().replace(player, text);
    }

    // general functions

    /*public boolean isBedrock(@NotNull UUID playerUniqueId) {
        try {
            return org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(playerUniqueId);
        } catch (NoClassDefFoundError ignored) {}
        return false;
    }*/

    /**
     * Gets the id associated to the item in the blocked-items.yml.
     *
     * @param itemStack The item to check the id for.
     * @return The id associated to the item in the blocked-items.yml (returns -1 if invalid).
     */
    public long getBlockedItemId(@NotNull ItemStack itemStack) {
        File file = new File(getPluginInstance().getDataFolder(), "blocked-items.yml");
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection cs = yaml.getConfigurationSection("");
        if (cs != null) for (String key : cs.getKeys(false)) {
            ItemStack foundItem = getPacketManager().toItem(Objects.requireNonNull(cs.getString(key)));
            if (foundItem != null && foundItem.isSimilar(itemStack)) return Long.parseLong(key);
        }

        return -1;
    }

    private void registerWorldEditEvents() {
        /*Plugin worldEditPlugin = getServer().getPluginManager().getPlugin("WorldEdit");
        if (worldEditPlugin != null) {
            final boolean fwaeFound = (getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") != null);
            com.sk89q.worldedit.WorldEdit.getInstance().getEventBus().register(new Object() {
                @com.sk89q.worldedit.util.eventbus.Subscribe
                public void onEditSessionEvent(com.sk89q.worldedit.event.extent.EditSessionEvent event) {
                    if (event.getStage() == com.sk89q.worldedit.EditSession.Stage.BEFORE_CHANGE) {
                        final String worldName = event.getWorld().getName();
                        getPluginInstance().getManager().getShopMap().values().forEach(shop -> {
                            if (shop.getBaseLocation() != null && worldName.equalsIgnoreCase(shop.getBaseLocation().getWorldName())
                                    && (event.getExtent().getMaximumPoint().getBlockX() >= shop.getBaseLocation().getX()
                                    && event.getExtent().getMinimumPoint().getBlockX() <= shop.getBaseLocation().getX())
                                    && (event.getExtent().getMaximumPoint().getBlockZ() >= shop.getBaseLocation().getZ()
                                    && event.getExtent().getMinimumPoint().getBlockZ() <= shop.getBaseLocation().getZ())
                                    && (event.getExtent().getMaximumPoint().getBlockY() >= shop.getBaseLocation().getY()
                                    && event.getExtent().getMinimumPoint().getBlockY() <= shop.getBaseLocation().getY())) {
                                shop.purge(null, false);
                                writeToLog("The shop '" + shop.getShopId() + "' was purged due to a world edit region from Max ("
                                        + event.getExtent().getMinimumPoint().getBlockX() + "," + event.getExtent().getMinimumPoint().getBlockY()
                                        + "," + event.getExtent().getMinimumPoint().getBlockZ() + ") to Min ("
                                        + event.getExtent().getMaximumPoint().getBlockX() + "," + event.getExtent().getMaximumPoint().getBlockY()
                                        + "," + event.getExtent().getMaximumPoint().getBlockZ() + ").");
                            }
                        });
                    }
                }
            });
        }*/
    }

    /**
     * Logs a message with a level to the console under the DisplayShops title.
     *
     * @param level   Level of the message.
     * @param message The message to log.
     */
    public void log(@NotNull Level level, @NotNull String message) {
        getServer().getLogger().log(level, "[" + getDescription().getName() + "] " + message);
    }

    public void runEventCommands(String eventName, Player player) {
        if (player == null) return;
        for (String command : getConfig().getStringList("event-commands." + eventName))
            getServer().dispatchCommand((command.toUpperCase().endsWith(":CONSOLE") ? getServer().getConsoleSender() : player),
                    command.replaceAll("(i?):CONSOLE", "").replaceAll("(i?):PLAYER", ""));
    }

    /**
     * Writes to the log file if size does NOT exceed configuration limitations.
     *
     * @param text The text to store on the next available line in the file.
     */
    public void writeToLog(@NotNull String text) {
        if (getLoggingFile() == null) setLoggingFile(new File(getDataFolder(), "log.txt"));

        long fileSize = (getLoggingFile().length() / 1048576),
                maxFileSize = getConfig().getLong("log-max-size"); // in megabytes.
        if (maxFileSize > 0 && fileSize >= maxFileSize) return;

        try {
            String line = ("[" + new Date() + "] " + text);
            FileWriter writer = new FileWriter(getLoggingFile(), true);
            writer.write(line + "\n");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            log(Level.WARNING, "Unable to write to logging file (" + e.getMessage() + ").");
        }
    }

    private void setupVaultEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null)
            return;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null)
            return;
        setVaultEconomy(rsp.getProvider());
    }

    /**
     * Sets up all tasks; however, it doesn't cancel or stop existing tasks.
     */
    public void setupTasks() {
        setManagementTask(new ManagementTask(this));
        getManagementTask().runTaskTimerAsynchronously(this, 20, 20);

        final int cleanDelay = getConfig().getInt("base-block-sync-delay");
        if (cleanDelay >= 0) {
            setCleanupTask(new CleanupTask(this));
            getCleanupTask().runTaskTimer(this, cleanDelay * 20L, cleanDelay * 20L);
        }

        setInSightTask(new VisualTask(this));
        getInSightTask().runTaskTimerAsynchronously(this, 60, getConfig().getInt("view-tick"));

        setShopVisitItemTask(new ShopVisitItemTask(this));
        getShopVisitItemTask().runTaskTimerAsynchronously(this, 0, 60);
    }

    private void updateConfigs() {
        long startTime = System.currentTimeMillis();
        int totalUpdates = 0;

        String[] configNames = {"config", "lang", "menus"};
        for (int i = -1; ++i < configNames.length; ) {
            String name = configNames[i];

            InputStream inputStream = getClass().getResourceAsStream("/" + name + ".yml");
            if (inputStream == null) continue;

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            FileConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
            int updateCount = updateKeys(yaml, name.equalsIgnoreCase("config") ? getConfig() : name.equalsIgnoreCase("lang") ? getLangConfig() : getMenusConfig());

            switch (name) {
                case "config":

                    String createSound = getConfig().getString("immersion-section.shop-creation-sound");
                    if (Math.floor(getServerVersion()) <= 1_8) {

                        if (createSound != null && createSound.equalsIgnoreCase("ENTITY_ITEM_PICKUP")) {
                            getConfig().set("immersion-section.shop-creation-sound", "ITEM_PICKUP");
                            updateCount++;
                        }

                        String deleteSound = getConfig().getString("immersion-section.shop-delete-sound");
                        if (deleteSound != null && (deleteSound.equalsIgnoreCase("BLOCK_WOOD_BREAK")
                                || deleteSound.equalsIgnoreCase("BLOCK_WOOD_STEP"))) {
                            getConfig().set("immersion-section.shop-delete-sound", "STEP_WOOD");
                            updateCount++;
                        }

                        String visitSound = getConfig().getString("immersion-section.shop-visit-sound");
                        if (visitSound != null && (visitSound.equalsIgnoreCase("ENTITY_ENDERMAN_TELEPORT")
                                || visitSound.equalsIgnoreCase("ENTITY_ENDERMEN_TELEPORT"))) {
                            getConfig().set("immersion-section.shop-visit-sound", "ENDERMAN_TELEPORT");
                            updateCount++;
                        }

                        String bbmSound = getConfig().getString("immersion-section.shop-bbm-sound");
                        if (bbmSound != null && (bbmSound.equalsIgnoreCase("ENTITY_SNOWBALL_THROW")
                                || bbmSound.equalsIgnoreCase("ENTITY_ARROW_SHOOT"))) {
                            getConfig().set("immersion-section.shop-bbm-sound", "SHOOT_ARROW");
                            updateCount++;
                        }

                    } else if (Math.floor(getServerVersion()) <= 1_12) {

                        if (createSound != null && createSound.equalsIgnoreCase("ITEM_PICKUP")) {
                            getConfig().set("immersion-section.shop-creation-sound", "ENTITY_ITEM_PICKUP");
                            updateCount++;
                        }

                        String deleteSound = getConfig().getString("immersion-section.shop-delete-sound");
                        if (deleteSound != null && (deleteSound.equalsIgnoreCase("BLOCK_WOOD_BREAK")
                                || deleteSound.equalsIgnoreCase("STEP_WOOD"))) {
                            getConfig().set("immersion-section.shop-delete-sound", "BLOCK_WOOD_STEP");
                            updateCount++;
                        }

                        if (createSound != null && createSound.equalsIgnoreCase("ITEM_PICKUP")) {
                            getConfig().set("immersion-section.shop-creation-sound", "ENTITY_ITEM_PICKUP");
                            updateCount++;
                        }

                        String bbmSound = getConfig().getString("immersion-section.shop-bbm-sound");
                        if (bbmSound != null && (bbmSound.equalsIgnoreCase("ENTITY_SNOWBALL_THROW")
                                || bbmSound.equalsIgnoreCase("SHOOT_ARROW"))) {
                            getConfig().set("immersion-section.shop-bbm-sound", "ENTITY_ARROW_SHOOT");
                            updateCount++;
                        }

                        String visitSound = getConfig().getString("immersion-section.shop-visit-sound");
                        if (visitSound != null && (visitSound.equalsIgnoreCase("ENTITY_ENDERMAN_TELEPORT")
                                || visitSound.equalsIgnoreCase("ENDERMAN_TELEPORT"))) {
                            getConfig().set("immersion-section.shop-visit-sound", "ENTITY_ENDERMEN_TELEPORT");
                            updateCount++;
                        }

                    } else {
                        if (createSound != null && createSound.equalsIgnoreCase("ITEM_PICKUP")) {
                            getConfig().set("immersion-section.shop-creation-sound", "ENTITY_ITEM_PICKUP");
                            updateCount++;
                        }

                        String deleteSound = getConfig().getString("immersion-section.shop-delete-sound");
                        if (deleteSound != null && deleteSound.equalsIgnoreCase("STEP_WOOD")) {
                            getConfig().set("immersion-section.shop-delete-sound", "BLOCK_WOOD_BREAK");
                            updateCount++;
                        }

                        String bbmSound = getConfig().getString("immersion-section.shop-bbm-sound");
                        if (bbmSound != null && bbmSound.equalsIgnoreCase("SHOOT_ARROW")) {
                            getConfig().set("immersion-section.shop-bbm-sound", "ENTITY_SNOWBALL_THROW");
                            updateCount++;
                        }

                        String visitSound = getConfig().getString("immersion-section.shop-visit-sound");
                        if (visitSound != null && (visitSound.equalsIgnoreCase("ENTITY_ENDERMEN_TELEPORT")
                                || visitSound.equalsIgnoreCase("ENDERMAN_TELEPORT"))) {
                            getConfig().set("immersion-section.shop-visit-sound", "ENTITY_ENDERMAN_TELEPORT");
                            updateCount++;
                        }
                    }

                    String baseBlockMaterial = getConfig().getString("shop-block-material"), craftingRecipeLineTwo = getConfig().getString("shop-creation-item.recipe.line-two");
                    if (Math.floor(getServerVersion()) <= 1_12) {
                        if (baseBlockMaterial != null && baseBlockMaterial.contains("END_PORTAL_FRAME")) {
                            getConfig().set("shop-block-material", baseBlockMaterial.replace("END_PORTAL_FRAME", "ENDER_PORTAL_FRAME"));
                            updateCount++;
                        }

                        if (craftingRecipeLineTwo == null || craftingRecipeLineTwo.equalsIgnoreCase("") || craftingRecipeLineTwo.contains("END_STONE")) {
                            getConfig().set("shop-creation-item.recipe.line-two", Objects.requireNonNull(craftingRecipeLineTwo).replace("END_STONE", craftingRecipeLineTwo.replace("END_STONE",
                                    "ENDER_STONE")));
                            updateCount++;
                        }
                    } else {
                        if (baseBlockMaterial != null && baseBlockMaterial.contains("ENDER_PORTAL_FRAME")) {
                            getConfig().set("shop-block-material", baseBlockMaterial.replace("ENDER_PORTAL_FRAME", "END_PORTAL_FRAME"));
                            updateCount++;
                        }

                        if (craftingRecipeLineTwo == null || craftingRecipeLineTwo.equalsIgnoreCase("") || craftingRecipeLineTwo.contains("ENDER_STONE")) {
                            getConfig().set("shop-creation-item.recipe.line-two", Objects.requireNonNull(craftingRecipeLineTwo).replace("ENDER_STONE", craftingRecipeLineTwo.replace("ENDER_STONE",
                                    "END_STONE")));
                            updateCount++;
                        }
                    }

                    break;
                case "menus":
                    ConfigurationSection currentConfigurationSection = getMenusConfig().getConfigurationSection("");
                    if (currentConfigurationSection != null)
                        for (String key : currentConfigurationSection.getKeys(true))
                            if (key.toLowerCase().endsWith("material")) {
                                String currentMaterialName = Objects.requireNonNull(currentConfigurationSection.getString(key)).toUpperCase().replace(" ", "_").replace("-", "_");

                                if (Math.floor(getServerVersion()) <= 1_12) {
                                    if (currentMaterialName.toUpperCase().endsWith("_WOOL")) {
                                        getMenusConfig().set(key, "WOOL");
                                        getMenusConfig().set(key.replaceAll("(?i)material", "durability"), key.toLowerCase().contains("deposit") ? 14 : 5);
                                        updateCount++;
                                    } else if (currentMaterialName.equalsIgnoreCase("CHEST_MINECART")) {
                                        getMenusConfig().set(key, currentMaterialName.replace("CHEST_MINECART", "STORAGE_MINECART"));
                                        updateCount++;
                                    } else if (currentMaterialName.equalsIgnoreCase("BLACK_STAINED_GLASS_PANE")) {
                                        getMenusConfig().set(key, currentMaterialName.replace("BLACK_STAINED_GLASS_PANE", "STAINED_GLASS_PANE"));
                                        getMenusConfig().set(key.replaceAll("(?i)material", "durability"), 15);
                                        updateCount++;
                                    }
                                } else {
                                    if (currentMaterialName.equalsIgnoreCase("WOOL")) {
                                        getMenusConfig().set(key, key.toLowerCase().contains("deposit") ? currentMaterialName.replace("WOOL", "GREEN_WOOL")
                                                : currentMaterialName.replace("WOOL", "RED_WOOL"));
                                        updateCount++;
                                    } else if (currentMaterialName.equalsIgnoreCase("STORAGE_MINECART")) {
                                        getMenusConfig().set(key, currentMaterialName.replace("STORAGE_MINECART", "CHEST_MINECART"));
                                        updateCount++;
                                    } else if (currentMaterialName.equalsIgnoreCase("STAINED_GLASS_PANE")) {
                                        getMenusConfig().set(key, currentMaterialName.replace("STAINED_GLASS_PANE", "BLACK_STAINED_GLASS_PANE"));
                                        updateCount++;
                                    }
                                }

                            }

                    break;
                default:
                    break;
            }

            try {
                inputStream.close();
                reader.close();
            } catch (IOException e) {
                log(Level.WARNING, e.getMessage());
            }

            if (updateCount > 0)
                switch (name) {
                    case "config":
                        saveConfig();
                        break;
                    case "menus":
                        saveMenusConfig();
                        break;
                    case "lang":
                        saveLangConfig();
                        break;
                    default:
                        break;
                }

            if (updateCount > 0) {
                totalUpdates += updateCount;
                log(Level.INFO, updateCount + " things were fixed, updated, or removed in the '" + name + ".yml' configuration file. (Took " + (System.currentTimeMillis() - startTime) + "ms)");
            }
        }

        if (totalUpdates > 0) {
            reloadConfigs();
            log(Level.INFO, "A total of " + totalUpdates + " thing(s) were fixed, updated, or removed from all the configuration together. (Took " + (System.currentTimeMillis() - startTime) + "ms)");
            log(Level.WARNING, "Please go checkout the configuration files as they are no longer the same as their default counterparts.");
        } else
            log(Level.INFO, "Everything inside the configuration seems to be up to date. (Took " + (System.currentTimeMillis() - startTime) + "ms)");
    }

    private boolean isOutdated() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(
                    "https://api.spigotmc.org/legacy/update.php?resource=69766").openConnection();
            c.setRequestMethod("GET");
            String oldVersion = getDescription().getVersion(),
                    newVersion = new BufferedReader(new InputStreamReader(c.getInputStream())).readLine();
            if (!newVersion.equalsIgnoreCase(oldVersion))
                return true;
        } catch (IOException e) {
            log(Level.WARNING, e.getMessage());
        }
        return false;
    }

    /**
     * Gets latest version text from Spigot.
     *
     * @return The version number on the page.
     */
    public String getLatestVersion() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(
                    "https://api.spigotmc.org/legacy/update.php?resource=69766").openConnection();
            c.setRequestMethod("GET");
            return new BufferedReader(new InputStreamReader(c.getInputStream())).readLine();
        } catch (IOException ex) {
            return getDescription().getVersion();
        }
    }

    // custom configurations

    /**
     * Reloads all configs associated with DisplayShops.
     */
    public void reloadConfigs() {
        reloadConfig();

        if (langFile == null) langFile = new File(getDataFolder(), "lang.yml");
        langConfig = YamlConfiguration.loadConfiguration(langFile);

        Reader defConfigStream = new InputStreamReader(Objects.requireNonNull(this.getResource("lang.yml")), StandardCharsets.UTF_8);
        YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defConfigStream);
        langConfig.setDefaults(defConfig);

        try {
            defConfigStream.close();
        } catch (IOException e) {
            log(Level.WARNING, e.getMessage());
        }

        if (menusFile == null) menusFile = new File(getDataFolder(), "menus.yml");
        menusConfig = YamlConfiguration.loadConfiguration(menusFile);

        defConfigStream = new InputStreamReader(Objects.requireNonNull(this.getResource("menus.yml")), StandardCharsets.UTF_8);
        defConfig = YamlConfiguration.loadConfiguration(defConfigStream);
        langConfig.setDefaults(defConfig);

        try {
            defConfigStream.close();
        } catch (IOException e) {
            log(Level.WARNING, e.getMessage());
        }
    }

    /**
     * Gets the language file configuration.
     *
     * @return The FileConfiguration found.
     */
    public FileConfiguration getLangConfig() {
        if (langConfig == null) reloadConfigs();
        return langConfig;
    }

    /**
     * Gets the menus file configuration.
     *
     * @return The FileConfiguration found.
     */
    public FileConfiguration getMenusConfig() {
        if (menusConfig == null) reloadConfigs();
        return menusConfig;
    }

    /**
     * Saves the default configuration files (Doesn't replace existing).
     */
    public void saveDefaultConfigs() {
        saveDefaultConfig();
        if (langFile == null) langFile = new File(getDataFolder(), "lang.yml");
        if (!langFile.exists()) saveResource("lang.yml", false);
        if (menusFile == null) menusFile = new File(getDataFolder(), "menus.yml");
        if (!menusFile.exists()) saveResource("menus.yml", false);

        reloadConfigs();
    }

    private void saveLangConfig() {
        if (langConfig == null || langFile == null) return;
        try {
            getLangConfig().save(langFile);
        } catch (IOException e) {
            log(Level.WARNING, e.getMessage());
        }
    }

    private void saveMenusConfig() {
        if (menusConfig == null || menusFile == null) return;
        try {
            getMenusConfig().save(menusFile);
        } catch (IOException e) {
            log(Level.WARNING, e.getMessage());
        }
    }

    // packet functions

    /**
     * Schedules a general thread-safe refresh for the shop's display.
     *
     * @param shop The shop to refresh the display of.
     */
    public void refreshShop(Shop shop) {
        getInSightTask().refreshShop(shop);
    }

    /**
     * Gets the display packet a player currently has made for a specific shop (Can return NULL).
     *
     * @param shop   The shop to check for.
     * @param player The player to check for.
     * @return the display packet instance for the shop.
     */
    public DisplayPacket getDisplayPacket(Shop shop, Player player) {
        if (!getDisplayPacketMap().isEmpty() && getDisplayPacketMap().containsKey(player.getUniqueId())) {
            HashMap<UUID, DisplayPacket> packetMap = getDisplayPacketMap().get(player.getUniqueId());
            if (packetMap != null && packetMap.containsKey(shop.getShopId())) return packetMap.get(shop.getShopId());
        }

        return null;
    }

    private int updateKeys(FileConfiguration jarYaml, FileConfiguration currentYaml) {
        int updateCount = 0;
        ConfigurationSection currentConfigurationSection = currentYaml.getConfigurationSection(""),
                latestConfigurationSection = jarYaml.getConfigurationSection("");
        if (currentConfigurationSection != null && latestConfigurationSection != null) {
            Set<String> newKeys = latestConfigurationSection.getKeys(true),
                    currentKeys = currentConfigurationSection.getKeys(true);

            for (String updatedKey : newKeys)
                if (!currentKeys.contains(updatedKey) && !updatedKey.toLowerCase().contains("-material-prices")
                        && !updatedKey.toLowerCase().startsWith("translated-") && !updatedKey.toLowerCase().startsWith("decorative-items.")) {
                    currentYaml.set(updatedKey, jarYaml.get(updatedKey));
                    updateCount++;
                }

            for (String currentKey : currentKeys)
                if (!newKeys.contains(currentKey) && !currentKey.toLowerCase().contains("-material-prices")
                        && !currentKey.toLowerCase().startsWith("translated-") && !currentKey.toLowerCase().startsWith("decorative-items")) {
                    currentYaml.set(currentKey, null);
                    updateCount++;
                }
        }

        return updateCount;
    }

    /**
     * Kills the current shop packets in view and removes it from memory for the player.
     * (Note: This is ONLY used for the shop the player is currently looking at)
     *
     * @param player The player to kill the packet for.
     */
    public void killCurrentShopPacket(Player player) {
        if (getShopMemory().isEmpty() || !getShopMemory().containsKey(player.getUniqueId())) return;

        final UUID shopId = getShopMemory().get(player.getUniqueId());
        if (shopId != null && getManager().getShopMap().containsKey(shopId)) {
            final Shop tempCurrentShop = getManager().getShopMap().get(shopId);
            if (tempCurrentShop != null) tempCurrentShop.kill(player);
        }

        getShopMemory().remove(player.getUniqueId());
    }

    /**
     * Un-registers the existing display packet for a specific shop from the player.
     *
     * @param shop   The shop to look for.
     * @param player The player to look for.
     */
    public void removeDisplayPacket(Shop shop, Player player) {
        if (!getDisplayPacketMap().isEmpty() && getDisplayPacketMap().containsKey(player.getUniqueId())) {
            HashMap<UUID, DisplayPacket> packetMap = getDisplayPacketMap().get(player.getUniqueId());
            if (packetMap != null) packetMap.remove(shop.getShopId());
        }
    }

    /**
     * Clears all display packets for a player on file.
     *
     * @param player The player to remove the packets for.
     */
    public void clearDisplayPackets(Player player) {

        if (player.isOnline() && getDisplayPacketMap().containsKey(player.getUniqueId()))
            for (Map.Entry<UUID, DisplayPacket> entry : getDisplayPacketMap().get(player.getUniqueId()).entrySet()) {
                if (entry == null || entry.getValue() == null) continue;
                entry.getValue().hide(player);
            }

        getDisplayPacketMap().remove(player.getUniqueId());
    }

    /**
     * Updates the display packet for a specific shop for the player.
     *
     * @param shop          The shop to use.
     * @param player        The player to set it for.
     * @param displayPacket The packet to set for the shop.
     */
    public void updateDisplayPacket(Shop shop, Player player, DisplayPacket displayPacket) {
        if (!getDisplayPacketMap().isEmpty() && getDisplayPacketMap().containsKey(player.getUniqueId())) {
            HashMap<UUID, DisplayPacket> packetMap = getDisplayPacketMap().get(player.getUniqueId());
            if (packetMap != null) {
                packetMap.put(shop.getShopId(), displayPacket);
                return;
            }
        }

        HashMap<UUID, DisplayPacket> packetMap = new HashMap<>();
        packetMap.put(shop.getShopId(), displayPacket);
        getDisplayPacketMap().put(player.getUniqueId(), packetMap);
    }

    // NBT functions

    /**
     * Sends the entire display to the player. (NOTE: The 'showHolograms' parameter will be ignored if the 'always-display' is enabled)
     *
     * @param shop          The shop to create the display for.
     * @param player        The player to send the display packets to.
     * @param showHolograms Whether holograms above the glass and item are visible/created.
     */
    public synchronized void sendDisplayPacket(Shop shop, Player player, boolean showHolograms) {
        if (shop == null || shop.getBaseLocation() == null || player == null || !player.isOnline()) return;
        shop.kill(player);

        final boolean isTooFar = (!player.getWorld().getName().equalsIgnoreCase(shop.getBaseLocation().getWorldName())
                || shop.getBaseLocation().distance(player.getLocation(), true) >= 16);
        if (shop.getBaseLocation() == null || isTooFar) return;

        shop.display(player, showHolograms);
    }

    // getters & setters

    /**
     * @return Returns the manager where most data and API methods are stored.
     */
    public DManager getManager() {
        return manager;
    }

    private void setManager(DManager manager) {
        this.manager = manager;
    }

    /**
     * @return Server version in the format XXX.X where the decimal digit is the 'R' version.
     */
    public double getServerVersion() {
        return serverVersion;
    }

    private void setServerVersion(double serverVersion) {
        this.serverVersion = serverVersion;
    }

    /**
     * Returns the vault economy hook.
     *
     * @return Economy class.
     */
    public Economy getVaultEconomy() {
        return vaultEconomy;
    }

    private void setVaultEconomy(Economy vaultEconomy) {
        this.vaultEconomy = vaultEconomy;
    }

    public Connection getDatabaseConnection() {
        return databaseConnection;
    }

    private void setDatabaseConnection(Connection databaseConnection) {
        this.databaseConnection = databaseConnection;
    }

    public ManagementTask getManagementTask() {
        return managementTask;
    }

    public void setManagementTask(ManagementTask managementTask) {
        this.managementTask = managementTask;
    }

    public VisualTask getInSightTask() {
        return inSightTask;
    }

    public void setInSightTask(VisualTask inSightTask) {
        this.inSightTask = inSightTask;
    }

    public CleanupTask getCleanupTask() {
        return cleanupTask;
    }

    public void setCleanupTask(CleanupTask cleanupTask) {
        this.cleanupTask = cleanupTask;
    }

    public ShopVisitItemTask getShopVisitItemTask() {
        return shopVisitItemTask;
    }

    public void setShopVisitItemTask(ShopVisitItemTask shopVisitItemTask) {
        this.shopVisitItemTask = shopVisitItemTask;
    }

    public HashMap<UUID, HashMap<UUID, DisplayPacket>> getDisplayPacketMap() {
        return displayPacketMap;
    }

    private void setDisplayPacketMap(HashMap<UUID, HashMap<UUID, DisplayPacket>> displayPacketMap) {
        this.displayPacketMap = displayPacketMap;
    }

    public List<UUID> getTeleportingPlayers() {
        return teleportingPlayers;
    }

    private void setTeleportingPlayers(List<UUID> teleportingPlayers) {
        this.teleportingPlayers = teleportingPlayers;
    }

    /**
     * Checks if paper spigot methods exist.
     *
     * @return Whether paper spigot is detected.
     */
    public boolean isPaperSpigot() {
        return paperSpigot;
    }

    private void setPaperSpigot(boolean paperSpigot) {
        this.paperSpigot = paperSpigot;
    }

    public HashMap<UUID, UUID> getShopMemory() {
        return shopMemory;
    }

    private void setShopMemory(HashMap<UUID, UUID> shopMemory) {
        this.shopMemory = shopMemory;
    }

    /**
     * This gets the logging file.
     *
     * @return The file used for plugin logging.
     */
    public File getLoggingFile() {
        return loggingFile;
    }

    private void setLoggingFile(File loggingFile) {
        this.loggingFile = loggingFile;
    }

    public boolean isPrismaInstalled() {
        return prismaInstalled;
    }

    private void setPrismaInstalled(boolean prismaInstalled) {
        this.prismaInstalled = prismaInstalled;
    }

    public HeadDatabaseAPI getHeadDatabaseAPI() {
        return headDatabaseAPI;
    }

    private void setHeadDatabaseAPI(HeadDatabaseAPI headDatabaseAPI) {
        this.headDatabaseAPI = headDatabaseAPI;
    }

    public PapiHelper getPapiHelper() {
        return papiHelper;
    }

    private void setPapiHelper(PapiHelper papiHelper) {
        this.papiHelper = papiHelper;
    }

    public SimpleDateFormat getDateFormat() {
        return dateFormat;
    }

    public boolean isTownyInstalled() {
        return townyInstalled;
    }

    public Listeners getListeners() {
        return listeners;
    }

    public PacketManager getPacketManager() {
        return packetManager;
    }

    public boolean isItemAdderInstalled() {
        return isItemAdderInstalled;
    }
}