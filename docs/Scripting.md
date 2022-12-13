# Scripting
## Syntax
Scajl is a command-based scripting language. The general syntax reflects this structure. An executable command is composed of a Head, and an Argument list. These can further be broken down.
### The Command Head
The command Head has three parts, in the following order:
- Execution modifier (optional): This can be used to alter how/when the command is run.
- Name (required): This is the name of the command to run.
- Storage (optional): This is where the result of the command will be stored.
#### Examples
- `print "Hello world!"`: A familiar demo. The head is just the name of the print command, and a single argument is provided, a value to print. 
- `5:print "Hello #", INDEX`: The Head is now modified to run 5 times, and the index of the run is concatinated onto the output. 
- `bool?print "bool is true!"`: Prints the output of the variable 'bool' is true. 
- `:go:call Foo`: While the variable 'go' is true, call the label 'Foo'. 
- `add->sum a, b`: Adds the variables 'a' and 'b', storing the result in 'sum'. 
- `:{compare sum < 100}:print {add->sum sum, a}`: While 'sum' is less than 100, increment 'sum' by 'a'. 
