/* Search functionality for Antora with Lunr */
(function () {
  'use strict'

  var defined = function (val) {
    return typeof val !== 'undefined'
  }

  var searchInput = document.getElementById('search-input')
  var searchResults = document.getElementById('search-results')

  if (!searchInput || !searchResults) return

  var index = null
  var store = null
  var indexLoaded = false

  // Determine the base path for the search index
  var siteRootPath = document.body.dataset.siteRootPath || ''
  var indexPath = siteRootPath + '/search-index.js'

  // Load the search index
  var script = document.createElement('script')
  script.src = indexPath
  script.onload = function () {
    if (typeof antoraSearch !== 'undefined' && defined(antoraSearch.index) && defined(antoraSearch.store)) {
      index = lunr.Index.load(antoraSearch.index)
      store = antoraSearch.store
      indexLoaded = true
    }
  }
  document.head.appendChild(script)

  // Load Lunr if not already loaded
  if (typeof lunr === 'undefined') {
    var lunrScript = document.createElement('script')
    lunrScript.src = 'https://unpkg.com/lunr@2.3.9/lunr.min.js'
    document.head.appendChild(lunrScript)
  }

  searchInput.addEventListener('input', debounce(search, 200))
  searchInput.addEventListener('focus', function () {
    if (this.value.length > 1) search()
  })

  document.addEventListener('click', function (e) {
    if (!e.target.closest('.search')) {
      searchResults.innerHTML = ''
      searchResults.style.display = 'none'
    }
  })

  function search () {
    var query = searchInput.value.trim()

    if (query.length < 2) {
      searchResults.innerHTML = ''
      searchResults.style.display = 'none'
      return
    }

    if (!indexLoaded) {
      searchResults.innerHTML = '<div class="search-result-item">Loading search index...</div>'
      searchResults.style.display = 'block'
      return
    }

    var results = []
    try {
      results = index.search(query)
    } catch (e) {
      // Try wildcard search if exact search fails
      try {
        results = index.search(query + '*')
      } catch (e2) {
        results = []
      }
    }

    if (results.length === 0) {
      searchResults.innerHTML = '<div class="search-result-item">No results found</div>'
      searchResults.style.display = 'block'
      return
    }

    var html = results.slice(0, 10).map(function (result) {
      var doc = store[result.ref]
      if (!doc) return ''
      var title = doc.title || 'Untitled'
      var text = doc.text || ''
      if (text.length > 150) text = text.substring(0, 150) + '...'
      return '<div class="search-result-item"><a href="' + doc.url + '">' +
        '<div class="title">' + escapeHtml(title) + '</div>' +
        '<div class="text">' + escapeHtml(text) + '</div>' +
        '</a></div>'
    }).join('')

    searchResults.innerHTML = html
    searchResults.style.display = 'block'
  }

  function debounce (fn, delay) {
    var timeout
    return function () {
      var context = this
      var args = arguments
      clearTimeout(timeout)
      timeout = setTimeout(function () {
        fn.apply(context, args)
      }, delay)
    }
  }

  function escapeHtml (text) {
    var div = document.createElement('div')
    div.textContent = text
    return div.innerHTML
  }
})()
