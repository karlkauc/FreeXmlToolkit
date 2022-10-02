package org.fxt.freexmltoolkit.service;

import java.util.Properties;

public interface PropertiesService {
    Properties loadProperties();
    void saveProperties(Properties save);
}
