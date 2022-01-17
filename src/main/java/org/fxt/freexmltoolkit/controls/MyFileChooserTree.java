package org.fxt.freexmltoolkit.controls;

import javafx.scene.layout.HBox;

public class MyFileChooserTree extends HBox {
    private HBox content;

    public void MyFileChooserTree() {
        content = new HBox();
    }



    public HBox getContent() {
        return content;
    }

}
