import os
from collections import defaultdict
from colors import Colors
from trie import DictionaryTrie


class Game:
    """
    Manages the gameplay of SpellChain, handling player turns, scoring, and game state.

    :param dictionary: The DictionaryTrie object containing the word definitions.
    """
    def __init__(self, dictionary: DictionaryTrie):
        self.dictionary = dictionary
        self.scores = defaultdict(int)
        self.sequence = ""
        self.round_count = 1
        self.player = 1
        self.found_words = defaultdict(set)

    def switch_player(self) -> None:
        """
        Switches the turn to the other player.
        """
        self.player = 3 - self.player

    def display_status(self) -> None:
        """
        Displays the current game status, including the sequence and player scores.
        """
        print(f"\n{Colors.CYAN}{'=' * 50}{Colors.RESET}")
        print(f"{Colors.BLUE}Current sequence: \"{self.sequence}\"{Colors.RESET}")
        print(f"{Colors.GREEN}(Round {self.round_count}) Scores -> Player 1: {self.scores[1]} | Player 2: {self.scores[2]}{Colors.RESET}")
        print(f"{Colors.CYAN}{'=' * 50}{Colors.RESET}\n")

    def get_input(self) -> str:
        """
        Prompts the current player to input a character or exit the game.
        Ensures that the input is a single alphabetic or allowed punctuation character.

        :return: The validated input character or "exit".
        """
        ALLOWED_PUNCTUATIONS = set("-'/ .")

        while True:
            inp = input(f"{Colors.MAGENTA}Player {self.player}, enter a character (or \"exit\"): {Colors.RESET}").lower()
            if inp == "exit" or (len(inp) == 1 and (inp.isalpha() or inp in ALLOWED_PUNCTUATIONS)):
                return inp
            print(f"{Colors.RED}Invalid input. Please enter a single alphabetic or punctuation character.{Colors.RESET}")

    def is_word_used(self, word: str) -> bool:
        """
        Checks if a word has already been used by any player.

        :param word: The word to check.
        
        :return: True if the word has been used, False otherwise.
        """
        return any(word in words for words in self.found_words.values())

    def play(self) -> None:
        """
        Starts and manages the main game loop.
        Handles player input, sequence updates, word validation, scoring, and round transitions.
        """
        os.system('cls||clear')
        print(f"{Colors.CYAN}{Colors.BOLD}Welcome to SpellChain!{Colors.RESET}")
        print("Players take turns adding one letter at a time to build a word.")
        print("If a player completes a valid word, they earn points based on the word's length and the word's definition is displayed.")
        print("If a word has been added to the SpellChain once by any player, it cannot be reused in subsequent turns.")
        print("The game continues indefinitely until a player types \"exit\", new rounds start whenever an invalid prefix is entered.")

        while True:
            self.display_status()
            char = self.get_input()
            
            if char == "exit":
                self.end_game()
                break
            
            self.sequence += char
            os.system('cls||clear')
            print(f"{Colors.BLUE}Player {self.player} added \"{char}\" -> Sequence: \"{self.sequence}\"{Colors.RESET}")
            
            if self.dictionary.search_word(self.sequence):
                if not self.is_word_used(self.sequence): 
                    self.scores[self.player] += (points := (len(self.sequence) + 1) // 2)
                    self.found_words[self.player].add(self.sequence)
                    print(f"\n{Colors.GREEN}{Colors.BOLD}*** Player {self.player} completed \"{self.sequence}\"! ({points} Point{'s' * (points != 1)}) ***{Colors.RESET}")
                    print(f"Definition: {(lambda d: d[:600] + '…' if len(d) > 600 else d)(self.dictionary.get_definition(self.sequence))}")
                else:
                    print(f"\n{Colors.YELLOW}\"{self.sequence}\" has already been used in the SpellChain. No points this round.{Colors.RESET}")

            if self.dictionary.search_prefix(self.sequence):
                self.switch_player()
            else:
                print(f"\n{Colors.RED}\"{self.sequence}\" is not a valid prefix of any word.\nRound over. The sequence will reset.{Colors.RESET}")
                self.sequence = ""
                self.round_count += 1
                self.switch_player()

    def end_game(self) -> None:
        """
        Handles the termination of the game, displaying final scores and words found.
        """
        os.system('cls||clear')
        print(f"{Colors.CYAN}{Colors.BOLD}Thank you for playing! Final Results:{Colors.RESET}")
        print(f"{Colors.GREEN}(Round {self.round_count}) Scores -> Player 1: {self.scores[1]} | Player 2: {self.scores[2]}{Colors.RESET}\n")
        print(f"{Colors.CYAN}{Colors.BOLD}Words Found by Each Player:{Colors.RESET}")
        
        for player in (1, 2):
            print(f"{Colors.BLUE}Player {player}:{Colors.RESET}")
            if self.found_words[player]:
                print(f"  Words: {', '.join(sorted(self.found_words[player]))}")
            else:
                print("  No words found.")


if __name__ == "__main__":
    dictionary_trie = DictionaryTrie()
    dictionary_trie.load_dictionary("oxford_english_dictionary.txt")
    Game(dictionary_trie).play()
