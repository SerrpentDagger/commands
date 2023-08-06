# Contents
- [Implementation](#implementation)
	- [Running Scripts](#running-scripts)
	- [Exposing Functionality](#exposing-functionality)
		- [Explicit Exposition](#explicit-exposition)
		- [Automatic Exposition](#automatic-exposition)

# Implementation
This section aims to inform the application developer about how they can implement Scajl into their application.
For information on how to write Scajl scripts, see [Scripting](docs/Scripting.md).

The section will be left somewhat sparse for the time being, since I don't anticipate it to be much-needed at the moment. Hopefully, I'll come back to it later, but if you need more help, feel free to contact me about an issue in particular, or to let me know that this information would be appreciated.

Either way, you can learn more about implementation through the Javadocs and source code's internal usage.

## Running Scripts
There are two ways to run a script: simply running it, or loading it as a library. In the former, the script is loaded, run from the start, terminates, and is unloaded. In the latter, the script is loaded, its `IMPORT` Label is called, and it is returned as a Script Object. You may then call its Labels from Java until you no longer need it.

## Exposing Functionality
You'll probably want to add certain Objects and Classes into Scajl. This is easily done.

### Explicit Exposition
This is where you manually add functionality to the Scajl environment using things like the `Scajl.add` functions. This is still relatively streamlined thanks to the `CmdArg` infrastructure and help of the surrounding automation. Examples can be found in the constants of `src.commands.Scajl` and `src.commands.CmdArg`. `CmdArg` also provides functions for defining custom argument loading patterns, use of which can be seen in the same Class, or in `src.commands.libs.BuiltInLibs`.

### Automatic Exposition
Point the `Scajl.expose` functions at nearly whatever you please. Functional interfaces do need special treatment with `CmdArg.funcInterfaceOf`.
You can customize the filter used for automatic exposition, and use the annotations in `src.annotations` to fine-tune the work and provide metadata to the runtime docs.
You can find examples in `src.commands.libs.BuiltInLibs`.
When exposing Java structures automatically and recursively, you may find that more stuff than you want ends up accessible to the user. You can avoid this with the `Scajl.noExpose` commands.