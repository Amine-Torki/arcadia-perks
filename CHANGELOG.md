# Changelog

All notable changes to Arcadia Perks are documented here.

---

## [1.2.0] - 2026-04-11

### Added

- **AH drag-and-drop selling** — Shift-click any item from inventory while on AH tab to sell it. Opens a steampunk-themed price input screen with item preview. New packets C2SAhSell and S2COpenAhSell handle the flow.
- **Arcadia Hub in lib** — Hub screen moved from prestige to lib as ArcadiaHubScreen. Dynamically detects installed modules and adapts card count. Any future mod can register cards via ArcadiaModRegistry.
- **Permission system** — Complete PermissionService API in lib with pluggable PermissionBackend interface. LuckPermsBackend with graceful fallback when LP is absent. PermissionConfig for configurable grade nodes.
- **Staff system** — StaffRole enum (NONE/HELPER/MOD/ADMIN), StaffService for role checks, StaffChatService for staff-only chat, StaffActions for kick/ban/mute with logging, StaffCommands (/staff command tree), StaffEventHandler for mute enforcement.
- **Database table registration** — TableDefinition interface in lib. Each mod declares its own tables via DatabaseManager.registerTables(). Removed hardcoded SQL from DatabaseManager.
- **NbtSerializer utility** — Centralized Base64 compressed NBT serialization for ItemStack and CompoundTag in lib. Eliminates duplicate code across mods.
- **Teleport system** — TeleportManager in lib with warmup delays, per-action cooldowns, movement cancellation, cross-dimension support, and sound effects.
- **Player management** — PlayerManager in lib with online player cache, join/quit callback registration, and auto-cleanup of subsystems on disconnect.
- **Cooldown system** — Generic CooldownManager in lib for per-player per-action cooldowns with millisecond precision and formatted output.
- **Task scheduler** — SchedulerService in lib for tick-based delayed/repeating tasks with error isolation and async→main thread bridge via runNextTick().
- **Text formatting** — TextFormatter in lib with placeholder replacement, fluent rich text builder, and number/time formatting helpers.
- **Message helpers** — MessageHelper in lib for action bar, title, and subtitle sending. SoundHelper for simplified server-side sound playback.
- **Item builder** — Fluent ItemBuilder in lib for constructing styled ItemStacks with name/lore/enchant glint.
- **Player utilities** — PlayerUtils with giveOrDrop (eliminates 8+ duplicates), findOnline by UUID/name, hasInventorySpace, countItem.
- **ArcadiaMessages** — Themed chat message helper with "⚙ Arcadia ▸" prefix. success(), error(), warning(), info() methods for consistent styling.
- **Steampunk UI theme** — ArcadiaTheme in lib with copper/bronze/brass palette, drawPanel, drawContainerBg, drawSlotGrid, drawTitleBar, drawGlow, drawSeparator, and color utilities.
- **Keybind L** — Opens the Arcadia Hub directly. Registered via ClientTickEvent to avoid singleplayer freeze.
- **Database enabled toggle** — Config option in arcadia-database.toml to enable/disable MySQL. Auto-detects singleplayer and skips DB connection.
- **DB-backed pet storage** — PetCollectionDatabase and PetHistoryDatabase for cross-server pet persistence via MySQL.
- **Pet HUD redesign** — Steampunk-themed portrait with copper borders, brass name, hunger strip, HP bar with copper micro-borders, cached HP string for performance.
- **HUD settings redesign** — Full-screen drag-to-reposition overlay with themed toggle buttons, copper borders, and action buttons.
- **Developer guide** — Comprehensive README_LIB.md (700+ lines) documenting all 39 lib classes with examples.

### Fixed

- **Daily reward re-claim on solo restart** — Created PlayerDataSavedData for world-file persistence. Claim timestamps now survive singleplayer restarts.
- **Daily reward cooldown bypass** — Separated DebugMode.ENABLED (gameplay debug) from DatabaseManager.isDebugMode() (storage mode). Cooldown only skipped in true debug mode.
- **Singleplayer crash** — CosmeticPermissionScanner caught Exception instead of Throwable for LuckPerms NoClassDefFoundError. Fixed.
- **Keybind freeze in singleplayer** — Changed PetKeyHandler and HubKeyHandler from PlayerTickEvent to ClientTickEvent. PlayerTickEvent fires on both threads in singleplayer causing freeze.
- **Race condition double-buy** — Added pendingSales Set to AuctionManager. Cache refresh filters out pending listings to prevent double-purchase.
- **Async inventory corruption** — drainMailbox now fetches data async then schedules inventory mutations via server.execute() on main thread.
- **AH cancel packet spam** — AhSearchScreen.cancel() no longer sends redundant C2SAhSearch packet.
- **Pet items visible in non-Pets tabs** — FilteredInventorySlot hides all arcadia_pets namespace items when not on Pets tab using registry namespace check.
- **Pet navbar in wrong tabs** — Slot 4 now delegates to active tab handler instead of always showing petsTab.getNavBarItem().
- **Toggle text overflow** — First-person toggle replaced with compact 12x12 icon button with tooltip on hover.
- **SQL on main thread** — DailyRewardHandler milestone claims cached in-memory with async DB writes. fetchLeaderboard bounded with 30-day rolling window.
- **Pet history N+1 connections** — PetHistoryDatabase.log() merged INSERT+TRIM into single async task with single connection.
- **Memory leaks** — playerSearch/lastSearchTime converted to ConcurrentHashMap. Dead bridge classes deleted.

### Changed

- **Commands renamed** — /pets → /arcadia_pets, /ah → /arcadia_ah, /prestige → /arcadia_prestige. Added /arcadia root command in lib.
- **Complete mod decoupling** — Zero cross-module Java imports. Each mod depends only on arcadia-lib. All communication via ArcadiaModRegistry callbacks (server actions, client actions, reward items).
- **Translation ownership** — 94 keys renamed from arcadia_prestige.* to arcadia_pets.* in pets module. Each mod has complete en_us.json and fr_fr.json with all its own translations.
- **Config paths reorganized** — All configs moved to arcadia/<module>/ subdirectories.
- **Container GUI** — All container screens (Dashboard, Fusion, PetHistory, AhLeaderboard) now use ArcadiaTheme.drawContainerBg instead of vanilla chest texture.
- **Screen registrations split** — Each mod registers its own screens/keybinds via its own ClientModEvents class.
- **Build dependencies cleaned** — Removed compileOnly cross-deps from all build.gradle files. Only arcadia-lib is required.
- **Log messages** — All DatabaseManager logs changed from "ArcadiaDashboard" to "ArcadiaLib".

### Performance

- **HP string caching** — PetHudRenderer caches the HP display string, rebuilds only when values change. Eliminates allocation per render frame.
- **Leaderboard query bounded** — fetchLeaderboard adds WHERE sold_at > ? for 30-day window. Prevents full table scan on growing sales_log.
- **Single connection per pet log** — PetHistoryDatabase uses one connection for INSERT + COUNT + DELETE instead of two separate async tasks.

---

### Ajouts

- **Vente drag-and-drop AH** — Shift-clic sur un item de l'inventaire dans le tab AH pour le vendre. Écran de saisie de prix thémé steampunk avec aperçu de l'item.
- **Arcadia Hub dans le lib** — L'écran hub déplacé de prestige vers lib. Détecte dynamiquement les modules installés. N'importe quel futur mod peut s'enregistrer.
- **Système de permissions** — API PermissionService complète dans le lib avec backend pluggable (LuckPerms ou fallback). Configuration des nœuds de grade.
- **Système staff** — Rôles (NONE/HELPER/MOD/ADMIN), chat staff, actions de modération (kick/ban/mute) avec logging, commandes /staff.
- **Enregistrement dynamique de tables DB** — Interface TableDefinition. Chaque mod déclare ses tables via le lib. SQL hardcodé supprimé.
- **Utilitaire NbtSerializer** — Sérialisation NBT Base64 centralisée pour ItemStack et CompoundTag.
- **Système de téléportation** — TeleportManager avec warmup, cooldown, annulation au mouvement, cross-dimension.
- **Gestion des joueurs** — PlayerManager avec cache en ligne, callbacks join/quit, nettoyage automatique.
- **Système de cooldowns** — CooldownManager générique par joueur/action avec précision milliseconde.
- **Planificateur de tâches** — SchedulerService tick-based avec tasks delayed/repeating et pont async→main thread.
- **Formatage de texte** — TextFormatter avec placeholders, rich text builder, formatage de nombres/temps.
- **Helpers de messages** — MessageHelper pour action bar/titre/sous-titre. SoundHelper pour sons simplifiés.
- **Item builder** — ItemBuilder fluent pour construire des ItemStack stylisés.
- **Utilitaires joueur** — PlayerUtils avec giveOrDrop, findOnline, hasInventorySpace, countItem.
- **ArcadiaMessages** — Messages chat thémés avec préfixe "⚙ Arcadia ▸".
- **Thème UI steampunk** — ArcadiaTheme avec palette cuivre/bronze/brass et helpers de rendu complets.
- **Touche L** — Ouvre le Hub Arcadia. Enregistré via ClientTickEvent.
- **Toggle base de données** — Option enabled dans la config DB. Détection automatique du singleplayer.
- **Stockage pets en DB** — PetCollectionDatabase et PetHistoryDatabase pour persistance cross-serveur.
- **Refonte HUD pet** — Portrait steampunk avec bordures cuivre, nom brass, barres avec micro-bordures.
- **Refonte paramètres HUD** — Overlay drag-to-reposition avec boutons thémés.
- **Guide développeur** — README_LIB.md complet (700+ lignes) documentant les 39 classes du lib.

### Correctifs

- **Re-claim daily en solo** — PlayerDataSavedData créé pour persistance. Les timestamps survivent aux redémarrages.
- **Bypass cooldown daily** — Séparation DebugMode.ENABLED (gameplay) de DatabaseManager.isDebugMode() (stockage).
- **Crash singleplayer** — CosmeticPermissionScanner : catch Throwable au lieu de Exception pour LuckPerms.
- **Freeze keybind en solo** — PlayerTickEvent → ClientTickEvent pour les deux keybinds.
- **Race condition double-achat** — Set pendingSales empêche le cache refresh de restaurer les listings vendus.
- **Corruption inventaire async** — drainMailbox : fetch async puis server.execute() pour les mutations d'inventaire.
- **Spam packet AH cancel** — cancel() ne renvoie plus de packet inutile.
- **Items pets visibles dans les autres tabs** — FilteredInventorySlot cache les items arcadia_pets par namespace.
- **Pet navbar dans les mauvais tabs** — Slot 4 délègue au tab handler actif.
- **Overflow texte toggle** — Remplacé par un bouton icône 12x12 compact avec tooltip.
- **SQL sur main thread** — Claims en cache mémoire + writes async. Leaderboard borné à 30 jours.
- **N+1 connexions pet history** — INSERT+TRIM en un seul task async.
- **Fuites mémoire** — HashMap → ConcurrentHashMap. Classes bridge mortes supprimées.

### Modifications

- **Commandes renommées** — /pets → /arcadia_pets, /ah → /arcadia_ah, /prestige → /arcadia_prestige. Ajout /arcadia.
- **Découplage complet** — Zéro imports croisés. Chaque mod dépend uniquement du lib. Communication via callbacks ArcadiaModRegistry.
- **Propriété des traductions** — 94 clés renommées arcadia_prestige.* → arcadia_pets.* dans le mod pets. Chaque mod a ses propres fichiers lang complets.
- **Chemins de config** — Réorganisés dans arcadia/<module>/.
- **GUI container** — Tous les écrans container utilisent ArcadiaTheme.drawContainerBg au lieu de la texture coffre vanilla.
- **Enregistrement des screens** — Chaque mod enregistre ses propres screens via sa propre classe ClientModEvents.
- **Dépendances build nettoyées** — Suppression des compileOnly croisés. Seul arcadia-lib requis.

### Performance

- **Cache string HP** — PetHudRenderer cache la string HP, reconstruit seulement quand les valeurs changent.
- **Requête leaderboard bornée** — Fenêtre de 30 jours pour limiter le scan de la table sales_log.
- **Connexion unique par log pet** — Une seule connexion pour INSERT + COUNT + DELETE.

---

## [1.1.2] - 2026-04-10

### Added

- **Pet collection book** — Right-click opens /pets, shift+click opens pet panel for active pet.
- **Independent enable/disable** — Each mod (/pets, /prestige, /ah) has its own enable/disable command.
- **Optional dependencies** — arcadia_pets and arcadia_ah marked as optional in prestige mods.toml.

### Ajouts

- **Livre de collection** — Clic droit ouvre /pets, shift+clic ouvre le panneau du familier actif.
- **Enable/disable indépendant** — Chaque mod a sa propre commande enable/disable.
- **Dépendances optionnelles** — arcadia_pets et arcadia_ah marqués optionnels dans le mods.toml de prestige.

---
