document.addEventListener('DOMContentLoaded', function () {
    processInfos("ERROR", "list_errors", "Error");
    processInfos("WARNING", "list_warnings", "Warning");
}, false);


function processInfos(selectorForInfos, divNameToWriteIn, infoType) {
    const infoArray = document.querySelectorAll("*[id^=" + selectorForInfos + "]");
    const divToWriteIn = document.getElementById(divNameToWriteIn);

    if (infoArray.length > 0) {
        const infoText = infoArray.length + " " + infoType + "s  found.";
        console.log(infoText);

        divToWriteIn.appendChild(document.createTextNode(infoText))
        divToWriteIn.appendChild(document.createElement("ol"));

        for (let i = 0; i < infoArray.length; i++) {
            console.log("ERROR: " + infoArray[i].dataset.errorMessage);

            const li = document.createElement("li");

            const a = document.createElement('a');
            const link = document.createTextNode(infoArray[i].dataset.errorMessage);
            a.appendChild(link);
            a.title = infoArray[i].dataset.errorMessage;
            a.href = "#" + infoArray[i].id;

            li.appendChild(a);

            divToWriteIn.appendChild(li);
        }

    } else {
        console.log("NO " + infoType + " FOUND");
        divToWriteIn.appendChild(document.createTextNode("No " + infoType + "s found!"));
    }
}

