ich möchte in den xsl seiten (xslt viewer, xslt developer, FOP) die - falls vorhanden - verknüpften xsl files automatisch laden.

zum beispiel:
<?xml version="1.0" encoding="UTF-8"?>
<?xml-stylesheet href="stylesheet.xsl" type="text/xsl"?>

oder:
<?xml-stylesheet href="stylesheet.xsl" type="text/xsl"?>
<?xml version="1.0" encoding="UTF-8"?>

wenn ein xml file geladen wird, welches ein xml-stylesheet verknüpft hat, soll das verknüpfte file automatisch geladen werden werden. 
das stylesheet soll nur geladen werde, wenn der user nicht bereits vorher in der ui ein eigenes stylesheet geladen hat. 
die stylesheets können lokal liegen (href="stylesheet.xsl"). dann sollen wir im selben verzeichnis geladen werden wie auch das xml file liegt. 
sie können aber auch absolut (lokal oder remote) oder relativ zum xml file liegen. 
sollte ein stylesheet verknüpft sein, welches nicht geladen werden kann (oder fehlerhaft ist) soll dem user ein warning angezeigt werden und das file nicht geladen werden. 

