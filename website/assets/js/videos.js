/*
 * freexmltoolkit.org/videos/ - one player for every film on the page.
 * The page works without this script: each entry links to its MP4 and the chapters jump into it (#t=).
 * With it, a click plays the film in the player above, chapters seek, #<film-id> opens a film,
 * and a series plays on to its next film.
 */
(function () {
  "use strict";
  var player = document.getElementById("video-player");
  if (!player) {
    return;
  }
  var items = Array.prototype.slice.call(document.querySelectorAll(".video-item"));
  var titleOut = document.getElementById("video-now-title");
  var descOut = document.getElementById("video-now-desc");
  var docsOut = document.getElementById("video-now-docs");
  var chaptersOut = document.createElement("ol");
  chaptersOut.className = "video-chapters video-now-chapters";
  document.querySelector(".video-now").appendChild(chaptersOut);
  var current = null;

  function text(item, selector) {
    var node = item.querySelector(selector);
    return node ? node.textContent : "";
  }

  function track(kind, src, item) {
    var t = document.createElement("track");
    t.kind = kind;
    t.src = src;
    t.srclang = item.dataset.lang;
    if (kind === "subtitles") {
      t.label = item.dataset.label;
    }
    return t;
  }

  function seek(seconds) {
    player.currentTime = seconds;
    player.play();
  }

  function showChapters(item) {
    chaptersOut.textContent = "";
    var list = item.querySelector(".video-chapters");
    if (!list) {
      return;
    }
    chaptersOut.setAttribute("aria-label", list.getAttribute("aria-label"));
    Array.prototype.forEach.call(list.querySelectorAll("a"), function (a) {
      var li = document.createElement("li");
      var copy = a.cloneNode(true);
      copy.addEventListener("click", function (event) {
        event.preventDefault();
        seek(Number(copy.dataset.t));
      });
      li.appendChild(copy);
      chaptersOut.appendChild(li);
    });
  }

  function open(item, play, scroll, keepHash) {
    if (current) {
      current.removeAttribute("aria-current");
    }
    current = item;
    item.setAttribute("aria-current", "true");
    var subtitlesOn = Array.prototype.some.call(player.textTracks, function (t) {
      return t.kind === "subtitles" && t.mode === "showing";
    });
    player.pause();
    player.textContent = "";
    var source = document.createElement("source");
    source.src = item.dataset.src;
    source.type = "video/mp4";
    player.appendChild(source);
    if (item.dataset.vtt) {
      var sub = track("subtitles", item.dataset.vtt, item);
      sub.default = subtitlesOn;
      player.appendChild(sub);
    }
    if (item.dataset.chapters) {
      player.appendChild(track("chapters", item.dataset.chapters, item));
    }
    player.poster = item.dataset.poster;
    player.load();
    titleOut.textContent = text(item, ".video-title");
    descOut.textContent = text(item, ".video-desc");
    if (item.dataset.docs) {
      docsOut.href = item.dataset.docs;
      docsOut.hidden = false;
    } else {
      docsOut.hidden = true;
    }
    showChapters(item);
    if (history.replaceState && !keepHash) {
      history.replaceState(null, "", "#" + item.id);
    }
    if (scroll) {
      player.scrollIntoView({ behavior: "smooth", block: "center" });
    }
    if (play) {
      var started = player.play();
      if (started && started.catch) {
        started.catch(function () { /* autoplay refused: the user presses play */ });
      }
    }
  }

  items.forEach(function (item) {
    item.querySelector(".video-open").addEventListener("click", function (event) {
      event.preventDefault();
      open(item, true, true);
    });
    Array.prototype.forEach.call(item.querySelectorAll(".video-chapters a"), function (a) {
      a.addEventListener("click", function (event) {
        event.preventDefault();
        if (current !== item) {
          open(item, false, true);
        }
        var t = Number(a.dataset.t);
        if (player.readyState >= 1) {
          seek(t);
        } else {
          player.addEventListener("loadedmetadata", function once() {
            player.removeEventListener("loadedmetadata", once);
            seek(t);
          });
        }
      });
    });
  });

  // a series plays on: the next entry of the same list
  player.addEventListener("ended", function () {
    var next = current && current.nextElementSibling;
    if (next && next.classList.contains("video-item")) {
      open(next, true, false);
    }
  });

  var wanted = location.hash ? document.getElementById(decodeURIComponent(location.hash.slice(1))) : null;
  if (wanted && wanted.classList.contains("video-item")) {
    open(wanted, false, false);
    // after the browser's own jump to the list entry: show the player instead
    window.addEventListener("load", function () {
      player.scrollIntoView({ block: "center" });
    });
  } else if (items.length) {
    open(items[0], false, false, true);   // the first film, without touching the address (#series stays)
  }
}());
