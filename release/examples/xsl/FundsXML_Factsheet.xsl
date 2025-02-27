<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
				xmlns:fo="http://www.w3.org/1999/XSL/Format"
>
	<xsl:template match="/">
		<fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">
			<fo:layout-master-set>
				<fo:simple-page-master master-name="A4" page-height="29.7cm" page-width="21cm">
					<fo:region-body margin="1cm"/>
				</fo:simple-page-master>
			</fo:layout-master-set>
			<fo:page-sequence master-reference="A4">
				<fo:flow flow-name="xsl-region-body">
					<fo:block font-size="24pt" font-weight="bold" margin-bottom="20pt">List of Funds</fo:block>
					<xsl:for-each select="FundsXML4/Funds">
						<fo:block font-size="16pt" margin-bottom="8pt">
							Name:  <xsl:value-of select="Fund/Names/OfficialName"/>
						</fo:block>
						<fo:block font-size="12pt" margin-bottom="8pt">
							LEI:  <xsl:value-of select="Fund/Identifiers/LEI"/>
						</fo:block>
					</xsl:for-each>
				</fo:flow>
			</fo:page-sequence>
		</fo:root>
	</xsl:template>
</xsl:stylesheet>
