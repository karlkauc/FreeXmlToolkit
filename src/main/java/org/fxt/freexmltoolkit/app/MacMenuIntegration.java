package org.fxt.freexmltoolkit.app;

import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

import javafx.application.Platform;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCombination;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.shell.ShellMenuBar;

import de.jangassen.MenuToolkit;

/**
 * Turns the shell's menu bar into a native macOS menu: the bar moves into the system menu bar,
 * and NSMenuFX supplies what JavaFX cannot build itself — the application menu (About,
 * Settings…, Hide, Quit) and a Window menu.
 *
 * <p>Only this class references NSMenuFX, and it is only loaded on macOS. The title of the
 * application menu is not set here: macOS takes it from {@code CFBundleName} of the app bundle
 * (the packaged application shows "FreeXmlToolkit", a plain {@code java} launch shows "java").
 */
public final class MacMenuIntegration {

    private static final Logger logger = LogManager.getLogger(MacMenuIntegration.class);

    private static final String APP_NAME = "FreeXmlToolkit";

    private MacMenuIntegration() {
    }

    /**
     * Installs the native menu on macOS; does nothing elsewhere. Never throws: without the
     * native menu the application keeps its in-window menu bar.
     *
     * @param menuBar the shell's menu bar, already part of the shown scene
     * @return whether the native menu was installed
     */
    public static boolean install(ShellMenuBar menuBar) {
        return install(System.getProperty("os.name", ""), () -> {
            installNative(menuBar);
            return true;
        });
    }

    /**
     * @param osName    the {@code os.name} system property
     * @param installer installs the native menu
     * @return whether the native menu was installed
     */
    static boolean install(String osName, BooleanSupplier installer) {
        if (osName == null || !osName.toLowerCase().contains("mac")) {
            return false;
        }
        try {
            return installer.getAsBoolean();
        } catch (Exception | LinkageError e) {
            logger.warn("Could not install the native macOS menu: {}", e.toString());
            return false;
        }
    }

    private static void installNative(ShellMenuBar menuBar) {
        // Created first: if NSMenuFX cannot load, the bar is left untouched in the window.
        MenuToolkit toolkit = MenuToolkit.toolkit(Locale.ENGLISH); // all user-facing text is English
        Menu applicationMenu = new Menu(APP_NAME);
        Menu windowMenu = new Menu("Window");
        windowMenu.getItems().addAll(
                toolkit.createMinimizeMenuItem(),
                toolkit.createZoomMenuItem(),
                toolkit.createCycleWindowsItem(),
                new SeparatorMenuItem(),
                toolkit.createBringAllToFrontItem());
        MenuItem hide = toolkit.createHideMenuItem(APP_NAME);
        MenuItem hideOthers = toolkit.createHideOthersMenuItem();
        MenuItem showAll = toolkit.createUnhideAllMenuItem();

        // About, Check for Updates…, Settings… — the same items the in-window bar would show.
        List<MenuItem> applicationItems = menuBar.detachApplicationItems();
        // Native menus render no JavaFX icon nodes.
        applicationItems.forEach(item -> item.setGraphic(null));
        MenuItem quit = new MenuItem("Quit " + APP_NAME);
        quit.setAccelerator(KeyCombination.valueOf("Shortcut+Q"));
        quit.setOnAction(e -> Platform.exit());

        applicationMenu.getItems().addAll(
                applicationItems.get(0),
                applicationItems.get(1),
                new SeparatorMenuItem(),
                applicationItems.get(2),
                new SeparatorMenuItem(),
                hide, hideOthers, showAll,
                new SeparatorMenuItem(),
                quit);

        menuBar.getMenus().add(menuBar.getMenus().indexOf(menuBar.getHelpMenu()), windowMenu);
        menuBar.setUseSystemMenuBar(true);
        toolkit.setApplicationMenu(applicationMenu);
        toolkit.autoAddWindowMenuItems(windowMenu);
    }
}
