<?xml version="1.0" encoding="UTF-8"?>
<!-- FreeXmlToolkit example: turns order.xml into a small HTML summary. -->
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema" exclude-result-prefixes="xs">
    <xsl:output method="html" indent="yes"/>

    <xsl:template match="/order">
        <html>
            <head><title>Order <xsl:value-of select="@id"/></title></head>
            <body>
                <h1>Order <xsl:value-of select="@id"/></h1>
                <p><xsl:value-of select="customer/name"/>, <xsl:value-of select="@date"/></p>
                <table border="1" cellpadding="4">
                    <tr><th>Item</th><th>Quantity</th><th>Price</th><th>Total</th></tr>
                    <xsl:for-each select="items/item">
                        <tr>
                            <td><xsl:value-of select="."/></td>
                            <td><xsl:value-of select="@quantity"/></td>
                            <td><xsl:value-of select="@price"/></td>
                            <td><xsl:value-of select="format-number(@quantity * @price, '0.00')"/></td>
                        </tr>
                    </xsl:for-each>
                </table>
                <p><b>Order total: <xsl:value-of
                        select="format-number(sum(items/item/(xs:decimal(@quantity) * xs:decimal(@price))), '0.00')"/></b></p>
            </body>
        </html>
    </xsl:template>
</xsl:stylesheet>
