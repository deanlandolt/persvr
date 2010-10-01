this is a collection of the persevere packages all together in a single repository. each package has been added as a submodule. for help with submodules see http://book.git-scm.com/5_submodules.html

Clone this repository and initialize any submodules:

    git clone --recursive REPOSITORY 

To Run off of narwhal:

./bin/persvr example


To build nodejs:

	cd packages/node && ./configure && make && cd ../../

To run with nodejs:

	./bin/node-persvr example


I'm still working on a good set of incantations and directions for using your own branches for all or a subset of this and will update when I have more information.  Here are a few notes:

Some submodule reference docs for helping you out:

	http://speirs.org/blog/2009/5/11/understanding-git-submodules.html
	http://chrisjean.com/2009/04/20/git-submodules-adding-using-removing-and-updating/

Here is a command to switch submodules to master and pull

	git submodule foreach 'git checkout master; git pull'

Better commands/directions are welcome!
