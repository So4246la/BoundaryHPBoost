package com.example.healthboost;

import org.bukkit.NamespacedKey; // Bukkit APIに含まれるNamespacedKeyをインポート
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class HealthBoostPlugin extends JavaPlugin implements Listener {
    // Modifierを確実に識別するためのNamespacedKey (onEnableで初期化)
    private NamespacedKey healthBoostModifierKey;

    @Override
    public void onEnable() {
        // NamespacedKeyを初期化 (第一引数はプラグインインスタンス, 第二引数はユニークなキー文字列)
        this.healthBoostModifierKey = new NamespacedKey(this, "boundary_hp_boost_modifier");

        getServer().getPluginManager().registerEvents(this, this);

        // サーバーリロード時などに、オンラインの全プレイヤーにModifierを再適用
        for (Player player : getServer().getOnlinePlayers()) {
            adjustPlayerHealth(player);
        }
        getLogger().info("HealthBoostPlugin enabled. Modifier Key for lookup: " + this.healthBoostModifierKey.toString());
    }

    @Override
    public void onDisable() {
        // プラグイン無効時に、このプラグインが追加したModifierを全プレイヤーから削除
        for (Player player : getServer().getOnlinePlayers()) {
            removeHealthModifier(player);
        }
        getLogger().info("HealthBoostPlugin disabled and all custom health modifiers removed.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        adjustPlayerHealth(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // プレイヤー退出時にModifierを削除（クリーンナップ）
        removeHealthModifier(event.getPlayer());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // プレイヤーが実際にブロックを跨いで移動した場合のみ処理を実行
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ() &&
            event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            return;
        }
        adjustPlayerHealth(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        adjustPlayerHealth(event.getPlayer());
    }

    /**
     * 指定されたプレイヤーからこのプラグインが追加した体力Modifierを削除します。
     * @param player 対象のプレイヤー
     */
    private void removeHealthModifier(Player player) {
        AttributeInstance healthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttribute != null) {
            if (this.healthBoostModifierKey == null) {
                getLogger().warning("healthBoostModifierKey is not initialized. Cannot remove modifiers by key for player " + player.getName());
                return;
            }
            // Paperサーバーでは modifier.getKey() が NamespacedKey を返すことを期待
            healthAttribute.getModifiers().stream()
                .filter(modifier -> this.healthBoostModifierKey.equals(modifier.getKey())) // NamespacedKeyでフィルタリング
                .findFirst()
                .ifPresent(healthAttribute::removeModifier);
        }
    }

    /**
     * プレイヤーの座標に基づいて最大体力を調整します。
     * @param player 対象のプレイヤー
     */
    private void adjustPlayerHealth(Player player) {
        AttributeInstance healthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttribute == null) {
            getLogger().warning("Could not get GENERIC_MAX_HEALTH attribute for player: " + player.getName());
            return;
        }

        double healthBeforeChanges = player.getHealth();
        removeHealthModifier(player);

        double healthIncreaseAmount = 0.0;
        World.Environment environment = player.getWorld().getEnvironment();
        double x = player.getLocation().getX();
        double z = player.getLocation().getZ();

        if (environment == World.Environment.NETHER) {
            x *= 8; // ネザー座標をオーバーワールド換算
            z *= 8;
        }

        if (environment == World.Environment.NORMAL || environment == World.Environment.NETHER) {
            double sumOfAbsoluteCoordinates = Math.abs(x) + Math.abs(z);
            int increaseSteps = (int) Math.floor(sumOfAbsoluteCoordinates / 5000.0);
            healthIncreaseAmount = 2.0 * increaseSteps;
        }

        if (healthIncreaseAmount > 0) {
            if (this.healthBoostModifierKey == null) {
                getLogger().warning("healthBoostModifierKey is not initialized. Cannot create modifier for player " + player.getName());
                return;
            }

            // Paper API 1.21.5 のJavadocに基づき、NamespacedKey, amount, operation を取るコンストラクタを使用
            AttributeModifier newModifier = new AttributeModifier(
                this.healthBoostModifierKey,
                healthIncreaseAmount,
                AttributeModifier.Operation.ADD_NUMBER
            );

            try {
                healthAttribute.addModifier(newModifier);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Failed to add health boost modifier for player " + player.getName() + ". Reason: " + e.getMessage());
            }
        }

        double newActualMaxHealth = healthAttribute.getValue();
        double targetHealth = Math.min(healthBeforeChanges, newActualMaxHealth);

        if (targetHealth <= 0 && !player.isDead()) {
            if (newActualMaxHealth > 0) {
                 targetHealth = Math.min(1.0, newActualMaxHealth);
            } else {
                 targetHealth = 1.0;
            }
        }
        
        if (player.getHealth() != targetHealth) {
            player.setHealth(targetHealth);
        }
    }
}