package deepslatedev.playervaults;

import org.powernukkitx.Player;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.inventory.fake.FakeInventory;
import org.powernukkitx.inventory.fake.FakeInventoryType;
import org.powernukkitx.item.Item;
import org.powernukkitx.plugin.PluginBase;
import org.powernukkitx.utils.TextFormat;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PlayerVaults extends PluginBase {
    private VaultStore store;
    private final Map<String, Map<Integer, Map<Integer, Item>>> cache = new HashMap<>();
    private final Map<String, FakeInventory> open = new HashMap<>();
    private final Map<String, Set<String>> viewers = new HashMap<>();
    private Set<String> blacklist = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        store = new VaultStore(new File(getDataFolder(), "vaults"));
        blacklist = new HashSet<>();
        for (String id : getConfig().getStringList("blacklist")) {
            blacklist.add(id.toLowerCase(Locale.ROOT));
        }
    }

    @Override
    public void onDisable() {
        for (String key : new HashSet<>(open.keySet())) {
            flush(key);
        }
        for (Map.Entry<String, Map<Integer, Map<Integer, Item>>> entry : cache.entrySet()) {
            store.save(entry.getKey(), entry.getValue());
        }
    }

    private String msg(String key, String... pairs) {
        String text = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            text = text.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return text;
    }

    private String prefixed(String key, String... pairs) {
        return msg("prefix") + msg(key, pairs);
    }

    private int maxVaults() {
        return Math.max(1, Math.min(54, getConfig().getInt("max-vaults", 27)));
    }

    int allowed(Player player) {
        if (player.isOp()) {
            return maxVaults();
        }
        for (int n = maxVaults(); n > 0; n--) {
            if (player.hasPermission("playervaults.amount." + n)) {
                return n;
            }
        }
        return Math.max(0, Math.min(maxVaults(), getConfig().getInt("default-vaults", 1)));
    }

    private Map<Integer, Map<Integer, Item>> vaultsOf(String owner) {
        return cache.computeIfAbsent(owner.toLowerCase(Locale.ROOT), store::load);
    }

    private static String key(String owner, int number) {
        return owner.toLowerCase(Locale.ROOT) + ":" + number;
    }

    private boolean blacklisted(Item item) {
        return item != null && !item.isNull() && blacklist.contains(item.getId().toLowerCase(Locale.ROOT));
    }

    private int vaultSize() {
        return getConfig().getInt("vault-size", 54) <= 27 ? 27 : 54;
    }

    private void later(Runnable task) {
        getServer().getScheduler().scheduleDelayedTask(this, task, Math.max(1, getConfig().getInt("open-delay-ticks", 4)));
    }

    public void openVault(Player viewer, String owner, int number) {
        String k = key(owner, number);
        FakeInventory inventory = open.get(k);
        if (inventory == null) {
            boolean self = owner.equalsIgnoreCase(viewer.getName());
            String title = self ? msg("vault-title", "number", String.valueOf(number)) : msg("vault-title-other", "player", owner, "number", String.valueOf(number));
            inventory = new FakeInventory(vaultSize() == 27 ? FakeInventoryType.CHEST : FakeInventoryType.DOUBLE_CHEST, title);
            Map<Integer, Item> contents = vaultsOf(owner).getOrDefault(number, new HashMap<>());
            for (Map.Entry<Integer, Item> slot : contents.entrySet()) {
                if (slot.getKey() < inventory.getSize()) {
                    inventory.setItem(slot.getKey(), slot.getValue().clone(), false);
                }
            }
            inventory.setDefaultItemHandler((fake, slot, oldItem, newItem, event) -> {
                Player actor = event.getPlayer();
                if (blacklisted(newItem) && !actor.hasPermission("playervaults.bypass.blacklist")) {
                    event.setCancelled(true);
                    actor.sendMessage(prefixed("blacklisted"));
                }
            });
            inventory.setOnCloseHandler(player -> {
                Set<String> set = viewers.get(k);
                if (set != null) {
                    set.remove(player.getName().toLowerCase(Locale.ROOT));
                    if (set.isEmpty()) {
                        flush(k);
                    }
                }
            });
            open.put(k, inventory);
        }
        viewers.computeIfAbsent(k, x -> new HashSet<>()).add(viewer.getName().toLowerCase(Locale.ROOT));
        viewer.addWindow(inventory);
    }

    private void flush(String k) {
        FakeInventory inventory = open.remove(k);
        viewers.remove(k);
        if (inventory == null) {
            return;
        }
        int split = k.lastIndexOf(':');
        String owner = k.substring(0, split);
        int number = Integer.parseInt(k.substring(split + 1));
        Map<Integer, Item> contents = new HashMap<>();
        for (Map.Entry<Integer, Item> slot : inventory.getContents().entrySet()) {
            if (slot.getValue() != null && !slot.getValue().isNull()) {
                contents.put(slot.getKey(), slot.getValue().clone());
            }
        }
        Map<Integer, Map<Integer, Item>> vaults = vaultsOf(owner);
        vaults.put(number, contents);
        store.save(owner, vaults);
    }

    private void openSelector(Player player) {
        int allowed = allowed(player);
        int shown = Math.min(27, Math.max(allowed, Math.min(maxVaults(), 9)));
        FakeInventory selector = new FakeInventory(FakeInventoryType.CHEST, msg("selector-title"));
        Map<Integer, Map<Integer, Item>> vaults = vaultsOf(player.getName());
        for (int i = 0; i < shown; i++) {
            int number = i + 1;
            Item icon;
            if (number <= allowed) {
                icon = Item.get("minecraft:chest");
                int stacks = vaults.getOrDefault(number, Map.of()).size();
                icon.setCustomName(TextFormat.RESET + msg("vault-entry", "number", String.valueOf(number)));
                icon.setLore(TextFormat.RESET + (stacks == 0 ? msg("vault-empty") : msg("vault-lore", "count", String.valueOf(stacks))));
                icon.setCount(Math.max(1, Math.min(64, number)));
            } else {
                icon = Item.get("minecraft:gray_stained_glass_pane");
                icon.setCustomName(TextFormat.RESET + msg("vault-locked", "number", String.valueOf(number)));
                icon.setLore(TextFormat.RESET + msg("vault-locked-lore"));
            }
            selector.setItem(i, icon, false);
        }
        selector.setDefaultItemHandler((fake, slot, oldItem, newItem, event) -> {
            event.setCancelled(true);
            Player actor = event.getPlayer();
            int number = slot + 1;
            if (slot < 0 || number > allowed) {
                return;
            }
            actor.removeWindow(fake);
            later(() -> {
                if (actor.isOnline()) {
                    openVault(actor, actor.getName(), number);
                }
            });
        });
        player.addWindow(selector);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefixed("players-only"));
            return true;
        }
        if (args.length == 0) {
            fromChat(() -> openSelector(player), player);
            return true;
        }
        int number;
        try {
            number = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(prefixed("invalid-number", "max", String.valueOf(maxVaults())));
            return true;
        }
        if (number < 1 || number > maxVaults()) {
            player.sendMessage(prefixed("invalid-number", "max", String.valueOf(maxVaults())));
            return true;
        }
        if (args.length >= 2 && player.hasPermission("playervaults.admin")) {
            String owner = args[1];
            Player online = getServer().getPlayerExact(owner);
            if (online != null) {
                owner = online.getName();
            } else if (!store.exists(owner) && !cache.containsKey(owner.toLowerCase(Locale.ROOT))) {
                player.sendMessage(prefixed("no-data", "player", owner));
                return true;
            }
            String target = owner;
            fromChat(() -> openVault(player, target, number), player);
            return true;
        }
        int allowed = allowed(player);
        if (number > allowed) {
            player.sendMessage(prefixed("no-access", "amount", String.valueOf(allowed)));
            return true;
        }
        fromChat(() -> openVault(player, player.getName(), number), player);
        return true;
    }

    private void fromChat(Runnable task, Player player) {
        getServer().getScheduler().scheduleDelayedTask(this, () -> {
            if (player.isOnline()) {
                task.run();
            }
        }, Math.max(1, getConfig().getInt("command-delay-ticks", 10)));
    }
}
