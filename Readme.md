# ⚔️ MoroSMP Plugins Ecosystem

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=java&logoColor=white)
![Paper](https://img.shields.io/badge/Paper-1.21-F6F6F6?style=for-the-badge)
![MongoDB](https://img.shields.io/badge/MongoDB-Database-4EA94B?style=for-the-badge&logo=mongodb&logoColor=white)
![SQLite](https://img.shields.io/badge/SQLite-003B57?style=for-the-badge&logo=sqlite&logoColor=white)
![Python](https://img.shields.io/badge/Python-3776AB?style=for-the-badge&logo=python&logoColor=white)

A comprehensive monorepo containing custom Minecraft plugins developed for the **MoroSMP** network. 
This project goes beyond standard Bukkit development by integrating a **Java-based game server with a MERN stack web frontend** and external Python APIs. 

Built with a strict **Premium UI** philosophy: 100% English, Small Caps typography, modern HEX colors, and absolutely zero Pay-To-Win mechanics.

## 📦 Core Ecosystem Plugins

* **MoroStats:** The heart of the server. Features a zero-flicker, packet-based Scoreboard (powered by `FastBoard`) and asynchronously pushes player statistics (K/D, Playtime) to a MongoDB cluster for real-time rendering on our React/Node.js frontend.
* **MoroBounty:** Hardcore headhunting system backed by a concurrent **SQLite** database (`database.db`). Fully integrated with our external Python API for Telegram/Discord bot webhooks. Features an interactive GUI with asynchronous SQL queries.
* **MoroRTP:** High-performance random teleportation. Utilizes Paper's native `teleportAsync` and background chunk loading to find safe locations (with strict Void-checks for the End dimension) without impacting main-thread TPS.
* **MoroCombat:** Advanced combat-logging prevention to protect the server's hardcore economy. Tracks PvP engagement and punishes combat loggers instantly.
* **MoroKillSound:** Dynamic audio feedback system. Reads customizable Sound Enums and pitch/volume modifiers directly from the config with safe fallback logic.
* **MoroTeams:** Clan system with friendly-fire protection and PlaceholderAPI (`%moroteams_team%`) integration. 
* **MoroHomes & MoroWarp:** Clean, localized teleportation management with physical/economy constraints for upgrades.
* **MoroSettings:** Custom GUI for players to toggle private messages, scoreboard visibility, and personal preferences.

## 🏗 Architecture & Best Practices
We utilize a **Lean Development Approach**, decoupled from heavy, bloated external dependencies.

* **Fullstack Integration:** Game data isn't trapped in YAML files. Utilizing MongoDB and SQLite allows external scripts (`API.py`) and MERN web apps to read server data concurrently (WAL mode).
* **Asynchronous Execution:** All database queries, chunk loading, and heavy math are offloaded via `Bukkit.getScheduler().runTaskAsynchronously()` to ensure a flawless 20.0 TPS.
* **Single Source of Truth (UI):** No hardcoded prefixes. All text formatting relies on LuckPerms weights and dynamic PlaceholderAPI parsing.

## 🚀 Building the Project

This project uses **Maven** for dependency management. Ensure you have Java 21+ and Maven installed.

### Mass Build (Python Pipeline - Recommended)
Use the included python script to clean, compile, shade, and package all plugins simultaneously into their respective `target/` directories:

```bash
python mass_builder.py