package dev.miningplus.gui;

import dev.miningplus.data.PlayerCommission;
import dev.miningplus.data.PlayerData;
import dev.miningplus.mining.ArtifactDefinition;
import dev.miningplus.mining.ArtifactResearchDefinition;
import dev.miningplus.mining.ArtifactSetDefinition;
import dev.miningplus.mining.CommissionDefinition;
import dev.miningplus.mining.JournalChapter;
import dev.miningplus.mining.JournalObjective;
import dev.miningplus.mining.LevelCurve;
import dev.miningplus.mining.MiningShopItem;
import dev.miningplus.mining.MiningService;
import dev.miningplus.mining.PerkDefinition;
import dev.miningplus.util.NumberFormat;
import dev.miningplus.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class MiningGui {
    public static final int SLOT_INFO = 4;
    public static final int SLOT_PERKS = 24;
    public static final int SLOT_NOTIFICATIONS = 42;
    public static final int SLOT_LEADERBOARD = 16;
    public static final int SLOT_JOURNAL = 20;
    public static final int SLOT_SHOP = 22;
    public static final int SLOT_COMMISSIONS = 30;
    public static final int SLOT_PICKAXE = 10;
    public static final int SLOT_SELL = 12;
    public static final int SLOT_ARTIFACTS = 14;
    public static final int JOURNAL_BACK = 49;
    public static final int COMMISSIONS_BACK = 49;
    public static final int SHOP_BACK = 49;
    public static final int PICKAXE_TOOL = 11;
    public static final int PICKAXE_UPGRADES = 13;
    public static final int PICKAXE_REFINE = 15;
    public static final int PICKAXE_BACK = 22;
    public static final int PERKS_BACK = 49;
    public static final int ARTIFACTS_CODEX = 10;
    public static final int ARTIFACTS_SETS = 12;
    public static final int ARTIFACTS_RESEARCH = 14;
    public static final int ARTIFACTS_SALVAGE_ALL = 30;
    public static final int ARTIFACTS_SALVAGE_HAND = 32;
    public static final int ARTIFACTS_BACK = 49;
    public static final int ARTIFACT_SETS_BACK = 49;
    public static final int ARTIFACT_RESEARCH_BACK = 49;
    private static final int JOURNAL_CAPACITY = 45;
    private static final int COMMISSIONS_CAPACITY = 45;
    private static final int SHOP_CAPACITY = 45;
    private static final int PERKS_CAPACITY = 45;
    private static final int ARTIFACT_CAPACITY = 36;
    private static final int ARTIFACT_SET_CAPACITY = 45;
    private static final int ARTIFACT_RESEARCH_CAPACITY = 45;

    private final MiningService service;

    public MiningGui(MiningService service) {
        this.service = service;
    }

    public void open(Player player) {
        MiningMenuHolder holder = new MiningMenuHolder(MiningMenuHolder.MenuType.MAIN);
        Inventory inventory = Bukkit.createInventory(holder, guiSize("main", 45), Text.component(service.config().guiTitle()));
        holder.inventory(inventory);
        render(player, inventory);
        player.openInventory(inventory);
    }

    public void openJournal(Player player) {
        MiningMenuHolder holder = new MiningMenuHolder(MiningMenuHolder.MenuType.JOURNAL);
        Inventory inventory = Bukkit.createInventory(holder, guiSize("journal", 54),
                Text.component(service.config().guiString("journal-title")));
        holder.inventory(inventory);
        renderJournal(player, inventory);
        player.openInventory(inventory);
    }

    public void openCommissions(Player player) {
        MiningMenuHolder holder = new MiningMenuHolder(MiningMenuHolder.MenuType.COMMISSIONS);
        Inventory inventory = Bukkit.createInventory(holder, guiSize("commissions", 54),
                Text.component(service.config().guiString("commissions-title")));
        holder.inventory(inventory);
        renderCommissions(player, inventory);
        player.openInventory(inventory);
    }

    public void openShop(Player player) {
        MiningMenuHolder holder = new MiningMenuHolder(MiningMenuHolder.MenuType.SHOP);
        Inventory inventory = Bukkit.createInventory(holder, guiSize("shop", 54),
                Text.component(service.config().guiString("shop-title")));
        holder.inventory(inventory);
        renderShop(player, inventory);
        player.openInventory(inventory);
    }

    public void openPickaxe(Player player) {
        MiningMenuHolder holder = new MiningMenuHolder(MiningMenuHolder.MenuType.PICKAXE);
        Inventory inventory = Bukkit.createInventory(holder, guiSize("pickaxe", 27),
                Text.component(service.config().guiString("pickaxe-title")));
        holder.inventory(inventory);
        renderPickaxe(player, inventory);
        player.openInventory(inventory);
    }

    public void openPerks(Player player) {
        MiningMenuHolder holder = new MiningMenuHolder(MiningMenuHolder.MenuType.PERKS);
        Inventory inventory = Bukkit.createInventory(holder, guiSize("perks", 54),
                Text.component(service.config().guiString("perks-title")));
        holder.inventory(inventory);
        renderPerks(player, inventory);
        player.openInventory(inventory);
    }

    public void openArtifacts(Player player) {
        MiningMenuHolder holder = new MiningMenuHolder(MiningMenuHolder.MenuType.ARTIFACTS);
        Inventory inventory = Bukkit.createInventory(holder, guiSize("artifacts", 54),
                Text.component(service.config().guiString("artifacts-title")));
        holder.inventory(inventory);
        renderArtifacts(player, inventory);
        player.openInventory(inventory);
    }

    public void openArtifactSets(Player player) {
        MiningMenuHolder holder = new MiningMenuHolder(MiningMenuHolder.MenuType.ARTIFACT_SETS);
        Inventory inventory = Bukkit.createInventory(holder, guiSize("artifact-sets", 54),
                Text.component(service.config().guiString("artifact-sets-title")));
        holder.inventory(inventory);
        renderArtifactSets(player, inventory);
        player.openInventory(inventory);
    }

    public void openArtifactResearch(Player player) {
        MiningMenuHolder holder = new MiningMenuHolder(MiningMenuHolder.MenuType.ARTIFACT_RESEARCH);
        Inventory inventory = Bukkit.createInventory(holder, guiSize("artifact-research", 54),
                Text.component(service.config().guiString("artifact-research-title")));
        holder.inventory(inventory);
        renderArtifactResearch(player, inventory);
        player.openInventory(inventory);
    }

    public void render(Player player, Inventory inventory) {
        PlayerData data = service.players().getOrCreate(player.getUniqueId(), player.getName());
        ItemStack filler = named(new ItemStack(guiMaterial("main-filler", Material.GRAY_STAINED_GLASS_PANE)),
                service.config().guiString("filler-name"), List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        service.syncPerkPoints(data);
        setItem(inventory, mainSlot("info"), infoItem(player, data));
        if (service.config().perksEnabled() && player.hasPermission("miningplus.perks")) {
            setItem(inventory, mainSlot("perks"), perksItem(data));
        }
        if (service.config().pickaxeProgressionEnabled()
                && player.hasPermission(service.config().pickaxeProgression().permission())) {
            setItem(inventory, mainSlot("pickaxe"), pickaxeItem(player));
        }
        if (service.config().sellEnabled() && service.economy().available() && player.hasPermission("miningplus.sell")) {
            setItem(inventory, mainSlot("sell"), sellItem(data));
        }
        if (service.config().artifactsEnabled() && player.hasPermission("miningplus.artifacts")) {
            setItem(inventory, mainSlot("artifacts"), artifactsItem(data));
        }
        setItem(inventory, mainSlot("notifications"), toggleItem("notifications", data.notificationsEnabled(), true));
        setItem(inventory, mainSlot("leaderboard"), leaderboardItem());
        if (service.config().currencyEnabled() && service.config().shopEnabled()
                && player.hasPermission("miningplus.shop")) {
            setItem(inventory, mainSlot("shop"), shopButton(data));
        }
        if (service.config().journalEnabled() && player.hasPermission("miningplus.journal")) {
            setItem(inventory, mainSlot("journal"), journalButton(data));
        }
        if (service.config().commissionsEnabled() && player.hasPermission("miningplus.commissions")) {
            setItem(inventory, mainSlot("commissions"), commissionsButton(data));
        }
    }

    public void renderPickaxe(Player player, Inventory inventory) {
        inventory.clear();
        ItemStack filler = named(new ItemStack(guiMaterial("pickaxe-filler", Material.BLACK_STAINED_GLASS_PANE)),
                service.config().guiString("filler-name"), List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        setItem(inventory, slot("pickaxe.tool", PICKAXE_TOOL), pickaxeToolItem(player));
        setItem(inventory, slot("pickaxe.upgrades", PICKAXE_UPGRADES), pickaxeUpgradesItem(player));
        setItem(inventory, slot("pickaxe.refine", PICKAXE_REFINE), pickaxeRefineItem(player));
        setItem(inventory, slot("pickaxe.back", PICKAXE_BACK), named(new ItemStack(guiMaterial("back", Material.ARROW)),
                service.config().guiString("back-name"), List.of()));
    }

    public void renderPerks(Player player, Inventory inventory) {
        inventory.clear();
        PlayerData data = service.players().getOrCreate(player.getUniqueId(), player.getName());
        service.syncPerkPoints(data);
        List<Integer> contentSlots = perksContentSlots();
        int index = 0;
        for (PerkDefinition definition : service.config().perks().definitions()) {
            if (!definition.active()) {
                continue;
            }
            if (index >= contentSlots.size() || index >= PERKS_CAPACITY) {
                break;
            }
            setItem(inventory, contentSlots.get(index++), perkEntry(player, data, definition));
        }
        ItemStack filler = named(new ItemStack(guiMaterial("perks-filler", Material.BLACK_STAINED_GLASS_PANE)),
                service.config().guiString("filler-name"), List.of());
        for (int slot = Math.max(0, inventory.getSize() - 9); slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        setItem(inventory, slot("perks.back", PERKS_BACK), named(new ItemStack(guiMaterial("back", Material.ARROW)),
                service.config().guiString("back-name"), List.of()));
    }

    private ItemStack infoItem(Player player, PlayerData data) {
        LevelCurve curve = service.config().levelCurve();
        String required = curve.atMaxLevel(data.level())
                ? "MAX"
                : NumberFormat.decimal(curve.xpToAdvance(data.level()));
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("rank", service.config().rankFor(data.level()));
        placeholders.put("level", String.valueOf(data.level()));
        placeholders.put("xp", NumberFormat.decimal(data.xp()));
        placeholders.put("xp_required", required);
        placeholders.put("progress", NumberFormat.decimal(service.progressPercent(data)));
        placeholders.put("bar", progressBar(service.progressPercent(data)));
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
        placeholders.put("shards", service.config().formatShards(data.shards()));
        placeholders.put("shards_earned", NumberFormat.integer(data.shardsEarned()));
        placeholders.put("shards_spent", NumberFormat.integer(data.shardsSpent()));
        placeholders.put("best_pickaxe_level", String.valueOf(data.bestPickaxeLevel()));
        placeholders.put("pickaxe_refines", NumberFormat.integer(data.pickaxeRefines()));
        placeholders.put("tool_upgrades", NumberFormat.integer(data.toolUpgradesFound()));
        placeholders.put("commissions_active", NumberFormat.integer(data.activeCommissions().size()));
        placeholders.put("commissions_completed", NumberFormat.integer(data.commissionsCompleted()));

        Material material = guiMaterial("info", Material.PLAYER_HEAD);
        ItemStack head = new ItemStack(material);
        if (material == Material.PLAYER_HEAD && head.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(player);
            skull.displayName(Text.component(renderName("info-name", placeholders)));
            skull.lore(renderLore("info-lore", placeholders));
            head.setItemMeta(skull);
        }
        return material == Material.PLAYER_HEAD
                ? head
                : named(head, renderName("info-name", placeholders), renderLoreStrings("info-lore", placeholders));
    }

    private ItemStack perksItem(PlayerData data) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("points", NumberFormat.integer(data.perkPoints()));
        List<String> lore = new ArrayList<>(renderLoreStrings("perks-lore", placeholders));
        for (PerkDefinition definition : service.config().perks().definitions()) {
            if (!definition.active()) {
                continue;
            }
            lore.add(service.config().guiString("perks-entry")
                    .replace("{name}", definition.displayName())
                    .replace("{level}", String.valueOf(data.perkLevel(definition.id())))
                    .replace("{max_level}", String.valueOf(definition.maxLevel())));
        }
        return named(new ItemStack(guiMaterial("perks", Material.EMERALD)), renderName("perks-name", placeholders), lore);
    }

    private ItemStack pickaxeItem(Player player) {
        Map<String, String> placeholders = pickaxePlaceholders(player);
        return named(new ItemStack(guiMaterial("pickaxe", Material.DIAMOND_PICKAXE)),
                renderName("pickaxe-name", placeholders), renderLoreStrings("pickaxe-lore", placeholders));
    }

    private ItemStack pickaxeToolItem(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        Map<String, String> placeholders = pickaxePlaceholders(player);
        if (!service.isPickaxe(held)) {
            return named(new ItemStack(guiMaterial("pickaxe-missing", Material.BARRIER)),
                    service.config().guiString("pickaxe-missing-name"),
                    renderLoreWithSection("pickaxe-tool-lore", placeholders, "{upgrades}", service.toolUpgradeLines(held)));
        }
        service.syncPickaxeLore(held);
        ItemStack preview = held.clone();
        preview.setAmount(1);
        return named(preview, renderName("pickaxe-tool-name", placeholders),
                renderLoreWithSection("pickaxe-tool-lore", placeholders, "{upgrades}", service.toolUpgradeLines(held)));
    }

    private ItemStack pickaxeUpgradesItem(Player player) {
        Map<String, String> placeholders = pickaxePlaceholders(player);
        ItemStack held = player.getInventory().getItemInMainHand();
        return named(new ItemStack(guiMaterial("pickaxe-upgrades", Material.ENCHANTED_BOOK)),
                renderName("pickaxe-upgrades-name", placeholders),
                renderLoreWithSection("pickaxe-upgrades-lore", placeholders, "{upgrades}", service.toolUpgradeLines(held)));
    }

    private ItemStack pickaxeRefineItem(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        Map<String, String> placeholders = pickaxePlaceholders(player);
        boolean hasPickaxe = service.isPickaxe(held);
        PlayerData data = service.players().getOrCreate(player.getUniqueId(), player.getName());
        double moneyCost = hasPickaxe ? service.pickaxeRefineMoneyCost(held) : 0.0D;
        long shardCost = hasPickaxe ? service.pickaxeRefineShardCost(held) : 0L;
        double moneyBalance = service.economy().balance(player);
        boolean maxed = hasPickaxe && service.pickaxeXpRequired(held) <= 0.0D;
        boolean needsEconomy = hasPickaxe && moneyCost > 0.0D && !service.economy().available();
        boolean needsMoney = hasPickaxe && !needsEconomy && moneyCost > 0.0D && moneyBalance < moneyCost;
        boolean needsShards = hasPickaxe && shardCost > 0L && data.shards() < shardCost;
        placeholders.put("money_cost", hasPickaxe ? service.formatMoney(moneyCost) : "-");
        placeholders.put("shard_cost", hasPickaxe ? service.config().formatShards(shardCost) : "-");
        placeholders.put("balance", service.formatMoney(moneyBalance));
        placeholders.put("shards", service.config().formatShards(data.shards()));
        placeholders.put("status", !hasPickaxe
                ? service.config().guiString("pickaxe-refine-no-pickaxe")
                : maxed ? service.config().guiString("pickaxe-refine-maxed")
                : needsEconomy ? service.config().guiString("pickaxe-refine-no-economy")
                : needsMoney ? service.config().guiString("pickaxe-refine-need-money")
                : needsShards ? service.config().guiString("pickaxe-refine-need-shards")
                : service.config().guiString("pickaxe-refine-ready"));
        return named(new ItemStack(guiMaterial("pickaxe-refine", Material.ANVIL)),
                renderName("pickaxe-refine-name", placeholders), renderLoreStrings("pickaxe-refine-lore", placeholders));
    }

    private Map<String, String> pickaxePlaceholders(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean hasPickaxe = service.isPickaxe(held);
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("state", hasPickaxe ? service.config().guiString("states.available")
                : service.config().guiString("states.unavailable"));
        placeholders.put("level", hasPickaxe ? String.valueOf(service.pickaxeLevel(held)) : "-");
        placeholders.put("xp", hasPickaxe ? NumberFormat.decimal(service.pickaxeXp(held)) : "-");
        double required = hasPickaxe ? service.pickaxeXpRequired(held) : 0.0D;
        placeholders.put("xp_required", hasPickaxe ? required > 0.0D ? NumberFormat.decimal(required) : "MAX" : "-");
        placeholders.put("progress", hasPickaxe ? NumberFormat.decimal(service.pickaxeProgressPercent(held)) : "-");
        placeholders.put("xp_bonus", hasPickaxe ? NumberFormat.decimal(service.pickaxeMiningXpBonusPercent(held)) : "0");
        placeholders.put("artifact_bonus", hasPickaxe ? NumberFormat.decimal(service.pickaxeArtifactBonusPercent(held)) : "0");
        return placeholders;
    }

    private ItemStack sellItem(PlayerData data) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("sold", NumberFormat.integer(data.itemsSold()));
        placeholders.put("money_earned", service.formatMoney(data.moneyEarned()));
        return named(new ItemStack(guiMaterial("sell", Material.GOLD_INGOT)),
                renderName("sell-name", placeholders), renderLoreStrings("sell-lore", placeholders));
    }

    private ItemStack artifactsItem(PlayerData data) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("found", NumberFormat.integer(data.artifactsFound()));
        placeholders.put("unique", NumberFormat.integer(data.uniqueArtifactsFound()));
        placeholders.put("total", NumberFormat.integer(service.config().artifacts().size()));
        placeholders.put("fragments", NumberFormat.integer(data.artifactFragments()));
        placeholders.put("sets", NumberFormat.integer(service.completedArtifactSetIds(data).size()));
        placeholders.put("set_total", NumberFormat.integer(service.config().artifactSets().size()));
        return named(new ItemStack(guiMaterial("artifacts", Material.AMETHYST_SHARD)),
                renderName("artifacts-name", placeholders), renderLoreStrings("artifacts-lore", placeholders));
    }

    public void renderArtifacts(Player player, Inventory inventory) {
        inventory.clear();
        PlayerData data = service.players().getOrCreate(player.getUniqueId(), player.getName());
        ItemStack filler = named(new ItemStack(guiMaterial("artifacts-filler", Material.BLACK_STAINED_GLASS_PANE)),
                service.config().guiString("filler-name"), List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        setItem(inventory, slot("artifacts.codex", ARTIFACTS_CODEX), artifactCodexButton(data));
        setItem(inventory, slot("artifacts.sets", ARTIFACTS_SETS), artifactSetsButton(data));
        setItem(inventory, slot("artifacts.research", ARTIFACTS_RESEARCH), artifactResearchButton(data));
        setItem(inventory, slot("artifacts.salvage-all", ARTIFACTS_SALVAGE_ALL), artifactSalvageButton(data, false));
        setItem(inventory, slot("artifacts.salvage-hand", ARTIFACTS_SALVAGE_HAND), artifactSalvageButton(data, true));
        setItem(inventory, slot("artifacts.back", ARTIFACTS_BACK), named(new ItemStack(guiMaterial("back", Material.ARROW)),
                service.config().guiString("back-name"), List.of()));

        List<Integer> contentSlots = artifactContentSlots();
        int index = 0;
        for (ArtifactDefinition artifact : service.config().artifacts()) {
            if (!artifact.enabled()) {
                continue;
            }
            if (index >= contentSlots.size() || index >= ARTIFACT_CAPACITY) {
                break;
            }
            setItem(inventory, contentSlots.get(index++), artifactEntry(data, artifact));
        }
    }

    public void renderArtifactSets(Player player, Inventory inventory) {
        inventory.clear();
        PlayerData data = service.players().getOrCreate(player.getUniqueId(), player.getName());
        List<Integer> contentSlots = artifactSetContentSlots();
        int index = 0;
        for (ArtifactSetDefinition set : service.config().artifactSets()) {
            if (!set.enabled()) {
                continue;
            }
            if (index >= contentSlots.size() || index >= ARTIFACT_SET_CAPACITY) {
                break;
            }
            setItem(inventory, contentSlots.get(index++), artifactSetEntry(data, set));
        }
        ItemStack filler = named(new ItemStack(guiMaterial("artifact-sets-filler", Material.BLACK_STAINED_GLASS_PANE)),
                service.config().guiString("filler-name"), List.of());
        for (int slot = Math.max(0, inventory.getSize() - 9); slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        setItem(inventory, slot("artifact-sets.back", ARTIFACT_SETS_BACK), named(new ItemStack(guiMaterial("back", Material.ARROW)),
                service.config().guiString("back-name"), List.of()));
    }

    public void renderArtifactResearch(Player player, Inventory inventory) {
        inventory.clear();
        PlayerData data = service.players().getOrCreate(player.getUniqueId(), player.getName());
        List<Integer> contentSlots = artifactResearchContentSlots();
        int index = 0;
        for (ArtifactResearchDefinition research : service.config().artifactResearch()) {
            if (!research.enabled()) {
                continue;
            }
            if (index >= contentSlots.size() || index >= ARTIFACT_RESEARCH_CAPACITY) {
                break;
            }
            setItem(inventory, contentSlots.get(index++), artifactResearchEntry(data, research));
        }
        ItemStack filler = named(new ItemStack(guiMaterial("artifact-research-filler", Material.BLACK_STAINED_GLASS_PANE)),
                service.config().guiString("filler-name"), List.of());
        for (int slot = Math.max(0, inventory.getSize() - 9); slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        setItem(inventory, slot("artifact-research.back", ARTIFACT_RESEARCH_BACK), named(new ItemStack(guiMaterial("back", Material.ARROW)),
                service.config().guiString("back-name"), List.of()));
    }

    private ItemStack artifactCodexButton(PlayerData data) {
        Map<String, String> placeholders = artifactPlaceholders(data);
        return named(new ItemStack(guiMaterial("artifact-codex", Material.AMETHYST_SHARD)),
                renderName("artifact-codex-name", placeholders), renderLoreStrings("artifact-codex-lore", placeholders));
    }

    private ItemStack artifactSetsButton(PlayerData data) {
        Map<String, String> placeholders = artifactPlaceholders(data);
        return named(new ItemStack(guiMaterial("artifact-sets", Material.KNOWLEDGE_BOOK)),
                renderName("artifact-sets-name", placeholders), renderLoreStrings("artifact-sets-lore", placeholders));
    }

    private ItemStack artifactResearchButton(PlayerData data) {
        Map<String, String> placeholders = artifactPlaceholders(data);
        placeholders.put("research", NumberFormat.integer(service.config().artifactResearch().size()));
        return named(new ItemStack(guiMaterial("artifact-research", Material.ENCHANTED_BOOK)),
                renderName("artifact-research-name", placeholders), renderLoreStrings("artifact-research-lore", placeholders));
    }

    private ItemStack artifactSalvageButton(PlayerData data, boolean handOnly) {
        Map<String, String> placeholders = artifactPlaceholders(data);
        String key = handOnly ? "artifact-salvage-hand" : "artifact-salvage-all";
        Material fallback = handOnly ? Material.GOLDEN_PICKAXE : Material.ANVIL;
        return named(new ItemStack(guiMaterial(key, fallback)), renderName(key + "-name", placeholders),
                renderLoreStrings(key + "-lore", placeholders));
    }

    private ItemStack artifactEntry(PlayerData data, ArtifactDefinition artifact) {
        long found = data.artifactFound(artifact.id());
        ArtifactSetDefinition set = service.config().artifactSet(artifact.setId());
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("id", artifact.id());
        placeholders.put("artifact", artifact.displayName());
        placeholders.put("set", set == null ? artifact.setId() : set.displayName());
        placeholders.put("level", String.valueOf(artifact.unlockLevel()));
        placeholders.put("chance", NumberFormat.decimal(artifact.chance() * 100.0D));
        placeholders.put("found", NumberFormat.integer(found));
        placeholders.put("fragments", NumberFormat.integer(artifact.fragmentValue()));
        placeholders.put("status", found > 0L
                ? service.config().guiString("artifact-found-status")
                : service.config().guiString("artifact-missing-status"));
        ItemStack item = artifact.toItemStack(1);
        return named(item, renderName("artifact-entry-name", placeholders), renderLoreStrings("artifact-entry-lore", placeholders));
    }

    private ItemStack artifactSetEntry(PlayerData data, ArtifactSetDefinition set) {
        boolean complete = service.artifactSetComplete(data, set);
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("id", set.id());
        placeholders.put("set", set.displayName());
        placeholders.put("found", NumberFormat.integer(service.artifactSetProgress(data, set)));
        placeholders.put("required", NumberFormat.integer(set.artifactIds().size()));
        placeholders.put("status", complete
                ? service.config().guiString("artifact-set-complete-status")
                : service.config().guiString("artifact-set-progress-status"));
        placeholders.put("mining_xp", NumberFormat.decimal(set.miningXpMultiplier() * 100.0D));
        placeholders.put("pickaxe_xp", NumberFormat.decimal(set.pickaxeXpMultiplier() * 100.0D));
        placeholders.put("money", NumberFormat.decimal(set.moneyMultiplier() * 100.0D));
        placeholders.put("points", NumberFormat.decimal(set.pointsMultiplier() * 100.0D));
        placeholders.put("shards", NumberFormat.decimal(set.shardMultiplier() * 100.0D));
        placeholders.put("artifact_chance", NumberFormat.decimal(set.artifactChance() * 100.0D));
        placeholders.put("treasure_chance", NumberFormat.decimal(set.treasureChance() * 100.0D));
        placeholders.put("hazard_reduction", NumberFormat.decimal(set.hazardChanceReduction() * 100.0D));
        List<String> lore = new ArrayList<>();
        for (String line : service.config().guiLore("artifact-set-entry-lore")) {
            if ("{description}".equals(line)) {
                lore.addAll(set.lore());
            } else if ("{artifacts}".equals(line)) {
                for (String artifactId : set.artifactIds()) {
                    ArtifactDefinition artifact = service.config().artifact(artifactId);
                    long found = data.artifactFound(artifactId);
                    lore.add(service.config().guiString("artifact-set-artifact-line")
                            .replace("{artifact}", artifact == null ? artifactId : artifact.displayName())
                            .replace("{found}", NumberFormat.integer(found)));
                }
            } else {
                lore.add(Text.render(line, placeholders));
            }
        }
        return named(new ItemStack(guiMaterial("artifact-set-entry", complete ? Material.LIME_DYE : Material.GRAY_DYE)),
                renderName("artifact-set-entry-name", placeholders), lore);
    }

    private ItemStack artifactResearchEntry(PlayerData data, ArtifactResearchDefinition research) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("id", research.id());
        placeholders.put("research", research.displayName());
        placeholders.put("cost", NumberFormat.integer(research.fragmentCost()));
        placeholders.put("balance", NumberFormat.integer(data.artifactFragments()));
        placeholders.put("level", String.valueOf(research.unlockLevel()));
        placeholders.put("status", artifactResearchStatus(data, research));
        placeholders.put("action", artifactResearchReady(data, research)
                ? service.config().guiString("artifact-research-action-complete")
                : service.config().guiString("artifact-research-action-locked"));
        List<String> lore = new ArrayList<>();
        for (String line : service.config().guiLore("artifact-research-entry-lore")) {
            if ("{description}".equals(line)) {
                lore.addAll(research.lore());
            } else if ("{sets}".equals(line)) {
                if (research.requiredSets().isEmpty()) {
                    lore.add(service.config().guiString("artifact-research-no-sets"));
                } else {
                    for (String setId : research.requiredSets()) {
                        ArtifactSetDefinition set = service.config().artifactSet(setId);
                        boolean complete = service.artifactSetComplete(data, set);
                        lore.add(service.config().guiString("artifact-research-set-line")
                                .replace("{set}", set == null ? setId : set.displayName())
                                .replace("{status}", complete
                                        ? service.config().guiString("artifact-set-complete-status")
                                        : service.config().guiString("artifact-set-progress-status")));
                    }
                }
            } else {
                lore.add(Text.render(line, placeholders));
            }
        }
        return named(new ItemStack(research.icon()), renderName("artifact-research-entry-name", placeholders), lore);
    }

    private Map<String, String> artifactPlaceholders(PlayerData data) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("found", NumberFormat.integer(data.artifactsFound()));
        placeholders.put("unique", NumberFormat.integer(data.uniqueArtifactsFound()));
        placeholders.put("total", NumberFormat.integer(service.config().artifacts().size()));
        placeholders.put("fragments", NumberFormat.integer(data.artifactFragments()));
        placeholders.put("sets", NumberFormat.integer(service.completedArtifactSetIds(data).size()));
        placeholders.put("set_total", NumberFormat.integer(service.config().artifactSets().size()));
        return placeholders;
    }

    private boolean artifactResearchReady(PlayerData data, ArtifactResearchDefinition research) {
        if (data.level() < research.unlockLevel() || data.artifactFragments() < research.fragmentCost()) {
            return false;
        }
        for (String setId : research.requiredSets()) {
            if (!service.artifactSetComplete(data, service.config().artifactSet(setId))) {
                return false;
            }
        }
        return true;
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

    private ItemStack toggleItem(String key, boolean enabled, boolean moduleEnabled) {
        Material material = enabled && moduleEnabled
                ? guiMaterial(key + "-enabled", Material.LIME_DYE)
                : guiMaterial(key + "-disabled", Material.GRAY_DYE);
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("state", moduleEnabled
                ? (enabled ? service.config().guiString("states.enabled") : service.config().guiString("states.disabled"))
                : service.config().guiString("states.unavailable"));
        return named(new ItemStack(material), renderName(key + "-name", placeholders),
                renderLoreStrings(key + "-lore", placeholders));
    }

    private ItemStack leaderboardItem() {
        List<PlayerData> top = service.players().topByLevel(service.config().leaderboardSize());
        List<String> lore = new ArrayList<>(service.config().guiLore("leaderboard-lore"));
        int rank = 1;
        for (PlayerData data : top) {
            lore.add(service.config().message("top-entry")
                    .replace("{position}", String.valueOf(rank++))
                    .replace("{player}", data.name() == null || data.name().isBlank() ? "Unknown" : data.name())
                    .replace("{level}", String.valueOf(data.level()))
                    .replace("{blocks}", NumberFormat.integer(data.blocksMined())));
        }
        if (top.isEmpty()) {
            lore.add(service.config().message("top-empty"));
        }
        return named(new ItemStack(guiMaterial("leaderboard", Material.BOOK)), service.config().guiString("leaderboard-name"), lore);
    }

    private ItemStack journalButton(PlayerData data) {
        JournalChapter next = service.nextJournalChapter(data);
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("claimed", NumberFormat.integer(data.claimedJournalChapters().size()));
        placeholders.put("total", NumberFormat.integer(service.config().journalChapters().size()));
        placeholders.put("claimable", NumberFormat.integer(service.claimableJournalChapters(data)));
        placeholders.put("next", next == null ? service.config().guiString("journal-complete") : next.displayName());
        placeholders.put("progress", next == null ? "100" : NumberFormat.decimal(service.journalProgressPercent(data, next)));
        return named(new ItemStack(guiMaterial("journal", Material.WRITTEN_BOOK)), renderName("journal-name", placeholders),
                renderLoreStrings("journal-lore", placeholders));
    }

    private ItemStack commissionsButton(PlayerData data) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("active", NumberFormat.integer(data.activeCommissions().size()));
        placeholders.put("max", NumberFormat.integer(service.config().maxActiveCommissions()));
        placeholders.put("completed", NumberFormat.integer(data.commissionsCompleted()));
        placeholders.put("ready", NumberFormat.integer(readyCommissions(data)));
        placeholders.put("available", NumberFormat.integer(availableCommissions(data)));
        return named(new ItemStack(guiMaterial("commissions", Material.COMPASS)),
                renderName("commissions-name", placeholders), renderLoreStrings("commissions-lore", placeholders));
    }

    private ItemStack shopButton(PlayerData data) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("shards", service.config().formatShards(data.shards()));
        long enabledItems = service.config().shopItems().stream().filter(MiningShopItem::enabled).count();
        placeholders.put("items", NumberFormat.integer(enabledItems));
        return named(new ItemStack(guiMaterial("shop", Material.EMERALD)), renderName("shop-name", placeholders),
                renderLoreStrings("shop-lore", placeholders));
    }

    public void renderCommissions(Player player, Inventory inventory) {
        inventory.clear();
        PlayerData data = service.players().getOrCreate(player.getUniqueId(), player.getName());
        List<Integer> contentSlots = commissionsContentSlots();
        int index = 0;
        for (CommissionDefinition commission : service.config().commissions()) {
            if (!commission.enabled()) {
                continue;
            }
            if (index >= contentSlots.size() || index >= COMMISSIONS_CAPACITY) {
                break;
            }
            setItem(inventory, contentSlots.get(index++), commissionEntry(data, commission));
        }
        ItemStack filler = named(new ItemStack(guiMaterial("commissions-filler", Material.BLACK_STAINED_GLASS_PANE)),
                service.config().guiString("filler-name"), List.of());
        for (int slot = Math.max(0, inventory.getSize() - 9); slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        setItem(inventory, slot("commissions.back", COMMISSIONS_BACK), named(new ItemStack(guiMaterial("back", Material.ARROW)),
                service.config().guiString("back-name"), List.of()));
    }

    public String commissionAtSlot(int slot) {
        List<Integer> contentSlots = commissionsContentSlots();
        int index = contentSlots.indexOf(slot);
        if (index < 0 || index >= COMMISSIONS_CAPACITY) {
            return null;
        }
        List<CommissionDefinition> commissions = service.config().commissions().stream()
                .filter(CommissionDefinition::enabled)
                .toList();
        return index >= commissions.size() ? null : commissions.get(index).id();
    }

    private ItemStack commissionEntry(PlayerData data, CommissionDefinition commission) {
        PlayerCommission active = data.activeCommission(commission.id());
        boolean activeCommission = active != null;
        boolean complete = activeCommission && service.commissionComplete(data, active, commission);
        boolean unlocked = data.level() >= commission.unlockLevel() && service.commissionUnlocked(data, commission);
        String status = activeCommission
                ? complete ? service.config().guiString("status.commission.ready")
                : service.config().guiString("status.commission.active")
                : !unlocked ? service.config().guiString("status.commission.locked")
                : service.config().guiString("status.commission.available");
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("id", commission.id());
        placeholders.put("commission", commission.displayName());
        placeholders.put("status", status);
        placeholders.put("progress", activeCommission
                ? NumberFormat.decimal(service.commissionProgressPercent(data, active, commission))
                : "0");
        placeholders.put("requirement", unlocked
                ? service.config().guiString("commission-unlocked")
                : service.commissionLockedDetail(data, commission));
        placeholders.put("action", activeCommission
                ? complete ? service.config().guiString("commission-action-claim")
                : service.config().guiString("commission-action-progress")
                : !unlocked ? service.config().guiString("commission-action-locked")
                : service.config().guiString("commission-action-accept"));

        List<String> lore = new ArrayList<>();
        for (String line : service.config().guiLore("commission-entry-lore")) {
            if ("{description}".equals(line)) {
                lore.addAll(commission.lore());
            } else if ("{objectives}".equals(line)) {
                for (JournalObjective objective : commission.objectives()) {
                    lore.add(service.commissionObjectiveLine(data, active, objective));
                }
            } else if ("{rewards}".equals(line)) {
                lore.addAll(service.commissionRewardLines(commission));
            } else {
                lore.add(Text.render(line, placeholders));
            }
        }
        return named(new ItemStack(commission.icon()), renderName("commission-entry-name", placeholders), lore);
    }

    public void renderJournal(Player player, Inventory inventory) {
        inventory.clear();
        PlayerData data = service.players().getOrCreate(player.getUniqueId(), player.getName());
        List<Integer> contentSlots = journalContentSlots();
        int index = 0;
        for (JournalChapter chapter : service.config().journalChapters()) {
            if (index >= contentSlots.size() || index >= JOURNAL_CAPACITY) {
                break;
            }
            setItem(inventory, contentSlots.get(index++), journalEntry(data, chapter));
        }
        ItemStack filler = named(new ItemStack(guiMaterial("journal-filler", Material.GRAY_STAINED_GLASS_PANE)),
                service.config().guiString("filler-name"), List.of());
        for (int slot = Math.max(0, inventory.getSize() - 9); slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        setItem(inventory, slot("journal.back", JOURNAL_BACK), named(new ItemStack(guiMaterial("back", Material.ARROW)),
                service.config().guiString("back-name"), List.of()));
    }

    public String journalChapterAtSlot(int slot) {
        List<Integer> contentSlots = journalContentSlots();
        int index = contentSlots.indexOf(slot);
        if (index < 0 || index >= JOURNAL_CAPACITY) {
            return null;
        }
        List<JournalChapter> chapters = service.config().journalChapters();
        return index >= chapters.size() ? null : chapters.get(index).id();
    }

    private ItemStack journalEntry(PlayerData data, JournalChapter chapter) {
        boolean claimed = data.hasClaimedJournalChapter(chapter.id());
        boolean unlocked = service.journalChapterUnlocked(data, chapter);
        boolean complete = service.journalChapterComplete(data, chapter);
        String status = claimed
                ? service.config().guiString("status.journal.claimed")
                : !unlocked ? service.config().guiString("status.journal.locked")
                : complete ? service.config().guiString("status.journal.ready")
                : service.config().guiString("status.journal.progress");
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("id", chapter.id());
        placeholders.put("chapter", chapter.displayName());
        placeholders.put("status", status);
        placeholders.put("progress", NumberFormat.decimal(service.journalProgressPercent(data, chapter)));
        JournalObjective nextObjective = service.firstIncompleteJournalObjective(data, chapter);
        placeholders.put("next_objective", nextObjective == null
                ? service.config().guiString("journal-complete")
                : service.journalObjectiveLine(data, nextObjective));
        placeholders.put("requirement", unlocked
                ? guiStringOr("journal-unlocked", "Unlocked")
                : service.journalLockedDetail(data, chapter));
        placeholders.put("action", claimed
                ? service.config().guiString("journal-action-claimed")
                : !unlocked ? service.config().guiString("journal-action-locked")
                : complete ? service.config().guiString("journal-action-claim")
                : service.config().guiString("journal-action-progress"));
        List<String> lore = new ArrayList<>();
        for (String line : service.config().guiLore("journal-entry-lore")) {
            if ("{story}".equals(line)) {
                lore.addAll(chapter.lore());
            } else if ("{objectives}".equals(line)) {
                for (JournalObjective objective : chapter.objectives()) {
                    lore.add(service.journalObjectiveLine(data, objective));
                }
            } else if ("{rewards}".equals(line)) {
                lore.addAll(service.journalRewardLines(chapter));
            } else {
                lore.add(Text.render(line, placeholders));
            }
        }
        return named(new ItemStack(chapter.icon()), renderName("journal-entry-name", placeholders), lore);
    }

    public void renderShop(Player player, Inventory inventory) {
        inventory.clear();
        PlayerData data = service.players().getOrCreate(player.getUniqueId(), player.getName());
        List<Integer> contentSlots = shopContentSlots();
        int index = 0;
        for (MiningShopItem item : service.config().shopItems()) {
            if (!item.enabled()) {
                continue;
            }
            if (index >= contentSlots.size() || index >= SHOP_CAPACITY) {
                break;
            }
            setItem(inventory, contentSlots.get(index++), shopEntry(data, item));
        }
        ItemStack filler = named(new ItemStack(guiMaterial("shop-filler", Material.GRAY_STAINED_GLASS_PANE)),
                service.config().guiString("filler-name"), List.of());
        for (int slot = Math.max(0, inventory.getSize() - 9); slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        setItem(inventory, slot("shop.back", SHOP_BACK), named(new ItemStack(guiMaterial("back", Material.ARROW)),
                service.config().guiString("back-name"), List.of()));
    }

    public String shopItemAtSlot(int slot) {
        List<Integer> contentSlots = shopContentSlots();
        int index = contentSlots.indexOf(slot);
        if (index < 0 || index >= SHOP_CAPACITY) {
            return null;
        }
        List<MiningShopItem> items = service.config().shopItems().stream()
                .filter(MiningShopItem::enabled)
                .toList();
        return index >= items.size() ? null : items.get(index).id();
    }

    private ItemStack shopEntry(PlayerData data, MiningShopItem item) {
        boolean unlocked = data.level() >= item.unlockLevel() && service.shopItemUnlocked(data, item);
        boolean affordable = data.shards() >= item.cost();
        String status = !unlocked
                ? service.config().guiString("status.shop.locked")
                : affordable ? service.config().guiString("status.shop.available")
                : service.config().guiString("status.shop.expensive");
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("id", item.id());
        placeholders.put("item", item.displayName());
        placeholders.put("status", status);
        placeholders.put("cost", service.config().formatShards(item.cost()));
        placeholders.put("balance", service.config().formatShards(data.shards()));
        placeholders.put("unlock_level", String.valueOf(item.unlockLevel()));
        placeholders.put("requirement", unlocked
                ? guiStringOr("shop-unlocked", "Unlocked")
                : service.shopLockedDetail(data, item));
        placeholders.put("action", !unlocked
                ? service.config().guiString("shop-action-locked")
                : affordable ? service.config().guiString("shop-action-buy")
                : service.config().guiString("shop-action-expensive"));
        List<String> lore = new ArrayList<>();
        for (String line : service.config().guiLore("shop-entry-lore")) {
            if ("{description}".equals(line)) {
                lore.addAll(item.lore());
            } else if ("{rewards}".equals(line)) {
                lore.addAll(service.shopRewardLines(item));
            } else {
                lore.add(Text.render(line, placeholders));
            }
        }
        return named(new ItemStack(item.icon()), renderName("shop-entry-name", placeholders), lore);
    }

    public String perkIdAtSlot(int slot) {
        List<Integer> contentSlots = perksContentSlots();
        int index = contentSlots.indexOf(slot);
        if (index < 0 || index >= PERKS_CAPACITY) {
            return null;
        }
        List<PerkDefinition> perks = service.config().perks().definitions().stream()
                .filter(PerkDefinition::active)
                .toList();
        return index >= perks.size() ? null : perks.get(index).id();
    }

    private ItemStack perkEntry(Player player, PlayerData data, PerkDefinition definition) {
        int level = data.perkLevel(definition.id());
        boolean maxed = level >= definition.maxLevel();
        boolean unlocked = data.level() >= definition.unlockLevel();
        boolean permitted = player.hasPermission(definition.permission());
        int cost = maxed ? 0 : definition.costForNextLevel(level);
        boolean affordable = data.perkPoints() >= cost;
        String status = maxed
                ? service.config().guiString("status.perk.maxed")
                : !permitted ? service.config().guiString("status.perk.no-permission")
                : !unlocked ? service.config().guiString("status.perk.locked")
                : affordable ? service.config().guiString("status.perk.available")
                : service.config().guiString("status.perk.expensive");
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("id", definition.id());
        placeholders.put("perk", definition.displayName());
        placeholders.put("status", status);
        placeholders.put("level", String.valueOf(level));
        placeholders.put("next_level", maxed ? "MAX" : String.valueOf(level + 1));
        placeholders.put("max_level", String.valueOf(definition.maxLevel()));
        placeholders.put("cost", maxed ? "MAX" : NumberFormat.integer(cost));
        placeholders.put("points", NumberFormat.integer(data.perkPoints()));
        placeholders.put("unlock_level", String.valueOf(definition.unlockLevel()));
        placeholders.put("requirement", unlocked
                ? service.config().guiString("perk-unlocked")
                : Text.render(service.config().guiString("perk-locked-level"),
                Map.of("level", String.valueOf(definition.unlockLevel()))));
        placeholders.put("action", maxed
                ? service.config().guiString("perk-action-maxed")
                : !permitted ? service.config().guiString("perk-action-no-permission")
                : !unlocked ? service.config().guiString("perk-action-locked")
                : affordable ? service.config().guiString("perk-action-buy")
                : service.config().guiString("perk-action-expensive"));
        List<String> lore = new ArrayList<>();
        for (String line : service.config().guiLore("perk-entry-lore")) {
            if ("{effects}".equals(line)) {
                lore.addAll(service.perkEffectLines(definition));
            } else {
                lore.add(Text.render(line, placeholders));
            }
        }
        return named(new ItemStack(definition.icon()), renderName("perk-entry-name", placeholders), lore);
    }

    private long readyCommissions(PlayerData data) {
        long ready = 0L;
        for (CommissionDefinition commission : service.config().commissions()) {
            PlayerCommission active = data.activeCommission(commission.id());
            if (active != null && service.commissionComplete(data, active, commission)) {
                ready++;
            }
        }
        return ready;
    }

    private long availableCommissions(PlayerData data) {
        long available = 0L;
        for (CommissionDefinition commission : service.config().commissions()) {
            if (!commission.enabled() || data.hasActiveCommission(commission.id())) {
                continue;
            }
            if (data.level() >= commission.unlockLevel() && service.commissionUnlocked(data, commission)) {
                available++;
            }
        }
        return available;
    }

    private String progressBar(double percent) {
        int width = Math.max(1, service.config().guiInt("progress-bar.width", 20));
        int filled = (int) Math.round((percent / 100.0D) * width);
        String symbol = service.config().guiString("progress-bar.symbol");
        if (symbol.isBlank()) {
            symbol = "|";
        }
        StringBuilder bar = new StringBuilder(service.config().guiString("progress-bar.complete-color"));
        for (int i = 0; i < width; i++) {
            if (i == filled) {
                bar.append(service.config().guiString("progress-bar.incomplete-color"));
            }
            bar.append(symbol);
        }
        return bar.toString();
    }

    public boolean isMainSlot(String key, int clickedSlot) {
        return clickedSlot == mainSlot(key);
    }

    public boolean isJournalBackSlot(int clickedSlot) {
        return clickedSlot == slot("journal.back", JOURNAL_BACK);
    }

    public boolean isCommissionsBackSlot(int clickedSlot) {
        return clickedSlot == slot("commissions.back", COMMISSIONS_BACK);
    }

    public boolean isShopBackSlot(int clickedSlot) {
        return clickedSlot == slot("shop.back", SHOP_BACK);
    }

    public boolean isPickaxeBackSlot(int clickedSlot) {
        return clickedSlot == slot("pickaxe.back", PICKAXE_BACK);
    }

    public boolean isPickaxeRefineSlot(int clickedSlot) {
        return clickedSlot == slot("pickaxe.refine", PICKAXE_REFINE);
    }

    public boolean isPerksBackSlot(int clickedSlot) {
        return clickedSlot == slot("perks.back", PERKS_BACK);
    }

    public boolean isArtifactsBackSlot(int clickedSlot) {
        return clickedSlot == slot("artifacts.back", ARTIFACTS_BACK);
    }

    public boolean isArtifactsSetsSlot(int clickedSlot) {
        return clickedSlot == slot("artifacts.sets", ARTIFACTS_SETS);
    }

    public boolean isArtifactsResearchSlot(int clickedSlot) {
        return clickedSlot == slot("artifacts.research", ARTIFACTS_RESEARCH);
    }

    public boolean isArtifactsSalvageAllSlot(int clickedSlot) {
        return clickedSlot == slot("artifacts.salvage-all", ARTIFACTS_SALVAGE_ALL);
    }

    public boolean isArtifactsSalvageHandSlot(int clickedSlot) {
        return clickedSlot == slot("artifacts.salvage-hand", ARTIFACTS_SALVAGE_HAND);
    }

    public boolean isArtifactSetsBackSlot(int clickedSlot) {
        return clickedSlot == slot("artifact-sets.back", ARTIFACT_SETS_BACK);
    }

    public boolean isArtifactResearchBackSlot(int clickedSlot) {
        return clickedSlot == slot("artifact-research.back", ARTIFACT_RESEARCH_BACK);
    }

    public String artifactResearchAtSlot(int slot) {
        List<Integer> contentSlots = artifactResearchContentSlots();
        int index = contentSlots.indexOf(slot);
        if (index < 0 || index >= ARTIFACT_RESEARCH_CAPACITY) {
            return null;
        }
        List<ArtifactResearchDefinition> research = service.config().artifactResearch().stream()
                .filter(ArtifactResearchDefinition::enabled)
                .toList();
        return index >= research.size() ? null : research.get(index).id();
    }

    private int mainSlot(String key) {
        int fallback = switch (key) {
            case "info" -> SLOT_INFO;
            case "perks" -> SLOT_PERKS;
            case "notifications" -> SLOT_NOTIFICATIONS;
            case "leaderboard" -> SLOT_LEADERBOARD;
            case "journal" -> SLOT_JOURNAL;
            case "shop" -> SLOT_SHOP;
            case "commissions" -> SLOT_COMMISSIONS;
            case "pickaxe" -> SLOT_PICKAXE;
            case "sell" -> SLOT_SELL;
            case "artifacts" -> SLOT_ARTIFACTS;
            default -> 0;
        };
        return slot("main." + key, fallback);
    }

    private int slot(String key, int fallback) {
        return service.config().guiSlot(key, fallback);
    }

    private List<Integer> journalContentSlots() {
        return service.config().guiSlots("journal.content", defaultContentSlots());
    }

    private List<Integer> commissionsContentSlots() {
        return service.config().guiSlots("commissions.content", defaultContentSlots());
    }

    private List<Integer> shopContentSlots() {
        return service.config().guiSlots("shop.content", defaultContentSlots());
    }

    private List<Integer> perksContentSlots() {
        return service.config().guiSlots("perks.content", defaultContentSlots());
    }

    private List<Integer> artifactContentSlots() {
        return service.config().guiSlots("artifacts.content", defaultArtifactContentSlots());
    }

    private List<Integer> artifactSetContentSlots() {
        return service.config().guiSlots("artifact-sets.content", defaultContentSlots());
    }

    private List<Integer> artifactResearchContentSlots() {
        return service.config().guiSlots("artifact-research.content", defaultContentSlots());
    }

    private List<Integer> defaultContentSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < 45; slot++) {
            slots.add(slot);
        }
        return slots;
    }

    private List<Integer> defaultArtifactContentSlots() {
        return List.of(18, 19, 20, 21, 22, 23, 24, 25, 26, 36, 37, 38, 39, 40, 41, 42, 43, 44);
    }

    private int guiSize(String key, int fallback) {
        return service.config().guiSize(key, fallback);
    }

    private Material guiMaterial(String key, Material fallback) {
        return service.config().guiMaterial(key, fallback);
    }

    private String guiStringOr(String key, String fallback) {
        String value = service.config().guiString(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private void setItem(Inventory inventory, int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }

    private String renderName(String key, Map<String, String> placeholders) {
        return Text.render(service.config().guiString(key), placeholders);
    }

    private List<net.kyori.adventure.text.Component> renderLore(String key, Map<String, String> placeholders) {
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String line : service.config().guiLore(key)) {
            lore.add(Text.component(Text.render(line, placeholders)));
        }
        return lore;
    }

    private List<String> renderLoreStrings(String key, Map<String, String> placeholders) {
        List<String> lore = new ArrayList<>();
        for (String line : service.config().guiLore(key)) {
            lore.add(Text.render(line, placeholders));
        }
        return lore;
    }

    private List<String> renderLoreWithSection(String key, Map<String, String> placeholders,
                                               String marker, List<String> section) {
        List<String> lore = new ArrayList<>();
        for (String line : service.config().guiLore(key)) {
            if (marker.equals(line)) {
                lore.addAll(section);
            } else {
                lore.add(Text.render(line, placeholders));
            }
        }
        return lore;
    }

    private ItemStack named(ItemStack item, String name, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.component(name));
            List<net.kyori.adventure.text.Component> components = new ArrayList<>();
            for (String line : lore) {
                components.add(Text.component(line));
            }
            meta.lore(components);
            item.setItemMeta(meta);
        }
        return item;
    }
}
