package org.fxt.freexmltoolkit.controls.v2.model;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Model representing an XSD group (sequence, choice, all).
 *
 * @since 2.0
 */
public class XsdGroupModel {

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final String id;

    private String name;
    private GroupType groupType;
    private int minOccurs = 1;
    private int maxOccurs = 1;

    private final List<XsdElementModel> elements = new ArrayList<>();

    public enum GroupType {
        SEQUENCE,
        CHOICE,
        ALL
    }

    public XsdGroupModel(String id, String name, GroupType groupType) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        this.name = name;
        this.groupType = Objects.requireNonNull(groupType, "Group type cannot be null");
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public GroupType getGroupType() {
        return groupType;
    }

    public void setGroupType(GroupType groupType) {
        this.groupType = groupType;
    }

    public int getMinOccurs() {
        return minOccurs;
    }

    public void setMinOccurs(int minOccurs) {
        this.minOccurs = minOccurs;
    }

    public int getMaxOccurs() {
        return maxOccurs;
    }

    public void setMaxOccurs(int maxOccurs) {
        this.maxOccurs = maxOccurs;
    }

    public List<XsdElementModel> getElements() {
        return Collections.unmodifiableList(elements);
    }

    public void addElement(XsdElementModel element) {
        elements.add(element);
    }

    public void removeElement(XsdElementModel element) {
        elements.remove(element);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    @Override
    public String toString() {
        return "XsdGroupModel{name='" + name + "', type=" + groupType + '}';
    }
}
