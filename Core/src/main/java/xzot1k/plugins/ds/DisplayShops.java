/*
 * Copyright (c) 2021 XZot1K, All rights reserved.
 */

package xzot1k.plugins.ds;

import me.arcaniax.hdb.api.HeadDatabaseAPI;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xzot1k.plugins.ds.api.DManager;
import xzot1k.plugins.ds.api.PacketManager;
import xzot1k.plugins.ds.api.handlers.DisplayPacket;
import xzot1k.plugins.ds.api.objects.DataPack;
import xzot1k.plugins.ds.api.objects.Menu;
import xzot1k.plugins.ds.api.objects.Shop;
import xzot1k.plugins.ds.core.Commands;
import xzot1k.plugins.ds.core.Listeners;
import xzot1k.plugins.ds.core.TabCompleter;
import xzot1k.plugins.ds.core.gui.BackendMenu;
import xzot1k.plugins.ds.core.gui.MenuListener;
import xzot1k.plugins.ds.core.hooks.*;
import xzot1k.plugins.ds.core.tasks.CleanupTask;
import xzot1k.plugins.ds.core.tasks.ManagementTask;
import xzot1k.plugins.ds.core.tasks.VisualTask;

import java.io.*;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Stream;

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

    // listeners
    private Listeners listeners;
    private MenuListener menuListener;

    // hook handlers
    private EconomyHandler economyHandler;
    private HeadDatabaseAPI headDatabaseAPI;
    private PapiHelper papiHelper;
    private boolean isItemAdderInstalled;

    // Task handlers
    private VisualTask inSightTask;
    private ManagementTask managementTask;
    private CleanupTask cleanupTask;

    // Data handlers
    private Connection databaseConnection;
    private FileConfiguration langConfig;
    private File langFile, loggingFile;
    private HashMap<String, Menu> menuMap;

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
        saveDefaultMenuConfigs();

        setServerVersion(Double.parseDouble(getServer().getClass().getPackage().getName()
                .replace(".", ",").split(",")[3]
                .replace("_R", ".").replaceAll("[rvV_]*", "")));

        fixConfig();

        menuMap = new HashMap<>();
        shopMemory = new HashMap<>();
        displayPacketMap = new HashMap<>();
        teleportingPlayers = new ArrayList<>();

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

        this.economyHandler = new EconomyHandler(this);
        this.townyInstalled = (getServer().getPluginManager().getPlugin("Towny") != null);
        this.isItemAdderInstalled = (getServer().getPluginManager().getPlugin("ItemsAdder") != null);
        setPrismaInstalled(getServer().getPluginManager().getPlugin("Prisma") != null);

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

        loadMenus();

        long databaseStartTime = System.currentTimeMillis();
        final boolean fixedTables = setupDatabase();
        if (getDatabaseConnection() != null)
            log(Level.INFO, "Communication to the database was successful. (Took " + (System.currentTimeMillis() - databaseStartTime) + "ms)");
        else {
            log(Level.WARNING, "Communication to the database failed. (Took " + (System.currentTimeMillis() - databaseStartTime) + "ms)");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(listeners = new Listeners(this), this);
        getServer().getPluginManager().registerEvents(menuListener = new MenuListener(this), this);

        if (isItemAdderInstalled()) getServer().getPluginManager().registerEvents(new ItemsAdderHandler(this), this);
        new SkyBlockListener(this);

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
                log(Level.WARNING, "Unable to create the custom recipe for the shop creation item. This is normally due to the version of Minecraft" +
                        " not supporting the new 'NamespacedKey' API values. "
                        + "To avoid this issue entirely -> DISABLE THE 'craftable' OPTION IN THE 'shop-creation-item' SECTION LOCATED IN THE " +
                        "'config.yml' file.");
            }
        }

        getManager().loadShops(false, false);
        getManager().loadMarketRegions(false);
        for (Player player : getServer().getOnlinePlayers()) getManager().loadDataPack(player);

        setupTasks();
        double version = Math.floor(getServerVersion());
        log(Level.INFO, "Fully loaded and enabled with " + (version / (version >= 100 ? 100 : 10))
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
                        log(Level.INFO, "Saving shops " + current + "/" + shopCount + " (" + Math.min(100,
                                (int) (((double) current / (double) shopCount) * 100)) + "%)...");
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
                        + " sell_price REAL, stock INTEGER, shop_item TEXT, trade_item TEXT, limits LONGTEXT, shop_item_amount INTEGER, balance REAL,"
                        + " command_only_mode NUMERIC, commands TEXT, change_time_stamp TEXT, description TEXT, base_material TEXT, extra_data TEXT)",
                        markRegionParameters = "(id TEXT PRIMARY KEY NOT NULL, point_one TEXT, point_two TEXT, renter TEXT, rent_time_stamp TEXT,"
                                + " extended_duration INTEGER, extra_data TEXT)",
                        playerDataParameters = "(uuid TEXT PRIMARY KEY NOT NULL, bbm_unlocks TEXT, cooldowns TEXT, transaction_limits TEXT, notify " +
                                "TEXT)",
                        recoveryParameters = "(uuid VARCHAR(100) PRIMARY KEY NOT NULL, currency REAL, item_amount INTEGER, item TEXT)";

                fixedTables = handleDatabaseFixing(statement, shopParameters, markRegionParameters, playerDataParameters, recoveryParameters, host);

            } else {
                try {
                    Class.forName("com.mysql.cj.jdbc.Driver");
                } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                    Class.forName("com.mysql.jdbc.Driver");
                }
                final boolean useSSL = getConfig().getBoolean("mysql.use-ssl");
                final String databaseName = getConfig().getString("mysql.database"), port = getConfig().getString("mysql.port"),
                        username = getConfig().getString("mysql.username"), password = getConfig().getString("mysql.password"),
                        syntax = "jdbc:mysql://" + host + ":" + port + "/" + databaseName + "?useSSL=" + (useSSL ? "true" : "false") +
                                "&autoReconnect=true&useUnicode=yes";

                setDatabaseConnection(DriverManager.getConnection(syntax, username, password));
                Statement statement = getDatabaseConnection().createStatement();

                final String shopParameters = "(id VARCHAR(100) PRIMARY KEY NOT NULL, location LONGTEXT NOT NULL, owner LONGTEXT, assistants " +
                        "LONGTEXT, buy_price DOUBLE, sell_price DOUBLE, stock INT, shop_item LONGTEXT, trade_item LONGTEXT, limits LONGTEXT, " +
                        "shop_item_amount INT, balance DOUBLE, command_only_mode BOOLEAN, commands LONGTEXT, change_time_stamp LONGTEXT, " +
                        "description LONGTEXT, base_material LONGTEXT, extra_data LONGTEXT)",
                        markRegionParameters = "(id VARCHAR(100) PRIMARY KEY NOT NULL, point_one LONGTEXT, point_two LONGTEXT, renter LONGTEXT, " +
                                "rent_time_stamp LONGTEXT, extended_duration INT, extra_data LONGTEXT)",
                        playerDataParameters = "(uuid VARCHAR(100) PRIMARY KEY NOT NULL, bbm_unlocks LONGTEXT, cooldowns LONGTEXT, " +
                                "transaction_limits LONGTEXT, notify LONGTEXT)",
                        recoveryParameters = "(uuid VARCHAR(100) PRIMARY KEY NOT NULL, currency DOUBLE, item_amount INT, item LONGTEXT)";

                fixedTables = handleDatabaseFixing(statement, shopParameters, markRegionParameters, playerDataParameters, recoveryParameters, host);

                exportMySQLDatabase();
            }
        } catch (ClassNotFoundException | SQLException | IOException e) {
            e.printStackTrace();
            log(Level.WARNING, e.getMessage());
        }

        return fixedTables;
    }

    private boolean handleDatabaseFixing(@NotNull Statement statement, @NotNull String shopParameters, @NotNull String markRegionParameters,
                                         @NotNull String playerDataParameters, @NotNull String recoveryParameters, @Nullable String host) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS shops " + shopParameters + ";");
        if (!tableExists("shops")) {
            getServer().getLogger().warning("There was an issue creating the \"shops\" table. This could be related to user permissions via SQL.");
            return false;
        }

        statement.execute("CREATE TABLE IF NOT EXISTS market_regions " + markRegionParameters + ";");
        if (!tableExists("market_regions")) {
            getServer().getLogger().warning("There was an issue creating the \"market_regions\" table."
                    + " This could be related to user permissions via SQL.");
            return false;
        }

        statement.execute("CREATE TABLE IF NOT EXISTS player_data " + playerDataParameters + ";");
        if (!tableExists("player_data")) {
            getServer().getLogger().warning("There was an issue creating the \"player_data\" table. This could be related to user permissions via " +
                    "SQL.");
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

        if (!hasColumn(resultSet, "base_material") || hasColumn(resultSet, "base_location")
                || !hasColumn(resultSet, "limits")) {

            resultSet.close();

            log(Level.WARNING, "Database Structure Mismatch. Fixing Tables...");

          /*  try {
                statement.executeUpdate("BACKUP TO '" + new File(getDataFolder(), "/table-fix-backup.db").getPath()
                        .replace("'", "").replace("\"", "") + "';");
            } catch (SQLException e) {
                log(Level.WARNING, "Unable to backup the 'table-fix-backup.db' file.");
            }*/

            for (String tableName : new String[]{"shops", "market_regions", "player_data"}) {
                statement.execute("CREATE TABLE IF NOT EXISTS temp_" + tableName + " "
                        + (tableName.equals("shops") ? shopParameters : (tableName.equals("market_regions")
                        ? markRegionParameters : playerDataParameters)) + ";");

                if (tableName.equals("shops")) insertRow(("temp_" + tableName), host);
                else statement.execute("INSERT INTO temp_" + tableName + " SELECT * FROM " + tableName + ";");

                statement.execute("DROP TABLE IF EXISTS " + tableName + ";");
                statement.execute("ALTER TABLE temp_" + tableName + " RENAME TO " + tableName + ";");
            }

            //getManager().loadShops(false, false);
            //statement.execute("DROP TABLE IF EXISTS shops;");

            // statement.execute("CREATE TABLE IF NOT EXISTS shops " + shopParameters + ";");
            // for (Shop shop : getManager().getShopMap().values()) shop.save(false);
            return true;

        } else resultSet.close();

        statement.close();
        return false;
    }

    private void insertRow(@NotNull String tableName, @Nullable String host) {

        long current = 0, count = 0, countPercentage = 0;
        try (PreparedStatement statement = getDatabaseConnection().prepareStatement("SELECT Count(*) FROM shops;");
             ResultSet rs = statement.executeQuery()) {

            count = (rs.next() ? rs.getInt(1) : 0);
            countPercentage = ((long) (count * 0.15));

        } catch (SQLException e) {
            e.printStackTrace();
        }

        try {

            Statement statement = getDatabaseConnection().createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT * FROM shops;");

            while (resultSet.next()) {

                final String id = resultSet.getString("id"), location = resultSet.getString("location"), owner = resultSet.getString("owner"),
                        assistants = resultSet.getString("assistants"), shopItem = resultSet.getString("shop_item"),
                        tradeItem = resultSet.getString("trade_item"), commands = resultSet.getString("commands"),
                        changeTimeStamp = resultSet.getString("change_time_stamp"), description = resultSet.getString("description"),
                        baseMaterial = resultSet.getString("base_material"), extraData = resultSet.getString("extra_data"),
                        limits = (resultSet.getInt("buy_limit") + ";" + resultSet.getInt("buy_counter") + ";" +
                                resultSet.getInt("sell_limit") + ";" + resultSet.getInt("sell_counter") + ";0;0");

                final double buyPrice = resultSet.getDouble("buy_price"), sellPrice = resultSet.getDouble("sell_price"),
                        balance = resultSet.getDouble("balance");

                final int stock = resultSet.getInt("stock"), shopItemAmount = resultSet.getInt("shop_item_amount"),
                        commandOnly = resultSet.getInt("command_only_mode");

                final String syntax = "INSERT " + ((host == null || host.isEmpty()) ? "OR REPLACE" : "") + " INTO " + tableName + "(id, location, owner, assistants,"
                        + " buy_price, sell_price, stock, shop_item, trade_item, limits, shop_item_amount, balance, command_only_mode, commands, change_time_stamp,"
                        + " description, base_material, extra_data) VALUES('" + id + "', '" + location + "', '" + owner + "', '" + assistants + "', " + buyPrice
                        + ", " + sellPrice + ", " + stock + ", '" + shopItem + "', '" + tradeItem + "', '" + limits + "', " + shopItemAmount + ", " + balance
                        + ", '" + commandOnly + "', '" + commands + "', '" + changeTimeStamp + "', '" + description + "', '" + baseMaterial + "', '" + extraData + "');";

                PreparedStatement ps = getDatabaseConnection().prepareStatement(syntax);
                ps.executeUpdate();
                ps.close();

                current++;

                if ((countPercentage <= 0 || current % countPercentage == 0 || current == count))
                    log(Level.INFO, "Populating the " + tableName + " table " + current + "/" + count
                            + " (" + Math.min(100, (int) (((double) current / (double) count) * 100)) + "%)...");
            }

            resultSet.close();
            statement.close();

        } catch (SQLException e) {
            e.printStackTrace();
        }

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
    }

    /**
     * Cancels all tasks, but doesn't restart them.
     */
    public void cancelTasks() {
        if (getManagementTask() != null) getManagementTask().cancel();
        if (getCleanupTask() != null) getCleanupTask().cancel();
        if (getInSightTask() != null) getInSightTask().cancel();
    }

    private boolean isOutdated() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("https://api.spigotmc.org/legacy/update.php?resource=69766").openConnection();
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
            HttpURLConnection c = (HttpURLConnection) new URL("https://api.spigotmc.org/legacy/update.php?resource=69766").openConnection();
            c.setRequestMethod("GET");
            return new BufferedReader(new InputStreamReader(c.getInputStream())).readLine();
        } catch (IOException ex) {
            return getDescription().getVersion();
        }
    }

    // custom configurations
    public FileConfiguration getConfigFromJar(@NotNull String configPath) {
        try (InputStream inputStream = getResource(configPath)) {
            if (inputStream == null) return null;

            try (InputStreamReader reader = new InputStreamReader(inputStream)) {
                return YamlConfiguration.loadConfiguration(reader);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public void syncConfiguration(@NotNull String configPath) {
        int addedCounter = 0, removedCounter = 0;

        final File file = new File(getDataFolder(), configPath);
        if (!file.exists()) return;

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        FileConfiguration jarConfig = getConfigFromJar(configPath);
        for (String key : jarConfig.getKeys(true)) {
            if (!config.contains(key)) {
                config.set(key, jarConfig.get(key));
                System.out.println("Added \"" + key + "\".");
                addedCounter++;
            }
        }

        for (String key : config.getKeys(true)) {
            if (!jarConfig.contains(key)) {
                config.set(key, null);
                System.out.println("Removed \"" + key + "\".");
                removedCounter++;
            }
        }

        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (addedCounter > 0 || removedCounter > 0)
            log(Level.INFO, (addedCounter + " additions and " + removedCounter + " removals were made to the \"" + configPath + "\" file."));
    }

    public void fixConfig() {

        if (getServerVersion() < 1_13) {
            final String shopBlock = getConfig().getString("shop-block-material");
            if (shopBlock == null || shopBlock.isEmpty() || shopBlock.toUpperCase().contains("END_PORTAL_FRAME"))
                getConfig().set("shop-block-material", "ENDER_PORTAL_FRAME:0");

            ConfigurationSection recipeSection = getConfig().getConfigurationSection("recipe");
            if (recipeSection != null) for (Map.Entry<String, Object> entry : recipeSection.getValues(true).entrySet()) {
                final String value = String.valueOf(entry.getValue());
                if (value.toUpperCase().contains("END_STONE")) recipeSection.set(entry.getKey(), value.replace("END_STONE", "ENDER_STONE"));
            }

            if (getServerVersion() < 1_9) {
                ConfigurationSection immersionSection = getConfig().getConfigurationSection("immersion-section");
                if (immersionSection != null) for (Map.Entry<String, Object> entry : immersionSection.getValues(true).entrySet()) {
                    final String value = String.valueOf(entry.getValue());
                    if (value.equalsIgnoreCase("ENTITY_ITEM_PICKUP")) immersionSection.set(entry.getKey(), "ITEM_PICKUP");
                    else if (value.equalsIgnoreCase("BLOCK_WOOD_BREAK")) immersionSection.set(entry.getKey(), "STEP_WOOD");
                    else if (value.equalsIgnoreCase("ENTITY_ENDERMAN_TELEPORT")) immersionSection.set(entry.getKey(), "ENDERMAN_TELEPORT");
                    else if (value.equalsIgnoreCase("ENTITY_SNOWBALL_THROW")) immersionSection.set(entry.getKey(), "SHOOT_ARROW");
                }
            } else if (getServerVersion() >= 1_9) {
                ConfigurationSection immersionSection = getConfig().getConfigurationSection("immersion-section");
                if (immersionSection != null) for (Map.Entry<String, Object> entry : immersionSection.getValues(true).entrySet()) {
                    final String value = String.valueOf(entry.getValue());
                    if (value.equalsIgnoreCase("ENTITY_ENDERMAN_TELEPORT")) immersionSection.set(entry.getKey(), "ENDERMEN_TELEPORT");
                }
            }

            saveConfig();
            reloadConfig();
            return;
        }

        final String shopBlock = getConfig().getString("shop-block-material");
        if (shopBlock == null || shopBlock.isEmpty() || shopBlock.toUpperCase().contains("ENDER_PORTAL_FRAME"))
            getConfig().set("shop-block-material", "END_PORTAL_FRAME:0");

        ConfigurationSection recipeSection = getConfig().getConfigurationSection("recipe");
        if (recipeSection != null) for (Map.Entry<String, Object> entry : recipeSection.getValues(true).entrySet()) {
            final String value = String.valueOf(entry.getValue());
            if (value.toUpperCase().contains("ENDER_STONE")) recipeSection.set(entry.getKey(), value.replace("ENDER_STONE", "END_STONE"));
        }

        ConfigurationSection immersionSection = getConfig().getConfigurationSection("immersion-section");
        if (immersionSection != null) for (Map.Entry<String, Object> entry : immersionSection.getValues(true).entrySet()) {
            final String value = String.valueOf(entry.getValue());
            if (value.equalsIgnoreCase("ITEM_PICKUP")) immersionSection.set(entry.getKey(), "ENTITY_ITEM_PICKUP");
            else if (value.equalsIgnoreCase("STEP_WOOD")) immersionSection.set(entry.getKey(), "BLOCK_WOOD_BREAK");
            else if (value.equalsIgnoreCase("ENDERMAN_TELEPORT")
                    || value.toUpperCase().contains("ENDERMEN_TELEPORT")) immersionSection.set(entry.getKey(), "ENTITY_ENDERMAN_TELEPORT");
            else if (value.equalsIgnoreCase("SHOOT_ARROW")) immersionSection.set(entry.getKey(), "ENTITY_SNOWBALL_THROW");
        }

        saveConfig();
        reloadConfig();
    }

    private void saveDefaultMenuConfigs() {
        try {
            final File menusDir = new File(getDataFolder(), "menus");
            menusDir.mkdirs();

            final URL dir = getClass().getResource("/menus");
            if (dir != null) {
                final FileSystem fileSystem = FileSystems.newFileSystem(dir.toURI(), Collections.emptyMap());
                final Path path = fileSystem.getPath("/menus");

                Stream<Path> walk = Files.walk(path);
                walk.parallel().filter(p -> !p.getFileName().toString().equals("menus")).forEach(source -> {
                    final Path newPath = Paths.get(getDataFolder().getPath(), "menus", source.getFileName().toString());
                    File file = new File(newPath.toUri());
                    if (file.exists()) return;

                    try {
                        Files.copy(source, newPath);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                walk.close();
                fileSystem.close();
            }
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public void loadMenus() {
        final File dir = new File(getDataFolder(), "/menus");
        if (dir.exists() && dir.isDirectory()) {

            final File[] files = dir.listFiles();
            if (files != null && files.length > 0) for (int i = -1; ++i < files.length; ) {
                final File file = files[i];
                getMenuMap().put(file.getName().toLowerCase().replace(".yml", ""), new BackendMenu(file));
            }
        }
    }

    @Override
    public Menu getMenu(@NotNull String name) {
        final Menu menu = getMenuMap().getOrDefault(name.toLowerCase(), null);
        if (menu != null) return menu;

        Map.Entry<String, Menu> menuEntry = getMenuMap().entrySet().parallelStream()
                .filter(entry -> entry.getValue().matches(name))
                .findFirst().orElse(null);
        return ((menuEntry != null) ? menuEntry.getValue() : null);
    }

    @Override
    public boolean matchesAnyMenu(@NotNull String name) {
        return getMenuMap().entrySet().parallelStream().anyMatch(entry -> entry.getValue().matches(name));
    }

    @Override
    public boolean matchesMenu(@NotNull String menuName, @NotNull String inventoryName) {
        final Menu menu = getMenuMap().getOrDefault(menuName, null);
        return (menu != null && menu.matches(inventoryName));
    }

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
     * Saves the default configuration files (Doesn't replace existing).
     */
    public void saveDefaultConfigs() {
        final File configFile = new File(getDataFolder(), "config.yml");
        if (configFile.exists()) syncConfiguration("config.yml");

        saveDefaultConfig();
        if (langFile == null) langFile = new File(getDataFolder(), "lang.yml");
        if (!langFile.exists()) {
            saveResource("lang.yml", false);
        } else syncConfiguration("lang.yml");

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

    // packet functions

    /**
     * Schedules a general thread-safe refresh for the shop's display.
     *
     * @param shop The shop to refresh the display of.
     */
    public void refreshShop(@NotNull Shop shop) {
        getInSightTask().refreshShop(shop);
    }

    /**
     * Gets the display packet a player currently has made for a specific shop (Can return NULL).
     *
     * @param shop   The shop to check for.
     * @param player The player to check for.
     * @return the display packet instance for the shop.
     */
    public DisplayPacket getDisplayPacket(@NotNull Shop shop, @NotNull Player player) {
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
    public void killCurrentShopPacket(@NotNull Player player) {
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
    public void removeDisplayPacket(@NotNull Shop shop, @NotNull Player player) {
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
    public void clearDisplayPackets(@NotNull Player player) {

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
    public void updateDisplayPacket(@NotNull Shop shop, @NotNull Player player, @NotNull DisplayPacket displayPacket) {
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

    /**
     * Sends the entire display to the player. (NOTE: The 'showHolograms' parameter will be ignored if the 'always-display' is enabled)
     *
     * @param shop          The shop to create the display for.
     * @param player        The player to send the display packets to.
     * @param showHolograms Whether holograms above the glass and item are visible/created.
     */
    public synchronized void sendDisplayPacket(@NotNull Shop shop, @NotNull Player player, boolean showHolograms) {
        if (shop.getBaseLocation() == null || !player.isOnline()) return;

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

    public HashMap<UUID, HashMap<UUID, DisplayPacket>> getDisplayPacketMap() {
        return displayPacketMap;
    }

    public List<UUID> getTeleportingPlayers() {
        return teleportingPlayers;
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

    public MenuListener getMenuListener() {
        return menuListener;
    }

    public PacketManager getPacketManager() {
        return packetManager;
    }

    public boolean isItemAdderInstalled() {
        return isItemAdderInstalled;
    }

    public HashMap<String, Menu> getMenuMap() {
        return menuMap;
    }

    public EconomyHandler getEconomyHandler() {return economyHandler;}

}