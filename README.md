Markdown

# MoroRTP

A lightweight, high-performance random teleportation plugin for PaperMC (1.21.1+) featuring dimension progression and modern text formatting.

## Key Features

* **Dimension Progression:** Worlds are locked for RTP until a player physically enters them (portals), creating a natural sense of exploration.
* **Small Caps Engine:** Integrated filter that converts all plugin messages to Small Caps typography without breaking MiniMessage/HEX gradients.
* **Asynchronous Search:** Utilizing Paper's Async Chunk Loading API to prevent main-thread stutters during location searching.
* **Smart Safety Check:** Automatically avoids lava, water, and regions protected by WorldGuard.
* **Economy Support:** Fully integrated with Vault for paid teleportation zones.
* **Modern Visuals:** Action bar countdowns and customizable particle effects (Portal/Enderman style).

## Configuration

The plugin uses a clean `config.yml` for all settings. Below is the default structure:

```yaml
settings:
  cooldown: 300
  warmup-time: 5
  price: 500.0
  radius:
    min: 1000
    max: 10000

Dependencies

To compile or run MoroRTP, you will need:

    Paper API (1.21.1+)

    Vault (For economy features)

    WorldGuard (Optional, for region protection)

Installation

    Download the latest .jar from the releases page.

    Place the file in your server's /plugins/ directory.

    Restart the server.

    Configure prices and radii in plugins/MoroRTP/config.yml.

Commands & Permissions
Command	Description	Permission
/rtp	Opens the RTP selection menu	morortp.use
/rtp help	Shows command help	morortp.use
