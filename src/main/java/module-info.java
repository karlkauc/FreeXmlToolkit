module org.fxt.freexmltoolkit {
    requires javafx.controls;
    requires javafx.fxml;
    requires fxribbon;


    opens org.fxt.freexmltoolkit to javafx.fxml;
    exports org.fxt.freexmltoolkit;
}