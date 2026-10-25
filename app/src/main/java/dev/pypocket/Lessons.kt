package dev.pypocket

data class Lesson(
    val id: Int,
    val title: String,
    val summary: String,
    val explanation: String,
    val example: String,
    val exercise: String
)

val beginnerLessons = listOf(
    Lesson(1, "Your first program", "Print text and do simple math", "Python runs instructions from top to bottom. print() displays a value in the output console.", "print(\"Hello, Python!\")\nprint(2 + 3)", "Change the message, then print the result of 12 * 4."),
    Lesson(2, "Variables and values", "Give data a useful name", "A variable stores a value. Use = to assign text, numbers, or the result of an expression.", "name = \"Ada\"\nage = 12\nprint(name)\nprint(age + 1)", "Create a variable for your favorite food and print a sentence that uses it."),
    Lesson(3, "Text and input", "Work with strings and user input", "Strings are text inside quotes. input() pauses the program and returns what the user types.", "name = input(\"What is your name? \")\nprint(f\"Nice to meet you, {name}!\")", "Ask for a city and print a sentence that includes the answer."),
    Lesson(4, "Make a decision", "Choose a path with if and else", "Python checks an if condition. Indent the code under it. else runs when the condition is false.", "temperature = 22\nif temperature > 25:\n    print(\"It's warm\")\nelse:\n    print(\"It's cool\")", "Ask for a number and print whether it is positive, zero, or negative."),
    Lesson(5, "Repeat with for", "Use loops to repeat instructions", "A for loop visits each item in a sequence. range(5) produces the numbers 0 through 4.", "for number in range(1, 6):\n    print(number)", "Print the seven times table from 1 to 10."),
    Lesson(6, "Lists", "Keep several values together", "A list stores an ordered group of values. Indexes start at zero, and append() adds another item.", "colors = [\"blue\", \"green\", \"orange\"]\ncolors.append(\"purple\")\nfor color in colors:\n    print(color)", "Make a list of three things to learn and print each one."),
    Lesson(7, "Functions", "Name reusable blocks of code", "Define a function with def. Parameters let you pass values in, and return sends a result back.", "def greet(name):\n    return f\"Hello, {name}!\"\n\nprint(greet(\"Sam\"))", "Write a function that accepts two numbers and returns their sum."),
    Lesson(8, "A small project", "Build a number guessing game", "Combine input, numbers, conditionals, and loops. int() converts a string of digits into an integer.", "secret = 7\nguess = int(input(\"Guess a number from 1 to 10: \"))\nif guess == secret:\n    print(\"You got it!\")\nelse:\n    print(\"Try again next time.\")", "Add a hint that says whether the guess was too high or too low.")
)
