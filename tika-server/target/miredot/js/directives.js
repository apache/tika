'use strict';

/* Directives */

angular.module('miredot.directives', []);

angular.module('miredot.directives')
    /**
    *  Renders a json-to
    */
    .directive('jsonTo', function ($compile) {

        var _idCount = 0;
        function getNewId() {
            return _idCount++;
        }

        function getHighlightHtml(to) {
            var html = '';
            var id = to.__md_id || to.__sub_id;
            var addHoverClass = 'onmouseover="$(\'#' + id + '\').addClass(\'highlightJsonRecursive\');"';
            var removeHoverClass = 'onmouseout="$(\'#' + id + '\').removeClass(\'highlightJsonRecursive\');"';
            html += '<a href="#' + id + '_a" ' + addHoverClass + ' ' + removeHoverClass + ' class="recursionLink" target="_self">';
            html += '<i class="icon-retweet"></i>';
            html += '</a>';
            return html;
        }

        return {
            restrict: 'E',
            transclude: false,
            scope: {
                to:'=',
                jsonDocConfig:'='
            },
            link: function (scope, element, attrs) {

                /**
                 * Recursively renders the given TO as json.
                 * @param {Object | string} to The current object to render.
                 * @param {string} comment The comment of this field.
                 * @param {Array} history      All TOs that have already been rendered in the path leading here.
                 * @return {String}
                 */
                var build = function(to, comment, history) {
                    history = history || [];
                    var newHistory;

                    var html = '';

                    switch (to.type) {
                        case 'simple':
                            html += '<span class="parameterType">';
                            html += to.typeValue;
                            html += '</span>';
                            html += buildComment(comment);
                            break;

                        case 'enum':
                            html += '<span class="parameterType">';
                            html += enumArrayToString(to.values);
                            html += '</span>';
                            html += buildComment(comment);
                            break;

                        case 'collection':
                            html += '<span>[</span>';
                            html += buildComment(comment);
                            html += '<ul class="toContainer"><li class="parameterItem">';
                            html += build(to.typeValue, to.comment, history);
                            html += '</li></ul>';
                            html += '<span>]</span>';
                            break;

                        case 'map':
                            html += '<span>{</span>';
                            html += buildComment(comment);
                            html += '<ul class="toContainer"><li class="parameterItem">';
                            html += '<span class="parameterType">string</span> =>';
                            html += build(to.typeValue, to.comment, history);
                            html += '</li></ul>';
                            html += '<span>}</span>';
                            break;

                        default: //(abstract or complex)
                            //did wee see this type before?
                            if (_.indexOf(history, to.name) >= 0) {
                                //use it's id to highlight it
                                html += getHighlightHtml(to);
                                html += buildComment(comment);
                            } else {
                                newHistory = history.slice(0); // clone the history
                                newHistory.push(to.name);

                                //set a unique id for this type
                                to.__md_id = 'md_to_' + getNewId();

                                html += buildComment(comment);

                                //start TO div (with id to be able to highlight)
                                html += '<a id="' + to.__md_id + '_a" class="anchor"></a>';
                                html += '<div id="' + to.__md_id + '">';
                                html += '<span>{</span>';
                                html += '<ul class="toContainer">';

                                switch(to.type) {
                                    case 'abstract':
                                        html += buildAbstractToProperties(to, newHistory);
                                    break;

                                    case 'complex':
                                        html += buildComplexToProperties(to, newHistory);
                                    break;
                                }

                                //end TO div
                                html += '</ul>';
                                html += '<span>}</span>';
                                html += '</div>';
                            }
                            break;
                    }
                    return html;
                };

                function buildComment(comment) {
                    var result = '';
                    if (scope.jsonDocConfig.enabled && comment) {
                        result += '<span class="propertyComment" ng-show="!jsonDocConfig.hidden">';
                        result += comment;
                        result += '</span>';
                    }
                    return result;
                }

                /**
                 * Lists properties of complex to.
                 * @param {Object | string} to The current object to render.
                 * @param {Array} history      All TOs that have already been rendered in the path leading here.
                 * @param {Array} listedProperties  All properties that have been shown and should not be shown again
                 */
                 function buildComplexToProperties(to, history, listedProperties) {
                    listedProperties = listedProperties || [];

                    var html = '';

                    _.each(to.content, function(field) {
                        if (_.indexOf(listedProperties, field.name) < 0) {
                            html += '<li class="parameterItem"><span class="parameterName">' + field.name + ':</span>';
                            html += build(field.typeValue, field.comment, history);
                            html += "</li>";

                            listedProperties.push(field.name);
                        }
                    });
                    return html;
                }

                /**
                 * Lists properties of abstract to.
                 * @param {Object | string} to The current object to render.
                 * @param {Array} history      All TOs that have already been rendered in the path leading here.
                 * @param {Array} listedProperties  All properties that have been shown and should not be shown again
                 */
                function buildAbstractToProperties(to, history, listedProperties) {
                    listedProperties = listedProperties || [];
                    if (to.property) { //property name used in JsonTypeInfo should not be repeated in the list of properties
                        listedProperties.push(to.property);
                    }

                    var html = '';

                    //get a unique name for the angular model for the subType switcher
                    var subTypeModel = 'subTypeModel' + getNewId();

                    //list properties of this class
                    html += buildComplexToProperties(to, history, listedProperties);

                    //show subType switcher
                    html += '<li class="parameterItem">';
                    if (to.property) { //property name used in JsonTypeInfo
                        html += '<span class="parameterName">' + to.property + ':</span>';
                    }
                    html += '<span class="parameterType"><div class="btn-group">';

                    var first = true;
                    _.each(to.subTypes, function(subType) {
                        //set a unique id for this subType
                        subType.to.__sub_id = 'md_to_' + getNewId();
                        if (first) {
                            scope[subTypeModel] = subType.to.__sub_id; //set the default model value to the first subType id
                            first = false;
                        }
                        //show the button with the name of the subType (based on JsonTypeInfo)
                        //clicking this button changes the value of the current subTypeModel property to the id of the subType
                        html += '<button type="button" class="btn" ng-model="' + subTypeModel + '" ' +
                            'btn-radio="\'' + subType.to.__sub_id + '\'">' + subType.name + '</button>';
                    });

                    html += '</div></span>';
                    html += buildComment(to.comment);
                    html += '</li>';


                    //show subTypes, like complex type, but inline fields & only shown when subTypeModel is set to it's id
                    _.each(to.subTypes, function(subType) {
                        var newHistory = history.slice(0); // clone the history
                        var newListedProperties = listedProperties.slice(0);  // clone the already listed properties

                        html += '<a id="' + subType.to.__sub_id + '_a" class="anchor"></a>';
                        //only show this subType's fields when subTypeModel is set to it's id
                        html += '<div ng-show="' + subTypeModel + ' == \'' + subType.to.__sub_id + '\'" id="' + subType.to.__sub_id + '">';

                        switch (subType.to.type) {
                            case 'complex':
                                newHistory.push(subType.to.name);
                                html += buildComplexToProperties(subType.to, newHistory, newListedProperties);
                                break;
                            case 'abstract':
                                html += buildAbstractToProperties(subType.to, newHistory, newListedProperties);
                                break;
                        }
                        html += '</div>';
                    });
                    return html;
                }


                var togglePropertyCommentsHtml = '';

                if (scope.jsonDocConfig.enabled) {
                    togglePropertyCommentsHtml += '<span class="togglePropertyComments" ' +
                    'ng-click="jsonDocConfig.hidden = !jsonDocConfig.hidden">' +
                    '<span ng-show="jsonDocConfig.hidden">Show</span>' +
                    '<span ng-show="!jsonDocConfig.hidden">Hide</span>' +
                    ' descriptions</span>';
                }

                var newElement = angular.element(togglePropertyCommentsHtml + build(scope.to));
                $compile(newElement)(scope);
                element.replaceWith(newElement);
            }
        };
    })

    .directive('widthonblur', function () {
        return function(scope, element, attrs) {
            element.css("width", attrs.widthonblur);

            element.bind("blur", function() {
                element.css("width", attrs.widthonblur);
            });
        }
    })
    .directive('widthonfocus', function () {
        return function(scope, element, attrs) {
            element.bind("focus", function() {
                element.css("width", attrs.widthonfocus);
            })
        }
    })
    //ngFocus will be included in later angular versions
    .directive('onFocus', ['$parse', function($parse) {
        return function(scope, element, attr) {
            var fn = $parse(attr['onFocus']);
            element.bind('focus', function(event) {
                scope.$apply(function() {
                    fn(scope, {$event:event});
                });
            });
        }
}   ])
    //ngBlur will be included in later angular versions
    .directive('onBlur', ['$parse', function($parse) {
        return function(scope, element, attr) {
            var fn = $parse(attr['onBlur']);
            element.bind('blur', function(event) {
                scope.$apply(function() {
                    fn(scope, {$event:event});
                });
            });
        }
    }])
    //focus when some condition becomes true
    .directive('focusWhen', function($parse, $timeout) {
        return function(scope, element, attr) {
            scope.$watch(attr['focusWhen'],
                function(newValue, oldValue) {
                    if (!oldValue && !!newValue) {
                        $timeout(function() {
                            element.focus();

                            //move cursor te end if input field (not required in chrome)
                            if (element.val()) {
                                var tmpStr = element.val();
                                element.val('');
                                element.val(tmpStr);
                            }
                        });
                    }
                }, true);
        };
    })
    //evaluate expression when user presses enter
    .directive('onEnter', function() {
        return function(scope, element, attrs) {
            element.bind("keydown keypress", function(event) {
                if(event.which === 13) {
                    scope.$apply(function(){
                        scope.$eval(attrs.onEnter);
                    });

                    event.preventDefault();
                }
            });
        };
    });



