# 🌟 Arcadia Perks

This project contains a suite of 4 interconnected Minecraft mods (built for NeoForge 1.21.1) designed to bring a robust economy, collectibles, and VIP rewards to your server. 

*(Note: Currently fine-tuned for the Arcadia Echoes of Power v2 modpack, with future plans for deeper configuration options).*

## 📦 The Mods

### 1. 📚 Arcadia Lib
The core library and shared foundation required by all other Arcadia Perks mods.
* **Functionality:** Handles the backend heavy lifting. It provides the central database layer, player data management systems, and a developer debug mode.

### 2. 🐾 Arcadia Pets
An engaging, collectible companion system for players.
* **Functionality:** Allows players to unbox and collect pets from crates across 6 different rarity tiers. Pets have unique, levelable skills and can be summoned to follow you around or ride on your shoulder via "pocket mode."
* **Pet Duels:** Challenge other players to turn-based pet battles. Build a roster of 3 pets and fight using **Spirit Points (SP)** — a per-pet resource that starts at 1, caps at 5, and carries over between turns. Basic attacks are free; skills cost 1–4 SP. Some skills restore SP. Win to earn Star Essence and coins.

### 3. ⚖️ Arcadia Auction House (AH)
A comprehensive cross-player marketplace module.
* **Functionality:** Facilitates a player-driven economy where users can list and buy items. It features full integration with Numismatics currency and includes a physical mailbox block/system for players to claim their delivered purchases. *(Requires Arcadia Lib)*

### 4. ✨ Arcadia Prestige
The ultimate VIP hub and rewards module.
* **Functionality:** Serves as the main dashboard GUI for server perks. It introduces cosmetic particle effects and a daily reward calendar to keep players engaged. *(Requires Arcadia Pets and Arcadia AH)*
* **Daily Quests:** Three difficulty-tiered quests per player per day. Progress is tracked automatically; claim rewards from the Dashboard Quest tab.
* **ELO Leaderboard:** Every duel result updates both players' ELO ratings (K=32/24/16, floor 800). The top 10 players are visible in the Dashboard Leaderboard tab, each showing their favourite pet as an icon.
* **Arcadia Pass:** Grant `arcadia.pass` via LuckPerms to award holders +50% quest rewards and +5% ELO gain per duel win. A glowing badge appears in their Dashboard nav bar.
