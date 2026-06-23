package dev.miningplus.integration;

import dev.miningplus.data.PlayerData;
import dev.miningplus.data.PlayerCommission;
import dev.miningplus.mining.CommissionDefinition;
import dev.miningplus.mining.JournalChapter;
import dev.miningplus.mining.LevelCurve;
import dev.miningplus.mining.MiningService;
import dev.miningplus.util.NumberFormat;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class MiningPlusExpansion extends PlaceholderExpansion {
    private final JavaPlugin plugin;
    private final MiningService service;

    public MiningPlusExpansion(JavaPlugin plugin, MiningService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "miningplus";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        PlayerData data = service.players().get(player.getUniqueId());
        if (data == null) {
            data = PlayerData.fresh(player.getUniqueId(), player.getName() == null ? "" : player.getName());
        }
        service.syncPerkPoints(data);
        LevelCurve curve = service.config().levelCurve();
        String normalized = params.toLowerCase();
        switch (normalized) {
            case "artifact_fragments" -> {
                return NumberFormat.integer(data.artifactFragments());
            }
            case "artifact_fragments_spent" -> {
                return NumberFormat.integer(data.artifactFragmentsSpent());
            }
            case "artifact_sets_completed" -> {
                return NumberFormat.integer(service.completedArtifactSetIds(data).size());
            }
            case "artifact_sets_total" -> {
                return NumberFormat.integer(service.config().artifactSets().size());
            }
            case "event_challenges_completed" -> {
                return NumberFormat.integer(data.eventChallengesCompleted());
            }
            case "encounters_defeated" -> {
                return NumberFormat.integer(data.encountersDefeated());
            }
            default -> {
            }
        }
        if (normalized.startsWith("perk_")) {
            String id = normalized.substring("perk_".length()).replace('_', '-');
            return String.valueOf(data.perkLevel(id));
        }
        if (normalized.startsWith("artifact_")) {
            String id = normalized.substring("artifact_".length()).replace('_', '-');
            return NumberFormat.integer(data.artifactFound(id));
        }
        if (normalized.startsWith("tool_upgrade_")) {
            String id = normalized.substring("tool_upgrade_".length()).replace('_', '-');
            return NumberFormat.integer(data.toolUpgradeFound(id));
        }
        if (normalized.startsWith("mined_group_")) {
            String id = normalized.substring("mined_group_".length()).replace('_', '-');
            return NumberFormat.integer(service.minedGroup(data, id));
        }
        if (normalized.startsWith("mined_")) {
            String id = normalized.substring("mined_".length()).replace('_', '-');
            return NumberFormat.integer(data.minedBlock(id));
        }
        Player online = player.getPlayer();
        ItemStack pickaxe = online == null ? null : online.getInventory().getItemInMainHand();
        JournalChapter nextChapter = service.nextJournalChapter(data);
        CommissionDefinition activeCommission = null;
        PlayerCommission activeProgress = null;
        for (CommissionDefinition commission : service.config().commissions()) {
            PlayerCommission progress = data.activeCommission(commission.id());
            if (progress != null) {
                activeCommission = commission;
                activeProgress = progress;
                break;
            }
        }
        final CommissionDefinition firstCommission = activeCommission;
        final PlayerCommission firstCommissionProgress = activeProgress;
        return switch (normalized) {
            case "level" -> String.valueOf(data.level());
            case "rank" -> service.config().rankFor(data.level());
            case "xp" -> NumberFormat.decimal(data.xp());
            case "xp_required" -> curve.atMaxLevel(data.level())
                    ? "MAX" : NumberFormat.decimal(curve.xpToAdvance(data.level()));
            case "progress" -> NumberFormat.decimal(service.progressPercent(data));
            case "blocks_mined" -> NumberFormat.integer(data.blocksMined());
            case "perk_points" -> NumberFormat.integer(data.perkPoints());
            case "items_sold" -> NumberFormat.integer(data.itemsSold());
            case "points_earned" -> NumberFormat.integer(data.pointsEarned());
            case "money_earned" -> NumberFormat.decimal(data.moneyEarned());
            case "treasures_found" -> NumberFormat.integer(data.treasuresFound());
            case "hazards_survived" -> NumberFormat.integer(data.hazardsSurvived());
            case "artifacts_found" -> NumberFormat.integer(data.artifactsFound());
            case "artifacts_unique" -> NumberFormat.integer(data.uniqueArtifactsFound());
            case "perks_purchased" -> NumberFormat.integer(data.perksPurchased());
            case "shards" -> NumberFormat.integer(data.shards());
            case "shards_formatted" -> service.config().formatShards(data.shards());
            case "shards_earned" -> NumberFormat.integer(data.shardsEarned());
            case "shards_spent" -> NumberFormat.integer(data.shardsSpent());
            case "best_pickaxe_level" -> String.valueOf(data.bestPickaxeLevel());
            case "pickaxe_refines" -> NumberFormat.integer(data.pickaxeRefines());
            case "tool_upgrades_found" -> NumberFormat.integer(data.toolUpgradesFound());
            case "journal_claimed" -> NumberFormat.integer(data.claimedJournalChapters().size());
            case "journal_total" -> NumberFormat.integer(service.config().journalChapters().size());
            case "journal_claimable" -> NumberFormat.integer(service.claimableJournalChapters(data));
            case "journal_next" -> nextChapter == null ? "Complete" : nextChapter.displayName();
            case "journal_progress" -> nextChapter == null ? "100"
                    : NumberFormat.decimal(service.journalProgressPercent(data, nextChapter));
            case "commissions_active" -> NumberFormat.integer(data.activeCommissions().size());
            case "commissions_completed" -> NumberFormat.integer(data.commissionsCompleted());
            case "commission_active" -> firstCommission == null ? "None" : firstCommission.displayName();
            case "commission_progress" -> firstCommission == null ? "0"
                    : NumberFormat.decimal(service.commissionProgressPercent(data,
                    firstCommissionProgress, firstCommission));
            case "pickaxe_level" -> String.valueOf(service.pickaxeLevel(pickaxe));
            case "pickaxe_xp" -> NumberFormat.decimal(service.pickaxeXp(pickaxe));
            case "pickaxe_xp_required" -> service.pickaxeXpRequired(pickaxe) <= 0.0D
                    ? "MAX" : NumberFormat.decimal(service.pickaxeXpRequired(pickaxe));
            case "pickaxe_progress" -> NumberFormat.decimal(service.pickaxeProgressPercent(pickaxe));
            case "pickaxe_xp_bonus" -> NumberFormat.decimal(service.pickaxeMiningXpBonusPercent(pickaxe));
            case "pickaxe_artifact_bonus" -> NumberFormat.decimal(service.pickaxeArtifactBonusPercent(pickaxe));
            default -> null;
        };
    }
}
