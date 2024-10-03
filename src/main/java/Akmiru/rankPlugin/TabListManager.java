package Akmiru.rankPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;

public class TabListManager {

    private final RankPlugin plugin;
    private final Map<String, String> playerRanks;
    private final Map<String, ChatColor> rankColors;
    private final Map<String, Integer> rankWeights;
    private final Scoreboard scoreboard; // Scoreboard to manage teams

    public TabListManager(RankPlugin plugin, Map<String, String> playerRanks, Map<String, ChatColor> rankColors) {
        this.plugin = plugin;
        this.playerRanks = playerRanks;
        this.rankColors = rankColors;
        this.rankWeights = new HashMap<>();
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard(); // Get the main scoreboard

        // Initialize rank weights
        rankWeights.put("Owner", 1);
        rankWeights.put("Admin", 2);
        rankWeights.put("Moderator", 3);
        rankWeights.put("Helper", 4);
        rankWeights.put("VIP", 5);
        rankWeights.put("Player", 6); // Default rank weight

        // Initialize teams for each rank
        initializeTeams();
    }

    public void startTabListUpdates() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Update all players' tab list entries
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updatePlayerTabList(player);
                }
            }
        }.runTaskTimer(plugin, 0, 20 * 5); // every 5 seconds
    }

    private void initializeTeams() {
        // Create or get teams based on rank weights
        for (Map.Entry<String, Integer> entry : rankWeights.entrySet()) {
            String rank = entry.getKey();
            int weight = entry.getValue();

            // Prefix the team name with a weight to enforce the sorting order
            String teamName = String.format("%02d_%s", weight, rank);
            Team team = scoreboard.getTeam(teamName);

            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
                team.setPrefix(rankColors.getOrDefault(rank, ChatColor.WHITE) + "[" + rank + "] " + ChatColor.RESET);
                team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
                team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.ALWAYS);
            }
        }
    }

    private void updatePlayerTabList(Player player) {
        String playerName = player.getName();
        String rank = playerRanks.getOrDefault(playerName, "Player");
        int weight = rankWeights.getOrDefault(rank, rankWeights.get("Player"));

        // Prefix the team name with weight to get the correct team
        String teamName = String.format("%02d_%s", weight, rank);
        Team team = scoreboard.getTeam(teamName);

        if (team != null) {
            // Remove player from all teams before adding to the correct one
            scoreboard.getTeams().forEach(t -> t.removeEntry(playerName));
            team.addEntry(playerName); // Add player to the correct team
        }

        // Update the player's display name in the tab list
        updatePlayerListName(player);
    }

    private void updatePlayerListName(Player player) {
        String playerName = player.getName();
        String rank = playerRanks.getOrDefault(playerName, "Player");
        ChatColor legacyColor = rankColors.getOrDefault(rank, ChatColor.WHITE);

        // Convert the Bukkit ChatColor to NamedTextColor for Adventure
        NamedTextColor rankColor = NamedTextColor.NAMES.value(legacyColor.name().toLowerCase());

        // Create the bold rank text using Adventure components
        Component boldRankComponent = Component.text("[" + rank + "] ")
                .color(rankColor)
                .decorate(TextDecoration.BOLD); // Make the rank bold

        // Create a normal text component for the player's name
        Component playerNameComponent = Component.text(playerName)
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.BOLD, false); // Ensure the player's name is not bold

        // Combine the bold rank component with the player's name
        Component finalNameComponent = boldRankComponent.append(playerNameComponent);

        // Set the player's name in the tab list
        player.playerListName(finalNameComponent);
    }


}
