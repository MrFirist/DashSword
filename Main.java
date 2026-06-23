package me.firist.dashsword;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class Main extends JavaPlugin implements Listener {

    private ItemStack dashSword;
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Set<UUID> fallImmunity = new HashSet<>();

    private int distance;
    private double speed;
    private int cooldownTicks;
    private boolean particles;
    private boolean sound;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        createDashSword();
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void loadSettings() {
        FileConfiguration cfg = getConfig();
        distance = cfg.getInt("dash.distance");
        speed = cfg.getDouble("dash.speed");
        cooldownTicks = cfg.getInt("dash.cooldown");
        particles = cfg.getBoolean("dash.particles");
        sound = cfg.getBoolean("dash.sound");
    }

    private void createDashSword() {
        dashSword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = dashSword.getItemMeta();
        meta.setDisplayName("§6§lДэш Меч");
        dashSword.setItemMeta(meta);

        FileConfiguration cfg = getConfig();
        NamespacedKey key = new NamespacedKey(this, "dash_sword");

        ShapedRecipe recipe = new ShapedRecipe(key, dashSword);

        recipe.shape(
                cfg.getString("craft.shape.0"),
                cfg.getString("craft.shape.1"),
                cfg.getString("craft.shape.2")
        );

        for (String k : cfg.getConfigurationSection("craft.ingredients").getKeys(false)) {
            Material m = Material.valueOf(cfg.getString("craft.ingredients." + k));
            recipe.setIngredient(k.charAt(0), m);
        }

        Bukkit.addRecipe(recipe);
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        if (!(e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK))
            return;

        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName())
            return;

        if (!item.getItemMeta().getDisplayName().equals("§6§lДэш Меч"))
            return;

        long now = System.currentTimeMillis();
        UUID id = p.getUniqueId();

        if (cooldown.containsKey(id)) {
            long last = cooldown.get(id);
            long cd = cooldownTicks * 50L;

            if (now - last < cd) {
                double left = (cd - (now - last)) / 1000.0;
                p.sendActionBar("§cКулдаун: §f" + String.format("%.1f", left) + " сек");
                return;
            }
        }

        cooldown.put(id, now);

        new BukkitRunnable() {
            @Override
            public void run() {
                long last = cooldown.get(id);
                long cd = cooldownTicks * 50L;
                long passed = System.currentTimeMillis() - last;

                if (passed >= cd) {
                    p.sendActionBar("");
                    cancel();
                    return;
                }

                double left = (cd - passed) / 1000.0;
                p.sendActionBar("§cКулдаун: §f" + String.format("%.1f", left) + " сек");
            }
        }.runTaskTimer(this, 0, 2);

        Vector direction = p.getLocation().getDirection().normalize().multiply(speed);

        new BukkitRunnable() {
            int moved = 0;

            @Override
            public void run() {
                if (moved >= distance) {
                    cancel();
                    return;
                }

                p.setVelocity(direction);

                if (particles)
                    p.getWorld().spawnParticle(Particle.FLAME, p.getLocation(), 5, 0.2, 0.2, 0.2, 0.01);

                if (sound)
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.2f);

                moved++;
            }
        }.runTaskTimer(this, 0, 1);

        fallImmunity.add(id);

        new BukkitRunnable() {
            @Override
            public void run() {
                fallImmunity.remove(id);
            }
        }.runTaskLater(this, 20 * 20);
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(e.getEntity() instanceof Player)) return;

        Player p = (Player) e.getEntity();

        if (fallImmunity.contains(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }
}
