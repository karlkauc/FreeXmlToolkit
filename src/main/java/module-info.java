module org.fxt.freexmltoolkit {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires Saxon.HE;
    requires org.fxmisc.richtext;
    requires flowless;
//     requires fxribbon;



    opens org.fxt.freexmltoolkit to javafx.fxml;
    exports org.fxt.freexmltoolkit;
}