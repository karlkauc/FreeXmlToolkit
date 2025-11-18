package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents an XPath context in an XML document.
 * Contains the element hierarchy from root to the current position.
 *
 * <p>This is an immutable value object.</p>
 */
public class XPathContext {

    private final List<String> elementStack;
    private final String xpath;

    /**
     * Creates a new XPath context.
     *
     * @param elementStack the stack of elements from root to current position
     */
    public XPathContext(List<String> elementStack) {
        this.elementStack = Collections.unmodifiableList(new ArrayList<>(elementStack));
        this.xpath = buildXPath(elementStack);
    }

    /**
     * Builds an XPath string from the element stack.
     *
     * @param elementStack the element stack
     * @return the XPath string
     */
    private String buildXPath(List<String> elementStack) {
        if (elementStack.isEmpty()) {
            return "/";
        }
        StringBuilder sb = new StringBuilder();
        for (String element : elementStack) {
            sb.append('/').append(element);
        }
        return sb.toString();
    }

    /**
     * Gets the element stack.
     *
     * @return the unmodifiable list of elements
     */
    public List<String> getElementStack() {
        return elementStack;
    }

    /**
     * Gets the XPath as a string.
     *
     * @return the XPath string (e.g., "/root/parent/child")
     */
    public String getXPath() {
        return xpath;
    }

    /**
     * Gets the depth of the XPath (number of elements).
     *
     * @return the depth
     */
    public int getDepth() {
        return elementStack.size();
    }

    /**
     * Gets the parent element name.
     *
     * @return the parent element, or null if at root level
     */
    public String getParentElement() {
        if (elementStack.size() < 2) {
            return null;
        }
        return elementStack.get(elementStack.size() - 2);
    }

    /**
     * Gets the current (last) element name.
     *
     * @return the current element, or null if stack is empty
     */
    public String getCurrentElement() {
        if (elementStack.isEmpty()) {
            return null;
        }
        return elementStack.get(elementStack.size() - 1);
    }

    /**
     * Checks if this XPath is at the root level.
     *
     * @return true if at root, false otherwise
     */
    public boolean isAtRoot() {
        return elementStack.isEmpty();
    }

    /**
     * Creates a new XPath context with an additional element pushed onto the stack.
     *
     * @param element the element to push
     * @return a new XPath context
     */
    public XPathContext push(String element) {
        List<String> newStack = new ArrayList<>(elementStack);
        newStack.add(element);
        return new XPathContext(newStack);
    }

    /**
     * Creates a new XPath context with the last element popped from the stack.
     *
     * @return a new XPath context, or this if stack is empty
     */
    public XPathContext pop() {
        if (elementStack.isEmpty()) {
            return this;
        }
        List<String> newStack = new ArrayList<>(elementStack);
        newStack.remove(newStack.size() - 1);
        return new XPathContext(newStack);
    }

    @Override
    public String toString() {
        return xpath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XPathContext that = (XPathContext) o;
        return Objects.equals(xpath, that.xpath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(xpath);
    }
}
