import json, logging, os, re, socket, signal, sys, threading, uuid
from collections import defaultdict

from trie import DictionaryTrie
from colors import Colors

logging.basicConfig(level=logging.INFO, format='[%(asctime)s] %(levelname)s: %(message)s')


class GameRoom:
    def __init__(self, room_id: str, player_count: int, dictionary: DictionaryTrie):
        """
        Initializes a new GameRoom instance.

        :param room_id: The unique identifier for the room.
        :param player_count: The number of players in the room.
        :param dictionary: The dictionary trie used for word validation.
        """
        self.room_id = room_id
        self.player_count = player_count
        self.dictionary = dictionary
        self.clients = []  # List of tuples: (client_socket, player_number)
        self.scores = defaultdict(int)
        self.sequence = ""
        self.round_count = 1
        self.current_player = 1
        self.found_words = defaultdict(set)
        self.lock = threading.Lock()

    def broadcast(self, message: dict):
        """
        Sends a message to all clients in the room.

        :param message: The message dictionary to be serialized and sent.
        """
        serialized = (json.dumps(message) + '\n').encode()
        for client, _ in self.clients:
            try:
                client.sendall(serialized)
            except Exception as e:
                logging.error(f"Failed to send message to client: {e}")

    def add_player(self, client_socket: socket.socket) -> tuple[bool, int | str]:
        """
        Adds a player to the room if there is space available.

        :param client_socket: The socket of the client to add.
        :return: A tuple containing a success boolean and either a player number or an error message.
        """
        with self.lock:
            if len(self.clients) >= self.player_count:
                return False, "Room is already full."
            player_number = len(self.clients) + 1
            self.clients.append((client_socket, player_number))
            return True, player_number

    def start_game(self):
        """
        Starts the game by broadcasting the initial game state to all clients.
        """
        logging.info(f"Starting game in Room {self.room_id} with {self.player_count} players.")
        initial_message = {
            "type": "game_start",
            "current_player": self.current_player,
            "sequence": self.sequence,
            "scores": dict(self.scores),
            "round_count": self.round_count
        }
        self.broadcast(initial_message)

    def switch_player(self) -> int:
        """
        Determines the next player's turn.

        :return: The player number of the next player.
        """
        return (self.current_player % self.player_count) + 1

    def process_add_character(self, player_number: int, char: str):
        """
        Processes the addition of a character to the current sequence by a player.

        :param player_number: The number of the player who added the character.
        :param char: The character added by the player.
        """
        with self.lock:
            if player_number != self.current_player:
                return self.create_error("Not your turn.")

            self.sequence += char
            messages = []

            if self.dictionary.search_word(self.sequence):
                if not self.is_word_used(self.sequence):
                    points = max((len(self.sequence) + 1) // 2, 1)
                    self.scores[player_number] += points
                    self.found_words[player_number].add(self.sequence)
                    definition = self.dictionary.get_definition(self.sequence)
                    messages.append(
                        f"{Colors.GREEN}{Colors.BOLD}*** Player {player_number} completed \"{self.sequence}\"! "
                        f"({points} Point{'s' if points != 1 else ''}) ***{Colors.RESET}\n"
                        f"Definition: {definition[:600]}{'…' if len(definition) > 600 else ''}"
                    )
                else:
                    messages.append(
                        f"{Colors.YELLOW}\"{self.sequence}\" has already been used in the SpellChain. "
                        f"No points this round.{Colors.RESET}"
                    )

            if not self.dictionary.search_prefix(self.sequence):
                messages.append(
                    f"{Colors.RED}\"{self.sequence}\" is not a valid prefix of any word.\n"
                    f"Round over. The sequence will reset.{Colors.RESET}"
                )
                self.sequence = ""
                self.round_count += 1

            self.current_player = self.switch_player()
            response = {
                "type": "game_update",
                "player": player_number,
                "char": char,
                "messages": messages,
                "current_player": self.current_player,
                "sequence": self.sequence,
                "scores": dict(self.scores),
                "round_count": self.round_count
            }

            logging.info(f"Room {self.room_id} Update: {response}")
            self.broadcast(response)

    def is_word_used(self, word: str) -> bool:
        """
        Checks if a word has already been used by any player.

        :param word: The word to check.
        :return: True if the word has been used, False otherwise.
        """
        return any(word in words for words in self.found_words.values())

    def remove_player(self, player_number: int, reason: str = "Disconnected"):
        """
        Removes a player from the room and broadcasts the final game state.

        :param player_number: The number of the player to remove.
        :param reason: The reason for removal.
        """
        with self.lock:
            logging.info(f"Player {player_number} left Room {self.room_id}. Reason: {reason}")
            update_message = {
                "type": "game_end",
                "player_number": player_number,
                "reason": reason,
                "scores": dict(self.scores),
                "found_words": {player: sorted(words) for player, words in self.found_words.items()}
            }
            self.broadcast(update_message)
            for client, _ in self.clients:
                try:
                    client.close()
                except Exception as e:
                    logging.error(f"Error closing client socket: {e}")
            self.clients.clear()


class SpellChainServer:
    def __init__(self, host: str, port: int, dictionary_file: str = os.path.join(os.path.dirname(__file__), "oxford_english_dictionary.txt")):
        """
        Initializes the SpellChainServer instance, loading the dictionary file.

        :param host: Host IP address to bind the server.
        :param port: Port number to bind the server.
        :param dictionary_file: Path to the dictionary file.
        """
        self.host = host
        self.port = port
        self.dictionary_trie = DictionaryTrie()
        self.dictionary_trie.load_dictionary(dictionary_file)
        self.rooms = {}
        self.lock = threading.Lock()

    def start(self):
        """
        Starts the server, accepting connections and handling clients.
        """
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server_socket:
            server_socket.bind((self.host, self.port))
            server_socket.listen()
            server_socket.settimeout(1.0)
            logging.info(f"SpellChain Server started on {self.host}:{self.port}")

            while True:
                try:
                    client_socket, addr = server_socket.accept()
                    logging.info(f"Accepted connection from {addr}")
                    threading.Thread(target=self.handle_client, args=(client_socket,), daemon=True).start()
                except socket.timeout:
                    continue

    def shutdown(self):
        """
        Shuts down the server, closing all active connections and clearing rooms.
        """
        logging.info("Closing all active connections and cleaning up.")
        with self.lock:
            for room in self.rooms.values():
                for client, _ in room.clients:
                    try:
                        client.close()
                    except Exception as e:
                        logging.error(f"Error closing client socket: {e}")
            self.rooms.clear()
        logging.info("Server has been shut down.")

    def handle_client(self, client_socket: socket.socket):
        """
        Handles messages from a connected client.

        :param client_socket: The client's socket connection.
        """
        MAX_MESSAGE_SIZE = 1024
        room = None
        player_number = None

        try:
            client_socket.settimeout(1800)
            with client_socket, client_socket.makefile('r') as client_file:
                for line in client_file:
                    if len(line) > MAX_MESSAGE_SIZE:
                        self.send_error(client_socket, "Message too large.")
                        continue
                    try:
                        message = json.loads(line.strip())
                    except json.JSONDecodeError:
                        self.send_error(client_socket, "Invalid JSON format.")
                        continue
                    if not message:
                        continue

                    response = self.handle_message(message, client_socket, room, player_number)
                    if isinstance(response, tuple):
                        room, player_number = response
        except (ConnectionResetError, BrokenPipeError):
            logging.warning("Client disconnected unexpectedly.")
            if room and player_number:
                self.remove_player(room, player_number, reason="Network disconnection")
        except Exception as e:
            logging.error(f"An error occurred: {e}")
            if room and player_number:
                self.remove_player(room, player_number, reason="Unexpected error")

    def handle_message(self, message: dict, client_socket: socket.socket, room: GameRoom, player_number: int) -> tuple[GameRoom, int] | None:
        """
        Dispatches client messages to the appropriate handler based on type.

        :param message: The incoming message as a dictionary.
        :param client_socket: The client's socket connection.
        :param room: The GameRoom the client is in, or None if not assigned.
        :param player_number: The player's number in the room, or None if not assigned.
        :return: The (room, player_number) tuple if they are modified, else None.
        """
        msg_type = message.get("type")
        handlers = {
            "create_room": self.create_room,
            "join_room": self.join_room,
            "add_character": self.add_character,
            "exit": self.exit_game
        }

        if handler := handlers.get(msg_type):
            return handler(message, client_socket, room, player_number)
        else:
            self.send_error(client_socket, "Unknown message type.")

    def create_room(self, message: dict, client_socket: socket.socket, room: GameRoom, player_number: int) -> tuple[GameRoom, int] | None:
        """
        Creates a new game room.

        :param message: The incoming message as a dictionary.
        :param client_socket: The client's socket connection.
        :param room: Placeholder for current room, not used here.
        :param player_number: Placeholder for player number, not used here.
        :return: A tuple containing the current room and player number, if created.
        """
        player_count = message.get("player_count")
        if not isinstance(player_count, int) or not (2 <= player_count <= 4):
            self.send_error(client_socket, "Invalid player count. Must be between 2 and 4.")
            return

        room_id = str(uuid.uuid4())[:6].upper()
        room = GameRoom(room_id, player_count, self.dictionary_trie)
        with self.lock:
            self.rooms[room_id] = room

        success, result = room.add_player(client_socket)
        if success:
            player_number = result
            response = {
                "type": "room_created",
                "room_id": room_id,
                "player_number": player_number,
                "player_count": player_count
            }
            self.send_message(client_socket, response)
            logging.info(f"Room {room_id} created with {player_count} player slots.")

            return room, player_number
        else:
            self.send_error(client_socket, result)

    def join_room(self, message: dict, client_socket: socket.socket, room: GameRoom, player_number: int) -> tuple[GameRoom, int] | None:
        """
        Allows a player to join an existing game room.

        :param message: The incoming message as a dictionary.
        :param client_socket: The client's socket connection.
        :param room: Placeholder for current room, not used here.
        :param player_number: Placeholder for player number, not used here.
        :return: A tuple containing the current room and player number, if joined successfully.
        """
        ROOM_ID_PATTERN = re.compile(r'^[A-Z0-9]{6}$')
        room_id = message.get("room_id")

        if not (room_id and ROOM_ID_PATTERN.match(room_id)):
            self.send_error(client_socket, "Invalid or missing Room ID.")
            return

        with self.lock:
            room = self.rooms.get(room_id)

        if not room:
            self.send_error(client_socket, "Room ID could not be found.")
            return

        success, player_number = room.add_player(client_socket)
        if not success:
            self.send_error(client_socket, player_number)
            return

        self.send_message(client_socket, {
            "type": "room_joined",
            "room_id": room_id,
            "player_number": player_number,
            "player_count": room.player_count
        })
        logging.info(f"Player {player_number} joined Room {room_id}.")

        if len(room.clients) == room.player_count:
            room.start_game()

        return room, player_number

    def add_character(self, message: dict, client_socket: socket.socket, room: GameRoom, player_number: int):
        """
        Adds a character to the current sequence for the player's turn.

        :param message: The incoming message as a dictionary.
        :param client_socket: The client's socket connection.
        :param room: The current game room.
        :param player_number: The player's number in the room.
        """
        if not room or not player_number:
            self.send_error(client_socket, "You are not part of any room.")
            return

        char = message.get("char").lower()
        ALLOWED_PUNCTUATIONS  = set("-'/ .")
        if not isinstance(char, str) or len(char) != 1 or not (char.isalpha() or char in ALLOWED_PUNCTUATIONS):
            self.send_error(client_socket, "Invalid character input.")
            return

        room.process_add_character(player_number, char)

    def exit_game(self, message: dict, client_socket: socket.socket, room: GameRoom, player_number: int):
        """
        Handles a player's request to exit the game.

        :param message: The incoming message as a dictionary.
        :param client_socket: The client's socket connection.
        :param room: The current game room.
        :param player_number: The player's number in the room.
        """
        if room and player_number:
            self.remove_player(room, player_number, reason="Player exited the game.")

    def remove_player(self, room: GameRoom, player_number: int, reason: str = "Disconnected"):
        """
        Removes a player from the room and deletes the room if empty.

        :param room: The current game room.
        :param player_number: The player's number to remove.
        :param reason: The reason for removal.
        """
        room.remove_player(player_number, reason)
        with self.lock:
            if not room.clients:
                del self.rooms[room.room_id]
                logging.info(f"Terminated Room {room.room_id}.")

    def send_error(self, client_socket: socket.socket, error_message: str):
        """
        Sends an error message to a client.

        :param client_socket: The client's socket connection.
        :param error_message: The error message to send.
        """
        self.send_message(client_socket, {"type": "error", "message": error_message})

    @staticmethod
    def send_message(client_socket: socket.socket, message: dict):
        """
        Sends a message to a specific client.

        :param client_socket: The client's socket connection.
        :param message: The message dictionary to be serialized and sent.
        """
        try:
            client_socket.sendall((json.dumps(message) + '\n').encode())
        except Exception as e:
            logging.error(f"Failed to send message to client: {e}")


if __name__ == "__main__":
    HOST = '0.0.0.0'
    PORT = 44390
    server = SpellChainServer(HOST, PORT)

    def shutdown_handler(*_):
        logging.info("Shutdown signal received. Shutting down the server.")
        server.shutdown()
        sys.exit(0)

    signal.signal(signal.SIGINT, shutdown_handler)
    signal.signal(signal.SIGTERM, shutdown_handler)

    try:
        server.start()
    except (KeyboardInterrupt, Exception) as e:
        logging.error(f"Server shutdown due to: {e}")
        server.shutdown()
