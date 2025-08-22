<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    
    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    
    <!-- Haupttemplate -->
    <xsl:template match="xpath-entries">
        <!-- Bestimme das Root-Element aus dem ersten Eintrag -->
        <xsl:variable name="first-xpath" select="entry[1]"/>
        <xsl:variable name="first-path" select="substring-before($first-xpath, '|')"/>
        <xsl:variable name="root-name" select="tokenize(substring-after($first-path, '/'), '/')[1]"/>
        
        <xsl:element name="{$root-name}">
            <xsl:call-template name="build-tree">
                <xsl:with-param name="entries" select="entry"/>
                <xsl:with-param name="current-path" select="concat('/', $root-name)"/>
            </xsl:call-template>
        </xsl:element>
    </xsl:template>
    
    <!-- Rekursives Template zum Aufbau der XML-Struktur -->
    <xsl:template name="build-tree">
        <xsl:param name="entries"/>
        <xsl:param name="current-path"/>
        
        <!-- Verarbeite Attribute für den aktuellen Pfad -->
        <xsl:for-each select="$entries[starts-with(substring-before(., '|'), concat($current-path, '/@'))]">
            <xsl:variable name="attr-path" select="substring-before(., '|')"/>
            <xsl:variable name="attr-name" select="substring-after($attr-path, '/@')"/>
            <xsl:variable name="attr-value" select="substring-after(., '|')"/>
            <xsl:attribute name="{$attr-name}">
                <xsl:value-of select="$attr-value"/>
            </xsl:attribute>
        </xsl:for-each>
        
        <!-- Setze direkten Textinhalt, falls vorhanden -->
        <xsl:variable name="direct-entry" select="$entries[substring-before(., '|') = $current-path]"/>
        <xsl:if test="$direct-entry">
            <xsl:value-of select="substring-after($direct-entry, '|')"/>
        </xsl:if>
        
        <!-- Finde alle direkten Kindelemente -->
        <xsl:variable name="child-entries" 
                     select="$entries[starts-with(substring-before(., '|'), concat($current-path, '/')) and 
                             not(contains(substring-after(substring-before(., '|'), concat($current-path, '/')), '/'))]"/>
        
        <!-- Gruppiere Kindelemente nach Namen -->
        <xsl:for-each-group select="$child-entries" 
                           group-by="tokenize(substring-after(substring-before(., '|'), concat($current-path, '/')), '\[')[1]">
            
            <xsl:variable name="element-name" select="current-grouping-key()"/>
            
            <!-- Verarbeite jedes Element in der Gruppe -->
            <xsl:for-each-group select="current-group()" 
                               group-by="substring-before(., '|')">
                
                <xsl:variable name="element-path" select="current-grouping-key()"/>
                
                <xsl:element name="{$element-name}">
                    <!-- Rekursiv für dieses Element -->
                    <xsl:call-template name="build-tree">
                        <xsl:with-param name="entries" select="$entries"/>
                        <xsl:with-param name="current-path" select="$element-path"/>
                    </xsl:call-template>
                </xsl:element>
            </xsl:for-each-group>
        </xsl:for-each-group>
    </xsl:template>
    
</xsl:stylesheet>