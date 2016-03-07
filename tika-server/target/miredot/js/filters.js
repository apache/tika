'use strict';

/* Filters */

angular.module('miredot.filters', ['ngSanitize', 'ui.filters']).
    filter('formatUrlParams', function(){
        return function(value) {
            if (!value) {
                return value;
            }
            return value.toString().replace(new RegExp("{(.*?)}", 'gi'), '<span class="paramName">$1</span>');
        };
    }).
    filter('capitaliseFirstLetter', function(){
        return function (string) {
            return string.charAt(0).toUpperCase() + string.slice(1).toLowerCase();
        };
    }).
    filter('arraySort', function() {
        return function(input) {
            return input.sort();
        }
    }).
    filter('searchByExampleFilter', function() {
        /**
         * Removes the domain (and protocol) from an uri. Requires / after domain part.
         * @param uri
         */
        function removeDomain(uri) {
            return uri.replace(/^.*\/\/[^\/]+/, '');
        }

        /**
         * Builds a regexp for the given interface
         * @param iface the interface to get the regex for
         * @return a case insensitive regex for the current interface
         */
        function getRegExp(iface) {
            var url = iface.url.stripTrailingSlash();

            // Escape regexp special characters.
            // url = url.replace(/[-\/\\^$*+?.()|[\]]/g, "\\$&");
            url = '^' + url + '$';
            var regex = '';

            //matches everything within {} (start with '{', than anything but '}', end with '}')
            var re = /{([^\}]+)}/g, paramMatch, lastMatchedIndex = 0;

            while ((paramMatch = re.exec(url)) !== null) {
                // Find each {param} in `url` and replace it with a capturing group.
                // Append all other sections of url unchanged.

                regex += url.slice(lastMatchedIndex, paramMatch.index);

                // The path param is the part between "{}" (the first parenthesized substring match)
                var pathParam = paramMatch[1];

                // Special case if path contains params with regex, e.g. {id : \d+}
                if (pathParam.indexOf(":") > 0) {
                    //the regex is the part after the ":"
                    var paramRegex = jQuery.trim(pathParam.split(":")[1]);
                    //just use this as regex
                    regex += '(' + paramRegex + ')';
                } else {
                    regex += '([^\\/]*)'; //anything except "/"
                }
                lastMatchedIndex = re.lastIndex;
            }
            // Append trailing path part.
            regex += url.substr(lastMatchedIndex);

            // Case insensitive regex
            return new RegExp(regex, "i");
        }

        /**
         * Filters out all interfaces that don't match the given search string.
         *
         * The baseUrl is dropped from the search string in searchString
         * If the search string contains a query param, all query params need to be filled in to match.
         *
         * e.g. search string: http://www.example.com/feature/5?limit=10
         * will match interface: /feature/{id} with query params limit
         * will not match interface: /feature/{id} with no query params
         * will not match interface: /feature/{id} with query params limit, start
         * will not match interface: /feature/ with query params limit
         *
         * @param searchString The search string
         * @return function that returns true if the given interface matches the search string, false otherwise
         */
        function searchByExampleFilter(searchString) {
            return function(currentInterface, baseUrl) {
                if (searchString.length === 0) {
                    return true;
                }

                var split = searchString.split("?");
                var locationPart = split[0];
                var queryPart = split[1];

                if (angular.isDefined(queryPart)) {
                    var queryParams = parseKeyValue(queryPart);
                    var valid = true;
                    angular.forEach(currentInterface.inputs.QUERY, function(queryParam) {
                        valid = valid && (valid = angular.isDefined(queryParams[queryParam.name.toLowerCase()]));
                    });
                    if (!valid) return false;
                }

                var search = locationPart.replace(baseUrl, "").stripTrailingSlash().ensureStartsWithSlash();

                //interface will not change, so create the regex only once
                currentInterface.regexp = currentInterface.regexp || getRegExp(currentInterface);

                return currentInterface.regexp.test(search);
            }
        }

        //expects a list of interfaces, returns a list of interfaces (default for filter)
//        return function(interfaces, searchString) {
//            return _.filter(interfaces, searchByExampleFilter(searchString));
//        }

        //expects one interface, returns true or false
        return function(iface, searchString, baseUrl) {
            return searchByExampleFilter(searchString)(iface, baseUrl);
        }
    }).
    filter('filterBySearchQuery', function() {
        function recursiveMatch(searchQuery) {
            return function(resource) {
                if (searchQuery.url.length === 0) {
                    return true;
                }
                if (resource.name.toLowerCase().indexOf(searchQuery.url.toLowerCase()) > -1) {
                    return true;
                }
                for (var i = 0; i<resource.leafResources.length; i++) {
                    if (resource.leafResources[i].url.toLowerCase().indexOf(searchQuery.url.toLowerCase()) > -1) {
                        return true;
                    }
                }
                for (var j = 0; j<resource.resources.length; j++) {
                    if (recursiveMatch(searchQuery)(resource.resources[j])) {
                        return true;
                    }
                }
                return false;
            }
        }

        //expects a list of resources, returns a list of resources (default for filter)
//        return function(resources, searchQuery) {
//            return _.filter(resources, recursiveMatch(searchQuery));
//        }

        //expects one resource, returns true or false
        return function(resource, searchQuery) {
            return recursiveMatch(searchQuery)(resource);
        }

    }).
    filter('serviceTagFilter', function() {
        /**
         * Filters out all interfaces that don't have all the currently selected service tags
         *
         * @param currentInterface The interface to check the service tags for
         */
        function matchesServiceTags(serviceTags) {
            return function(currentInterface) {
                return _.every(serviceTags, function(serviceTag) {
                    if (serviceTag.selected) {
                        return _.contains(currentInterface.tags, serviceTag.name);
                    }
                    return true;
                });
            }
        }

        //expects a list of interfaces, returns a list of interfaces (default for filter)
//        return function(interfaces, serviceTags) {
//            return _.filter(interfaces, matchesServiceTags(serviceTags));
//        };

        //expects one interface, returns true or false
        return function(currentInterface, serviceTags) {
            return matchesServiceTags(serviceTags)(currentInterface);
        }
    }).
    filter('searchQueryFilter', function() {
        /**
         * Filters out all interfaces that don't match the current search query
         *
         * @param currentInterface The interface to check the search query for
         */
        function matchesSearchQuery(searchQuery) {
            return function(currentInterface) {
                if(searchQuery.http && currentInterface.http.indexOf(searchQuery.http) < 0) {
                    return false;
                }
                if(searchQuery.url && currentInterface.url.toLowerCase().indexOf(searchQuery.url.toLowerCase()) < 0) {
                    return false;
                }
                
                return true;
            }
        }

        //expects a list of interfaces, returns a list of interfaces (default for filter)
//        return function(interfaces, serviceTags) {
//            return _.filter(interfaces, matchesServiceTags(serviceTags));
//        };

        //expects one interface, returns true or false
        return function(currentInterface, searchQuery) {
            return matchesSearchQuery(searchQuery)(currentInterface);
        }
    });