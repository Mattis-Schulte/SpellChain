import os, threading
from colors import Colors

print_lock = threading.Lock()

def clear_screen():
    os.system("cls" if os.name == "nt" else "clear")

def input_with_prompt(prompt: str = "") -> str:
    """
    Prompt the user for input, returning "exit" on EOFError or KeyboardInterrupt.

    :param prompt: The prompt message.
    :return: The user's input.
    """
    try:
        return input(prompt)
    except (EOFError, KeyboardInterrupt):
        return "exit"

def safe_print(*args, **kwargs):
    """
    Thread-safe print function to ensure output integrity.

    :param args: Positional arguments for print.
    :param kwargs: Keyword arguments for print.
    """
    with print_lock:
        print(*args, **kwargs)

def print_welcome_message(end: str = "\n"):
    """
    Clears the screen and prints the welcome message.

    :param end: The end character for the print statement.
    """
    clear_screen()
    safe_print(
        f"{Colors.CYAN}{Colors.BOLD}Welcome to SpellChain!{Colors.RESET}\n"
        "Players take turns adding one letter to form a word.\n"
        "Completing a valid word awards points based on its length, and the word's definition is displayed.\n"
        "Once a word has been used by any player, it cannot be reused in subsequent turns.\n"
        "The game continues until a player types \"exit\" or presses Ctrl+C.\n", end=end
    )
