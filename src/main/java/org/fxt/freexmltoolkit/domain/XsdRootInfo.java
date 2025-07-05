package org.fxt.freexmltoolkit.domain;

import java.util.List;

public record XsdRootInfo(String name, String documentation, List<String> childElementNames) {
}