package com.example.orders;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.plugin.DocumentValidator;
import org.fxt.freexmltoolkit.plugin.FxtPlugin;
import org.fxt.freexmltoolkit.plugin.PluginActivity;
import org.fxt.freexmltoolkit.plugin.PluginContext;
import org.fxt.freexmltoolkit.plugin.PluginMenuItem;
import org.fxt.freexmltoolkit.plugin.PluginUi;
import org.fxt.freexmltoolkit.plugin.XmlScan;

/**
 * A sample plugin for order documents ({@code <order>}): an activity with a side panel, a Tools menu
 * item, a validator, a hint bar when an order is opened, and its own stylesheet.
 */
public final class OrdersPlugin implements FxtPlugin {

    @Override
    public String id() {
        return "com.example.orders";
    }

    @Override
    public String name() {
        return "Orders (sample)";
    }

    @Override
    public String version() {
        return "1.0.0";
    }

    @Override
    public String description() {
        return "Checks order documents: positive quantities and a total that matches the lines.";
    }

    @Override
    public void register(PluginContext context) {
        OrderValidator validator = new OrderValidator();

        context.addActivity(new PluginActivity("orders", "Orders", "bi-cart", ui -> panel(ui, validator)));

        context.addMenuItem(new PluginMenuItem(PluginMenuItem.Menu.TOOLS, "orders-new", "New Order", "bi-cart-plus",
                ui -> ui.editorHost().openGeneratedDocument(SAMPLE,
                        org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.XML, "order.xml"),
                null));

        context.addValidator(validator);

        context.addDocumentListener((ui, path, content) -> {
            if (validator.accepts(content, null)) {
                Button show = new Button("Show the Orders panel");
                show.setOnAction(e -> {
                    ui.hideEditorBar();
                    ui.showActivity("orders");
                });
                ui.showEditorBar(new VBox(new Label("This is an order document."), show));
            }
        });

        context.addStylesheet(OrdersPlugin.class.getResource("orders.css").toExternalForm());
    }

    /** The side panel: created on the JavaFX thread the first time the activity is shown. */
    private static VBox panel(PluginUi ui, OrderValidator validator) {
        Label title = new Label("ORDERS");
        title.getStyleClass().add("orders-title");
        Label result = new Label("Open an order and click Check.");
        result.getStyleClass().add("orders-hint");
        Button check = new Button("Check Active Document");
        check.setOnAction(e -> {
            String text = ui.editorHost().getActiveText().orElse("");
            List<DocumentValidator.Problem> problems = validator.validate(text, null);
            result.setText(problems.isEmpty() ? "No problems." : problems.size() + " problem(s): "
                    + problems.getFirst().message());
        });
        VBox box = new VBox(title, check, result);
        box.getStyleClass().add("orders-panel");
        return box;
    }

    static final String SAMPLE = """
            <order id="A-1">
              <line><product>Pen</product><quantity>2</quantity><price>1.50</price></line>
              <line><product>Paper</product><quantity>1</quantity><price>4.00</price></line>
              <total>7.00</total>
            </order>
            """;

    /** The plugin's checks: run with every validation and from the panel. */
    static final class OrderValidator implements DocumentValidator {

        @Override
        public String name() {
            return "Orders";
        }

        @Override
        public boolean accepts(String content, String documentName) {
            return XmlScan.rootOf(content).map(root -> root[0].equals("order")).orElse(false);
        }

        @Override
        public List<Problem> validate(String content, String documentName) {
            List<Problem> problems = new ArrayList<>();
            XmlScan scan;
            try {
                scan = XmlScan.of(content);
            } catch (javax.xml.stream.XMLStreamException e) {
                return List.of(new Problem("error", 0, "Not well-formed: " + e.getMessage()));
            }
            java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
            for (XmlScan.Element line : scan.named("line")) {
                var inside = scan.within(line);
                var quantity = inside.stream().filter(e -> e.localName().equals("quantity")).findFirst();
                var price = inside.stream().filter(e -> e.localName().equals("price")).findFirst();
                if (quantity.isEmpty() || price.isEmpty()) {
                    problems.add(new Problem("error", line.line(), "A line needs a quantity and a price."));
                    continue;
                }
                java.math.BigDecimal q = new java.math.BigDecimal(quantity.get().text());
                if (q.signum() <= 0) {
                    problems.add(new Problem("error", quantity.get().line(), "The quantity must be positive."));
                }
                sum = sum.add(q.multiply(new java.math.BigDecimal(price.get().text())));
            }
            java.math.BigDecimal expected = sum;
            scan.named("total").stream().findFirst().ifPresent(total -> {
                if (new java.math.BigDecimal(total.text()).compareTo(expected) != 0) {
                    problems.add(new Problem("warning", total.line(), "The total is " + total.text()
                            + ", the lines add up to " + expected.toPlainString() + "."));
                }
            });
            return problems;
        }
    }
}
