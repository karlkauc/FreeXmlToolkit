Rolle: Du bist ein erfahrener Softwareentwickler, spezialisiert auf die Entwicklung von Code-Editoren und die
Integration von Language Server Protocols (LSP).

Ziel: Implementiere eine IntelliSense-Funktion für meinen bestehenden XML-Texteditor. Der Editor ist
in Java geschrieben. Die Intelligenz soll über einen bereits vorhandenen XML Language Server bereitgestellt werden.

Vorhandener Code-Kontext:
Mein Editor hat eine zentrale Klasse 'XmlEditor', die für die Textmanipulation und UI-Events zuständig ist. Die
wichtigsten Funktionen sind:

* getText(): Gibt den gesamten Textinhalt des Editors zurück.
* on('textChanged', callback): Ein Event-Listener, der bei Textänderungen ausgelöst wird.
* getCursorPosition(): Gibt die aktuelle Zeilen- und Spaltennummer des Cursors zurück.
* showCompletionItems(items): Eine Funktion, um eine Vorschlagsliste an der Cursor-Position anzuzeigen.
* showHoverInfo(info): Eine Funktion, um eine Tooltip-Box mit Informationen anzuzeigen.
* applyEdits(edits): Eine Funktion, um vom Server vorgeschlagene Textänderungen anzuwenden.
* highlightErrors(diagnostics): Eine Funktion, um Fehler und Warnungen im Text zu markieren.

Dokumenten-Synchronisation:

* Nutze den textChanged-Event meines Editors, um den Server über Änderungen im Dokument zu informieren.
* Sende textDocument/didOpen, sobald eine XML-Datei geladen wird.
* Sende textDocument/didChange bei jeder Textänderung. Implementiere hierfür eine inkrementelle Synchronisation, falls
  möglich, andernfalls eine vollständige Synchronisation (Full).
* Sende textDocument/didClose, wenn die Datei geschlossen wird.

Implementierung der IntelliSense-Funktionen:

Auto-Vervollständigung (Completion):

* Wenn der Benutzer tippt (insbesondere nach dem Öffnen eines Tags < oder dem Hinzufügen eines Leerzeichens für ein
  Attribut), sende eine textDocument/completion-Anfrage an den LSP-Server.
* Die Anfrage muss die aktuelle Textdokument-URI und die Cursor-Position enthalten.
* Verarbeite die Antwort vom Server und nutze meine showCompletionItems(items)-Funktion, um die Vorschläge (Tags,
  Attribute, Werte) anzuzeigen.

Hover-Informationen:

Wenn der Benutzer mit der Maus über einem Tag oder Attribut verweilt, sende eine textDocument/hover-Anfrage an den
Server.

Zeige die zurückgelieferte Dokumentation oder Typinformation mithilfe meiner showHoverInfo(info)-Funktion an.

Diagnose und Fehler-Highlighting (Linting):

Der LSP-Server sendet von sich aus textDocument/publishDiagnostics-Benachrichtigungen, wenn er Fehler oder Warnungen im
XML-Code findet (z.B. aufgrund einer zugeordneten XSD/DTD).

Empfange diese Benachrichtigungen und nutze meine highlightErrors(diagnostics)-Funktion, um die entsprechenden Zeilen im
Editor zu markieren.

Gewünschtes Code-Format:

Stelle den Code in der Klasse XmlEditor bereit.

Kommentiere die Schlüsselbereiche, insbesondere die Interaktion zwischen dem Editor und der LspClient-Klasse.

Strukturiere den Code modular, sodass die LSP-Logik klar vom UI-Code des Editors getrennt ist.

Füge ein kurzes Beispiel hinzu, wie der LspClient in meiner XmlEditor-Klasse instanziiert und verwendet wird.

Bitte beginne mit der Implementierung der LspClient-Klasse.