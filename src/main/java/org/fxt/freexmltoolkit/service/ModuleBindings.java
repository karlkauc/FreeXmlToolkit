package org.fxt.freexmltoolkit.service;

import com.google.inject.AbstractModule;

public class ModuleBindings extends AbstractModule {
    @Override
    protected void configure() {
        bind(XmlService.class).to(XmlServiceImpl.class).asEagerSingleton();
        bind(PropertiesService.class).to(PropertiesServiceImpl.class).asEagerSingleton();
    }
}
