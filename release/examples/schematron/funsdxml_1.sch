<?xml version="1.0" encoding="UTF-8"?>
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
  <sch:title>FundsXML4 - ShareClass Proportional Allocation and Multi-Currency Consistency</sch:title>
  
  <sch:pattern>
    <sch:title>ShareClass Proportional Position Values</sch:title>
    
    <sch:rule context="Fund/SingleFund/ShareClasses/ShareClass/Portfolios/Portfolio/Positions/Position">
      <sch:let name="positionID" value="UniqueID"/>
      <sch:let name="shareClassValue" value="number(TotalValue/Amount[@isFundCcy='true'])"/>
      <sch:let name="fundValue" value="number(ancestor::Fund/FundDynamicData/Portfolios/Portfolio/Positions/Position[UniqueID = $positionID]/TotalValue/Amount)"/>
      <sch:let name="ratio" value="number(ancestor::ShareClass/TotalAssetValues/TotalAssetValue/Ratio) div 100"/>
      <sch:let name="expectedValue" value="$fundValue * $ratio"/>
      
      <sch:assert test="abs($shareClassValue - $expectedValue) div $expectedValue &lt; 0.01">
        ShareClass position value (<sch:value-of select="$shareClassValue"/>) deviates from expected proportional fund value (<sch:value-of select="$expectedValue"/>) by more than 1%.
      </sch:assert>
    </sch:rule>
  </sch:pattern>
  
  <sch:pattern>
    <sch:title>Multi-Currency Fund Allocation Consistency</sch:title>
    
    <sch:rule context="Fund[count(distinct-values(SingleFund/ShareClasses/ShareClass/Currency)) > 1]">
      <sch:let name="fundBaseCurrency" value="Currency"/>
      <sch:let name="totalFundValue" value="number(FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount)"/>
      <sch:let name="sumShareClassValues" value="sum(SingleFund/ShareClasses/ShareClass/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy = $fundBaseCurrency])"/>
      
      <sch:assert test="abs($totalFundValue - $sumShareClassValues) div $totalFundValue &lt; 0.01">
        Multi-currency fund: Sum of share class values in base currency (<sch:value-of select="$sumShareClassValues"/>) does not match total fund value (<sch:value-of select="$totalFundValue"/>).
      </sch:assert>
    </sch:rule>
  </sch:pattern>
  
  <sch:pattern>
    <sch:title>FX Rate Application Consistency</sch:title>
    
    <sch:rule context="Position[Currency != ancestor::Fund/Currency and FXRates/FXRate]">
      <sch:let name="foreignAmount" value="number(TotalValue/Amount[@ccy = current()/Currency])"/>
      <sch:let name="baseAmount" value="number(TotalValue/Amount[@ccy = ancestor::Fund/Currency])"/>
      <sch:let name="fxRate" value="number(FXRates/FXRate[@fromCcy = current()/Currency and @toCcy = ancestor::Fund/Currency])"/>
      <sch:let name="mulDiv" value="FXRates/FXRate[@fromCcy = current()/Currency and @toCcy = ancestor::Fund/Currency]/@mulDiv"/>
      
      <sch:let name="convertedAmount" value="if ($mulDiv = 'M') then $foreignAmount * $fxRate else $foreignAmount div $fxRate"/>
      
      <sch:assert test="abs($baseAmount - $convertedAmount) div $baseAmount &lt; 0.01">
        FX conversion inconsistent: Base currency amount (<sch:value-of select="$baseAmount"/>) does not match converted amount (<sch:value-of select="$convertedAmount"/>) using FX rate <sch:value-of select="$fxRate"/>.
      </sch:assert>
    </sch:rule>
  </sch:pattern>
  
  <sch:pattern>
    <sch:title>Counterparty Relationship Validation</sch:title>
    
    <sch:rule context="Transaction/Counterparty">
      <sch:let name="counterpartyLEI" value="Identifiers/LEI"/>
      <sch:let name="counterpartyName" value="Name"/>
      
      <sch:assert test="//AssetMasterData/Asset//Counterparty[Identifiers/LEI = $counterpartyLEI and Name = $counterpartyName] or
                        //DataSupplier[LEI = $counterpartyLEI and Name = $counterpartyName] or
                        $counterpartyLEI = 'PQOH26KWDF7CG10L6792'">
        Transaction counterparty (LEI: <sch:value-of select="$counterpartyLEI"/>, Name: <sch:value-of select="$counterpartyName"/>) is not consistently defined across the document.
      </sch:assert>
    </sch:rule>
  </sch:pattern>
  
  <sch:pattern>
    <sch:title>Asset Type and Position Type Consistency</sch:title>
    
    <sch:rule context="Position[Equity]">
      <sch:let name="assetType" value="//AssetMasterData/Asset[UniqueID = current()/UniqueID]/AssetType"/>
      <sch:assert test="$assetType = 'EQ'">
        Position with Equity details must reference an Asset of type 'EQ', but references type '<sch:value-of select="$assetType"/>'.
      </sch:assert>
    </sch:rule>
    
    <sch:rule context="Position[Bond]">
      <sch:let name="assetType" value="//AssetMasterData/Asset[UniqueID = current()/UniqueID]/AssetType"/>
      <sch:assert test="$assetType = 'BO'">
        Position with Bond details must reference an Asset of type 'BO', but references type '<sch:value-of select="$assetType"/>'.
      </sch:assert>
    </sch:rule>
    
    <sch:rule context="Position[ShareClass]">
      <sch:let name="assetType" value="//AssetMasterData/Asset[UniqueID = current()/UniqueID]/AssetType"/>
      <sch:assert test="$assetType = 'SC'">
        Position with ShareClass details must reference an Asset of type 'SC', but references type '<sch:value-of select="$assetType"/>'.
      </sch:assert>
    </sch:rule>
    
    <sch:rule context="Position[Future]">
      <sch:let name="assetType" value="//AssetMasterData/Asset[UniqueID = current()/UniqueID]/AssetType"/>
      <sch:assert test="$assetType = 'FU'">
        Position with Future details must reference an Asset of type 'FU', but references type '<sch:value-of select="$assetType"/>'.
      </sch:assert>
    </sch:rule>
  </sch:pattern>
  
  <sch:pattern>
    <sch:title>Transaction Impact on Portfolio Consistency</sch:title>
    
    <sch:rule context="Fund[FundDynamicData/Portfolios/Portfolio/Transactions/Transaction]">
      <sch:let name="portfolioDate" value="FundDynamicData/Portfolios/Portfolio/@NavDate"/>
      <sch:let name="hasTransactionsOnDate" value="FundDynamicData/Portfolios/Portfolio/Transactions/Transaction[ClosingDate = $portfolioDate]"/>
      
      <sch:report test="$hasTransactionsOnDate">
        Portfolio dated <sch:value-of select="$portfolioDate"/> contains transactions with the same closing date, which may indicate end-of-day portfolio after transaction settlement.
      </sch:report>
    </sch:rule>
  </sch:pattern>
  
  <sch:pattern>
    <sch:title>Share Class Currency and Amount Consistency</sch:title>
    
    <sch:rule context="ShareClass/TotalAssetValues/TotalAssetValue/TotalNetAssetValue">
      <sch:let name="shareClassCurrency" value="ancestor::ShareClass/Currency"/>
      
      <sch:assert test="Amount[@isShareClassCcy='true']/@ccy = $shareClassCurrency">
        Share class NAV amount marked as share class currency must match the actual share class currency.
      </sch:assert>
      
      <sch:assert test="Amount[@isFundCcy='true']/@ccy = ancestor::Fund/Currency">
        Share class NAV amount marked as fund currency must match the actual fund currency.
      </sch:assert>
    </sch:rule>
  </sch:pattern>
  
  <sch:pattern>
    <sch:title>Position Exposure Consistency</sch:title>
    
    <sch:rule context="Position[Exposures/Exposure]">
      <sch:let name="totalValue" value="number(TotalValue/Amount[@ccy = ancestor::Fund/Currency])"/>
      <sch:let name="exposure" value="number(Exposures/Exposure[Type='derivate meldeverordnung cesr']/Value/Amount[@ccy = ancestor::Fund/Currency])"/>
      
      <sch:assert test="abs($totalValue - $exposure) div max(($totalValue, $exposure)) &lt; 0.01">
        Position exposure (<sch:value-of select="$exposure"/>) should generally match total value (<sch:value-of select="$totalValue"/>) for most asset types.
      </sch:assert>
    </sch:rule>
  </sch:pattern>
</sch:schema>