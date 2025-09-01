package com.creativetechguy;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(NoBadAlchsConfig.GROUP_NAME)
public interface NoBadAlchsConfig extends Config {
    static String GROUP_NAME = "no-bad-alchs";

    @ConfigItem(
            keyName = "minAlchRatio",
            name = "Min Alch Ratio",
            description = "Multiplied by the item's GE value. If the alch value is lower than the result, the item cannot be alched.",
            position = 1
    )
    default double minAlchRatio() {
        return 0.99;
    }

    @Range(min = Integer.MIN_VALUE)
    @ConfigItem(
            keyName = "alchValueMargin",
            name = "Alch Value Margin",
            description = "Added to the GE value.",
            position = 2
    )
    default int alchValueMargin() {
        return 0;
    }

    @Range(min = 0)
    @ConfigItem(
            keyName = "minAlchValue",
            name = "Min Alch Value",
            description = "Hide items with alch values below this",
            position = 3
    )
    default int minAlchValue() {
        return 0;
    }

    @ConfigItem(
            keyName = "includeRuneCost",
            name = "Include Rune Cost",
            description = "Automatically adds the cost of 1 nature and 5 fire runes to the GE value.",
            position = 4
    )
    default boolean includeRuneCost() {
        return true;
    }

    @ConfigItem(
            keyName = "hideUntradeables",
            name = "Hide Untradeables",
            description = "Items that RuneLite marks as untradeable will be hidden.",
            position = 5
    )
    default boolean hideUntradeables() {
        return true;
    }

    @ConfigItem(
            keyName = "allowedItems",
            name = "Allowed items",
            description = "Items in this list will always be shown.",
            position = 6
    )
    default String getAllowedItems() {
        return "";
    }

    @ConfigItem(
            keyName = "deniedItems",
            name = "Denied items",
            description = "Items in this list will always be hidden.",
            position = 7
    )
    default String getDeniedItems() {
        return "";
    }

}
