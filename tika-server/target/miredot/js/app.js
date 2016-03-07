'use strict';


// Declare app level module which depends on filters, and services
angular.module('miredot', ['miredot.filters', 'miredot.directives', 'ui.bootstrap.buttons', 'watchFighers', 'ngStorage']).
    // Make sure our local href="#hash" link bindings work
    config(['$compileProvider', '$locationProvider', function ($compileProvider, $locationProvider) {
        $compileProvider.aHrefSanitizationWhitelist(/^\s*(https?|ftp|mailto|file|tel):/);
        $locationProvider.html5Mode(true);
    }]);
