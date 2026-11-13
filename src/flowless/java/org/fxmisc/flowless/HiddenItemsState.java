package org.fxmisc.flowless;

import org.reactfx.value.Val;
import org.reactfx.value.Var;

/**
 * FreeXmlToolkit patch (see {@code src/flowless/PATCHES.md}): the {@link HiddenItems} of one
 * {@link VirtualFlow}, shared by its {@link Navigator} and {@link SizeTracker}, with a revision that
 * invalidates the scroll estimates when the hidden items change.
 */
final class HiddenItemsState {

    private HiddenItems items = HiddenItems.NONE;
    private final Var<Integer> revision = Var.newSimpleVar(0);

    HiddenItems get() {
        return items;
    }

    void set(HiddenItems hiddenItems) {
        items = hiddenItems == null ? HiddenItems.NONE : hiddenItems;
        changed();
    }

    void changed() {
        revision.setValue(revision.getValue() + 1);
    }

    /** @return a value that changes whenever the hidden items may have changed */
    Val<Integer> revision() {
        return revision;
    }

    boolean isHidden(int itemIndex) {
        return items != HiddenItems.NONE && items.isHidden(itemIndex);
    }

    int hiddenRunEnd(int itemIndex) {
        return items.hiddenRunEnd(itemIndex);
    }

    int hiddenRunStart(int itemIndex) {
        return items.hiddenRunStart(itemIndex);
    }

    boolean isActive() {
        return items != HiddenItems.NONE;
    }
}
