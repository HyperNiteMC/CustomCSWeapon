package com.ericlam.mc.csweapon;

import com.shampaggon.crackshot.CSDirector;
import com.shampaggon.crackshot.CSUtility;
import com.shampaggon.crackshot.events.*;
import me.DeeCaaD.CrackShotPlus.Events.WeaponPreReloadEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class WeaponListeners implements Listener {
    static HashSet<UUID> leftScopes = new HashSet<>(); //later change permission
    private final CSUtility csUtility;
    private final CSDirector csDirector;
    private HashMap<Player, String> scoping = new HashMap<>();
    private HashMap<Player, ItemStack> originalOffhandItem = new HashMap<>();
    private final HashMap<String, ItemStack> skinScope = new HashMap<>();
    private final CustomCSWeapon csWeapon;
    private final CWSConfig cwsConfig;

    WeaponListeners(CustomCSWeapon csWeapon, CWSConfig cwsConfig) {
        this.csWeapon = csWeapon;
        csUtility = new CSUtility();
        csDirector = csUtility.getHandle();
        this.cwsConfig = cwsConfig;
        cwsConfig.scope_skin.forEach((k, wea) -> {
            if (wea == null) return;
            String[] value = wea.split(":");
            String material = value[0];
            ItemStack skinStack = new ItemStack(Material.valueOf(material));
            if (value.length > 1) {
                ItemMeta meta = skinStack.getItemMeta();
                ((Damageable) meta).setDamage(Integer.parseInt(value[1]));
                meta.setUnbreakable(true);
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
                skinStack.setItemMeta(meta);
            }
            skinScope.put(k, skinStack);
        });
    }

    @EventHandler
    public void onMolotovExplode(WeaponExplodeEvent e) {
        if (!cwsConfig.molotov.contains(e.getWeaponTitle())) return;
        csWeapon.getMolotovManager().spawnFires(e.getLocation().getBlock());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGunDamage(WeaponDamageEntityEvent e) {
        if (!(e.getVictim() instanceof Player)) return;
        Player attacker = e.getPlayer();
        Player player = (Player) e.getVictim();
        this.checkFriendlyFire(e, attacker, player);
        this.shotGunDamage(e, attacker, player);
        this.checkHeadShot(e, player);
    }

    private void checkHeadShot(WeaponDamageEntityEvent e, Player player) {
        if (!(e.getDamager() instanceof Projectile)) return;
        if (!e.isHeadshot()) return;
        boolean helmet = player.getInventory().getHelmet() != null;
        String sound = helmet ? cwsConfig.headshot.helmet_sound : cwsConfig.headshot.no_helmet_sound;
        if (!cwsConfig.headshot.custom_sound) {
            player.getWorld().playSound(player.getLocation(), Sound.valueOf(sound), 3, 1);
        } else {
            player.getWorld().playSound(player.getLocation(), sound, 3, 1);
        }
    }

    private void checkFriendlyFire(WeaponDamageEntityEvent e, Player attacker, Player player) {
        Team VTteam = player.getScoreboard().getEntryTeam(player.getName());
        Team ATteam = attacker.getScoreboard().getEntryTeam(attacker.getName());
        if (VTteam == null || ATteam == null) {
            return;
        }
        if (ATteam.getName().equals(VTteam.getName())) {
            if (!ATteam.allowFriendlyFire()) {
                e.setCancelled(true);
            }
        }
    }

    private void shotGunDamage(WeaponDamageEntityEvent e, Player attacker, Player victim) {
        if (!cwsConfig.shotguns.containsKey(e.getWeaponTitle())) return;
        double distance = attacker.getLocation().distance(victim.getLocation());
        final double origDamage = e.getDamage();
        final double finalDamage = origDamage - (distance * cwsConfig.shotguns.get(e.getWeaponTitle()));
        e.setDamage(finalDamage <= 5 ? 5 : finalDamage);
    }


    @EventHandler
    public void onPlayerLeftScope(PlayerInteractEvent e) {
        ItemStack item = e.getPlayer().getInventory().getItemInMainHand();
        Player player = e.getPlayer();
        if (e.getAction() != Action.LEFT_CLICK_AIR) return;
        if (!leftScopes.contains(player.getUniqueId())) return;
        if (!scoping.containsKey(player)) {
            String weaponTitle = csUtility.getWeaponTitle(item);
            if (weaponTitle == null) return;
            if (!cwsConfig.scope_skin.containsKey(weaponTitle)) return;
            scoping.put(player, weaponTitle);
            scope(weaponTitle, player, true);
        } else {
            unscope(player, true);
        }

    }

    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent e) {
        ItemStack item = e.getPlayer().getInventory().getItemInMainHand();
        Player player = e.getPlayer();
        if (leftScopes.contains(player.getUniqueId())) return;
        if (e.isSneaking()) {
            String weaponTitle = csUtility.getWeaponTitle(item);
            if (weaponTitle == null) return;
            if (!cwsConfig.scope_skin.containsKey(weaponTitle)) return;
            scoping.put(player, weaponTitle);
            scope(weaponTitle, player, true);
        } else {
            unscope(player, true);
        }
    }

    @EventHandler
    public void onPlayerSwitch(PlayerItemHeldEvent e) {
        Player player = e.getPlayer();
        unscope(player, true);
    }

    private void unscope(Player player, boolean remove) {
        if (!scoping.containsKey(player)) return;
        String weaponTitle = scoping.get(player);
        if (remove) scoping.remove(player);
        if (player.getInventory().getItemInOffHand().isSimilar(skinScope.get(weaponTitle))) {
            ItemStack stack;
            if (originalOffhandItem.containsKey(player)) stack = originalOffhandItem.get(player);
            else stack = new ItemStack(Material.AIR);
            player.getInventory().setItemInOffHand(stack);
            originalOffhandItem.remove(player);
        }
        csWeapon.getServer().getPluginManager().callEvent(new WeaponScopeEvent(player, weaponTitle, false));
        player.removePotionEffect(PotionEffectType.SPEED);
    }

    @EventHandler
    public void onScopeShoot(WeaponShootEvent e) {
        /*
        Projectile entity = (Projectile) e.getProjectile();
        Arrow arrow = entity.getWorld().spawnArrow(entity.getLocation(), e.getPlayer().getEyeLocation().getDirection(), 10.0f, 0);
        entity.remove();
        arrow.setGravity(false);
        arrow.setBounce(false);
        arrow.setTicksLived(100);
        arrow.setColor(Color.RED);

         */

        String weaponTitle = e.getWeaponTitle();
        if (!cwsConfig.scope_skin.containsKey(weaponTitle)) return;
        if (!csDirector.getString(weaponTitle + ".Firearm_Action.Type").equalsIgnoreCase("bolt")) return;
        Player player = e.getPlayer();
        unscope(player, false);
        int openTime = csDirector.getInt(e.getWeaponTitle() + ".Firearm_Action.Open_Duration");
        int closeShootDelay = csDirector.getInt(e.getWeaponTitle() + ".Firearm_Action.Close_Shoot_Delay");
        Bukkit.getScheduler().scheduleSyncDelayedTask(csWeapon, () -> scope(e.getWeaponTitle(), e.getPlayer(), false), closeShootDelay + openTime);
    }

    private void scope(String weaponTitle, Player player, boolean put) {
        if (!scoping.containsKey(player)) return;
        int zoomAmount = csDirector.getInt(weaponTitle + ".Scope.Zoom_Amount");
        csWeapon.getServer().getPluginManager().callEvent(new WeaponScopeEvent(player, weaponTitle, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 99999 * 20, -zoomAmount));
        if (!cwsConfig.scope_skin.containsKey(weaponTitle)) return;
        PlayerInventory playerInventory = player.getInventory();
        final ItemStack original_item = playerInventory.getItemInOffHand();
        if (put && original_item.getType() != Material.AIR)
            originalOffhandItem.put(player, original_item);
        ItemStack stack = skinScope.get(weaponTitle);
        playerInventory.setItemInOffHand(stack);
    }

    @EventHandler
    public void onReload(WeaponPreReloadEvent e) {
        if (originalOffhandItem.containsKey(e.getPlayer())) unscope(e.getPlayer(), true);
    }

    @EventHandler
    public void arrarcute(WeaponPreShootEvent e) {
        Player player = e.getPlayer();
        if (player.isSprinting() || !player.isOnGround()) {
            String type = csDirector.getString(e.getWeaponTitle() + ".Shooting.Projectile_Type");
            boolean notGrenade = !type.equalsIgnoreCase("grenade") && !type.equalsIgnoreCase("flare");
            boolean notMolotov = !cwsConfig.molotov.contains(e.getWeaponTitle());
            if (notGrenade && notMolotov) {
                e.setBulletSpread(5);
                return;
            }
        }
        if (!cwsConfig.scope_skin.containsKey(e.getWeaponTitle())) return;
        if (!scoping.containsKey(player)) return;
        double spread = csDirector.getDouble(e.getWeaponTitle() + ".Scope.Zoom_Bullet_Spread");
        e.setBulletSpread(spread);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        if (e.getClickedInventory() == null) return;
        if (e.getSlotType() == InventoryType.SlotType.OUTSIDE) return;
        if (!e.getClickedInventory().equals(player.getInventory())) return;
        if (e.getSlot() != 40) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getInventorySlots().contains(40)) e.setCancelled(true);
    }
}
