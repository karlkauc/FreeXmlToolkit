# Patched Flowless 0.7.4

Sources of [Flowless](https://github.com/FXMisc/Flowless) 0.7.4 (`org.fxmisc.flowless:flowless:0.7.4`,
`flowless-0.7.4-sources.jar` from Maven Central, SHA-1 `32f2e5ed2aa35e8e8613fcab3c94c61674709aa2`), BSD 2-Clause
License (`LICENSE`, © 2014 Tomas Mikula). The jar is excluded from RichTextFX's dependencies in
`build.gradle.kts`; these sources are compiled into the application instead (`sourceSets.main.java.srcDir`).
Every change is marked with `FreeXmlToolkit patch` in the code.

## Why

RichTextFX folds lines by giving them the paragraph style `collapse`; a collapsed paragraph has height 0.
Flowless fills the viewport cell by cell until it is covered, so it built a cell for **every** hidden line -
folding `<Funds>` in a FundsXML file (24,450 lines) built 24,000 cells (and their line numbers, fold arrows and
highlighted text) and froze the application for minutes; `CellListManager.presentCellsChanged` made it quadratic.

## Changes

1. **`HiddenItems`** (new, public): which items take no space and get no cell, plus optional counts for the
   scroll estimates and run boundaries to jump over a hidden block. `HiddenItems.NONE` = original behaviour.
2. **`HiddenItemsState`** (new): the hidden items of one flow, shared by `Navigator` and `SizeTracker`, with a
   revision that invalidates the estimates.
3. **`VirtualFlow`**: `setHiddenItems(HiddenItems)`, `hiddenItemsChanged()`; `jumpToAbsolutePosition` maps a
   pixel offset to the n-th *shown* item (`itemIndexOfShown`).
4. **`Navigator`**: the fill loops (`fillForwardFrom0`, `fillBackwardFrom0`) skip hidden items without creating
   a cell (jumping over a whole hidden run) and return the last item that got a cell; the anchor of a layout
   (`visit(StartOffStart|EndOffEnd|MinDistanceTo)`) is moved to the nearest shown item; the crop and shift
   loops in `fillViewportFrom` / `shiftCellsTowardsGround` tolerate items without a cell.
5. **`SizeTracker`**: the total length and the offset estimate leave hidden items out; `avoidFalseInvalidations`
   uses `Val.create(…, EventStreams.changesOf(src))` instead of an anonymous `ValBase`, which no longer compiles
   against JavaFX ≥ 19 (`Val.flatMap` and `ObservableValue.flatMap` clash) - same behaviour.

The user is `controls/v2/editor/managers/FoldingManagerV2` (installs the hidden items of a code area and keeps
the caret out of folded blocks). Tests: `FoldingLargeRegionTest`, `FoldingManagerV2TextPreservationTest`.

## Updating

When RichTextFX moves to another Flowless version: diff the new sources against this copy's original
(the sources jar above), re-apply the marked changes, keep the tests green.
