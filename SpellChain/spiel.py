import os, sys
from collections import defaultdict

from colors import Colors
from trie import DictionaryTrie
from online_client import OnlineGameClientWrapper
from utils import clear_screen, input_with_prompt, print_welcome_message


class Game:
    """
    Manages the gameplay of SpellChain, handling player turns, scoring, and game state.
    """
    def __init__(self, dictionary: DictionaryTrie, player_count: int):
        """
        Initializes the Game with the given dictionary and player count.
        
        :param dictionary: The DictionaryTrie used for word validation.
        :param player_count: Number of players in the game.
        """
        self.dictionary = dictionary
        self.scores = defaultdict(int)
        self.sequence = ""
        self.round_count = 1
        self.player_count = player_count
        self.current_player = 1
        self.found_words = defaultdict(set)
    
    def switch_player(self) -> None:
        """
        Switches the turn to the next player.
        """
        self.current_player = (self.current_player % self.player_count) + 1

    def display_status(self) -> None:
        """
        Displays the current game status, including the sequence and player scores.
        """
        sequence_line = f"{Colors.BLUE}Current sequence: \"{self.sequence}\"{Colors.RESET}"
        score_display = " | ".join([f"Player {player}: {self.scores[player]}" for player in range(1, self.player_count + 1)])
        score_line = f"{Colors.GREEN}(Round {self.round_count}) Scores -> {score_display}{Colors.RESET}"
        
        max_length = max(len(sequence_line), len(score_line))
        bar = f"{Colors.CYAN}{'=' * max_length}{Colors.RESET}"
        
        print(f"{bar}\n{sequence_line}\n{score_line}\n{bar}\n")

    def is_word_used(self, word: str) -> bool:
        """
        Checks if a word has already been used by any player.

        :param word: The word to check.
        :return: True if the word has been used, False otherwise.
        """
        return any(word in words for words in self.found_words.values())

    def add_character(self, char: str):
        """
        Adds a character to the sequence and updates the game state.

        :param char: The character added by the player.
        """
        self.sequence += char
        print(f"{Colors.BLUE}Player {self.current_player} added \"{char}\" -> Sequence: \"{self.sequence}\"{Colors.RESET}\n")
        
        if self.dictionary.search_word(self.sequence):
            if not self.is_word_used(self.sequence):
                points = max((len(self.sequence) + 1) // 2, 1)
                self.scores[self.current_player] += points
                self.found_words[self.current_player].add(self.sequence)
                definition = self.dictionary.get_definition(self.sequence)
                print(
                    f"{Colors.GREEN}{Colors.BOLD}*** Player {self.current_player} completed \"{self.sequence}\"! "
                    f"({points} Point{'s' if points != 1 else ''}) ***{Colors.RESET}\nDefinition: "
                    f"{definition[:600]}{'…' if len(definition) > 600 else ''}\n"
                )
            else:
                print(
                    f"{Colors.YELLOW}\"{self.sequence}\" has already been used in the SpellChain. "
                    f"No points this round.{Colors.RESET}\n"
                )
        
        if not self.dictionary.search_prefix(self.sequence):
            print(
                f"{Colors.RED}\"{self.sequence}\" is not a valid prefix of any word.\n"
                f"Round over. The sequence will reset.{Colors.RESET}\n"
            )
            self.sequence = ""
            self.round_count += 1
        
        self.switch_player()

    def end_game(self):
        """
        Handles the termination of the game, displaying final scores and words found.
        """
        clear_screen()
        print(f"{Colors.CYAN}{Colors.BOLD}Thank you for playing! Final Results:{Colors.RESET}")
        score_display = " | ".join(
            [f"Player {player}: {self.scores[player]}" for player in range(1, self.player_count + 1)])
        print(f"{Colors.GREEN}(Round {self.round_count}) Scores -> {score_display}{Colors.RESET}\n")
        print(f"{Colors.CYAN}{Colors.BOLD}Words Found by Each Player:{Colors.RESET}")
        
        for player in range(1, self.player_count + 1):
            print(f"{Colors.BLUE}Player {player}:{Colors.RESET}")
            if self.found_words[player]:
                print(f"  Words: {', '.join(sorted(self.found_words[player]))}")
            else:
                print("  No words found.")

class LocalGameClient:
    """
    Client for playing SpellChain locally.
    """
    def play_local_game(self, dictionary_file: str = os.path.join(os.path.dirname(__file__), "oxford_english_dictionary.txt")):
        """
        Initiates and manages a local game session.
        """
        dictionary_trie = DictionaryTrie()
        dictionary_trie.load_dictionary(dictionary_file)
        players = self.get_players()
        print_welcome_message()
        self.game = Game(dictionary_trie, players)

        while True:
            self.game.display_status()
            char = self.get_input_character(self.game.current_player)

            if char == "exit":
                self.game.end_game()
                break
            
            clear_screen()
            self.game.add_character(char)

    @staticmethod
    def get_players() -> int:
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

    @staticmethod
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
            print(f"{Colors.RED}Invalid input. Please enter a single alphabetic or punctuation character.{Colors.RESET}")


if __name__ == "__main__":
    print_welcome_message()

    while True:
        choice = input_with_prompt(f"{Colors.MAGENTA}Select game mode (1: Local, 2: Online): {Colors.RESET}").strip()

        if choice == "1":
            local_client = LocalGameClient()
            local_client.play_local_game()
        elif choice == "2":
            online_client = OnlineGameClientWrapper()
            online_client.start()
        elif choice.lower() != "exit":
            print(f"{Colors.RED}Invalid choice. Please select a valid option.{Colors.RESET}")
            continue
        break
