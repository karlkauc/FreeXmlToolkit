# Sample FreeXmlToolkit plugin

A complete plugin for "order" documents: an activity with a side panel, a Tools menu item, a validator,
a hint bar and a stylesheet. See the guide: https://karlkauc.github.io/FreeXmlToolkit/plugin-development/

```bash
gradle jar -PfxtJar=/opt/freexmltoolkit/lib/app/FreeXmlToolkit.jar   # path of your installation
cp build/libs/orders-plugin.jar ~/.freeXmlToolkit/plugins/
```

Restart FreeXmlToolkit; the plugin appears in Settings → Extensions → PLUGINS and the "Orders" activity
in the Activity Bar.
