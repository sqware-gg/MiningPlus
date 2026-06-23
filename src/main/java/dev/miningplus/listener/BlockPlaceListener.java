package dev.miningplus.listener;

import dev.miningplus.mining.MiningService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public final class BlockPlaceListener implements Listener {
    private final MiningService service;

    public BlockPlaceListener(MiningService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        service.handlePlace(event.getBlockPlaced());
    }
}
