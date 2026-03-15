package net.morosmp.rtp;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class TeleportManager {
    private final MoroRTP plugin;
    private final Map<UUID, BukkitTask> activeTeleports = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public TeleportManager(MoroRTP plugin) {
        this.plugin = plugin;
    }

    public void startWarmup(Player player, double price, int maxRadius) {
        if (activeTeleports.containsKey(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().parseMessage("already-teleporting"));
            return;
        }

        if (cooldowns.containsKey(player.getUniqueId())) {
            long timePassed = System.currentTimeMillis() - cooldowns.get(player.getUniqueId());
            int cooldownSeconds = plugin.getConfig().getInt("settings.cooldown", 300);
            if (timePassed < cooldownSeconds * 1000L) {
                int timeLeft = (int) (cooldownSeconds - (timePassed / 1000));
                player.sendMessage(
                        plugin.getConfigManager().parseMessage("cooldown", "%time%", String.valueOf(timeLeft)));
                return;
            }
        }

        if (price > 0 && !plugin.getVaultHook().hasEnoughMoney(player, price)) {
            player.sendMessage(plugin.getConfigManager().parseMessage("no-money", "%price%", String.valueOf(price)));
            return;
        }

        int delay = plugin.getConfig().getInt("settings.warmup-time", 5);
        RTPTeleportTask taskRunnable = new RTPTeleportTask(plugin, this, player, delay, price, maxRadius);
        BukkitTask task = taskRunnable.runTaskTimer(plugin, 0L, 20L);

        activeTeleports.put(player.getUniqueId(), task);
        if (!plugin.getConfig().getBoolean("settings.disable-chat-messages", false)) {
            player.sendMessage(
                    plugin.getConfigManager().parseMessage("warmup-started", "%time%", String.valueOf(delay)));
        }
    }

    public void cancelTeleport(UUID uuid) {
        BukkitTask task = activeTeleports.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    public void cancelTeleportWithEvent(Player player, String messageKey) {
        if (activeTeleports.containsKey(player.getUniqueId())) {
            BukkitTask task = activeTeleports.get(player.getUniqueId());
            if (task instanceof RTPTeleportTask) {
                player.sendActionBar(Component.empty());
            }

            cancelTeleport(player.getUniqueId());
            if (!plugin.getConfig().getBoolean("settings.disable-chat-messages", false)) {
                player.sendMessage(plugin.getConfigManager().parseMessage(messageKey));
            }

            Sound cancelSound = plugin.getConfigManager().getSound("cancel");
            if (cancelSound != null) {
                player.playSound(player.getLocation(), cancelSound,
                        plugin.getConfigManager().getSoundVolume("cancel"),
                        plugin.getConfigManager().getSoundPitch("cancel"));
            }
        }
    }

    public boolean isTeleporting(UUID uuid) {
        return activeTeleports.containsKey(uuid);
    }

    public void findLocationAndTeleport(Player player, boolean isHardcoreRespawn, double price, int maxRadius) {
        // Выбираем случайный РАЗБЛОКИРОВАННЫЙ мир
        World world = plugin.getWorldUnlockManager().getRandomUnlockedWorld();

        int minRadius = 0;

        if (isHardcoreRespawn) {
            maxRadius = plugin.getConfig().getInt("settings.radius.hardcore", 100000);
            World borderWorld = player.getWorld();
            int borderSize = (int) borderWorld.getWorldBorder().getSize() / 2;
            if (borderSize > 0 && borderSize < maxRadius) {
                maxRadius = borderSize;
            }
        } else {
            minRadius = plugin.getConfig().getInt("settings.radius.min", 1000);
            if (maxRadius <= 0) {
                maxRadius = plugin.getConfig().getInt("settings.radius.max", 10000);
            }
        }

        // Адаптация радиуса для Ада (1 блок в аду = 8 в обычном мире)
        if (world.getEnvironment() == World.Environment.NETHER) {
            maxRadius /= 8;
            minRadius /= 8;
        }

        final int finalMaxRadius = maxRadius;
        final int finalMinRadius = minRadius;
        final World finalWorld = world;

        int x = ThreadLocalRandom.current().nextInt(finalMinRadius, finalMaxRadius + 1)
                * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int z = ThreadLocalRandom.current().nextInt(finalMinRadius, finalMaxRadius + 1)
                * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);

        final int finalX = x;
        final int finalZ = z;

        finalWorld.getChunkAtAsync(finalX >> 4, finalZ >> 4).thenAccept(chunk -> {
            int y = finalWorld.getHighestBlockYAt(finalX, finalZ);
            Block targetBlock = finalWorld.getBlockAt(finalX, y - 1, finalZ);

            List<String> unsafeBlocks = plugin.getConfig().getStringList("unsafe-blocks");

            if (unsafeBlocks.contains(targetBlock.getType().name())) {
                findLocationAndTeleport(player, isHardcoreRespawn, price, finalMaxRadius);
                return;
            }

            Location finalLoc = new Location(finalWorld, finalX + 0.5, y, finalZ + 0.5);

            if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
                if (!WorldGuardHook.isSafeLocation(finalLoc)) {
                    findLocationAndTeleport(player, isHardcoreRespawn, price, finalMaxRadius);
                    return;
                }
            }

            player.teleportAsync(finalLoc).thenAccept(success -> {
                if (success) {
                    if (isHardcoreRespawn) {
                        plugin.getServer().getScheduler().runTask(plugin, player::clearActivePotionEffects);
                        player.sendMessage(plugin.getConfigManager().parseMessage("hardcore-respawn"));
                    } else {
                        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());

                        if (price > 0) {
                            plugin.getVaultHook().withdrawMoney(player, price);
                        }

                        int blindDur = plugin.getConfig().getInt("effects.blindness-duration", 3) * 20;
                        int slowDur = plugin.getConfig().getInt("effects.slow-falling-duration", 5) * 20;

                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (blindDur > 0) {
                                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindDur, 1));
                            }
                            if (slowDur > 0) {
                                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, slowDur, 1));
                            }
                        });

                        plugin.getLogger()
                                .info("Player " + player.getName() + " teleported to " + finalWorld.getName()
                                        + " at X: " + finalX + ", Z: " + finalZ);

                        if (!plugin.getConfig().getBoolean("settings.disable-chat-messages", false)) {
                            if (plugin.getConfig().getBoolean("settings.show-coordinates", true)) {
                                player.sendMessage(plugin.getConfigManager().parseMessage("success",
                                        "%x%", String.valueOf(finalX),
                                        "%y%", String.valueOf(y),
                                        "%z%", String.valueOf(finalZ),
                                        "%price%", String.valueOf(price)));
                            } else {
                                player.sendMessage(plugin.getConfigManager().parseMessage("success-simple",
                                        "%price%", String.valueOf(price)));
                            }
                        }
                    }

                    org.bukkit.Sound successSound = plugin.getConfigManager().getSound("success");
                    if (successSound != null) {
                        player.playSound(player.getLocation(), successSound,
                                plugin.getConfigManager().getSoundVolume("success"),
                                plugin.getConfigManager().getSoundPitch("success"));
                    }
                }
            });
        });
    }
}