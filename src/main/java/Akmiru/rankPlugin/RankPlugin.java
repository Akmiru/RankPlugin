package Akmiru.rankPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.permissions.PermissionAttachment;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RankPlugin extends JavaPlugin implements Listener {

    private RankPermissionManager permissionManager;

    private final Map<String, String> playerRanks = new ConcurrentHashMap<>();

    private final Map<String, ChatColor> rankColors = new HashMap<>();
    private final Map<String, List<String>> rankPermissions = new HashMap<>();
    private File ranksFile;
    private FileConfiguration ranksConfig;
    private TabListManager tabListManager;
    private FileConfiguration permissionsConfig;
    private HashMap<UUID, PermissionAttachment> playerPermissions = new HashMap<>();

    @Override
    public void onEnable() {
        // Initialize rank colors
        initializeRankColors();

        // Initialize files
        createRanksFile();

        //Permission Manager
        permissionManager = new RankPermissionManager();

        // Load ranks from file asynchronously
        new BukkitRunnable() {
            @Override
            public void run() {
                loadRanksFromFile();

            }
        }.runTaskAsynchronously(this);

        // Register events
        Bukkit.getPluginManager().registerEvents(this, this);

        // Initialize and start the TabListManager
        tabListManager = new TabListManager(this, playerRanks, rankColors);
        tabListManager.startTabListUpdates();

        getLogger().info("RankPlugin has been enabled!");
    }

    @Override
    public void onDisable() {
        // Save ranks to file asynchronously
        new BukkitRunnable() {
            @Override
            public void run() {
                saveRanksToFile();
            }
        }.runTaskAsynchronously(this);

        getLogger().info("RankPlugin has been disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("apermission")) {
            if (args.length != 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /apermission <player> <rank>");
                return false;
            }

            String playerName = args[0];
            String rank = args[1];

            // Validate rank
            if (!isValidRank(rank)) {
                sender.sendMessage(ChatColor.RED + "Invalid rank. Valid ranks: Owner, Admin, Helper, Moderator, VIP");
                return false;
            }

            // Assign rank
            playerRanks.put(playerName, rank);
            sender.sendMessage(ChatColor.GREEN + "Assigned rank " + rank + " to " + playerName);

            // Notify player if online
            Player player = Bukkit.getPlayer(playerName);
            if (player != null) {
                player.sendMessage(ChatColor.GREEN + "Your rank has been set to " + rank);
                assignPermissions(player);
            }

            // Save the updated ranks to the file asynchronously
            new BukkitRunnable() {
                @Override
                public void run() {
                    saveRanksToFile();
                }
            }.runTaskAsynchronously(this);

            return true;
        }

        return false;
    }
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Load player's rank if not present in memory
        if (!playerRanks.containsKey(player.getName())) {
            // Default to "Player" rank if no rank is found in file
            String rank = ranksConfig.getString(player.getName(), "Player");
            playerRanks.put(player.getName(), rank);
        }

        // Assign permissions based on rank
        assignPermissions(player);
    }


    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        if (playerPermissions.containsKey(playerUUID)) {
            playerPermissions.get(playerUUID).remove();
            playerPermissions.remove(playerUUID);
        }
    }
    public void assignPermissions(Player player) {
        String rank;

        // Get player's rank from memory (playerRanks)
        synchronized (playerRanks) {
            rank = playerRanks.getOrDefault(player.getName(), "Player");
        }

        // List of permissions based on rank
        List<String> permissions = permissionManager.getPermissionsForRank(rank);


        // Remove any old permission attachment for this player
        if (playerPermissions.containsKey(player.getUniqueId())) {
            playerPermissions.get(player.getUniqueId()).remove();
        }

        // Create a new PermissionAttachment for the player
        PermissionAttachment attachment = player.addAttachment(this);

        // Assign permissions to the attachment
        for (String permission : permissions) {
            attachment.setPermission(permission, true);
            getLogger().info("Setting permission for " + player.getName() + ": " + permission);
        }

        // Store the permission attachment in the playerPermissions map
        playerPermissions.put(player.getUniqueId(), attachment);

        // Recalculate permissions to make sure they apply
        player.recalculatePermissions();

        getLogger().info("Permissions assigned for " + player.getName() + ": " + player.getEffectivePermissions());
    }

    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        String message = event.getMessage();

        // Get rank
        String rank = playerRanks.getOrDefault(playerName, "Player");

        // Get rank color
        NamedTextColor rankColor = NamedTextColor.NAMES.value(rankColors.getOrDefault(rank, ChatColor.WHITE).name().toLowerCase());

        // Create a formatted rank message using Adventure API with bold rank
        TextComponent rankComponent = Component.text("[" + rank + "] ")
                .color(rankColor)
                .decorate(TextDecoration.BOLD); // Make the rank bold

        // Create components for the player name and message with normal styling
        TextComponent playerComponent = Component.text(playerName + ": ").color(NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false);
        TextComponent messageComponent = Component.text(message).color(NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false);

        // Combine components
        TextComponent formattedMessage = rankComponent.append(playerComponent).append(messageComponent);

        // Send formatted message to all players
        Bukkit.getServer().sendMessage(formattedMessage);

        // Cancel the original event to prevent double messaging
        event.setCancelled(true);
    }


    private void initializeRankColors() {
        rankColors.put("Owner", ChatColor.RED);
        rankColors.put("Admin", ChatColor.GOLD);
        rankColors.put("Helper", ChatColor.BLUE);
        rankColors.put("Moderator", ChatColor.GREEN);
        rankColors.put("VIP", ChatColor.YELLOW);
        rankColors.put("Player", ChatColor.GRAY); // Default rank color
    }

    private boolean isValidRank(String rank) {
        return rank.equalsIgnoreCase("Owner") || rank.equalsIgnoreCase("Admin") ||
                rank.equalsIgnoreCase("Helper") || rank.equalsIgnoreCase("Moderator")||
        rank.equalsIgnoreCase("Player") || rank.equalsIgnoreCase("VIP");
    }

    private void createRanksFile() {
        ranksFile = new File(getDataFolder(), "ranks.yml");
        if (!ranksFile.exists()) {
            try {
                getDataFolder().mkdir();
                ranksFile.createNewFile();
                getLogger().info("Created ranks.yml file.");
            } catch (IOException e) {
                getLogger().severe("Could not create ranks.yml file.");
                e.printStackTrace();
            }
        }
        ranksConfig = YamlConfiguration.loadConfiguration(ranksFile);
    }


    private void loadRanksFromFile() {
        if (ranksFile.exists()) {
            Set<String> players = ranksConfig.getKeys(false);
            synchronized (playerRanks) { // Synchronizing access
                for (String player : players) {
                    String rank = ranksConfig.getString(player);
                    playerRanks.put(player, rank);
                }
            }
            getLogger().info("Loaded ranks from ranks.yml.");
        }
    }

    private void saveRanksToFile() {
        for (Map.Entry<String, String> entry : playerRanks.entrySet()) {
            ranksConfig.set(entry.getKey(), entry.getValue());
        }
        try {
            ranksConfig.save(ranksFile);
            getLogger().info("Saved ranks to ranks.yml.");
        } catch (IOException e) {
            getLogger().severe("Could not save ranks to ranks.yml.");
            e.printStackTrace();
        }
    }
}
