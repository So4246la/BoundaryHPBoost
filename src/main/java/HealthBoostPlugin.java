package com.example.healthboost;

import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class HealthBoostPlugin extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        adjustPlayerHealth(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        adjustPlayerHealth(event.getPlayer());
    }

    private void adjustPlayerHealth(Player player) {
        double maxHealth = 20.0; // デフォルト10ハート (1ハート=2)

        World.Environment environment = player.getWorld().getEnvironment();

        double x = player.getLocation().getX();
        double z = player.getLocation().getZ();

        if (environment == World.Environment.NETHER) {
            // ネザー座標をオーバーワールド換算
            x *= 8;
            z *= 8;
        }

        if (environment == World.Environment.NORMAL || environment == World.Environment.NETHER) {
            double sum = Math.abs(x) + Math.abs(z);
            int increase = (int) Math.floor(sum / 5000.0);
            maxHealth += 2 * increase;
        }

        // Updated attribute for newer Paper API versions
        AttributeInstance healthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttribute != null && healthAttribute.getBaseValue() != maxHealth) {
            healthAttribute.setBaseValue(maxHealth);
            if (player.getHealth() > maxHealth) {
                player.setHealth(maxHealth);
            }
        }
    }
}
