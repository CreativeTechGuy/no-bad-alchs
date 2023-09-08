package com.creativetechguy;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
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
import java.util.HashSet;
import java.util.Objects;

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

    @Provides
    NoBadAlchsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(NoBadAlchsConfig.class);
    }

    @Override
    protected void shutDown() throws Exception {
        showHiddenItems();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals(NoBadAlchsConfig.GROUP_NAME)) {
            return;
        }
        if (isAlching()) {
            resetHiddenItems();
        }
    }

    @Subscribe()
    public void onMenuOptionClicked(MenuOptionClicked event) {
        String menuTarget = Text.removeTags(event.getMenuTarget());
        String menuOption = Text.removeTags(event.getMenuOption());
        if (menuOption.equals("Cast") && Objects.equals(menuTarget, MagicAction.LOW_LEVEL_ALCHEMY.getName())) {
            alchType = AlchType.Low;
        } else if (menuOption.equals("Cast") && Objects.equals(menuTarget, MagicAction.HIGH_LEVEL_ALCHEMY.getName())) {
            alchType = AlchType.High;
        } else if (menuOption.equals("Magic") && isUsingExplorerRingAlch()) {
            if (client.getVarbitValue(Varbits.EXPLORER_RING_ALCHTYPE) == 0) {
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
        if (widget.getGroupId() == WidgetInfo.EXPLORERS_RING_ALCH_INVENTORY.getGroupId()) {
            if (client.getVarbitValue(Varbits.EXPLORER_RING_ALCHTYPE) == 0) {
                alchType = AlchType.RingLow;
            } else {
                alchType = AlchType.RingHigh;
            }
            hideItems();
        }
    }

    @Subscribe
    private void onVarClientIntChanged(VarClientIntChanged varClientIntChanged) {
        if (varClientIntChanged.getIndex() == VarClientInt.INVENTORY_TAB && isUsingExplorerRingAlch()) {
            int tabId = client.getVarcIntValue(VarClientInt.INVENTORY_TAB);
            if (tabId == 3) { // Inventory
                showHiddenItems();
            } else if (tabId == 6) { // Magic Spellbook (This is where the Explorer Ring Alch takes place)
                hideItems();
            }
        }
    }

    @Subscribe
    private void onVarbitChanged(VarbitChanged varbitChanged) {
        if (varbitChanged.getVarbitId() == Varbits.EXPLORER_RING_ALCHTYPE && isAlching()) {
            if (varbitChanged.getValue() == 0) {
                alchType = AlchType.RingLow;
            } else {
                alchType = AlchType.RingHigh;
            }
            resetHiddenItems();
        }
    }

    @Subscribe
    private void onWidgetClosed(WidgetClosed widget) {
        if (widget.getGroupId() == WidgetInfo.EXPLORERS_RING_ALCH_INVENTORY.getGroupId()) {
            alchType = AlchType.None;
            showHiddenItems();
        }
    }

    @Subscribe
    private void onItemContainerChanged(ItemContainerChanged event) {
        ItemContainer container = event.getItemContainer();
        if (container.getId() == InventoryID.INVENTORY.getId() && isUsingExplorerRingAlch()) {
            hideItems();
        }
    }

    private void hideItems() {
        queueSingleTask(TaskName.HideItems, this::_hideItemsTask);
    }

    private void _hideItemsTask() {
        Widget[] inventoryItems;
        if (isUsingExplorerRingAlch()) {
            Widget alchInventory = client.getWidget(WidgetInfo.EXPLORERS_RING_ALCH_INVENTORY);
            if (alchInventory == null) {
                return;
            }
            inventoryItems = alchInventory.getChildren();
        } else {
            Widget inventory = client.getWidget(WidgetInfo.INVENTORY);
            if (inventory == null) {
                return;
            }
            inventoryItems = inventory.getChildren();
        }
        if (inventoryItems.length == 0) {
            return;
        }

        double minAlchPriceRatio = config.minAlchRatio();
        double alchPriceMargin = config.alchValueMargin();
        double runeCost = config.includeRuneCost() ? itemManager.getItemPrice(ItemID.FIRE_RUNE) * 5 + itemManager.getItemPrice(ItemID.NATURE_RUNE) : 0;
        if (isUsingExplorerRingAlch() || isAlchCastPoweredByExplorerRing()) {
            runeCost = 0;
        }
        for (Widget inventoryItem : inventoryItems) {
            if (inventoryItem.getItemId() == NullItemID.NULL_6512) {
                continue;
            }
            if (inventoryItem.isHidden()) {
                continue;
            }
            int itemPrice = itemManager.getItemComposition(inventoryItem.getItemId()).getPrice();
            int alchPrice = (alchType == AlchType.Low || alchType == AlchType.RingLow) ? (int) (itemPrice * 0.4) : (int) (itemPrice * 0.6);
            int geValue = (int) (itemManager.getItemPrice(inventoryItem.getItemId()) * 0.99); // Account for GE tax
            int minAlchPrice = (int) (geValue * minAlchPriceRatio + alchPriceMargin + runeCost);
            boolean shouldHide = false;
            if (geValue == 0 && config.hideUntradeables()) {
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
        // We are naively assuming that if there's an explorer ring in the inventory, it still has charges. I don't want to track charges for this edge case.
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (containsExplorerRing(inventory)) {
            return true;
        }
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        if (containsExplorerRing(equipment)) {
            return true;
        }
        return false;
    }

    private boolean containsExplorerRing(@Nullable ItemContainer container) {
        if (container == null) {
            return false;
        }
        return container.contains(ItemID.EXPLORERS_RING_1) || container.contains(ItemID.EXPLORERS_RING_2) || container.contains(
                ItemID.EXPLORERS_RING_3) || container.contains(ItemID.EXPLORERS_RING_4);
    }

    private boolean isAlching() {
        return alchType != AlchType.None;
    }

    private boolean isUsingExplorerRingAlch() {
        return alchType == AlchType.RingLow || alchType == AlchType.RingHigh;
    }
}
