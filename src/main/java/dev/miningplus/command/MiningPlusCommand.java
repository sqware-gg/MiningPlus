package dev.miningplus.command;

import dev.miningplus.data.PlayerData;
import dev.miningplus.mining.MiningService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class MiningPlusCommand implements CommandExecutor, TabCompleter {
    private final MiningService service;
    private final Runnable reloadHook;

    public MiningPlusCommand(MiningService service, Runnable reloadHook) {
        this.service = service;
        this.reloadHook = reloadHook;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("miningplus.admin")) {
            service.send(sender, "no-permission", Map.of());
            return true;
        }
        if (args.length == 0) {
            service.send(sender, "usage-admin", Map.of());
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "save" -> save(sender);
            case "stats" -> stats(sender);
            case "setlevel" -> setLevel(sender, args);
            case "addxp" -> addXp(sender, args);
            default -> service.send(sender, "usage-admin", Map.of());
        }
        return true;
    }

    private void reload(CommandSender sender) {
        service.config().reload();
        service.economy().refresh();
        reloadHook.run();
        service.send(sender, "reloaded", Map.of());
    }

    private void save(CommandSender sender) {
        service.saveAll();
        service.send(sender, "saved", Map.of());
    }

    private void stats(CommandSender sender) {
        service.send(sender, "admin-stats", Map.of(
                "players", String.valueOf(service.players().playerCount()),
                "blocks", String.valueOf(service.config().blockRewards().size()),
                "tracked", String.valueOf(service.placedBlocks().size()),
                "economy", service.economy().available() ? service.economy().providerName() : "none",
                "points", service.points().available() ? "PointsPlus" : "none"
        ));
    }

    private void setLevel(CommandSender sender, String[] args) {
        if (args.length != 3) {
            service.send(sender, "usage-admin", Map.of());
            return;
        }
        OfflinePlayer target = resolvePlayer(args[1]);
        Integer level = positiveInt(args[2]);
        if (target == null || target.getName() == null) {
            service.send(sender, "invalid-player", Map.of());
            return;
        }
        if (level == null) {
            service.send(sender, "invalid-amount", Map.of());
            return;
        }
        PlayerData data = service.players().getOrCreate(target.getUniqueId(), target.getName());
        data.level(Math.min(level, service.config().levelCurve().maxLevel()));
        data.xp(0.0D);
        service.send(sender, "set-level", Map.of(
                "player", target.getName(),
                "level", String.valueOf(data.level())
        ));
    }

    private void addXp(CommandSender sender, String[] args) {
        if (args.length != 3) {
            service.send(sender, "usage-admin", Map.of());
            return;
        }
        OfflinePlayer target = resolvePlayer(args[1]);
        Double amount = positiveDouble(args[2]);
        if (target == null || target.getName() == null) {
            service.send(sender, "invalid-player", Map.of());
            return;
        }
        if (amount == null) {
            service.send(sender, "invalid-amount", Map.of());
            return;
        }
        Player online = target.getPlayer();
        if (online == null) {
            PlayerData data = service.players().getOrCreate(target.getUniqueId(), target.getName());
            service.grantStoredXp(data, amount);
        } else {
            service.grantXp(online, amount);
        }
        service.send(sender, "add-xp", Map.of(
                "player", target.getName(),
                "amount", String.valueOf(amount)
        ));
    }

    private OfflinePlayer resolvePlayer(String name) {
        OfflinePlayer online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline : null;
    }

    private Integer positiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double positiveDouble(String value) {
        try {
            double parsed = Double.parseDouble(value);
            return parsed > 0.0D && Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("miningplus.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("reload", "save", "stats", "setlevel", "addxp"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("setlevel") || args[0].equalsIgnoreCase("addxp"))) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(player -> names.add(player.getName()));
            return filter(names, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setlevel")) {
            return filter(List.of("1", "10", "25", "50", "100"), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("addxp")) {
            return filter(List.of("100", "500", "1000"), args[2]);
        }
        return List.of();
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
}
