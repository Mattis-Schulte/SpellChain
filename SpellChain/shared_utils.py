from colors import Colors
from utils import input_with_prompt, safe_print, print_welcome_message

def get_player_count_input() -> int:
    """
    Prompts for the number of players and returns the value.

    :return: The chosen number of players (between 2 and 4).
    """
    print_welcome_message()

    while True:
        num = input_with_prompt(f"{Colors.MAGENTA}Enter number of players (2-4): {Colors.RESET}").strip()
        if num.isdigit() and 2 <= int(num) <= 4:
            return int(num)
        elif num.lower() == "exit":
            sys.exit()
        print(f"{Colors.RED}Invalid number of players. Please enter a number between 2 and 4.{Colors.RESET}")

def get_input_character(player: int) -> str:
    """
    Prompts the current player to input a character or exit the game.
    Ensures that the input is a single alphabetic or allowed punctuation character.

    :param player: The current player number.
    :return: The validated input character or "exit".
    """
    ALLOWED_PUNCTUATIONS = set("-'/ .")
    while True:
        inp = input_with_prompt(
            f"{Colors.MAGENTA}Player {player}, enter a character (or \"exit\"): {Colors.RESET}"
        ).strip().lower()
        if inp == "exit" or (len(inp) == 1 and (inp.isalpha() or inp in ALLOWED_PUNCTUATIONS)):
            return inp
        safe_print(f"{Colors.RED}Invalid input. Please enter a single alphabetic or punctuation character.{Colors.RESET}")

def _display_score_line(scores: dict, player_count: int, round_count: str) -> str:
    score_display = " | ".join([f"Player {player}: {scores.get(str(player), scores.get(player, 0))}" for player in range(1, player_count + 1)])
    return f"{Colors.GREEN}(Round {round_count}) Scores -> {score_display}{Colors.RESET}"

def display_status(sequence: str, scores: dict, player_count: int, round_count: int):
    """
    Prints the current sequence and score status.

    :param sequence: The current sequence of characters.
    :param scores: The scores for each player.
    :param player_count: The number of players.
    :param round_count: The current round number.
    """
    sequence_line = f"{Colors.BLUE}Current sequence: \"{sequence}\"{Colors.RESET}"
    score_line = _display_score_line(scores, player_count, round_count)

    bar_length = max(len(sequence_line), len(score_line))
    bar = f"{Colors.CYAN}{'=' * bar_length}{Colors.RESET}"

    safe_print(f"\n{bar}\n{sequence_line}\n{score_line}\n{bar}\n")

def end_game_screen(scores: dict, player_count: int, round_count: int, found_words: dict):
    """
    Handles the termination of the game, displaying final scores and words found.

    :param scores: The final scores for each player.
    :param player_count: The number of players.
    :param round_count: The total number of rounds played.
    :param found_words: The words found by each player.
    """
    safe_print(
        f"{Colors.CYAN}{Colors.BOLD}Thank you for playing! Final Results:{Colors.RESET}\n"
        f"{_display_score_line(scores, player_count, round_count)}\n\n"
        f"{Colors.CYAN}{Colors.BOLD}Words Found by Each Player:{Colors.RESET}"
    )

    for player in range(1, player_count + 1):
        safe_print(f"{Colors.BLUE}Player {player}:{Colors.RESET}")
        if words := found_words.get(str(player), found_words.get(player)):
            safe_print(f"  Words: {', '.join(sorted(words))}")
        else:
            safe_print("  No words found.")
