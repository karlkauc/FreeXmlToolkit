package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class BoundedPatternSamplerTest {

    @Test
    void samplesStayWithinTheLengthRange() {
        BoundedPatternSampler sampler = new BoundedPatternSampler("[a-z]+", new Random(1));
        for (int i = 0; i < 1000; i++) {
            String value = sampler.sample(5, 8);
            assertTrue(value.length() >= 5 && value.length() <= 8, value);
            assertTrue(value.matches("[a-z]+"), value);
        }
    }

    @Test
    void alternativesThatCannotReachTheMinimumLengthAreAvoided() {
        // UCI NITF_DeclassificationExemptionType (length 4): picking [DNIO] ended the walk at length 1, the sampler
        // returned null and the generator fell back to concatenating the alternatives ("X1 25X9I ")
        String pattern = "(X[1-8]( ){2})|25X[1-9]|[DNIO]|( ){4}";
        BoundedPatternSampler sampler = new BoundedPatternSampler(pattern, new Random(17));
        for (int i = 0; i < 2000; i++) {
            String value = sampler.sample(4, 4);
            assertNotNull(value);
            assertTrue(value.length() == 4 && value.matches(pattern), value);
        }
    }

    @Test
    void automatonOperatorsOfTheSamplerAreLiteralCharactersInXsdPatterns() {
        // KML atomEmailAddress .+@.+: '@' is the "any string" operator of dk.brics RegExp, so the sampler failed and
        // the fallback emitted "@"; '&', '~', '#' and '<' are XSD literals too
        String[] patterns = {".+@.+", "[a-z]+&[a-z]+", "~[0-9]{2}", "#[A-F0-9]{6}", "<[a-z]+>"};
        for (String pattern : patterns) {
            BoundedPatternSampler sampler = new BoundedPatternSampler(pattern, new Random(19));
            for (int i = 0; i < 200; i++) {
                String value = sampler.sample(1, 30);
                assertNotNull(value, pattern);
                assertTrue(value.matches(pattern), pattern + " -> " + value);
            }
        }
    }

    @Test
    void returnsNullWhenNoMemberFitsTheLengthRange() {
        assertNull(new BoundedPatternSampler("[a-z]{2}", new Random(1)).sample(5, 8));
    }

    @Test
    void patternWithRareAcceptingCharacterStaysShort() {
        // Generex.random recursed without a depth bound for this A-GRA pattern
        BoundedPatternSampler sampler = new BoundedPatternSampler(".+Z", new Random(7));
        for (int i = 0; i < 20_000; i++) {
            String value = sampler.sample(1, 50);
            assertTrue(value.length() >= 2 && value.length() <= 50 && value.endsWith("Z"), value);
        }
    }

    @Test
    void samplesContainOnlyCharactersAllowedInXml() {
        // UCI Timestamp/Epoch (.+Z): uniform picks from the whole Unicode range produced U+0005, U+001C and surrogates,
        // which made the generated documents not well-formed
        BoundedPatternSampler sampler = new BoundedPatternSampler(".+Z", new Random(11));
        for (int i = 0; i < 20_000; i++) {
            String value = sampler.sample(1, 50);
            value.chars().forEach(c -> assertTrue(isXmlCharacter(c), "U+" + Integer.toHexString(c) + " in sample"));
        }
    }

    @Test
    void samplesContainNoLineBreaksOrTabs() {
        // XTCE NameType [^.\[\]:/ \t]+: the transition for U+0000..U+001F offered only TAB, LF and CR as XML characters;
        // in an attribute value they are normalized to spaces, which the pattern forbids
        BoundedPatternSampler sampler = new BoundedPatternSampler("[^.\\[\\]:/ \\t]+", new Random(13));
        for (int i = 0; i < 20_000; i++) {
            String value = sampler.sample(1, 20);
            assertTrue(value.chars().noneMatch(c -> c == '\t' || c == '\n' || c == '\r'), value);
        }
    }

    @Test
    void negatedClassesPreferPrintableCharacters() {
        BoundedPatternSampler sampler = new BoundedPatternSampler("[^a]{5}", new Random(5));
        for (int i = 0; i < 500; i++) {
            String value = sampler.sample(5, 5);
            assertTrue(value.chars().allMatch(c -> c >= 0x21 && c <= 0x7E && c != 'a'), value);
        }
    }

    @Test
    void patternWithOnlyForbiddenCharactersYieldsNoSample() {
        assertNull(new BoundedPatternSampler("[\u0001-\u0008]+", new Random(1)).sample(1, 10));
    }

    private static boolean isXmlCharacter(int c) {
        return c == 0x9 || c == 0xA || c == 0xD || (c >= 0x20 && c <= 0xD7FF) || (c >= 0xE000 && c <= 0xFFFD);
    }

    @Test
    void predefinedCharacterClassesAreSupported() {
        BoundedPatternSampler sampler = new BoundedPatternSampler("\\d{3}-\\w{2}", new Random(3));
        for (int i = 0; i < 200; i++) {
            String value = sampler.sample(1, 50);
            assertTrue(value.matches("[0-9]{3}-[a-zA-Z_0-9]{2}"), value);
        }
    }

    @Test
    void largeRepetitionUpperBoundsAreCapped() {
        assertEquals("(.|x){0,256}", BoundedPatternSampler.capRepetitionBounds("(.|x){0,4096}"));
        assertEquals("[a-z]{1,256}x{3}\\{0,9999\\}", BoundedPatternSampler.capRepetitionBounds("[a-z]{1,100000}x{3}\\{0,9999\\}"));
        assertEquals("a{300,300}", BoundedPatternSampler.capRepetitionBounds("a{300,5000}"));
        assertEquals("b{2,}", BoundedPatternSampler.capRepetitionBounds("b{2,}"));
    }
}
