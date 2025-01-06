import os
from collections import defaultdict

from colors import Colors
from trie import DictionaryTrie
from online_client import OnlineGameClientWrapper
from utils import clear_screen, input_with_prompt, print_welcome_message
from shared_utils import get_player_count_input, get_input_character, display_status, end_game_screen


class SpellChainGame:
    """
    Manages the local gameplay of SpellChain, handling player turns, scoring, and game state.
    
    :param dictionary_file: Path to the dictionary file.
    """
    def __init__(self, dictionary_file: str = os.path.join(os.path.dirname(__file__), "oxford_english_dictionary.txt")):
        """
        Initializes the game with the provided dictionary.
        
        :param dictionary_file: Path to the dictionary file.
        """
        self.dictionary_trie = DictionaryTrie()
        self.dictionary_trie.load_dictionary(dictionary_file)
        self.scores = defaultdict(int)
        self.sequence = ""
        self.round_count = 1
        self.found_words = defaultdict(set)
        self.current_player = 1
        self.player_count = get_player_count_input()
        print_welcome_message(end="")

    def switch_player(self):
        """
        Switches the turn to the next player.
        """
        self.current_player = (self.current_player % self.player_count) + 1

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
        print(f"{Colors.BLUE}Player {self.current_player} added \"{char}\" -> Sequence: \"{self.sequence}\"{Colors.RESET}")

        if self.dictionary_trie.search_word(self.sequence):
            if not self.is_word_used(self.sequence):
                points = max((len(self.sequence) + 1) // 2, 1)
                self.scores[self.current_player] += points
                self.found_words[self.current_player].add(self.sequence)
                definition = self.dictionary_trie.get_definition(self.sequence)
                print(
                    f"\n{Colors.GREEN}{Colors.BOLD}*** Player {self.current_player} completed \"{self.sequence}\"! "
                    f"({points} Point{'s' if points != 1 else ''}) ***{Colors.RESET}\nDefinition: "
                    f"{definition[:600]}{'…' if len(definition) > 600 else ''}"
                )
            else:
                print(
                    f"\n{Colors.YELLOW}\"{self.sequence}\" has already been used in the SpellChain. "
                    f"No points this round.{Colors.RESET}"
                )

        if not self.dictionary_trie.search_prefix(self.sequence):
            print(
                f"\n{Colors.RED}\"{self.sequence}\" is not a valid prefix of any word.\n"
                f"Round over. The sequence will reset.{Colors.RESET}"
            )
            self.sequence = ""
            self.round_count += 1

        self.switch_player()

    def play(self):
        """
        Initiates and manages a local game session.
        """
        while True:
            display_status(self.sequence, self.scores, self.player_count, self.round_count)
            char = get_input_character(self.current_player)

            if char == "exit":
                clear_screen()
                end_game_screen(self.scores, self.player_count, self.round_count, self.found_words)
                break

            clear_screen()
            self.add_character(char)


if __name__ == "__main__":
    print_welcome_message()

    while True:
        choice = input_with_prompt(f"{Colors.MAGENTA}Select game mode (1: Local, 2: Online): {Colors.RESET}").strip()

        if choice == "1":
            SpellChainGame().play()
        elif choice == "2":
            OnlineGameClientWrapper().start()
        elif choice.lower() != "exit":
            print(f"{Colors.RED}Invalid choice. Please select a valid option.{Colors.RESET}")
            continue
        break
