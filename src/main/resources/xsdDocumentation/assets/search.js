/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
 *
 * Client-side search functionality for generated XSD documentation.
 */
document.addEventListener('DOMContentLoaded', () => {
    const searchInput = document.getElementById('search-input');
    const searchResults = document.getElementById('search-results');
    const rootPath = document.body.dataset.rootPath || '.';
    const searchIndexPath = document.body.dataset.searchIndexPath || 'search_index.json';
    let searchIndex = [];

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

        if (query.length < 2) { // Start searching after 1 characters
            searchResults.innerHTML = '';
            searchResults.classList.add('hidden');
            return;
        }

        const results = searchIndex.filter(item =>
            item.name.toLowerCase().includes(query) ||
            item.xpath.toLowerCase().includes(query) ||
            item.doc.toLowerCase().includes(query)
        );

        displayResults(results, query);
    });

    function displayResults(results, query) {
        if (results.length === 0) {
            searchResults.innerHTML = '<div class="p-3 text-sm text-slate-500">No results found.</div>';
            searchResults.classList.remove('hidden');
            return;
        }

        const resultsHtml = results.slice(0, 15).map(item => { // Limit to 15 results
            const highlight = (text) => text.replace(new RegExp(query, 'gi'), (match) => `<strong class="text-sky-500 bg-sky-100 rounded-sm">${match}</strong>`);

            const name = highlight(item.name);
            const xpath = highlight(item.xpath);

            let docSnippet = '';
            const docIndex = item.doc.toLowerCase().indexOf(query);
            if (docIndex > -1) {
                const start = Math.max(0, docIndex - 40);
                const end = Math.min(item.doc.length, docIndex + query.length + 40);
                const snippet = item.doc.substring(start, end);
                docSnippet = `...${highlight(snippet)}...`;
            }

            // Construct the final URL using the rootPath
            const url = `${rootPath}/${item.url}`;

            return `
                <a href="${url}" class="block p-3 hover:bg-slate-50 border-b border-slate-100 last:border-b-0">
                    <div class="font-semibold text-slate-800 text-sm">${name}</div>
                    <div class="text-xs text-slate-500 font-mono truncate">${xpath}</div>
                    ${docSnippet ? `<div class="text-xs text-slate-600 mt-1">${docSnippet}</div>` : ''}
                </a>
            `;
        }).join('');

        searchResults.innerHTML = resultsHtml;
        searchResults.classList.remove('hidden');
    }

    // Hide results when clicking outside
    document.addEventListener('click', (e) => {
        if (searchResults && !searchInput.contains(e.target) && !searchResults.contains(e.target)) {
            searchResults.classList.add('hidden');
        }
    });

    // Also hide on 'Esc' key press
    document.addEventListener('keydown', (e) => {
        if (e.key === "Escape") {
            searchResults.classList.add('hidden');
        }
    });
});
