# ⚔️ MoroSMP Plugins Ecosystem

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=java&logoColor=white)
![Spigot](https://img.shields.io/badge/Spigot-1.21.1-orange?style=for-the-badge)
![MongoDB](https://img.shields.io/badge/MongoDB-Database-4EA94B?style=for-the-badge&logo=mongodb&logoColor=white)

A comprehensive monorepo containing custom Minecraft plugins developed for the MoroSMP network. These plugins feature modular architecture, asynchronous database operations, and custom GUI systems.

## 📦 Included Plugins

* **MoroAuction:** A robust auction house system hooked into Vault economy and MongoDB logging. Features a dynamic GUI and expiration handling.
* **MoroShop:** Custom GUI-based economy shop with smart buying/selling mechanics.
* **MoroBounty:** Headhunting and bounty system with a custom UI and database tracking.
* **MoroRTP:** Random teleportation system with safe location algorithms and async chunk loading.
* **MoroCombat:** Advanced combat-logging and PvP mechanics to prevent combat logging.
* **MoroKillSound:** Custom audio feedback and effects for PvP events.

*(Note: Server-specific core plugins containing proprietary database schemas and internal network logic are kept in a separate private repository).*

## Architecture & Best Practices
We use a **Data-Driven Approach**. The Minecraft server acts as a Data Provider. Time-consuming operations (like MongoDB reads/writes in the Auction and Bounty systems) are handled asynchronously via `Bukkit.getScheduler().runTaskAsynchronously()` to ensure zero impact on the main server thread (TPS). 

GUI instances are managed as Singletons or Plugin-managed objects to ensure reliable `InventoryClickEvent` handling without memory leaks. We utilize the `maven-shade-plugin` to build "Fat JARs", bundling all external drivers directly into the final build.

## Building the Project

This project uses **Maven** for dependency management. Ensure you have Java 21+ and Maven installed.

### Option 1: Mass Build (Python - Recommended)
Use the included mass builder script to compile, shade, and package all plugins simultaneously:
`mass_builder.py`

### Option 2: Manual Build
Navigate to any specific plugin directory and run:
`mvn clean package`

*Compiled and shaded `.jar` files will be generated in the respective `target/` directories.*

## ⚙️ Configuration
Before running the database-reliant plugins (like MoroAuction) on a server, ensure you configure the MongoDB connection in their respective `config.yml` files:

`mongodb-uri: "mongodb+srv://<user>:<pass>@cluster0.mongodb.net/?retryWrites=true&w=majority"`
`database-name: "morosmp"`