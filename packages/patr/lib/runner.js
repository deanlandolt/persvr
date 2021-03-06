var when = require("promised-io/promise").when,
	print = require("promised-io/process").print,
	onError;
exports.run = function(tests, args){
	if(!args){
		var params = require("promised-io/process").args;
		args = {};
		for(var i = 0; i < params.length; i++){
			if(params[i].charAt(0) == "-"){
				args[params[i].substring(1)] = params[i+1];
				params++; 
			}
		}
	}
	print("Running tests ");
	doTests(compileTests(tests, args));
};

function doTests(tests, prefix){
	prefix = prefix || "";
	function doTest(index){
		var done;
		try{
			var test = tests[index++];
			if(!test){
				onError = false;
				return {failed: 0, total: tests.length};
			}
			if(test.children){
				print(prefix + "Group: " + test.name);
				return when(doTests(test.children, prefix + "  "), function(childrenResults){
					return when(doTest(index), function(results){
						results.failed += childrenResults.failed;
						results.total += childrenResults.total - 1;
						return results;
					});
				});
			}
			onError = function(e){
				print("onError");
				testFailed(e);
				done = true;
			}
			var start = new Date().getTime();
			var iterations = test.iterations || tests.flags.iterations;
			var iterationsLeft = iterations || 1;
			var testFinishedSync;
			function runIteration(){
				while(true){
					iterationsLeft--
					testFinishedSync = false;
					var result = when(test.test(), testCompleted, testFailed);
					if(iterationsLeft > 0){
						if(!testFinishedSync){
							return when(result, runIteration);
						}
					}else{
						return result;
					}
				}
				
			}
			return runIteration();
			function testCompleted(){
				testFinishedSync = true;
				if(!done){
					if(iterationsLeft <= 0){
						var duration = new Date().getTime() - start;
						print(prefix + test.name + ": passed" + (iterations || duration > 200 ? " in " + (duration / (iterations || 1)) + "ms per iteration" : ""));
						return doTest(index);
					}
				}
			}
		}catch(e){
			return testFailed(e);
		}
		function testFailed(e){
			if(!done){
				print(prefix + test.name + ": failed");
				print(e.stack || e);
				return when(doTest(index), function(results){ results.failed++; return results;});
			}
		}
		
	}
	return when(doTest(0), function(results){
		print(prefix + "passed: " + (results.total - results.failed) + "/" + results.total);
		return results;
	});
}

function compileTests(tests, parent){
	var listOfTests = [];
	listOfTests.flags = {};
	for(var i in parent){
		listOfTests.flags[i] = parent[i];
	}
	for(var i in tests){
		listOfTests.flags[i] = tests[i];
	}
	for(var i in tests){
		if(i.substring(0,4) == "test"){
			var test = tests[i];
			if(typeof test == "function"){
				listOfTests.push({
					name: i,
					test: test
				});
			}
			if(typeof test == "object"){
				if(typeof test.runTest == "function"){
					test.test = test.runTest;
					test.name = test.name || i;
					listOfTests.push(test);
				}else{
					listOfTests.push({
						name: i,
						children: compileTests(test, listOfTests.flags)
					});
				}
			}
		}
	}
	return listOfTests;
}

if(typeof process !== "undefined"){
	process.addListener("uncaughtException", function(e){
		if(onError){
			onError(e);
		}else{
			print("Error thrown outside of test, unable to associate with a test. Ensure that a promise returned from a test is not fulfilled until the test is completed. Error: " + e.stack)
		}
	});
}


