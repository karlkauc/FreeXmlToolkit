/*
 * FreeXmlToolkit docs – live download links.
 *
 * Fills the download buttons on the home page and the version badge in the
 * announcement bar from the latest GitHub release. Without JavaScript (or when
 * the GitHub API is unreachable / rate-limited) every link keeps its static
 * fallback to https://github.com/karlkauc/FreeXmlToolkit/releases/latest.
 */
(function () {
  "use strict";

  var API_URL = "https://api.github.com/repos/karlkauc/FreeXmlToolkit/releases/latest";
  var CACHE_KEY = "fxt-latest-release";
  var CACHE_TTL_MS = 60 * 60 * 1000; // 1 hour

  // data-fxt-asset key -> regex on the release asset name
  var ASSET_PATTERNS = {
    "win-exe": /^FreeXmlToolkit-x64-.*\.exe$/i,
    "win-msi": /^FreeXmlToolkit-x64-.*\.msi$/i,
    "win-zip": /^FreeXmlToolkit-windows-x64-app-image-.*\.zip$/i,
    "mac-arm64-dmg": /^FreeXmlToolkit-arm64-.*\.dmg$/i,
    "mac-x64-dmg": /^FreeXmlToolkit-x64-.*\.dmg$/i,
    "mac-arm64-pkg": /^FreeXmlToolkit-arm64-.*\.pkg$/i,
    "mac-x64-pkg": /^FreeXmlToolkit-x64-.*\.pkg$/i,
    "mac-arm64-zip": /^FreeXmlToolkit-macos-arm64-app-image-.*\.zip$/i,
    "mac-x64-zip": /^FreeXmlToolkit-macos-x64-app-image-.*\.zip$/i,
    "linux-deb": /^freexmltoolkit-x64_.*_amd64\.deb$/i,
    "linux-rpm": /^freexmltoolkit-x64-.*\.x86_64\.rpm$/i,
    "linux-zip": /^FreeXmlToolkit-linux-x64-app-image-.*\.zip$/i
  };

  function readCache() {
    try {
      var raw = window.sessionStorage.getItem(CACHE_KEY);
      if (!raw) return null;
      var entry = JSON.parse(raw);
      if (!entry || typeof entry.time !== "number" || Date.now() - entry.time > CACHE_TTL_MS) return null;
      return entry.release;
    } catch (e) {
      return null;
    }
  }

  function writeCache(release) {
    try {
      window.sessionStorage.setItem(CACHE_KEY, JSON.stringify({ time: Date.now(), release: release }));
    } catch (e) {
      /* storage unavailable – ignore */
    }
  }

  function fetchRelease() {
    var cached = readCache();
    if (cached) return Promise.resolve(cached);
    if (typeof window.fetch !== "function") return Promise.reject(new Error("fetch unavailable"));
    return window
      .fetch(API_URL, { headers: { Accept: "application/vnd.github+json" } })
      .then(function (response) {
        if (!response.ok) throw new Error("GitHub API responded " + response.status);
        return response.json();
      })
      .then(function (json) {
        var release = {
          tag: json.tag_name || "",
          publishedAt: json.published_at || "",
          htmlUrl: json.html_url || "",
          assets: (json.assets || []).map(function (a) {
            return { name: a.name, url: a.browser_download_url, size: a.size };
          })
        };
        writeCache(release);
        return release;
      });
  }

  function findAsset(release, key) {
    var pattern = ASSET_PATTERNS[key];
    if (!pattern) return null;
    for (var i = 0; i < release.assets.length; i++) {
      if (pattern.test(release.assets[i].name)) return release.assets[i];
    }
    return null;
  }

  function formatSize(bytes) {
    if (!bytes || bytes <= 0) return "";
    var mb = bytes / (1024 * 1024);
    return (mb >= 100 ? Math.round(mb) : mb.toFixed(1)) + " MB";
  }

  function detectPlatform() {
    var ua = (window.navigator.userAgent || "").toLowerCase();
    var platform = (window.navigator.platform || "").toLowerCase();
    if (ua.indexOf("windows") !== -1 || platform.indexOf("win") === 0) return "windows";
    if (ua.indexOf("mac os") !== -1 || ua.indexOf("macintosh") !== -1 || platform.indexOf("mac") === 0) return "macos";
    if (ua.indexOf("linux") !== -1 && ua.indexOf("android") === -1) return "linux";
    return null;
  }

  function applyRelease(release) {
    var versionNodes = document.querySelectorAll(".fxt-release-version");
    for (var v = 0; v < versionNodes.length; v++) {
      versionNodes[v].textContent = release.tag;
    }
    var dateNodes = document.querySelectorAll(".fxt-release-date");
    if (release.publishedAt) {
      var date = new Date(release.publishedAt);
      var text = isNaN(date.getTime()) ? "" : date.toISOString().slice(0, 10);
      for (var d = 0; d < dateNodes.length; d++) dateNodes[d].textContent = text;
    }

    var links = document.querySelectorAll("a[data-fxt-asset]");
    for (var i = 0; i < links.length; i++) {
      var link = links[i];
      var asset = findAsset(release, link.getAttribute("data-fxt-asset"));
      if (!asset) continue;
      link.setAttribute("href", asset.url);
      link.setAttribute("title", asset.name);
      var sizeNode = link.querySelector(".fxt-asset-size");
      if (sizeNode) sizeNode.textContent = formatSize(asset.size);
    }
  }

  function highlightPlatform() {
    var current = detectPlatform();
    if (!current) return;
    var cards = document.querySelectorAll(".fxt-download-card[data-fxt-platform]");
    for (var i = 0; i < cards.length; i++) {
      var card = cards[i];
      if (card.getAttribute("data-fxt-platform") === current) {
        card.classList.add("fxt-download-card--current");
      } else {
        card.classList.remove("fxt-download-card--current");
      }
    }
  }

  function init() {
    var hasTargets =
      document.querySelector("a[data-fxt-asset]") || document.querySelector(".fxt-release-version");
    if (!hasTargets) return;
    highlightPlatform();
    fetchRelease().then(applyRelease).catch(function () {
      /* keep the static releases/latest fallbacks */
    });
  }

  // Material for MkDocs instant navigation re-renders the page without a full
  // reload; document$ emits on every navigation.
  if (window.document$ && typeof window.document$.subscribe === "function") {
    window.document$.subscribe(init);
  } else if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
