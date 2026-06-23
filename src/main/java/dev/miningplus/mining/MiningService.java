package dev.miningplus.mining;

import dev.miningplus.config.MiningPlusConfig;
import dev.miningplus.data.LocationKey;
import dev.miningplus.data.PlacedBlockStore;
import dev.miningplus.data.PlayerCommission;
import dev.miningplus.data.PlayerData;
import dev.miningplus.data.PlayerDataStore;
import dev.miningplus.economy.EconomyService;
import dev.miningplus.economy.PointsHook;
import dev.miningplus.util.NumberFormat;
import dev.miningplus.util.Text;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToDoubleFunction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public final class MiningService {
    private static final String REINFORCED_UPGRADE = "reinforced";
    private static final String LUMINOUS_UPGRADE = "luminous";
    private static final String PROSPECTOR_UPGRADE = "prospector";
    private static final String SHARD_MAGNET_UPGRADE = "shard-magnet";
    private static final double REINFORCED_VEIN_DAMAGE_REDUCTION = 0.35D;
    private static final double PROSPECTOR_ARTIFACT_CHANCE_BONUS = 0.10D;
    private static final double SHARD_MAGNET_BONUS = 0.15D;
    private static final int LUMINOUS_NIGHT_VISION_TICKS = 260;
    private static final int LUMINOUS_REFRESH_THRESHOLD_TICKS = 80;
    private static final int ORE_SENSE_MAX_MARKERS = 4;

    private final JavaPlugin plugin;
    private final MiningPlusConfig config;
    private final PlayerDataStore playerStore;
    private final PlacedBlockStore placedStore;
    private final EconomyService economy;
    private final PointsHook points;
    private BukkitTask saveTask;
    private final Set<java.util.UUID> veinMining = new HashSet<>();
    private final Set<Particle> feedbackParticleWarnings = new HashSet<>();
    private final Map<java.util.UUID, Long> miningFeedbackCooldowns = new HashMap<>();
    private final Map<java.util.UUID, ActiveTimedChallenge> timedChallenges = new HashMap<>();
    private final Map<java.util.UUID, MiningEventSettings.EventAction> encounterRewards = new HashMap<>();
    private boolean internalBreakCheck;

    public MiningService(JavaPlugin plugin, MiningPlusConfig config, PlayerDataStore playerStore,
                         PlacedBlockStore placedStore, EconomyService economy, PointsHook points) {
        this.plugin = plugin;
        this.config = config;
        this.playerStore = playerStore;
        this.placedStore = placedStore;
        this.economy = economy;
        this.points = points;
    }

    public MiningPlusConfig config() {
        return config;
    }

    public PlayerDataStore players() {
        return playerStore;
    }

    public PlacedBlockStore placedBlocks() {
        return placedStore;
    }

    public EconomyService economy() {
        return economy;
    }

    public PointsHook points() {
        return points;
    }

    public boolean internalBreakCheckActive() {
        return internalBreakCheck;
    }

    public void syncPerkPoints(PlayerData data) {
        if (!config.perksEnabled() || data == null) {
            return;
        }
        int targetLevel = data.level();
        int awardedLevel = Math.max(1, data.perkPointsAwardedLevel());
        if (targetLevel <= awardedLevel) {
            capPerkPoints(data);
            return;
        }
        int earned = safeMultiplyToInt(targetLevel - awardedLevel, config.perks().pointsPerLevel());
        if (earned > 0) {
            data.addPerkPoints(earned);
            capPerkPoints(data);
        }
        data.perkPointsAwardedLevel(targetLevel);
    }

    public PerkPurchaseResult purchasePerk(Player player, String id) {
        if (!config.perksEnabled()) {
            return new PerkPurchaseResult(PerkPurchaseResult.Status.DISABLED, null, 0, 0);
        }
        PerkDefinition definition = config.perks().definition(id);
        if (definition == null || !definition.active()) {
            return new PerkPurchaseResult(PerkPurchaseResult.Status.INVALID, null, 0, 0);
        }
        PlayerData data = playerStore.getOrCreate(player.getUniqueId(), player.getName());
        syncPerkPoints(data);
        int currentLevel = data.perkLevel(definition.id());
        int cost = definition.costForNextLevel(currentLevel);
        if (!player.hasPermission(definition.permission())) {
            return new PerkPurchaseResult(PerkPurchaseResult.Status.NO_PERMISSION, definition, currentLevel, cost);
        }
        if (data.level() < definition.unlockLevel()) {
            return new PerkPurchaseResult(PerkPurchaseResult.Status.LOCKED, definition, currentLevel, cost);
        }
        if (currentLevel >= definition.maxLevel()) {
            return new PerkPurchaseResult(PerkPurchaseResult.Status.MAXED, definition, currentLevel, cost);
        }
        if (!data.spendPerkPoints(cost)) {
            return new PerkPurchaseResult(PerkPurchaseResult.Status.INSUFFICIENT_POINTS, definition, currentLevel, cost);
        }
        int newLevel = currentLevel + 1;
        data.perkLevel(definition.id(), newLevel);
        data.addPerksPurchased(1L);
        return new PerkPurchaseResult(PerkPurchaseResult.Status.SUCCESS, definition, newLevel, cost);
    }

    public void start() {
        long interval = config.saveIntervalTicks();
        saveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveAll, interval, interval);
    }

    public void stop() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        saveAll();
    }

    public void saveAll() {
        playerStore.save();
        if (config.antiExploitEnabled()) {
            placedStore.save();
        }
    }

    public void handlePlace(Block block) {
        if (!config.antiExploitEnabled()) {
            return;
        }
        if (config.blockReward(block.getType()) != null || config.veinMiner().isVeinBlock(block.getType())) {
            placedStore.markPlaced(LocationKey.of(block));
        }
    }

    public void handleBreak(Player player, Block block, BlockBreakEvent event) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        Material primaryType = block.getType();
        ItemStack tool = player.getInventory().getItemInMainHand();
        syncPickaxeLore(tool);

        // Primary block first, using the event to control vanilla drops.
        boolean naturalBlock = processBlock(player, block, event, tool);
        if (!naturalBlock) {
            return;
        }

        // Vein mining expands from the primary block when eligible.
        if (shouldVeinMine(player, primaryType, tool)) {
            runVeinMiner(player, block, primaryType, tool);
        }

        // Mining abilities proc off the primary block.
        if (config.abilitiesEnabled()) {
            rollAbilities(player, block, tool);
        }

        // Treasure and hazard events also proc only from the primary block.
        if (config.eventsEnabled()) {
            rollMiningEvents(player, block, primaryType, tool);
        }
    }

    private boolean shouldVeinMine(Player player, Material type, ItemStack tool) {
        if (!config.veinMinerEnabled() || !player.hasPermission("miningplus.veinminer")) {
            return false;
        }
        if (!playerStore.getOrCreate(player.getUniqueId(), player.getName()).veinMinerEnabled()) {
            return false;
        }
        if (!toolUpgradeActive(tool, "vein-miner")) {
            return false;
        }
        if (veinMining.contains(player.getUniqueId())) {
            return false;
        }
        VeinMinerSettings settings = config.veinMiner();
        if (!settings.isVeinBlock(type)) {
            return false;
        }
        if (settings.requireSneak() && !player.isSneaking()) {
            return false;
        }
        return settings.isAllowedTool(tool == null ? Material.AIR : tool.getType());
    }

    private void runVeinMiner(Player player, Block origin, Material type, ItemStack tool) {
        VeinMinerSettings settings = config.veinMiner();
        veinMining.add(player.getUniqueId());
        try {
            List<Block> vein = collectVein(origin, type, settings);
            for (Block block : vein) {
                if (settings.damageTool() && shouldDamageToolForVeinBlock(tool) && breaksTool(player, tool)) {
                    break;
                }
                processManualBlock(player, block, tool);
                if (settings.hungerPerBlock() > 0.0D) {
                    player.setExhaustion(player.getExhaustion() + (float) settings.hungerPerBlock());
                }
            }
        } finally {
            veinMining.remove(player.getUniqueId());
        }
    }

    private List<Block> collectVein(Block origin, Material type, VeinMinerSettings settings) {
        List<Block> result = new ArrayList<>();
        Set<Block> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        visited.add(origin);
        queue.add(origin);
        while (!queue.isEmpty() && result.size() < settings.maxBlocks()) {
            Block current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        if (!settings.includeDiagonals() && Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) {
                            continue;
                        }
                        Block neighbor = current.getRelative(dx, dy, dz);
                        if (visited.contains(neighbor) || neighbor.getType() != type) {
                            continue;
                        }
                        visited.add(neighbor);
                        if (result.size() >= settings.maxBlocks()) {
                            return result;
                        }
                        result.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
        }
        return result;
    }

    private boolean shouldDamageToolForVeinBlock(ItemStack tool) {
        if (!activeToolUpgrade(tool, REINFORCED_UPGRADE)) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble() >= REINFORCED_VEIN_DAMAGE_REDUCTION;
    }

    private boolean breaksTool(Player player, ItemStack tool) {
        if (tool == null || tool.getType().getMaxDurability() <= 0) {
            return false;
        }
        if (tool.getEnchantmentLevel(Enchantment.UNBREAKING) > 0
                && ThreadLocalRandom.current().nextInt(tool.getEnchantmentLevel(Enchantment.UNBREAKING) + 1) != 0) {
            return false;
        }
        if (!(tool.getItemMeta() instanceof Damageable damageable) || damageable.isUnbreakable()) {
            return false;
        }
        int newDamage = damageable.getDamage() + 1;
        if (newDamage >= tool.getType().getMaxDurability()) {
            tool.setAmount(Math.max(0, tool.getAmount() - 1));
            if (tool.getAmount() <= 0) {
                player.getInventory().setItemInMainHand(null);
            }
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 0.8F, 1.0F);
            return true;
        }
        damageable.setDamage(newDamage);
        tool.setItemMeta(damageable);
        return false;
    }

    private boolean processBlock(Player player, Block block, BlockBreakEvent event, ItemStack tool) {
        BlockReward reward = config.blockReward(block.getType());
        LocationKey key = LocationKey.of(block);
        boolean playerPlaced = config.antiExploitEnabled() && placedStore.isPlaced(key);
        if (playerPlaced) {
            placedStore.remove(key);
            return false;
        }
        List<ItemStack> vanillaDrops = new ArrayList<>(block.getDrops(tool));
        if (reward != null && vanillaDrops.isEmpty()) {
            return false;
        }
        int fortune = fortuneLevel(tool);
        boolean autoSmelt = autoSmeltActive(player, tool);
        boolean autoPickup = autoPickupActive(player, tool);
        boolean overrideVanilla = config.customDropsEnabled() && reward != null && reward.overrideVanillaDrops();
        boolean takeOver = autoSmelt || autoPickup || overrideVanilla;

        if (takeOver) {
            event.setDropItems(false);
            List<ItemStack> produced = new ArrayList<>();
            if (!overrideVanilla) {
                produced.addAll(vanillaDrops);
            }
            if (config.customDropsEnabled() && reward != null && reward.hasCustomDrops()) {
                produced.addAll(rollCustomDrops(reward, fortune));
            }
            deliver(player, block.getLocation().add(0.5D, 0.5D, 0.5D), produced, autoSmelt, autoPickup);
        } else if (config.customDropsEnabled() && reward != null && reward.hasCustomDrops()) {
            Location dropLocation = block.getLocation().add(0.5D, 0.5D, 0.5D);
            for (ItemStack drop : rollCustomDrops(reward, fortune)) {
                block.getWorld().dropItemNaturally(dropLocation, drop);
            }
        }

        awardRewards(player, reward, fortune, tool, block.getLocation().add(0.5D, 0.5D, 0.5D));
        return true;
    }

    /**
     * Processes a block broken programmatically (vein miner, explosive ability). Captures the
     * drops, runs them through the smelt/pickup pipeline, clears the block, then awards rewards.
     */
    private void processManualBlock(Player player, Block block, ItemStack tool) {
        if (!canBreakManualBlock(player, block)) {
            return;
        }
        Material material = block.getType();
        BlockReward reward = config.blockReward(material);
        LocationKey key = LocationKey.of(block);
        List<ItemStack> vanillaDrops = new ArrayList<>(block.getDrops(tool));
        Location dropLocation = block.getLocation().add(0.5D, 0.5D, 0.5D);

        boolean playerPlaced = config.antiExploitEnabled() && placedStore.isPlaced(key);
        if (playerPlaced) {
            placedStore.remove(key);
            block.setType(Material.AIR);
            // Still drop vanilla items so the block is not destroyed for free.
            deliver(player, dropLocation, vanillaDrops, false, false);
            return;
        }
        if (reward != null && vanillaDrops.isEmpty()) {
            return;
        }
        int fortune = fortuneLevel(tool);
        boolean autoSmelt = autoSmeltActive(player, tool);
        boolean autoPickup = autoPickupActive(player, tool);
        boolean overrideVanilla = config.customDropsEnabled() && reward != null && reward.overrideVanillaDrops();

        List<ItemStack> produced = new ArrayList<>();
        if (!overrideVanilla) {
            produced.addAll(vanillaDrops);
        }
        if (config.customDropsEnabled() && reward != null && reward.hasCustomDrops()) {
            produced.addAll(rollCustomDrops(reward, fortune));
        }
        block.setType(Material.AIR);
        deliver(player, dropLocation, produced, autoSmelt, autoPickup);
        dropVanillaExperience(material, dropLocation, tool, !vanillaDrops.isEmpty());
        awardRewards(player, reward, fortune, tool, dropLocation);
    }

    private void dropVanillaExperience(Material material, Location location, ItemStack tool, boolean canLootBlock) {
        if (!canLootBlock) {
            return;
        }
        int amount = vanillaExperienceDrop(material, tool);
        if (amount <= 0) {
            return;
        }
        location.getWorld().spawn(location, ExperienceOrb.class, orb -> orb.setExperience(amount));
    }

    private int vanillaExperienceDrop(Material material, ItemStack tool) {
        if (tool != null && tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0) {
            return 0;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return switch (material) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> random.nextInt(0, 3);
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE,
                    EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> random.nextInt(3, 8);
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE,
                    NETHER_QUARTZ_ORE -> random.nextInt(2, 6);
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> random.nextInt(1, 6);
            case NETHER_GOLD_ORE -> random.nextInt(0, 2);
            default -> 0;
        };
    }

    private boolean canBreakManualBlock(Player player, Block block) {
        if (player == null || block == null || block.getType().isAir()) {
            return false;
        }
        BlockBreakEvent check = new BlockBreakEvent(block, player);
        check.setDropItems(false);
        boolean previous = internalBreakCheck;
        internalBreakCheck = true;
        try {
            plugin.getServer().getPluginManager().callEvent(check);
        } finally {
            internalBreakCheck = previous;
        }
        return !check.isCancelled();
    }

    private void rollAbilities(Player player, Block origin, ItemStack tool) {
        PlayerData data = playerStore.getOrCreate(player.getUniqueId(), player.getName());
        AbilitySettings abilities = config.abilities();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        AbilitySettings.Ability haste = abilities.haste();
        if (canProc(player, data, haste, random)) {
            int amplifier = Math.max(0, (int) haste.magnitude() - 1);
            int duration = haste.duration() > 0 ? haste.duration() : 120;
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, duration, amplifier, true, false, true));
        }

        AbilitySettings.Ability oreSense = abilities.oreSense();
        if (canProc(player, data, oreSense, random)) {
            revealNearbyOre(player, origin, Math.max(3, Math.min(12, (int) oreSense.magnitude())));
        }

        AbilitySettings.Ability stoneguard = abilities.stoneguard();
        if (canProc(player, data, stoneguard, random)) {
            int amplifier = Math.max(0, (int) stoneguard.magnitude() - 1);
            int duration = stoneguard.duration() > 0 ? stoneguard.duration() : 100;
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, amplifier, true, false, true));
            player.spawnParticle(Particle.ENCHANT, player.getLocation().add(0.0D, 1.0D, 0.0D),
                    18, 0.35D, 0.45D, 0.35D, 0.03D);
            send(player, "ability-stoneguard", Map.of());
        }

        AbilitySettings.Ability explosive = abilities.explosive();
        if (canProc(player, data, explosive, random) && config.blockReward(origin.getType()) != null) {
            explode(player, origin, Math.max(1, (int) explosive.magnitude()), tool);
        }
    }

    private boolean canProc(Player player, PlayerData data, AbilitySettings.Ability ability, ThreadLocalRandom random) {
        return ability.active()
                && data.level() >= ability.unlockLevel()
                && player.hasPermission(ability.permission())
                && random.nextDouble() < ability.chance();
    }

    private void revealNearbyOre(Player player, Block origin, int radius) {
        List<Block> found = new ArrayList<>();
        Location originLocation = origin.getLocation();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    Block block = origin.getRelative(dx, dy, dz);
                    if (config.blockReward(block.getType()) != null) {
                        found.add(block);
                    }
                }
            }
        }
        if (found.isEmpty()) {
            return;
        }
        found.sort(Comparator.comparingDouble(block -> block.getLocation().distanceSquared(originLocation)));
        int markers = Math.min(ORE_SENSE_MAX_MARKERS, found.size());
        for (int i = 0; i < markers; i++) {
            Location location = found.get(i).getLocation().add(0.5D, 0.5D, 0.5D);
            player.spawnParticle(Particle.ELECTRIC_SPARK, location, 10, 0.2D, 0.2D, 0.2D, 0.01D);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                SoundCategory.PLAYERS, 0.45F, 1.6F);
        send(player, "ability-ore-sense", Map.of("count", NumberFormat.integer(markers)));
    }

    private void explode(Player player, Block origin, int radius, ItemStack tool) {
        if (veinMining.contains(player.getUniqueId())) {
            return;
        }
        veinMining.add(player.getUniqueId());
        try {
            origin.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION,
                    origin.getLocation().add(0.5D, 0.5D, 0.5D), 1);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        Block block = origin.getRelative(dx, dy, dz);
                        if (block.getType().isAir() || config.blockReward(block.getType()) == null) {
                            continue;
                        }
                        processManualBlock(player, block, tool);
                    }
                }
            }
        } finally {
            veinMining.remove(player.getUniqueId());
        }
    }

    private void rollMiningEvents(Player player, Block origin, Material type, ItemStack tool) {
        if (config.blockReward(type) == null) {
            return;
        }
        PlayerData data = playerStore.getOrCreate(player.getUniqueId(), player.getName());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (MiningEventSettings.TreasureEvent treasure : config.miningEvents().treasures()) {
            if (canTriggerEvent(player, data, treasure.trigger(), type, treasureChanceMultiplier(data), random)) {
                runTreasureEvent(player, origin, treasure, fortuneLevel(tool), tool);
            }
        }
        for (MiningEventSettings.HazardEvent hazard : config.miningEvents().hazards()) {
            if (canTriggerEvent(player, data, hazard.trigger(), type, hazardChanceMultiplier(data), random)) {
                runHazardEvent(player, origin, hazard, fortuneLevel(tool), tool);
            }
        }
    }

    private boolean canTriggerEvent(Player player, PlayerData data, MiningEventSettings.Trigger trigger,
                                    Material type, double chanceMultiplier, ThreadLocalRandom random) {
        return trigger.activeFor(type)
                && data.level() >= trigger.unlockLevel()
                && player.hasPermission(trigger.permission())
                && random.nextDouble() < Math.min(1.0D, trigger.chance() * Math.max(0.0D, chanceMultiplier));
    }

    private void runTreasureEvent(Player player, Block origin, MiningEventSettings.TreasureEvent treasure, int fortune,
                                  ItemStack tool) {
        playerStore.getOrCreate(player.getUniqueId(), player.getName()).addTreasuresFound(1L);
        Location location = origin.getLocation().add(0.5D, 0.5D, 0.5D);
        boolean autoPickup = autoPickupActive(player, tool);
        deliver(player, location, rollDrops(treasure.drops(), fortune), false, autoPickup);
        if (treasure.money().active()) {
            awardMoney(player, treasure.money(), moneyMultiplier(player));
        }
        if (treasure.points().active()) {
            awardPoints(player, treasure.points());
        }
        long shardsAwarded = 0L;
        if (treasure.shards().active() && config.currencyEnabled()) {
            shardsAwarded = awardShards(player, treasure.shards(), tool);
        }
        for (String command : treasure.commands()) {
            if (command == null || command.isBlank()) {
                continue;
            }
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                    renderEventText(command, player, treasure.trigger().id(), origin.getType(), shardsAwarded));
        }
        sendEventMessage(player, treasure.message(), treasure.trigger().id(), origin.getType(), shardsAwarded);
        playEventEffects(player, location, treasure.effects());
        runEventActions(player, origin, treasure.actions(), fortune, tool);
    }

    private void runHazardEvent(Player player, Block origin, MiningEventSettings.HazardEvent hazard,
                                int fortune, ItemStack tool) {
        PlayerData data = playerStore.getOrCreate(player.getUniqueId(), player.getName());
        data.addHazardsSurvived(1L);
        double damage = hazard.damage() * hazardDamageMultiplier(data);
        if (damage > 0.0D) {
            player.damage(damage);
        }
        if (hazard.fireTicks() > 0) {
            player.setFireTicks(Math.max(player.getFireTicks(), hazard.fireTicks()));
        }
        if (hazard.exhaustion() > 0.0D) {
            player.setExhaustion(player.getExhaustion() + (float) hazard.exhaustion());
        }
        if (hazard.effectDurationTicks() > 0) {
            player.addPotionEffect(new PotionEffect(hazard.effect(), hazard.effectDurationTicks(),
                    hazard.effectAmplifier(), true, false, true));
        }
        Location location = origin.getLocation().add(0.5D, 0.5D, 0.5D);
        sendEventMessage(player, hazard.message(), hazard.trigger().id(), origin.getType(), 0L);
        playEventEffects(player, location, hazard.effects());
        runEventActions(player, origin, hazard.actions(), fortune, tool);
    }

    private void runEventActions(Player player, Block origin, List<MiningEventSettings.EventAction> actions,
                                 int fortune, ItemStack tool) {
        if (actions == null || actions.isEmpty()) {
            return;
        }
        for (MiningEventSettings.EventAction action : actions) {
            switch (action.type()) {
                case SCAN_ORES -> runScanAction(player, origin, action);
                case TIMED_CHALLENGE -> startTimedChallenge(player, origin, action);
                case SPAWN_ENCOUNTER -> spawnEncounter(player, origin, action);
            }
        }
    }

    private void runScanAction(Player player, Block origin, MiningEventSettings.EventAction action) {
        List<Block> found = new ArrayList<>();
        Location originLocation = origin.getLocation();
        int radius = Math.max(1, Math.min(16, action.radius()));
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    Block block = origin.getRelative(dx, dy, dz);
                    if (config.blockReward(block.getType()) != null && eventActionMatches(action, block.getType())) {
                        found.add(block);
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble(block -> block.getLocation().distanceSquared(originLocation)));
        int markers = Math.min(action.markerCount() <= 0 ? 6 : action.markerCount(), found.size());
        for (int i = 0; i < markers; i++) {
            Location location = found.get(i).getLocation().add(0.5D, 0.5D, 0.5D);
            if (action.effects().particle() != null) {
                player.spawnParticle(action.effects().particle(), location, 12, 0.2D, 0.2D, 0.2D, 0.01D);
            }
        }
        playEventEffects(player, origin.getLocation().add(0.5D, 0.5D, 0.5D), action.effects());
        if (!action.message().isBlank()) {
            sendRaw(player, config.prefix() + renderActionText(action.message(), player, action, origin.getType(), 0, markers));
        }
    }

    private void startTimedChallenge(Player player, Block origin, MiningEventSettings.EventAction action) {
        long now = System.currentTimeMillis();
        ActiveTimedChallenge current = timedChallenges.get(player.getUniqueId());
        if (current != null && current.expiresAtMillis() > now) {
            return;
        }
        int required = Math.max(1, action.requiredBlocks());
        long expiresAt = now + Math.max(20, action.durationTicks()) * 50L;
        timedChallenges.put(player.getUniqueId(), new ActiveTimedChallenge(action, expiresAt, 0));
        if (!action.message().isBlank()) {
            sendRaw(player, config.prefix() + renderActionText(action.message(), player, action,
                    origin.getType(), 0, required));
        }
        playEventEffects(player, origin.getLocation().add(0.5D, 0.5D, 0.5D), action.effects());
    }

    private void spawnEncounter(Player player, Block origin, MiningEventSettings.EventAction action) {
        Location base = origin.getLocation().add(0.5D, 1.0D, 0.5D);
        int spawned = 0;
        for (int i = 0; i < Math.max(1, action.entityCount()); i++) {
            Location location = safeEncounterLocation(base, i);
            Entity entity = location.getWorld().spawnEntity(location, action.entityType());
            if (!(entity instanceof LivingEntity living)) {
                entity.remove();
                continue;
            }
            if (!action.entityName().isBlank()) {
                living.customName(Text.component(renderActionText(action.entityName(), player, action,
                        origin.getType(), 0, action.requiredBlocks())));
                living.setCustomNameVisible(true);
            }
            if (action.entityHealth() > 0.0D && action.entityHealth() <= living.getHealth()) {
                living.setHealth(action.entityHealth());
            }
            if (living instanceof Mob mob) {
                mob.setTarget(player);
            }
            encounterRewards.put(living.getUniqueId(), action);
            spawned++;
        }
        if (spawned <= 0) {
            return;
        }
        if (!action.message().isBlank()) {
            sendRaw(player, config.prefix() + renderActionText(action.message(), player, action,
                    origin.getType(), 0, spawned));
        }
        playEventEffects(player, base, action.effects());
    }

    private Location safeEncounterLocation(Location base, int index) {
        double angle = (Math.PI * 2.0D / 4.0D) * (index % 4);
        Location location = base.clone().add(Math.cos(angle) * 1.5D, 0.0D, Math.sin(angle) * 1.5D);
        if (!location.getBlock().isPassable()) {
            location = base.clone();
        }
        return location;
    }

    private boolean eventActionMatches(MiningEventSettings.EventAction action, Material material) {
        if (config.blockReward(material) == null) {
            return false;
        }
        if (action.blocks().isEmpty() && action.groups().isEmpty()) {
            return true;
        }
        if (action.blocks().contains(material)) {
            return true;
        }
        String materialId = material.name().toLowerCase(Locale.ROOT).replace('_', '-');
        for (String group : action.groups()) {
            if (config.materialGroup(group).contains(materialId)) {
                return true;
            }
        }
        return false;
    }

    private void sendEventMessage(Player player, String message, String eventId, Material material, long shardsAwarded) {
        if (message == null || message.isBlank()) {
            return;
        }
        sendRaw(player, config.prefix() + renderEventText(message, player, eventId, material, shardsAwarded));
    }

    private String renderEventText(String text, Player player, String eventId, Material material, long shardsAwarded) {
        return text
                .replace("{player}", player.getName())
                .replace("{event}", eventId)
                .replace("{block}", material.name().toLowerCase(java.util.Locale.ROOT))
                .replace("{shards}", NumberFormat.integer(shardsAwarded));
    }

    private String renderActionText(String text, Player player, MiningEventSettings.EventAction action,
                                    Material material, int progress, int count) {
        long seconds = Math.max(1L, action.durationTicks() / 20L);
        return text
                .replace("{player}", player.getName())
                .replace("{event}", action.id())
                .replace("{action}", action.id())
                .replace("{block}", material.name().toLowerCase(Locale.ROOT))
                .replace("{progress}", NumberFormat.integer(progress))
                .replace("{required}", NumberFormat.integer(action.requiredBlocks()))
                .replace("{count}", NumberFormat.integer(count))
                .replace("{seconds}", NumberFormat.integer(seconds));
    }

    private void playEventEffects(Player player, Location location, MiningEventSettings.EventEffects effects) {
        Particle particle = effects.particle();
        if (particle != null && effects.particleCount() > 0) {
            location.getWorld().spawnParticle(particle, location, effects.particleCount(),
                    0.35D, 0.35D, 0.35D, 0.02D);
        }
        Sound sound = effects.sound();
        if (sound != null && effects.volume() > 0.0F) {
            player.playSound(player, sound, effects.volume(), effects.pitch());
        }
    }

    private void deliver(Player player, Location location, List<ItemStack> items, boolean autoSmelt, boolean autoPickup) {
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            if (autoSmelt && config.autoSmelt().canSmelt(item.getType())) {
                item = new ItemStack(config.autoSmelt().smelt(item.getType()), item.getAmount());
            }
            if (autoPickup) {
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
                for (ItemStack leftover : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            } else {
                location.getWorld().dropItemNaturally(location, item);
            }
        }
    }

    private List<ItemStack> rollCustomDrops(BlockReward reward, int fortune) {
        return rollDrops(reward.drops(), fortune);
    }

    private List<ItemStack> rollDrops(List<DropEntry> entries, int fortune) {
        List<ItemStack> drops = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (DropEntry drop : entries) {
            if (random.nextDouble() > drop.chance()) {
                continue;
            }
            int amount = drop.minAmount() == drop.maxAmount()
                    ? drop.minAmount()
                    : random.nextInt(drop.minAmount(), drop.maxAmount() + 1);
            if (drop.affectedByFortune() && fortune > 0) {
                amount += random.nextInt(fortune + 1);
            }
            if (amount <= 0) {
                continue;
            }
            drops.add(drop.toItemStack(amount));
        }
        return drops;
    }

    private void awardRewards(Player player, BlockReward reward, int fortune, ItemStack tool, Location dropLocation) {
        if (reward == null) {
            return;
        }
        PlayerData data = playerStore.getOrCreate(player.getUniqueId(), player.getName());
        syncPerkPoints(data);
        data.incrementBlockMined(reward.material().name());
        applyPassiveToolUpgradeEffects(player, tool);
        double miningXpGained = 0.0D;

        if (config.rewardsEnabled() && reward.money().active()) {
            awardMoney(player, reward.money(), moneyMultiplier(player));
        }
        if (config.pointsRewardsEnabled() && reward.points().active()) {
            awardPoints(player, reward.points());
        }
        if (config.currencyEnabled() && reward.shards().active()) {
            awardShards(player, reward.shards(), tool);
        }
        if (config.levelsEnabled() && reward.xp() > 0.0D) {
            double gained = reward.xp() * config.globalXpMultiplier() * xpMultiplier(player)
                    * pickaxeMiningXpMultiplier(player, tool);
            if (config.abilitiesEnabled()) {
                AbilitySettings.Ability bonusXp = config.abilities().bonusXp();
                if (canProc(player, data, bonusXp, ThreadLocalRandom.current())) {
                    gained *= Math.max(1.0D, bonusXp.magnitude());
                }
            }
            awardXp(player, data, gained);
            miningXpGained = gained;
        }
        if (config.artifactsEnabled()) {
            rollArtifacts(player, dropLocation, reward.material(), fortune, tool);
        }
        advancePickaxe(player, tool, miningXpGained);
        advanceTimedChallenge(player, reward.material(), dropLocation, fortune, tool);
        playMiningFeedback(player, dropLocation, reward, miningXpGained, data);
    }

    private void applyPassiveToolUpgradeEffects(Player player, ItemStack tool) {
        if (!activeToolUpgrade(tool, LUMINOUS_UPGRADE)) {
            return;
        }
        PotionEffect current = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
        if (current == null || current.getDuration() < LUMINOUS_REFRESH_THRESHOLD_TICKS) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                    LUMINOUS_NIGHT_VISION_TICKS, 0, true, false, true));
        }
    }

    private void rollArtifacts(Player player, Location location, Material material, int fortune, ItemStack tool) {
        PlayerData data = playerStore.getOrCreate(player.getUniqueId(), player.getName());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<ArtifactDefinition> eligible = eligibleArtifacts(player, data, material);
        if (eligible.isEmpty()) {
            return;
        }

        boolean found = false;
        for (ArtifactDefinition artifact : eligible) {
            if (random.nextDouble() >= artifactChance(player, artifact, tool)) {
                continue;
            }
            found |= grantArtifact(player, data, location, artifact, fortune, tool, random);
        }
        if (found) {
            data.resetBlocksSinceArtifact();
            return;
        }

        int pityBlocks = config.artifactPityBlocks();
        if (pityBlocks <= 0) {
            return;
        }
        data.incrementBlocksSinceArtifact();
        if (data.blocksSinceArtifact() < pityBlocks) {
            return;
        }
        ArtifactDefinition artifact = chooseArtifact(eligible, random);
        if (artifact != null && grantArtifact(player, data, location, artifact, fortune, tool, random)) {
            data.resetBlocksSinceArtifact();
        }
    }

    private List<ArtifactDefinition> eligibleArtifacts(Player player, PlayerData data, Material material) {
        List<ArtifactDefinition> eligible = new ArrayList<>();
        for (ArtifactDefinition artifact : config.artifacts()) {
            if (artifact.active()
                    && artifact.chance() > 0.0D
                    && data.level() >= artifact.unlockLevel()
                    && player.hasPermission(artifact.permission())
                    && artifactActiveFor(artifact, material)) {
                eligible.add(artifact);
            }
        }
        return eligible;
    }

    private double artifactChance(Player player, ArtifactDefinition artifact, ItemStack tool) {
        return Math.min(1.0D, Math.max(0.0D, artifact.chance() * pickaxeArtifactChanceMultiplier(player, tool)));
    }

    private ArtifactDefinition chooseArtifact(List<ArtifactDefinition> artifacts, ThreadLocalRandom random) {
        double totalWeight = 0.0D;
        for (ArtifactDefinition artifact : artifacts) {
            totalWeight += Math.max(0.0D, artifact.chance());
        }
        if (totalWeight <= 0.0D) {
            return null;
        }
        double roll = random.nextDouble(totalWeight);
        double cursor = 0.0D;
        for (ArtifactDefinition artifact : artifacts) {
            cursor += Math.max(0.0D, artifact.chance());
            if (roll <= cursor) {
                return artifact;
            }
        }
        return artifacts.getLast();
    }

    private boolean grantArtifact(Player player, PlayerData data, Location location, ArtifactDefinition artifact,
                                  int fortune, ItemStack tool, ThreadLocalRandom random) {
        int amount = artifact.minAmount() == artifact.maxAmount()
                ? artifact.minAmount()
                : random.nextInt(artifact.minAmount(), artifact.maxAmount() + 1);
        if (artifact.affectedByFortune() && fortune > 0) {
            amount += random.nextInt(fortune + 1);
        }
        if (amount <= 0) {
            return false;
        }
        boolean autoPickup = autoPickupActive(player, tool);
        deliver(player, location, List.of(artifact.toItemStack(amount)), false, autoPickup);
        Set<String> completedBefore = completedArtifactSetIds(data);
        data.addArtifactFound(artifact.id(), amount);
        announceNewArtifactSetCompletions(player, data, completedBefore);
        if (artifact.money().active()) {
            awardMoney(player, artifact.money(), moneyMultiplier(player));
        }
        if (artifact.points().active()) {
            awardPoints(player, artifact.points());
        }
        if (artifact.shards().active() && config.currencyEnabled()) {
            awardShards(player, artifact.shards(), tool);
        }
        runArtifactCommands(player, artifact, amount);
        send(player, "artifact-found", Map.of(
                "artifact", artifact.displayName(),
                "amount", NumberFormat.integer(amount)
        ));
        playFeedback(player, location, config.feedback().artifact(), Map.of(
                "artifact", artifact.displayName(),
                "amount", NumberFormat.integer(amount),
                "id", artifact.id()
        ));
        return true;
    }

    private boolean artifactActiveFor(ArtifactDefinition artifact, Material material) {
        if (artifact.blocks().isEmpty() && artifact.groups().isEmpty()) {
            return true;
        }
        if (artifact.blocks().contains(material)) {
            return true;
        }
        String materialId = material.name().toLowerCase(Locale.ROOT).replace('_', '-');
        for (String group : artifact.groups()) {
            if (config.materialGroup(group).contains(materialId)) {
                return true;
            }
        }
        return false;
    }

    private void runArtifactCommands(Player player, ArtifactDefinition artifact, int amount) {
        for (String command : artifact.commands()) {
            if (command == null || command.isBlank()) {
                continue;
            }
            String parsed = command
                    .replace("{player}", player.getName())
                    .replace("{artifact}", artifact.id())
                    .replace("{amount}", String.valueOf(amount));
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), parsed);
        }
    }

    public boolean artifactSetComplete(PlayerData data, ArtifactSetDefinition set) {
        if (data == null || set == null || !set.active()) {
            return false;
        }
        for (String artifactId : set.artifactIds()) {
            if (data.artifactFound(artifactId) <= 0L) {
                return false;
            }
        }
        return true;
    }

    public int artifactSetProgress(PlayerData data, ArtifactSetDefinition set) {
        if (data == null || set == null) {
            return 0;
        }
        int found = 0;
        for (String artifactId : set.artifactIds()) {
            if (data.artifactFound(artifactId) > 0L) {
                found++;
            }
        }
        return found;
    }

    public Set<String> completedArtifactSetIds(PlayerData data) {
        Set<String> completed = new LinkedHashSet<>();
        if (data == null) {
            return completed;
        }
        for (ArtifactSetDefinition set : config.artifactSets()) {
            if (artifactSetComplete(data, set)) {
                completed.add(set.id());
            }
        }
        return completed;
    }

    private void announceNewArtifactSetCompletions(Player player, PlayerData data, Set<String> completedBefore) {
        for (ArtifactSetDefinition set : config.artifactSets()) {
            if (!artifactSetComplete(data, set) || completedBefore.contains(set.id())) {
                continue;
            }
            send(player, "artifact-set-complete", Map.of(
                    "set", set.displayName(),
                    "id", set.id()
            ));
        }
    }

    public ArtifactSalvageResult salvageArtifacts(Player player, boolean handOnly) {
        if (!config.artifactsEnabled()) {
            return new ArtifactSalvageResult(ArtifactSalvageResult.Status.DISABLED, 0L, 0L);
        }
        PlayerData data = playerStore.getOrCreate(player.getUniqueId(), player.getName());
        long items = 0L;
        long fragments = 0L;
        if (handOnly) {
            ItemStack item = player.getInventory().getItemInMainHand();
            SalvageCount count = salvageArtifactStack(item);
            if (count.fragments() > 0L) {
                player.getInventory().setItemInMainHand(null);
                items += count.items();
                fragments += count.fragments();
            }
        } else {
            ItemStack[] contents = player.getInventory().getStorageContents();
            for (int i = 0; i < contents.length; i++) {
                SalvageCount count = salvageArtifactStack(contents[i]);
                if (count.fragments() <= 0L) {
                    continue;
                }
                contents[i] = null;
                items += count.items();
                fragments += count.fragments();
            }
            player.getInventory().setStorageContents(contents);
        }
        if (fragments <= 0L) {
            return new ArtifactSalvageResult(ArtifactSalvageResult.Status.NO_ARTIFACTS, 0L, 0L);
        }
        data.addArtifactFragments(fragments);
        return new ArtifactSalvageResult(ArtifactSalvageResult.Status.SUCCESS, items, fragments);
    }

    private SalvageCount salvageArtifactStack(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return new SalvageCount(0L, 0L);
        }
        if (!MiningItemTags.itemKind(plugin, item).equals("artifact")) {
            return new SalvageCount(0L, 0L);
        }
        ArtifactDefinition artifact = config.artifact(MiningItemTags.itemId(plugin, item));
        if (artifact == null || artifact.fragmentValue() <= 0L) {
            return new SalvageCount(0L, 0L);
        }
        int amount = Math.max(1, item.getAmount());
        return new SalvageCount(amount, safeMultiply(artifact.fragmentValue(), amount));
    }

    public ArtifactResearchResult completeArtifactResearch(Player player, String researchId) {
        if (!config.artifactsEnabled()) {
            return new ArtifactResearchResult(ArtifactResearchResult.Status.DISABLED, null, 0L,
                    0.0D, 0.0D, 0.0D, 0L, 0L, 0);
        }
        ArtifactResearchDefinition research = config.artifactResearch(researchId);
        if (research == null || !research.active()) {
            return new ArtifactResearchResult(ArtifactResearchResult.Status.INVALID, null, 0L,
                    0.0D, 0.0D, 0.0D, 0L, 0L, 0);
        }
        PlayerData data = playerStore.getOrCreate(player.getUniqueId(), player.getName());
        if (data.level() < research.unlockLevel()) {
            return new ArtifactResearchResult(ArtifactResearchResult.Status.LOCKED_LEVEL, research,
                    research.fragmentCost(), 0.0D, 0.0D, 0.0D, 0L, 0L, 0);
        }
        for (String setId : research.requiredSets()) {
            if (!artifactSetComplete(data, config.artifactSet(setId))) {
                return new ArtifactResearchResult(ArtifactResearchResult.Status.LOCKED_SET, research,
                        research.fragmentCost(), 0.0D, 0.0D, 0.0D, 0L, 0L, 0);
            }
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (research.pickaxeXpReward() > 0.0D && !isPickaxe(tool)) {
            return new ArtifactResearchResult(ArtifactResearchResult.Status.NO_PICKAXE, research,
                    research.fragmentCost(), 0.0D, 0.0D, 0.0D, 0L, 0L, 0);
        }
        if (research.pickaxeXpReward() > 0.0D && pickaxeXpRequired(tool) <= 0.0D) {
            return new ArtifactResearchResult(ArtifactResearchResult.Status.PICKAXE_MAXED, research,
                    research.fragmentCost(), 0.0D, 0.0D, 0.0D, 0L, 0L, 0);
        }
        if (!data.spendArtifactFragments(research.fragmentCost())) {
            return new ArtifactResearchResult(ArtifactResearchResult.Status.INSUFFICIENT_FRAGMENTS, research,
                    research.fragmentCost(), 0.0D, 0.0D, 0.0D, 0L, 0L, 0);
        }
        double xpAwarded = 0.0D;
        if (research.xpReward() > 0.0D && config.levelsEnabled()) {
            xpAwarded = research.xpReward() * config.globalXpMultiplier() * xpMultiplier(player);
            awardXp(player, data, xpAwarded);
        }
        double pickaxeXpAwarded = 0.0D;
        if (research.pickaxeXpReward() > 0.0D) {
            pickaxeXpAwarded = scaledPickaxeXp(player, research.pickaxeXpReward());
            PickaxeProgress progress = addPickaxeXp(tool, pickaxeXpAwarded);
            data.recordPickaxeLevel(progress.level());
        }
        double moneyAwarded = 0.0D;
        if (research.moneyReward() > 0.0D && economy.available()
                && economy.deposit(player, research.moneyReward()).transactionSuccess()) {
            moneyAwarded = research.moneyReward();
            data.addMoneyEarned(moneyAwarded);
        }
        long pointsAwarded = 0L;
        if (research.pointsReward() > 0L && points.available()) {
            pointsAwarded = scaleLong(research.pointsReward(), pointsMultiplier(player));
            if (points.give(player, pointsAwarded)) {
                data.addPointsEarned(pointsAwarded);
            } else {
                pointsAwarded = 0L;
            }
        }
        long shardsAwarded = 0L;
        if (research.shardsReward() > 0L && config.currencyEnabled()) {
            shardsAwarded = scaleLong(research.shardsReward(), shardMultiplier(player, tool));
            data.addShards(shardsAwarded);
        }
        int itemStacksAwarded = 0;
        for (ItemStack item : research.rewardItems()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            itemStacksAwarded++;
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        for (String command : research.commands()) {
            if (command == null || command.isBlank()) {
                continue;
            }
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                    command.replace("{player}", player.getName()).replace("{research}", research.id()));
        }
        return new ArtifactResearchResult(ArtifactResearchResult.Status.SUCCESS, research, research.fragmentCost(),
                xpAwarded, pickaxeXpAwarded, moneyAwarded, pointsAwarded, shardsAwarded, itemStacksAwarded);
    }

    private void advanceTimedChallenge(Player player, Material material, Location location, int fortune, ItemStack tool) {
        ActiveTimedChallenge active = timedChallenges.get(player.getUniqueId());
        if (active == null) {
            return;
        }
        long now = System.currentTimeMillis();
        MiningEventSettings.EventAction action = active.action();
        if (active.expiresAtMillis() <= now) {
            timedChallenges.remove(player.getUniqueId());
            if (!action.failMessage().isBlank()) {
                sendRaw(player, config.prefix() + renderActionText(action.failMessage(), player, action,
                        material, active.progress(), action.requiredBlocks()));
            }
            return;
        }
        if (!eventActionMatches(action, material)) {
            return;
        }
        int progress = active.progress() + 1;
        if (progress < action.requiredBlocks()) {
            timedChallenges.put(player.getUniqueId(), new ActiveTimedChallenge(action, active.expiresAtMillis(), progress));
            if (playerStore.getOrCreate(player.getUniqueId(), player.getName()).notificationsEnabled()) {
                player.sendActionBar(Text.component(renderActionText(config.message("event-challenge-progress"),
                        player, action, material, progress, action.requiredBlocks())));
            }
            return;
        }
        timedChallenges.remove(player.getUniqueId());
        PlayerData data = playerStore.getOrCreate(player.getUniqueId(), player.getName());
        data.addEventChallengesCompleted(1L);
        grantEventActionRewards(player, location, action, fortune, tool);
        String message = action.completeMessage().isBlank()
                ? config.message("event-challenge-complete")
                : action.completeMessage();
        if (!message.isBlank()) {
            sendRaw(player, config.prefix() + renderActionText(message, player, action,
                    material, progress, action.requiredBlocks()));
        }
        playEventEffects(player, location, action.effects());
    }

    public void handleEncounterDeath(EntityDeathEvent event) {
        MiningEventSettings.EventAction action = encounterRewards.remove(event.getEntity().getUniqueId());
        if (action == null) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null || !killer.hasPermission("miningplus.use")) {
            return;
        }
        PlayerData data = playerStore.getOrCreate(killer.getUniqueId(), killer.getName());
        data.addEncountersDefeated(1L);
        ItemStack tool = killer.getInventory().getItemInMainHand();
        grantEventActionRewards(killer, event.getEntity().getLocation(), action, fortuneLevel(tool), tool);
        send(killer, "encounter-defeated", Map.of("event", action.id()));
        playEventEffects(killer, event.getEntity().getLocation(), action.effects());
    }

    private void grantEventActionRewards(Player player, Location location, MiningEventSettings.EventAction action,
                                         int fortune, ItemStack tool) {
        PlayerData data = playerStore.getOrCreate(player.getUniqueId(), player.getName());
        if (action.xpReward() > 0.0D && config.levelsEnabled()) {
            awardXp(player, data, action.xpReward() * config.globalXpMultiplier() * xpMultiplier(player));
        }
        if (action.pickaxeXpReward() > 0.0D && config.pickaxeProgressionEnabled()
                && player.hasPermission(config.pickaxeProgression().permission())
                && isPickaxe(tool) && pickaxeXpRequired(tool) > 0.0D) {
            int startingLevel = pickaxeLevel(tool);
            PickaxeProgress progress = addPickaxeXp(tool, scaledPickaxeXp(player, action.pickaxeXpReward()));
            data.recordPickaxeLevel(progress.level());
            if (progress.level() > startingLevel) {
                send(player, "pickaxe-level-up", Map.of(
                        "level", String.valueOf(progress.level()),
                        "bonus", NumberFormat.decimal(pickaxeMiningXpBonusPercent(tool))
                ));
            }
        }
        if (action.money().active()) {
            awardMoney(player, action.money(), moneyMultiplier(player));
        }
        if (action.points().active()) {
            awardPoints(player, action.points());
        }
        if (action.shards().active() && config.currencyEnabled()) {
            awardShards(player, action.shards(), tool);
        }
        List<ItemStack> rewards = new ArrayList<>();
        for (ItemStack item : action.rewardItems()) {
            if (item != null && !item.getType().isAir()) {
                rewards.add(item.clone());
            }
        }
        rewards.addAll(rollDrops(action.drops(), fortune));
        deliver(player, location, rewards, false, autoPickupActive(player, tool));
        for (String command : action.commands()) {
            if (command == null || command.isBlank()) {
                continue;
            }
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                    renderActionText(command, player, action, location.getBlock().getType(), 0, action.requiredBlocks()));
        }
    }

    private void awardMoney(Player player, MoneyReward money, double multiplier) {
        if (!economy.available() || ThreadLocalRandom.current().nextDouble() > money.chance()) {
            return;
        }
        double amount = money.min() == money.max()
                ? money.min()
                : ThreadLocalRandom.current().nextDouble(money.min(), money.max());
        amount *= config.globalMoneyMultiplier() * multiplier;
        if (amount > 0.0D && economy.deposit(player, amount).transactionSuccess()) {
            playerStore.getOrCreate(player.getUniqueId(), player.getName()).addMoneyEarned(amount);
        }
    }

    private void awardPoints(Player player, PointsReward reward) {
        if (!points.available() || ThreadLocalRandom.current().nextDouble() > reward.chance()) {
            return;
        }
        long amount = reward.min() == reward.max()
                ? reward.min()
                : ThreadLocalRandom.current().nextLong(reward.min(), reward.max() + 1L);
        amount = scaleLong(amount, pointsMultiplier(player));
        if (amount > 0L && points.give(player, amount)) {
            playerStore.getOrCreate(player.getUniqueId(), player.getName()).addPointsEarned(amount);
        }
    }

    private long awardShards(Player player, PointsReward reward, ItemStack tool) {
        if (ThreadLocalRandom.current().nextDouble() > reward.chance()) {
            return 0L;
        }
        long amount = reward.min() == reward.max()
                ? reward.min()
                : ThreadLocalRandom.current().nextLong(reward.min(), reward.max() + 1L);
        amount = scaleLong(amount, shardMultiplier(player, tool));
        if (amount > 0L) {
            playerStore.getOrCreate(player.getUniqueId(), player.getName()).addShards(amount);
            return amount;
        }
        return 0L;
    }

    private double shardMultiplier(Player player, ItemStack tool) {
        double bonus = perkBonus(player, PerkDefinition::shardMultiplierPerLevel)
                + artifactSetBonus(player, ArtifactSetDefinition::shardMultiplier);
        if (activeToolUpgrade(tool, SHARD_MAGNET_UPGRADE)) {
            bonus += SHARD_MAGNET_BONUS;
        }
        return 1.0D + Math.max(0.0D, bonus);
    }

    /**
     * Sells the configured sellable items in the player's inventory for Vault money.
     *
     * @param handOnly when true, only sells the item in the main hand.
     * @return the total money earned, or -1 if Vault economy is unavailable or rejects the deposit.
     */
    public double sell(Player player, boolean handOnly) {
        if (!economy.available()) {
            return -1.0D;
        }
        if (handOnly) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            double total = sellValue(hand);
            if (total <= 0.0D) {
                return 0.0D;
            }
            if (!economy.deposit(player, total).transactionSuccess()) {
                return -1.0D;
            }
            PlayerData data = playerStore.getOrCreate(player.getUniqueId(), player.getName());
            data.addItemsSold(hand.getAmount());
            data.addMoneyEarned(total);
            player.getInventory().setItemInMainHand(null);
            playFeedback(player, player.getLocation(), config.feedback().sell(), Map.of(
                    "money", formatMoney(total),
                    "items", NumberFormat.integer(hand.getAmount())
            ));
            return total;
        }

        ItemStack[] contents = player.getInventory().getStorageContents();
        double total = 0.0D;
        long soldItems = 0L;
        boolean[] soldSlots = new boolean[contents.length];
        for (int i = 0; i < contents.length; i++) {
            double value = sellValue(contents[i]);
            if (value <= 0.0D) {
                continue;
            }
            total = safeAdd(total, value);
            soldItems = safeAdd(soldItems, contents[i].getAmount());
            soldSlots[i] = true;
        }
        if (total > 0.0D) {
            if (!economy.deposit(player, total).transactionSuccess()) {
                return -1.0D;
            }
            PlayerData data = playerStore.getOrCreate(player.getUniqueId(), player.getName());
            data.addItemsSold(soldItems);
            data.addMoneyEarned(total);
            for (int i = 0; i < contents.length; i++) {
                if (soldSlots[i]) {
                    contents[i] = null;
                }
            }
            player.getInventory().setStorageContents(contents);
            playFeedback(player, player.getLocation(), config.feedback().sell(), Map.of(
                    "money", formatMoney(total),
                    "items", NumberFormat.integer(soldItems)
            ));
        }
        return total;
    }

    public JournalClaimResult claimJournalChapter(Player player, String chapterId) {
        if (!config.journalEnabled()) {
            return JournalClaimResult.failure("journal-disabled", null, "");
        }
        PlayerData data = playerStore.getOrCreate(player.getUniqueId(), player.getName());
        String requested = chapterId == null || chapterId.isBlank() ? "next" : chapterId;
        JournalChapter chapter = "next".equalsIgnoreCase(requested)
                ? firstClaimableJournalChapter(data)
                : config.journalChapter(requested);
        if (chapter == null) {
            return JournalClaimResult.failure("next".equalsIgnoreCase(requested)
                    ? "journal-none-claimable" : "journal-invalid", null, "");
        }
        if (!journalChapterUnlocked(data, chapter)) {
            return JournalClaimResult.failure("journal-locked", chapter, journalLockedDetail(data, chapter));
        }
        if (data.hasClaimedJournalChapter(chapter.id())) {
            return JournalClaimResult.failure("journal-already-claimed", chapter, "");
        }
        JournalObjective incomplete = firstIncompleteJournalObjective(data, chapter);
        if (incomplete != null) {
            return JournalClaimResult.failure("journal-incomplete", chapter, journalObjectiveLine(data, incomplete));
        }

        double xpAwarded = config.levelsEnabled() ? chapter.xpReward() : 0.0D;
        if (xpAwarded > 0.0D) {
            awardXp(player, data, xpAwarded);
        }
        double moneyAwarded = 0.0D;
        if (chapter.moneyReward() > 0.0D && economy.available()) {
            if (economy.deposit(player, chapter.moneyReward()).transactionSuccess()) {
                moneyAwarded = chapter.moneyReward();
                data.addMoneyEarned(moneyAwarded);
            }
        }
        long pointsAwarded = 0L;
        if (chapter.pointsReward() > 0L && points.available()) {
            if (points.give(player, chapter.pointsReward())) {
                pointsAwarded = chapter.pointsReward();
                data.addPointsEarned(pointsAwarded);
            }
        }
        long shardsAwarded = 0L;
        if (chapter.shardsReward() > 0L && config.currencyEnabled()) {
            shardsAwarded = chapter.shardsReward();
            data.addShards(shardsAwarded);
        }
        if (chapter.perkPointsReward() > 0) {
            data.addPerkPoints(chapter.perkPointsReward());
            capPerkPoints(data);
        }
        int itemStacksAwarded = giveItems(player, chapter.itemRewards());
        runJournalCommands(player, chapter);
        data.claimJournalChapter(chapter.id());
        playFeedback(player, player.getLocation(), config.feedback().journal(), Map.of(
                "chapter", chapter.displayName(),
                "id", chapter.id(),
                "xp", NumberFormat.decimal(xpAwarded),
                "money", formatMoney(moneyAwarded),
                "points", NumberFormat.integer(pointsAwarded),
                "shards", NumberFormat.integer(shardsAwarded),
                "perk_points", NumberFormat.integer(chapter.perkPointsReward()),
                "items", NumberFormat.integer(itemStacksAwarded)
        ));
        return JournalClaimResult.success(chapter, xpAwarded, moneyAwarded,
                pointsAwarded, shardsAwarded, chapter.perkPointsReward(), itemStacksAwarded);
    }

    public void grantXp(Player player, double amount) {
        if (player == null || amount <= 0.0D || !Double.isFinite(amount)) {
            return;
        }
        PlayerData data = playerStore.getOrCreate(player.getUniqueId(), player.getName());
        awardXp(player, data, amount);
    }

    public void grantStoredXp(PlayerData data, double amount) {
        if (data == null || amount <= 0.0D || !Double.isFinite(amount)) {
            return;
        }
        data.addXp(amount);
        applyLevelProgress(null, data, amount);
    }

    public JournalChapter firstClaimableJournalChapter(PlayerData data) {
        for (JournalChapter chapter : config.journalChapters()) {
            if (!data.hasClaimedJournalChapter(chapter.id()) && journalChapterUnlocked(data, chapter)
                    && journalChapterComplete(data, chapter)) {
                return chapter;
            }
        }
        return null;
    }

    public JournalChapter nextJournalChapter(PlayerData data) {
        JournalChapter firstClaimable = firstClaimableJournalChapter(data);
        if (firstClaimable != null) {
            return firstClaimable;
        }
        for (JournalChapter chapter : config.journalChapters()) {
            if (!data.hasClaimedJournalChapter(chapter.id()) && journalChapterUnlocked(data, chapter)) {
                return chapter;
            }
        }
        for (JournalChapter chapter : config.journalChapters()) {
            if (!data.hasClaimedJournalChapter(chapter.id())) {
                return chapter;
            }
        }
        return null;
    }

    public int claimableJournalChapters(PlayerData data) {
        int count = 0;
        for (JournalChapter chapter : config.journalChapters()) {
            if (!data.hasClaimedJournalChapter(chapter.id()) && journalChapterUnlocked(data, chapter)
                    && journalChapterComplete(data, chapter)) {
                count++;
            }
        }
        return count;
    }

    public boolean journalChapterUnlocked(PlayerData data, JournalChapter chapter) {
        for (String required : chapter.requiredChapters()) {
            if (!data.hasClaimedJournalChapter(required)) {
                return false;
            }
        }
        return true;
    }

    public String journalLockedDetail(PlayerData data, JournalChapter chapter) {
        for (String required : chapter.requiredChapters()) {
            if (!data.hasClaimedJournalChapter(required)) {
                JournalChapter requiredChapter = config.journalChapter(required);
                return requiredChapter == null ? required : requiredChapter.displayName();
            }
        }
        return "";
    }

    public boolean journalChapterComplete(PlayerData data, JournalChapter chapter) {
        for (JournalObjective objective : chapter.objectives()) {
            if (journalProgress(data, objective) < objective.amount()) {
                return false;
            }
        }
        return true;
    }

    public JournalObjective firstIncompleteJournalObjective(PlayerData data, JournalChapter chapter) {
        for (JournalObjective objective : chapter.objectives()) {
            if (journalProgress(data, objective) < objective.amount()) {
                return objective;
            }
        }
        return null;
    }

    public long journalProgress(PlayerData data, JournalObjective objective) {
        if (externalObjectiveUnavailable(objective)) {
            return objective.amount();
        }
        return switch (objective.type()) {
            case MINE_BLOCKS -> data.blocksMined();
            case MINE_BLOCK -> data.minedBlock(objective.target());
            case MINE_GROUP -> minedGroup(data, objective.target());
            case REACH_LEVEL -> data.level();
            case SELL_ITEMS -> data.itemsSold();
            case EARN_POINTS -> data.pointsEarned();
            case EARN_SHARDS -> data.shardsEarned();
            case EARN_MONEY -> (long) Math.floor(data.moneyEarned());
            case SPEND_SHARDS -> data.shardsSpent();
            case SPEND_ARTIFACT_FRAGMENTS -> data.artifactFragmentsSpent();
            case BUY_PERKS -> data.perksPurchased();
            case FIND_TREASURES -> data.treasuresFound();
            case FIND_ARTIFACTS -> data.artifactsFound();
            case REACH_PICKAXE_LEVEL -> data.bestPickaxeLevel();
            case REFINE_PICKAXE -> data.pickaxeRefines();
            case FIND_TOOL_UPGRADES -> data.toolUpgradesFound();
            case FIND_TOOL_UPGRADE -> data.toolUpgradeFound(objective.target());
            case SURVIVE_HAZARDS -> data.hazardsSurvived();
            case COMPLETE_EVENT_CHALLENGES -> data.eventChallengesCompleted();
            case DEFEAT_ENCOUNTERS -> data.encountersDefeated();
        };
    }

    public long minedGroup(PlayerData data, String groupId) {
        long total = 0L;
        for (String materialId : config.materialGroup(groupId)) {
            total = safeAdd(total, data.minedBlock(materialId));
        }
        return total;
    }

    public double journalProgressPercent(PlayerData data, JournalChapter chapter) {
        if (chapter.objectives().isEmpty()) {
            return 100.0D;
        }
        double total = 0.0D;
        for (JournalObjective objective : chapter.objectives()) {
            total += Math.min(1.0D, journalProgress(data, objective) / (double) objective.amount());
        }
        return Math.max(0.0D, Math.min(100.0D, (total / chapter.objectives().size()) * 100.0D));
    }

    public String journalObjectiveLine(PlayerData data, JournalObjective objective) {
        long progress = Math.min(journalProgress(data, objective), objective.amount());
        String key = progress >= objective.amount()
                ? "formats.objective-complete"
                : "formats.objective-incomplete";
        return Text.render(config.guiString(key), Map.of(
                "description", objective.description(),
                "progress", NumberFormat.integer(progress),
                "required", NumberFormat.integer(objective.amount())
        ));
    }

    public List<String> journalRewardLines(JournalChapter chapter) {
        List<String> lines = new ArrayList<>();
        if (chapter.xpReward() > 0.0D) {
            lines.add(Text.render(config.guiString("reward-lines.xp"),
                    Map.of("amount", NumberFormat.decimal(chapter.xpReward()))));
        }
        if (chapter.moneyReward() > 0.0D) {
            lines.add(Text.render(config.guiString("reward-lines.money"),
                    Map.of("amount", formatMoney(chapter.moneyReward()))));
        }
        if (chapter.pointsReward() > 0L) {
            lines.add(Text.render(config.guiString("reward-lines.points"),
                    Map.of("amount", NumberFormat.integer(chapter.pointsReward()))));
        }
        if (chapter.shardsReward() > 0L) {
            lines.add(Text.render(config.guiString("reward-lines.shards"),
                    Map.of("amount", config.formatShards(chapter.shardsReward()))));
        }
        if (chapter.perkPointsReward() > 0) {
            lines.add(Text.render(config.guiString("reward-lines.perk-points"),
                    Map.of("amount", NumberFormat.integer(chapter.perkPointsReward()))));
        }
        for (ItemStack item : chapter.itemRewards()) {
            lines.add(rewardItemLine(item, chapter.displayName()));
        }
        if (lines.isEmpty()) {
            lines.add(config.guiString("reward-lines.none"));
        }
        return lines;
    }

    private void runJournalCommands(Player player, JournalChapter chapter) {
        for (String command : chapter.commands()) {
            if (command == null || command.isBlank()) {
                continue;
            }
            String parsed = command
                    .replace("{player}", player.getName())
                    .replace("{chapter}", chapter.id())
                    .replace("{shards}", String.valueOf(chapter.shardsReward()));
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), parsed);
        }
    }

    public CommissionActionResult acceptCommission(Player player, String commissionId) {
        if (!config.commissionsEnabled()) {
            return CommissionActionResult.simple(CommissionActionResult.Status.DISABLED, null, "");
        }
        CommissionDefinition commission = config.commission(commissionId);
        if (commission == null || !commission.enabled()) {
            return CommissionActionResult.simple(CommissionActionResult.Status.INVALID, null, "");
        }
        PlayerData data = playerStore.getOrCreate(player.getUniqueId(), player.getName());
        if (data.hasActiveCommission(commission.id())) {
            return CommissionActionResult.simple(CommissionActionResult.Status.ALREADY_ACTIVE, commission, "");
        }
        if (data.activeCommissions().size() >= config.maxActiveCommissions()) {
            return CommissionActionResult.simple(CommissionActionResult.Status.ACTIVE_LIMIT, commission,
                    String.valueOf(config.maxActiveCommissions()));
        }
        if (data.level() < commission.unlockLevel()) {
            return CommissionActionResult.simple(CommissionActionResult.Status.LOCKED_LEVEL, commission,
                    String.valueOf(commission.unlockLevel()));
        }
        if (!commissionUnlocked(data, commission)) {
            return CommissionActionResult.simple(CommissionActionResult.Status.LOCKED_CHAPTER, commission,
                    commissionLockedDetail(data, commission));
        }
        Map<String, Long> baselines = new LinkedHashMap<>();
        for (JournalObjective objective : commission.objectives()) {
            baselines.put(objective.id(), journalProgress(data, objective));
        }
        data.startCommission(new PlayerCommission(commission.id(), System.currentTimeMillis(), baselines));
        return CommissionActionResult.simple(CommissionActionResult.Status.SUCCESS, commission, "");
    }

    public CommissionActionResult abandonCommission(Player player, String commissionId) {
        if (!config.commissionsEnabled()) {
            return CommissionActionResult.simple(CommissionActionResult.Status.DISABLED, null, "");
        }
        PlayerData data = playerStore.getOrCreate(player.getUniqueId(), player.getName());
        CommissionDefinition commission = config.commission(commissionId);
        if (commission == null) {
            if (!data.removeCommission(commissionId)) {
                return CommissionActionResult.simple(CommissionActionResult.Status.INVALID, null, "");
            }
            return CommissionActionResult.simple(CommissionActionResult.Status.ABANDONED, null, commissionId);
        }
        if (!data.removeCommission(commission.id())) {
            return CommissionActionResult.simple(CommissionActionResult.Status.NOT_ACTIVE, commission, "");
        }
        return CommissionActionResult.simple(CommissionActionResult.Status.ABANDONED, commission, "");
    }

    public CommissionActionResult claimCommission(Player player, String commissionId) {
        if (!config.commissionsEnabled()) {
            return CommissionActionResult.simple(CommissionActionResult.Status.DISABLED, null, "");
        }
        PlayerData data = playerStore.getOrCreate(player.getUniqueId(), player.getName());
        CommissionDefinition commission = config.commission(commissionId);
        if (commission == null || !commission.enabled()) {
            return CommissionActionResult.simple(CommissionActionResult.Status.INVALID, null, "");
        }
        PlayerCommission active = data.activeCommission(commission.id());
        if (active == null) {
            return CommissionActionResult.simple(CommissionActionResult.Status.NOT_ACTIVE, commission, "");
        }
        JournalObjective incomplete = firstIncompleteCommissionObjective(data, active, commission);
        if (incomplete != null) {
            return CommissionActionResult.simple(CommissionActionResult.Status.INCOMPLETE, commission,
                    commissionObjectiveLine(data, active, incomplete));
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        boolean hasPickaxeRewards = commission.pickaxeXpReward() > 0.0D || !commission.toolUpgradeRewards().isEmpty();
        if (hasPickaxeRewards && !isPickaxe(tool)) {
            return CommissionActionResult.simple(CommissionActionResult.Status.NO_PICKAXE, commission, "");
        }

        double xpAwarded = 0.0D;
        if (commission.xpReward() > 0.0D && config.levelsEnabled()) {
            xpAwarded = commission.xpReward();
            awardXp(player, data, xpAwarded);
        }
        double pickaxeXpAwarded = 0.0D;
        if (commission.pickaxeXpReward() > 0.0D && pickaxeXpRequired(tool) > 0.0D) {
            int startingLevel = pickaxeLevel(tool);
            pickaxeXpAwarded = scaledPickaxeXp(player, commission.pickaxeXpReward());
            PickaxeProgress progress = addPickaxeXp(tool, pickaxeXpAwarded);
            data.recordPickaxeLevel(progress.level());
            if (progress.level() > startingLevel) {
                send(player, "pickaxe-level-up", Map.of(
                        "level", String.valueOf(progress.level()),
                        "bonus", NumberFormat.decimal(pickaxeMiningXpBonusPercent(tool))
                ));
            }
        }
        int upgradesAwarded = 0;
        for (ToolUpgradeDefinition upgrade : unownedToolUpgrades(tool, commission.toolUpgradeRewards())) {
            if (!addToolUpgrade(tool, upgrade)) {
                continue;
            }
            upgradesAwarded++;
            recordToolUpgradeFound(player, upgrade);
            send(player, "tool-upgrade-granted", Map.of(
                    "upgrade", upgrade.displayName(),
                    "item", commission.displayName()
            ));
        }
        double moneyAwarded = 0.0D;
        if (commission.moneyReward() > 0.0D && economy.available()) {
            if (economy.deposit(player, commission.moneyReward()).transactionSuccess()) {
                moneyAwarded = commission.moneyReward();
                data.addMoneyEarned(moneyAwarded);
            }
        }
        long pointsAwarded = 0L;
        if (commission.pointsReward() > 0L && points.available()) {
            if (points.give(player, commission.pointsReward())) {
                pointsAwarded = commission.pointsReward();
                data.addPointsEarned(pointsAwarded);
            }
        }
        long shardsAwarded = 0L;
        if (commission.shardsReward() > 0L && config.currencyEnabled()) {
            shardsAwarded = commission.shardsReward();
            data.addShards(shardsAwarded);
        }
        if (commission.perkPointsReward() > 0) {
            data.addPerkPoints(commission.perkPointsReward());
            capPerkPoints(data);
        }
        int itemStacksAwarded = giveItems(player, commission.itemRewards());
        runCommissionCommands(player, commission);
        data.removeCommission(commission.id());
        data.addCommissionsCompleted(1L);
        playFeedback(player, player.getLocation(), config.feedback().journal(), Map.of(
                "chapter", commission.displayName(),
                "id", commission.id(),
                "xp", NumberFormat.decimal(xpAwarded),
                "money", formatMoney(moneyAwarded),
                "points", NumberFormat.integer(pointsAwarded),
                "shards", NumberFormat.integer(shardsAwarded),
                "perk_points", NumberFormat.integer(commission.perkPointsReward()),
                "items", NumberFormat.integer(itemStacksAwarded)
        ));
        return CommissionActionResult.success(commission, xpAwarded, pickaxeXpAwarded, moneyAwarded,
                pointsAwarded, shardsAwarded, commission.perkPointsReward(), itemStacksAwarded, upgradesAwarded);
    }

    public boolean commissionUnlocked(PlayerData data, CommissionDefinition commission) {
        for (String chapterId : commission.requiredChapters()) {
            if (!data.hasClaimedJournalChapter(chapterId)) {
                return false;
            }
        }
        return true;
    }

    public String commissionLockedDetail(PlayerData data, CommissionDefinition commission) {
        if (data.level() < commission.unlockLevel()) {
            return "Level " + commission.unlockLevel();
        }
        for (String chapterId : commission.requiredChapters()) {
            if (!data.hasClaimedJournalChapter(chapterId)) {
                JournalChapter chapter = config.journalChapter(chapterId);
                return chapter == null ? chapterId : chapter.displayName();
            }
        }
        return "";
    }

    public boolean commissionComplete(PlayerData data, PlayerCommission active, CommissionDefinition commission) {
        return firstIncompleteCommissionObjective(data, active, commission) == null;
    }

    public JournalObjective firstIncompleteCommissionObjective(PlayerData data, PlayerCommission active,
                                                              CommissionDefinition commission) {
        for (JournalObjective objective : commission.objectives()) {
            if (commissionProgress(data, active, objective) < objective.amount()) {
                return objective;
            }
        }
        return null;
    }

    public long commissionProgress(PlayerData data, PlayerCommission active, JournalObjective objective) {
        if (data == null || active == null || objective == null) {
            return 0L;
        }
        if (externalObjectiveUnavailable(objective)) {
            return objective.amount();
        }
        long current = journalProgress(data, objective);
        long baseline = active.baseline(objective.id());
        return Math.max(0L, current - baseline);
    }

    private boolean externalObjectiveUnavailable(JournalObjective objective) {
        return switch (objective.type()) {
            case EARN_POINTS -> config.completePointObjectivesWithoutPointsPlus() && !points.available();
            case EARN_MONEY -> config.completeMoneyObjectivesWithoutVault() && !economy.available();
            default -> false;
        };
    }

    public double commissionProgressPercent(PlayerData data, PlayerCommission active, CommissionDefinition commission) {
        if (commission.objectives().isEmpty()) {
            return 100.0D;
        }
        double total = 0.0D;
        for (JournalObjective objective : commission.objectives()) {
            total += Math.min(1.0D, commissionProgress(data, active, objective) / (double) objective.amount());
        }
        return Math.max(0.0D, Math.min(100.0D, (total / commission.objectives().size()) * 100.0D));
    }

    public String commissionObjectiveLine(PlayerData data, PlayerCommission active, JournalObjective objective) {
        long progress = Math.min(commissionProgress(data, active, objective), objective.amount());
        String key = progress >= objective.amount()
                ? "formats.objective-complete"
                : "formats.objective-incomplete";
        return Text.render(config.guiString(key), Map.of(
                "description", objective.description(),
                "progress", NumberFormat.integer(progress),
                "required", NumberFormat.integer(objective.amount())
        ));
    }

    public List<String> commissionRewardLines(CommissionDefinition commission) {
        List<String> lines = new ArrayList<>();
        if (commission.xpReward() > 0.0D) {
            lines.add(Text.render(config.guiString("reward-lines.xp"),
                    Map.of("amount", NumberFormat.decimal(commission.xpReward()))));
        }
        if (commission.pickaxeXpReward() > 0.0D) {
            lines.add(Text.render(config.guiString("reward-lines.pickaxe-xp"),
                    Map.of("amount", NumberFormat.decimal(commission.pickaxeXpReward()))));
        }
        if (commission.moneyReward() > 0.0D) {
            lines.add(Text.render(config.guiString("reward-lines.money"),
                    Map.of("amount", formatMoney(commission.moneyReward()))));
        }
        if (commission.pointsReward() > 0L) {
            lines.add(Text.render(config.guiString("reward-lines.points"),
                    Map.of("amount", NumberFormat.integer(commission.pointsReward()))));
        }
        if (commission.shardsReward() > 0L) {
            lines.add(Text.render(config.guiString("reward-lines.shards"),
                    Map.of("amount", config.formatShards(commission.shardsReward()))));
        }
        if (commission.perkPointsReward() > 0) {
            lines.add(Text.render(config.guiString("reward-lines.perk-points"),
                    Map.of("amount", NumberFormat.integer(commission.perkPointsReward()))));
        }
        for (String upgradeId : commission.toolUpgradeRewards()) {
            ToolUpgradeDefinition upgrade = config.toolUpgrades().definition(upgradeId);
            if (upgrade == null) {
                continue;
            }
            lines.add(Text.render(config.guiString("reward-lines.tool-upgrade"), Map.of(
                    "upgrade", upgrade.displayName(),
                    "rarity", upgrade.rarity()
            )));
        }
        for (ItemStack item : commission.itemRewards()) {
            lines.add(rewardItemLine(item, commission.displayName()));
        }
        if (lines.isEmpty()) {
            lines.add(config.guiString("reward-lines.none"));
        }
        return lines;
    }

    private void runCommissionCommands(Player player, CommissionDefinition commission) {
        for (String command : commission.commands()) {
            if (command == null || command.isBlank()) {
                continue;
            }
            String parsed = command
                    .replace("{player}", player.getName())
                    .replace("{commission}", commission.id())
                    .replace("{shards}", String.valueOf(commission.shardsReward()));
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), parsed);
        }
    }

    public ShopPurchaseResult purchaseShopItem(Player player, String itemId) {
        if (!config.currencyEnabled() || !config.shopEnabled()) {
            return new ShopPurchaseResult(ShopPurchaseResult.Status.DISABLED, null, 0L);
        }
        MiningShopItem item = config.shopItem(itemId);
        if (item == null || !item.enabled()) {
            return new ShopPurchaseResult(ShopPurchaseResult.Status.INVALID, null, 0L);
        }
        PlayerData data = playerStore.getOrCreate(player.getUniqueId(), player.getName());
        if (data.level() < item.unlockLevel()) {
            return new ShopPurchaseResult(ShopPurchaseResult.Status.LOCKED_LEVEL, item, item.cost());
        }
        if (!shopItemUnlocked(data, item)) {
            return new ShopPurchaseResult(ShopPurchaseResult.Status.LOCKED_CHAPTER, item, item.cost());
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        boolean hasPickaxeRewards = item.pickaxeXpReward() > 0.0D || !item.toolUpgradeRewards().isEmpty();
        if (hasPickaxeRewards && !isPickaxe(tool)) {
            return new ShopPurchaseResult(ShopPurchaseResult.Status.NO_PICKAXE, item, item.cost());
        }
        List<ToolUpgradeDefinition> unownedUpgrades = unownedToolUpgradeRewards(tool, item);
        boolean hasNonPickaxeXpRewards = !item.rewardItems().isEmpty()
                || !unownedUpgrades.isEmpty()
                || item.xpReward() > 0.0D
                || item.moneyReward() > 0.0D
                || item.pointsReward() > 0L
                || item.perkPointsReward() > 0
                || !item.commands().isEmpty();
        boolean pickaxeMaxed = item.pickaxeXpReward() > 0.0D && pickaxeXpRequired(tool) <= 0.0D;
        if (pickaxeMaxed && !hasNonPickaxeXpRewards) {
            return new ShopPurchaseResult(ShopPurchaseResult.Status.PICKAXE_MAXED, item, item.cost());
        }
        if (item.pickaxeXpReward() <= 0.0D
                && !item.toolUpgradeRewards().isEmpty()
                && unownedUpgrades.isEmpty()
                && item.rewardItems().isEmpty()
                && item.xpReward() <= 0.0D
                && item.moneyReward() <= 0.0D
                && item.pointsReward() <= 0L
                && item.perkPointsReward() <= 0) {
            return new ShopPurchaseResult(ShopPurchaseResult.Status.UPGRADE_ALREADY_OWNED, item, item.cost());
        }
        if (item.cost() > 0L && !data.spendShards(item.cost())) {
            return new ShopPurchaseResult(ShopPurchaseResult.Status.INSUFFICIENT_SHARDS, item, item.cost());
        }
        giveItems(player, item.rewardItems());
        if (item.xpReward() > 0.0D && config.levelsEnabled()) {
            awardXp(player, data, item.xpReward());
        }
        if (item.pickaxeXpReward() > 0.0D && !pickaxeMaxed) {
            int startingLevel = pickaxeLevel(tool);
            PickaxeProgress progress = addPickaxeXp(tool, scaledPickaxeXp(player, item.pickaxeXpReward()));
            data.recordPickaxeLevel(progress.level());
            if (progress.level() > startingLevel) {
                send(player, "pickaxe-level-up", Map.of(
                        "level", String.valueOf(progress.level()),
                        "bonus", NumberFormat.decimal(pickaxeMiningXpBonusPercent(tool))
                ));
            }
        }
        for (ToolUpgradeDefinition upgrade : unownedUpgrades) {
            if (!addToolUpgrade(tool, upgrade)) {
                continue;
            }
            recordToolUpgradeFound(player, upgrade);
            send(player, "tool-upgrade-granted", Map.of(
                    "upgrade", upgrade.displayName(),
                    "item", item.displayName()
            ));
        }
        if (item.moneyReward() > 0.0D && economy.available()) {
            if (economy.deposit(player, item.moneyReward()).transactionSuccess()) {
                data.addMoneyEarned(item.moneyReward());
            }
        }
        if (item.pointsReward() > 0L && points.available()) {
            if (points.give(player, item.pointsReward())) {
                data.addPointsEarned(item.pointsReward());
            }
        }
        if (item.perkPointsReward() > 0) {
            data.addPerkPoints(item.perkPointsReward());
            capPerkPoints(data);
        }
        runShopCommands(player, item);
        playFeedback(player, player.getLocation(), config.feedback().shop(), Map.of(
                "item", item.displayName(),
                "id", item.id(),
                "cost", NumberFormat.integer(item.cost()),
                "balance", NumberFormat.integer(data.shards()),
                "shards", NumberFormat.integer(data.shards())
        ));
        return new ShopPurchaseResult(ShopPurchaseResult.Status.SUCCESS, item, item.cost());
    }

    public boolean shopItemUnlocked(PlayerData data, MiningShopItem item) {
        for (String chapterId : item.requiredChapters()) {
            if (!data.hasClaimedJournalChapter(chapterId)) {
                return false;
            }
        }
        return true;
    }

    private List<ToolUpgradeDefinition> unownedToolUpgradeRewards(ItemStack tool, MiningShopItem item) {
        return unownedToolUpgrades(tool, item.toolUpgradeRewards());
    }

    private List<ToolUpgradeDefinition> unownedToolUpgrades(ItemStack tool, List<String> upgradeIds) {
        List<ToolUpgradeDefinition> upgrades = new ArrayList<>();
        if (!isPickaxe(tool)) {
            return upgrades;
        }
        for (String upgradeId : upgradeIds) {
            ToolUpgradeDefinition upgrade = config.toolUpgrades().definition(upgradeId);
            if (upgrade != null && upgrade.active() && !hasToolUpgrade(tool, upgrade.id())) {
                upgrades.add(upgrade);
            }
        }
        return upgrades;
    }

    public String shopLockedDetail(PlayerData data, MiningShopItem item) {
        if (data.level() < item.unlockLevel()) {
            return "Level " + item.unlockLevel();
        }
        for (String chapterId : item.requiredChapters()) {
            if (!data.hasClaimedJournalChapter(chapterId)) {
                JournalChapter chapter = config.journalChapter(chapterId);
                return chapter == null ? chapterId : chapter.displayName();
            }
        }
        return "";
    }

    public List<String> shopRewardLines(MiningShopItem item) {
        List<String> lines = new ArrayList<>();
        for (ItemStack rewardItem : item.rewardItems()) {
            lines.add(rewardItemLine(rewardItem, item.displayName()));
        }
        if (item.xpReward() > 0.0D) {
            lines.add(Text.render(config.guiString("reward-lines.xp"),
                    Map.of("amount", NumberFormat.decimal(item.xpReward()))));
        }
        if (item.pickaxeXpReward() > 0.0D) {
            lines.add(Text.render(config.guiString("reward-lines.pickaxe-xp"),
                    Map.of("amount", NumberFormat.decimal(item.pickaxeXpReward()))));
        }
        for (String upgradeId : item.toolUpgradeRewards()) {
            ToolUpgradeDefinition upgrade = config.toolUpgrades().definition(upgradeId);
            if (upgrade == null) {
                continue;
            }
            lines.add(Text.render(config.guiString("reward-lines.tool-upgrade"), Map.of(
                    "upgrade", upgrade.displayName(),
                    "rarity", upgrade.rarity()
            )));
        }
        if (item.moneyReward() > 0.0D) {
            lines.add(Text.render(config.guiString("reward-lines.money"),
                    Map.of("amount", formatMoney(item.moneyReward()))));
        }
        if (item.pointsReward() > 0L) {
            lines.add(Text.render(config.guiString("reward-lines.points"),
                    Map.of("amount", NumberFormat.integer(item.pointsReward()))));
        }
        if (item.perkPointsReward() > 0) {
            lines.add(Text.render(config.guiString("reward-lines.perk-points"),
                    Map.of("amount", NumberFormat.integer(item.perkPointsReward()))));
        }
        if (lines.isEmpty()) {
            lines.add(config.guiString("reward-lines.none"));
        }
        return lines;
    }

    public List<String> perkEffectLines(PerkDefinition definition) {
        List<String> lines = new ArrayList<>();
        if (definition.xpMultiplierPerLevel() > 0.0D) {
            lines.add(Text.render(config.guiString("perk-effect-lines.xp"), Map.of(
                    "amount", NumberFormat.decimal(definition.xpMultiplierPerLevel() * 100.0D)
            )));
        }
        if (definition.pickaxeXpMultiplierPerLevel() > 0.0D) {
            lines.add(Text.render(config.guiString("perk-effect-lines.pickaxe-xp"), Map.of(
                    "amount", NumberFormat.decimal(definition.pickaxeXpMultiplierPerLevel() * 100.0D)
            )));
        }
        if (definition.moneyMultiplierPerLevel() > 0.0D) {
            lines.add(Text.render(config.guiString("perk-effect-lines.money"), Map.of(
                    "amount", NumberFormat.decimal(definition.moneyMultiplierPerLevel() * 100.0D)
            )));
        }
        if (definition.pointsMultiplierPerLevel() > 0.0D) {
            lines.add(Text.render(config.guiString("perk-effect-lines.points"), Map.of(
                    "amount", NumberFormat.decimal(definition.pointsMultiplierPerLevel() * 100.0D)
            )));
        }
        if (definition.shardMultiplierPerLevel() > 0.0D) {
            lines.add(Text.render(config.guiString("perk-effect-lines.shards"), Map.of(
                    "amount", NumberFormat.decimal(definition.shardMultiplierPerLevel() * 100.0D)
            )));
        }
        if (definition.treasureChancePerLevel() > 0.0D) {
            lines.add(Text.render(config.guiString("perk-effect-lines.treasure"), Map.of(
                    "amount", NumberFormat.decimal(definition.treasureChancePerLevel() * 100.0D)
            )));
        }
        if (definition.artifactChancePerLevel() > 0.0D) {
            lines.add(Text.render(config.guiString("perk-effect-lines.artifact"), Map.of(
                    "amount", NumberFormat.decimal(definition.artifactChancePerLevel() * 100.0D)
            )));
        }
        if (definition.hazardChanceReductionPerLevel() > 0.0D) {
            lines.add(Text.render(config.guiString("perk-effect-lines.hazard-chance"), Map.of(
                    "amount", NumberFormat.decimal(definition.hazardChanceReductionPerLevel() * 100.0D)
            )));
        }
        if (definition.hazardDamageReductionPerLevel() > 0.0D) {
            lines.add(Text.render(config.guiString("perk-effect-lines.hazard-damage"), Map.of(
                    "amount", NumberFormat.decimal(definition.hazardDamageReductionPerLevel() * 100.0D)
            )));
        }
        if (lines.isEmpty()) {
            lines.add(config.guiString("perk-effect-lines.none"));
        }
        return lines;
    }

    public boolean isPickaxe(ItemStack item) {
        return item != null
                && !item.getType().isAir()
                && item.getType().name().endsWith("_PICKAXE")
                && item.getType().getMaxDurability() > 0;
    }

    public int pickaxeLevel(ItemStack item) {
        return isPickaxe(item) ? MiningItemTags.pickaxeLevel(plugin, item) : 0;
    }

    public double pickaxeXp(ItemStack item) {
        return isPickaxe(item) ? MiningItemTags.pickaxeXp(plugin, item) : 0.0D;
    }

    public double pickaxeXpRequired(ItemStack item) {
        if (!isPickaxe(item)) {
            return 0.0D;
        }
        int level = pickaxeLevel(item);
        LevelCurve curve = config.pickaxeProgression().curve();
        return curve.atMaxLevel(level) ? 0.0D : curve.xpToAdvance(level);
    }

    public double pickaxeProgressPercent(ItemStack item) {
        if (!isPickaxe(item)) {
            return 0.0D;
        }
        double required = pickaxeXpRequired(item);
        if (required <= 0.0D) {
            return 100.0D;
        }
        return Math.max(0.0D, Math.min(100.0D, (pickaxeXp(item) / required) * 100.0D));
    }

    public double pickaxeMiningXpBonusPercent(ItemStack item) {
        if (!isPickaxe(item)) {
            return 0.0D;
        }
        return Math.max(0.0D, (pickaxeLevel(item) - 1) * config.pickaxeProgression().miningXpMultiplierPerLevel() * 100.0D);
    }

    public double pickaxeArtifactBonusPercent(ItemStack item) {
        if (!isPickaxe(item)) {
            return 0.0D;
        }
        return Math.max(0.0D, (pickaxeLevel(item) - 1) * config.pickaxeProgression().artifactChancePerLevel() * 100.0D);
    }

    public void syncPickaxeLore(ItemStack item) {
        if (!isPickaxe(item)) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        List<Component> preserved = new ArrayList<>();
        List<String> generatedMarkers = pickaxeGeneratedLoreMarkers(item);
        for (Component line : lore) {
            if (matchesGeneratedLoreMarker(line, generatedMarkers)) {
                break;
            }
            preserved.add(line);
        }
        while (!preserved.isEmpty() && plain(preserved.get(preserved.size() - 1)).isBlank()) {
            preserved.remove(preserved.size() - 1);
        }
        if (!preserved.isEmpty()) {
            preserved.add(Component.empty());
        }
        for (String line : pickaxeItemLoreLines(item)) {
            preserved.add(Text.component(line));
        }
        meta.lore(preserved);
        item.setItemMeta(meta);
    }

    public boolean hasToolUpgrade(ItemStack item, String upgradeId) {
        return isPickaxe(item) && MiningItemTags.hasToolUpgrade(plugin, item, upgradeId);
    }

    public boolean applyAnvilPickaxeData(ItemStack result, ItemStack firstInput, ItemStack secondInput) {
        if (!isPickaxe(result) || (!isPickaxe(firstInput) && !isPickaxe(secondInput))) {
            return false;
        }
        if (!hasMiningPickaxeData(firstInput) && !hasMiningPickaxeData(secondInput)) {
            return false;
        }

        if (MiningItemTags.hasPickaxeProgress(plugin, firstInput)
                || MiningItemTags.hasPickaxeProgress(plugin, secondInput)) {
            PickaxeProgress progress = betterPickaxeProgress(firstInput, secondInput);
            MiningItemTags.setPickaxeProgress(plugin, result, progress.level(), progress.xp());
        }

        Set<String> upgrades = new LinkedHashSet<>();
        if (isPickaxe(firstInput)) {
            upgrades.addAll(MiningItemTags.toolUpgrades(plugin, firstInput));
        }
        if (isPickaxe(secondInput)) {
            upgrades.addAll(MiningItemTags.toolUpgrades(plugin, secondInput));
        }
        if (!upgrades.isEmpty()) {
            MiningItemTags.setToolUpgrades(plugin, result, upgrades);
        }
        syncPickaxeLore(result);
        return true;
    }

    private boolean hasMiningPickaxeData(ItemStack item) {
        return isPickaxe(item) && MiningItemTags.hasPickaxeData(plugin, item);
    }

    private PickaxeProgress betterPickaxeProgress(ItemStack firstInput, ItemStack secondInput) {
        PickaxeProgress best = pickaxeProgress(firstInput);
        PickaxeProgress candidate = pickaxeProgress(secondInput);
        if (candidate.level() > best.level()
                || (candidate.level() == best.level() && candidate.xp() > best.xp())) {
            return candidate;
        }
        return best;
    }

    private PickaxeProgress pickaxeProgress(ItemStack item) {
        if (!isPickaxe(item) || !MiningItemTags.hasPickaxeProgress(plugin, item)) {
            return new PickaxeProgress(1, 0.0D);
        }
        return new PickaxeProgress(MiningItemTags.pickaxeLevel(plugin, item), MiningItemTags.pickaxeXp(plugin, item));
    }

    public List<String> toolUpgradeLines(ItemStack item) {
        List<String> lines = new ArrayList<>();
        if (!isPickaxe(item)) {
            lines.add(config.guiString("tool-upgrades-no-pickaxe"));
            return lines;
        }
        for (ToolUpgradeDefinition definition : config.toolUpgrades().definitions()) {
            if (!definition.active()) {
                continue;
            }
            String key = hasToolUpgrade(item, definition.id())
                    ? "formats.tool-upgrade-owned"
                    : "formats.tool-upgrade-missing";
            lines.add(Text.render(config.guiString(key), Map.of(
                    "id", definition.id(),
                    "upgrade", definition.displayName(),
                    "rarity", definition.rarity()
            )));
        }
        if (lines.isEmpty()) {
            lines.add(config.guiString("tool-upgrades-none"));
        }
        return lines;
    }

    private List<String> pickaxeItemLoreLines(ItemStack item) {
        List<String> lines = new ArrayList<>();
        double required = pickaxeXpRequired(item);
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("level", String.valueOf(pickaxeLevel(item)));
        placeholders.put("xp", NumberFormat.decimal(pickaxeXp(item)));
        placeholders.put("xp_required", required <= 0.0D ? "MAX" : NumberFormat.decimal(required));
        placeholders.put("progress", NumberFormat.decimal(pickaxeProgressPercent(item)));
        placeholders.put("xp_bonus", NumberFormat.decimal(pickaxeMiningXpBonusPercent(item)));
        placeholders.put("artifact_bonus", NumberFormat.decimal(pickaxeArtifactBonusPercent(item)));
        List<String> upgrades = ownedToolUpgradeLines(item);
        if (!upgrades.isEmpty()) {
            lines.addAll(upgrades);
            lines.add("");
        } else if (!item.getEnchantments().isEmpty()) {
            lines.add("");
        }
        lines.add(Text.render(config.guiString("pickaxe-item-lore-header"), placeholders));
        lines.add(Text.render(config.guiString("pickaxe-item-lore-level"), placeholders));
        lines.add(Text.render(config.guiString("pickaxe-item-lore-xp"), placeholders));
        lines.add(Text.render(config.guiString("pickaxe-item-lore-xp-bonus"), placeholders));
        lines.add(Text.render(config.guiString("pickaxe-item-lore-artifact-bonus"), placeholders));
        return lines;
    }

    private List<String> pickaxeGeneratedLoreMarkers(ItemStack item) {
        List<String> markers = new ArrayList<>();
        String header = plain(config.guiString("pickaxe-item-lore-header"));
        if (!header.isBlank()) {
            markers.add(header);
        }
        for (String line : ownedToolUpgradeLines(item)) {
            String marker = plain(line);
            if (!marker.isBlank()) {
                markers.add(marker);
            }
        }
        return markers;
    }

    private boolean matchesGeneratedLoreMarker(Component line, List<String> markers) {
        if (line == null || markers.isEmpty()) {
            return false;
        }
        String text = plain(line);
        for (String marker : markers) {
            if (text.equalsIgnoreCase(marker)) {
                return true;
            }
        }
        return false;
    }

    private List<String> ownedToolUpgradeLines(ItemStack item) {
        List<String> lines = new ArrayList<>();
        if (!config.toolUpgrades().enabled()) {
            return lines;
        }
        for (ToolUpgradeDefinition definition : config.toolUpgrades().definitions()) {
            if (!definition.active() || !hasToolUpgrade(item, definition.id())) {
                continue;
            }
            lines.add(Text.render(config.guiString("pickaxe-item-lore-upgrade-entry"), Map.of(
                    "id", definition.id(),
                    "upgrade", definition.displayName(),
                    "rarity", definition.rarity()
            )));
        }
        return lines;
    }

    public ToolUpgradeDefinition rollToolUpgradeFromEnchantingTable(Player player, ItemStack item, int enchantLevel) {
        if (player == null || !config.toolUpgrades().enabled() || !isPickaxe(item)) {
            return null;
        }
        PlayerData data = playerStore.getOrCreate(player.getUniqueId(), player.getName());
        syncPickaxeLore(item);
        List<ToolUpgradeDefinition> available = new ArrayList<>();
        for (ToolUpgradeDefinition definition : config.toolUpgrades().definitions()) {
            if (definition.active() && !hasToolUpgrade(item, definition.id())) {
                available.add(definition);
            }
        }
        if (available.isEmpty()) {
            return null;
        }
        double chance = Math.min(config.toolUpgrades().maxChance(),
                config.toolUpgrades().baseChance() + Math.max(0, enchantLevel) * config.toolUpgrades().chancePerLevel());
        ToolUpgradeDefinition firstSelected = null;
        while (!available.isEmpty()) {
            if (ThreadLocalRandom.current().nextDouble() >= chance) {
                break;
            }
            ToolUpgradeDefinition selected = chooseToolUpgrade(available);
            if (selected == null) {
                break;
            }
            available.remove(selected);
            if (!addToolUpgrade(item, selected)) {
                continue;
            }
            recordToolUpgradeFound(player, selected);
            if (firstSelected == null) {
                firstSelected = selected;
            }
            send(player, "tool-upgrade-found", Map.of(
                    "upgrade", selected.displayName(),
                    "chance", NumberFormat.decimal(chance * 100.0D)
            ));
            if (!config.toolUpgrades().allowMultiplePerEnchant()) {
                break;
            }
        }
        if (firstSelected == null) {
            firstSelected = rollPityToolUpgrade(player, data, item, available, enchantLevel);
            if (firstSelected == null) {
                return null;
            }
        } else {
            data.resetEnchantsSinceToolUpgrade();
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 1.0F, 1.25F);
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0.0D, 1.0D, 0.0D),
                36, 0.45D, 0.6D, 0.45D, 0.08D);
        return firstSelected;
    }

    private ToolUpgradeDefinition rollPityToolUpgrade(Player player, PlayerData data, ItemStack item,
                                                      List<ToolUpgradeDefinition> available, int enchantLevel) {
        int pityEnchants = config.toolUpgrades().pityEnchants();
        if (pityEnchants <= 0 || enchantLevel < config.toolUpgrades().pityMinimumEnchantLevel()) {
            return null;
        }
        data.addEnchantWithoutToolUpgrade();
        if (data.enchantsSinceToolUpgrade() < pityEnchants) {
            return null;
        }
        ToolUpgradeDefinition selected = chooseToolUpgrade(available);
        if (selected == null || !addToolUpgrade(item, selected)) {
            return null;
        }
        recordToolUpgradeFound(player, selected);
        send(player, "tool-upgrade-found", Map.of(
                "upgrade", selected.displayName(),
                "chance", NumberFormat.decimal(100.0D)
        ));
        return selected;
    }

    private boolean autoSmeltActive(Player player, ItemStack tool) {
        return config.autoSmeltEnabled()
                && player.hasPermission("miningplus.autosmelt")
                && toolUpgradeActive(tool, "auto-smelt");
    }

    private boolean autoPickupActive(Player player, ItemStack tool) {
        return config.autoPickupEnabled()
                && player.hasPermission("miningplus.autopickup")
                && toolUpgradeActive(tool, "auto-pickup");
    }

    private boolean toolUpgradeActive(ItemStack tool, String upgradeId) {
        if (!config.toolUpgrades().enabled() || !config.toolUpgrades().requireForFeatures()) {
            return true;
        }
        return activeToolUpgrade(tool, upgradeId);
    }

    private boolean activeToolUpgrade(ItemStack tool, String upgradeId) {
        if (!config.toolUpgrades().enabled()) {
            return false;
        }
        ToolUpgradeDefinition definition = config.toolUpgrades().definition(upgradeId);
        return definition != null && definition.active() && hasToolUpgrade(tool, upgradeId);
    }

    private ToolUpgradeDefinition chooseToolUpgrade(List<ToolUpgradeDefinition> upgrades) {
        double totalWeight = 0.0D;
        for (ToolUpgradeDefinition upgrade : upgrades) {
            totalWeight += Math.max(0.0D, upgrade.weight());
        }
        if (totalWeight <= 0.0D) {
            return null;
        }
        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cursor = 0.0D;
        for (ToolUpgradeDefinition upgrade : upgrades) {
            cursor += Math.max(0.0D, upgrade.weight());
            if (roll <= cursor) {
                return upgrade;
            }
        }
        return upgrades.getLast();
    }

    private boolean addToolUpgrade(ItemStack item, ToolUpgradeDefinition upgrade) {
        if (!MiningItemTags.addToolUpgrade(plugin, item, upgrade.id())) {
            return false;
        }
        syncPickaxeLore(item);
        return true;
    }

    private void recordToolUpgradeFound(Player player, ToolUpgradeDefinition upgrade) {
        if (player == null || upgrade == null) {
            return;
        }
        PlayerData data = playerStore.getOrCreate(player.getUniqueId(), player.getName());
        data.addToolUpgradeFound(upgrade.id(), 1L);
        data.resetEnchantsSinceToolUpgrade();
    }

    public double pickaxeRefineMoneyCost(ItemStack item) {
        int level = Math.max(1, pickaxeLevel(item));
        PickaxeProgressionSettings settings = config.pickaxeProgression();
        return settings.refineMoneyBase() + Math.max(0, level - 1) * settings.refineMoneyPerLevel();
    }

    public long pickaxeRefineShardCost(ItemStack item) {
        int level = Math.max(1, pickaxeLevel(item));
        PickaxeProgressionSettings settings = config.pickaxeProgression();
        return safeAdd(settings.refineShardBase(), safeMultiply(settings.refineShardPerLevel(), Math.max(0, level - 1)));
    }

    public PickaxeRefineResult refinePickaxe(Player player) {
        if (!config.pickaxeProgressionEnabled()
                || !config.pickaxeProgression().refineEnabled()
                || !player.hasPermission(config.pickaxeProgression().permission())) {
            return new PickaxeRefineResult(PickaxeRefineResult.Status.DISABLED, 0, 0.0D, 0.0D, 0L, 0.0D);
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!isPickaxe(tool)) {
            return new PickaxeRefineResult(PickaxeRefineResult.Status.NO_PICKAXE, 0, 0.0D, 0.0D, 0L, 0.0D);
        }
        int level = MiningItemTags.pickaxeLevel(plugin, tool);
        double xp = MiningItemTags.pickaxeXp(plugin, tool);
        LevelCurve curve = config.pickaxeProgression().curve();
        if (curve.atMaxLevel(level)) {
            return new PickaxeRefineResult(PickaxeRefineResult.Status.MAX_LEVEL, level, xp, 0.0D, 0L, 0.0D);
        }
        double moneyCost = pickaxeRefineMoneyCost(tool);
        long shardCost = pickaxeRefineShardCost(tool);
        PlayerData data = playerStore.getOrCreate(player.getUniqueId(), player.getName());
        if (moneyCost > 0.0D) {
            if (!economy.available()) {
                return new PickaxeRefineResult(PickaxeRefineResult.Status.NO_ECONOMY, level, xp, moneyCost, shardCost, 0.0D);
            }
            if (!economy.has(player, moneyCost)) {
                return new PickaxeRefineResult(PickaxeRefineResult.Status.INSUFFICIENT_MONEY, level, xp, moneyCost, shardCost, 0.0D);
            }
        }
        if (shardCost > 0L && data.shards() < shardCost) {
            return new PickaxeRefineResult(PickaxeRefineResult.Status.INSUFFICIENT_SHARDS, level, xp, moneyCost, shardCost, 0.0D);
        }
        if (moneyCost > 0.0D && !economy.withdraw(player, moneyCost).transactionSuccess()) {
            return new PickaxeRefineResult(PickaxeRefineResult.Status.INSUFFICIENT_MONEY, level, xp, moneyCost, shardCost, 0.0D);
        }
        if (shardCost > 0L && !data.spendShards(shardCost)) {
            if (moneyCost > 0.0D) {
                economy.deposit(player, moneyCost);
            }
            return new PickaxeRefineResult(PickaxeRefineResult.Status.INSUFFICIENT_SHARDS, level, xp, moneyCost, shardCost, 0.0D);
        }

        double gained = scaledPickaxeXp(player, config.pickaxeProgression().refineXp());
        PickaxeProgress progress = addPickaxeXp(tool, gained);
        data.addPickaxeRefines(1L);
        data.recordPickaxeLevel(progress.level());
        if (progress.level() > level) {
            send(player, "pickaxe-level-up", Map.of(
                    "level", String.valueOf(progress.level()),
                    "bonus", NumberFormat.decimal(pickaxeMiningXpBonusPercent(tool))
            ));
        }
        playFeedback(player, player.getLocation(), config.feedback().refine(), Map.of(
                "level", String.valueOf(progress.level()),
                "xp", NumberFormat.decimal(progress.xp()),
                "cost", formatMoney(moneyCost),
                "money", formatMoney(moneyCost),
                "shards", NumberFormat.integer(shardCost),
                "gained", NumberFormat.decimal(gained)
        ));
        return new PickaxeRefineResult(PickaxeRefineResult.Status.SUCCESS, progress.level(), progress.xp(),
                moneyCost, shardCost, gained);
    }

    private void giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
        for (ItemStack stack : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), stack);
        }
    }

    private int giveItems(Player player, List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        int awarded = 0;
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            giveItem(player, item);
            awarded++;
        }
        return awarded;
    }

    private String rewardItemLine(ItemStack item, String fallbackName) {
        return Text.render(config.guiString("reward-lines.item"), Map.of(
                "amount", NumberFormat.integer(item.getAmount()),
                "item", itemDisplayName(item, fallbackName)
        ));
    }

    private String itemDisplayName(ItemStack item, String fallbackName) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.displayName() != null) {
            return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        }
        if (fallbackName != null && !fallbackName.isBlank()) {
            return fallbackName;
        }
        return formatMaterial(item.getType());
    }

    private void runShopCommands(Player player, MiningShopItem item) {
        for (String command : item.commands()) {
            if (command == null || command.isBlank()) {
                continue;
            }
            String parsed = command
                    .replace("{player}", player.getName())
                    .replace("{item}", item.id())
                    .replace("{cost}", String.valueOf(item.cost()));
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), parsed);
        }
    }

    private double sellValue(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0.0D;
        }
        String itemId = MiningItemTags.itemId(plugin, item);
        if (!itemId.isBlank()) {
            double customPrice = config.customSellPrice(MiningItemTags.itemKind(plugin, item), itemId);
            if (customPrice > 0.0D) {
                return safeMultiply(customPrice, item.getAmount());
            }
            if (!config.sellCustomItemsByMaterial()) {
                return 0.0D;
            }
        }
        if (!config.sellMaterialPricesEnabled()) {
            return 0.0D;
        }
        double price = config.sellPrice(item.getType());
        if (price <= 0.0D) {
            return 0.0D;
        }
        return safeMultiply(price, item.getAmount());
    }

    private long safeAdd(long current, long amount) {
        if (amount <= 0L) {
            return current;
        }
        if (current > Long.MAX_VALUE - amount) {
            return Long.MAX_VALUE;
        }
        return current + amount;
    }

    private double safeAdd(double current, double amount) {
        if (amount <= 0.0D || !Double.isFinite(amount)) {
            return current;
        }
        double result = current + amount;
        return Double.isFinite(result) ? result : Double.MAX_VALUE;
    }

    private long safeMultiply(long value, int multiplier) {
        if (value <= 0L || multiplier <= 0) {
            return 0L;
        }
        if (value > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }
        return value * multiplier;
    }

    private double safeMultiply(double value, int multiplier) {
        if (value <= 0.0D || multiplier <= 0 || !Double.isFinite(value)) {
            return 0.0D;
        }
        double result = value * multiplier;
        return Double.isFinite(result) ? result : Double.MAX_VALUE;
    }

    private int safeMultiplyToInt(int value, int multiplier) {
        if (value <= 0 || multiplier <= 0) {
            return 0;
        }
        if (value > Integer.MAX_VALUE / multiplier) {
            return Integer.MAX_VALUE;
        }
        return value * multiplier;
    }

    private long scaleLong(long value, double multiplier) {
        if (value <= 0L || multiplier <= 0.0D || !Double.isFinite(multiplier)) {
            return 0L;
        }
        double scaled = value * multiplier;
        if (scaled >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, Math.round(scaled));
    }

    private void capPerkPoints(PlayerData data) {
        int cap = config.perks().maxBankedPoints();
        if (cap > 0 && data.perkPoints() > cap) {
            data.perkPoints(cap);
        }
    }

    private void advancePickaxe(Player player, ItemStack tool, double miningXpGained) {
        if (!config.pickaxeProgressionEnabled()
                || !player.hasPermission(config.pickaxeProgression().permission())
                || !isPickaxe(tool)) {
            return;
        }
        PickaxeProgressionSettings settings = config.pickaxeProgression();
        PlayerData data = playerStore.getOrCreate(player.getUniqueId(), player.getName());
        int startingLevel = MiningItemTags.pickaxeLevel(plugin, tool);
        double gained = settings.xpPerRewardBlock()
                + Math.max(0.0D, miningXpGained) * settings.xpFromMiningXp();
        gained = scaledPickaxeXp(player, gained);
        PickaxeProgress progress = addPickaxeXp(tool, gained);
        data.recordPickaxeLevel(progress.level());
        if (progress.level() > startingLevel) {
            send(player, "pickaxe-level-up", Map.of(
                    "level", String.valueOf(progress.level()),
                    "bonus", NumberFormat.decimal(pickaxeMiningXpBonusPercent(tool))
            ));
        }
    }

    private PickaxeProgress addPickaxeXp(ItemStack tool, double amount) {
        int level = MiningItemTags.pickaxeLevel(plugin, tool);
        double xp = MiningItemTags.pickaxeXp(plugin, tool) + Math.max(0.0D, amount);
        LevelCurve curve = config.pickaxeProgression().curve();
        while (!curve.atMaxLevel(level) && xp >= curve.xpToAdvance(level)) {
            xp -= curve.xpToAdvance(level);
            level++;
        }
        if (curve.atMaxLevel(level)) {
            xp = 0.0D;
        }
        MiningItemTags.setPickaxeProgress(plugin, tool, level, xp);
        syncPickaxeLore(tool);
        return new PickaxeProgress(level, xp);
    }

    private double scaledPickaxeXp(Player player, double amount) {
        if (amount <= 0.0D || !Double.isFinite(amount)) {
            return 0.0D;
        }
        double scaled = amount * pickaxeXpMultiplier(player);
        return Double.isFinite(scaled) ? scaled : Double.MAX_VALUE;
    }

    private record PickaxeProgress(int level, double xp) {
    }

    private record ActiveTimedChallenge(
            MiningEventSettings.EventAction action,
            long expiresAtMillis,
            int progress
    ) {
    }

    private record SalvageCount(long items, long fragments) {
    }

    private void awardXp(Player player, PlayerData data, double amount) {
        if (amount <= 0.0D || !Double.isFinite(amount)) {
            return;
        }
        data.addXp(amount);
        applyLevelProgress(player, data, amount);
    }

    private void applyLevelProgress(Player player, PlayerData data, double amount) {
        LevelCurve curve = config.levelCurve();
        int startLevel = data.level();
        while (!curve.atMaxLevel(data.level())) {
            double required = curve.xpToAdvance(data.level());
            if (data.xp() < required) {
                break;
            }
            data.xp(data.xp() - required);
            data.level(data.level() + 1);
            if (player != null) {
                applyMilestone(player, data.level());
            }
        }
        if (curve.atMaxLevel(data.level())) {
            data.xp(0.0D);
        }
        if (data.level() > startLevel) {
            syncPerkPoints(data);
            if (player != null) {
                announceLevelUp(player, data.level());
            }
        } else if (player != null && config.actionBarEnabled() && data.notificationsEnabled()) {
            sendXpActionBar(player, data, amount);
        }
    }

    private void sendXpActionBar(Player player, PlayerData data, double gained) {
        LevelCurve curve = config.levelCurve();
        String progress = curve.atMaxLevel(data.level())
                ? "MAX"
                : NumberFormat.decimal(progressPercent(data)) + "%";
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("gained", NumberFormat.decimal(gained));
        placeholders.put("level", String.valueOf(data.level()));
        placeholders.put("progress", progress);
        player.sendActionBar(Text.component(Text.render(config.message("xp-actionbar"), placeholders)));
    }

    private void playMiningFeedback(Player player, Location location, BlockReward reward, double xpGained,
                                    PlayerData data) {
        if (!config.feedbackEnabled() || reward == null || !data.notificationsEnabled()) {
            return;
        }
        int cooldownTicks = config.feedback().miningCooldownTicks();
        if (cooldownTicks > 0) {
            long now = System.currentTimeMillis();
            long nextAt = miningFeedbackCooldowns.getOrDefault(player.getUniqueId(), 0L);
            if (nextAt > now) {
                return;
            }
            miningFeedbackCooldowns.put(player.getUniqueId(), now + cooldownTicks * 50L);
        }
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("block", formatMaterial(reward.material()));
        placeholders.put("xp", NumberFormat.decimal(xpGained));
        placeholders.put("level", String.valueOf(data.level()));
        placeholders.put("rank", config.rankFor(data.level()));
        placeholders.put("progress", NumberFormat.decimal(progressPercent(data)));
        playFeedback(player, location, config.feedback().mining(), placeholders);
    }

    private void playFeedback(Player player, Location location, FeedbackSettings.Effect effect,
                              Map<String, String> placeholders) {
        if (player == null || effect == null || !config.feedbackEnabled() || !effect.enabled()) {
            return;
        }
        Map<String, String> values = placeholders == null ? Map.of() : placeholders;
        if (!effect.actionBar().isBlank()) {
            player.sendActionBar(Text.component(Text.render(effect.actionBar(), values)));
        }
        if (effect.titleEnabled() && (!effect.title().isBlank() || !effect.subtitle().isBlank())) {
            player.showTitle(Title.title(
                    Text.component(Text.render(effect.title(), values)),
                    Text.component(Text.render(effect.subtitle(), values)),
                    Title.Times.times(
                            Duration.ofMillis(effect.fadeInTicks() * 50L),
                            Duration.ofMillis(effect.stayTicks() * 50L),
                            Duration.ofMillis(effect.fadeOutTicks() * 50L)
                    )
            ));
        }

        Location origin = location == null ? player.getLocation() : location;
        if (effect.soundEnabled() && effect.sound() != null) {
            player.playSound(origin, effect.sound(), SoundCategory.PLAYERS, effect.volume(), effect.pitch());
        }
        if (effect.particlesEnabled()
                && effect.particle() != null
                && effect.particleCount() > 0
                && origin.getWorld() != null) {
            try {
                origin.getWorld().spawnParticle(effect.particle(), origin.clone().add(0.0D, 0.7D, 0.0D),
                        effect.particleCount(), effect.offset(), effect.offset(), effect.offset(), effect.speed());
            } catch (IllegalArgumentException ignored) {
                if (feedbackParticleWarnings.add(effect.particle())) {
                    plugin.getLogger().warning("Feedback particle '" + effect.particle().getKey()
                            + "' needs extra particle data and cannot be used by generic feedback effects.");
                }
            }
        }
    }

    private String formatMaterial(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String plain(String line) {
        return PlainTextComponentSerializer.plainText().serialize(Text.component(line == null ? "" : line)).trim();
    }

    private String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component == null ? Component.empty() : component).trim();
    }

    private void applyMilestone(Player player, int level) {
        MilestoneReward milestone = config.milestone(level);
        if (milestone == null) {
            return;
        }
        if (milestone.money() > 0.0D && economy.available()) {
            if (economy.deposit(player, milestone.money()).transactionSuccess()) {
                playerStore.getOrCreate(player.getUniqueId(), player.getName()).addMoneyEarned(milestone.money());
            }
        }
        for (String command : milestone.commands()) {
            String parsed = command.replace("{player}", player.getName()).replace("{level}", String.valueOf(level));
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), parsed);
        }
        if (milestone.message() != null && !milestone.message().isBlank()) {
            player.sendMessage(Text.component(milestone.message().replace("{level}", String.valueOf(level))));
        }
    }

    private void announceLevelUp(Player player, int level) {
        LevelUpEffects effects = config.levelUpEffects();
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("level", String.valueOf(level));
        placeholders.put("rank", config.rankFor(level));
        send(player, "level-up", placeholders);

        boolean enhancedFeedback = config.feedbackEnabled() && config.feedback().levelUp().enabled();
        if (!enhancedFeedback && effects.soundEnabled()) {
            player.playSound(player, effects.sound(), effects.soundVolume(), effects.soundPitch());
        }
        if (!enhancedFeedback && effects.particleEnabled() && effects.particleCount() > 0) {
            player.getWorld().spawnParticle(effects.particle(),
                    player.getLocation().add(0.0D, 1.0D, 0.0D), effects.particleCount(),
                    0.4D, 0.6D, 0.4D, 0.0D);
        }
        if (effects.broadcast()) {
            plugin.getServer().broadcast(Text.component(Text.render(config.message("level-up-broadcast"), placeholders)));
        }
        playFeedback(player, player.getLocation(), config.feedback().levelUp(), placeholders);
    }

    public double xpMultiplier(Player player) {
        double best = 1.0D;
        for (MiningPlusConfig.PermissionMultiplier multiplier : config.permissionMultipliers()) {
            if (player.hasPermission(multiplier.permission())) {
                best = Math.max(best, multiplier.xp());
            }
        }
        return best + perkBonus(player, PerkDefinition::xpMultiplierPerLevel)
                + artifactSetBonus(player, ArtifactSetDefinition::miningXpMultiplier);
    }

    public double moneyMultiplier(Player player) {
        double best = 1.0D;
        for (MiningPlusConfig.PermissionMultiplier multiplier : config.permissionMultipliers()) {
            if (player.hasPermission(multiplier.permission())) {
                best = Math.max(best, multiplier.money());
            }
        }
        return best + perkBonus(player, PerkDefinition::moneyMultiplierPerLevel)
                + artifactSetBonus(player, ArtifactSetDefinition::moneyMultiplier);
    }

    private double pointsMultiplier(Player player) {
        return 1.0D + perkBonus(player, PerkDefinition::pointsMultiplierPerLevel)
                + artifactSetBonus(player, ArtifactSetDefinition::pointsMultiplier);
    }

    private double pickaxeXpMultiplier(Player player) {
        return 1.0D + perkBonus(player, PerkDefinition::pickaxeXpMultiplierPerLevel)
                + artifactSetBonus(player, ArtifactSetDefinition::pickaxeXpMultiplier);
    }

    private double pickaxeMiningXpMultiplier(Player player, ItemStack tool) {
        if (!config.pickaxeProgressionEnabled()
                || !player.hasPermission(config.pickaxeProgression().permission())
                || !isPickaxe(tool)) {
            return 1.0D;
        }
        double bonus = Math.max(0, MiningItemTags.pickaxeLevel(plugin, tool) - 1)
                * config.pickaxeProgression().miningXpMultiplierPerLevel();
        return 1.0D + Math.max(0.0D, bonus);
    }

    private double pickaxeArtifactChanceMultiplier(Player player, ItemStack tool) {
        double bonus = perkBonus(player, PerkDefinition::artifactChancePerLevel)
                + artifactSetBonus(player, ArtifactSetDefinition::artifactChance);
        if (isPickaxe(tool) && config.pickaxeProgressionEnabled()
                && player.hasPermission(config.pickaxeProgression().permission())) {
            bonus += Math.max(0, MiningItemTags.pickaxeLevel(plugin, tool) - 1)
                    * config.pickaxeProgression().artifactChancePerLevel();
        }
        if (activeToolUpgrade(tool, PROSPECTOR_UPGRADE)) {
            bonus += PROSPECTOR_ARTIFACT_CHANCE_BONUS;
        }
        return 1.0D + Math.max(0.0D, bonus);
    }

    private double treasureChanceMultiplier(PlayerData data) {
        return 1.0D + perkBonus(data, PerkDefinition::treasureChancePerLevel)
                + artifactSetBonus(data, ArtifactSetDefinition::treasureChance);
    }

    private double hazardChanceMultiplier(PlayerData data) {
        return Math.max(0.0D, 1.0D - perkBonus(data, PerkDefinition::hazardChanceReductionPerLevel)
                - artifactSetBonus(data, ArtifactSetDefinition::hazardChanceReduction));
    }

    private double hazardDamageMultiplier(PlayerData data) {
        return Math.max(0.0D, 1.0D - perkBonus(data, PerkDefinition::hazardDamageReductionPerLevel)
                - artifactSetBonus(data, ArtifactSetDefinition::hazardDamageReduction));
    }

    private double perkBonus(Player player, ToDoubleFunction<PerkDefinition> effect) {
        if (!config.perksEnabled()) {
            return 0.0D;
        }
        PlayerData data = playerStore.getOrCreate(player.getUniqueId(), player.getName());
        syncPerkPoints(data);
        return perkBonus(data, effect);
    }

    private double perkBonus(PlayerData data, ToDoubleFunction<PerkDefinition> effect) {
        if (!config.perksEnabled() || data == null) {
            return 0.0D;
        }
        double bonus = 0.0D;
        for (PerkDefinition definition : config.perks().definitions()) {
            int level = data.perkLevel(definition.id());
            if (definition.active() && level > 0) {
                bonus += Math.max(0.0D, effect.applyAsDouble(definition)) * level;
            }
        }
        return bonus;
    }

    private double artifactSetBonus(Player player, ToDoubleFunction<ArtifactSetDefinition> effect) {
        if (player == null) {
            return 0.0D;
        }
        return artifactSetBonus(playerStore.getOrCreate(player.getUniqueId(), player.getName()), effect);
    }

    private double artifactSetBonus(PlayerData data, ToDoubleFunction<ArtifactSetDefinition> effect) {
        if (!config.artifactsEnabled() || data == null) {
            return 0.0D;
        }
        double bonus = 0.0D;
        for (ArtifactSetDefinition set : config.artifactSets()) {
            if (set.active() && artifactSetComplete(data, set)) {
                bonus += Math.max(0.0D, effect.applyAsDouble(set));
            }
        }
        return bonus;
    }

    private int fortuneLevel(ItemStack tool) {
        return tool == null ? 0 : tool.getEnchantmentLevel(Enchantment.FORTUNE);
    }

    public double progressPercent(PlayerData data) {
        LevelCurve curve = config.levelCurve();
        if (curve.atMaxLevel(data.level())) {
            return 100.0D;
        }
        double required = curve.xpToAdvance(data.level());
        if (required <= 0.0D) {
            return 100.0D;
        }
        return Math.max(0.0D, Math.min(100.0D, (data.xp() / required) * 100.0D));
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String template = config.message(key);
        if (template == null || template.isBlank()) {
            return;
        }
        String rendered = Text.render(config.prefix() + template, placeholders);
        sender.sendMessage(Text.component(rendered));
    }

    public void sendRaw(CommandSender sender, String message) {
        sender.sendMessage(Text.component(message));
    }

    public Component component(String message) {
        return Text.component(message);
    }

    public String formatMoney(double amount) {
        return economy.available() ? economy.format(amount) : NumberFormat.decimal(amount);
    }
}
