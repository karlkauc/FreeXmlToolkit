<?xml version="1.0" encoding="UTF-8"?>
<!--
    FundsXML4 Data Quality Validation Rules - Schematron
    =====================================================
    This Schematron file implements comprehensive data quality checks
    for FundsXML4 documents, validating structural integrity, calculations,
    and business rules compliance.
    
    Severity Levels:
    - ERROR: Critical issues that must be fixed
    - WARNING: Important issues that should be reviewed
    - INFO: Informational messages about data quality
-->
<schema xmlns="http://purl.oclc.org/dsdl/schematron" 
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        queryBinding="xslt2">
    
    <title>FundsXML4 Data Quality Validation Rules</title>
    
    <!-- ============================================
         PATTERN 1: STRUCTURAL CHECKS
         Basic validation of required elements
         ============================================ -->
    <pattern id="structural-checks">
        <title>Structural Integrity Checks</title>
        
        <!-- Rule: Fund should have a LEI identifier -->
        <rule context="Fund">
            <assert test="Identifiers/LEI" role="warning">
                WARNING: Fund "<value-of select="Names/OfficialName"/>" should have a LEI identifier
            </assert>
            
            <!-- Check for at least one portfolio -->
            <assert test="count(FundDynamicData/Portfolios/Portfolio) > 0" role="warning">
                WARNING: Fund must have at least one portfolio
            </assert>
            
            <!-- Check that Total Asset Value exists in fund currency -->
            <let name="fundCurrency" value="Currency"/>
            <assert test="FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy = $fundCurrency]" role="error">
                ERROR: Fund Total Asset Value must be provided in fund currency (<value-of select="$fundCurrency"/>)
            </assert>
        </rule>
        
        <!-- Rule: ShareClasses should have ISIN -->
        <rule context="ShareClass">
            <assert test="Identifiers/ISIN" role="warning">
                WARNING: ShareClass "<value-of select="Names/OfficialName"/>" should have an ISIN
            </assert>
        </rule>
    </pattern>
    
    <!-- ============================================
         PATTERN 2: FUND NAV CALCULATIONS
         Validates NAV consistency across fund and share classes
         ============================================ -->
    <pattern id="nav-calculations">
        <title>NAV Calculation Validations</title>
        
        <!-- Rule: Sum of ShareClass NAVs should equal Fund Total NAV -->
        <rule context="Fund[SingleFund/ShareClasses/ShareClass]">
            <let name="fundCurrency" value="Currency"/>
            <let name="fundTotalNAV" value="number(FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy = $fundCurrency])"/>
            <let name="sumShareClassNAV" value="sum(SingleFund/ShareClasses/ShareClass/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy = $fundCurrency])"/>
            <let name="difference" value="abs($fundTotalNAV - $sumShareClassNAV)"/>
            
            <!-- Allow small rounding differences (< 1 currency unit) -->
            <assert test="$difference &lt; 1" role="error">
                ERROR: Sum of ShareClass NAVs (<value-of select="format-number($sumShareClassNAV, '#,##0.00')"/> <value-of select="$fundCurrency"/>) 
                does not equal Fund Total NAV (<value-of select="format-number($fundTotalNAV, '#,##0.00')"/> <value-of select="$fundCurrency"/>). 
                Difference: <value-of select="format-number($difference, '#,##0.00')"/>
            </assert>
            
            <!-- Warning for small rounding differences -->
            <report test="$difference &gt;= 0.01 and $difference &lt; 1" role="warning">
                WARNING: Small rounding difference detected in NAV summation. 
                Difference: <value-of select="format-number($difference, '#,##0.00')"/> <value-of select="$fundCurrency"/>
            </report>
        </rule>
        
        <!-- Rule: ShareClass Price × Shares should equal NAV -->
        <rule context="SingleFund/ShareClasses/ShareClass">
            <let name="shareclassCurrency" value="Currency"/>
            <let name="price" value="number(Prices/Price/NavPrice)"/>
            <let name="shares" value="number(TotalAssetValues/TotalAssetValue/SharesOutstanding)"/>
            <let name="reportedNAV" value="number(TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy = $shareclassCurrency])"/>
            <let name="calculatedNAV" value="$price * $shares"/>
            <let name="calculatedPrice" value="if ($shares > 0) then $reportedNAV div $shares else 0"/>
            <let name="priceDifference" value="abs($calculatedPrice - $price)"/>
            
            <!-- Check if price calculation is correct (tolerance: 0.1) -->
            <assert test="$priceDifference &lt; 0.1 or $shares = 0" role="error">
                ERROR: ShareClass <value-of select="Identifiers/ISIN"/> price calculation mismatch. 
                Reported price: <value-of select="format-number($price, '#,##0.00')"/>, 
                Calculated price from NAV/Shares: <value-of select="format-number($calculatedPrice, '#,##0.00')"/>
            </assert>
            
            <!-- Warning for small differences -->
            <report test="$priceDifference &gt;= 0.01 and $priceDifference &lt; 0.1" role="warning">
                WARNING: Small rounding difference in ShareClass <value-of select="Identifiers/ISIN"/> price calculation. 
                Difference: <value-of select="format-number($priceDifference, '#,##0.00')"/>
            </report>
        </rule>
    </pattern>
    
    <!-- ============================================
         PATTERN 3: PORTFOLIO VALIDATIONS
         Checks portfolio positions and allocations
         ============================================ -->
    <pattern id="portfolio-validations">
        <title>Portfolio Position Validations</title>
        
        <!-- Rule: Sum of position values should equal Fund Total NAV -->
        <rule context="Fund[FundDynamicData/Portfolios/Portfolio]">
            <let name="fundCurrency" value="Currency"/>
            <let name="fundTotalNAV" value="number(FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy = $fundCurrency])"/>
            <let name="sumPositionValues" value="sum(FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalValue/Amount[@ccy = $fundCurrency])"/>
            <let name="difference" value="abs($sumPositionValues - $fundTotalNAV)"/>
            
            <assert test="$difference &lt; 1" role="error">
                ERROR: Sum of portfolio position values (<value-of select="format-number($sumPositionValues, '#,##0.00')"/> <value-of select="$fundCurrency"/>) 
                does not equal Fund Total NAV (<value-of select="format-number($fundTotalNAV, '#,##0.00')"/> <value-of select="$fundCurrency"/>). 
                Difference: <value-of select="format-number($difference, '#,##0.00')"/>
            </assert>
        </rule>
        
        <!-- Rule: Portfolio percentages should sum to 100% -->
        <rule context="Fund[FundDynamicData/Portfolios/Portfolio/Positions/Position]">
            <let name="sumPercentages" value="sum(FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalPercentage)"/>
            <let name="difference" value="abs($sumPercentages - 100)"/>
            
            <!-- Allow 1% tolerance for rounding -->
            <assert test="$difference &lt;= 1" role="error">
                ERROR: Portfolio position percentages sum to <value-of select="format-number($sumPercentages, '#,##0.0000')"/>% 
                instead of 100%. Difference: <value-of select="format-number($difference, '#,##0.0000')"/>%
            </assert>
            
            <report test="$difference > 0.01 and $difference &lt;= 1" role="warning">
                WARNING: Small deviation in percentage sum. Total: <value-of select="format-number($sumPercentages, '#,##0.0000')"/>%
            </report>
        </rule>
        
        <!-- Rule: Each position should have value in fund currency -->
        <rule context="FundDynamicData/Portfolios/Portfolio/Positions/Position">
            <let name="fundCurrency" value="ancestor::Fund/Currency"/>
            <assert test="TotalValue/Amount[@ccy = $fundCurrency]" role="error">
                ERROR: Position <value-of select="UniqueID"/> does not have a value in fund currency (<value-of select="$fundCurrency"/>).
                Available currencies: <value-of select="string-join(TotalValue/Amount/@ccy, ', ')"/>
            </assert>
        </rule>
        
        <!-- Rule: Position values should have consistent direction across currencies -->
        <rule context="FundDynamicData/Portfolios/Portfolio/Positions/Position[count(TotalValue/Amount) > 1]">
            <let name="hasPositiveSignificant" value="count(TotalValue/Amount[number(.) > 1]) > 0"/>
            <let name="hasNegativeSignificant" value="count(TotalValue/Amount[number(.) &lt; -1]) > 0"/>
            
            <assert test="not($hasPositiveSignificant and $hasNegativeSignificant)" role="error">
                ERROR: Position <value-of select="UniqueID"/> has mixed value directions across currencies. 
                All values must be either positive or negative.
            </assert>
        </rule>
    </pattern>
    
    <!-- ============================================
         PATTERN 4: ASSET-SPECIFIC VALIDATIONS
         Asset type specific requirements
         ============================================ -->
    <pattern id="asset-validations">
        <title>Asset-Specific Validations</title>
        
        <!-- Rule: Equity, Bond, and ShareClass assets must have ISIN -->
        <rule context="AssetMasterData/Asset[AssetType = 'EQ' or AssetType = 'BO' or AssetType = 'SC']">
            <assert test="Identifiers/ISIN" role="error">
                ERROR: <value-of select="AssetType"/> asset "<value-of select="Name"/>" must have an ISIN identifier
            </assert>
        </rule>
        
        <!-- Rule: Account assets should have counterparty with LEI or BIC -->
        <rule context="AssetMasterData/Asset[AssetType = 'AC']">
            <assert test="AssetDetails/Account/Counterparty/Identifiers/LEI or AssetDetails/Account/Counterparty/Identifiers/BIC" role="warning">
                WARNING: Account "<value-of select="Name"/>" should have a counterparty with LEI or BIC identifier
            </assert>
        </rule>
        
        <!-- Rule: Derivatives should have exposure -->
        <rule context="AssetMasterData/Asset[AssetType = 'OP' or AssetType = 'FU' or AssetType = 'FX' or AssetType = 'SW']">
            <assert test="AssetDetails//Exposure" role="warning">
                WARNING: Derivative asset "<value-of select="Name"/>" (Type: <value-of select="AssetType"/>) should have exposure information
            </assert>
        </rule>
        
        <!-- Rule: Options must have underlying -->
        <rule context="AssetMasterData/Asset[AssetType = 'OP']">
            <assert test="AssetDetails/Option/Underlyings/Underlying" role="error">
                ERROR: Option "<value-of select="Name"/>" must have at least one underlying asset
            </assert>
        </rule>
        
        <!-- Rule: Futures must have underlying -->
        <rule context="AssetMasterData/Asset[AssetType = 'FU']">
            <assert test="AssetDetails/Future/Underlyings/Underlying" role="error">
                ERROR: Future "<value-of select="Name"/>" must have at least one underlying asset
            </assert>
        </rule>
    </pattern>
    
    <!-- ============================================
         PATTERN 5: DATE CONSISTENCY
         Validates date consistency across the document
         ============================================ -->
    <pattern id="date-validations">
        <title>Date Consistency Validations</title>
        
        <!-- Rule: All NAV dates should be consistent -->
        <rule context="Fund">
            <let name="fundNavDate" value="FundDynamicData/TotalAssetValues/TotalAssetValue/NavDate"/>
            
            <!-- Check ShareClass NAV dates -->
            <assert test="every $date in SingleFund/ShareClasses/ShareClass/TotalAssetValues/TotalAssetValue/NavDate 
                          satisfies $date = $fundNavDate" role="warning">
                WARNING: Not all ShareClass NAV dates match the Fund NAV date (<value-of select="$fundNavDate"/>)
            </assert>
        </rule>
        
        <!-- Rule: Content date should not be in the future -->
        <rule context="ContentDate">
            <assert test=". &lt;= current-date()" role="warning">
                WARNING: Content date (<value-of select="."/>) is in the future
            </assert>
        </rule>
    </pattern>
    
    <!-- ============================================
         PATTERN 6: IDENTIFIER VALIDATIONS
         Validates format and presence of identifiers
         ============================================ -->
    <pattern id="identifier-validations">
        <title>Identifier Format Validations</title>
        
        <!-- Rule: ISIN format validation (12 characters) -->
        <rule context="Identifiers/ISIN">
            <assert test="string-length(.) = 12" role="error">
                ERROR: ISIN "<value-of select="."/>" must be exactly 12 characters long
            </assert>
            <assert test="matches(., '^[A-Z]{2}[A-Z0-9]{9}[0-9]$')" role="warning">
                WARNING: ISIN "<value-of select="."/>" does not match expected format (2 letters, 9 alphanumeric, 1 digit)
            </assert>
        </rule>
        
        <!-- Rule: LEI format validation (20 characters) -->
        <rule context="Identifiers/LEI">
            <assert test="string-length(.) = 20" role="error">
                ERROR: LEI "<value-of select="."/>" must be exactly 20 characters long
            </assert>
            <assert test="matches(., '^[A-Z0-9]{18}[0-9]{2}$')" role="warning">
                WARNING: LEI "<value-of select="."/>" does not match expected format (18 alphanumeric, 2 check digits)
            </assert>
        </rule>
        
        <!-- Rule: BIC format validation (8 or 11 characters) -->
        <rule context="Identifiers/BIC">
            <assert test="string-length(.) = 8 or string-length(.) = 11" role="error">
                ERROR: BIC "<value-of select="."/>" must be either 8 or 11 characters long
            </assert>
        </rule>
    </pattern>
    
    <!-- ============================================
         PATTERN 7: CURRENCY VALIDATIONS
         Validates currency codes and consistency
         ============================================ -->
    <pattern id="currency-validations">
        <title>Currency Validations</title>
        
        <!-- Rule: Currency codes should be 3 letters (ISO 4217) -->
        <rule context="Currency | Amount/@ccy">
            <assert test="matches(., '^[A-Z]{3}$')" role="warning">
                WARNING: Currency code "<value-of select="."/>" should be a 3-letter ISO code
            </assert>
        </rule>
        
        <!-- Rule: All amounts should have currency attribute -->
        <rule context="Amount[not(@ccy)]">
            <assert test="false()" role="error">
                ERROR: Amount element without currency attribute found (value: <value-of select="."/>)
            </assert>
        </rule>
    </pattern>
    
    <!-- ============================================
         DIAGNOSTICS
         Detailed diagnostic messages for complex validations
         ============================================ -->
    <diagnostics>
        <diagnostic id="nav-calc-details">
            The sum of all ShareClass NAVs must equal the Fund's Total Net Asset Value.
            This ensures that the fund-level NAV is properly distributed across all share classes.
        </diagnostic>
        
        <diagnostic id="position-sum-details">
            The sum of all portfolio position values must equal the Fund's Total Net Asset Value.
            This ensures that all assets are properly accounted for in the portfolio.
        </diagnostic>
        
        <diagnostic id="percentage-sum-details">
            Portfolio position percentages must sum to 100% (with 1% tolerance for rounding).
            This ensures proper allocation representation.
        </diagnostic>
        
        <diagnostic id="identifier-format-details">
            Identifiers must follow international standards:
            - ISIN: 12 characters (2 country code + 9 alphanumeric + 1 check digit)
            - LEI: 20 characters (18 alphanumeric + 2 check digits)
            - BIC: 8 or 11 characters
        </diagnostic>
    </diagnostics>
    
</schema>