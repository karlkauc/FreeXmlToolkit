<?xml version="1.0" encoding="UTF-8"?>
<!-- Test of an external XSLT processor (Settings → XSLT Processors → Test): XSLT 1.0 so every processor runs it;
     the parameter "check" must arrive as "ok". -->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:param name="check" select="'missing'"/>
  <xsl:output method="text"/>
  <xsl:template match="/">
    <xsl:message>processor test running</xsl:message>
    <xsl:text>processor test: </xsl:text>
    <xsl:value-of select="count(/items/item)"/>
    <xsl:text> items, parameter </xsl:text>
    <xsl:value-of select="$check"/>
  </xsl:template>
</xsl:stylesheet>
