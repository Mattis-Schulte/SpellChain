import os, sys
from collections import defaultdict

from colors import Colors
from trie import DictionaryTrie
from online_client import OnlineGameClientWrapper
from utils import clear_screen, input_with_prompt, print_welcome_message
from shared_utils import get_player_count_input, get_input_character, display_status, end_game_screen


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
        
        if self.dictionary.search_word(self.sequence):
            if not self.is_word_used(self.sequence):
                points = max((len(self.sequence) + 1) // 2, 1)
                self.scores[self.current_player] += points
                self.found_words[self.current_player].add(self.sequence)
                definition = self.dictionary.get_definition(self.sequence)
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
        
        if not self.dictionary.search_prefix(self.sequence):
            print(
                f"\n{Colors.RED}\"{self.sequence}\" is not a valid prefix of any word.\n"
                f"Round over. The sequence will reset.{Colors.RESET}"
            )
            self.sequence = ""
            self.round_count += 1
        
        self.switch_player()

class LocalGameClient:
    """
    Client for playing SpellChain locally.
    """
    def play_local_game(self, dictionary_file: str = os.path.join(os.path.dirname(__file__), "oxford_english_dictionary.txt")):
        """
        Initiates and manages a local game session.

        :param dictionary_file: Path to the dictionary file.
        """
        dictionary_trie = DictionaryTrie()
        dictionary_trie.load_dictionary(dictionary_file)
        num = get_player_count_input()
        print_welcome_message(end="")
        self.game = Game(dictionary_trie, num)

        while True:
            display_status(self.game.sequence, self.game.scores, self.game.player_count, self.game.round_count)
            char = get_input_character(self.game.current_player)

            if char == "exit":
                clear_screen()
                end_game_screen(self.game.scores, self.game.player_count, self.game.round_count, self.game.found_words)
                break
            
            clear_screen()
            self.game.add_character(char)


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
