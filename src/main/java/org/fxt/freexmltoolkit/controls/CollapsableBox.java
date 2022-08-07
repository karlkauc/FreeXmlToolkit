package org.fxt.freexmltoolkit.controls;

import javafx.beans.DefaultProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

@DefaultProperty(value = "children")
public class CollapsableBox extends VBox {

    List<Node> components;
    VBox componentContainer, collapseContainer;

    @FXML
    private IntegerProperty myPrefWidth = new SimpleIntegerProperty();

    private int prefWidth = 300;

    public CollapsableBox() {
        HBox wrapper = new HBox();
        wrapper.setPrefHeight(this.prefWidth);

        System.out.println("myPrefWidth = " + myPrefWidth.getValue());

    }

    public void addComponent(Node n) {
        components.add(n);
    }

    @FXML
    public void setMyStartWidth(String i) {
        prefWidth = Integer.parseInt(i);
    }

    @Override
    public ObservableList<Node> getChildren() {
        return super.getChildren();
    }

    public final void setMyPrefWidth(Integer value) {
        myPrefWidth.setValue(value);
        this.setPrefWidth(myPrefWidth.getValue());
    }

    public final Integer getMyPrefWidth() {
        return myPrefWidth == null ? 0 : myPrefWidth.getValue();
    }

}
