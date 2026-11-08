# Industry Plugins

FreeXmlToolkit ships optional [plugins](plugins.md) for industry standards. They are **off by
default**: switch them on in **Settings → Extensions → PLUGINS** and restart. Each one then adds

- an **activity** in the Activity Bar with a side panel: what the active document is, a
  **Check Active Document** button, sample documents and an identifier checker;
- **checks** that run with every validation (Validation panel, **F8**, `fxt-cli validate`) and appear
  with the plugin's name as source;
- where the standard has official XML Schemas, **Schema Library entries** (source *Plugin*), so
  documents get their schema bound automatically. Remote schemas are downloaded once and cached.

The identifier checker in the side panels checks IBAN (ISO 13616, country length and check
digits), BIC (ISO 9362), LEI (ISO 17442), ISIN (ISO 6166) and the Austrian VAT id (UID).

## ISO 20022 Payments (SEPA)

For `pain`, `pacs` and `camt` messages (namespace `urn:iso:std:iso:20022:tech:xsd:…`).

**Schemas** of these versions are bound automatically, downloaded from iso20022.org:
pain.001.001.03/.09/.12, pain.002.001.03/.10, pain.007.001.09, pain.008.001.02/.08/.11,
pacs.002.001.10, pacs.004.001.09, pacs.008.001.08/.13, camt.052.001.08, camt.053.001.02/.08,
camt.054.001.02/.08, camt.056.001.08. Other versions: add them in the [Schema Library](schema-library.md).

**Checks** (all messages):

- IBAN (`IBAN`), BIC (`BICFI`, `BIC`, `AnyBIC`) and LEI (`LEI`) values, including check digits
- currency codes (`Ccy`) are three capital letters

Payment initiations `pain.001` and `pain.008`, and `pacs.008`:

- `GrpHdr/NbOfTxs` and `GrpHdr/CtrlSum` match the transactions of the message
- `PmtInf/NbOfTxs` and `PmtInf/CtrlSum` match the transactions of their payment group

Payment groups with service level `SEPA` (`PmtTpInf/SvcLvl/Cd`), after the EPC implementation
guidelines 2025 (SCT and SDD Core, customer-to-PSP):

- amounts in `EUR` only, between 0.01 and 999 999 999.99, at most two decimals
- `MsgId`, `PmtInfId`, `InstrId`, `EndToEndId` and `MndtId` use only the SEPA character set
  (`a-z A-Z 0-9 / - ? : ( ) . , ' +` and space), do not start or end with `/` and contain no `//`
- the charge bearer `ChrgBr` is `SLEV`

**Side panel:** the message type and name, the number of payment groups and transactions, their
total and whether it is SEPA; **New SEPA Credit Transfer (pain.001)** and **New SEPA Direct Debit
(pain.008)** open sample messages (schema-valid, version 2025) to start from.
