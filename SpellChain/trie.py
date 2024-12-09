import os, sys, re
from colors import Colors


class TrieNode:
    """
    Represents a node in the trie data structure.
    """
    __slots__ = ("children", "is_word", "definition")

    def __init__(self):
        self.children = {}
        self.is_word = False
        self.definition = None


class DictionaryTrie:
    """
    Implements a trie data structure for storing words and their definitions.
    """
    def __init__(self):
        self.root = TrieNode()

    def insert(self, word: str, definition: str) -> None:
        """
        Inserts a word and its definition into the trie. 
        If the word already exists, appends the new definition with an "OR" separator.

        :param word: The word to insert.
        :param definition: The definition of the word.
        """
        node = self.root
        for char in word:
            node = node.children.get(char) or node.children.setdefault(char, TrieNode())
        if node.is_word:
            node.definition += f" {Colors.YELLOW}OR{Colors.RESET} {definition}"
        else:
            node.definition = definition
            node.is_word = True

    def load_dictionary(self, file_path: str) -> None:
        """
        Loads words and their definitions from a dictionary file into the trie.
        Each line in the file should contain a word and its definition separated by two spaces.

        :param file_path: The path to the dictionary file.
        """
        if not os.path.exists(file_path):
            os.system('cls||clear')
            print(f"{Colors.RED}Dictionary file \"{file_path}\" not found.{Colors.RESET}")
            sys.exit(1)

        with open(file_path, "r", encoding="utf-8") as f:
            for line in f:
                parts = re.split(r"\s{2,}", line.strip(), maxsplit=1)
                if len(parts) < 2:
                    continue
                raw_word, definition = parts[0].lower(), parts[1].strip()
                base_word = re.sub(r"\d+$", "", raw_word)
                self.insert(base_word, definition)

    def _find_node(self, sequence: str) -> TrieNode | None:
        """
        Finds the trie node corresponding to a given sequence of characters.

        :param sequence: The sequence of characters to search for.
        
        :return: The TrieNode if the sequence exists, otherwise None.
        """
        node = self.root
        for char in sequence:
            node = node.children.get(char)
            if not node:
                return None
        return node

    def search_word(self, word: str) -> bool:
        """
        Checks if a word exists in the trie.

        :param word: The word to search for.
        
        :return: True if the word exists, False otherwise.
        """
        node = self._find_node(word)
        return bool(node and node.is_word)

    def search_prefix(self, prefix: str) -> bool:
        """
        Checks if a prefix exists in the trie.

        :param prefix: The prefix to search for.
        
        :return: True if the prefix exists, False otherwise.
        """
        node = self._find_node(prefix)
        return bool(node and node.children)

    def get_definition(self, word: str) -> str:
        """
        Retrieves the definition of a word from the trie.

        :param word: The word whose definition is to be retrieved.
        
        :return: The definition of the word if it exists, otherwise a default message.
        """
        node = self._find_node(word)
        return node.definition if node and node.is_word else "No definition available."
