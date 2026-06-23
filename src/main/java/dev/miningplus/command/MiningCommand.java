package dev.miningplus.command;

import dev.miningplus.data.PlayerData;
import dev.miningplus.data.PlayerCommission;
import dev.miningplus.gui.MiningGui;
import dev.miningplus.mining.ArtifactDefinition;
import dev.miningplus.mining.ArtifactResearchDefinition;
import dev.miningplus.mining.ArtifactResearchResult;
import dev.miningplus.mining.ArtifactSalvageResult;
import dev.miningplus.mining.ArtifactSetDefinition;
import dev.miningplus.mining.CommissionActionResult;
import dev.miningplus.mining.CommissionDefinition;
import dev.miningplus.mining.JournalChapter;
import dev.miningplus.mining.JournalClaimResult;
import dev.miningplus.mining.JournalObjective;
import dev.miningplus.mining.LevelCurve;
import dev.miningplus.mining.MiningShopItem;
import dev.miningplus.mining.MiningService;
import dev.miningplus.mining.PickaxeRefineResult;
import dev.miningplus.mining.PerkDefinition;
import dev.miningplus.mining.PerkPurchaseResult;
import dev.miningplus.mining.ShopPurchaseResult;
import dev.miningplus.util.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class MiningCommand implements CommandExecutor, TabCompleter {
    private final MiningService service;
    private final MiningGui gui;

    public MiningCommand(MiningService service, MiningGui gui) {
        this.service = service;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("miningplus.use")) {
            service.send(sender, "no-permission", Map.of());
            return true;
        }
        String sub = args.length == 0
                ? (service.config().guiEnabled() ? "menu" : "stats")
                : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu" -> openMenu(sender);
            case "stats" -> stats(sender);
            case "progress" -> progress(sender);
            case "top" -> top(sender);
            case "veinminer", "vein", "vm" -> toggleVeinMiner(sender);
            case "notifications" -> toggleNotifications(sender);
            case "sell" -> sell(sender, args);
            case "perks" -> perks(sender);
            case "perk" -> buyPerk(sender, args);
            case "journal", "story", "quest", "quests" -> journal(sender, args);
            case "commission", "commissions", "mission", "missions" -> commissions(sender, args);
            case "shop" -> shop(sender, args);
            case "shards", "currency" -> shards(sender);
            case "artifacts", "relics" -> artifacts(sender, args);
            case "pickaxe" -> pickaxe(sender, args);
            case "help" -> service.send(sender, "help", Map.of());
            default -> service.send(sender, "help", Map.of());
        }
        return true;
    }

    private void openMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", Map.of());
            return;
        }
        if (!service.config().guiEnabled()) {
            stats(sender);
            return;
        }
        gui.open(player);
    }

    private void sell(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", Map.of());
            return;
        }
        if (!service.config().sellEnabled()) {
            service.send(player, "sell-disabled", Map.of());
            return;
        }
        if (!player.hasPermission("miningplus.sell")) {
            service.send(player, "no-permission", Map.of());
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE) {
            service.send(player, "sell-creative", Map.of());
            return;
        }
        String mode = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "all";
        if (!mode.equals("all") && !mode.equals("hand")) {
            service.send(player, "help", Map.of());
            return;
        }
        boolean handOnly = mode.equals("hand");
        double earned = service.sell(player, handOnly);
        if (earned < 0.0D) {
            service.send(player, "sell-unavailable", Map.of());
            return;
        }
        if (earned == 0.0D) {
            service.send(player, "sell-nothing", Map.of());
            return;
        }
        service.send(player, "sell-success", Map.of("money", service.formatMoney(earned)));
    }

    private void toggleNotifications(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", Map.of());
            return;
        }
        boolean enabled = service.players().getOrCreate(player.getUniqueId(), player.getName()).toggleNotifications();
        service.send(player, enabled ? "notifications-on" : "notifications-off", Map.of());
    }

    private void toggleVeinMiner(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", Map.of());
            return;
        }
        if (!service.config().veinMinerEnabled()) {
            service.send(player, "veinminer-disabled", Map.of());
            return;
        }
        if (!player.hasPermission("miningplus.veinminer")) {
            service.send(player, "no-permission", Map.of());
            return;
        }
        boolean enabled = service.players().getOrCreate(player.getUniqueId(), player.getName()).toggleVeinMiner();
        service.send(player, enabled ? "veinminer-on" : "veinminer-off", Map.of());
    }

    private void perks(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", Map.of());
            return;
        }
        if (!service.config().perksEnabled() || !player.hasPermission("miningplus.perks")) {
            service.send(player, "no-permission", Map.of());
            return;
        }
        PlayerData data = service.players().getOrCreate(player.getUniqueId(), player.getName());
        service.syncPerkPoints(data);
        service.send(player, "perks-header", Map.of("points", NumberFormat.integer(data.perkPoints())));
        for (PerkDefinition definition : service.config().perks().definitions()) {
            if (!definition.active()) {
                continue;
            }
            int level = data.perkLevel(definition.id());
            String cost = level >= definition.maxLevel()
                    ? "MAX"
                    : NumberFormat.integer(definition.costForNextLevel(level));
            service.sendRaw(player, service.config().prefix()
                    + service.config().message("perks-entry")
                    .replace("{id}", definition.id())
                    .replace("{name}", definition.displayName())
                    .replace("{level}", String.valueOf(level))
                    .replace("{max_level}", String.valueOf(definition.maxLevel()))
                    .replace("{cost}", cost)
                    .replace("{unlock_level}", String.valueOf(definition.unlockLevel())));
        }
    }

    private void buyPerk(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", Map.of());
            return;
        }
        if (args.length != 2) {
            service.send(player, "perks-usage", Map.of());
            return;
        }
        PerkPurchaseResult result = service.purchasePerk(player, args[1]);
        switch (result.status()) {
            case SUCCESS -> service.send(player, "perk-purchased", Map.of(
                    "perk", result.definition().displayName(),
                    "level", String.valueOf(result.level()),
                    "cost", NumberFormat.integer(result.cost())
            ));
            case DISABLED, NO_PERMISSION -> service.send(player, "no-permission", Map.of());
            case INVALID -> service.send(player, "perk-invalid", Map.of("perk", args[1]));
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

    private void journal(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", Map.of());
            return;
        }
        if (!service.config().journalEnabled()) {
            service.send(player, "journal-disabled", Map.of());
            return;
        }
        if (!player.hasPermission("miningplus.journal")) {
            service.send(player, "no-permission", Map.of());
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("claim")) {
            JournalClaimResult result = service.claimJournalChapter(player, args.length >= 3 ? args[2] : "next");
            announceJournal(player, result);
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            listJournal(player);
            return;
        }
        if (service.config().guiEnabled()) {
            gui.openJournal(player);
            return;
        }
        listJournal(player);
    }

    private void listJournal(Player player) {
        PlayerData data = service.players().getOrCreate(player.getUniqueId(), player.getName());
        if (service.config().journalChapters().isEmpty()) {
            service.send(player, "journal-empty", Map.of());
            return;
        }
        service.send(player, "journal-header", Map.of(
                "claimed", NumberFormat.integer(data.claimedJournalChapters().size()),
                "total", NumberFormat.integer(service.config().journalChapters().size())
        ));
        for (JournalChapter chapter : service.config().journalChapters()) {
            boolean claimed = data.hasClaimedJournalChapter(chapter.id());
            boolean unlocked = service.journalChapterUnlocked(data, chapter);
            boolean complete = service.journalChapterComplete(data, chapter);
            String status = claimed
                    ? service.config().guiString("status.journal.claimed")
                    : !unlocked ? service.config().guiString("status.journal.locked")
                    : complete ? service.config().guiString("status.journal.ready")
                    : service.config().guiString("status.journal.progress");
            service.send(player, "journal-entry", Map.of(
                    "id", chapter.id(),
                    "chapter", chapter.displayName(),
                    "status", status,
                    "progress", NumberFormat.decimal(service.journalProgressPercent(data, chapter)),
                    "requirement", unlocked ? guiStringOr("journal-unlocked", "Unlocked")
                            : service.journalLockedDetail(data, chapter)
            ));
        }
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

    private void commissions(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", Map.of());
            return;
        }
        if (!service.config().commissionsEnabled()) {
            service.send(player, "commissions-disabled", Map.of());
            return;
        }
        if (!player.hasPermission("miningplus.commissions")) {
            service.send(player, "no-permission", Map.of());
            return;
        }
        if (args.length >= 2) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if (action.equals("accept")) {
                if (args.length < 3) {
                    service.send(player, "commission-usage", Map.of());
                    return;
                }
                announceCommissionAccept(player, service.acceptCommission(player, args[2]));
                return;
            }
            if (action.equals("claim")) {
                if (args.length < 3) {
                    service.send(player, "commission-usage", Map.of());
                    return;
                }
                announceCommissionClaim(player, service.claimCommission(player, args[2]));
                return;
            }
            if (action.equals("abandon")) {
                if (args.length < 3) {
                    service.send(player, "commission-usage", Map.of());
                    return;
                }
                announceCommissionAbandon(player, service.abandonCommission(player, args[2]));
                return;
            }
            if (!action.equals("list")) {
                service.send(player, "commission-usage", Map.of());
                return;
            }
        }
        listCommissions(player);
    }

    private void listCommissions(Player player) {
        PlayerData data = service.players().getOrCreate(player.getUniqueId(), player.getName());
        service.send(player, "commission-header", Map.of(
                "active", NumberFormat.integer(data.activeCommissions().size()),
                "max", NumberFormat.integer(service.config().maxActiveCommissions()),
                "completed", NumberFormat.integer(data.commissionsCompleted())
        ));
        boolean anyActive = false;
        for (CommissionDefinition commission : service.config().commissions()) {
            PlayerCommission active = data.activeCommission(commission.id());
            if (active == null) {
                continue;
            }
            anyActive = true;
            JournalObjective next = service.firstIncompleteCommissionObjective(data, active, commission);
            String objective = next == null
                    ? service.config().message("commission-ready")
                    : service.commissionObjectiveLine(data, active, next);
            service.send(player, "commission-active-entry", Map.of(
                    "id", commission.id(),
                    "commission", commission.displayName(),
                    "progress", NumberFormat.decimal(service.commissionProgressPercent(data, active, commission)),
                    "objective", objective
            ));
        }
        for (String activeId : data.activeCommissions().keySet()) {
            if (service.config().commission(activeId) != null) {
                continue;
            }
            anyActive = true;
            service.send(player, "commission-active-entry", Map.of(
                    "id", activeId,
                    "commission", activeId,
                    "progress", "0",
                    "objective", service.config().message("commission-missing-active")
                            .replace("{id}", activeId)
            ));
        }
        if (!anyActive) {
            service.send(player, "commission-active-empty", Map.of());
        }
        boolean anyAvailable = false;
        for (CommissionDefinition commission : service.config().commissions()) {
            if (!commission.enabled() || data.hasActiveCommission(commission.id())) {
                continue;
            }
            anyAvailable = true;
            boolean unlocked = data.level() >= commission.unlockLevel() && service.commissionUnlocked(data, commission);
            service.send(player, "commission-available-entry", Map.of(
                    "id", commission.id(),
                    "commission", commission.displayName(),
                    "status", unlocked ? service.config().message("commission-status-available")
                            : service.config().message("commission-status-locked"),
                    "requirement", unlocked ? service.config().message("commission-unlocked")
                            : service.commissionLockedDetail(data, commission)
            ));
        }
        if (!anyAvailable) {
            service.send(player, "commission-available-empty", Map.of());
        }
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

    private void announceCommissionAbandon(Player player, CommissionActionResult result) {
        switch (result.status()) {
            case ABANDONED -> service.send(player, "commission-abandoned", Map.of(
                    "commission", commissionName(result)
            ));
            case DISABLED -> service.send(player, "commissions-disabled", Map.of());
            case INVALID -> service.send(player, "commission-invalid", Map.of());
            case NOT_ACTIVE -> service.send(player, "commission-not-active", Map.of(
                    "commission", result.commission() == null ? "" : result.commission().displayName()
            ));
            default -> service.send(player, "commission-usage", Map.of());
        }
    }

    private String commissionName(CommissionActionResult result) {
        if (result.commission() != null) {
            return result.commission().displayName();
        }
        return result.detail().isBlank() ? "unknown" : result.detail();
    }

    private void shop(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", Map.of());
            return;
        }
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
        if (args.length >= 2 && args[1].equalsIgnoreCase("buy")) {
            if (args.length < 3) {
                service.send(player, "shop-usage", Map.of());
                return;
            }
            announceShop(player, service.purchaseShopItem(player, args[2]));
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            listShop(player);
            return;
        }
        if (service.config().guiEnabled()) {
            gui.openShop(player);
            return;
        }
        listShop(player);
    }

    private void listShop(Player player) {
        PlayerData data = service.players().getOrCreate(player.getUniqueId(), player.getName());
        service.send(player, "shop-header", Map.of("balance", service.config().formatShards(data.shards())));
        boolean any = false;
        for (MiningShopItem item : service.config().shopItems()) {
            if (!item.enabled()) {
                continue;
            }
            any = true;
            boolean unlocked = data.level() >= item.unlockLevel() && service.shopItemUnlocked(data, item);
            boolean affordable = data.shards() >= item.cost();
            service.send(player, "shop-entry", Map.of(
                    "id", item.id(),
                    "item", item.displayName(),
                    "cost", service.config().formatShards(item.cost()),
                    "status", !unlocked ? service.config().guiString("status.shop.locked")
                            : affordable ? service.config().guiString("status.shop.available")
                            : service.config().guiString("status.shop.expensive"),
                    "requirement", unlocked ? guiStringOr("shop-unlocked", "Unlocked")
                            : service.shopLockedDetail(data, item)
            ));
        }
        if (!any) {
            service.send(player, "shop-empty", Map.of());
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

    private void shards(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", Map.of());
            return;
        }
        if (!service.config().currencyEnabled()) {
            service.send(player, "currency-disabled", Map.of());
            return;
        }
        PlayerData data = service.players().getOrCreate(player.getUniqueId(), player.getName());
        service.send(player, "shards-balance", Map.of(
                "balance", service.config().formatShards(data.shards()),
                "earned", NumberFormat.integer(data.shardsEarned()),
                "spent", NumberFormat.integer(data.shardsSpent())
        ));
    }

    private void artifacts(CommandSender sender, String[] args) {
        if (!service.config().artifactsEnabled()) {
            service.send(sender, "artifacts-disabled", Map.of());
            return;
        }
        if (sender instanceof Player player && !player.hasPermission("miningplus.artifacts")) {
            service.send(player, "no-permission", Map.of());
            return;
        }
        if (args.length >= 2) {
            String mode = args[1].toLowerCase(Locale.ROOT);
            if (mode.equals("sets") || mode.equals("codex")) {
                listArtifactSets(sender);
                return;
            }
            if (mode.equals("research")) {
                artifactResearch(sender, args);
                return;
            }
            if (mode.equals("salvage")) {
                salvageArtifacts(sender, args);
                return;
            }
        }
        PlayerData data = sender instanceof Player player
                ? service.players().getOrCreate(player.getUniqueId(), player.getName())
                : null;
        service.send(sender, "artifacts-header", Map.of(
                "count", NumberFormat.integer(service.config().artifacts().size()),
                "fragments", NumberFormat.integer(data == null ? 0L : data.artifactFragments()),
                "sets", NumberFormat.integer(data == null ? 0 : service.completedArtifactSetIds(data).size()),
                "set_total", NumberFormat.integer(service.config().artifactSets().size())
        ));
        boolean any = false;
        for (ArtifactDefinition artifact : service.config().artifacts()) {
            if (!artifact.enabled()) {
                continue;
            }
            any = true;
            String found = data == null
                    ? "0"
                    : NumberFormat.integer(data.artifactFound(artifact.id()));
            service.send(sender, "artifacts-entry", Map.of(
                    "id", artifact.id(),
                    "artifact", artifact.displayName(),
                    "level", String.valueOf(artifact.unlockLevel()),
                    "chance", NumberFormat.decimal(artifact.chance() * 100.0D),
                    "found", found,
                    "set", artifact.setId().isBlank() ? "None" : artifact.setId(),
                    "fragments", NumberFormat.integer(artifact.fragmentValue())
            ));
        }
        if (!any) {
            service.send(sender, "artifacts-empty", Map.of());
        }
    }

    private void listArtifactSets(CommandSender sender) {
        PlayerData data = sender instanceof Player player
                ? service.players().getOrCreate(player.getUniqueId(), player.getName())
                : null;
        service.send(sender, "artifact-sets-header", Map.of(
                "count", NumberFormat.integer(service.config().artifactSets().size()),
                "completed", NumberFormat.integer(data == null ? 0 : service.completedArtifactSetIds(data).size())
        ));
        boolean any = false;
        for (ArtifactSetDefinition set : service.config().artifactSets()) {
            if (!set.enabled()) {
                continue;
            }
            any = true;
            int found = data == null ? 0 : service.artifactSetProgress(data, set);
            boolean complete = data != null && service.artifactSetComplete(data, set);
            service.send(sender, "artifact-set-entry", Map.of(
                    "id", set.id(),
                    "set", set.displayName(),
                    "found", NumberFormat.integer(found),
                    "required", NumberFormat.integer(set.artifactIds().size()),
                    "status", complete ? service.config().message("artifact-set-complete-status")
                            : service.config().message("artifact-set-progress-status")
            ));
        }
        if (!any) {
            service.send(sender, "artifact-sets-empty", Map.of());
        }
    }

    private void artifactResearch(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", Map.of());
            return;
        }
        if (args.length < 3 || args[2].equalsIgnoreCase("list")) {
            listArtifactResearch(player);
            return;
        }
        announceArtifactResearch(player, service.completeArtifactResearch(player, args[2]));
    }

    private void listArtifactResearch(Player player) {
        PlayerData data = service.players().getOrCreate(player.getUniqueId(), player.getName());
        service.send(player, "artifact-research-header", Map.of(
                "fragments", NumberFormat.integer(data.artifactFragments())
        ));
        boolean any = false;
        for (ArtifactResearchDefinition research : service.config().artifactResearch()) {
            if (!research.enabled()) {
                continue;
            }
            any = true;
            service.send(player, "artifact-research-entry", Map.of(
                    "id", research.id(),
                    "research", research.displayName(),
                    "cost", NumberFormat.integer(research.fragmentCost()),
                    "status", artifactResearchStatus(data, research)
            ));
        }
        if (!any) {
            service.send(player, "artifact-research-empty", Map.of());
        }
    }

    private String artifactResearchStatus(PlayerData data, ArtifactResearchDefinition research) {
        if (data.level() < research.unlockLevel()) {
            return service.config().message("artifact-research-locked-level")
                    .replace("{level}", String.valueOf(research.unlockLevel()));
        }
        for (String setId : research.requiredSets()) {
            ArtifactSetDefinition set = service.config().artifactSet(setId);
            if (!service.artifactSetComplete(data, set)) {
                return service.config().message("artifact-research-locked-set")
                        .replace("{set}", set == null ? setId : set.displayName());
            }
        }
        if (data.artifactFragments() < research.fragmentCost()) {
            return service.config().message("artifact-research-need-fragments");
        }
        return service.config().message("artifact-research-ready");
    }

    private void announceArtifactResearch(Player player, ArtifactResearchResult result) {
        switch (result.status()) {
            case SUCCESS -> service.send(player, "artifact-research-complete", Map.of(
                    "research", result.research().displayName(),
                    "cost", NumberFormat.integer(result.cost()),
                    "xp", NumberFormat.decimal(result.xpAwarded()),
                    "pickaxe_xp", NumberFormat.decimal(result.pickaxeXpAwarded()),
                    "money", service.formatMoney(result.moneyAwarded()),
                    "points", NumberFormat.integer(result.pointsAwarded()),
                    "shards", service.config().formatShards(result.shardsAwarded()),
                    "items", NumberFormat.integer(result.itemStacksAwarded())
            ));
            case DISABLED -> service.send(player, "artifacts-disabled", Map.of());
            case INVALID -> service.send(player, "artifact-research-invalid", Map.of());
            case LOCKED_LEVEL -> service.send(player, "artifact-research-locked", Map.of(
                    "research", result.research().displayName(),
                    "detail", service.config().message("artifact-research-locked-level")
                            .replace("{level}", String.valueOf(result.research().unlockLevel()))
            ));
            case LOCKED_SET -> service.send(player, "artifact-research-locked", Map.of(
                    "research", result.research().displayName(),
                    "detail", service.config().message("artifact-research-locked-set")
                            .replace("{set}", missingResearchSet(player, result.research()))
            ));
            case INSUFFICIENT_FRAGMENTS -> service.send(player, "artifact-research-fragments", Map.of(
                    "research", result.research().displayName(),
                    "cost", NumberFormat.integer(result.cost()),
                    "balance", NumberFormat.integer(service.players()
                            .getOrCreate(player.getUniqueId(), player.getName()).artifactFragments())
            ));
            case NO_PICKAXE -> service.send(player, "artifact-research-no-pickaxe", Map.of());
            case PICKAXE_MAXED -> service.send(player, "artifact-research-pickaxe-maxed", Map.of());
        }
    }

    private String missingResearchSet(Player player, ArtifactResearchDefinition research) {
        PlayerData data = service.players().getOrCreate(player.getUniqueId(), player.getName());
        for (String setId : research.requiredSets()) {
            ArtifactSetDefinition set = service.config().artifactSet(setId);
            if (!service.artifactSetComplete(data, set)) {
                return set == null ? setId : set.displayName();
            }
        }
        return "required set";
    }

    private void salvageArtifacts(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", Map.of());
            return;
        }
        boolean handOnly = args.length >= 3 && args[2].equalsIgnoreCase("hand");
        ArtifactSalvageResult result = service.salvageArtifacts(player, handOnly);
        switch (result.status()) {
            case SUCCESS -> service.send(player, "artifact-salvaged", Map.of(
                    "items", NumberFormat.integer(result.artifactsSalvaged()),
                    "fragments", NumberFormat.integer(result.fragmentsAwarded()),
                    "balance", NumberFormat.integer(service.players()
                            .getOrCreate(player.getUniqueId(), player.getName()).artifactFragments())
            ));
            case DISABLED -> service.send(player, "artifacts-disabled", Map.of());
            case NO_ARTIFACTS -> service.send(player, "artifact-salvage-empty", Map.of());
        }
    }

    private void pickaxe(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", Map.of());
            return;
        }
        if (!service.config().pickaxeProgressionEnabled()
                || !player.hasPermission(service.config().pickaxeProgression().permission())) {
            service.send(player, "pickaxe-disabled", Map.of());
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("refine")) {
            announcePickaxeRefine(player, service.refinePickaxe(player));
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!service.isPickaxe(item)) {
            service.send(player, "pickaxe-not-held", Map.of());
            return;
        }
        String required = service.pickaxeXpRequired(item) <= 0.0D
                ? "MAX"
                : NumberFormat.decimal(service.pickaxeXpRequired(item));
        service.send(player, "pickaxe-stats", Map.of(
                "level", String.valueOf(service.pickaxeLevel(item)),
                "xp", NumberFormat.decimal(service.pickaxeXp(item)),
                "xp_required", required,
                "progress", NumberFormat.decimal(service.pickaxeProgressPercent(item)),
                "xp_bonus", NumberFormat.decimal(service.pickaxeMiningXpBonusPercent(item)),
                "artifact_bonus", NumberFormat.decimal(service.pickaxeArtifactBonusPercent(item))
        ));
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

    private void progress(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", Map.of());
            return;
        }
        PlayerData data = service.players().getOrCreate(player.getUniqueId(), player.getName());
        service.syncPerkPoints(data);
        LevelCurve curve = service.config().levelCurve();
        String required = curve.atMaxLevel(data.level())
                ? "MAX"
                : NumberFormat.decimal(curve.xpToAdvance(data.level()));
        JournalChapter next = service.nextJournalChapter(data);
        String chapter = next == null ? guiStringOr("journal-complete", "Complete") : next.displayName();
        String objective = next == null
                ? guiStringOr("journal-complete", "Complete")
                : !service.journalChapterUnlocked(data, next)
                ? service.journalLockedDetail(data, next)
                : nextObjectiveLine(data, next);
        ItemStack pickaxe = player.getInventory().getItemInMainHand();
        String pickaxeLevel = service.isPickaxe(pickaxe) ? String.valueOf(service.pickaxeLevel(pickaxe)) : "None";
        String pickaxeProgress = service.isPickaxe(pickaxe)
                ? NumberFormat.decimal(service.pickaxeProgressPercent(pickaxe)) + "%"
                : "N/A";
        ActiveCommissionView commissionView = nextActiveCommission(data);
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("level", String.valueOf(data.level()));
        placeholders.put("rank", service.config().rankFor(data.level()));
        placeholders.put("xp", NumberFormat.decimal(data.xp()));
        placeholders.put("xp_required", required);
        placeholders.put("progress", NumberFormat.decimal(service.progressPercent(data)));
        placeholders.put("pickaxe_level", pickaxeLevel);
        placeholders.put("pickaxe_progress", pickaxeProgress);
        placeholders.put("best_pickaxe_level", String.valueOf(data.bestPickaxeLevel()));
        placeholders.put("pickaxe_refines", NumberFormat.integer(data.pickaxeRefines()));
        placeholders.put("tool_upgrades", NumberFormat.integer(data.toolUpgradesFound()));
        placeholders.put("shards", service.config().formatShards(data.shards()));
        placeholders.put("money_earned", service.formatMoney(data.moneyEarned()));
        placeholders.put("artifacts", NumberFormat.integer(data.artifactsFound()));
        placeholders.put("unique_artifacts", NumberFormat.integer(data.uniqueArtifactsFound()));
        placeholders.put("artifact_total", NumberFormat.integer(service.config().artifacts().size()));
        placeholders.put("artifact_fragments", NumberFormat.integer(data.artifactFragments()));
        placeholders.put("artifact_sets", NumberFormat.integer(service.completedArtifactSetIds(data).size()));
        placeholders.put("artifact_set_total", NumberFormat.integer(service.config().artifactSets().size()));
        placeholders.put("event_challenges", NumberFormat.integer(data.eventChallengesCompleted()));
        placeholders.put("encounters", NumberFormat.integer(data.encountersDefeated()));
        placeholders.put("journal_chapter", chapter);
        placeholders.put("journal_objective", objective);
        placeholders.put("journal_claimable", NumberFormat.integer(service.claimableJournalChapters(data)));
        placeholders.put("commission", commissionView.name());
        placeholders.put("commission_objective", commissionView.objective());
        placeholders.put("commission_progress", commissionView.progress());
        placeholders.put("commissions_active", NumberFormat.integer(data.activeCommissions().size()));
        placeholders.put("commissions_completed", NumberFormat.integer(data.commissionsCompleted()));
        service.send(player, "progress", placeholders);
    }

    private ActiveCommissionView nextActiveCommission(PlayerData data) {
        for (CommissionDefinition commission : service.config().commissions()) {
            PlayerCommission active = data.activeCommission(commission.id());
            if (active == null) {
                continue;
            }
            JournalObjective objective = service.firstIncompleteCommissionObjective(data, active, commission);
            String objectiveLine = objective == null
                    ? service.config().message("commission-ready")
                    : service.commissionObjectiveLine(data, active, objective);
            return new ActiveCommissionView(
                    commission.displayName(),
                    objectiveLine,
                    NumberFormat.decimal(service.commissionProgressPercent(data, active, commission))
            );
        }
        for (String activeId : data.activeCommissions().keySet()) {
            if (service.config().commission(activeId) != null) {
                continue;
            }
            return new ActiveCommissionView(
                    activeId,
                    service.config().message("commission-missing-active").replace("{id}", activeId),
                    "0"
            );
        }
        return new ActiveCommissionView(
                service.config().message("commission-none-active"),
                service.config().message("commission-none-active-detail"),
                "0"
        );
    }

    private record ActiveCommissionView(String name, String objective, String progress) {
    }

    private String nextObjectiveLine(PlayerData data, JournalChapter chapter) {
        if (data.hasClaimedJournalChapter(chapter.id())) {
            return guiStringOr("journal-complete", "Complete");
        }
        JournalObjective objective = service.firstIncompleteJournalObjective(data, chapter);
        return objective == null ? service.config().message("journal-none-claimable")
                : service.journalObjectiveLine(data, objective);
    }

    private void stats(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", Map.of());
            return;
        }
        PlayerData data = service.players().getOrCreate(player.getUniqueId(), player.getName());
        service.syncPerkPoints(data);
        LevelCurve curve = service.config().levelCurve();
        String required = curve.atMaxLevel(data.level())
                ? "MAX"
                : NumberFormat.decimal(curve.xpToAdvance(data.level()));
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("level", String.valueOf(data.level()));
        placeholders.put("rank", service.config().rankFor(data.level()));
        placeholders.put("xp", NumberFormat.decimal(data.xp()));
        placeholders.put("xp_required", required);
        placeholders.put("progress", NumberFormat.decimal(service.progressPercent(data)));
        placeholders.put("blocks", NumberFormat.integer(data.blocksMined()));
        placeholders.put("perk_points", NumberFormat.integer(data.perkPoints()));
        placeholders.put("sold", NumberFormat.integer(data.itemsSold()));
        placeholders.put("points_earned", NumberFormat.integer(data.pointsEarned()));
        placeholders.put("money_earned", service.formatMoney(data.moneyEarned()));
        placeholders.put("treasures", NumberFormat.integer(data.treasuresFound()));
        placeholders.put("hazards", NumberFormat.integer(data.hazardsSurvived()));
        placeholders.put("artifacts", NumberFormat.integer(data.artifactsFound()));
        placeholders.put("artifact_fragments", NumberFormat.integer(data.artifactFragments()));
        placeholders.put("artifact_sets", NumberFormat.integer(service.completedArtifactSetIds(data).size()));
        placeholders.put("artifact_set_total", NumberFormat.integer(service.config().artifactSets().size()));
        placeholders.put("event_challenges", NumberFormat.integer(data.eventChallengesCompleted()));
        placeholders.put("encounters", NumberFormat.integer(data.encountersDefeated()));
        placeholders.put("perks_purchased", NumberFormat.integer(data.perksPurchased()));
        placeholders.put("shards", service.config().formatShards(data.shards()));
        placeholders.put("shards_earned", NumberFormat.integer(data.shardsEarned()));
        placeholders.put("shards_spent", NumberFormat.integer(data.shardsSpent()));
        placeholders.put("best_pickaxe_level", String.valueOf(data.bestPickaxeLevel()));
        placeholders.put("pickaxe_refines", NumberFormat.integer(data.pickaxeRefines()));
        placeholders.put("tool_upgrades", NumberFormat.integer(data.toolUpgradesFound()));
        placeholders.put("commissions_active", NumberFormat.integer(data.activeCommissions().size()));
        placeholders.put("commissions_completed", NumberFormat.integer(data.commissionsCompleted()));
        placeholders.put("journal_claimed", NumberFormat.integer(data.claimedJournalChapters().size()));
        placeholders.put("journal_total", NumberFormat.integer(service.config().journalChapters().size()));
        placeholders.put("journal_claimable", NumberFormat.integer(service.claimableJournalChapters(data)));
        service.send(player, "stats", placeholders);
    }

    private void top(CommandSender sender) {
        List<PlayerData> top = service.players().topByLevel(service.config().leaderboardSize());
        service.send(sender, "top-header", Map.of());
        int rank = 1;
        for (PlayerData data : top) {
            service.sendRaw(sender, service.config().prefix()
                    + service.config().message("top-entry")
                    .replace("{position}", String.valueOf(rank++))
                    .replace("{player}", data.name() == null || data.name().isBlank() ? "Unknown" : data.name())
                    .replace("{level}", String.valueOf(data.level()))
                    .replace("{blocks}", NumberFormat.integer(data.blocksMined())));
        }
        if (top.isEmpty()) {
            service.send(sender, "top-empty", Map.of());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("miningplus.use")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("stats", "progress", "top", "notifications", "help"));
            if (service.config().guiEnabled()) {
                subs.add("menu");
            }
            if (service.config().sellEnabled() && sender.hasPermission("miningplus.sell")) {
                subs.add("sell");
            }
            if (service.config().veinMinerEnabled() && sender.hasPermission("miningplus.veinminer")) {
                subs.add("veinminer");
            }
            if (service.config().perksEnabled() && sender.hasPermission("miningplus.perks")) {
                subs.add("perks");
                subs.add("perk");
            }
            if (service.config().journalEnabled() && sender.hasPermission("miningplus.journal")) {
                subs.add("journal");
                subs.add("story");
                subs.add("quest");
                subs.add("quests");
            }
            if (service.config().commissionsEnabled() && sender.hasPermission("miningplus.commissions")) {
                subs.add("commissions");
                subs.add("commission");
                subs.add("missions");
                subs.add("mission");
            }
            if (service.config().currencyEnabled()) {
                subs.add("shards");
            }
            if (service.config().currencyEnabled() && service.config().shopEnabled()
                    && sender.hasPermission("miningplus.shop")) {
                subs.add("shop");
            }
            if (service.config().artifactsEnabled() && sender.hasPermission("miningplus.artifacts")) {
                subs.add("artifacts");
                subs.add("relics");
            }
            if (service.config().pickaxeProgressionEnabled()) {
                subs.add("pickaxe");
            }
            return filter(subs, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            return filter(List.of("all", "hand"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("pickaxe")) {
            return filter(List.of("refine"), args[1]);
        }
        if (args.length == 2 && isArtifactAlias(args[0])) {
            return filter(List.of("sets", "research", "salvage"), args[1]);
        }
        if (args.length == 3 && isArtifactAlias(args[0]) && args[1].equalsIgnoreCase("research")) {
            List<String> ids = new ArrayList<>();
            ids.add("list");
            for (ArtifactResearchDefinition research : service.config().artifactResearch()) {
                if (research.enabled()) {
                    ids.add(research.id());
                }
            }
            return filter(ids, args[2]);
        }
        if (args.length == 3 && isArtifactAlias(args[0]) && args[1].equalsIgnoreCase("salvage")) {
            return filter(List.of("all", "hand"), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("perk")) {
            List<String> ids = new ArrayList<>();
            for (PerkDefinition definition : service.config().perks().definitions()) {
                if (definition.active()) {
                    ids.add(definition.id());
                }
            }
            return filter(ids, args[1]);
        }
        if (args.length == 2 && isJournalAlias(args[0])) {
            return filter(List.of("claim", "list"), args[1]);
        }
        if (args.length == 3 && isJournalAlias(args[0]) && args[1].equalsIgnoreCase("claim")) {
            List<String> ids = new ArrayList<>();
            ids.add("next");
            for (JournalChapter chapter : service.config().journalChapters()) {
                ids.add(chapter.id());
            }
            return filter(ids, args[2]);
        }
        if (args.length == 2 && isCommissionAlias(args[0])) {
            return filter(List.of("list", "accept", "claim", "abandon"), args[1]);
        }
        if (args.length == 3 && isCommissionAlias(args[0])) {
            if (args[1].equalsIgnoreCase("accept")) {
                List<String> ids = new ArrayList<>();
                for (CommissionDefinition commission : service.config().commissions()) {
                    if (commission.enabled()) {
                        ids.add(commission.id());
                    }
                }
                return filter(ids, args[2]);
            }
            if (sender instanceof Player player
                    && (args[1].equalsIgnoreCase("claim") || args[1].equalsIgnoreCase("abandon"))) {
                PlayerData data = service.players().getOrCreate(player.getUniqueId(), player.getName());
                return filter(new ArrayList<>(data.activeCommissions().keySet()), args[2]);
            }
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("shop")) {
            return filter(List.of("buy", "list"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("shop") && args[1].equalsIgnoreCase("buy")) {
            List<String> ids = new ArrayList<>();
            for (MiningShopItem item : service.config().shopItems()) {
                if (item.enabled()) {
                    ids.add(item.id());
                }
            }
            return filter(ids, args[2]);
        }
        return List.of();
    }

    private boolean isJournalAlias(String value) {
        return value.equalsIgnoreCase("journal")
                || value.equalsIgnoreCase("story")
                || value.equalsIgnoreCase("quest")
                || value.equalsIgnoreCase("quests");
    }

    private boolean isCommissionAlias(String value) {
        return value.equalsIgnoreCase("commission")
                || value.equalsIgnoreCase("commissions")
                || value.equalsIgnoreCase("mission")
                || value.equalsIgnoreCase("missions");
    }

    private boolean isArtifactAlias(String value) {
        return value.equalsIgnoreCase("artifacts")
                || value.equalsIgnoreCase("relics");
    }

    private List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                result.add(value);
            }
        }
        return result;
    }

    private String guiStringOr(String key, String fallback) {
        String value = service.config().guiString(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
