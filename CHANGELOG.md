# Arcadia Mods — Changelog

---

## [1.1.0] — 2026-03-23

### Bug Fixes
- **Silent following pets** — following pets no longer emit ambient/hurt/death sounds (`mob.setSilent(true)`)
- **Default movement mode = Pocket** — pets now spawn in Pocket mode on login instead of Follow, reducing server load
- **F1 hides pet HUD** — pressing F1 (hide GUI) now correctly hides the pet portrait/HP/aftershock bars
- **UI sounds respect GUI volume** — all custom button clicks and reveal sounds now use `SoundSource.MASTER` (via `SimpleSoundInstance`); muting GUI volume in vanilla settings silences them correctly
- **Anvil rename blocked** — pet items can no longer be renamed via an anvil; the anvil operation returns the unchanged item at no cost
- **PetPoolConfig never applied** — the `arcadia-pets.toml` rarity/stat/pool values were registered but never actually loaded at runtime; this is now wired correctly via `ModConfigEvent`

### Improvements
- **"Epilepsy-safe mode (bag spin)"** — the "Reduced Motion" toggle in HUD settings has been renamed for clarity
- **Multi-reveal strip height** — opening a stack of 4 crates now shows 4 strips at the same total height as 3 strips (dynamic per-strip height)
- **Strip index labels removed** — the `#1 #2 #3 #4` labels next to roulette strips are gone
- **Stars pop animation** — in the phase-1 minicard view, each card's total star count now fills in one star at a time with a sound, staggered across cards
- **25+ stars special event** — pets with 25 or more total stars (out of 30) trigger a gold glow on their minicard and a distinct sound when fully revealed
- **Rarity-based landing sounds** — Epic, Legendary, and Mythic crate landings now play progressively more dramatic sounds instead of the plain anvil thud
- **Aftershock target configuration** — admins can now control which entity categories trigger the aftershock skill independently:
  - `/pets aftershock hostile <true|false>` — hostile mobs (default: on)
  - `/pets aftershock neutral <true|false>` — neutral/passive mobs (default: on)
  - `/pets aftershock players <true|false>` — players (default: off)
- **`ServerContext` in arcadia-lib** — shared server ID holder readable by all modules without circular dependencies

### Config Files (new — for server owners)
Three new TOML config files are generated in the `config/` folder on first launch:

- **`arcadia-prestige.toml`** — map each Arcadia rank to your server's LuckPerms permission node without recompiling:
  ```toml
  [grades]
  perm_vip     = "arcadia.grade.vip"
  perm_vip_plus = "arcadia.grade.vipplus"
  perm_mvp     = "arcadia.grade.mvp"
  perm_founder = "arcadia.grade.founder"

  [server]
  server_id = "server1"
  ```
- **`arcadia-ah.toml`** — tune Auction House limits:
  ```toml
  [listings]
  listing_duration_hours = 48
  max_listings_per_player = 30
  ```
- **`arcadia-pets.toml`** *(existing, now actually applied)* — rarity weights, stat floors, star weights, and mob pools per rarity tier

---

## [1.0.3] — 2026-03-21

- Accessibility: "Reduced Motion" toggle in HUD settings (skips bag spin animation)
- Pet HUD visible in both survival and creative (switched to HOTBAR GUI layer)
- UI button sounds added across PetScreen, DashboardScreen, PetGuideScreen, PetHudSettingsScreen
- FeatherFall (Chicken skill) cooldown fix — no longer triggers every tick while airborne

## [1.0.2] — 2026-03-19

- AlmanacOptimizationMixin: skips Almanac's per-tick NBT scan for pet items
- JAR manifest fix: `Implementation-Version` and `MixinConfigs` added to all 4 manifests

## [1.0.1] — 2026-03-18

- NBT flattening: `PetData` stats/skills stored as flat bytes + packed string (eliminates recursive CompoundTag.copy allocations)
- All 4 module versions use `${file.jarVersion}` for single-source versioning

## [1.0.0] — 2026-03-17

- Initial release: arcadia-lib, arcadia-pets, arcadia-ah, arcadia-prestige
- 6-rarity pet system, wheel-of-fortune reveal, Auction House, daily rewards, cosmetic particles
