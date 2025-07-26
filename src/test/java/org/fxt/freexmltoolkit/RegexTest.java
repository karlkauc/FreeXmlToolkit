package org.fxt.freexmltoolkit;

import com.github.curiousoddman.rgxgen.RgxGen;
import com.github.curiousoddman.rgxgen.iterators.StringIterator;
import com.mifmif.common.regex.Generex;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Optional;

public class RegexTest {

    public static final String EMAIL_PATTERN = "[_\\-a-zA-Z0-9\\.\\+]+@[a-zA-Z0-9](\\.?[\\-a-zA-Z0-9]*[a-zA-Z0-9])*";

    @Test
    void t() {
        Generex generex = new Generex(EMAIL_PATTERN);

        // Generate random String
        String randomStr = generex.random();
        System.out.println(randomStr);// a random value from the previous String list


        RgxGen rgxGen = RgxGen.parse(EMAIL_PATTERN);       // Create generator
        String s = rgxGen.generate();                                        // Generate new random value
        Optional<BigInteger> estimation = rgxGen.getUniqueEstimation();      // The estimation (not accurate, see Limitations) how much unique values can be generated with that pattern.
        StringIterator uniqueStrings = rgxGen.iterateUnique();               // Iterate over unique values (not accurate, see Limitations)
        String notMatching = rgxGen.generateNotMatching();                   // Generate not matching string

        System.out.println(s);
        System.out.println(estimation);
        System.out.println(uniqueStrings);
        System.out.println(notMatching);
    }
}
