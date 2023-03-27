package com.creativetechguy;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemID;
import net.runelite.api.NullItemID;
import net.runelite.api.ScriptID;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Objects;

@Slf4j
@PluginDescriptor(
        name = "No Bad Alchs",
        description = "Prevents casting alchemy on items which give less than the GE value",
        configName = "no-bad-alchs"
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
    ArrayList<Widget> hiddenItems = new ArrayList<>();

    @Provides
    NoBadAlchsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(NoBadAlchsConfig.class);
    }

    @Override
    protected void shutDown() throws Exception {
        clientThread.invoke(this::showHiddenItems);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals("no-bad-alchs")) {
            return;
        }
        if (isAlching()) {
            clientThread.invoke(this::resetHiddenItems);
        }
    }

    @Subscribe()
    public void onMenuOptionClicked(MenuOptionClicked event) {
        String menuTarget = Text.removeTags(event.getMenuTarget());
        if (Objects.equals(event.getMenuOption(), "Cast")) {
            if (Objects.equals(menuTarget, "Low Level Alchemy")) {
                alchType = AlchType.Low;
            } else {
                alchType = AlchType.High;
            }
        } else {
            alchType = AlchType.None;
        }
        if (!isAlching()) {
            showHiddenItems();
        }
    }

    @Subscribe
    private void onScriptPostFired(ScriptPostFired event) {
        if (event.getScriptId() == ScriptID.INVENTORY_DRAWITEM && isAlching()) {
            hideItems();
        }
    }

    private void hideItems() {
        Widget inventory = client.getWidget(WidgetInfo.INVENTORY);
        if (inventory == null) {
            return;
        }
        double minAlchPriceRatio = config.minAlchRatio();
        double alchPriceMargin = config.alchValueMargin();
        double runeCost = config.includeRuneCost() ? itemManager.getItemPrice(ItemID.FIRE_RUNE) * 5 + itemManager.getItemPrice(ItemID.NATURE_RUNE) : 0;
        for (Widget inventoryItem : Objects.requireNonNull(inventory.getChildren())) {
            if (inventoryItem.getItemId() == NullItemID.NULL_6512) {
                continue;
            }
            int itemPrice = itemManager.getItemComposition(inventoryItem.getItemId()).getPrice();
            int alchPrice = alchType == AlchType.Low ? (int) (itemPrice * 0.4) : (int) (itemPrice * 0.6);
            int geValue = itemManager.getItemPrice(inventoryItem.getItemId());
            int minAlchPrice = (int) (geValue * minAlchPriceRatio + alchPriceMargin + runeCost);
            boolean shouldHide = false;
            if (geValue == 0 && config.hideUntradeables()) {
                shouldHide = true;
            }
            if (alchPrice <= minAlchPrice) {
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

        for (int i = hiddenItems.size() - 1; i >= 0; i--) {
            Widget inventoryItem = hiddenItems.get(i);
            inventoryItem.setHidden(false);
            hiddenItems.remove(i);
        }
    }

    private void resetHiddenItems() {
        showHiddenItems();
        hideItems();
    }

    private boolean isAlching() {
        return alchType != AlchType.None;
    }
}
