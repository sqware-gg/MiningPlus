package dev.miningplus.gui;

import dev.miningplus.data.PlayerData;
import dev.miningplus.mining.CommissionActionResult;
import dev.miningplus.mining.JournalClaimResult;
import dev.miningplus.mining.MiningService;
import dev.miningplus.mining.PerkPurchaseResult;
import dev.miningplus.mining.PickaxeRefineResult;
import dev.miningplus.mining.ShopPurchaseResult;
import dev.miningplus.util.NumberFormat;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class MiningMenuListener implements Listener {
    private final MiningService service;
    private final MiningGui gui;

    public MiningMenuListener(MiningService service, MiningGui gui) {
        this.service = service;
        this.gui = gui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MiningMenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof MiningMenuHolder)) {
            return;
        }
        MiningMenuHolder holder = (MiningMenuHolder) event.getInventory().getHolder();
        PlayerData data = service.players().getOrCreate(player.getUniqueId(), player.getName());
        switch (holder.type()) {
            case MAIN -> handleMain(event.getSlot(), player, data, event.getInventory());
            case JOURNAL -> handleJournal(event.getSlot(), player, event.getInventory());
            case COMMISSIONS -> handleCommissions(event.getSlot(), player, event.getInventory());
            case SHOP -> handleShop(event.getSlot(), player, event.getInventory());
            case PICKAXE -> handlePickaxe(event.getSlot(), player, event.getInventory());
            case PERKS -> handlePerks(event.getSlot(), player, event.getInventory());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MiningMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void handleMain(int slot, Player player, PlayerData data, Inventory inventory) {
        if (gui.isMainSlot("perks", slot)) {
            if (!service.config().perksEnabled() || !player.hasPermission("miningplus.perks")) {
                return;
            }
            gui.openPerks(player);
            return;
        }
        if (gui.isMainSlot("pickaxe", slot)) {
            if (!service.config().pickaxeProgressionEnabled()
                    || !player.hasPermission(service.config().pickaxeProgression().permission())) {
                return;
            }
            gui.openPickaxe(player);
            return;
        }
        if (gui.isMainSlot("sell", slot)) {
            if (!service.config().sellEnabled() || !service.economy().available()
                    || !player.hasPermission("miningplus.sell")) {
                return;
            }
            player.closeInventory();
            player.performCommand("mining sell all");
            return;
        }
        if (gui.isMainSlot("artifacts", slot)) {
            if (!service.config().artifactsEnabled() || !player.hasPermission("miningplus.artifacts")) {
                return;
            }
            player.closeInventory();
            player.performCommand("mining artifacts");
            return;
        }
        if (gui.isMainSlot("notifications", slot)) {
            boolean enabled = data.toggleNotifications();
            service.send(player, enabled ? "notifications-on" : "notifications-off", Map.of());
            gui.render(player, inventory);
            return;
        }
        if (gui.isMainSlot("journal", slot)) {
            if (!service.config().journalEnabled() || !player.hasPermission("miningplus.journal")) {
                service.send(player, "no-permission", Map.of());
                return;
            }
            gui.openJournal(player);
            return;
        }
        if (gui.isMainSlot("commissions", slot)) {
            if (!service.config().commissionsEnabled()) {
                service.send(player, "commissions-disabled", Map.of());
                return;
            }
            if (!player.hasPermission("miningplus.commissions")) {
                service.send(player, "no-permission", Map.of());
                return;
            }
            gui.openCommissions(player);
            return;
        }
        if (gui.isMainSlot("shop", slot)) {
            if (!service.config().currencyEnabled()) {
                service.send(player, "currency-disabled", Map.of());
                return;
            }
            if (!service.config().shopEnabled()) {
                service.send(player, "shop-disabled", Map.of());
                return;
            }
            if (!player.hasPermission("miningplus.shop")) {
                service.send(player, "no-permission", Map.of());
                return;
            }
            gui.openShop(player);
        }
    }

    private void handlePickaxe(int slot, Player player, Inventory inventory) {
        if (!service.config().pickaxeProgressionEnabled()
                || !player.hasPermission(service.config().pickaxeProgression().permission())) {
            service.send(player, "pickaxe-disabled", Map.of());
            player.closeInventory();
            return;
        }
        if (gui.isPickaxeBackSlot(slot)) {
            gui.open(player);
            return;
        }
        if (!gui.isPickaxeRefineSlot(slot)) {
            return;
        }
        PickaxeRefineResult result = service.refinePickaxe(player);
        announcePickaxeRefine(player, result);
        gui.renderPickaxe(player, inventory);
    }

    private void handleJournal(int slot, Player player, Inventory inventory) {
        if (!service.config().journalEnabled() || !player.hasPermission("miningplus.journal")) {
            service.send(player, "no-permission", Map.of());
            player.closeInventory();
            return;
        }
        if (gui.isJournalBackSlot(slot)) {
            gui.open(player);
            return;
        }
        String chapterId = gui.journalChapterAtSlot(slot);
        if (chapterId == null) {
            return;
        }
        JournalClaimResult result = service.claimJournalChapter(player, chapterId);
        announceJournal(player, result);
        gui.renderJournal(player, inventory);
    }

    private void handleCommissions(int slot, Player player, Inventory inventory) {
        if (!service.config().commissionsEnabled()) {
            service.send(player, "commissions-disabled", Map.of());
            player.closeInventory();
            return;
        }
        if (!player.hasPermission("miningplus.commissions")) {
            service.send(player, "no-permission", Map.of());
            player.closeInventory();
            return;
        }
        if (gui.isCommissionsBackSlot(slot)) {
            gui.open(player);
            return;
        }
        String commissionId = gui.commissionAtSlot(slot);
        if (commissionId == null) {
            return;
        }
        PlayerData data = service.players().getOrCreate(player.getUniqueId(), player.getName());
        if (data.hasActiveCommission(commissionId)) {
            announceCommissionClaim(player, service.claimCommission(player, commissionId));
        } else {
            announceCommissionAccept(player, service.acceptCommission(player, commissionId));
        }
        gui.renderCommissions(player, inventory);
    }

    private void handleShop(int slot, Player player, Inventory inventory) {
        if (!service.config().currencyEnabled()) {
            service.send(player, "currency-disabled", Map.of());
            player.closeInventory();
            return;
        }
        if (!service.config().shopEnabled()) {
            service.send(player, "shop-disabled", Map.of());
            player.closeInventory();
            return;
        }
        if (!player.hasPermission("miningplus.shop")) {
            service.send(player, "no-permission", Map.of());
            player.closeInventory();
            return;
        }
        if (gui.isShopBackSlot(slot)) {
            gui.open(player);
            return;
        }
        String itemId = gui.shopItemAtSlot(slot);
        if (itemId == null) {
            return;
        }
        ShopPurchaseResult result = service.purchaseShopItem(player, itemId);
        announceShop(player, result);
        gui.renderShop(player, inventory);
    }

    private void handlePerks(int slot, Player player, Inventory inventory) {
        if (!service.config().perksEnabled() || !player.hasPermission("miningplus.perks")) {
            service.send(player, "no-permission", Map.of());
            player.closeInventory();
            return;
        }
        if (gui.isPerksBackSlot(slot)) {
            gui.open(player);
            return;
        }
        String perkId = gui.perkIdAtSlot(slot);
        if (perkId == null) {
            return;
        }
        PerkPurchaseResult result = service.purchasePerk(player, perkId);
        announcePerk(player, result, perkId);
        gui.renderPerks(player, inventory);
    }

    private void announceJournal(Player player, JournalClaimResult result) {
        service.send(player, result.messageKey(), Map.of(
                "chapter", result.chapter() == null ? "" : result.chapter().displayName(),
                "detail", result.detail(),
                "xp", NumberFormat.decimal(result.xp()),
                "money", service.formatMoney(result.money()),
                "points", NumberFormat.integer(result.points()),
                "shards", service.config().formatShards(result.shards()),
                "perk_points", NumberFormat.integer(result.perkPoints()),
                "items", NumberFormat.integer(result.itemStacks())
        ));
    }

    private void announceCommissionAccept(Player player, CommissionActionResult result) {
        switch (result.status()) {
            case SUCCESS -> service.send(player, "commission-accepted", Map.of(
                    "commission", result.commission().displayName()
            ));
            case DISABLED -> service.send(player, "commissions-disabled", Map.of());
            case INVALID -> service.send(player, "commission-invalid", Map.of());
            case LOCKED_LEVEL -> service.send(player, "commission-locked", Map.of(
                    "commission", result.commission().displayName(),
                    "requirement", "Level " + result.detail()
            ));
            case LOCKED_CHAPTER -> service.send(player, "commission-locked", Map.of(
                    "commission", result.commission().displayName(),
                    "requirement", result.detail()
            ));
            case ACTIVE_LIMIT -> service.send(player, "commission-active-limit", Map.of("max", result.detail()));
            case ALREADY_ACTIVE -> service.send(player, "commission-already-active", Map.of(
                    "commission", result.commission().displayName()
            ));
            default -> service.send(player, "commission-usage", Map.of());
        }
    }

    private void announceCommissionClaim(Player player, CommissionActionResult result) {
        switch (result.status()) {
            case SUCCESS -> service.send(player, "commission-claimed", Map.of(
                    "commission", result.commission().displayName(),
                    "xp", NumberFormat.decimal(result.xp()),
                    "pickaxe_xp", NumberFormat.decimal(result.pickaxeXp()),
                    "money", service.formatMoney(result.money()),
                    "points", NumberFormat.integer(result.points()),
                    "shards", service.config().formatShards(result.shards()),
                    "perk_points", NumberFormat.integer(result.perkPoints()),
                    "items", NumberFormat.integer(result.itemStacks()),
                    "upgrades", NumberFormat.integer(result.toolUpgrades())
            ));
            case DISABLED -> service.send(player, "commissions-disabled", Map.of());
            case INVALID -> service.send(player, "commission-invalid", Map.of());
            case NOT_ACTIVE -> service.send(player, "commission-not-active", Map.of(
                    "commission", result.commission() == null ? "" : result.commission().displayName()
            ));
            case INCOMPLETE -> service.send(player, "commission-incomplete", Map.of(
                    "commission", result.commission().displayName(),
                    "detail", result.detail()
            ));
            case NO_PICKAXE -> service.send(player, "commission-no-pickaxe", Map.of(
                    "commission", result.commission().displayName()
            ));
            default -> service.send(player, "commission-usage", Map.of());
        }
    }

    private void announceShop(Player player, ShopPurchaseResult result) {
        String itemName = result.item() == null ? "" : result.item().displayName();
        String detail = result.item() == null ? "" : service.shopLockedDetail(
                service.players().getOrCreate(player.getUniqueId(), player.getName()), result.item());
        switch (result.status()) {
            case SUCCESS -> service.send(player, "shop-purchased", Map.of(
                    "item", itemName,
                    "cost", service.config().formatShards(result.cost())
            ));
            case DISABLED -> service.send(player, "shop-disabled", Map.of());
            case INVALID -> service.send(player, "shop-invalid", Map.of("item", itemName));
            case LOCKED_LEVEL, LOCKED_CHAPTER -> service.send(player, "shop-locked", Map.of(
                    "item", itemName,
                    "requirement", detail
            ));
            case INSUFFICIENT_SHARDS -> service.send(player, "shop-insufficient", Map.of(
                    "item", itemName,
                    "cost", service.config().formatShards(result.cost()),
                    "balance", service.config().formatShards(
                            service.players().getOrCreate(player.getUniqueId(), player.getName()).shards())
            ));
            case NO_PICKAXE -> service.send(player, "shop-no-pickaxe", Map.of("item", itemName));
            case PICKAXE_MAXED -> service.send(player, "shop-pickaxe-maxed", Map.of("item", itemName));
            case UPGRADE_ALREADY_OWNED -> service.send(player, "shop-upgrade-owned", Map.of("item", itemName));
        }
    }

    private void announcePickaxeRefine(Player player, PickaxeRefineResult result) {
        switch (result.status()) {
            case SUCCESS -> service.send(player, "pickaxe-refined", Map.of(
                    "level", String.valueOf(result.level()),
                    "xp", NumberFormat.decimal(result.xp()),
                    "cost", service.formatMoney(result.moneyCost()),
                    "shards", service.config().formatShards(result.shardCost()),
                    "gained", NumberFormat.decimal(result.xpGained())
            ));
            case DISABLED -> service.send(player, "pickaxe-disabled", Map.of());
            case NO_PICKAXE -> service.send(player, "pickaxe-not-held", Map.of());
            case MAX_LEVEL -> service.send(player, "pickaxe-maxed", Map.of("level", String.valueOf(result.level())));
            case NO_ECONOMY -> service.send(player, "pickaxe-refine-no-economy", Map.of());
            case INSUFFICIENT_MONEY -> service.send(player, "pickaxe-refine-money", Map.of(
                    "cost", service.formatMoney(result.moneyCost()),
                    "balance", service.formatMoney(service.economy().balance(player))
            ));
            case INSUFFICIENT_SHARDS -> service.send(player, "pickaxe-refine-shards", Map.of(
                    "cost", service.config().formatShards(result.shardCost()),
                    "balance", service.config().formatShards(
                            service.players().getOrCreate(player.getUniqueId(), player.getName()).shards())
            ));
        }
    }

    private void announcePerk(Player player, PerkPurchaseResult result, String requested) {
        switch (result.status()) {
            case SUCCESS -> service.send(player, "perk-purchased", Map.of(
                    "perk", result.definition().displayName(),
                    "level", String.valueOf(result.level()),
                    "cost", NumberFormat.integer(result.cost())
            ));
            case DISABLED, NO_PERMISSION -> service.send(player, "no-permission", Map.of());
            case INVALID -> service.send(player, "perk-invalid", Map.of("perk", requested));
            case LOCKED -> service.send(player, "perk-locked", Map.of(
                    "perk", result.definition().displayName(),
                    "level", String.valueOf(result.definition().unlockLevel())
            ));
            case MAXED -> service.send(player, "perk-maxed", Map.of("perk", result.definition().displayName()));
            case INSUFFICIENT_POINTS -> service.send(player, "perk-insufficient", Map.of(
                    "perk", result.definition().displayName(),
                    "cost", NumberFormat.integer(result.cost())
            ));
        }
    }
}
