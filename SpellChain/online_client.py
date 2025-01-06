import json, os, socket, sys, threading

from colors import Colors
from utils import clear_screen, input_with_prompt, safe_print, print_welcome_message


class OnlineGameClient:
    """
    Client for playing SpellChain online.
    """
    def __init__(self, host: str, port: int):
        """
        Initializes an OnlineGameClient with the specified server host and port.

        :param host: The server host.
        :param port: The server port.
        """
        self.server_host = host
        self.server_port = port
        self.socket = None
        self.room_id = None
        self.player_number = None
        self.player_count = None
        self.listener_thread = None
        self.stop_listener = False
        self.game_start_event = threading.Event()
        self.sequence = ""
        self.scores = {}
        self.current_player = 1
        self.round_count = 1

    def connect_to_server(self, action: str, player_count: int = None, room_id: str = None):
        """
        Connects to the server to create or join a game room.

        :param action: The action to take ('create' or 'join').
        :param player_count: Number of players if creating a room.
        :param room_id: Room ID if joining a room.
        """
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.connect((self.server_host, self.server_port))

            if action == "create" and player_count:
                self.create_room(player_count)
            elif action == "join" and room_id:
                self.join_room(room_id)
            else:
                raise ValueError("Action must be 'create' or 'join' with valid player count or room ID.")

            self.listener_thread = threading.Thread(target=self.listen_to_server, daemon=True)
            self.listener_thread.start()

            try:
                while not self.game_start_event.is_set() and not self.stop_listener:
                    self.game_start_event.wait(timeout=0.1)
            except (EOFError, KeyboardInterrupt):
                self.exit_game()

            if self.game_start_event.is_set():
                self.game_loop()

            self.listener_thread.join()
        except Exception as e:
            safe_print(f"{Colors.RED}{e}{Colors.RESET}")
            self.termination_handler()

    def create_room(self, player_count: int):
        """
        Sends a request to the server to create a new game room.

        :param player_count: Number of players expected in the room.
        """
        self.send_message({"type": "create_room", "player_count": player_count})

    def join_room(self, room_id: str):
        """
        Sends a request to the server to join an existing game room.

        :param room_id: The ID of the room to join.
        """
        self.send_message({"type": "join_room", "room_id": room_id})

    def listen_to_server(self) -> None:
        """
        Listens for messages from the server and handles them.
        """
        buffer = ""
        while not self.stop_listener:
            try:
                data = self.socket.recv(1024).decode()
                if not data:
                    safe_print(f"{Colors.RED}Disconnected from server.{Colors.RESET}")
                    self.termination_handler()
                buffer += data
                while "\n" in buffer:
                    line, buffer = buffer.split("\n", 1)
                    if line.strip():
                        self.handle_server_message(json.loads(line))
            except Exception as e:
                safe_print(f"{Colors.RED}{e}{Colors.RESET}")
                self.termination_handler()

    def handle_server_message(self, message: dict) -> None:
        """
        Handles incoming messages from the server based on message type.

        :param message: The message received from the server.
        """
        msg_type = message.get("type")
        if msg_type in ["room_created", "room_joined"]:
            self.room_id, self.player_number, self.player_count = message["room_id"], message["player_number"], message["player_count"]
            safe_print(f"{Colors.GREEN}{'Created' if msg_type == 'room_created' else 'Joined'} Room {self.room_id} as Player {self.player_number}, waiting for other players ...{Colors.RESET}")
        elif msg_type == "game_start":
            clear_screen()
            safe_print(f"{Colors.CYAN}Game started in Room {self.room_id}! You are Player {self.player_number}.{Colors.RESET}")
            self.display_game_status(message)
            self.game_start_event.set()
        elif msg_type == "game_update":
            self.display_game_update(message)
        elif msg_type == "game_end":
            self.handle_game_ended(message)
        elif msg_type == "error":
            safe_print(f"{Colors.RED}{message.get('message')}{Colors.RESET}")
            self.termination_handler()

    def send_message(self, message: dict) -> None:
        """
        Sends a message to the server.

        :param message: The message to send.
        """
        try:
            self.socket.sendall((json.dumps(message) + "\n").encode())
        except Exception as e:
            safe_print(f"{Colors.RED}{e}{Colors.RESET}")
            self.termination_handler()

    def game_loop(self) -> None:
        """
        Manages the game loop for live game updates.
        """
        try:
            while self.game_start_event.is_set():
                if self.player_number == self.current_player:
                    char = self.get_input_character()
                    if char == "exit":
                        self.exit_game()
                        break
                    self.send_message({"type": "add_character", "char": char})
                    self.current_player = 0 # Prevents local input while waiting for server update
        except (EOFError, KeyboardInterrupt):
            self.exit_game()

    def get_input_character(self) -> str:
        """
        Prompts the current player to input a character or exit the game.
        Ensures that the input is a single alphabetic or allowed punctuation character.

        :return: The validated input character or "exit".
        """
        ALLOWED_PUNCTUATIONS = set("-'/ .")
        while True:
            inp = input_with_prompt(
                f"{Colors.MAGENTA}Player {self.player_number}, enter a character (or \"exit\"): {Colors.RESET}"
            ).strip().lower()
            if inp == "exit" or (len(inp) == 1 and (inp.isalpha() or inp in ALLOWED_PUNCTUATIONS)):
                return inp
            safe_print(f"{Colors.RED}Invalid input. Please enter a single alphabetic or punctuation character.{Colors.RESET}")
    
    def display_game_status(self, message: dict) -> None:
        """
        Displays the current game status based on server updates.

        :param message: The message containing game status information.
        """
        self.sequence = message.get("sequence", "")
        self.scores = message.get("scores", {})
        self.current_player = message.get("current_player", 1)
        self.round_count = message.get("round_count", 1)
        self.print_status()
        if message.get("current_player", self.current_player) != self.player_number:
            safe_print(f"{Colors.CYAN}Waiting for Player {message.get('current_player', self.current_player)} ...{Colors.RESET}")

    def display_game_update(self, message: dict) -> None:
        """
        Displays updates to the game sequence and scores.

        :param message: The update message with game details.
        """
        player = message.get("player")
        char = message.get("char")
        self.sequence = message.get("sequence", "")
        self.scores = message.get("scores", self.scores)
        self.round_count = message.get("round_count", self.round_count)
        messages = message.get("messages", [])

        clear_screen()
        safe_print(f"{Colors.BLUE}Player {player} added \"{char}\" -> Sequence: \"{self.sequence}\"{Colors.RESET}")

        if messages:
            safe_print("\n" + "\n\n".join(messages))

        self.print_status()
        if message.get("current_player", self.current_player) != self.player_number:
            safe_print(f"{Colors.CYAN}Waiting for Player {message.get('current_player', self.current_player)} ...{Colors.RESET}")

        self.current_player = message.get("current_player", self.current_player)

    def print_status(self) -> None:
        """
        Prints the current sequence and score status.
        """
        sequence_line = f"{Colors.BLUE}Current sequence: \"{self.sequence}\"{Colors.RESET}"
        score_display = " | ".join([f"Player {player}: {self.scores.get(str(player), 0)}" for player in range(1, self.player_count + 1)])
        score_line = f"{Colors.GREEN}(Round {self.round_count}) Scores -> {score_display}{Colors.RESET}"

        bar_length = max(len(sequence_line), len(score_line))
        bar = f"{Colors.CYAN}{'=' * bar_length}{Colors.RESET}"

        safe_print(f"\n{bar}\n{sequence_line}\n{score_line}\n{bar}\n")

    def handle_game_ended(self, message: dict) -> None:
        """
        Handles the end of the game, printing final results.

        :param message: The message with final game results.
        """
        left_player = message.get("player_number")
        reason = message.get("reason", "Unknown")
        final_scores = message.get("scores", {})
        final_found_words = message.get("found_words", {})

        clear_screen()
        safe_print(f"{Colors.YELLOW}*** Player {left_player} has left the game. Reason: {reason} ***{Colors.RESET}\n")
        safe_print(f"{Colors.CYAN}{Colors.BOLD}Thank you for playing! Final Results:{Colors.RESET}")

        score_display = " | ".join([f"Player {player}: {final_scores.get(str(player), 0)}" for player in range(1, self.player_count + 1)])
        safe_print(f"{Colors.GREEN}(Round {self.round_count}) Final Scores -> {score_display}{Colors.RESET}\n")

        safe_print(f"{Colors.CYAN}{Colors.BOLD}Words Found by Each Player:{Colors.RESET}")
        for player in range(1, self.player_count + 1):
            safe_print(f"{Colors.BLUE}Player {player}:{Colors.RESET}")
            words = final_found_words.get(str(player), [])
            if words:
                safe_print(f"  Words: {', '.join(words)}")
            else:
                safe_print("  No words found.")
        self.termination_handler()

    def exit_game(self) -> None:
        """
        Sends a request to exit the game.
        """
        self.send_message({"type": "exit"})

    def termination_handler(self) -> None:
        """
        Handles termination, closing connections and resetting state.
        """
        self.game_start_event.clear()
        self.stop_listener = True
        self.socket.close()
        os._exit(0)

class OnlineGameClientWrapper:
    """
    Wrapper for managing online game client interactions like room creation and joining.
    """
    SERVER_HOST = "cloud-47f46d7f8e7e.mattisschulte.io"
    SERVER_PORT = 44390

    def start(self) -> None:
        """
        Starts the online game client wrapper, presenting options for creating or joining a room.
        """
        print_welcome_message()

        while True:
            _choice = input_with_prompt(f"{Colors.MAGENTA}Select an option (1: Create Room, 2: Join Room): {Colors.RESET}").strip()

            if _choice == "1":
                self.create_room()
            elif _choice == "2":
                self.join_room()
            elif _choice.lower() != "exit":
                print(f"{Colors.RED}Invalid choice. Please select a valid option.{Colors.RESET}")
                continue
            break

    def create_room(self) -> None:
        """
        Prompts the user to create a room with a specified number of players.
        """
        print_welcome_message()

        while True:
            num = input_with_prompt(f"{Colors.MAGENTA}Enter number of players (2-4): {Colors.RESET}").strip()
            if num.isdigit() and 2 <= int(num) <= 4:
                break
            elif num.lower() == "exit":
                sys.exit()
            print(f"{Colors.RED}Invalid number of players. Please enter a number between 2 and 4.{Colors.RESET}")

        self.client = OnlineGameClient(self.SERVER_HOST, self.SERVER_PORT)
        self.client.connect_to_server(action="create", player_count=int(num))

    def join_room(self) -> None:
        """
        Prompts the user to enter a room ID to join.
        """
        print_welcome_message()

        while True:
            room_id = input_with_prompt(f"{Colors.MAGENTA}Enter Room ID to join: {Colors.RESET}").strip()
            if room_id.lower() == "exit":
                sys.exit()
            elif room_id:
                break
            print(f"{Colors.RED}Room ID cannot be empty. Please enter a valid Room ID.{Colors.RESET}")

        self.client = OnlineGameClient(self.SERVER_HOST, self.SERVER_PORT)
        self.client.connect_to_server(action="join", room_id=room_id)
