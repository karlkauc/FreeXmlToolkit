/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

document.addEventListener('DOMContentLoaded', () => {
    const searchInput = document.getElementById('search-input');
    const searchResults = document.getElementById('search-results');

    // Get paths from data attributes in the body tag for better flexibility
    const rootPath = document.body.dataset.rootPath || '.';
    const searchIndexPath = document.body.dataset.searchIndexPath || 'search_index.json';
    let searchIndex = [];
    let activeIndex = -1; // Track the currently selected item

    /**
     * Escapes special characters in a string for use in a regular expression.
     * @param {string} string The string to escape.
     * @returns {string} The escaped string.
     */
    function escapeRegex(string) {
        return string.replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&');
    }

    /**
     * Highlights occurrences of a query in a text string.
     * @param {string} text The text to search within.
     * @param {string} query The search query.
     * @returns {string} The text with the query highlighted.
     */
    function highlightText(text, query) {
        if (!query || !text) {
            return text;
        }
        const safeQuery = escapeRegex(query);
        const regex = new RegExp(safeQuery, 'gi');
        return text.replace(regex, (match) => `<strong class="bg-sky-200 text-sky-800 font-semibold">${match}</strong>`);
    }

    // Fetch the search index data
    fetch(searchIndexPath)
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return response.json();
        })
        .then(data => {
            searchIndex = data;
            searchInput.disabled = false;
            searchInput.placeholder = "Search name, xpath, docs...";
        })
        .catch(error => {
            console.error('Error loading search index:', error);
            searchInput.placeholder = "Search index failed to load.";
        });

    searchInput.addEventListener('input', (e) => {
        const query = e.target.value.toLowerCase().trim();
        searchResults.innerHTML = '';
        activeIndex = -1;

        if (query.length < 2) {
            searchResults.classList.add('hidden');
            return;
        }

        const results = searchIndex.filter(item =>
            item.name.toLowerCase().includes(query) ||
            item.xpath.toLowerCase().includes(query) ||
            item.doc.toLowerCase().includes(query)
        ).slice(0, 15);

        if (results.length > 0) {
            searchResults.classList.remove('hidden');
            results.forEach(item => {
                const resultItem = document.createElement('a');
                resultItem.href = `${rootPath}/${item.url}`;
                resultItem.className = 'block px-4 py-3 text-sm text-slate-700 hover:bg-sky-100 hover:text-slate-800 border-b border-slate-100 last:border-b-0';

                const highlightedName = highlightText(item.name, query);
                const highlightedXpath = highlightText(item.xpath, query);
                const docSnippet = item.doc.length > 120 ? item.doc.substring(0, 120) + '...' : item.doc;
                const highlightedDoc = highlightText(docSnippet, query);

                resultItem.innerHTML = `
                    <div class="font-bold truncate">${highlightedName}</div>
                    <div class="text-xs text-slate-500 font-mono truncate">${highlightedXpath}</div>
                    ${docSnippet ? `<div class="text-xs text-slate-500 mt-1">${highlightedDoc}</div>` : ''}
                `;
                searchResults.appendChild(resultItem);
            });
        } else {
            searchResults.classList.add('hidden');
        }
    });

    searchInput.addEventListener('keydown', (e) => {
        const items = searchResults.querySelectorAll('a');
        if (items.length === 0) return;

        switch (e.key) {
            case 'ArrowDown':
                e.preventDefault();
                activeIndex = (activeIndex + 1) % items.length;
                updateActiveItem(items);
                break;
            case 'ArrowUp':
                e.preventDefault();
                activeIndex = (activeIndex - 1 + items.length) % items.length;
                updateActiveItem(items);
                break;
            case 'Enter':
                e.preventDefault();
                if (activeIndex > -1 && items[activeIndex]) {
                    items[activeIndex].click();
                }
                break;
            case 'Escape':
                searchResults.classList.add('hidden');
                break;
        }
    });

    function updateActiveItem(items) {
        items.forEach(item => item.classList.remove('bg-sky-100'));
        if (activeIndex > -1) {
            items[activeIndex].classList.add('bg-sky-100');
            items[activeIndex].scrollIntoView({ block: 'nearest' });
        }
    }

    document.addEventListener('click', (e) => {
        if (!searchResults.contains(e.target) && e.target !== searchInput) {
            searchResults.classList.add('hidden');
        }
    });
});