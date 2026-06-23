package dev.miningplus.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class MiningMenuHolder implements InventoryHolder {
    public enum MenuType {
        MAIN,
        JOURNAL,
        COMMISSIONS,
        SHOP,
        PICKAXE,
        PERKS
    }

    private final MenuType type;
    private Inventory inventory;

    public MiningMenuHolder() {
        this(MenuType.MAIN);
    }

    public MiningMenuHolder(MenuType type) {
        this.type = type;
    }

    public MenuType type() {
        return type;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
