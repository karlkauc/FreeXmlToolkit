module org.fxt.freexmltoolkit {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires Saxon.HE;
    requires org.fxmisc.richtext;
    requires flowless;
    // requires org.slf4j;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j.slf4j;
    requires java.desktop;

    requires org.apache.commons.lang3;

    opens org.fxt.freexmltoolkit to javafx.fxml;
    exports org.fxt.freexmltoolkit;

}