<?xml version="1.0"?>

<xsl:stylesheet version="3.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="html" version="5.0"/>

    <xsl:template match="/">
        <html>
            <body>
                <h1>test report</h1>
                <h2><xsl:value-of select="/root/test/asdf" /></h2>
                <hr/>
                <TextArea>
                    <xsl:copy-of select="." />
                </TextArea>
            </body>
        </html>
    </xsl:template>

</xsl:stylesheet>