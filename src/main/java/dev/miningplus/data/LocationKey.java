package dev.miningplus.data;

import org.bukkit.block.Block;

public record LocationKey(String world, int x, int y, int z) {
    public static LocationKey of(Block block) {
        return new LocationKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public String chunkKey() {
        return world + ":" + (x >> 4) + ":" + (z >> 4);
    }
}
