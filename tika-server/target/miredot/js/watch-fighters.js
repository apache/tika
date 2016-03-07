'use strict';

//based on https://github.com/abourget/abourget-angular
angular.module('watchFighers', [])

  .directive('setIf', [function () {
    return {
      transclude: 'element',
      priority: 1000,
      terminal: true,
      restrict: 'A',
      compile: function (element, attr, linker) {
        return function (scope, iterStartElement, attr) {
          iterStartElement[0].doNotMove = true;
          var expression = attr.setIf;
          var value = scope.$eval(expression);
          if (value) {
            linker(scope, function (clone) {
              iterStartElement.after(clone);
            });
          }
        };
      }
    };
  }])


  .directive('setHtml', function() {
    return {
      restrict: "A",
      priority: 100,
      link: function($scope, $el, $attr) {
        $($el).html($scope.$eval($attr.setHtml) || '');
      }
    };
  })

  .directive('setText', function() {
    return {
      restrict: "A",
      priority: 100,
      link: function($scope, $el, $attr) {
        $($el).text($scope.$eval($attr.setText) || '');
      }
    };
  })

  .directive('setClass', function() {
    return {
      restrict: "A",
      priority: 100,
      link: function($scope, $el, $attr) {

          function setClass(attributeValue, $scope, $el) {
              if (attributeValue.indexOf(":") > 0) { //classname : condition
                  var classNameCondition = attributeValue.split(":", 2);
                  var className = jQuery.trim(classNameCondition[0]);
                  var condition = $scope.$eval(jQuery.trim(classNameCondition[1]));
                  if (condition) {
                      $($el).addClass(className);
                  }
              } else { //just classname
                  $($el).addClass($scope.$eval(attributeValue) || '');
              }
          }

          if ($attr.setClass.indexOf(",") > 0) { //multiple classes
              _.each($attr.setClass.split(','), function(attributeValue) {
                  setClass(jQuery.trim(attributeValue), $scope, $el);
              });
          } else {
              setClass($attr.setClass, $scope, $el);
          }
      }
    };
  })

  .directive('setTitle', function() {
    return {
      restrict: "A",
      priority: 100,
      link: function($scope, $el, $attr) {
        $($el).attr('title', $scope.$eval($attr.setTitle) || '');
      }
    };
  })

  .directive('setHref', function() {
    return {
      restrict: "A",
      priority: 100,
      link: function($scope, $el, $attr) {
        $($el).attr('href', $scope.$eval($attr.setHref) || '');
      }
    };
  })

  .directive('setId', function() {
    return {
      restrict: "A",
      priority: 100,
      link: function($scope, $el, $attr) {
        $($el).attr('id', $scope.$eval($attr.setId) || '');
      }
    };
  })

  ;