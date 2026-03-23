package com.arcadia.prestige.server;

import com.arcadia.prestige.config.PrestigeConfig;
import com.mojang.logging.LogUtils;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.node.NodeAddEvent;
import net.luckperms.api.event.node.NodeRemoveEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.NodeType;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scans LuckPerms groups to determine which tier (VIP / VIP+ / MVP / Founder / null)
 * grants each cosmetic effect.
 *
 * <p>The result is cached in {@link #tierLabels} and rebuilt automatically whenever
 * a {@code arcadia.cosmetic.*} permission is added or removed from any LP group.
 * Call {@link #rescan()} manually (or via {@code /arcadia cosmetics rescan}) if you
 * need an immediate refresh.</p>
 *
 * <p>A cosmetic with no tier label is considered "individually sold" — access is still
 * gated by the {@code arcadia.cosmetic.<id>} LP node, but no rank is shown in the UI.</p>
 */
public final class CosmeticPermissionScanner {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** All known cosmetic IDs. Add new ones here when creating new effects. */
    public static final List<String> ALL_COSMETICS = List.of(
            // Static
            "orbit", "aura", "wings", "storm", "platform",
            "snow", "void", "dragon", "helix", "meteor",
            "comet", "pulsar", "binary", "nova", "galaxy",
            // Movement
            "trail", "hearts", "enchant", "flame", "stars",
            "bubble", "ghost", "sakura", "shockwave", "rainbow"
    );

    /** cosmetic_id → tier display label ("VIP", "VIP+", "MVP", "Founder").
     *  Absent = no rank grants it directly (individually sold or unconfigured). */
    private static volatile Map<String, String> tierLabels = Map.of();

    private CosmeticPermissionScanner() {}

    // -------------------------------------------------------------------------

    /**
     * Initialises the scanner: runs the first scan and subscribes to LP group
     * permission change events so the cache stays up to date automatically.
     */
    public static void init() {
        rescan();
        try {
            LuckPerms lp = LuckPermsProvider.get();
            lp.getEventBus().subscribe(NodeAddEvent.class, e -> {
                if (e.getTarget() instanceof Group
                        && e.getNode().getKey().startsWith("arcadia.cosmetic.")) {
                    rescan();
                }
            });
            lp.getEventBus().subscribe(NodeRemoveEvent.class, e -> {
                if (e.getTarget() instanceof Group
                        && e.getNode().getKey().startsWith("arcadia.cosmetic.")) {
                    rescan();
                }
            });
        } catch (Exception e) {
            LOGGER.warn("[CosmeticPermissionScanner] Could not subscribe to LP events: {}", e.getMessage());
        }
    }

    /**
     * Re-scans all loaded LP groups and rebuilds the tier label cache.
     * Thread-safe: the map is replaced atomically with an immutable copy.
     */
    public static void rescan() {
        try {
            LuckPerms lp = LuckPermsProvider.get();

            // Ordered from lowest to highest rank — first match wins for each cosmetic.
            String[][] tiers = {
                    { PrestigeConfig.GRADE_PERM_VIP,      "VIP"     },
                    { PrestigeConfig.GRADE_PERM_VIP_PLUS, "VIP+"    },
                    { PrestigeConfig.GRADE_PERM_MVP,      "MVP"     },
                    { PrestigeConfig.GRADE_PERM_FOUNDER,  "Founder" },
            };

            // cosmetic_id → index of the lowest tier that directly grants it
            Map<String, Integer> tierIndex = new HashMap<>();

            for (int t = 0; t < tiers.length; t++) {
                String gradeNode = tiers[t][0];
                if (gradeNode == null || gradeNode.isBlank()) continue;
                final int tierIdx = t;

                for (Group group : lp.getGroupManager().getLoadedGroups()) {
                    // Only consider groups that ARE this tier (have the grade node directly set)
                    boolean isThisTier = group.getNodes(NodeType.PERMISSION).stream()
                            .anyMatch(n -> n.getKey().equals(gradeNode) && n.getValue());
                    if (!isThisTier) continue;

                    // Check which cosmetics this group directly grants
                    for (String cosId : ALL_COSMETICS) {
                        boolean hasCos = group.getNodes(NodeType.PERMISSION).stream()
                                .anyMatch(n -> n.getKey().equals("arcadia.cosmetic." + cosId) && n.getValue());
                        if (hasCos) tierIndex.merge(cosId, tierIdx, Math::min);
                    }
                }
            }

            // Convert indexes to labels
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, Integer> e : tierIndex.entrySet()) {
                result.put(e.getKey(), tiers[e.getValue()][1]);
            }
            tierLabels = Map.copyOf(result);
            LOGGER.info("[CosmeticPermissionScanner] Scanned — {} tier assignments found.", result.size());

        } catch (Exception e) {
            LOGGER.warn("[CosmeticPermissionScanner] Scan failed: {}", e.getMessage());
        }
    }

    /**
     * Returns the display tier label for a cosmetic (e.g. {@code "VIP"}, {@code "VIP+"}),
     * or {@code null} if no rank directly grants it (individually sold / unconfigured).
     */
    public static String getTierLabel(String cosmeticId) {
        return tierLabels.get(cosmeticId);
    }
}
