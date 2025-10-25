import os, sys, json
from colors import Colors


class TrieNode:
    """
    Represents a node in the trie data structure.
    """
    __slots__ = ("children", "definition")

    def __init__(self):
        self.children: dict[str, TrieNode] = {}
        self.definition: str | None = None


class DictionaryTrie:
    """
    Implements a trie data structure for storing words and their definitions.
    """
    DEF_MAX = 500
    NO_DEF = "No definition available."

    def __init__(self):
        self.root = TrieNode()

    def insert(self, word: str, definition: str | None = None) -> None:
        """
        Inserts a word and its definition into the trie.

        :param word: The word to insert.
        :param definition: Optional definition to store for the word.
        """
        node = self.root
        for ch in word:
            node = node.children.setdefault(ch, TrieNode())
        node.definition = definition if definition else self.NO_DEF

    def _find_node(self, sequence: str) -> TrieNode | None:
        """
        Finds the trie node corresponding to a given sequence of characters.

        :param sequence: The sequence of characters to search for.
        
        :return: The TrieNode if the sequence exists, otherwise None.
        """
        node = self.root
        for ch in sequence:
            node = node.children.get(ch)
            if node is None:
                return None
        return node

    def search_word(self, word: str) -> bool:
        """
        Checks if a word exists in the trie.

        :param word: The word to search for.
        
        :return: True if the word exists, False otherwise.
        """
        node = self._find_node(word)
        return bool(node and node.definition is not None)

    def search_prefix(self, prefix: str) -> bool:
        """
        Checks if a prefix exists in the trie.

        :param prefix: The prefix to search for.
        
        :return: True if the prefix exists, False otherwise.
        """
        return bool(self._find_node(prefix) and self._find_node(prefix).children)

    def get_definition(self, word: str) -> str | None:
        """
        Retrieves the definition of a word from the trie.

        :param word: The word whose definition is to be retrieved.
        
        :return: The definition of the word if it exists, otherwise a default message.
        """
        node = self._find_node(word)
        return node.definition if node and node.definition is not None else self.NO_DEF

    def load_dictionary(self, dictionary_file: str) -> None:
        """
        Loads words and their definitions from a JSON dictionary file into the trie.
        The JSON is expected to be an object mapping words to a map of part-of-speech (POS) to a list
        of senses. Each sense is an object with fields:
          - "gloss": string definition text
          - "tags": optional list of strings

        :param dictionary_file: Path to the dictionary file.
        """
        if not os.path.exists(dictionary_file):
            os.system('cls||clear')
            print(f"{Colors.RED}Dictionary file \"{dictionary_file}\" not found.{Colors.RESET}")
            sys.exit(1)

        with open(dictionary_file, "r", encoding="utf-8") as f:
            data = json.load(f)

        if isinstance(data, dict):
            for w, d in data.items():
                word = self._normalize_word(w)
                if not word or not isinstance(d, dict):
                    continue
                definition = self._format_definition(d)
                self.insert(word, self._truncate(definition, self.DEF_MAX))
        else:
            os.system('cls||clear')
            print(f"{Colors.RED}Invalid dictionary JSON format (expected root object).{Colors.RESET}")
            sys.exit(1)
    
    @staticmethod
    def _normalize_word(word: str) -> str | None:
        return word.strip().lower() or None
    
    @staticmethod
    def _truncate(s: str, max_len: int) -> str:
        if s is None or len(s) <= max_len:
            return s
        return s[: max(0, max_len - 1)] + "…"

    @staticmethod
    def _format_definition(by_pos: dict) -> str:
        """
        Formats the definition dictionary into a readable string.

        :param by_pos: Dictionary mapping parts of speech to lists of senses.

        :return: Formatted definition string.
        """
        parts = []
        for pos in sorted(by_pos.keys()):
            senses = by_pos.get(pos) or []
            if not senses:
                continue
            seg = [f"{pos}: "]
            sense_strs = []
            for i, s in enumerate(senses, start=1):
                gloss = s.get("gloss", "")
                tags = s.get("tags", []) or []
                piece = f"{i}. {gloss}"
                if tags:
                    piece += f" [{', '.join(tags)}]"
                sense_strs.append(piece)
            seg.append(" ; ".join(sense_strs))
            parts.append("".join(seg))
        return " | ".join(parts) if parts else DictionaryTrie.NO_DEF
