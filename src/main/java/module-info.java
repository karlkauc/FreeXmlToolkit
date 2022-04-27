module org.fxt.freexmltoolkit {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires Saxon.HE;
    requires org.fxmisc.richtext;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j.slf4j;
    requires java.desktop;
    // requires org.xmlresolver.xmlresolver;

    requires org.apache.commons.lang3;
    requires org.apache.commons.io;
    requires com.google.guice;

    opens org.fxt.freexmltoolkit.controller;
    opens org.fxt.freexmltoolkit.service;

    exports org.fxt.freexmltoolkit;
    exports org.fxt.freexmltoolkit.controller;
    exports org.fxt.freexmltoolkit.controls;
}