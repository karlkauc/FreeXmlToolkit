package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.domain.GenerationProfile;
import org.fxt.freexmltoolkit.domain.GenerationStrategy;
import org.fxt.freexmltoolkit.domain.XPathInfo;
import org.fxt.freexmltoolkit.domain.XPathRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The advanced sample-data dialog turns the schema's XPaths into editable per-XPath generation rules
 * (strategy + config) plus batch options, and yields a {@link GenerationProfile}.
 */
@ExtendWith(ApplicationExtension.class)
class ProfiledSampleDialogTest {

    private static final List<XPathInfo> XPATHS = List.of(
            new XPathInfo("/order/@id", "xs:string", false, true, 0),
            new XPathInfo("/order/country", "xs:string", true, false, 1));

    private ProfiledSampleDialog dialog;

    @Start
    void start(Stage stage) {
        dialog = new ProfiledSampleDialog(XPATHS);
    }

    @Test
    void buildsProfileWithPerXPathRulesAndBatchOptions() {
        GenerationProfile profile = WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            // one rule row per extracted XPath
            assertEquals(2, dialog.getRows().size());
            // set a FIXED rule on the country element
            var countryRow = dialog.getRows().stream()
                    .filter(r -> r.getXpath().endsWith("country")).findFirst().orElseThrow();
            countryRow.strategyProperty().set(GenerationStrategy.FIXED);
            countryRow.configProperty().set("AT");
            // batch options
            dialog.setBatch(5, "order_{seq:3}.xml");
            return dialog.currentProfile();
        });

        // AUTO rows produce no rule; only the explicitly-set FIXED row becomes a rule.
        List<XPathRule> rules = profile.getRules();
        assertEquals(1, rules.size(), "only the non-AUTO row yields a rule");
        XPathRule rule = rules.get(0);
        assertTrue(rule.getXpath().endsWith("country"));
        assertEquals(GenerationStrategy.FIXED, rule.getStrategy());
        assertEquals("AT", rule.getConfigValue("value"));

        assertEquals(5, profile.getBatchCount());
        assertEquals("order_{seq:3}.xml", profile.getFileNamePattern());
    }

    @Test
    void applyProfileLoadsRulesAndBatchIntoTheRows() {
        GenerationProfile saved = new GenerationProfile("saved");
        saved.setBatchCount(7);
        saved.setFileNamePattern("x_{seq:2}.xml");
        saved.addRule(new XPathRule("/order/country", GenerationStrategy.FIXED,
                java.util.Map.of("value", "DE")));

        GenerationProfile rebuilt = WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            dialog.applyProfile(saved);
            var countryRow = dialog.getRows().stream()
                    .filter(r -> r.getXpath().endsWith("country")).findFirst().orElseThrow();
            assertEquals(GenerationStrategy.FIXED, countryRow.strategyProperty().get());
            assertEquals("DE", countryRow.configProperty().get());
            return dialog.currentProfile();
        });

        // Round-trips: applying then re-building yields the same rule + batch options.
        assertEquals(7, rebuilt.getBatchCount());
        assertEquals("x_{seq:2}.xml", rebuilt.getFileNamePattern());
        assertEquals(1, rebuilt.getRules().size());
        assertEquals("DE", rebuilt.getRules().get(0).getConfigValue("value"));
    }
}
