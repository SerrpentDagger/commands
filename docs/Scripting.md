# Contents
- [Scripting](#scripting)
- [Syntax](#syntax)
- [Comments](#comments)
- [Labels and Scope](#labels-and-scope)
- [Primitive Types](#scajl-primitive-types)
- [Reference Modifiers](#reference-modifiers)
- [Utility Commands](#utility-commands)

# Scripting
## Ending a Script With ==
A Script can be ended by the end of a file, or in some way determined by the implementing application. But a Script can also always be ended by the '==' symbol. For example, when typing in the System input, the Script will stop requesting more lines when you end one with '=='. This can also be used to cut off chunks of Script from a file without deleting them.

## When in Doubt, ?
'?' is a very useful symbol in Scajl. It causes help text to be printed about various constructs. If you enter '?' as a single line, it will give you an overview of the current structure of functionality exposed to the Scajl runtime, including global Commands, Object Types and Libraries. When used on a single element, it can give you information about a specific Command or Object Type.

## Aborting Script Executtion
Scajl Scripts are meant to be used for a variety of things, including mouse and keyboard manipulation. To this end, it is very easy to abort Script execution at any time. You can do so by simply moving your mouse to the top left corner of the moniter. The Script will then terminate before running the next Scajl Executable.

## Syntax
Scajl is a command-based scripting language. The general syntax reflects this structure. An executable Command is composed of a Head, and an Argument list. These can further be broken down. There are then several expansions on this format, which allow for greater flexibility in scripting.
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
- Demonstration of inline if-else statement:
```
{compare a == 1}?print "'a' is 1!"
:{compare a == 2}?print "'a' is 2!"
:print "'a' was neither 1 nor 2."
```

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

## Comments
Comments in Scajl are simple. A line comment begins with '//', and a multiline comment is enclosed as '<< ... >>'.
#### Examples
```
// This will not be run as a command.
print "Hi!" // This will also not be run.
<< If you have more to say,
then you can spread it
across multiple lines,
like this. >>
print "You can also", <<" ommit ",>> "sections of lines." // Prints "You can alsosections of lines."
```

## Labels and Scope
Labels in Scajl behave basically like functions in other languages, with slight differences. Scope is also very analougue.
### Labels
A Label is created in the Script with '--' or '\~\~' at the beginning of a line. The first is 'unscoped', the second is 'scoped'. An unscoped Label, when called, does not alter the scope of operation. A scoped Label does. You can further modify the Label's scope behaviour with '^' and '|', discussed in 'Accessibility.'
### Scope
You can see a print-out of variables and scope by using the 'print_all_vars' Command (see 'Debugging').
Variables are set in the lowest accessible scope. If no Labels have been called, this will be the 'GLOBAL' scope of the current Script. If you call a scoped label, then its scope will be the lowest, but it will not necessarily be accessible.
Variables are read in the lowest scope in which they're present. If they have not been set in any containing scope, they will be read as 'null' (a special Value).
This is all demonstrated by the first Script below, which I recommend you run, to see and understand the output.
### Accessibility
The '^' and '|' symbols can be incorperated on a Label declaration to alter how its scope behaves. The '^' symbol will grant access to the scope directly above that of the Label in question, while the '|' symbol grants access to all those below. You should modify the below Script, using '^' and '|' on S1, S2 and S3, to see how this affects the final variable structure.
### Anonymous Scopes
An anonymous scope can be created with a multiline curly bracket. This scope will behave the same as Label scopes, but will be entered and left automatically as the runtime progresses into and out of the brackets.

#### Examples
```
// Demonstration of scope
var a 1 // Sets in GLOBAL
goto S1

~~S1 // A scoped Label
var b 2 // Sets in S1
goto S2
return

~~S2 // Another scoped Label
var c 3 // Sets in S2
goto S3
return

~~S3 // Another scoped Label
var d 4 // Sets in S3
goto End
return

~~End // Another scoped Label
print_all_vars // Prints variable structure
goto Print // Print will have the same scope as End.

var a -1, b -2, c -3, d -4

print_all_vars // Prints variable structure after changes.
goto Print
return // Only a will be changed after returns, because other scopes are not accessible.

--Print // An unscoped Label
print "a: ", a, ", b: ", b, ", c: ", c, ", d: ", d // All the values can be read.
return
```
```
// Demonstration of anonymous scope
{ // Enters the first scope
	var a 1 // Sets a within this scope
	print a
	print_all_vars
	{ // Enters nested scope
		var a 2 // Sets a within nested scope, because above is not accessible
		print a
		print_all_vars
	}
	{
		print a // a can be read, though, because reading is public
		print_all_vars
	}
	{^
		var a 3 // Sets a within outer scope, because of '^' symbol
		print a
		print_all_vars
	}
}
print a // a was never set in GLOBAL scope, so this prints "a".
print_all_vars
```

## Scajl Primitive Types
Scajl has a number of Primitive variable types. The details of each are listed below:
### Value
The Value is the most basic Primitive. It holds some value. The value itself has no type asscociated with it - it is interperted as needed by the context in which it is used. For example, a value containing '3.0' can be used as a number or a string, depending on the Token context.
#### Examples
```
// Value demonstration
var a 2, b 3 // These are values.
print {add a, b} // a and b are first interpereted as numbers (2 and 3), and then the output is read as a string ("5.0").
```
### Reference
A Reference is created with the '@' symbol. In general, Scajl primitives are pass-by-value, except for Containers (see below). When you reference a Value, the value read from the Reference will be looked up when read, rather than set on creation. This difference is illustrated below.
You can also reference Executables. When you do this, the value of the Executible will be looked up on use, thus calling the line of code at a later time. The variable context of the Executable Reference will be that in which it is called, not that in which it is created. Executable Reference brings us to the 'echo' command, which is useful for calling these in a standard way. '~' is a direct overload of 'echo', being simply a convenient abbreviation.
#### Examples
```
// Reference vs. Value
var a 1
var b a, c @a
var a 2
print "a:", a, ", b:", b, ", c:", c // Prints "a:2, b:1, c:2".
```
```
// Executable Reference
var a 1
var b @{print "a:", a} // When b is read, the print line will be called.
echo b // Tries to read b, thus calling the executable.
~ b // Exactly the same as 'echo b', but shorter.
```

### String
Enclosed as '" ... "', escape char is '\\'.
### Object
Java Object references! Only created as return values from Commands.
### Map
Enclosed as '\[key1=val1; key2=val2; ... keyN=valN\]'.
### Array
Enclosed as '\[val0; val1; ... valNm1\]'. Can be used for var-arg functions with '#'.
```
var a [0; 1; 2; 3; 4]
print {add #a} // Prints the sum of 0, 1, 2, 3, and 4.
```
### TokenGroup
Enclosed as '\(val0 val1 ... valNm1\)'. Can be unpacked into Arguments with '^'.
#### Examples
```
var a (b 2)
var ^a // Sets b to 2.
```

## Reference Modifiers
Raw, unraw, etc.
## Utility Commands
There are many commands built into the default Scajl runtime environment, but these global utility ones will be very useful to the aspiring scripter. A brief overview will be mentioned below, but it is recommended that you view the technicalities further by querying the help documentation with '?' (See [When in Doubt](#when-in-doubt-)).
### var, var_if..., var_array, var_array_fill
These are examples of commands used to set variables and arrays in a simple way. You can set variables under certain conditions, and fill arrays by index using an executable.
### test, enforce, enforce_one, assert
These can be used to test and modify user input, ensuring it complies with the expectations of your function. They work by matching a variable with an expected structure, or filling it with your defaults where the input is incompatible.
### pack
This can also be used to adjust user input. It will pack an array to the desired dimensions, allowing the user to supply their information more intuitively, while allowing you to deal only with the highest level dimension.
### echo, ~, exec, read, -
These are very useful when it comes to running Member Commands and Executable References in a compact way. You should definitely read their help documentation.
### of_type, get_type
These can be used to query information about a variable, in case the above section is not flexible enough for your needs.
### add, sub, mult..., not, or, and...
Arithmetic and logical operations. These are grouped together in the help documentation, so you refer to that section of the list to see them all.
### for, while, if, call
Flow-control Commands. These have mostly been replaced by the inline formats (see [Command Head](#the-command-head)), but can still be useful. In particular, you should look at call and if.
### runscr, runlab, impscr
These are used for working with other Scripts. runscr and runlab will load a Script, run a portion of it, then unload the Script. Impscr will load a Script, and then return a handle to it so that you can work with it over the lifetime of your own Script. This can be used to create library Scripts for future use, which is discussed further below.
### print, print_all_vars, print_debug
These can be used for displaying text and debugging. print_all_vars and print_debug are particularly useful.
