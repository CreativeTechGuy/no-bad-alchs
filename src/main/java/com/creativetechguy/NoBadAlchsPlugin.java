package com.creativetechguy;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.events.*;
import net.runelite.api.gameval.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.util.Text;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

@Slf4j
@PluginDescriptor(
        name = "No Bad Alchs",
        description = "Prevents casting alchemy on items which give less than the GE value",
        configName = NoBadAlchsConfig.GROUP_NAME
)
public class NoBadAlchsPlugin extends Plugin {
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private NoBadAlchsConfig config;
    @Inject
    private ItemManager itemManager;

    AlchType alchType = AlchType.None;
    HashSet<Widget> hiddenItems = new HashSet<>();
    HashSet<TaskName> tasksQueued = new HashSet<>();
    boolean wasDragging = false;
    List<Integer> excludedItems = Arrays.asList(
            ItemID.BLANKOBJECT,
            ItemID.MAGICTRAINING_LEATHER_BOOTS,
            ItemID.MAGICTRAINING_ADAMANT_KITESHIELD,
            ItemID.MAGICTRAINING_ADAMANT_MED_HELM,
            ItemID.MAGICTRAINING_EMERALD,
            ItemID.MAGICTRAINING_RUNE_LONGSWORD
    );
    private List<String> allowList;
    private List<String> denyList;

    @Provides
    NoBadAlchsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(NoBadAlchsConfig.class);
    }

    @Override
    protected void startUp() {
        loadLists();
    }

    @Override
    protected void shutDown() throws Exception {
        showHiddenItems();
        allowList = null;
        denyList = null;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals(NoBadAlchsConfig.GROUP_NAME)) {
            return;
        }
        loadLists();
        if (isAlching()) {
            resetHiddenItems();
        }
    }

    @Subscribe
    private void onPostClientTick(PostClientTick postClientTick) {
        if (client.getDraggedWidget() == null && wasDragging) {
            wasDragging = false;
            resetHiddenItems();
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        String menuTarget = Text.removeTags(event.getMenuTarget());
        String menuOption = Text.removeTags(event.getMenuOption());
        if (menuOption.contains("Cast") && menuTarget.contains(MagicAction.LOW_LEVEL_ALCHEMY.getName()) && !menuTarget.contains(
                "->")) {
            alchType = AlchType.Low;
        } else if (menuOption.contains("Cast") && menuTarget.contains(MagicAction.HIGH_LEVEL_ALCHEMY.getName()) && !menuTarget.contains(
                "->")) {
            alchType = AlchType.High;
        } else if (menuOption.contains("Magic") && isUsingExplorerRingAlch()) {
            if (client.getVarbitValue(VarbitID.LUMBRIDGE_ALCHEMY_HIGH) == 0) {
                alchType = AlchType.RingLow;
            } else {
                alchType = AlchType.RingHigh;
            }
            hideItems();
        } else if (!isUsingExplorerRingAlch()) {
            alchType = AlchType.None;
        }
        if (!isAlching()) {
            showHiddenItems();
        }
    }

    @Subscribe
    private void onScriptPostFired(ScriptPostFired event) {
        if (event.getScriptId() == ScriptID.INVENTORY_DRAWITEM) {
            if (isAlching()) {
                hideItems();
            } else {
                showHiddenItems();
            }
        }
    }

    @Subscribe
    private void onWidgetLoaded(WidgetLoaded widget) {
        if (widget.getGroupId() == InterfaceID.LUMBRIDGE_ALCHEMY) {
            if (client.getVarbitValue(VarbitID.LUMBRIDGE_ALCHEMY_HIGH) == 0) {
                alchType = AlchType.RingLow;
            } else {
                alchType = AlchType.RingHigh;
            }
            hideItems();
        }
    }

    @Subscribe
    private void onVarClientIntChanged(VarClientIntChanged varClientIntChanged) {
        if (varClientIntChanged.getIndex() == VarClientID.TOPLEVEL_PANEL && isUsingExplorerRingAlch()) {
            int tabId = client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL);
            if (tabId == 3) { // Inventory
                showHiddenItems();
            } else if (tabId == 6) { // Magic Spellbook (This is where the Explorer Ring Alch takes place)
                hideItems();
            }
        }
    }

    @Subscribe
    private void onVarbitChanged(VarbitChanged varbitChanged) {
        if (varbitChanged.getVarbitId() == VarbitID.LUMBRIDGE_ALCHEMY_HIGH && isAlching()) {
            if (varbitChanged.getValue() == 0) {
                alchType = AlchType.RingLow;
            } else {
                alchType = AlchType.RingHigh;
            }
            resetHiddenItems();
        }
    }

    @Subscribe
    private void onWidgetDrag(WidgetDrag widgetDrag) {
        if (isAlching()) {
            this.wasDragging = true;
        }
    }

    @Subscribe
    private void onWidgetClosed(WidgetClosed widget) {
        if (widget.getGroupId() == InterfaceID.LUMBRIDGE_ALCHEMY) {
            alchType = AlchType.None;
            showHiddenItems();
        }
    }

    @Subscribe
    private void onItemContainerChanged(ItemContainerChanged event) {
        ItemContainer container = event.getItemContainer();
        if (container.getId() == InventoryID.INV && isUsingExplorerRingAlch()) {
            hideItems();
        }
    }

    private void hideItems() {
        queueSingleTask(TaskName.HideItems, this::_hideItemsTask);
    }

    private void _hideItemsTask() {
        Widget[] inventoryItems;
        if (isUsingExplorerRingAlch()) {
            Widget alchInventory = client.getWidget(InterfaceID.LumbridgeAlchemy.ITEMS);
            if (alchInventory == null) {
                return;
            }
            inventoryItems = alchInventory.getChildren();
        } else {
            Widget inventory = client.getWidget(InterfaceID.Inventory.ITEMS);
            if (inventory == null) {
                return;
            }
            inventoryItems = inventory.getChildren();
        }
        assert inventoryItems != null;
        if (inventoryItems.length == 0) {
            return;
        }

        double minAlchPriceRatio = config.minAlchRatio();
        double alchPriceMargin = config.alchValueMargin();
        double runeCost = config.includeRuneCost() ? itemManager.getItemPrice(ItemID.FIRERUNE) * 5 + itemManager.getItemPrice(
                ItemID.NATURERUNE) : 0;
        if (isUsingExplorerRingAlch() || isAlchCastPoweredByExplorerRing()) {
            runeCost = 0;
        }
        for (Widget inventoryItem : inventoryItems) {
            int itemId = inventoryItem.getItemId();
            ItemComposition itemComposition = itemManager.getItemComposition(itemId);
            String name = Text.removeTags(itemComposition.getName()).toLowerCase();
            if (excludedItems.contains(itemId)) {
                continue;
            }
            if (inventoryItem.isHidden()) {
                continue;
            }
            if (denyList.contains(name)) {
                inventoryItem.setHidden(true);
                hiddenItems.add(inventoryItem);
                continue;
            }
            if (allowList.contains(name)) {
                continue;
            }
            int itemPrice = itemComposition.getPrice();
            int alchPrice = (alchType == AlchType.Low || alchType == AlchType.RingLow) ? (int) (itemPrice * 0.4) : (int) (itemPrice * 0.6);
            int geValue = (int) (itemManager.getItemPrice(itemId) * 0.98); // Account for GE tax
            int minAlchPrice = (int) (geValue * minAlchPriceRatio + alchPriceMargin + runeCost);
            // For some reason, noted items are tagged as untradeable, so check both versions
            boolean hasNotedVersion = itemComposition.getLinkedNoteId() != -1;
            boolean isTradeable = itemComposition.isTradeable() || (hasNotedVersion && itemManager.getItemComposition(itemComposition.getLinkedNoteId()).isTradeable());
            boolean shouldHide = false;
            if (!isTradeable && config.hideUntradeables()) {
                shouldHide = true;
            }
            if (alchPrice <= minAlchPrice) {
                shouldHide = true;
            }
            if (alchPrice < config.minAlchValue()) {
                shouldHide = true;
            }
            if (shouldHide) {
                inventoryItem.setHidden(true);
                hiddenItems.add(inventoryItem);
            }
        }
    }

    private void showHiddenItems() {
        if (hiddenItems.isEmpty()) {
            return;
        }
        queueSingleTask(TaskName.ShowHiddenItems, this::_showHiddenItemsTask);
    }

    private void _showHiddenItemsTask() {
        if (hiddenItems.isEmpty()) {
            return;
        }

        for (Widget inventoryItem : hiddenItems) {
            inventoryItem.setHidden(false);
        }
    }

    private void loadLists() {
        allowList = Text.fromCSV(config.getAllowedItems().toLowerCase());
        denyList = Text.fromCSV(config.getDeniedItems().toLowerCase());
    }

    private void resetHiddenItems() {
        queueSingleTask(TaskName.ResetHiddenItems, this::_resetHiddenItemsTask);
    }

    private void _resetHiddenItemsTask() {
        _showHiddenItemsTask();
        _hideItemsTask();
    }

    private void queueSingleTask(TaskName taskName, Runnable task) {
        if (tasksQueued.contains(taskName)) {
            return;
        }
        clientThread.invokeAtTickEnd(() -> {
            tasksQueued.remove(taskName);
            task.run();
        });
        tasksQueued.add(taskName);
    }

    private boolean isAlchCastPoweredByExplorerRing() {
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
        // If there are alch runes in the inventory, those will be used first
        if (containsAlchRunes(inventory, equipment)) {
            return false;
        }
        if (containsExplorerRing(inventory) || containsExplorerRing(equipment)) {
            return client.getVarbitValue(VarbitID.LUMBRIDGE_FREE_ALCHS) < 30;
        }
        return false;
    }

    private boolean containsAlchRunes(@Nullable ItemContainer inventory, @Nullable ItemContainer equipment) {
        if (inventory == null) {
            return false;
        }
        int fireRunes = 0;
        int natureRunes = 0;

        if (inventory.contains(ItemID.BH_RUNE_POUCH) || inventory.contains(ItemID.BH_RUNE_POUCH_TROUVER) || inventory.contains(ItemID.DIVINE_RUNE_POUCH) || inventory.contains(ItemID.DIVINE_RUNE_POUCH_TROUVER)) {
            int[] runeAmounts = {
                    VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3, VarbitID.RUNE_POUCH_QUANTITY_4,
                    VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6
            };
            int[] runeIndices = {
                    VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3, VarbitID.RUNE_POUCH_TYPE_4,
                    VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6
            };
            int[] fireIds = {4, 18, 19, 20, 22};
            int natureId = 10;
            for (int i = 0; i < runeIndices.length; i++) {
                int indexId = client.getVarbitValue(runeIndices[i]);
                if (IntStream.of(fireIds).anyMatch(x -> x == indexId)) {
                    fireRunes += client.getVarbitValue(runeAmounts[i]);
                } else if (indexId == natureId) {
                    natureRunes += client.getVarbitValue(runeAmounts[i]);
                }
            }
        }
        if (fireRunes <= 5) {
            int[] fireRuneAlternatives = {
                    ItemID.FIRERUNE,
                    ItemID.LAVARUNE,
                    ItemID.STEAMRUNE,
                    ItemID.SMOKERUNE
            };
            for (int itemId : fireRuneAlternatives) {
                if (inventory.contains(itemId)) {
                    int quantity = Objects.requireNonNull(inventory.getItem(inventory.find(itemId))).getQuantity();
                    fireRunes += quantity;
                }
            }
        }
        if (natureRunes <= 1) {
            if (inventory.contains(ItemID.NATURERUNE)) {
                int quantity = Objects.requireNonNull(inventory.getItem(inventory.find(ItemID.NATURERUNE))).getQuantity();
                natureRunes += quantity;
            }
        }
        if (fireRunes >= 5 && natureRunes >= 1) {
            return true;
        }
        if (equipment == null) {
            return false;
        }
        int[] fireStaffAlternatives = {
                ItemID.STAFF_OF_FIRE,
                ItemID.FIRE_BATTLESTAFF,
                ItemID.MYSTIC_FIRE_STAFF,
                ItemID.LAVA_BATTLESTAFF,
                ItemID.LAVA_BATTLESTAFF_PRETTY,
                ItemID.MYSTIC_LAVA_STAFF,
                ItemID.MYSTIC_LAVA_STAFF_PRETTY,
                ItemID.SMOKE_BATTLESTAFF,
                ItemID.MYSTIC_SMOKE_BATTLESTAFF,
                ItemID.STEAM_BATTLESTAFF,
                ItemID.STEAM_BATTLESTAFF_PRETTY,
                ItemID.MYSTIC_STEAM_BATTLESTAFF,
                ItemID.MYSTIC_STEAM_BATTLESTAFF_PRETTY,
                ItemID.TOME_OF_FIRE
        };
        for (int itemId : fireStaffAlternatives) {
            if (equipment.contains(itemId)) {
                fireRunes += 999;
            }
        }
        if (equipment.contains(ItemID.NATURE_STAFF_CHARGED)) {
            natureRunes += 999;
        }
        return fireRunes >= 5 && natureRunes >= 1;
    }

    private boolean containsExplorerRing(@Nullable ItemContainer container) {
        if (container == null) {
            return false;
        }
        return container.contains(ItemID.LUMBRIDGE_RING_EASY) || container.contains(ItemID.LUMBRIDGE_RING_MEDIUM) || container.contains(
                ItemID.LUMBRIDGE_RING_HARD) || container.contains(ItemID.LUMBRIDGE_RING_ELITE);
    }

    private boolean isAlching() {
        return alchType != AlchType.None;
    }

    private boolean isUsingExplorerRingAlch() {
        return alchType == AlchType.RingLow || alchType == AlchType.RingHigh;
    }
}
