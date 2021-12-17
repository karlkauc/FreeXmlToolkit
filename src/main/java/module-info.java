module org.fxt.freexmltoolkit {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires Saxon.HE;
    requires org.fxmisc.richtext;
    requires flowless;
    requires org.slf4j;

    requires org.eclipse.lemminx;
    requires org.eclipse.lsp4j;
    requires org.eclipse.lsp4j.generator;
    requires org.eclipse.lsp4j.jsonrpc;

    opens org.fxt.freexmltoolkit to javafx.fxml;
    exports org.fxt.freexmltoolkit;

}