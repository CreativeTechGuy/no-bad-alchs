# No Bad Alchs

![Demo](./demo.gif)

Prevents casting alchemy on items which give less than the GE value based on configurable thresholds.

## Config Options

* Min Alch Ratio - A number (default 0.99) to be multiplied by the item's GE value. If the alch value is lower than the
  result, the
  item cannot be alched and will be hidden when alching.
    * Example: 1.5 means you only want to alch items which have an alch value 150% of the GE value.
    * Example: 0.5 means you are willing to alch items which have an alch value of 50% of the GE value.
* Alch Value Margin - A number (default 0) to be added to the GE value.
    * Example: 100 means the alch value must be at least 100 coins more than the GE value
    * Example: -250 means the alch value can be at most -250 coins less than the GE value
* Min Alch Value - A number (default 0) causing items with alch values lower than this to be always hidden.
* Include Rune Cost - Automatically adds the cost of 1 nature and 5 fire runes to the GE value.
* Hide Untradeables - Items that RuneLite marks as untradeable will be hidden.
* Allowed Items - Write an item's name in this list, and it will always be shown when alching.
    * Example: Digsite pendant(1), ring of dueling(1), rune arrow
* Denied Items - Write an item's name in this list, and it will never be shown. This overrides the Allowed list, so an
  item in both will be hidden.
    * Example: Dragon med helm, rune scimitar, lava battlestaff

Note: GE value automatically incorporates the GE tax deduction.

## Version History

* 1.2.3 - Fix inventory disappearing and plugin crashing
* 1.2.2 - Fix dragging an items incorrectly making all items visible
* 1.2.1 - Fix incorrectly marking noted items as untradeable
* 1.2.0 - Add allowlist/denylist config, fixes untradeable detection, updates GE tax percentage
* 1.1.4 - Fix keyboard support for swapping to inventory
* 1.1.3 - Check names of menu items loosely to increase compatibility with other plugins which change menu item names
* 1.1.2 - Exclude Mage Training Arena items
* 1.1.1 - Fixed rare error
* 1.1.0 - Add config for minimum alch value
* 1.0.0 - Now works with Explorer Ring Alch in all cases.
* 0.9.0 - Immediately re-show items as soon as alch is cast
* 0.8.1 - Fixed bug where all spells cast would hide inventory
* 0.8.0 - Initial RuneLite Release

## Known issues

* Doesn't work correctly if [Normal Ancient Teleports](https://runelite.net/plugin-hub/show/normal-ancient-teleports)
  plugin is enabled.
    * Disabling the `Normal Ancient Teleports` plugin fixes the `No Bad Alchs` behaviour.
