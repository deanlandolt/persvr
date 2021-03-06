/**
 * Provides a Query constructor with chainable capability. For example:
 * var Query = require("./query").Query;
 * query = Query();
 * query.executor = function(query){
 *    require("./js-array").query(query, params, data); // if we want to operate on an array
 * };
 * query.eq("a", 3).le("b", 4).forEach(function(object){
 *   // for each object that matches the query
 * });
 */
require.def||(require.def=function(deps, factory){module.exports = factory(require, exports);});
require.def(["require", "exports"], function(require, exports){

try{
	var when = require("promised-io/promise").when;
}catch(e){
	when = function(value, callback){callback(value)};
}

exports.knownOperators = ["and", "or", "eq", "ne", "le", "lt", "gt", "ge", "sort", "in", "select", "contains","values","aggregate","distinct","limit","recurse"];
exports.knownScalarOperators = ["mean", "sum", "min", "max", "count", "first", "one"];
exports.arrayMethods = ["forEach", "reduce", "map", "filter", "indexOf", "some", "every"];
exports.Query = function(seed, params){
	if (typeof seed === 'string')
		return parseQuery(seed, params);
	var q = new Query();
	if (seed && seed.name && seed.args)
		q.name = seed.name, q.args = seed.args;
	return q;
};

function Query(name){
	this.name = name || "and";
	this.args = [];
}
exports.Query.prototype = Query.prototype;
Query.prototype.toString = function(){
	return this.name === "and" ?
		this.args.map(queryToString).join("&") :
		queryToString(this);
};

function queryToString(part) {
    if (part instanceof Array) {
        return part.map(function(arg) {
            return queryToString(arg);
        }).join(",");
    }
    if (part && part.name && part.args) {
        return [
            part.name,
            "(",
            part.args.map(function(arg) {
                return queryToString(arg);
            }).join(","),
            ")"
        ].join("");
    }
    return exports.encodeValue(part);
};

function encodeString(s) {
    if (typeof s === "string") {
        s = encodeURIComponent(s);
        if (s.match(/[\(\)]/)) {
            s = s.replace("(","%28").replace(")","%29");
        };
    }
    return s;
}

exports.encodeValue = function(val) {
    var encoded;
    if (val !== require("./parser").converters["default"]('' + (
        val.toISOString && val.toISOString() || val.toString()
    ))) {
        var type = typeof val;
        if(val instanceof RegExp){
            type = "re";
            val = val.toString();
            val = val.substr(1, val.length-2);
        }
        if(type === "object"){
            type = "epoch";
            val = val.getTime();
        }
        if(type === "string") {
            val = encodeString(val);
            encoded = true;
        }
        val = [type, val].join(":");
    }
    if (!encoded && typeof val === "string") val = encodeString(val);
    return val;
};

exports.updateQueryMethods = function(){
	exports.knownOperators.forEach(function(name){
		Query.prototype[name] = function(){
			var newQuery = new Query();
			newQuery.executor = this.executor;
			var newTerm = new Query(name);
			newTerm.args = Array.prototype.slice.call(arguments);
			newQuery.args = this.args.concat([newTerm]);
			return newQuery;
		};
	});
	exports.knownScalarOperators.forEach(function(name){
		Query.prototype[name] = function(){
			var newQuery = new Query();
			newQuery.executor = this.executor;
			var newTerm = new Query(name);
			newTerm.args = Array.prototype.slice.call(arguments);
			newQuery.args = this.args.concat([newTerm]);
			return newQuery.executor(newQuery);
		};
	});
	exports.arrayMethods.forEach(function(name){
		Query.prototype[name] = function(){
			var args = arguments;
			return when(this.executor(this), function(results){
				return results[name].apply(results, args);
			});
		};
	});

};

exports.updateQueryMethods();
var parseQuery = require("./parser").parseQuery;
return exports;
});
