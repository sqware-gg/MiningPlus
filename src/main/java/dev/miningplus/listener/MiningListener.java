package dev.miningplus.listener;

import dev.miningplus.gui.MiningGui;
import dev.miningplus.mining.MiningService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class MiningListener implements Listener {
    private final MiningService service;
    private final MiningGui gui;

    public MiningListener(MiningService service, MiningGui gui) {
        this.service = service;
        this.gui = gui;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (service.internalBreakCheckActive()) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("miningplus.use")) {
            return;
        }
        service.handleBreak(player, event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("miningplus.use")
                || !service.config().guiEnabled()
                || !service.config().guiPickaxeRightClickEnabled()) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!service.isPickaxe(item)) {
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK && offhandPlaceableShouldWin(player)) {
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK && !canOpenMenuOverBlock(event.getClickedBlock(), player)) {
            return;
        }
        event.setCancelled(true);
        gui.open(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        if (!player.hasPermission("miningplus.use")) {
            return;
        }
        service.rollToolUpgradeFromEnchantingTable(player, event.getItem(), event.getExpLevelCost());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (result == null || result.getType().isAir()) {
            return;
        }
        ItemStack merged = result.clone();
        if (service.applyAnvilPickaxeData(merged, event.getInventory().getItem(0), event.getInventory().getItem(1))) {
            event.setResult(merged);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        service.handleEncounterDeath(event);
    }

    @SuppressWarnings("deprecation")
    private boolean canOpenMenuOverBlock(Block block, Player player) {
        if (!service.config().guiPickaxeRightClickBlocks()) {
            return false;
        }
        if (block == null) {
            return true;
        }
        Material type = block.getType();
        return player.isSneaking() || !type.isInteractable();
    }

    private boolean offhandPlaceableShouldWin(Player player) {
        if (!service.config().guiPickaxeRightClickRespectsOffhandPlaceable()) {
            return false;
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        return offhand != null && !offhand.getType().isAir() && offhand.getType().isBlock();
    }
}
