package dev.miningplus.config;

import dev.miningplus.mining.AbilitySettings;
import dev.miningplus.mining.ArtifactDefinition;
import dev.miningplus.mining.ArtifactResearchDefinition;
import dev.miningplus.mining.ArtifactSetDefinition;
import dev.miningplus.mining.AutoSmeltSettings;
import dev.miningplus.mining.BlockReward;
import dev.miningplus.mining.CommissionDefinition;
import dev.miningplus.mining.DropEntry;
import dev.miningplus.mining.FeedbackSettings;
import dev.miningplus.mining.JournalChapter;
import dev.miningplus.mining.JournalObjective;
import dev.miningplus.mining.JournalObjectiveType;
import dev.miningplus.mining.LevelCurve;
import dev.miningplus.mining.LevelUpEffects;
import dev.miningplus.mining.MilestoneReward;
import dev.miningplus.mining.MiningItemTags;
import dev.miningplus.mining.MiningEventSettings;
import dev.miningplus.mining.MiningShopItem;
import dev.miningplus.mining.MoneyReward;
import dev.miningplus.mining.PerkDefinition;
import dev.miningplus.mining.PerkSettings;
import dev.miningplus.mining.PickaxeProgressionSettings;
import dev.miningplus.mining.PointsReward;
import dev.miningplus.mining.ToolUpgradeDefinition;
import dev.miningplus.mining.ToolUpgradeSettings;
import dev.miningplus.mining.VeinMinerSettings;
import dev.miningplus.util.Text;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

public final class MiningPlusConfig {
    private static final List<String> DEFAULT_SECTION_CONFIG_FILES = List.of(
            "sections/progression.yml",
            "sections/mining.yml",
            "sections/abilities-events.yml",
            "sections/artifacts.yml",
            "sections/economy.yml",
            "sections/journal.yml",
            "sections/commissions.yml",
            "sections/shop.yml",
            "sections/interface.yml"
    );

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration defaultConfig;

    private Map<Material, BlockReward> blockRewards = new LinkedHashMap<>();
    private NavigableMap<Integer, MilestoneReward> milestones = new TreeMap<>();
    private NavigableMap<Integer, String> ranks = new TreeMap<>();
    private List<PermissionMultiplier> multipliers = new ArrayList<>();
    private LevelCurve levelCurve = new LevelCurve(100.0D, 1.15D, 100);
    private LevelUpEffects levelUpEffects;
    private VeinMinerSettings veinMiner;
    private AutoSmeltSettings autoSmelt;
    private AbilitySettings abilities;
    private MiningEventSettings miningEvents;
    private PerkSettings perks;
    private FeedbackSettings feedback;
    private ToolUpgradeSettings toolUpgrades;
    private Map<Material, Double> sellPrices = new LinkedHashMap<>();
    private Map<String, Double> customSellPrices = new LinkedHashMap<>();
    private List<JournalChapter> journalChapters = new ArrayList<>();
    private Map<String, Set<String>> materialGroups = new LinkedHashMap<>();
    private List<CommissionDefinition> commissions = new ArrayList<>();
    private List<MiningShopItem> shopItems = new ArrayList<>();
    private List<ArtifactDefinition> artifacts = new ArrayList<>();
    private List<ArtifactSetDefinition> artifactSets = new ArrayList<>();
    private List<ArtifactResearchDefinition> artifactResearch = new ArrayList<>();
    private PickaxeProgressionSettings pickaxeProgression =
            new PickaxeProgressionSettings(true, "miningplus.pickaxe",
                    new LevelCurve(100.0D, 1.2D, 50), 1.0D, 0.05D, 0.01D, 0.005D,
                    true, 500.0D, 75.0D, 50L, 10L, 150.0D);

    public MiningPlusConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        defaultConfig = loadBundledConfig();
        mergeExternalConfigFiles(config, defaultConfig);
        blockRewards = loadBlockRewards(config.getConfigurationSection("blocks"));
        milestones = loadMilestones(config.getConfigurationSection("rewards.milestones"));
        ranks = loadRanks(config.getConfigurationSection("ranks"));
        multipliers = loadMultipliers(config.getConfigurationSection("multipliers.permissions"));
        levelCurve = loadLevelCurve();
        levelUpEffects = loadLevelUpEffects();
        veinMiner = loadVeinMiner();
        autoSmelt = loadAutoSmelt();
        abilities = loadAbilities();
        miningEvents = loadMiningEvents();
        perks = loadPerks();
        feedback = loadFeedback();
        toolUpgrades = loadToolUpgrades();
        sellPrices = loadSellPrices(config.getConfigurationSection("sell.prices"));
        customSellPrices = loadCustomSellPrices(config.getConfigurationSection("sell.custom-prices"));
        materialGroups = loadMaterialGroups(config.getConfigurationSection("material-groups"));
        journalChapters = loadJournalChapters(journalChaptersSection());
        commissions = loadCommissions(config.getConfigurationSection("commissions.definitions"));
        shopItems = loadShopItems(config.getConfigurationSection("shop.items"));
        artifacts = loadArtifacts(config.getConfigurationSection("artifacts.definitions"));
        artifactSets = loadArtifactSets(config.getConfigurationSection("artifacts.codex.sets"));
        artifactResearch = loadArtifactResearch(config.getConfigurationSection("artifacts.codex.research"));
        pickaxeProgression = loadPickaxeProgression();
    }

    // Module toggles
    public boolean levelsEnabled() {
        return config.getBoolean("modules.levels", true);
    }

    public boolean customDropsEnabled() {
        return config.getBoolean("modules.custom-drops", true);
    }

    public boolean rewardsEnabled() {
        return config.getBoolean("modules.rewards", true);
    }

    public boolean antiExploitEnabled() {
        return config.getBoolean("modules.anti-exploit", true);
    }

    public boolean placeholdersEnabled() {
        return config.getBoolean("modules.placeholders", true);
    }

    public boolean veinMinerEnabled() {
        return config.getBoolean("modules.vein-miner", true) && veinMiner.enabled();
    }

    public boolean autoSmeltEnabled() {
        return config.getBoolean("modules.auto-smelt", true) && autoSmelt.enabled();
    }

    public boolean autoPickupEnabled() {
        return config.getBoolean("modules.auto-pickup", true);
    }

    public boolean guiEnabled() {
        return config.getBoolean("modules.gui", true);
    }

    public boolean guiPickaxeRightClickEnabled() {
        return config.getBoolean("gui.open-with-pickaxe-right-click.enabled",
                defaultConfig.getBoolean("gui.open-with-pickaxe-right-click.enabled", true));
    }

    public boolean guiPickaxeRightClickBlocks() {
        return config.getBoolean("gui.open-with-pickaxe-right-click.blocks",
                defaultConfig.getBoolean("gui.open-with-pickaxe-right-click.blocks", false));
    }

    public boolean guiPickaxeRightClickRespectsOffhandPlaceable() {
        return config.getBoolean("gui.open-with-pickaxe-right-click.respect-offhand-placeable",
                defaultConfig.getBoolean("gui.open-with-pickaxe-right-click.respect-offhand-placeable", true));
    }

    public boolean abilitiesEnabled() {
        return config.getBoolean("modules.abilities", true);
    }

    public boolean eventsEnabled() {
        return config.getBoolean("modules.events", true);
    }

    public boolean perksEnabled() {
        return config.getBoolean("modules.perks", true);
    }

    public boolean journalEnabled() {
        return config.getBoolean("modules.journal", config.getBoolean("modules.quests", true));
    }

    public boolean commissionsEnabled() {
        return config.getBoolean("modules.commissions", true);
    }

    public boolean currencyEnabled() {
        return config.getBoolean("modules.currency", true);
    }

    public boolean shopEnabled() {
        return config.getBoolean("modules.shop", true);
    }

    public boolean artifactsEnabled() {
        return config.getBoolean("modules.artifacts", true);
    }

    public boolean pickaxeProgressionEnabled() {
        return config.getBoolean("modules.pickaxe-progression", true) && pickaxeProgression.active();
    }

    public boolean pointsRewardsEnabled() {
        return config.getBoolean("modules.points-rewards", false);
    }

    public boolean completePointObjectivesWithoutPointsPlus() {
        return config.getBoolean("integrations.optional-objectives.complete-points-without-pointsplus", true);
    }

    public boolean completeMoneyObjectivesWithoutVault() {
        return config.getBoolean("integrations.optional-objectives.complete-money-without-vault", true);
    }

    public boolean sellEnabled() {
        return config.getBoolean("modules.sell", true);
    }

    public boolean feedbackEnabled() {
        return config.getBoolean("feedback.enabled", true) && feedback.enabled();
    }

    public Map<Material, Double> sellPrices() {
        return sellPrices;
    }

    public double sellPrice(Material material) {
        return sellPrices.getOrDefault(material, 0.0D);
    }

    public double customSellPrice(String kind, String id) {
        String normalizedKind = normalizeId(kind);
        String normalizedId = normalizeId(id);
        if (normalizedId.isBlank()) {
            return 0.0D;
        }
        double scoped = customSellPrices.getOrDefault(normalizedKind + ":" + normalizedId, 0.0D);
        return scoped > 0.0D ? scoped : customSellPrices.getOrDefault(normalizedId, 0.0D);
    }

    public boolean sellMaterialPricesEnabled() {
        return config.getBoolean("sell.allow-material-prices", true);
    }

    public boolean sellCustomItemsByMaterial() {
        return config.getBoolean("sell.allow-material-price-for-custom-items", false);
    }

    public VeinMinerSettings veinMiner() {
        return veinMiner;
    }

    public AutoSmeltSettings autoSmelt() {
        return autoSmelt;
    }

    public AbilitySettings abilities() {
        return abilities;
    }

    public MiningEventSettings miningEvents() {
        return miningEvents;
    }

    public PerkSettings perks() {
        return perks;
    }

    public FeedbackSettings feedback() {
        return feedback;
    }

    public ToolUpgradeSettings toolUpgrades() {
        return toolUpgrades;
    }

    public PickaxeProgressionSettings pickaxeProgression() {
        return pickaxeProgression;
    }

    public List<ArtifactDefinition> artifacts() {
        return new ArrayList<>(artifacts);
    }

    public ArtifactDefinition artifact(String id) {
        String normalized = normalizeId(id);
        for (ArtifactDefinition artifact : artifacts) {
            if (artifact.id().equals(normalized)) {
                return artifact;
            }
        }
        return null;
    }

    public List<ArtifactSetDefinition> artifactSets() {
        return new ArrayList<>(artifactSets);
    }

    public ArtifactSetDefinition artifactSet(String id) {
        String normalized = normalizeId(id);
        for (ArtifactSetDefinition set : artifactSets) {
            if (set.id().equals(normalized)) {
                return set;
            }
        }
        return null;
    }

    public List<ArtifactResearchDefinition> artifactResearch() {
        return new ArrayList<>(artifactResearch);
    }

    public ArtifactResearchDefinition artifactResearch(String id) {
        String normalized = normalizeId(id);
        for (ArtifactResearchDefinition research : artifactResearch) {
            if (research.id().equals(normalized)) {
                return research;
            }
        }
        return null;
    }

    public int artifactPityBlocks() {
        return Math.max(0, config.getInt("artifacts.pity.blocks-without-artifact", 350));
    }

    public boolean actionBarEnabled() {
        return config.getBoolean("levels.action-bar", true);
    }

    public String guiTitle() {
        return config.getString("gui.title", defaultConfig.getString("gui.title", "&0Mining"));
    }

    public List<String> guiLore(String key) {
        String path = "gui." + key;
        List<String> lines = config.getStringList(path);
        return lines.isEmpty() ? defaultConfig.getStringList(path) : lines;
    }

    public String guiString(String key) {
        String path = "gui." + key;
        String value = config.getString(path);
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = defaultConfig.getString(path);
        return value == null ? "" : value;
    }

    public Material guiMaterial(String key, Material fallback) {
        String path = "gui.materials." + key;
        return matchMaterial(config.getString(path, defaultConfig.getString(path, fallback.name())), fallback);
    }

    public int guiSlot(String key, int fallback) {
        String path = "gui.slots." + key;
        return Math.max(0, config.getInt(path, defaultConfig.getInt(path, fallback)));
    }

    public int guiSize(String key, int fallback) {
        String path = "gui.sizes." + key;
        int size = config.getInt(path, defaultConfig.getInt(path, fallback));
        size = Math.max(9, Math.min(54, size));
        return (size / 9) * 9;
    }

    public List<Integer> guiSlots(String key, List<Integer> fallback) {
        String path = "gui.slots." + key;
        List<Integer> slots = config.getIntegerList(path);
        if (slots.isEmpty()) {
            slots = defaultConfig.getIntegerList(path);
        }
        if (slots.isEmpty()) {
            slots = fallback;
        }
        List<Integer> filtered = new ArrayList<>();
        for (Integer slot : slots) {
            if (slot != null && slot >= 0 && slot < 54 && !filtered.contains(slot)) {
                filtered.add(slot);
            }
        }
        return filtered.isEmpty() ? fallback : filtered;
    }

    public int guiInt(String key, int fallback) {
        String path = "gui." + key;
        return config.getInt(path, defaultConfig.getInt(path, fallback));
    }

    // Levels
    public LevelCurve levelCurve() {
        return levelCurve;
    }

    public double defaultBlockXp() {
        return nonNegative(config.getDouble("levels.default-xp-per-block", 0.0D));
    }

    public boolean levelUpBroadcast() {
        return levelUpEffects.broadcast();
    }

    public LevelUpEffects levelUpEffects() {
        return levelUpEffects;
    }

    public String rankFor(int level) {
        Map.Entry<Integer, String> entry = ranks.floorEntry(level);
        return entry == null ? config.getString("ranks-default", "Novice") : entry.getValue();
    }

    // Multipliers
    public double globalXpMultiplier() {
        return nonNegative(config.getDouble("multipliers.global-xp", 1.0D));
    }

    public double globalMoneyMultiplier() {
        return nonNegative(config.getDouble("multipliers.global-money", 1.0D));
    }

    public List<PermissionMultiplier> permissionMultipliers() {
        return new ArrayList<>(multipliers);
    }

    // Blocks
    public BlockReward blockReward(Material material) {
        return material == null ? null : blockRewards.get(material);
    }

    public Map<Material, BlockReward> blockRewards() {
        return new LinkedHashMap<>(blockRewards);
    }

    public List<JournalChapter> journalChapters() {
        return new ArrayList<>(journalChapters);
    }

    public List<CommissionDefinition> commissions() {
        return new ArrayList<>(commissions);
    }

    public CommissionDefinition commission(String id) {
        if (id == null) {
            return null;
        }
        String normalized = normalizeId(id);
        for (CommissionDefinition commission : commissions) {
            if (commission.id().equals(normalized)) {
                return commission;
            }
        }
        return null;
    }

    public int maxActiveCommissions() {
        return Math.max(1, Math.min(9, config.getInt("commissions.max-active", 3)));
    }

    public JournalChapter journalChapter(String id) {
        if (id == null) {
            return null;
        }
        String normalized = normalizeId(id);
        for (JournalChapter chapter : journalChapters) {
            if (chapter.id().equals(normalized)) {
                return chapter;
            }
        }
        return null;
    }

    public Set<String> materialGroup(String id) {
        Set<String> group = materialGroups.get(normalizeId(id));
        return group == null ? Set.of() : new LinkedHashSet<>(group);
    }

    public List<MiningShopItem> shopItems() {
        return new ArrayList<>(shopItems);
    }

    public MiningShopItem shopItem(String id) {
        if (id == null) {
            return null;
        }
        String normalized = normalizeId(id);
        for (MiningShopItem item : shopItems) {
            if (item.id().equals(normalized)) {
                return item;
            }
        }
        return null;
    }

    public String currencyName(long amount) {
        String path = amount == 1L ? "currency.name-singular" : "currency.name-plural";
        String value = config.getString(path, defaultConfig.getString(path));
        return value == null || value.isBlank() ? (amount == 1L ? "Mine Shard" : "Mine Shards") : value;
    }

    public String formatShards(long amount) {
        return dev.miningplus.util.NumberFormat.integer(amount) + " " + currencyName(amount);
    }

    // Rewards
    public MilestoneReward milestone(int level) {
        return milestones.get(level);
    }

    public NavigableMap<Integer, MilestoneReward> milestones() {
        return new TreeMap<>(milestones);
    }

    // Misc
    public long saveIntervalTicks() {
        return Math.max(20L, safeMultiply(Math.max(1L,
                config.getLong("storage.save-interval-seconds", 300L)), 20L));
    }

    public int leaderboardSize() {
        return Math.max(1, Math.min(100, config.getInt("leaderboard.size", 10)));
    }

    public String prefix() {
        return message("prefix");
    }

    public String message(String key) {
        String path = "messages." + key;
        String message = config.getString(path);
        if (message != null && !message.isBlank()) {
            return message;
        }
        message = defaultConfig.getString(path);
        if (message != null && !message.isBlank()) {
            return message;
        }
        return "Missing message: " + key;
    }

    private LevelCurve loadLevelCurve() {
        double baseXp = Math.max(1.0D, nonNegative(config.getDouble("levels.base-xp", 100.0D)));
        double growth = clamp(config.getDouble("levels.growth", 1.15D), 1.0D, 10.0D);
        int maxLevel = Math.max(1, config.getInt("levels.max-level", 100));
        return new LevelCurve(baseXp, growth, maxLevel);
    }

    private LevelUpEffects loadLevelUpEffects() {
        boolean broadcast = config.getBoolean("levels.level-up.broadcast", false);
        boolean soundEnabled = config.getBoolean("levels.level-up.sound.enabled", true);
        Sound sound = matchSound(config.getString("levels.level-up.sound.type"), Sound.ENTITY_PLAYER_LEVELUP);
        float volume = (float) clamp(config.getDouble("levels.level-up.sound.volume", 1.0D), 0.0D, 4.0D);
        float pitch = (float) clamp(config.getDouble("levels.level-up.sound.pitch", 1.0D), 0.0D, 2.0D);
        boolean particleEnabled = config.getBoolean("levels.level-up.particles.enabled", true);
        Particle particle = matchParticle(config.getString("levels.level-up.particles.type"), Particle.HAPPY_VILLAGER);
        int count = boundedInt("levels.level-up.particles.count", 24, 0, 200);
        return new LevelUpEffects(broadcast, soundEnabled, sound, volume, pitch, particleEnabled, particle, count);
    }

    private VeinMinerSettings loadVeinMiner() {
        boolean enabled = config.getBoolean("vein-miner.enabled", true);
        int maxBlocks = Math.max(1, Math.min(512, config.getInt("vein-miner.max-blocks", 64)));
        boolean requireSneak = config.getBoolean("vein-miner.require-sneak", true);
        boolean damageTool = config.getBoolean("vein-miner.damage-tool", true);
        double hunger = clamp(config.getDouble("vein-miner.hunger-per-block", 0.0D), 0.0D, 20.0D);
        boolean diagonals = config.getBoolean("vein-miner.include-diagonals", true);
        boolean requireMatchingTool = config.getBoolean("vein-miner.require-matching-tool", true);
        Set<Material> tools = materialSet(config.getStringList("vein-miner.tools"));
        Set<Material> blocks = materialSet(config.getStringList("vein-miner.blocks"));
        return new VeinMinerSettings(enabled, maxBlocks, requireSneak, damageTool, hunger,
                diagonals, requireMatchingTool, tools, blocks);
    }

    private AutoSmeltSettings loadAutoSmelt() {
        boolean enabled = config.getBoolean("auto-smelt.enabled", true);
        Map<Material, Material> recipes = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("auto-smelt.recipes");
        if (section != null) {
            for (String rawInput : section.getKeys(false)) {
                Material input = matchMaterial(rawInput, null);
                Material output = matchMaterial(section.getString(rawInput), null);
                if (input == null || output == null || !output.isItem()) {
                    plugin.getLogger().warning("Ignoring auto-smelt recipe '" + rawInput + "': invalid material.");
                    continue;
                }
                recipes.put(input, output);
            }
        }
        return new AutoSmeltSettings(enabled, recipes);
    }

    private AbilitySettings loadAbilities() {
        AbilitySettings.Ability explosive = loadAbility("abilities.explosive",
                "miningplus.ability.explosive", 1.0D);
        AbilitySettings.Ability haste = loadAbility("abilities.haste",
                "miningplus.ability.haste", 1.0D);
        AbilitySettings.Ability bonusXp = loadAbility("abilities.bonus-xp",
                "miningplus.ability.bonusxp", 2.0D);
        AbilitySettings.Ability oreSense = loadAbility("abilities.ore-sense",
                "miningplus.ability.oresense", 6.0D);
        AbilitySettings.Ability stoneguard = loadAbility("abilities.stoneguard",
                "miningplus.ability.stoneguard", 1.0D);
        return new AbilitySettings(explosive, haste, bonusXp, oreSense, stoneguard);
    }

    private AbilitySettings.Ability loadAbility(String path, String defaultPermission, double defaultMagnitude) {
        boolean enabled = config.getBoolean(path + ".enabled", true);
        double chance = clamp(config.getDouble(path + ".chance", 0.1D), 0.0D, 1.0D);
        int unlockLevel = Math.max(1, config.getInt(path + ".unlock-level", 1));
        String permission = config.getString(path + ".permission", defaultPermission);
        double magnitude = nonNegative(config.getDouble(path + ".magnitude", defaultMagnitude));
        int duration = Math.max(0, config.getInt(path + ".duration-ticks", 0));
        return new AbilitySettings.Ability(enabled, chance, unlockLevel, permission, magnitude, duration);
    }

    private MiningEventSettings loadMiningEvents() {
        List<MiningEventSettings.TreasureEvent> treasures =
                loadTreasureEvents(config.getConfigurationSection("events.treasure"));
        List<MiningEventSettings.HazardEvent> hazards =
                loadHazardEvents(config.getConfigurationSection("events.hazards"));
        return new MiningEventSettings(treasures, hazards);
    }

    private PerkSettings loadPerks() {
        int pointsPerLevel = Math.max(0, config.getInt("perks.points-per-level", 1));
        int maxBankedPoints = Math.max(0, config.getInt("perks.max-banked-points", 0));
        List<PerkDefinition> definitions = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("perks.definitions");
        if (section != null) {
            for (String rawId : section.getKeys(false)) {
                ConfigurationSection perkSection = section.getConfigurationSection(rawId);
                if (perkSection == null) {
                    continue;
                }
                String id = normalizeId(rawId);
                Material icon = matchMaterial(perkSection.getString("icon"), Material.EMERALD);
                if (!icon.isItem()) {
                    plugin.getLogger().warning("Ignoring perk icon for '" + id + "': not an item material.");
                    icon = Material.EMERALD;
                }
                definitions.add(new PerkDefinition(
                        id,
                        perkSection.getBoolean("enabled", true),
                        perkSection.getString("display-name", id),
                        icon,
                        Math.max(1, perkSection.getInt("max-level", 5)),
                        Math.max(1, perkSection.getInt("cost-per-level", 1)),
                        Math.max(1, perkSection.getInt("unlock-level", 1)),
                        perkSection.getString("permission", "miningplus.perks"),
                        nonNegative(perkSection.getDouble("effects.xp-multiplier-per-level", 0.0D)),
                        nonNegative(perkSection.getDouble("effects.pickaxe-xp-multiplier-per-level", 0.0D)),
                        nonNegative(perkSection.getDouble("effects.money-multiplier-per-level", 0.0D)),
                        nonNegative(perkSection.getDouble("effects.points-multiplier-per-level", 0.0D)),
                        nonNegative(perkSection.getDouble("effects.shard-multiplier-per-level", 0.0D)),
                        nonNegative(perkSection.getDouble("effects.treasure-chance-per-level", 0.0D)),
                        nonNegative(perkSection.getDouble("effects.artifact-chance-per-level", 0.0D)),
                        clamp(perkSection.getDouble("effects.hazard-chance-reduction-per-level", 0.0D), 0.0D, 1.0D),
                        clamp(perkSection.getDouble("effects.hazard-damage-reduction-per-level", 0.0D), 0.0D, 1.0D)
                ));
            }
        }
        definitions.sort(Comparator.comparing(PerkDefinition::id));
        return new PerkSettings(pointsPerLevel, maxBankedPoints, definitions);
    }

    private FeedbackSettings loadFeedback() {
        boolean enabled = config.getBoolean("feedback.enabled", true);
        int miningCooldownTicks = Math.max(0, config.getInt("feedback.mining.cooldown-ticks", 8));
        return new FeedbackSettings(
                enabled,
                miningCooldownTicks,
                loadFeedbackEffect("feedback.mining", "", "", "",
                        Particle.DUST_PLUME, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.2F, 1.5F, 3),
                loadFeedbackEffect("feedback.artifact", "&#FEE75CArtifact found: &f{artifact}",
                        "&#FEE75CArtifact Found", "&f{artifact}",
                        Particle.VAULT_CONNECTION, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 1.2F, 32),
                loadFeedbackEffect("feedback.sell", "&7Sold mining items for &#57F287{money}", "", "",
                        Particle.HAPPY_VILLAGER, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 1.25F, 12),
                loadFeedbackEffect("feedback.refine", "&#2b98fdPickaxe refined: &f+{gained} XP",
                        "&#2b98fdPickaxe Refined", "&f+{gained} XP",
                        Particle.ELECTRIC_SPARK, Sound.BLOCK_ANVIL_USE, 0.9F, 1.35F, 28),
                loadFeedbackEffect("feedback.journal", "&#57F287Journal chapter claimed: &f{chapter}",
                        "&#57F287Chapter Complete", "&f{chapter}",
                        Particle.FIREWORK, Sound.ENTITY_PLAYER_LEVELUP, 0.9F, 1.15F, 36),
                loadFeedbackEffect("feedback.shop", "&#57F287Purchased {item}", "", "",
                        Particle.HAPPY_VILLAGER, Sound.ENTITY_VILLAGER_YES, 0.8F, 1.15F, 16),
                loadFeedbackEffect("feedback.level-up", "&#57F287Mining level {level}",
                        "&#57F287Mining Level {level}", "&7{rank}",
                        Particle.TOTEM_OF_UNDYING, Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F, 32)
        );
    }

    private FeedbackSettings.Effect loadFeedbackEffect(String path, String defaultActionBar,
                                                       String defaultTitle, String defaultSubtitle,
                                                       Particle defaultParticle, Sound defaultSound,
                                                       float defaultVolume, float defaultPitch, int defaultCount) {
        boolean enabled = config.getBoolean(path + ".enabled", true);
        String actionBar = config.getString(path + ".action-bar", defaultActionBar);
        boolean titleEnabled = config.getBoolean(path + ".title.enabled",
                defaultTitle != null && !defaultTitle.isBlank());
        String title = config.getString(path + ".title.text", defaultTitle);
        String subtitle = config.getString(path + ".title.subtitle", defaultSubtitle);
        int fadeIn = Math.max(0, config.getInt(path + ".title.fade-in-ticks", 5));
        int stay = Math.max(0, config.getInt(path + ".title.stay-ticks", 35));
        int fadeOut = Math.max(0, config.getInt(path + ".title.fade-out-ticks", 10));
        boolean soundEnabled = config.getBoolean(path + ".sound.enabled", true);
        Sound sound = matchSound(config.getString(path + ".sound.type"), defaultSound);
        float volume = (float) clamp(config.getDouble(path + ".sound.volume", defaultVolume), 0.0D, 4.0D);
        float pitch = (float) clamp(config.getDouble(path + ".sound.pitch", defaultPitch), 0.0D, 2.0D);
        boolean particlesEnabled = config.getBoolean(path + ".particles.enabled", true);
        Particle particle = matchParticle(config.getString(path + ".particles.type"), defaultParticle);
        int count = Math.max(0, Math.min(250, config.getInt(path + ".particles.count", defaultCount)));
        double offset = clamp(config.getDouble(path + ".particles.offset", 0.35D), 0.0D, 5.0D);
        double speed = clamp(config.getDouble(path + ".particles.speed", 0.02D), 0.0D, 2.0D);
        return new FeedbackSettings.Effect(enabled, actionBar == null ? "" : actionBar,
                titleEnabled, title == null ? "" : title, subtitle == null ? "" : subtitle,
                fadeIn, stay, fadeOut, soundEnabled, sound, volume, pitch,
                particlesEnabled, particle, count, offset, speed);
    }

    private ToolUpgradeSettings loadToolUpgrades() {
        boolean enabled = config.getBoolean("tool-upgrades.enabled", true);
        boolean require = config.getBoolean("tool-upgrades.require-for-features", true);
        boolean allowMultiple = config.getBoolean("tool-upgrades.enchanting-table.allow-multiple-per-enchant", false);
        double baseChance = clamp(config.getDouble("tool-upgrades.enchanting-table.base-chance", 0.003D), 0.0D, 1.0D);
        double perLevel = clamp(config.getDouble("tool-upgrades.enchanting-table.chance-per-level", 0.00055D), 0.0D, 1.0D);
        double maxChance = clamp(config.getDouble("tool-upgrades.enchanting-table.max-chance", 0.025D), 0.0D, 1.0D);
        int pityEnchants = Math.max(0, config.getInt("tool-upgrades.enchanting-table.pity.enchants-without-upgrade", 18));
        int pityMinimumEnchantLevel = Math.max(1, config.getInt("tool-upgrades.enchanting-table.pity.minimum-enchant-level", 20));
        List<ToolUpgradeDefinition> definitions = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("tool-upgrades.definitions");
        if (section == null) {
            definitions.add(defaultToolUpgrade("vein-miner", "&#57F287Vein Miner",
                    List.of("&7Break connected ore veins in one swing."), "&#EB459ELegendary", 0.65D));
            definitions.add(defaultToolUpgrade("auto-smelt", "&#FEE75CAuto Smelt",
                    List.of("&7Smelt configured ore drops as you mine."), "&#FEE75CEpic", 1.0D));
            definitions.add(defaultToolUpgrade("auto-pickup", "&#2b98fdAuto Pickup",
                    List.of("&7Send mined drops straight into your inventory."), "&#2b98fdRare", 1.0D));
            definitions.add(defaultToolUpgrade("reinforced", "&#57F287Reinforced",
                    List.of("&7Reduces extra durability wear from vein mining."), "&#2b98fdRare", 0.75D));
            definitions.add(defaultToolUpgrade("luminous", "&#FEE75CLuminous",
                    List.of("&7Keeps your vision clear while mining ore."), "&#2b98fdRare", 0.7D));
            definitions.add(defaultToolUpgrade("prospector", "&#EB459EProspector",
                    List.of("&7Slightly improves artifact find chance."), "&#FEE75CEpic", 0.55D));
            definitions.add(defaultToolUpgrade("shard-magnet", "&#57F287Shard Magnet",
                    List.of("&7Increases Mine Shards earned from mining rewards."), "&#FEE75CEpic", 0.6D));
        } else {
            for (String rawId : section.getKeys(false)) {
                ConfigurationSection upgradeSection = section.getConfigurationSection(rawId);
                if (upgradeSection == null) {
                    continue;
                }
                String id = normalizeId(rawId);
                definitions.add(new ToolUpgradeDefinition(
                    id,
                    upgradeSection.getBoolean("enabled", true),
                    upgradeSection.getString("display-name", rawId),
                    upgradeSection.getStringList("lore"),
                    upgradeSection.getString("rarity", "&#2b98fdRare"),
                    nonNegative(upgradeSection.getDouble("weight", 1.0D))
            ));
            }
        }
        definitions.sort(Comparator.comparing(ToolUpgradeDefinition::id));
        return new ToolUpgradeSettings(enabled, require, allowMultiple,
                baseChance, perLevel, Math.max(baseChance, maxChance), pityEnchants, pityMinimumEnchantLevel, definitions);
    }

    private ToolUpgradeDefinition defaultToolUpgrade(String id, String displayName, List<String> lore,
                                                     String rarity, double weight) {
        return new ToolUpgradeDefinition(id, true, displayName, lore, rarity, weight);
    }

    private PickaxeProgressionSettings loadPickaxeProgression() {
        boolean enabled = config.getBoolean("pickaxe-progression.enabled", true);
        String permission = config.getString("pickaxe-progression.permission", "miningplus.pickaxe");
        double baseXp = Math.max(1.0D, nonNegative(config.getDouble("pickaxe-progression.base-xp", 100.0D)));
        double growth = clamp(config.getDouble("pickaxe-progression.growth", 1.2D), 1.0D, 10.0D);
        int maxLevel = Math.max(1, config.getInt("pickaxe-progression.max-level", 50));
        double xpPerBlock = nonNegative(config.getDouble("pickaxe-progression.xp-per-reward-block", 1.0D));
        double xpFromMiningXp = nonNegative(config.getDouble("pickaxe-progression.xp-from-mining-xp", 0.05D));
        double miningXpMultiplier = nonNegative(config.getDouble(
                "pickaxe-progression.bonuses.mining-xp-multiplier-per-level", 0.01D));
        double artifactChance = nonNegative(config.getDouble(
                "pickaxe-progression.bonuses.artifact-chance-per-level", 0.005D));
        boolean refineEnabled = config.getBoolean("pickaxe-progression.refine.enabled", true);
        double refineMoneyBase = nonNegative(config.getDouble("pickaxe-progression.refine.money-base", 500.0D));
        double refineMoneyPerLevel = nonNegative(config.getDouble("pickaxe-progression.refine.money-per-level", 75.0D));
        long refineShardBase = Math.max(0L, config.getLong("pickaxe-progression.refine.shards-base", 50L));
        long refineShardPerLevel = Math.max(0L, config.getLong("pickaxe-progression.refine.shards-per-level", 10L));
        double refineXp = nonNegative(config.getDouble("pickaxe-progression.refine.xp", 150.0D));
        return new PickaxeProgressionSettings(enabled, permission, new LevelCurve(baseXp, growth, maxLevel),
                xpPerBlock, xpFromMiningXp, miningXpMultiplier, artifactChance,
                refineEnabled, refineMoneyBase, refineMoneyPerLevel, refineShardBase, refineShardPerLevel, refineXp);
    }

    private List<MiningEventSettings.TreasureEvent> loadTreasureEvents(ConfigurationSection section) {
        List<MiningEventSettings.TreasureEvent> events = new ArrayList<>();
        if (section == null) {
            return events;
        }
        for (String rawId : section.getKeys(false)) {
            ConfigurationSection eventSection = section.getConfigurationSection(rawId);
            if (eventSection == null) {
                continue;
            }
            String id = normalizeId(rawId);
            events.add(new MiningEventSettings.TreasureEvent(
                    loadEventTrigger(id, eventSection, "miningplus.event.treasure"),
                    loadMoneyReward(eventSection.getConfigurationSection("money")),
                    loadPointsReward(eventSection.getConfigurationSection("points")),
                    loadPointsReward(eventSection.getConfigurationSection("shards")),
                    loadDrops("treasure event " + id, eventSection.getConfigurationSection("drops")),
                    eventSection.getStringList("commands"),
                    eventSection.getString("message", ""),
                    loadEventEffects(eventSection, Particle.HAPPY_VILLAGER, Sound.ENTITY_EXPERIENCE_ORB_PICKUP),
                    loadEventActions(eventSection.getConfigurationSection("actions"), "treasure event " + id)
            ));
        }
        return events;
    }

    private List<MiningEventSettings.HazardEvent> loadHazardEvents(ConfigurationSection section) {
        List<MiningEventSettings.HazardEvent> events = new ArrayList<>();
        if (section == null) {
            return events;
        }
        for (String rawId : section.getKeys(false)) {
            ConfigurationSection eventSection = section.getConfigurationSection(rawId);
            if (eventSection == null) {
                continue;
            }
            String id = normalizeId(rawId);
            events.add(new MiningEventSettings.HazardEvent(
                    loadEventTrigger(id, eventSection, "miningplus.event.hazard"),
                    nonNegative(eventSection.getDouble("damage", 0.0D)),
                    Math.max(0, eventSection.getInt("fire-ticks", 0)),
                    clamp(eventSection.getDouble("exhaustion", 0.0D), 0.0D, 40.0D),
                    matchPotionEffectType(eventSection.getString("effect.type"), PotionEffectType.MINING_FATIGUE),
                    Math.max(0, eventSection.getInt("effect.duration-ticks", 0)),
                    Math.max(0, eventSection.getInt("effect.amplifier", 0)),
                    eventSection.getString("message", ""),
                    loadEventEffects(eventSection, Particle.CLOUD, Sound.BLOCK_GRAVEL_BREAK),
                    loadEventActions(eventSection.getConfigurationSection("actions"), "hazard event " + id)
            ));
        }
        return events;
    }

    private List<MiningEventSettings.EventAction> loadEventActions(ConfigurationSection section, String context) {
        List<MiningEventSettings.EventAction> actions = new ArrayList<>();
        if (section == null) {
            return actions;
        }
        for (String rawId : section.getKeys(false)) {
            ConfigurationSection actionSection = section.getConfigurationSection(rawId);
            if (actionSection == null) {
                continue;
            }
            String id = normalizeId(rawId);
            MiningEventSettings.EventActionType type =
                    MiningEventSettings.EventActionType.from(actionSection.getString("type", "scan-ores"));
            Set<String> groups = new LinkedHashSet<>();
            for (String rawGroup : actionSection.getStringList("groups")) {
                String group = normalizeId(rawGroup);
                if (group.isBlank()) {
                    continue;
                }
                if (materialGroup(group).isEmpty()) {
                    plugin.getLogger().warning("Ignoring unknown event action group '" + rawGroup
                            + "' in " + context + "." + rawId + ".");
                    continue;
                }
                groups.add(group);
            }
            EntityType entityType = matchEntityType(actionSection.getString("entity.type"), EntityType.ZOMBIE);
            ConfigurationSection reward = actionSection.getConfigurationSection("reward");
            actions.add(new MiningEventSettings.EventAction(
                    id,
                    type,
                    actionSection.getString("message", ""),
                    actionSection.getString("fail-message", ""),
                    actionSection.getString("complete-message", ""),
                    Math.max(1, actionSection.getInt("radius", 8)),
                    Math.max(0, Math.min(24, actionSection.getInt("markers", actionSection.getInt("count", 6)))),
                    Math.max(20, actionSection.getInt("duration-ticks", 600)),
                    Math.max(1, actionSection.getInt("required-blocks", 3)),
                    materialSet(actionSection.getStringList("blocks")),
                    groups,
                    entityType,
                    actionSection.getString("entity.name", ""),
                    Math.max(1, Math.min(8, actionSection.getInt("entity.count", 1))),
                    nonNegative(actionSection.getDouble("entity.health", 0.0D)),
                    nonNegative(reward == null ? 0.0D : reward.getDouble("xp", 0.0D)),
                    nonNegative(reward == null ? 0.0D : reward.getDouble("pickaxe-xp", 0.0D)),
                    loadMoneyReward(reward == null ? null : reward.getConfigurationSection("money")),
                    loadPointsReward(reward == null ? null : reward.getConfigurationSection("points")),
                    loadPointsReward(reward == null ? null : reward.getConfigurationSection("shards")),
                    loadRewardItems(reward, context + "." + rawId),
                    loadDrops(context + "." + rawId, reward == null ? null : reward.getConfigurationSection("drops")),
                    reward == null ? List.of() : reward.getStringList("commands"),
                    loadEventEffects(actionSection, Particle.ELECTRIC_SPARK, Sound.BLOCK_AMETHYST_BLOCK_CHIME)
            ));
        }
        return actions;
    }

    private MiningEventSettings.Trigger loadEventTrigger(String id, ConfigurationSection section, String defaultPermission) {
        boolean enabled = section.getBoolean("enabled", true);
        double chance = clamp(section.getDouble("chance", 0.0D), 0.0D, 1.0D);
        int unlockLevel = Math.max(1, section.getInt("unlock-level", 1));
        String permission = section.getString("permission", defaultPermission);
        Set<Material> blocks = materialSet(section.getStringList("blocks"));
        return new MiningEventSettings.Trigger(id, enabled, chance, unlockLevel, permission, blocks);
    }

    private MiningEventSettings.EventEffects loadEventEffects(
            ConfigurationSection section, Particle defaultParticle, Sound defaultSound) {
        Particle particle = matchParticle(section.getString("particles.type"), defaultParticle);
        int particleCount = Math.max(0, Math.min(200, section.getInt("particles.count", 12)));
        Sound sound = matchSound(section.getString("sound.type"), defaultSound);
        float volume = (float) clamp(section.getDouble("sound.volume", 1.0D), 0.0D, 4.0D);
        float pitch = (float) clamp(section.getDouble("sound.pitch", 1.0D), 0.0D, 2.0D);
        return new MiningEventSettings.EventEffects(particle, particleCount, sound, volume, pitch);
    }

    private Set<Material> materialSet(List<String> values) {
        Set<Material> materials = new LinkedHashSet<>();
        for (String value : values) {
            Material material = matchMaterial(value, null);
            if (material == null) {
                plugin.getLogger().warning("Ignoring unknown material in config: " + value);
                continue;
            }
            materials.add(material);
        }
        return materials;
    }

    private Map<Material, BlockReward> loadBlockRewards(ConfigurationSection section) {
        Map<Material, BlockReward> loaded = new LinkedHashMap<>();
        if (section == null) {
            plugin.getLogger().warning("No mining blocks are configured.");
            return loaded;
        }
        for (String rawKey : section.getKeys(false)) {
            ConfigurationSection blockSection = section.getConfigurationSection(rawKey);
            if (blockSection == null) {
                continue;
            }
            Material material = matchMaterial(rawKey, null);
            if (material == null || !material.isBlock()) {
                plugin.getLogger().warning("Ignoring block reward '" + rawKey + "': not a valid block material.");
                continue;
            }
            double xp = nonNegative(blockSection.getDouble("xp", defaultBlockXp()));
            MoneyReward money = loadMoneyReward(blockSection.getConfigurationSection("money"));
            PointsReward points = loadPointsReward(blockSection.getConfigurationSection("points"));
            PointsReward shards = loadPointsReward(blockSection.getConfigurationSection("shards"));
            List<DropEntry> drops = loadDrops(rawKey, blockSection.getConfigurationSection("drops"));
            boolean overrideDrops = blockSection.getBoolean("override-vanilla-drops", false);
            loaded.put(material, new BlockReward(material, xp, money, points, shards, drops, overrideDrops));
        }
        return loaded;
    }

    private MoneyReward loadMoneyReward(ConfigurationSection section) {
        if (section == null) {
            return MoneyReward.NONE;
        }
        double min = nonNegative(section.getDouble("min", 0.0D));
        double max = Math.max(min, nonNegative(section.getDouble("max", min)));
        double chance = clamp(section.getDouble("chance", 1.0D), 0.0D, 1.0D);
        return new MoneyReward(min, max, chance);
    }

    private Map<Material, Double> loadSellPrices(ConfigurationSection section) {
        Map<Material, Double> prices = new LinkedHashMap<>();
        if (section == null) {
            return prices;
        }
        for (String rawKey : section.getKeys(false)) {
            Material material = matchMaterial(rawKey, null);
            if (material == null || !material.isItem()) {
                plugin.getLogger().warning("Ignoring sell price '" + rawKey + "': not a valid item material.");
                continue;
            }
            double price = nonNegative(section.getDouble(rawKey, 0.0D));
            if (price > 0.0D) {
                prices.put(material, price);
            }
        }
        return prices;
    }

    private Map<String, Double> loadCustomSellPrices(ConfigurationSection section) {
        Map<String, Double> prices = new LinkedHashMap<>();
        if (section == null) {
            return prices;
        }
        for (String rawKey : section.getKeys(false)) {
            String id = normalizeCustomSellId(rawKey);
            if (id.isBlank()) {
                continue;
            }
            double price = nonNegative(section.getDouble(rawKey, 0.0D));
            if (price > 0.0D) {
                prices.put(id, price);
            }
        }
        return prices;
    }

    private List<ArtifactDefinition> loadArtifacts(ConfigurationSection section) {
        List<ArtifactDefinition> loaded = new ArrayList<>();
        if (section == null) {
            return loaded;
        }
        for (String rawId : section.getKeys(false)) {
            ConfigurationSection artifactSection = section.getConfigurationSection(rawId);
            if (artifactSection == null) {
                continue;
            }
            String id = normalizeId(rawId);
            Material material = matchMaterial(artifactSection.getString("material"), Material.AMETHYST_SHARD);
            if (material == null || !material.isItem() || material.isAir()) {
                plugin.getLogger().warning("Ignoring artifact '" + rawId + "': invalid material.");
                continue;
            }
            ItemStack item = buildDropItem(artifactSection, material);
            MiningItemTags.tagCustomItem(plugin, item, "artifact", id);
            int minAmount = Math.max(1, artifactSection.getInt("min-amount", artifactSection.getInt("amount", 1)));
            int maxAmount = Math.max(minAmount, artifactSection.getInt("max-amount", minAmount));
            Set<String> groups = new LinkedHashSet<>();
            for (String group : artifactSection.getStringList("groups")) {
                String normalized = normalizeId(group);
                if (materialGroup(normalized).isEmpty()) {
                    plugin.getLogger().warning("Ignoring artifact group '" + group + "' for '" + rawId + "': unknown group.");
                    continue;
                }
                groups.add(normalized);
            }
            loaded.add(new ArtifactDefinition(
                    id,
                    artifactSection.getBoolean("enabled", true),
                    artifactSection.getString("display-name", rawId),
                    item,
                    minAmount,
                    maxAmount,
                    clamp(artifactSection.getDouble("chance", 0.0D), 0.0D, 1.0D),
                    artifactSection.getBoolean("affected-by-fortune", false),
                    Math.max(1, artifactSection.getInt("unlock-level", 1)),
                    artifactSection.getString("permission", "miningplus.artifacts"),
                    normalizeId(artifactSection.getString("codex.set", artifactSection.getString("set", ""))),
                    Math.max(0L, artifactSection.getLong("codex.fragment-value",
                            artifactSection.getLong("fragment-value",
                                    config.getLong("artifacts.codex.default-fragment-value", 1L)))),
                    materialSet(artifactSection.getStringList("blocks")),
                    groups,
                    loadMoneyReward(artifactSection.getConfigurationSection("reward.money")),
                    loadPointsReward(artifactSection.getConfigurationSection("reward.points")),
                    loadPointsReward(artifactSection.getConfigurationSection("reward.shards")),
                    artifactSection.getStringList("reward.commands")
            ));
        }
        loaded.sort(Comparator.comparing(ArtifactDefinition::unlockLevel).thenComparing(ArtifactDefinition::id));
        return loaded;
    }

    private List<ArtifactSetDefinition> loadArtifactSets(ConfigurationSection section) {
        List<ArtifactSetDefinition> loaded = new ArrayList<>();
        if (section == null) {
            return loaded;
        }
        for (String rawId : section.getKeys(false)) {
            ConfigurationSection setSection = section.getConfigurationSection(rawId);
            if (setSection == null) {
                continue;
            }
            String id = normalizeId(rawId);
            Set<String> artifactIds = new LinkedHashSet<>();
            for (String rawArtifact : setSection.getStringList("artifacts")) {
                String artifactId = normalizeId(rawArtifact);
                if (artifactId.isBlank()) {
                    continue;
                }
                if (artifact(artifactId) == null) {
                    plugin.getLogger().warning("Ignoring unknown artifact '" + rawArtifact
                            + "' in artifact set '" + rawId + "'.");
                    continue;
                }
                artifactIds.add(artifactId);
            }
            if (artifactIds.isEmpty()) {
                for (ArtifactDefinition artifact : artifacts) {
                    if (artifact.setId().equals(id)) {
                        artifactIds.add(artifact.id());
                    }
                }
            }
            if (artifactIds.isEmpty()) {
                plugin.getLogger().warning("Ignoring artifact set '" + rawId + "': no valid artifacts.");
                continue;
            }
            ConfigurationSection effects = setSection.getConfigurationSection("effects");
            loaded.add(new ArtifactSetDefinition(
                    id,
                    setSection.getBoolean("enabled", true),
                    setSection.getString("display-name", rawId),
                    setSection.getStringList("lore"),
                    artifactIds,
                    nonNegative(effects == null ? 0.0D : effects.getDouble("mining-xp-multiplier", 0.0D)),
                    nonNegative(effects == null ? 0.0D : effects.getDouble("pickaxe-xp-multiplier", 0.0D)),
                    nonNegative(effects == null ? 0.0D : effects.getDouble("money-multiplier", 0.0D)),
                    nonNegative(effects == null ? 0.0D : effects.getDouble("points-multiplier", 0.0D)),
                    nonNegative(effects == null ? 0.0D : effects.getDouble("shard-multiplier", 0.0D)),
                    nonNegative(effects == null ? 0.0D : effects.getDouble("treasure-chance", 0.0D)),
                    nonNegative(effects == null ? 0.0D : effects.getDouble("artifact-chance", 0.0D)),
                    clamp(effects == null ? 0.0D : effects.getDouble("hazard-chance-reduction", 0.0D), 0.0D, 1.0D),
                    clamp(effects == null ? 0.0D : effects.getDouble("hazard-damage-reduction", 0.0D), 0.0D, 1.0D)
            ));
        }
        loaded.sort(Comparator.comparing(ArtifactSetDefinition::id));
        return loaded;
    }

    private List<ArtifactResearchDefinition> loadArtifactResearch(ConfigurationSection section) {
        List<ArtifactResearchDefinition> loaded = new ArrayList<>();
        if (section == null) {
            return loaded;
        }
        for (String rawId : section.getKeys(false)) {
            ConfigurationSection researchSection = section.getConfigurationSection(rawId);
            if (researchSection == null) {
                continue;
            }
            String id = normalizeId(rawId);
            Material icon = matchMaterial(researchSection.getString("icon"), Material.WRITABLE_BOOK);
            if (icon == null || !icon.isItem()) {
                icon = Material.WRITABLE_BOOK;
            }
            Set<String> requiredSets = new LinkedHashSet<>();
            for (String rawSet : researchSection.getStringList("required-sets")) {
                String setId = normalizeId(rawSet);
                if (setId.isBlank()) {
                    continue;
                }
                if (artifactSet(setId) == null) {
                    plugin.getLogger().warning("Ignoring unknown artifact set '" + rawSet
                            + "' in artifact research '" + rawId + "'.");
                    continue;
                }
                requiredSets.add(setId);
            }
            ConfigurationSection reward = researchSection.getConfigurationSection("reward");
            loaded.add(new ArtifactResearchDefinition(
                    id,
                    researchSection.getBoolean("enabled", true),
                    researchSection.getString("display-name", rawId),
                    icon,
                    researchSection.getStringList("lore"),
                    Math.max(1, researchSection.getInt("unlock-level", 1)),
                    requiredSets,
                    Math.max(0L, researchSection.getLong("cost", 0L)),
                    loadRewardItems(reward, "artifact research '" + rawId + "'"),
                    nonNegative(reward == null ? 0.0D : reward.getDouble("xp", 0.0D)),
                    nonNegative(reward == null ? 0.0D : reward.getDouble("pickaxe-xp", 0.0D)),
                    nonNegative(reward == null ? 0.0D : reward.getDouble("money", 0.0D)),
                    Math.max(0L, reward == null ? 0L : reward.getLong("points", 0L)),
                    Math.max(0L, reward == null ? 0L : reward.getLong("shards", 0L)),
                    reward == null ? List.of() : reward.getStringList("commands")
            ));
        }
        loaded.sort(Comparator.comparing(ArtifactResearchDefinition::unlockLevel)
                .thenComparing(ArtifactResearchDefinition::id));
        return loaded;
    }

    private List<JournalChapter> loadJournalChapters(ConfigurationSection section) {
        List<JournalChapter> loaded = new ArrayList<>();
        if (section == null) {
            return loaded;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection chapterSection = section.getConfigurationSection(key);
            if (chapterSection == null) {
                continue;
            }
            String id = normalizeId(key);
            List<JournalObjective> objectives = loadJournalObjectives(
                    chapterSection.getConfigurationSection("objectives"), key);
            if (objectives.isEmpty()) {
                plugin.getLogger().warning("Ignoring journal chapter '" + key + "': no objectives.");
                continue;
            }
            Material icon = matchMaterial(chapterSection.getString("icon"), Material.WRITTEN_BOOK);
            if (icon == null || !icon.isItem()) {
                icon = Material.WRITTEN_BOOK;
            }
            loaded.add(new JournalChapter(
                    id,
                    chapterSection.getString("display-name", key),
                    icon,
                    chapterSection.getStringList("lore"),
                    normalizeIds(chapterSection.getStringList("required-chapters")),
                    objectives,
                    nonNegative(chapterSection.getDouble("rewards.xp", 0.0D)),
                    nonNegative(chapterSection.getDouble("rewards.money", 0.0D)),
                    Math.max(0L, chapterSection.getLong("rewards.points", 0L)),
                    Math.max(0L, chapterSection.getLong("rewards.shards", 0L)),
                    Math.max(0, chapterSection.getInt("rewards.perk-points", 0)),
                    loadRewardItems(chapterSection.getConfigurationSection("rewards"), "journal chapter '" + key + "'"),
                    chapterSection.getStringList("rewards.commands")
            ));
        }
        return loaded;
    }

    private List<CommissionDefinition> loadCommissions(ConfigurationSection section) {
        List<CommissionDefinition> loaded = new ArrayList<>();
        if (section == null) {
            return loaded;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection commissionSection = section.getConfigurationSection(key);
            if (commissionSection == null) {
                continue;
            }
            String id = normalizeId(key);
            List<JournalObjective> objectives = loadJournalObjectives(
                    commissionSection.getConfigurationSection("objectives"), "commission " + key);
            if (objectives.isEmpty()) {
                plugin.getLogger().warning("Ignoring commission '" + key + "': no objectives.");
                continue;
            }
            Material icon = matchMaterial(commissionSection.getString("icon"), Material.FILLED_MAP);
            if (icon == null || !icon.isItem()) {
                icon = Material.FILLED_MAP;
            }
            loaded.add(new CommissionDefinition(
                    id,
                    commissionSection.getBoolean("enabled", true),
                    commissionSection.getString("display-name", key),
                    icon,
                    commissionSection.getStringList("lore"),
                    Math.max(1, commissionSection.getInt("unlock-level", 1)),
                    normalizeIds(commissionSection.getStringList("required-chapters")),
                    objectives,
                    nonNegative(commissionSection.getDouble("rewards.xp", 0.0D)),
                    nonNegative(commissionSection.getDouble("rewards.pickaxe-xp", 0.0D)),
                    nonNegative(commissionSection.getDouble("rewards.money", 0.0D)),
                    Math.max(0L, commissionSection.getLong("rewards.points", 0L)),
                    Math.max(0L, commissionSection.getLong("rewards.shards", 0L)),
                    Math.max(0, commissionSection.getInt("rewards.perk-points", 0)),
                    loadRewardItems(commissionSection.getConfigurationSection("rewards"),
                            "commission '" + key + "'"),
                    loadToolUpgradeRewards(commissionSection.getStringList("rewards.tool-upgrades"),
                            "commission '" + key + "'"),
                    commissionSection.getStringList("rewards.commands")
            ));
        }
        loaded.sort(Comparator.comparing(CommissionDefinition::unlockLevel).thenComparing(CommissionDefinition::id));
        return loaded;
    }

    private List<JournalObjective> loadJournalObjectives(ConfigurationSection section, String chapterKey) {
        List<JournalObjective> objectives = new ArrayList<>();
        if (section == null) {
            return objectives;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection objectiveSection = section.getConfigurationSection(key);
            if (objectiveSection == null) {
                continue;
            }
            JournalObjectiveType type = JournalObjectiveType.from(objectiveSection.getString("type", "mine-blocks"));
            String target = normalizeId(objectiveSection.getString("target", ""));
            if (type == JournalObjectiveType.MINE_BLOCK) {
                Material material = matchMaterial(objectiveSection.getString("target"), null);
                if (material == null || !material.isBlock()) {
                    plugin.getLogger().warning("Ignoring journal objective '" + chapterKey + "." + key
                            + "': unknown block target '" + target + "'.");
                    continue;
                }
                target = normalizeId(material.name());
            } else if (type == JournalObjectiveType.MINE_GROUP) {
                if (materialGroup(target).isEmpty()) {
                    plugin.getLogger().warning("Ignoring journal objective '" + chapterKey + "." + key
                            + "': unknown material group '" + target + "'.");
                    continue;
                }
            } else if (type == JournalObjectiveType.FIND_TOOL_UPGRADE) {
                if (target.isBlank() || toolUpgrades.definition(target) == null) {
                    plugin.getLogger().warning("Ignoring journal objective '" + chapterKey + "." + key
                            + "': unknown tool upgrade target '" + target + "'.");
                    continue;
                }
            }
            long amount = Math.max(1L, objectiveSection.getLong("amount", 1L));
            objectives.add(new JournalObjective(
                    normalizeId(key),
                    type,
                    target,
                    amount,
                    objectiveSection.getString("description", defaultObjectiveDescription(type, target, amount))
            ));
        }
        return objectives;
    }

    private String defaultObjectiveDescription(JournalObjectiveType type, String target, long amount) {
        return switch (type) {
            case MINE_BLOCKS -> "Mine " + amount + " reward blocks";
            case MINE_BLOCK -> "Mine " + amount + " " + target;
            case MINE_GROUP -> "Mine " + amount + " " + target;
            case REACH_LEVEL -> "Reach mining level " + amount;
            case SELL_ITEMS -> "Sell " + amount + " mining items";
            case EARN_POINTS -> "Earn " + amount + " mining points";
            case EARN_SHARDS -> "Earn " + amount + " mine shards";
            case EARN_MONEY -> "Earn " + amount + " mining money";
            case SPEND_SHARDS -> "Spend " + amount + " mine shards";
            case SPEND_ARTIFACT_FRAGMENTS -> "Spend " + amount + " artifact fragments";
            case BUY_PERKS -> "Buy " + amount + " mining perk upgrades";
            case FIND_TREASURES -> "Find " + amount + " treasure events";
            case FIND_ARTIFACTS -> "Find " + amount + " mining artifacts";
            case REACH_PICKAXE_LEVEL -> "Reach pickaxe level " + amount;
            case REFINE_PICKAXE -> "Refine a pickaxe " + amount + " times";
            case FIND_TOOL_UPGRADES -> "Find " + amount + " pickaxe upgrades";
            case FIND_TOOL_UPGRADE -> "Find the " + target + " pickaxe upgrade";
            case SURVIVE_HAZARDS -> "Survive " + amount + " mining hazards";
            case COMPLETE_EVENT_CHALLENGES -> "Complete " + amount + " mining event challenges";
            case DEFEAT_ENCOUNTERS -> "Defeat " + amount + " mining encounters";
        };
    }

    private Map<String, Set<String>> loadMaterialGroups(ConfigurationSection section) {
        Map<String, Set<String>> loaded = new LinkedHashMap<>();
        if (section == null) {
            return loaded;
        }
        for (String rawId : section.getKeys(false)) {
            Set<String> materials = new LinkedHashSet<>();
            for (String rawMaterial : section.getStringList(rawId)) {
                Material material = matchMaterial(rawMaterial, null);
                if (material == null || !material.isBlock()) {
                    plugin.getLogger().warning("Ignoring material group entry '" + rawMaterial
                            + "' in '" + rawId + "': not a valid block.");
                    continue;
                }
                materials.add(normalizeId(material.name()));
            }
            if (!materials.isEmpty()) {
                loaded.put(normalizeId(rawId), materials);
            }
        }
        return loaded;
    }

    private List<MiningShopItem> loadShopItems(ConfigurationSection section) {
        List<MiningShopItem> loaded = new ArrayList<>();
        if (section == null) {
            return loaded;
        }
        for (String rawId : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(rawId);
            if (itemSection == null) {
                continue;
            }
            String id = normalizeId(rawId);
            Material icon = matchMaterial(itemSection.getString("icon"), Material.EMERALD);
            if (icon == null || !icon.isItem()) {
                icon = Material.EMERALD;
            }
            loaded.add(new MiningShopItem(
                    id,
                    itemSection.getBoolean("enabled", true),
                    itemSection.getString("display-name", rawId),
                    icon,
                    itemSection.getStringList("lore"),
                    Math.max(0L, itemSection.getLong("cost", 0L)),
                    Math.max(1, itemSection.getInt("unlock-level", 1)),
                    normalizeIds(itemSection.getStringList("required-chapters")),
                    loadRewardItems(itemSection.getConfigurationSection("reward"), "shop item '" + rawId + "'"),
                    nonNegative(itemSection.getDouble("reward.xp", 0.0D)),
                    nonNegative(itemSection.getDouble("reward.pickaxe-xp", 0.0D)),
                    nonNegative(itemSection.getDouble("reward.money", 0.0D)),
                    Math.max(0L, itemSection.getLong("reward.points", 0L)),
                    Math.max(0, itemSection.getInt("reward.perk-points", 0)),
                    loadToolUpgradeRewards(itemSection.getStringList("reward.tool-upgrades"),
                            "shop item '" + rawId + "'"),
                    itemSection.getStringList("reward.commands")
            ));
        }
        loaded.sort(Comparator.comparing(MiningShopItem::unlockLevel).thenComparing(MiningShopItem::id));
        return loaded;
    }

    private List<String> loadToolUpgradeRewards(List<String> rawIds, String context) {
        List<String> ids = new ArrayList<>();
        for (String rawId : rawIds) {
            String id = normalizeId(rawId);
            if (id.isBlank() || ids.contains(id)) {
                continue;
            }
            ToolUpgradeDefinition definition = toolUpgrades.definition(id);
            if (definition == null || !definition.active()) {
                plugin.getLogger().warning("Ignoring unknown or inactive tool upgrade reward '" + rawId
                        + "' in " + context + ".");
                continue;
            }
            ids.add(id);
        }
        return ids;
    }

    private List<ItemStack> loadRewardItems(ConfigurationSection rewardSection, String context) {
        List<ItemStack> items = new ArrayList<>();
        if (rewardSection == null) {
            return items;
        }
        ItemStack single = loadRewardItem(rewardSection.getConfigurationSection("item"), context + " reward.item");
        if (single != null) {
            items.add(single);
        }
        ConfigurationSection itemsSection = rewardSection.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String rawId : itemsSection.getKeys(false)) {
                ItemStack item = loadRewardItem(itemsSection.getConfigurationSection(rawId),
                        context + " reward.items." + rawId);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        return items;
    }

    private ItemStack loadRewardItem(ConfigurationSection section, String context) {
        if (section == null) {
            return null;
        }
        Material material = matchMaterial(section.getString("material"), null);
        if (material == null || !material.isItem() || material.isAir()) {
            plugin.getLogger().warning("Ignoring " + context + ": invalid material.");
            return null;
        }
        ItemStack item = buildDropItem(section, material);
        item.setAmount(Math.max(1, Math.min(material.getMaxStackSize(), section.getInt("amount", 1))));
        return item;
    }

    private PointsReward loadPointsReward(ConfigurationSection section) {
        if (section == null) {
            return PointsReward.NONE;
        }
        long min = Math.max(0L, section.getLong("min", 0L));
        long max = Math.max(min, section.getLong("max", min));
        double chance = clamp(section.getDouble("chance", 1.0D), 0.0D, 1.0D);
        return new PointsReward(min, max, chance);
    }

    private List<DropEntry> loadDrops(String blockKey, ConfigurationSection section) {
        List<DropEntry> drops = new ArrayList<>();
        if (section == null) {
            return drops;
        }
        for (String rawId : section.getKeys(false)) {
            ConfigurationSection dropSection = section.getConfigurationSection(rawId);
            if (dropSection == null) {
                continue;
            }
            String id = normalizeId(rawId);
            Material material = matchMaterial(dropSection.getString("material"), Material.AIR);
            if (!material.isItem() || material.isAir()) {
                plugin.getLogger().warning("Ignoring drop " + id + " in block " + blockKey + ": invalid material.");
                continue;
            }
            int minAmount = Math.max(0, dropSection.getInt("min-amount", dropSection.getInt("amount", 1)));
            int maxAmount = Math.max(minAmount, dropSection.getInt("max-amount", minAmount));
            if (maxAmount <= 0) {
                plugin.getLogger().warning("Ignoring drop " + id + " in block " + blockKey + ": max amount must be positive.");
                continue;
            }
            double chance = clamp(dropSection.getDouble("chance", 1.0D), 0.0D, 1.0D);
            if (chance <= 0.0D) {
                plugin.getLogger().warning("Ignoring drop " + id + " in block " + blockKey + ": chance must be positive.");
                continue;
            }
            boolean fortune = dropSection.getBoolean("affected-by-fortune", true);
            ItemStack item = buildDropItem(dropSection, material);
            MiningItemTags.tagCustomItem(plugin, item, "drop", id);
            drops.add(new DropEntry(id, dropSection.getString("display-name", rawId),
                    item, minAmount, maxAmount, chance, fortune));
        }
        drops.sort(Comparator.comparing(DropEntry::id));
        return drops;
    }

    @SuppressWarnings("deprecation")
    private ItemStack buildDropItem(ConfigurationSection section, Material material) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String name = section.getString("name", "");
        if (name != null && !name.isBlank()) {
            meta.displayName(Text.component(name));
        }
        List<String> lore = section.getStringList("lore");
        if (!lore.isEmpty()) {
            meta.lore(lore.stream().map(Text::component).toList());
        }
        int customModelData = section.getInt("custom-model-data", 0);
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        if (section.getBoolean("hide-flags", false)) {
            meta.addItemFlags(ItemFlag.values());
        }
        item.setItemMeta(meta);
        return item;
    }

    private NavigableMap<Integer, MilestoneReward> loadMilestones(ConfigurationSection section) {
        NavigableMap<Integer, MilestoneReward> loaded = new TreeMap<>();
        if (section == null) {
            return loaded;
        }
        for (String rawLevel : section.getKeys(false)) {
            Integer level = parseInt(rawLevel);
            ConfigurationSection milestoneSection = section.getConfigurationSection(rawLevel);
            if (level == null || level < 1 || milestoneSection == null) {
                continue;
            }
            loaded.put(level, new MilestoneReward(
                    level,
                    nonNegative(milestoneSection.getDouble("money", 0.0D)),
                    milestoneSection.getStringList("commands"),
                    milestoneSection.getString("message", "")
            ));
        }
        return loaded;
    }

    private NavigableMap<Integer, String> loadRanks(ConfigurationSection section) {
        NavigableMap<Integer, String> loaded = new TreeMap<>();
        if (section == null) {
            loaded.put(1, "Novice");
            return loaded;
        }
        for (String rawLevel : section.getKeys(false)) {
            Integer level = parseInt(rawLevel);
            if (level == null || level < 1) {
                continue;
            }
            loaded.put(level, section.getString(rawLevel, "Novice"));
        }
        if (loaded.isEmpty()) {
            loaded.put(1, "Novice");
        }
        return loaded;
    }

    private List<PermissionMultiplier> loadMultipliers(ConfigurationSection section) {
        List<PermissionMultiplier> loaded = new ArrayList<>();
        if (section == null) {
            return loaded;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            String permission = entry.getString("permission", "miningplus.multiplier." + key);
            double xp = nonNegative(entry.getDouble("xp", 1.0D));
            double money = nonNegative(entry.getDouble("money", 1.0D));
            loaded.add(new PermissionMultiplier(key, permission, xp, money));
        }
        return loaded;
    }

    private FileConfiguration loadBundledConfig() {
        try (InputStream inputStream = plugin.getResource("config.yml")) {
            if (inputStream == null) {
                return new YamlConfiguration();
            }
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            mergeBundledConfigFiles(bundled);
            return bundled;
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load bundled config defaults: " + e.getMessage());
            return new YamlConfiguration();
        }
    }

    private void mergeExternalConfigFiles(FileConfiguration target, FileConfiguration defaults) {
        if (!target.getBoolean("config-files.enabled", defaults.getBoolean("config-files.enabled", true))) {
            return;
        }
        boolean overwrite = target.getBoolean("config-files.override-config-yml",
                defaults.getBoolean("config-files.override-config-yml", false));
        Path dataFolder = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        for (String fileName : configuredSectionFiles(target, defaults)) {
            Path sectionPath = dataFolder.resolve(fileName).normalize();
            if (!sectionPath.startsWith(dataFolder)) {
                plugin.getLogger().warning("Ignoring config section outside plugin folder: " + fileName);
                continue;
            }
            File sectionFile = sectionPath.toFile();
            if (!sectionFile.isFile()) {
                plugin.getLogger().warning("Missing config section file: " + fileName);
                continue;
            }
            YamlConfiguration sectionConfig = YamlConfiguration.loadConfiguration(sectionFile);
            mergeSection(sectionConfig, target, overwrite);
        }
    }

    private void mergeBundledConfigFiles(FileConfiguration target) {
        for (String fileName : configuredSectionFiles(target, null)) {
            try (InputStream inputStream = plugin.getResource(fileName)) {
                if (inputStream == null) {
                    plugin.getLogger().warning("Missing bundled config section: " + fileName);
                    continue;
                }
                YamlConfiguration sectionConfig = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                mergeSection(sectionConfig, target, false);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not load bundled config section '" + fileName
                        + "': " + e.getMessage());
            }
        }
    }

    private List<String> configuredSectionFiles(FileConfiguration primary, FileConfiguration defaults) {
        List<String> files = new ArrayList<>(primary.getStringList("config-files.files"));
        if (files.isEmpty() && defaults != null) {
            files = new ArrayList<>(defaults.getStringList("config-files.files"));
        }
        for (String defaultFile : DEFAULT_SECTION_CONFIG_FILES) {
            if (!files.contains(defaultFile)) {
                files.add(defaultFile);
            }
        }
        return files.isEmpty() ? DEFAULT_SECTION_CONFIG_FILES : files;
    }

    private void mergeSection(ConfigurationSection source, ConfigurationSection target, boolean overwrite) {
        for (String key : source.getKeys(false)) {
            ConfigurationSection sourceChild = source.getConfigurationSection(key);
            if (sourceChild != null) {
                ConfigurationSection targetChild = target.getConfigurationSection(key);
                if (targetChild == null) {
                    if (target.contains(key) && !overwrite) {
                        continue;
                    }
                    target.set(key, null);
                    targetChild = target.createSection(key);
                }
                mergeSection(sourceChild, targetChild, overwrite);
                continue;
            }
            if (overwrite || !target.contains(key)) {
                target.set(key, source.get(key));
            }
        }
    }

    private Material matchMaterial(String value, Material fallback) {
        Material material = Material.matchMaterial(value == null ? "" : value);
        return material == null ? fallback : material;
    }

    private Particle matchParticle(String value, Particle fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.contains(":") ? value.toLowerCase(Locale.ROOT)
                : "minecraft:" + value.toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(normalized);
        Particle particle = key == null ? null : Registry.PARTICLE_TYPE.get(key);
        if (particle == null) {
            plugin.getLogger().warning("Ignoring invalid particle: " + value);
            return fallback;
        }
        return particle;
    }

    private Sound matchSound(String value, Sound fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        // Accept both enum-style names (ENTITY_PLAYER_LEVELUP) and namespaced keys (entity.player.levelup).
        String normalized = value.contains(":") || value.contains(".")
                ? value.toLowerCase(Locale.ROOT)
                : value.toLowerCase(Locale.ROOT).replace('_', '.');
        NamespacedKey key = NamespacedKey.fromString(normalized.contains(":") ? normalized : "minecraft:" + normalized);
        Sound sound = key == null ? null : Registry.SOUND_EVENT.get(key);
        if (sound == null) {
            plugin.getLogger().warning("Ignoring invalid sound: " + value);
            return fallback;
        }
        return sound;
    }

    private EntityType matchEntityType(String value, EntityType fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            EntityType type = EntityType.valueOf(normalized);
            return type.isSpawnable() && type.isAlive() ? type : fallback;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Ignoring invalid encounter entity type: " + value);
            return fallback;
        }
    }

    private PotionEffectType matchPotionEffectType(String value, PotionEffectType fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        PotionEffectType type = switch (normalized) {
            case "BLINDNESS" -> PotionEffectType.BLINDNESS;
            case "DARKNESS" -> PotionEffectType.DARKNESS;
            case "FIRE_RESISTANCE" -> PotionEffectType.FIRE_RESISTANCE;
            case "HASTE", "FAST_DIGGING" -> PotionEffectType.HASTE;
            case "HUNGER" -> PotionEffectType.HUNGER;
            case "MINING_FATIGUE", "SLOW_DIGGING" -> PotionEffectType.MINING_FATIGUE;
            case "NAUSEA", "CONFUSION" -> PotionEffectType.NAUSEA;
            case "POISON" -> PotionEffectType.POISON;
            case "RESISTANCE", "DAMAGE_RESISTANCE" -> PotionEffectType.RESISTANCE;
            case "SLOWNESS", "SLOW" -> PotionEffectType.SLOWNESS;
            case "WEAKNESS" -> PotionEffectType.WEAKNESS;
            default -> null;
        };
        if (type == null) {
            plugin.getLogger().warning("Ignoring invalid potion effect: " + value);
            return fallback;
        }
        return type;
    }

    private double nonNegative(double value) {
        return Double.isFinite(value) && value >= 0.0D ? value : 0.0D;
    }

    private double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private int boundedInt(String path, int fallback, int min, int max) {
        return Math.max(min, Math.min(max, config.getInt(path, fallback)));
    }

    private long safeMultiply(long value, long multiplier) {
        if (value > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }
        return value * multiplier;
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '-').replace('_', '-');
    }

    private String normalizeCustomSellId(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace(' ', '-').replace('_', '-');
        int separator = normalized.indexOf(':');
        if (separator < 0) {
            return normalizeId(normalized);
        }
        String kind = normalizeId(normalized.substring(0, separator));
        String id = normalizeId(normalized.substring(separator + 1));
        return kind.isBlank() || id.isBlank() ? "" : kind + ":" + id;
    }

    private List<String> normalizeIds(List<String> values) {
        List<String> ids = new ArrayList<>();
        for (String value : values) {
            String id = normalizeId(value);
            if (!id.isBlank() && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private ConfigurationSection journalChaptersSection() {
        ConfigurationSection section = config.getConfigurationSection("journal.chapters");
        return section == null ? config.getConfigurationSection("quests.chapters") : section;
    }

    public record PermissionMultiplier(String id, String permission, double xp, double money) {
    }
}
