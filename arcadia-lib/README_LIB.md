# Arcadia Lib — Developer Guide

Arcadia Lib is the shared foundation library for all Arcadia Minecraft mods (NeoForge 1.21.1).
It provides a complete, ready-to-use toolkit: **permissions**, **database**, **UI theme**, **hub integration**, **chat messages**, **teleportation**, **player management**, **cooldowns**, **task scheduling**, and more.

**Any new Arcadia mod only needs to depend on arcadia-lib** — no dependency on other mods is required.

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Hub Integration](#hub-integration)
3. [Dashboard Tabs](#dashboard-tabs)
4. [Permission System](#permission-system)
5. [Database System](#database-system)
6. [UI Theme (ArcadiaTheme)](#ui-theme)
7. [Chat Messages](#chat-messages)
8. [NBT Serialization](#nbt-serialization)
9. [Configuration Files](#configuration-files)
10. [Teleport System](#teleport-system)
11. [Player Management](#player-management)
12. [Cooldown System](#cooldown-system)
13. [Task Scheduler](#task-scheduler)
14. [Text Formatting](#text-formatting)
15. [Staff System](#staff-system)
16. [Item Builder](#item-builder)
17. [Sound Helper](#sound-helper)
18. [Message Helper](#message-helper)
19. [Player Utilities](#player-utilities)
20. [Economy System](#economy-system)
21. [API Reference](#api-reference)

---

## Quick Start

### 1. Add the dependency

In your `build.gradle`:
```groovy
dependencies {
    compileOnly project(':arcadia-lib')
}
```

In your `neoforge.mods.toml`:
```toml
[[dependencies.your_mod_id]]
    modId = "arcadia_lib"
    type = "required"
    versionRange = "[1.0.0,)"
    ordering = "AFTER"
    side = "BOTH"
```

### 2. Register your mod in the lib

In your main mod class constructor:
```java
public YourMod(IEventBus modBus, ModContainer container) {
    // Register your items, menus, etc.
    modBus.addListener(this::onCommonSetup);
    
    // Register your config
    container.registerConfig(ModConfig.Type.SERVER, YourConfig.SPEC, "arcadia/yourmod/config.toml");
}

private void onCommonSetup(FMLCommonSetupEvent event) {
    // 1. Register your database tables
    DatabaseManager.registerTables(new YourTableDefinition());
    
    // 2. Register your hub card (appears in /arcadia menu)
    ArcadiaModRegistry.registerCard(new ArcadiaModCard(
        "your_mod",                          // unique ID
        "🎮",                                // emoji icon
        "your_mod.hub.label",                // translation key
        "your_mod.hub.sub",                  // subtitle key
        0xCC5533,                            // accent color (hex RGB)
        5,                                   // sort order (lower = more left)
        true                                 // available
    ));
    
    // 3. Register your dashboard tab (optional)
    ArcadiaModRegistry.registerTabHandler(5, YourDashboardTab::new);
}
```

---

## Hub Integration

The Arcadia Hub (`/arcadia` command or `L` key) dynamically shows all registered mod cards.

### Register a card

```java
ArcadiaModRegistry.registerCard(new ArcadiaModCard(
    "fishing",                              // ID
    "🐟",                                   // emoji
    "arcadia_fishing.hub.label",            // label translation key
    "arcadia_fishing.hub.sub",              // subtitle translation key
    0x3498db,                               // blue accent
    5,                                      // position (0=leftmost)
    true,                                   // available
    "arcadia.hub.fishing"                   // permission node (null = always visible)
));
```

### Card with permission gating

The optional `permissionNode` field (8th parameter) allows you to gate visibility:
- `null` → card always visible
- `"arcadia.hub.fishing"` → only players with this LuckPerms node see the card

### Card click behavior

When a player clicks your card, the hub calls `ArcadiaModRegistry.openTabClient(sortOrder)`.
This sends a packet to open the dashboard at your tab index. The packet is handled by the dashboard mod — your mod doesn't need to know about it.

---

## Dashboard Tabs

If your mod needs a tab in the Arcadia Dashboard (the inventory-style GUI), implement `DashboardTabHandler`:

```java
public class FishingDashboardTab implements DashboardTabHandler {

    @Override
    public void buildTab(SimpleContainer container, ServerPlayer player) {
        // Populate slots 9-53 with your content
        container.setItem(9, new ItemStack(Items.FISHING_ROD));
    }

    @Override
    public boolean handleClick(int slotId, int button, ServerPlayer player, Runnable refreshTab) {
        if (slotId == 9) {
            // Handle click on your item
            refreshTab.run(); // Refresh the tab display
            return true;
        }
        return false;
    }

    @Override
    public ItemStack getNavBarItem(ServerPlayer player) {
        // Item shown in the center navbar slot (slot 4)
        return new ItemStack(Items.FISHING_ROD);
    }

    @Override
    public boolean handleNavBarClick(ServerPlayer player, Runnable refreshTab) {
        // Handle click on the navbar center item
        return false;
    }

    @Override
    public boolean handleInventoryShiftClick(Slot slot, ServerPlayer player, Runnable refreshTab) {
        // Handle shift-click from player inventory into this tab
        return false;
    }

    @Override
    public void onClose(ServerPlayer player) {
        // Cleanup when the dashboard closes
    }
}
```

Register it in `onCommonSetup`:
```java
ArcadiaModRegistry.registerTabHandler(5, FishingDashboardTab::new);
```

---

## Permission System

The lib includes a complete, pluggable permission system that works with or without LuckPerms.

### Check permissions from any mod

```java
import com.arcadia.lib.permissions.PermissionService;

// Basic permission check
boolean canFish = PermissionService.hasPermission(player, "arcadia.fishing.use");

// Grade checks
String grade = PermissionService.getGrade(player);          // "default", "vip", "vip+", "mvp"
boolean isVip = PermissionService.hasMinimumGrade(player, "vip");
boolean isFounder = PermissionService.isFounder(player);

// Convenience methods
boolean canAccess = PermissionService.canAccessFeature(player, "fishing");     // checks arcadia.feature.fishing
boolean canCosmetic = PermissionService.canUseCosmetic(player, "trail");       // checks arcadia.cosmetic.trail
boolean canSeeCard = PermissionService.canSeeHubCard(player, "fishing");      // checks arcadia.hub.fishing
```

### Grade hierarchy

| Grade    | Level | Permission node (configurable)   |
|----------|-------|----------------------------------|
| default  | 0     | (no node required)               |
| vip      | 1     | `arcadia.grade.vip`              |
| vip+     | 2     | `arcadia.grade.vipplus`          |
| mvp      | 3     | `arcadia.grade.mvp`              |
| founder  | —     | `arcadia.grade.founder` (cosmetic only) |

Grade nodes are configured in `config/arcadia/lib/permissions.toml`.

### Fallback behavior

- **LuckPerms installed** → real permission checks
- **LuckPerms absent** (singleplayer) → NOOP backend, grants everything
- **DebugMode enabled** → simulated grades via commands

---

## Database System

### Register your tables

Implement `TableDefinition` and register it:

```java
public class FishingTableDefinition implements TableDefinition {
    @Override
    public String moduleId() { return "arcadia-fishing"; }

    @Override
    public List<String> createTableStatements() {
        return List.of("""
            CREATE TABLE IF NOT EXISTS arcadia_fishing_catches (
                uuid         VARCHAR(36) NOT NULL,
                fish_type    VARCHAR(64) NOT NULL,
                caught_at    BIGINT NOT NULL,
                weight       INT NOT NULL,
                INDEX idx_fisher (uuid)
            )
        """);
    }
}
```

Register in `onCommonSetup`:
```java
DatabaseManager.registerTables(new FishingTableDefinition());
```

Tables are created automatically when the database pool initializes.

### Execute queries

```java
import com.arcadia.lib.data.DatabaseManager;

// Check if database is available
if (DatabaseManager.isDatabaseActive()) {
    // Synchronous (use sparingly, prefer async)
    try (Connection conn = DatabaseManager.getConnection();
         PreparedStatement ps = conn.prepareStatement("SELECT ...")) {
        // ...
    }
}

// Async (preferred for writes)
DatabaseManager.executeAsync(() -> {
    try (Connection conn = DatabaseManager.getConnection()) {
        // INSERT, UPDATE, DELETE here
    } catch (SQLException e) {
        LOGGER.error("Query failed", e);
    }
});

// Async with result
CompletableFuture<List<Fish>> future = DatabaseManager.supplyAsync(() -> {
    List<Fish> result = new ArrayList<>();
    // SELECT query here
    return result;
});
```

### Singleplayer / no database mode

When `DatabaseManager.isDebugMode()` returns `true`, there is no MySQL connection.
Your mod should use in-memory fallbacks (ConcurrentHashMap, SavedData, etc.):

```java
if (DatabaseManager.isDebugMode()) {
    // Use in-memory storage
    return inMemoryMap.getOrDefault(uuid, defaultValue);
}
// Use MySQL
try (Connection conn = DatabaseManager.getConnection()) { ... }
```

---

## UI Theme

`ArcadiaTheme` provides a complete steampunk/copper rendering toolkit.

### Color palette

| Constant        | Hex        | Usage                      |
|-----------------|------------|----------------------------|
| `COPPER`        | `#B87333`  | Primary accent             |
| `BRONZE`        | `#CD7F32`  | Secondary accent           |
| `BRASS`         | `#D4A847`  | Highlights, titles         |
| `AMBER`         | `#FFBF00`  | Active/hovered elements    |
| `PATINA`        | `#4ECCA3`  | Green accent (oxidized copper) |
| `TEXT_PRIMARY`  | `#F5E6C8`  | Main text (warm cream)     |
| `TEXT_SECONDARY` | `#B8A88A` | Secondary text             |
| `TEXT_DIM`      | `#7A6E5A`  | Hints, disabled text       |

### Rendering helpers

```java
import com.arcadia.lib.client.ArcadiaTheme;

// Draw a steampunk panel with gradient, copper borders, riveted corners
ArcadiaTheme.drawPanel(g, x, y, width, height, isHovered);
ArcadiaTheme.drawPanel(g, x, y, width, height, isHovered, accentColor);

// Draw a full container GUI (replaces chest texture)
ArcadiaTheme.drawContainerBg(g, leftPos, topPos, imageWidth, contentRows);

// Draw themed slots
ArcadiaTheme.drawSlot(g, x, y);         // single 18x18 slot
ArcadiaTheme.drawSlotGrid(g, x, y, rows); // 9-column grid

// Text with shadows
ArcadiaTheme.drawCenteredText(g, component, cx, y, color);
ArcadiaTheme.drawText(g, component, x, y, color);

// Decorated title bar with copper lines
ArcadiaTheme.drawTitleBar(g, title, cx, y, lineWidth);

// Visual effects
ArcadiaTheme.drawGlow(g, x, y, w, h, color);
ArcadiaTheme.drawSeparator(g, x, y, width, color);
ArcadiaTheme.drawBorder(g, x, y, w, h, color);

// Color utilities
int brighter = ArcadiaTheme.brighten(color, 40);
int darker   = ArcadiaTheme.darken(color, 40);
int alpha    = ArcadiaTheme.withAlpha(color, 0x88);
```

### Container screen example

```java
public class FishingScreen extends AbstractContainerScreen<FishingMenu> {
    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        ArcadiaTheme.drawContainerBg(g, this.leftPos, this.topPos, this.imageWidth, 6);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        int tx = (this.imageWidth - this.font.width(this.title)) / 2;
        g.drawString(this.font, this.title, tx, 6, ArcadiaTheme.BRASS, false);
    }
}
```

---

## Chat Messages

`ArcadiaMessages` provides consistent, themed chat messages with the `⚙ Arcadia ▸` prefix.

```java
import com.arcadia.lib.ArcadiaMessages;

player.sendSystemMessage(ArcadiaMessages.success("Fish caught! +50 XP"));
player.sendSystemMessage(ArcadiaMessages.error("You need bait to fish here."));
player.sendSystemMessage(ArcadiaMessages.warning("Your rod is about to break!"));
player.sendSystemMessage(ArcadiaMessages.info("Fishing is available in this biome."));
```

Result: `⚙ Arcadia ▸ Fish caught! +50 XP` (with gold prefix + green body)

---

## NBT Serialization

`NbtSerializer` provides Base64-compressed NBT serialization for database storage:

```java
import com.arcadia.lib.data.NbtSerializer;

// ItemStack ↔ Base64 string
String data = NbtSerializer.serializeStack(itemStack, server.registryAccess());
ItemStack item = NbtSerializer.deserializeStack(data, server.registryAccess());

// CompoundTag ↔ Base64 string
String nbt = NbtSerializer.serializeTag(compoundTag);
CompoundTag tag = NbtSerializer.deserializeTag(nbt);
```

---

## Configuration Files

All Arcadia configs follow the convention: `config/arcadia/<module>/<file>.toml`

| File                              | Module   | Content                              |
|-----------------------------------|----------|--------------------------------------|
| `arcadia/lib/database.toml`       | lib      | MySQL connection (host, port, etc.)  |
| `arcadia/lib/permissions.toml`    | lib      | Grade permission nodes               |
| `arcadia/lib/staff.toml`          | lib      | Staff role permission nodes          |
| `arcadia/lib/economy.toml`        | lib      | Economy provider + currency config   |
| `arcadia/prestige/prestige.toml`  | prestige | Server ID, prestige settings         |
| `arcadia/pets/pets.toml`          | pets     | Pet pool weights, rarities           |
| `arcadia/pets/hud.json`           | pets     | HUD position + toggle settings       |
| `arcadia/ah/ah.toml`             | ah       | Listing duration, max listings       |

Your mod should follow the same pattern:
```java
container.registerConfig(ModConfig.Type.SERVER, YourConfig.SPEC, "arcadia/yourmod/config.toml");
```

---

## Teleport System

`TeleportManager` provides safe teleportation with warmup, cooldown, and movement cancellation.

```java
import com.arcadia.lib.teleport.TeleportManager;

// Instant teleport
TeleportManager.teleportNow(player, new Vec3(100, 64, 200));

// Teleport with 3-second warmup + 30-second cooldown
TeleportManager.teleportWithWarmup(player, targetPos, level, 60, 30_000);

// Teleport with named action (for separate cooldown tracking)
TeleportManager.teleportWithWarmup(player, pos, level, 40, 10_000, "home");

// Check cooldown
long remaining = TeleportManager.getCooldownRemaining(uuid, "home");

// Cancel warmup
TeleportManager.cancelWarmup(uuid);
```

Features:
- **Warmup delay** — configurable in ticks (movement cancels)
- **Per-action cooldowns** — separate cooldowns for "home", "spawn", etc.
- **Movement detection** — cancels if player moves > 0.3 blocks
- **Cross-dimension** — supports different ServerLevel targets
- **Sound effects** — enderman teleport sound on arrival
- **Auto-cleanup** — warmups/cooldowns cleared on disconnect

---

## Player Management

`PlayerManager` tracks online players and provides join/quit callbacks.

```java
import com.arcadia.lib.player.PlayerManager;

// Register callbacks (call during mod setup)
PlayerManager.onJoin(player -> {
    LOGGER.info("{} joined!", player.getName().getString());
    loadPlayerData(player);
});

PlayerManager.onQuit(player -> {
    savePlayerData(player);
});

// Query online players
ServerPlayer player = PlayerManager.getPlayer(uuid);
boolean online = PlayerManager.isOnline(uuid);
int count = PlayerManager.getOnlineCount();
Collection<ServerPlayer> all = PlayerManager.getOnlinePlayers();
```

Features:
- **Centralized join/quit hooks** — no need to subscribe to raw events
- **Safe callbacks** — one failing callback doesn't break others
- **Auto-cleanup** — clears teleport warmups and cooldowns on quit
- **Online player cache** — O(1) UUID → ServerPlayer lookup

---

## Cooldown System

`CooldownManager` provides generic per-player cooldowns for any action.

```java
import com.arcadia.lib.player.CooldownManager;

// Check and set cooldown
if (CooldownManager.isReady(uuid, "fishing.cast")) {
    CooldownManager.set(uuid, "fishing.cast", 5000); // 5 seconds
    performFishing(player);
} else {
    String remaining = CooldownManager.getRemainingFormatted(uuid, "fishing.cast");
    player.sendSystemMessage(ArcadiaMessages.error("On cooldown: " + remaining));
}

// Get raw remaining time
long ms = CooldownManager.getRemaining(uuid, "fishing.cast");

// Clear specific cooldown
CooldownManager.clear(uuid, "fishing.cast");
```

Features:
- **Any action ID** — "fishing.cast", "teleport.home", "shop.buy", etc.
- **Millisecond precision** — accurate cooldown tracking
- **Formatted output** — `getRemainingFormatted()` returns "2m 30s"
- **Auto-cleanup** — cleared on player disconnect

---

## Task Scheduler

`SchedulerService` replaces raw tick counters with a centralized task system.

```java
import com.arcadia.lib.scheduler.SchedulerService;

// Run once after 3 seconds (60 ticks)
SchedulerService.delayed(60, () -> player.sendSystemMessage(...));

// Run every 1 second forever
int taskId = SchedulerService.repeating(20, () -> checkPlayerStatus());

// Run every 5 seconds with initial 2-second delay
int id = SchedulerService.repeatingDelayed(40, 100, () -> syncData());

// Schedule from async thread back to main thread
DatabaseManager.executeAsync(() -> {
    List<Data> results = fetchFromDB();
    SchedulerService.runNextTick(() -> deliverResults(player, results));
});

// Cancel a task
SchedulerService.cancel(taskId);

// Get current server tick
long tick = SchedulerService.getCurrentTick();
```

Features:
- **Main thread execution** — all tasks run during ServerTickEvent
- **One-shot or repeating** — delayed() vs repeating()
- **Async→main bridge** — runNextTick() for safe main-thread scheduling from async callbacks
- **Error isolation** — one failing task doesn't crash the server
- **Auto-cleanup** — all tasks cancelled on server shutdown

---

## Text Formatting

`TextFormatter` provides advanced text formatting for chat and UI.

```java
import com.arcadia.lib.text.TextFormatter;

// Placeholder replacement
Component msg = TextFormatter.format(
    "Welcome {player}! You have {coins} coins.",
    Map.of("player", name, "coins", "1,500")
);

// Rich text builder
Component rich = TextFormatter.rich()
    .gold().bold().text("⚙ Arcadia")
    .gray().text(" ▸ ")
    .green().text("Fish caught! ")
    .white().text("+50 XP")
    .build();

// Number formatting
TextFormatter.formatNumber(1234567);   // "1,234,567"
TextFormatter.formatTicks(600);         // "30s"
TextFormatter.formatTicks(2400);        // "2m 0s"
TextFormatter.formatMs(150000);         // "2m 30s"
TextFormatter.formatPercent(0.756f);    // "76%"
```

---

## Staff System

Complete admin/moderator system with roles, staff chat, and moderation actions.

### Roles

| Role | Level | Permission Node | Color |
|------|-------|-----------------|-------|
| NONE | 0 | — | Gray |
| HELPER | 1 | `arcadia.staff.helper` | Aqua |
| MOD | 2 | `arcadia.staff.mod` | Green |
| ADMIN | 3 | `arcadia.staff.admin` | Red |

Configured in `config/arcadia/lib/staff.toml`.

### Check staff roles

```java
import com.arcadia.lib.staff.StaffService;
import com.arcadia.lib.staff.StaffRole;

StaffRole role = StaffService.getRole(player);
boolean isMod = role.atLeast(StaffRole.MOD);
boolean isStaff = StaffService.isStaff(player);
List<ServerPlayer> staff = StaffService.getStaffOnline();

// Command guard (sends error if insufficient role)
if (!StaffService.requireRole(source, StaffRole.MOD)) return 0;
```

### Staff chat

```java
import com.arcadia.lib.staff.StaffChatService;

StaffChatService.broadcast(sender, "Watch player XYZ");
StaffChatService.broadcastAlert("Player XYZ was banned");
StaffChatService.toggle(uuid); // toggle staff chat mode
```

### Moderation actions

```java
import com.arcadia.lib.staff.StaffActions;

StaffActions.kick(target, executor, "Spam");
StaffActions.ban(target, executor, "Cheating", 60 * 60_000); // 1 hour
StaffActions.mute(uuid, executor, "Toxicity", 30 * 60_000);  // 30 min
StaffActions.unmute(uuid, executor);
boolean muted = StaffActions.isMuted(uuid);
```

### Commands

| Command | Role | Description |
|---------|------|-------------|
| `/staff` | — | Show help |
| `/staff chat <msg>` | HELPER | Send to staff chat |
| `/staff toggle` | HELPER | Toggle staff chat mode |
| `/staff list` | HELPER | List online staff |
| `/staff kick <player> [reason]` | MOD | Kick a player |
| `/staff ban <player> <minutes> [reason]` | MOD | Ban a player |
| `/staff mute <player> <minutes> [reason]` | MOD | Mute a player |
| `/staff unmute <player>` | MOD | Unmute a player |

---

## Item Builder

Fluent builder for constructing styled ItemStacks (menus, rewards, GUIs).

```java
import com.arcadia.lib.item.ItemBuilder;

ItemStack icon = ItemBuilder.of(Items.DIAMOND_SWORD)
    .name(Component.literal("Excalibur").withStyle(ChatFormatting.GOLD))
    .addLore("A legendary blade")
    .addLore(Component.literal("Damage: +10").withStyle(ChatFormatting.RED))
    .enchanted()
    .count(1)
    .build();
```

---

## Sound Helper

Simplified server-side sound playback (replaces 7-argument calls).

```java
import com.arcadia.lib.util.SoundHelper;

SoundHelper.playAt(player, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP);
SoundHelper.playAt(player, SoundEvents.BLOCK_ANVIL_USE, 0.5f, 1.2f);
SoundHelper.playAt(level, position, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

// Predefined convenience sounds
SoundHelper.success(player);  // level-up sound
SoundHelper.error(player);    // villager "no" sound
SoundHelper.reward(player);   // XP orb sound
```

---

## Message Helper

Action bar, title, and subtitle messaging.

```java
import com.arcadia.lib.text.MessageHelper;

// Action bar (above hotbar)
MessageHelper.sendActionBar(player, "Teleporting in 3...");

// Full title + subtitle with custom timings
MessageHelper.sendTitle(player,
    Component.literal("VICTORY!").withStyle(ChatFormatting.GOLD),
    Component.literal("You won the game"),
    10, 60, 20); // fadeIn, stay, fadeOut (ticks)

// Quick title with defaults
MessageHelper.sendTitle(player, Component.literal("Welcome!"));
```

---

## Player Utilities

Common player operations that eliminate copy-paste patterns.

```java
import com.arcadia.lib.util.PlayerUtils;

// Give item or drop at feet if inventory full (used 8+ times in codebase)
PlayerUtils.giveOrDrop(player, itemStack);
PlayerUtils.giveOrDrop(player, listOfStacks);

// Find online players
ServerPlayer p = PlayerUtils.findOnline(uuid);
ServerPlayer p2 = PlayerUtils.findOnline("Steve");

// Inventory checks
boolean hasSpace = PlayerUtils.hasInventorySpace(player);
int diamonds = PlayerUtils.countItem(player, Items.DIAMOND);
```

---

## Economy System

Centralized, configurable economy for all Arcadia mods. Supports multiple currency providers out of the box.

### Configuration (`config/arcadia/lib/economy.toml`)

```toml
[economy]
# Options: 'numismatics', 'items', 'none'
provider = "numismatics"

# Display name used in chat messages
currency_name = "Coins"

# Short symbol for compact displays
currency_symbol = "¤"

# When provider = 'items', the item used as currency
item_currency_id = "minecraft:emerald"
```

### Supported Providers

| Provider | Mod Required | How it Works |
|----------|-------------|--------------|
| `numismatics` | Create: Numismatics | Bank account API (spurs/copper/silver/gold). Auto-detects if installed, falls back to `none` if absent. |
| `items` | None (vanilla) | Any Minecraft item as currency. Counts items in inventory, removes on purchase, gives on sale. Configurable via `item_currency_id`. |
| `none` | None | Free mode — no currency deducted. Good for testing or free servers. |

### Item Currency Examples

```toml
# Use emeralds as currency
item_currency_id = "minecraft:emerald"

# Use diamonds
item_currency_id = "minecraft:diamond"

# Use gold ingots
item_currency_id = "minecraft:gold_ingot"

# Use a modded item
item_currency_id = "create:experience_nugget"
```

### Usage from any mod

```java
import com.arcadia.lib.economy.EconomyService;

// Check balance
long balance = EconomyService.getBalance(player);

// Deduct currency (returns false if insufficient funds)
if (EconomyService.deduct(player, 500)) {
    // purchase successful
} else {
    player.sendSystemMessage(ArcadiaMessages.error("Not enough funds!"));
}

// Add currency
EconomyService.add(player, 1000);

// Format for display (adapts to active provider)
String price = EconomyService.formatPrice(1500);
// Numismatics: "15s"
// Items: "1500 Coins"
// None: "Free"

// Check provider info
boolean available = EconomyService.isAvailable();
String name = EconomyService.getProviderName(); // "Create: Numismatics"
```

### Creating a Custom Backend

Implement `EconomyBackend` to add support for other economy mods:

```java
public class MyEconomyBackend implements EconomyBackend {
    @Override public long getBalance(ServerPlayer player) { ... }
    @Override public boolean deduct(ServerPlayer player, long amount) { ... }
    @Override public void add(ServerPlayer player, long amount) { ... }
    @Override public boolean isAvailable() { ... }
    @Override public String getName() { return "My Economy Mod"; }
    @Override public String formatPrice(long amount) { return amount + " coins"; }
}
```

Register it by modifying `EconomyService.init()` to recognize your provider name in the config.

---

## API Reference

### Core Classes

| Class                      | Package                          | Purpose                                    |
|----------------------------|----------------------------------|--------------------------------------------|
| `ArcadiaModRegistry`       | `com.arcadia.lib`                | Module card & tab registration             |
| `ArcadiaMessages`          | `com.arcadia.lib`                | Themed chat messages                       |
| `ArcadiaCommands`          | `com.arcadia.lib`                | /arcadia command                           |
| `DebugMode`                | `com.arcadia.lib`                | Debug mode toggle                          |
| `ServerContext`            | `com.arcadia.lib`                | Server ID for cross-server features        |

### Client Classes

| Class                      | Package                          | Purpose                                    |
|----------------------------|----------------------------------|--------------------------------------------|
| `ArcadiaTheme`             | `com.arcadia.lib.client`         | Steampunk rendering toolkit                |
| `ArcadiaHubScreen`         | `com.arcadia.lib.client`         | Hub screen (auto-detects modules)          |
| `ArcadiaModCard`           | `com.arcadia.lib.client`         | Hub card descriptor                        |

### Data Classes

| Class                      | Package                          | Purpose                                    |
|----------------------------|----------------------------------|--------------------------------------------|
| `DatabaseManager`          | `com.arcadia.lib.data`           | MySQL pool + async executor                |
| `DatabaseConfig`           | `com.arcadia.lib.config`         | DB connection config                       |
| `TableDefinition`          | `com.arcadia.lib.data`           | Interface for mod table schemas            |
| `NbtSerializer`            | `com.arcadia.lib.data`           | ItemStack/NBT ↔ Base64                     |
| `PlayerDataHandler`        | `com.arcadia.lib.data`           | Player data CRUD                           |
| `PlayerDataSavedData`      | `com.arcadia.lib.data`           | Singleplayer persistence                   |

### Player & Gameplay Classes

| Class                      | Package                          | Purpose                                    |
|----------------------------|----------------------------------|--------------------------------------------|
| `PlayerManager`            | `com.arcadia.lib.player`         | Online player tracking + join/quit hooks   |
| `CooldownManager`          | `com.arcadia.lib.player`         | Per-player action cooldowns                |
| `TeleportManager`          | `com.arcadia.lib.teleport`       | Safe teleport with warmup/cooldown         |
| `SchedulerService`         | `com.arcadia.lib.scheduler`      | Tick-based task scheduling                 |
| `TextFormatter`            | `com.arcadia.lib.text`           | Placeholders, rich text, number formatting |

### Economy Classes

| Class                      | Package                          | Purpose                                    |
|----------------------------|----------------------------------|--------------------------------------------|
| `EconomyService`           | `com.arcadia.lib.economy`        | Public API (getBalance, deduct, add, formatPrice) |
| `EconomyBackend`           | `com.arcadia.lib.economy`        | Pluggable backend interface + NONE fallback |
| `NumismaticsBackend`       | `com.arcadia.lib.economy`        | Create: Numismatics (pure reflection)       |
| `ItemCurrencyBackend`      | `com.arcadia.lib.economy`        | Vanilla item currency (configurable)        |
| `EconomyConfig`            | `com.arcadia.lib.economy`        | Provider selection + currency config        |

### Staff Classes

| Class                      | Package                          | Purpose                                    |
|----------------------------|----------------------------------|--------------------------------------------|
| `StaffRole`                | `com.arcadia.lib.staff`          | Role enum (NONE/HELPER/MOD/ADMIN)          |
| `StaffService`             | `com.arcadia.lib.staff`          | Staff role checks + command guard           |
| `StaffChatService`         | `com.arcadia.lib.staff`          | Staff-only chat channel                    |
| `StaffActions`             | `com.arcadia.lib.staff`          | Kick/ban/mute wrappers with logging         |
| `StaffCommands`            | `com.arcadia.lib.staff`          | /staff command tree                        |
| `StaffEventHandler`        | `com.arcadia.lib.staff`          | Mute enforcement + chat toggle              |
| `StaffConfig`              | `com.arcadia.lib.staff`          | Staff permission nodes config               |

### Utility Classes

| Class                      | Package                          | Purpose                                    |
|----------------------------|----------------------------------|--------------------------------------------|
| `ItemBuilder`              | `com.arcadia.lib.item`           | Fluent ItemStack builder                   |
| `SoundHelper`              | `com.arcadia.lib.util`           | Simplified sound playback                  |
| `MessageHelper`            | `com.arcadia.lib.text`           | Action bar / title / subtitle sending       |
| `PlayerUtils`              | `com.arcadia.lib.util`           | giveOrDrop, findOnline, countItem           |

### Permission Classes

| Class                      | Package                          | Purpose                                    |
|----------------------------|----------------------------------|--------------------------------------------|
| `PermissionService`        | `com.arcadia.lib.permissions`    | Public API (hasPermission, getGrade, etc.) |
| `PermissionBackend`        | `com.arcadia.lib.permissions`    | Pluggable backend interface                |
| `LuckPermsBackend`         | `com.arcadia.lib.permissions`    | LuckPerms implementation                   |
| `PermissionConfig`         | `com.arcadia.lib.permissions`    | Grade node config                          |

### Dashboard Interface

| Class                      | Package                          | Purpose                                    |
|----------------------------|----------------------------------|--------------------------------------------|
| `DashboardTabHandler`      | `com.arcadia.lib.dashboard`      | Pluggable tab interface                    |

---

## Complete Integration Example

Here's a minimal complete mod that integrates with Arcadia Lib:

```java
@Mod("arcadia_fishing")
public class ArcadiaFishing {
    public static final String MOD_ID = "arcadia_fishing";

    public ArcadiaFishing(IEventBus modBus, ModContainer container) {
        modBus.addListener(this::onCommonSetup);
        container.registerConfig(ModConfig.Type.SERVER, FishingConfig.SPEC, "arcadia/fishing/config.toml");
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        // Database tables
        DatabaseManager.registerTables(new FishingTableDefinition());

        // Hub card
        ArcadiaModRegistry.registerCard(new ArcadiaModCard(
            "fishing", "🐟", "arcadia_fishing.hub.label", "arcadia_fishing.hub.sub",
            0x3498db, 5, true, "arcadia.hub.fishing"
        ));

        // Dashboard tab
        ArcadiaModRegistry.registerTabHandler(5, FishingDashboardTab::new);
    }
}
```

---

**Author:** vyrriox
**License:** All Rights Reserved
