ich möchte, dass der xml editor folgende neue eigenschaften hat:

[ ] Schematron einbindung. Ich möchte die Möglichkeit haben Schematron Regeln gegen ein XML file zu prüfen.
[ ] intellisense code vervollständigung.
[ ] verwende den LSP server um eine code vervollständigung einzubauen. die code vervollständigung soll den knotennamen
per tab vervollständigen, wenn ein user einen neuen knoten eingibt welcher laut xsd schema vorgegeben ist.
[ ] wenn der user einen neuen xml knoten öffnet, erstelle automatisch den schließen eintrag. also zum beispiel: wenn der
user "<name>" erstellt, erstelle automatisch nach dem neuen knoten den schließ tag "</name>".

befolge dabei folglendes:

* verwende für die grafischen element nur native javafx methoden.
* schreibe entsprechende testmethoden um die korrektheit des codes zu überprüfen.
* verwende wenn möglich die fähigkeiten des eingebauten LSP servers.
* überarbeite den code mehrfach, damit er allgemeinen java prinzipien entspricht, gut strukturiert und kommentiert ist. 
