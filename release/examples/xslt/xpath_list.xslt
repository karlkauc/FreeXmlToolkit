<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    
    <!-- Output-Methode als Text -->
    <xsl:output method="text" encoding="UTF-8"/>
    
    <!-- Template für das Root-Element -->
    <xsl:template match="/">
        <!-- Verarbeite alle Elemente mit Textinhalt -->
        <xsl:apply-templates select="//*[normalize-space(text()) != '']"/>
        <!-- Verarbeite alle Attribute -->
        <xsl:apply-templates select="//@*"/>
    </xsl:template>
    
    <!-- Template für Elemente mit Textinhalt -->
    <xsl:template match="*[normalize-space(text()) != '']">
        <!-- Prüfe, ob das Element nur direkten Text hat (keine Kindelemente mit Text) -->
        <xsl:if test="not(*[normalize-space(text()) != ''])">
            <!-- Generiere den vollständigen XPath -->
            <xsl:call-template name="generate-xpath"/>
            <xsl:text>|</xsl:text>
            <!-- Extrahiere nur den direkten Textinhalt -->
            <xsl:value-of select="normalize-space(text())"/>
            <xsl:text>&#10;</xsl:text> <!-- Neue Zeile -->
        </xsl:if>
    </xsl:template>
    
    <!-- Template für Attribute -->
    <xsl:template match="@*">
        <!-- Generiere XPath für das Elternelement -->
        <xsl:for-each select="..">
            <xsl:call-template name="generate-xpath"/>
        </xsl:for-each>
        <xsl:text>/@</xsl:text>
        <xsl:value-of select="local-name()"/>
        <xsl:text>|</xsl:text>
        <xsl:value-of select="."/>
        <xsl:text>&#10;</xsl:text>
    </xsl:template>
    
    <!-- Rekursive Template-Funktion zur XPath-Generierung -->
    <xsl:template name="generate-xpath">
        <xsl:param name="node" select="."/>
        
        <!-- Wenn wir nicht am Root sind, gehe rekursiv nach oben -->
        <xsl:if test="$node/parent::*">
            <xsl:call-template name="generate-xpath">
                <xsl:with-param name="node" select="$node/parent::*"/>
            </xsl:call-template>
        </xsl:if>
        
        <!-- Füge den aktuellen Knotennamen hinzu -->
        <xsl:text>/</xsl:text>
        <xsl:value-of select="local-name($node)"/>
        
        <!-- Behandle mehrere gleichnamige Geschwisterelemente mit Position -->
        <xsl:variable name="position" 
                      select="count($node/preceding-sibling::*[local-name() = local-name($node)]) + 1"/>
        <xsl:variable name="total" 
                      select="count($node/parent::*/*[local-name() = local-name($node)])"/>
        
        <xsl:if test="$total > 1">
            <xsl:text>[</xsl:text>
            <xsl:value-of select="$position"/>
            <xsl:text>]</xsl:text>
        </xsl:if>
    </xsl:template>
    
</xsl:stylesheet>