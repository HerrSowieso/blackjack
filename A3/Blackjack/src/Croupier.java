import com.fasterxml.jackson.core.JsonProcessingException;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Croupier {
    private List<Card> deck;
    private List<Card> dealtCards;
    private List<Player> players;
    private static final int MAX_PLAYERS = 5; // Example limit for maximum players
    private static final int MAX_BET = 1000; // Example limit for maximum bet

    public Croupier(int numberOfDecks) {
        this.deck = new ArrayList<>();
        this.dealtCards = new ArrayList<>();
        this.players = new ArrayList<>();
        initializeDeck(numberOfDecks);
        shuffleDeck();
    }

    // Initialize the deck with the specified number of decks
    private void initializeDeck(int numberOfDecks) {
        for (int i = 0; i < numberOfDecks; i++) {
            for (Card.Color color : Card.Color.values()) {
                for (Card.Value value : Card.Value.values()) {
                    deck.add(new Card(color, value, i + 1));
                }
            }
        }
    }

    // Shuffle the deck
    private void shuffleDeck() {
        Collections.shuffle(deck);
    }

    // Deal a card from the deck
    public Card dealCard(String owner) {
        if (deck.isEmpty()) {
            throw new IllegalStateException("No cards left in the deck");
        }
        Card dealtCard = deck.remove(deck.size() - 1);
        dealtCard.setOwner(owner);
        dealtCards.add(dealtCard);
        return dealtCard;
    }

    // Get the number of cards remaining in the deck
    public int cardsRemaining() {
        return deck.size();
    }

    // Reset the deck by combining the dealt cards back into the deck and reshuffling
    public void resetDeck() {
        deck.addAll(dealtCards);
        dealtCards.clear();
        shuffleDeck();
    }

    // Register a player
    public String registerPlayer(String ipAddress, int port, String name) {
        if (players.size() >= MAX_PLAYERS) {
            return "registration declined: zu viele Spieler";
        }
        players.add(new Player(ipAddress, port, name));
        return "registration successful";
    }

    // Place a bet
    public String placeBet(String name, int betAmount) {
        if (betAmount > MAX_BET) {
            return "bet declined: Einsatz zu hoch";
        }
        // Further logic for bet handling can be added here
        return "bet accepted";
    }

    // Deal a card and send it to all players
    public void dealAndSendCard(String owner) {
        Card card = dealCard(owner);
        String cardJson;
        try {
            cardJson = card.toJSON();
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return;
        }

        for (Player player : players) {
            sendCardToPlayer(player, cardJson);
        }
    }

    // Send a card to a player
    private void sendCardToPlayer(Player player, String cardJson) {
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] buffer = cardJson.getBytes();
            InetAddress playerAddress = InetAddress.getByName(player.getIpAddress());
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, playerAddress, player.getPort());
            socket.send(packet);

            // Receive acknowledgment
            byte[] responseBuffer = new byte[1024];
            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
            socket.receive(responsePacket);
            String response = new String(responsePacket.getData(), 0, responsePacket.getLength());
            System.out.println("Player response: " + response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // UDP server to listen for player requests
    public void startUDPServer(int port) {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("Croupier UDP server started on port " + port);
            byte[] buffer = new byte[1024];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength());
                System.out.println("Received: " + message);

                // Parse the message
                String[] parts = message.split(" ");
                String responseMessage = "";
                if (parts.length == 4 && "registerPlayer".equals(parts[0])) {
                    String ipAddress = parts[1];
                    int playerPort = Integer.parseInt(parts[2]);
                    String name = parts[3];
                    responseMessage = registerPlayer(ipAddress, playerPort, name);
                } else if (parts.length == 3 && "bet".equals(parts[0])) {
                    String name = parts[1];
                    int betAmount = Integer.parseInt(parts[2]);
                    responseMessage = placeBet(name, betAmount);
                } else {
                    responseMessage = "unknown command";
                }

                // Send response
                byte[] responseBuffer = responseMessage.getBytes();
                InetAddress playerAddress = packet.getAddress();
                int playerPortResponse = packet.getPort();
                DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, playerAddress, playerPortResponse);
                socket.send(responsePacket);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Croupier croupier = new Croupier(1); // Initialize with 1 deck
        croupier.startUDPServer(9999); // Start UDP server on port 9999
    }
}
