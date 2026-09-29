package deepslatedev.playervaults;

import org.cloudburstmc.nbt.NBTInputStream;
import org.cloudburstmc.nbt.NBTOutputStream;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.nbt.NbtUtils;
import org.powernukkitx.item.Item;
import org.powernukkitx.nbt.tag.CompoundTag;
import org.powernukkitx.utils.ItemHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class VaultStore {
    private final File folder;

    public VaultStore(File folder) {
        this.folder = folder;
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IllegalStateException("Cannot create " + folder);
        }
    }

    private File fileOf(String player) {
        return new File(folder, player.toLowerCase(Locale.ROOT) + ".dat");
    }

    public boolean exists(String player) {
        return fileOf(player).isFile();
    }

    public Map<Integer, Map<Integer, Item>> load(String player) {
        Map<Integer, Map<Integer, Item>> vaults = new HashMap<>();
        File file = fileOf(player);
        if (!file.isFile()) {
            return vaults;
        }
        try (NBTInputStream in = NbtUtils.createGZIPReader(new FileInputStream(file))) {
            NbtMap root = (NbtMap) in.readTag();
            for (String key : root.keySet()) {
                int number;
                try {
                    number = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    continue;
                }
                Map<Integer, Item> contents = new HashMap<>();
                for (NbtMap entry : root.getList(key, NbtType.COMPOUND)) {
                    int slot = entry.getInt("Slot", -1);
                    Item item = ItemHelper.read(CompoundTag.fromNetwork(entry));
                    if (slot >= 0 && item != null && !item.isNull()) {
                        contents.put(slot, item);
                    }
                }
                vaults.put(number, contents);
            }
        } catch (IOException | RuntimeException e) {
            File broken = new File(folder, file.getName() + ".broken-" + System.currentTimeMillis());
            if (!file.renameTo(broken)) {
                throw new IllegalStateException("Cannot read vaults of " + player, e);
            }
        }
        return vaults;
    }

    public void save(String player, Map<Integer, Map<Integer, Item>> vaults) {
        NbtMapBuilder root = NbtMap.builder();
        for (Map.Entry<Integer, Map<Integer, Item>> vault : vaults.entrySet()) {
            List<NbtMap> items = new ArrayList<>();
            for (Map.Entry<Integer, Item> slot : vault.getValue().entrySet()) {
                Item item = slot.getValue();
                if (item == null || item.isNull()) {
                    continue;
                }
                NbtMap tag = ItemHelper.write(item).toNetwork();
                items.add(tag.toBuilder().putInt("Slot", slot.getKey()).build());
            }
            root.putList(String.valueOf(vault.getKey()), NbtType.COMPOUND, items);
        }
        File file = fileOf(player);
        File temp = new File(folder, file.getName() + ".tmp");
        try (NBTOutputStream out = NbtUtils.createGZIPWriter(new FileOutputStream(temp))) {
            out.writeTag(root.build());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot save vaults of " + player, e);
        }
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("Cannot replace " + file);
        }
        if (!temp.renameTo(file)) {
            throw new IllegalStateException("Cannot move " + temp);
        }
    }
}
