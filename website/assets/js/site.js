/*
 * freexmltoolkit.org - small progressive enhancements. Every page works without this script:
 * links fall back to the download page or to GitHub's releases/latest.
 *
 * 1. Mobile menu toggle.
 * 2. Detects the visitor's operating system and adapts the "Download for …" buttons and the
 *    recommendation box on the download page.
 * 3. Reads the latest release from the GitHub API (cached for an hour in sessionStorage) and fills
 *    version, date and direct asset links. No cookies; the request goes from the browser to GitHub.
 */
(function () {
  "use strict";

  document.documentElement.classList.add("js");

  var API_URL = "https://api.github.com/repos/karlkauc/FreeXmlToolkit/releases/latest";
  var CACHE_KEY = "fxt-latest-release";
  var CACHE_TTL_MS = 60 * 60 * 1000;

  // data-fxt-asset key -> regex on the release asset name (same as docs/overrides/javascripts/download.js)
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

  var PLATFORMS = {
    windows: { name: "Windows", title: "Windows, 64-bit", asset: "win-exe", button: "Download .exe installer",
      note: "No administrator rights required. Not your system? Pick a package below." },
    macos: { name: "macOS", title: "macOS, Apple Silicon", asset: "mac-arm64-dmg", button: "Download .dmg",
      note: "Signed and notarized by Apple. On an Intel Mac, take the Intel .dmg below." },
    linux: { name: "Linux", title: "Linux, 64-bit", asset: "linux-deb", button: "Download .deb package",
      note: "For Debian and Ubuntu. Fedora, RHEL and openSUSE: take the .rpm below." }
  };

  function detectPlatform() {
    var ua = (navigator.userAgent || "").toLowerCase();
    var platform = (navigator.platform || "").toLowerCase();
    if (/iphone|ipad|android/.test(ua)) return null;
    if (ua.indexOf("windows") !== -1 || platform.indexOf("win") === 0) return "windows";
    if (ua.indexOf("mac os") !== -1 || ua.indexOf("macintosh") !== -1 || platform.indexOf("mac") === 0) return "macos";
    if (ua.indexOf("linux") !== -1) return "linux";
    return null;
  }

  function each(selector, fn) {
    var nodes = document.querySelectorAll(selector);
    for (var i = 0; i < nodes.length; i++) fn(nodes[i]);
  }

  function setupMenu() {
    var toggle = document.querySelector(".menu-toggle");
    var nav = document.getElementById("site-nav");
    if (!toggle || !nav) return;
    toggle.addEventListener("click", function () {
      var open = nav.classList.toggle("open");
      toggle.setAttribute("aria-expanded", open ? "true" : "false");
    });
  }

  function applyPlatform(os) {
    if (!os) return;
    var p = PLATFORMS[os];
    each("[data-os-name]", function (n) { n.textContent = p.name; });
    each("[data-os-title]", function (n) { n.textContent = p.title; });
    each("[data-os-note]", function (n) { n.textContent = p.note; });
    each("[data-os-button]", function (n) { n.textContent = p.button; });
    each("a[data-os-asset]", function (a) { a.setAttribute("data-fxt-asset", p.asset); });
  }

  function readCache() {
    try {
      var entry = JSON.parse(sessionStorage.getItem(CACHE_KEY) || "null");
      if (!entry || Date.now() - entry.time > CACHE_TTL_MS) return null;
      return entry.release;
    } catch (e) {
      return null;
    }
  }

  function writeCache(release) {
    try {
      sessionStorage.setItem(CACHE_KEY, JSON.stringify({ time: Date.now(), release: release }));
    } catch (e) {
      /* storage unavailable */
    }
  }

  function fetchRelease() {
    var cached = readCache();
    if (cached) return Promise.resolve(cached);
    if (typeof fetch !== "function") return Promise.reject(new Error("fetch unavailable"));
    return fetch(API_URL, { headers: { Accept: "application/vnd.github+json" } })
      .then(function (r) {
        if (!r.ok) throw new Error("GitHub API " + r.status);
        return r.json();
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
    if (!bytes) return "";
    var mb = bytes / (1024 * 1024);
    return (mb >= 100 ? Math.round(mb) : mb.toFixed(1)) + " MB";
  }

  function formatDate(iso) {
    var d = new Date(iso);
    if (isNaN(d.getTime())) return "";
    var months = ["January", "February", "March", "April", "May", "June", "July", "August", "September",
      "October", "November", "December"];
    return d.getUTCDate() + " " + months[d.getUTCMonth()] + " " + d.getUTCFullYear();
  }

  function applyRelease(release) {
    var version = release.tag.replace(/^v/, "");
    if (version) each("[data-fxt-version]", function (n) { n.textContent = version; });
    var date = formatDate(release.publishedAt);
    if (date) each("[data-fxt-date]", function (n) { n.textContent = date; });
    if (release.htmlUrl) each("a[data-fxt-notes]", function (a) { a.setAttribute("href", release.htmlUrl); });
    each("a[data-fxt-asset]", function (a) {
      var asset = findAsset(release, a.getAttribute("data-fxt-asset"));
      if (!asset) return;
      a.setAttribute("href", asset.url);
      a.setAttribute("title", asset.name);
      var size = a.querySelector("[data-fxt-size]");
      if (size) size.textContent = formatSize(asset.size);
    });
  }

  function init() {
    setupMenu();
    applyPlatform(detectPlatform());
    if (document.querySelector("a[data-fxt-asset], [data-fxt-version]")) {
      fetchRelease().then(applyRelease).catch(function () {
        /* keep the static fallbacks */
      });
    }
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
  else init();
})();
