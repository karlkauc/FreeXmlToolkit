<?xml version="1.0" encoding="UTF-8"?>
<!--
    FundsXML4 to JSON (XSLT 3.0)

    Converts a FundsXML4 document into a compact JSON summary using the
    XSLT 3.0 JSON output method together with XPath 3.1 map/array constructors
    (works with Saxon HE - no schema awareness or streaming required).

    Input : any FundsXML4 file, e.g. xml/FundsXML4_Equity_Fund.xml
    Output: JSON (document header, funds, share classes, portfolio positions)

    Alternative approach: build an XML tree in the fn:json-to-xml vocabulary
    and serialize it with fn:xml-to-json(). The map/array style below is
    usually shorter and easier to read.

    See also: Data_Binding_JSON/ in https://github.com/fundsxml/examples
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                version="3.0"
                expand-text="yes">

    <xsl:output method="json" build-tree="no" indent="yes"/>
    <xsl:mode on-no-match="shallow-skip"/>

    <xsl:template match="/">
        <xsl:sequence select="map {
            'document': map {
                'id':          string(FundsXML4/ControlData/UniqueDocumentID),
                'generated':   string(FundsXML4/ControlData/DocumentGenerated),
                'contentDate': string(FundsXML4/ControlData/ContentDate),
                'supplier':    string(FundsXML4/ControlData/DataSupplier/Name)
            },
            'funds': array {
                for $fund in FundsXML4/Funds/Fund
                return map {
                    'name':     string($fund/Names/OfficialName),
                    'isin':     string($fund/Identifiers/ISIN),
                    'lei':      string($fund/Identifiers/LEI),
                    'currency': string($fund/Currency),
                    'nav':      number(($fund/FundDynamicData/TotalAssetValues/TotalAssetValue
                                        /TotalNetAssetValue/Amount)[1]),
                    'shareClasses': array {
                        for $sc in $fund/SingleFund/ShareClasses/ShareClass
                        return map {
                            'isin':     string($sc/Identifiers/ISIN),
                            'name':     string($sc/Names/OfficialName),
                            'navPrice': number(($sc/Prices/Price/NavPrice)[1])
                        }
                    },
                    'positions': array {
                        for $pos in $fund/FundDynamicData/Portfolios/Portfolio/Positions/Position
                        return map {
                            'id':       string($pos/UniqueID),
                            'isin':     string(($pos/Identifiers/ISIN)[1]),
                            'name':     string(($pos/Identifiers/OtherID)[1]),
                            'currency': string($pos/Currency),
                            'value':    number(($pos/TotalValue/Amount)[1]),
                            'percent':  number($pos/TotalPercentage)
                        }
                    }
                }
            }
        }"/>
    </xsl:template>

</xsl:stylesheet>
