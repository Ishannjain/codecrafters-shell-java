[![progress-banner](https://backend.codecrafters.io/progress/shell/396a0632-e55e-4da1-bc06-1679000fa261)](https://app.codecrafters.io/users/codecrafters-bot?r=2qF)

This is a starting point for Java solutions to the
["Build Your Own Shell" Challenge](https://app.codecrafters.io/courses/shell/overview).

In this challenge, you'll build your own POSIX compliant shell that's capable of
interpreting shell commands, running external programs and builtin commands like
cd, pwd, echo and more. Along the way, you'll learn about shell command parsing,
REPLs, builtin commands, and more.

**Note**: If you're viewing this repo on GitHub, head over to
[codecrafters.io](https://codecrafters.io) to try the challenge.

# Passing the first stage

The entry point for your `shell` implementation is in `src/main/java/Main.java`.
Study and uncomment the relevant code, and push your changes to pass the first
stage:

```sh
git commit -am "pass 1st stage" # any msg
git push origin master
```

Time to move on to the next stage!

# Stage 2 & beyond

Note: This section is for stages 2 and beyond.
t 
1. Ensure you have `mvn` installed locally
1. Run `./your_program.sh` to run your program, which is implemented in
   `src/main/java/Main.java`.
1. Commit your changes and run `git push origin master` to submit your solution
   to CodeCrafters. Test output will be streamed to your terminal.

The goal of this challenge is to build a POSIX-like shell that can:
- Parse user input
- Execute external programs
- Implement common shell built-in commands

---

## ✅ Implemented Features (So Far)

### 🔹 Interactive REPL
- Displays a shell prompt (`$ `)
- Continuously reads user input until `exit` is called

---

### 🔹 Built-in Commands 

#### `exit`
- Terminates the shell loop

#### `echo`
- Prints the provided arguments to standard output

#### `pwd`
- Prints the current working directory

#### `type`
- Identifies whether a command is:
  - a shell builtin, or
  - an executable found in `$PATH`, or
  - not found

#### `cd`
- Changes the current working directory
- Supported behavior:
  - `cd <path>` — absolute or relative paths
  - `cd` — navigates to the home directory
  - `cd ~` — navigates to `$HOME`
  - `cd ~/subdir` — resolves relative to `$HOME`
- Directory state is preserved across commands

---

### 🔹 External Command Execution
- Searches executables in `$PATH`
- Executes commands using `ProcessBuilder`
- Supports command arguments
- Inherits standard input/output/error streams

Examples:
```sh
ls
ls -l
cat file.txt


## Command Parsing

The shell implements a character-by-character parser that closely follows
POSIX shell rules.

### Whitespace Handling
- Outside quotes: whitespace separates arguments
- Inside quotes: whitespace is preserved

### Single Quotes (`'`)
- Disable all special meaning
- Everything inside is treated literally
- Backslashes have no special behavior

Example:
```sh
echo 'a\ b " c'

## Output Redirection (`>`)

The shell supports redirecting standard output to a file using the `>` operator.

### Behavior
- Redirects **standard output (fd 1)** only
- Creates the file if it does not exist
- Overwrites the file if it already exists
- Standard error is not redirected

### Examples
```sh
echo hello > output.txt
cat output.txt

## Standard Error Redirection (`2>`)

The shell supports redirecting standard error (file descriptor 2) to a file.

### Behavior
- Redirects **stderr only**
- Creates the file if it does not exist
- Overwrites the file if it already exists
- Standard output is unaffected

### Example
```sh
cat missing 2> error.txt

# Java Shell Implementation

This project is a minimal Unix-like shell written in Java.

It supports:
- Built-in commands
- External command execution
- PATH lookup
- Output redirection
- Error redirection
- Append redirection
- Basic quote parsing
- Built-in command autocomplete (TAB)
- Raw terminal input mode

---

## 🚀 Features

### Built-in Commands
- `exit` — Exit the shell
- `echo` — Print text
- `pwd` — Print current working directory
- `cd` — Change directory
- `type` — Identify command type

---

### 🔄 Redirection Support

| Operator | Meaning |
|----------|----------|
| `>`      | Redirect stdout |
| `>>`     | Append stdout |
| `2>`     | Redirect stderr |
| `2>>`    | Append stderr |

Example:
```bash
echo hello > file.txt
ls missing 2>> errors.txt
```

---

### 🧠 Autocomplete

Press `TAB` to autocomplete built-in commands.

Example:
```
$ ec<TAB>
$ echo 
```

---

### 🧩 External Commands

Commands not recognized as built-ins are executed using:
```
ProcessBuilder
```

The shell automatically searches using the system `PATH`.

---

## 🛠 How It Works

1. Terminal is switched to raw mode using `stty`
2. Shell reads input character-by-character
3. Input is parsed into tokens
4. Redirection operators are extracted
5. Built-in commands are executed internally
6. External commands are executed using `ProcessBuilder`
7. Output redirection is applied
8. Loop continues until `exit`

---

## 📦 Compile & Run

```bash
javac Main.java
java Main
```

---

## 📌 Notes

- Designed for Unix-like systems
- Uses `stty` for raw terminal control
- Compatible with Codecrafters shell challenge

---
