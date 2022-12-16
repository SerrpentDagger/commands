# Scripting
## Syntax
Scajl is a command-based scripting language. The general syntax reflects this structure. An executable Command is composed of a Head, and an Argument list. These can further be broken down.
### The Command Head
The Command Head has three parts, in the following order:
- Execution modifier (optional): This can be used to alter how/when the Command is run.
- Name (required): This is the name of the Command to run.
- Storage (optional): This is where the result of the Command will be stored.
#### Examples
- `print "Hello world!"`: A familiar demo. The head is just the name of the print Command, and a single argument is provided, a value to print. 
- `5:print "Hello #", INDEX`: The Head is now modified to run 5 times, and the index of the run is concatinated onto the output. 
- `bool?print "bool is true!"`: Prints the output if the variable 'bool' is true. 
- `:go:call Foo`: While the variable 'go' is true, call the label 'Foo'. 
- `add->sum a, b`: Adds the variables 'a' and 'b', storing the result in 'sum'. 
- `:{compare sum < 100}:print {add->sum sum, a}`: While 'sum' is less than 100, increment 'sum' by 'a'. 

### The Argument List
The Argument list is a comma-separated list of Arguments. Arguments can be further broken down into a space-separated list of Tokens.
#### Examples
- `var a 3`: Sets the variable 'a' to 3. This Command has 1 Argument, which has 2 Tokens.
- `var a 3, b 5`: Sets the variable 'a' to 3, and 'b' to 5. This Command has 2 Arguments, each with 2 Tokens.
- `compare a < b`: Returns whether 'a' is less than 'b'. This Command has 1 Argument, with 3 Tokens.

### Executables
You can get by without nesting executions, but it is sometimes helpful to execute one line of code, and use its output directly in the Argument of another Command. You can use Executables to do this. An Executable is the same as any other Command, but is enclosed in '{}', and can then be used in place of Tokens. You can also use Executables in the Command Head, as shown above.
#### Examples
- `print {add a, b}`: Prints the sum of 'a' and 'b'.
- `{compare a < b}?print "'a' is less than 'b'."`: Prints the output if 'a' is less than 'b'.
