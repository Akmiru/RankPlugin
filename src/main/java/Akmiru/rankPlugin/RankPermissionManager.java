package Akmiru.rankPlugin;

import java.util.ArrayList;
import java.util.List;

public class RankPermissionManager {

    // Method to get permissions based on rank
    public List<String> getPermissionsForRank(String rank) {
        List<String> permissions = new ArrayList<>();

        // Switch case to assign permissions based on rank
        switch (rank) {
            case "Owner":
                permissions.add("essentials.*");
                permissions.add("minecraft.command.*");
                break;
            case "Admin":
                permissions.add("essentials.ban");
                permissions.add("essentials.kick");
                permissions.add("essentials.tp");
                permissions.add("essentials.tp.position");
                permissions.add("essentials.tp.others");
                permissions.add("minecraft.command.gamemode");
                break;
            case "Helper":
                permissions.add("essentials.help");
                break;
            case "Moderator":
                permissions.add("essentials.kick");
                permissions.add("essentials.mute");
                permissions.add("essentials.tp");
                permissions.add("essentials.tp.others");
                break;
            default:  // Default rank is Player
                permissions.add("essentials.help");
                permissions.add("essentials.list");
                break;
        }

        return permissions;
    }
}
