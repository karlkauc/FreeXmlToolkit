package org.fxt.freexmltoolkit;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ConcertSvgToImgTest {

    private static final Logger logger = LoggerFactory.getLogger(ConcertSvgToImgTest.class);

    // HINWEIS: Die PLUS_ICON_BASE64 Konstante wird nicht mehr benötigt und wurde entfernt.

    @Test
    public void testConvert() {
        // KORRIGIERT: Die <image>-Tags wurden durch ein wiederverwendbares, natives SVG-Symbol ersetzt.
        String svgString = """
                <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="468.0" height="617.1796875" style="background-color: rgb(235, 252, 241)">
                   <defs>
                      <!-- Definition des Drop-Shadow-Filters -->
                      <filter id="drop-shadow" x="-20%" y="-20%" width="140%" height="140%">
                         <feGaussianBlur in="SourceAlpha" stdDeviation="2"/>
                         <feOffset dx="3" dy="5" result="offsetblur"/>
                         <feFlood flood-color="black" flood-opacity="0.4"/>
                         <feComposite in2="offsetblur" operator="in"/>
                         <feMerge>
                            <feMergeNode/>
                            <feMergeNode in="SourceGraphic"/>
                         </feMerge>
                      </filter>
                
                      <!-- NEU: Definition eines nativen, umrandeten Plus-Symbols -->
                      <g id="plus-icon" style="stroke: #096574; stroke-width: 1.5; stroke-linecap: round;">
                          <circle cx="10" cy="10" r="9" fill="none"/>
                          <line x1="10" y1="5" x2="10" y2="15" />
                          <line x1="5" y1="10" x2="15" y2="10" />
                      </g>
                   </defs>
                
                   <!-- Inhalt mit Verweis auf den Filter und das neue Plus-Symbol -->
                   <a href="#">
                      <rect x="20" y="195" fill="#d5e3e8" width="107.0" style="stroke: rgb(2,23,23); stroke-width: 1.5;" filter="url(#drop-shadow)" rx="2" ry="2" height="38.3984375" id="FundsXML4"></rect>
                      <text x="30.0" fill="#096574" y="219.3984375" font-size="16" font-family="Arial">FundsXML4</text>
                   </a>
                   <g id="comment">
                      <text x="30" y="248.3984375">
                         <tspan x="25" dy="1.2em">Root element  </tspan>
                         <tspan x="25" dy="1.2em">of FundsXML  </tspan>
                         <tspan x="25" dy="1.2em">4.2.5  </tspan>
                      </text>
                   </g>
                   <a href="ControlData_36594a8329d5275bb684901d4b38e46e.html">
                      <rect x="207.0" y="20.0" fill="#d5e3e8" width="231.0" style="stroke: rgb(2,23,23); stroke-width: 1.5; stroke-dasharray: 7, 7;" filter="url(#drop-shadow)" rx="2" ry="2" height="64.796875" id="ControlData"></rect>
                      <g>
                         <text x="217.0" fill="#096574" y="48.3984375" font-size="16" font-family="Arial">ControlData</text>
                         <line y2="52.3984375" stroke-width="1" x1="217.0" x2="428.0" stroke="#8cb5c2" y1="52.3984375"></line>
                         <text x="217.0" fill="#54828d" y="74.796875" font-size="14" font-family="Arial">ControlDataType</text>
                      </g>
                      <use xlink:href="#plus-icon" x="440.0" y="42.3984375" />
                   </a>
                   <path style="stroke: rgb(2,23,23); stroke-width: 1.5;" fill="none" d="M 127.0 213.3984375 h 40 V 52.3984375 h 40"></path>
                   <a href="Funds_656aeab202d160b949f1bc6412654b59.html">
                      <rect x="207.0" y="104.796875" fill="#d5e3e8" width="231.0" style="stroke: rgb(2,23,23); stroke-width: 1.5; stroke-dasharray: 7, 7;" filter="url(#drop-shadow)" rx="2" ry="2" height="38.3984375" id="Funds"></rect>
                      <g>
                         <text x="217.0" fill="#096574" y="133.1953125" font-size="16" font-family="Arial">Funds</text>
                      </g>
                      <use xlink:href="#plus-icon" x="440.0" y="113.99609375" />
                   </a>
                   <path style="stroke: rgb(2,23,23); stroke-width: 1.5; stroke-dasharray: 7, 7;" fill="none" d="M 127.0 213.3984375 h 40 V 123.99609375 h 40"></path>
                   <a href="AssetMgmtCompanyDynData_da7ef743258bab0102186a867684d4d9.html">
                      <rect x="207.0" y="163.1953125" fill="#d5e3e8" width="231.0" style="stroke: rgb(2,23,23); stroke-width: 1.5; stroke-dasharray: 7, 7;" filter="url(#drop-shadow)" rx="2" ry="2" height="64.796875" id="AssetMgmtCompanyDynData"></rect>
                      <g>
                         <text x="217.0" fill="#096574" y="191.59375" font-size="16" font-family="Arial">AssetMgmtCompanyDynData</text>
                         <line y2="195.59375" stroke-width="1" x1="217.0" x2="428.0" stroke="#8cb5c2" y1="195.59375"></line>
                         <text x="217.0" fill="#54828d" y="217.9921875" font-size="14" font-family="Arial">AssetMgmtCompanyDynDataType</text>
                      </g>
                      <use xlink:href="#plus-icon" x="440.0" y="185.59375" />
                   </a>
                   <path style="stroke: rgb(2,23,23); stroke-width: 1.5; stroke-dasharray: 7, 7;" fill="none" d="M 127.0 213.3984375 h 40 V 195.59375 h 40"></path>
                   <a href="AssetMasterData_1944b3819a21ab3426c2741e35e5c461.html">
                      <rect x="207.0" y="247.9921875" fill="#d5e3e8" width="231.0" style="stroke: rgb(2,23,23); stroke-width: 1.5; stroke-dasharray: 7, 7;" filter="url(#drop-shadow)" rx="2" ry="2" height="64.796875" id="AssetMasterData"></rect>
                      <g>
                         <text x="217.0" fill="#096574" y="276.390625" font-size="16" font-family="Arial">AssetMasterData</text>
                         <line y2="280.390625" stroke-width="1" x1="217.0" x2="428.0" stroke="#8cb5c2" y1="280.390625"></line>
                         <text x="217.0" fill="#54828d" y="302.7890625" font-size="14" font-family="Arial">AssetMasterDataType</text>
                      </g>
                      <use xlink:href="#plus-icon" x="440.0" y="270.390625" />
                   </a>
                   <path style="stroke: rgb(2,23,23); stroke-width: 1.5; stroke-dasharray: 7, 7;" fill="none" d="M 127.0 213.3984375 h 40 V 280.390625 h 40"></path>
                   <a href="Documents_a504ed2dec58fe90bcde40319688b4e6.html">
                      <rect x="207.0" y="332.7890625" fill="#d5e3e8" width="231.0" style="stroke: rgb(2,23,23); stroke-width: 1.5; stroke-dasharray: 7, 7;" filter="url(#drop-shadow)" rx="2" ry="2" height="64.796875" id="Documents"></rect>
                      <g>
                         <text x="217.0" fill="#096574" y="361.1875" font-size="16" font-family="Arial">Documents</text>
                         <line y2="365.1875" stroke-width="1" x1="217.0" x2="428.0" stroke="#8cb5c2" y1="365.1875"></line>
                         <text x="217.0" fill="#54828d" y="387.5859375" font-size="14" font-family="Arial">DocumentsType</text>
                      </g>
                      <use xlink:href="#plus-icon" x="440.0" y="355.1875" />
                   </a>
                   <path style="stroke: rgb(2,23,23); stroke-width: 1.5; stroke-dasharray: 7, 7;" fill="none" d="M 127.0 213.3984375 h 40 V 365.1875 h 40"></path>
                   <a href="RegulatoryReportings_e93b40b6a782673701031e15cd7537d0.html">
                      <rect x="207.0" y="417.5859375" fill="#d5e3e8" width="231.0" style="stroke: rgb(2,23,23); stroke-width: 1.5; stroke-dasharray: 7, 7;" filter="url(#drop-shadow)" rx="2" ry="2" height="64.796875" id="RegulatoryReportings"></rect>
                      <g>
                         <text x="217.0" fill="#096574" y="445.984375" font-size="16" font-family="Arial">RegulatoryReportings</text>
                         <line y2="449.984375" stroke-width="1" x1="217.0" x2="428.0" stroke="#8cb5c2" y1="449.984375"></line>
                         <text x="217.0" fill="#54828d" y="472.3828125" font-size="14" font-family="Arial">RegulatoryReportingsType</text>
                      </g>
                      <use xlink:href="#plus-icon" x="440.0" y="439.984375" />
                   </a>
                   <path style="stroke: rgb(2,23,23); stroke-width: 1.5; stroke-dasharray: 7, 7;" fill="none" d="M 127.0 213.3984375 h 40 V 449.984375 h 40"></path>
                   <a href="CountrySpecificData_52e61bfc3331a3782fe090d0ae796b84.html">
                      <rect x="207.0" y="502.3828125" fill="#d5e3e8" width="231.0" style="stroke: rgb(2,23,23); stroke-width: 1.5; stroke-dasharray: 7, 7;" filter="url(#drop-shadow)" rx="2" ry="2" height="64.796875" id="CountrySpecificData"></rect>
                      <g>
                         <text x="217.0" fill="#096574" y="530.78125" font-size="16" font-family="Arial">CountrySpecificData</text>
                         <line y2="534.78125" stroke-width="1" x1="217.0" x2="428.0" stroke="#8cb5c2" y1="534.78125"></line>
                         <text x="217.0" fill="#54828d" y="557.1796875" font-size="14" font-family="Arial">CountrySpecificDataType</text>
                      </g>
                      <use xlink:href="#plus-icon" x="440.0" y="524.78125" />
                   </a>
                   <path style="stroke: rgb(2,23,23); stroke-width: 1.5; stroke-dasharray: 7, 7;" fill="none" d="M 127.0 213.3984375 h 40 V 534.78125 h 40"></path>
                </svg>
                """;

        Path outputPngPath = Paths.get("target/diagramm.png");

        try {
            Files.createDirectories(outputPngPath.getParent());

            try (StringReader reader = new StringReader(svgString);
                 OutputStream os = new FileOutputStream(outputPngPath.toFile())) {

                TranscoderInput input = new TranscoderInput(reader);
                TranscoderOutput output = new TranscoderOutput(os);
                PNGTranscoder transcoder = new PNGTranscoder();

                transcoder.transcode(input, output);
            }

            logger.info("Konvertierung erfolgreich! PNG gespeichert unter: {}", outputPngPath.toAbsolutePath());

            assertTrue(Files.exists(outputPngPath), "Die PNG-Datei wurde nicht erstellt.");
            assertTrue(Files.size(outputPngPath) > 0, "Die erstellte PNG-Datei ist leer.");

        } catch (Exception e) {
            logger.error("Ein Fehler bei der Konvertierung ist aufgetreten:", e);
            fail("Die Konvertierung ist mit einer Exception fehlgeschlagen: " + e.getMessage());
        }
    }
}