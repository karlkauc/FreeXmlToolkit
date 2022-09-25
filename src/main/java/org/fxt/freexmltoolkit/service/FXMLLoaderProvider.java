package org.fxt.freexmltoolkit.service;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import javafx.fxml.FXMLLoader;

public class FXMLLoaderProvider implements Provider<FXMLLoader> {

    @Inject
    Injector injector;

    @Override
    public FXMLLoader get() {
        System.out.println("injector = " + injector);
        FXMLLoader loader = new FXMLLoader();
        loader.setControllerFactory(p -> injector.getInstance(p));
        return loader;
    }

}
