package com.example.healthboost;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
            player.getScheduler().run(this, scheduledTask -> adjustPlayerHealth(player));
        }
        getLogger().info("HealthBoostPlugin enabled. Modifier Key for lookup: " + this.healthBoostModifierKey.toString());
    }

    @Override
    public void onDisable() {
        // プラグイン無効時に、このプラグインが追加したModifierを全プレイヤーから削除
        for (Player player : getServer().getOnlinePlayers()) {
            player.getScheduler().run(this, scheduledTask -> removeHealthModifier(player));
        }
        getLogger().info("HealthBoostPlugin disabled and all custom health modifiers removed.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        AttributeInstance healthAttribute = player.getAttribute(Attribute.MAX_HEALTH);

        if (healthAttribute != null) {
            removeHealthModifier(player);

            // サーバーのデフォルト最大体力を取得
            double serverDefaultMaxHealth = healthAttribute.getDefaultValue();

            if (healthAttribute.getBaseValue() > serverDefaultMaxHealth) {
                // BaseValueがデフォルト最大体力を超える場合は初期化する
                getLogger().info(String.format(
                    "Player: %s has a BaseValue of %.2f (server default: %.2f). Resetting to default.",
                    player.getName(), healthAttribute.getBaseValue(), serverDefaultMaxHealth
                ));
                healthAttribute.setBaseValue(serverDefaultMaxHealth);

                // 現在の体力が新しいBaseValueを超える場合は調整
                if (player.getHealth() > serverDefaultMaxHealth) {
                    player.setHealth(serverDefaultMaxHealth);
                }
            }
        }
        adjustPlayerHealth(player);
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

            int removedCount = 0;
            Iterator<AttributeModifier> iterator = healthAttribute.getModifiers().iterator();
            while (iterator.hasNext()) {
                AttributeModifier modifier = iterator.next();
                if (this.healthBoostModifierKey.equals(modifier.getKey())) {
                    iterator.remove();
                    removedCount++;
                }
            }

            if (removedCount >= 2) {
                getLogger().info(String.format("Player: %s - Removed %d stacked/duplicate modifier(s) with key %s.",
                        player.getName(), removedCount, this.healthBoostModifierKey));
            }
        }
    }

    /**
     * プレイヤーの座標に基づいて最大体力を調整します。
     * @param player 対象のプレイヤー
     */
    private void adjustPlayerHealth(Player player) {
        AttributeInstance healthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttribute == null) {
            getLogger().warning("Could not get MAX_HEALTH attribute for player: " + player.getName());
            return;
        }

        // 現在の座標に基づく増加値を算出する
        double requiredIncreaseAmount = 0.0;
        World.Environment environment = player.getWorld().getEnvironment();
        double x = player.getLocation().getX();
        double z = player.getLocation().getZ();

        if (environment == World.Environment.NETHER) {
            x *= 8;
            z *= 8;
        }

        if (environment == World.Environment.NORMAL || environment == World.Environment.NETHER) {
            double sumOfAbsoluteCoordinates = Math.abs(x) + Math.abs(z);
            int increaseSteps = (int) Math.floor(sumOfAbsoluteCoordinates / 5000.0);
            requiredIncreaseAmount = 2.0 * increaseSteps;
        }

        // このプラグインによって増加している既存の値取得する
        Optional<AttributeModifier> existingModifierOpt = healthAttribute.getModifiers().stream()
            .filter(modifier -> this.healthBoostModifierKey.equals(modifier.getKey()))
            .findFirst();
        double currentIncreaseAmount = existingModifierOpt.map(AttributeModifier::getAmount).orElse(0.0);

        // 現在座標による値と既存の値が同じであれば何もしない
        if (currentIncreaseAmount == requiredIncreaseAmount) {
            return;
        }

        double healthBeforeChanges = player.getHealth();

        // 既存のModifierを削除
        existingModifierOpt.ifPresent(healthAttribute::removeModifier);

        // 新しいModifierを追加
        if (requiredIncreaseAmount > 0) {
            AttributeModifier newModifier = new AttributeModifier(
                this.healthBoostModifierKey,
                requiredIncreaseAmount,
                AttributeModifier.Operation.ADD_NUMBER
            );
            healthAttribute.addModifier(newModifier);
        }
 
        double newActualMaxHealth = healthAttribute.getValue();
        double targetHealth = Math.min(healthBeforeChanges, newActualMaxHealth);

        if (targetHealth <= 0 && !player.isDead()) {
            targetHealth = Math.min(1.0, newActualMaxHealth);
        }
        
        if (player.getHealth() != targetHealth) {
            player.setHealth(targetHealth);
        }
    }
}