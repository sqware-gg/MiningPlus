package dev.miningplus;

import dev.miningplus.command.MiningCommand;
import dev.miningplus.command.MiningPlusCommand;
import dev.miningplus.config.ConfigReferenceWriter;
import dev.miningplus.config.MiningPlusConfig;
import dev.miningplus.data.PlacedBlockStore;
import dev.miningplus.data.PlayerDataStore;
import dev.miningplus.economy.EconomyService;
import dev.miningplus.economy.PointsHook;
import dev.miningplus.gui.MiningGui;
import dev.miningplus.gui.MiningMenuListener;
import dev.miningplus.integration.MiningPlusExpansion;
import dev.miningplus.listener.BlockPlaceListener;
import dev.miningplus.listener.MiningListener;
import dev.miningplus.mining.MiningService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MiningPlusPlugin extends JavaPlugin {
    private MiningPlusConfig config;
    private PlayerDataStore playerStore;
    private PlacedBlockStore placedStore;
    private EconomyService economyService;
    private PointsHook pointsHook;
    private MiningService miningService;
    private MiningGui gui;
    private MiningPlusExpansion expansion;

    @Override
    public void onEnable() {
        ConfigReferenceWriter.saveDefaultAndReferenceIfNeeded(this);

        config = new MiningPlusConfig(this);
        playerStore = new PlayerDataStore(this);
        placedStore = new PlacedBlockStore(this);
        economyService = new EconomyService(this);
        pointsHook = new PointsHook(this);
        miningService = new MiningService(this, config, playerStore, placedStore, economyService, pointsHook);
        gui = new MiningGui(miningService);

        registerCommands();
        getServer().getPluginManager().registerEvents(new MiningListener(miningService, gui), this);
        getServer().getPluginManager().registerEvents(new MiningMenuListener(miningService, gui), this);
        if (config.antiExploitEnabled()) {
            getServer().getPluginManager().registerEvents(new BlockPlaceListener(miningService), this);
        }

        miningService.start();
        registerPlaceholders();

        if (economyService.available()) {
            getLogger().info("Hooked Vault economy provider: " + economyService.providerName());
        } else {
            getLogger().info("No Vault economy provider found. Money rewards and /mining sell are disabled.");
        }
        if (pointsHook.available()) {
            getLogger().info("Hooked PointsPlus API.");
        } else {
            getLogger().info("No PointsPlus plugin found. Optional point rewards are disabled.");
        }
        getLogger().info("Loaded " + config.blockRewards().size() + " mining blocks and "
                + playerStore.playerCount() + " player profiles.");
    }

    @Override
    public void onDisable() {
        if (miningService != null) {
            miningService.stop();
        }
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
    }

    private void registerCommands() {
        MiningCommand miningCommand = new MiningCommand(miningService, gui);
        PluginCommand mining = getCommand("mining");
        if (mining != null) {
            mining.setExecutor(miningCommand);
            mining.setTabCompleter(miningCommand);
        }

        MiningPlusCommand adminCommand = new MiningPlusCommand(miningService, () -> pointsHook.refresh());
        PluginCommand admin = getCommand("miningplus");
        if (admin != null) {
            admin.setExecutor(adminCommand);
            admin.setTabCompleter(adminCommand);
        }
    }

    private void registerPlaceholders() {
        if (!config.placeholdersEnabled()) {
            return;
        }
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        expansion = new MiningPlusExpansion(this, miningService);
        expansion.register();
        getLogger().info("Registered PlaceholderAPI expansion: %miningplus_*%.");
    }
}
