/**
 * Language Switcher for XSD Documentation
 *
 * Provides on-the-fly language switching for multi-language XSD documentation.
 * Languages are stored in localStorage for persistence across pages.
 *
 * Usage:
 * - Include this script in all HTML templates
 * - Call initLanguageSwitcher() on DOMContentLoaded
 * - Documentation elements should have data-lang attribute
 */

(function () {
    'use strict';

    const STORAGE_KEY = 'xsdDocLang';
    const DEFAULT_LANG = 'default';

    // Fallback language loaded from languages.json
    var configuredFallbackLang = null;

    /**
     * Get the stored language preference from localStorage
     * @returns {string} The stored language or 'default'
     */
    function getStoredLanguage() {
        try {
            return localStorage.getItem(STORAGE_KEY) || DEFAULT_LANG;
        } catch (e) {
            // localStorage might be unavailable (e.g., file:// protocol in some browsers)
            console.warn('localStorage unavailable, using default language');
            return DEFAULT_LANG;
        }
    }

    /**
     * Store the language preference in localStorage
     * @param {string} lang - The language code to store
     */
    function storeLanguage(lang) {
        try {
            localStorage.setItem(STORAGE_KEY, lang);
        } catch (e) {
            console.warn('Could not store language preference');
        }
    }

    /**
     * Switch the documentation language globally
     * @param {string} lang - The language code to switch to
     */
    function switchLanguage(lang) {
        console.log('[LangSwitcher] Switching to language:', lang);
        // Store the preference
        storeLanguage(lang);

        // Find all documentation sections
        var sections = document.querySelectorAll('.documentation-section');
        console.log('[LangSwitcher] Found documentation sections:', sections.length);

        sections.forEach(function (section) {
            var allDocs = section.querySelectorAll('.doc-content');
            console.log('[LangSwitcher] Found doc-content elements in section:', allDocs.length);
            var found = false;

            // Hide all, show selected language (using CSS classes only, no hidden attribute)
            allDocs.forEach(function (doc) {
                console.log('[LangSwitcher] Checking doc with lang:', doc.dataset.lang);
                if (doc.dataset.lang === lang) {
                    console.log('[LangSwitcher] SHOWING:', doc.dataset.lang);
                    // Remove hidden attribute if present (for backwards compatibility)
                    doc.removeAttribute('hidden');
                    // Use CSS classes for visibility control
                    doc.classList.remove('lang-hidden');
                    doc.classList.add('lang-visible');
                    found = true;
                } else {
                    console.log('[LangSwitcher] HIDING:', doc.dataset.lang);
                    // Use CSS classes for visibility control (not hidden attribute)
                    doc.classList.remove('lang-visible');
                    doc.classList.add('lang-hidden');
                }
            });

            // Fallback logic when selected language not found
            if (!found) {
                console.log('[LangSwitcher] Language not found, using fallback logic');
                // First, try to show 'default' (no language tag) documentation
                var defaultDoc = section.querySelector('[data-lang="default"]');
                console.log('[LangSwitcher] defaultDoc found:', defaultDoc !== null);
                if (defaultDoc) {
                    console.log('[LangSwitcher] Showing default as fallback');
                    defaultDoc.removeAttribute('hidden');
                    defaultDoc.classList.remove('lang-hidden');
                    defaultDoc.classList.add('lang-visible');
                } else if (configuredFallbackLang) {
                    console.log('[LangSwitcher] Trying configured fallback:', configuredFallbackLang);
                    // If no default documentation exists, use the configured fallback language
                    var fallbackDoc = section.querySelector('[data-lang="' + configuredFallbackLang + '"]');
                    if (fallbackDoc) {
                        fallbackDoc.removeAttribute('hidden');
                        fallbackDoc.classList.remove('lang-hidden');
                        fallbackDoc.classList.add('lang-visible');
                    }
                } else {
                    // Last resort: show the first available documentation
                    var firstDoc = allDocs[0];
                    if (firstDoc) {
                        console.log('[LangSwitcher] Showing first available as last resort:', firstDoc.dataset.lang);
                        firstDoc.removeAttribute('hidden');
                        firstDoc.classList.remove('lang-hidden');
                        firstDoc.classList.add('lang-visible');
                    }
                }
            } else {
                console.log('[LangSwitcher] Language found in section');
            }
        });

        // Update all language switcher dropdowns on the page
        document.querySelectorAll('.lang-switcher').forEach(function (switcher) {
            switcher.value = lang;
        });

        // Update active state in language buttons (if using button-style switcher)
        document.querySelectorAll('.lang-btn').forEach(function (btn) {
            if (btn.dataset.lang === lang) {
                btn.classList.add('active');
            } else {
                btn.classList.remove('active');
            }
        });
    }

    /**
     * Populate language switcher dropdown with available languages
     * @param {HTMLSelectElement} switcher - The select element to populate
     * @param {Array<string>} languages - Array of available language codes
     */
    function populateLanguageSwitcher(switcher, languages) {
        // Clear existing options except the first placeholder if any
        switcher.innerHTML = '';

        // Add default option first
        var defaultOption = document.createElement('option');
        defaultOption.value = 'default';
        defaultOption.textContent = 'Default (no language)';
        switcher.appendChild(defaultOption);

        // Add other languages
        languages.forEach(function (lang) {
            if (lang !== 'default') {
                var option = document.createElement('option');
                option.value = lang;
                option.textContent = lang.toUpperCase();
                switcher.appendChild(option);
            }
        });

        // Set current value
        switcher.value = getStoredLanguage();
    }

    // Whether to show the language switcher (determined from languages.json)
    var shouldShowSwitcher = true;
    // Whether to show the SVG page link (determined from languages.json)
    var shouldShowSvgPage = true;

    /**
     * Load available languages and configuration from languages.json
     * @param {function} callback - Callback function receiving the languages array
     */
    function loadAvailableLanguages(callback) {
        // Determine the base path for languages.json
        // Check if we're in a subdirectory (details/, complexTypes/, simpleTypes/)
        var rootPath = document.body.dataset.rootPath || '.';
        var jsonPath = rootPath + '/languages.json';

        // Also check for meta tag
        var basePath = document.querySelector('meta[name="base-path"]');
        if (basePath && basePath.content) {
            jsonPath = basePath.content + 'languages.json';
        }

        fetch(jsonPath)
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('languages.json not found');
                }
                return response.json();
            })
            .then(function (data) {
                // Store the configured fallback language
                if (data.fallback && data.fallback !== 'null') {
                    configuredFallbackLang = data.fallback;
                }
                // Store whether to show the language switcher
                shouldShowSwitcher = data.showSwitcher !== false;
                // Store whether to show the SVG page link
                shouldShowSvgPage = data.showSvgPage !== false;
                callback(data.available || ['default']);
            })
            .catch(function (error) {
                console.warn('Could not load languages.json, scanning page for languages:', error.message);
                // Fallback: scan the page for available languages
                var languages = new Set(['default']);
                document.querySelectorAll('[data-lang]').forEach(function (el) {
                    languages.add(el.dataset.lang);
                });
                var langArray = Array.from(languages).sort();
                // If only 'default' language found, hide the switcher
                shouldShowSwitcher = langArray.length > 1;
                callback(langArray);
            });
    }

    /**
     * Hide the SVG page link in the navigation if SVG page was not generated
     */
    function hideSvgPageLink() {
        // Find all links to schema-svg.html and hide them
        document.querySelectorAll('a[href*="schema-svg.html"]').forEach(function (link) {
            link.style.display = 'none';
        });
    }

    /**
     * Get the configured fallback language
     * @returns {string|null} The fallback language or null if not configured
     */
    function getFallbackLanguage() {
        return configuredFallbackLang;
    }

    /**
     * Initialize the language switcher
     * Called on DOMContentLoaded
     */
    function initLanguageSwitcher() {
        var switchers = document.querySelectorAll('.lang-switcher');
        console.log('[LangSwitcher] Found switchers:', switchers.length);

        // Load available languages (this also loads the fallback configuration)
        loadAvailableLanguages(function (languages) {
            console.log('[LangSwitcher] Available languages:', languages);
            console.log('[LangSwitcher] shouldShowSwitcher:', shouldShowSwitcher);
            // Hide SVG page link if not generated
            if (!shouldShowSvgPage) {
                hideSvgPageLink();
            }

            // Check if we should show the language switcher
            if (!shouldShowSwitcher) {
                // Hide all language switcher containers (the parent div with label)
                switchers.forEach(function (switcher) {
                    var container = switcher.closest('.flex.items-center.space-x-2');
                    if (container) {
                        container.style.display = 'none';
                    } else {
                        // Fallback: hide just the switcher if container not found
                        switcher.style.display = 'none';
                    }
                });
                // Still apply default language for any existing documentation
                switchLanguage('default');
                return;
            }

            // Populate all switchers
            switchers.forEach(function (switcher) {
                populateLanguageSwitcher(switcher, languages);
                console.log('[LangSwitcher] Populated switcher with options:', switcher.options.length);

                // Add change event listener
                switcher.addEventListener('change', function (e) {
                    console.log('[LangSwitcher] Change event fired, new value:', e.target.value);
                    switchLanguage(e.target.value);
                });
            });

            // Apply stored language preference
            var storedLang = getStoredLanguage();
            switchLanguage(storedLang);
        });
    }

    // Export functions to global scope
    window.XsdDocLang = {
        switchLanguage: switchLanguage,
        getStoredLanguage: getStoredLanguage,
        getFallbackLanguage: getFallbackLanguage,
        initLanguageSwitcher: initLanguageSwitcher,
        loadAvailableLanguages: loadAvailableLanguages
    };

    // Auto-initialize on DOMContentLoaded
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initLanguageSwitcher);
    } else {
        // DOM already loaded
        initLanguageSwitcher();
    }

})();
