import java.io.IOException;
import java.net.*;
import java.util.*;

public class Croupier {
    private static final int PORT = 12345;
    private DatagramSocket socket;
    private List<Player> players;
    private Map<String, Integer> playerBets;
    private Map<String, List<Card>> playerHands;
    private Map<String, List<Card>> splitHands;
    private Map<String, Boolean> playerStanding;
    private Map<String, Boolean> playerSplit;
    private Deck deck;

    public static void main(String[] args) throws IOException {
        new Croupier().start();
    }

    public Croupier() throws SocketException {
        socket = new DatagramSocket(PORT);
        players = new ArrayList<>();
        playerBets = new HashMap<>();
        playerHands = new HashMap<>();
        splitHands = new HashMap<>();
        playerStanding = new HashMap<>();
        playerSplit = new HashMap<>();
        deck = new Deck(4); // Erstellen eines Decks mit gerade 4 Kartendecks
    }

    public void start() throws IOException {
        while (true) {
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            String message = new String(packet.getData(), 0, packet.getLength());
            handleRequest(packet, message);
        }
    }

    private void handleRequest(DatagramPacket packet, String message) throws IOException {
        String[] parts = message.split(" ");
        String command = parts[0];
        switch (command) {
            case "registerPlayer":
                handleRegisterPlayer(packet, parts[1], parts[2], parts[3]);
                break;
            case "bet":
                handleBet(packet, parts[1], Integer.parseInt(parts[2]));
                break;
            case "hit":
                handleHit(packet, parts[1]);
                break;
            case "stand":
                handleStand(packet, parts[1]);
                break;
            case "split":
                handleSplit(packet, parts[1]);
                break;
            case "doubleDown":
                handleDoubleDown(packet, parts[1]);
                break;
            case "surrender":
                handleSurrender(packet, parts[1]);
                break;
            case "registerCounter":
                handleRegisterCounter(packet, parts[1], parts[2], parts[3]);
                break;
            case "removePlayer":
                handleRemovePlayer(packet, parts[1]);
                break;
        }
    }

    private void handleRegisterPlayer(DatagramPacket packet, String ip, String port, String name) throws IOException {
        if (players.size() >= 5) { // Spielerzahl hier einfügen
            sendResponse(packet.getAddress(), packet.getPort(), "registration declined zu viele Spieler");
        } else {
            Player player = new Player(name, ip, Integer.parseInt(port));
            players.add(player);
            playerHands.put(name, new ArrayList<>());
            splitHands.put(name, new ArrayList<>());
            playerStanding.put(name, false);
            playerSplit.put(name, false);
            sendResponse(packet.getAddress(), packet.getPort(), "registration successful");
        }
    }

    private void handleBet(DatagramPacket packet, String name, int bet) throws IOException {
        Optional<Player> playerOpt = players.stream().filter(p -> p.getName().equals(name)).findFirst();
        if (playerOpt.isPresent()) {
            playerBets.put(name, bet);
            sendResponse(packet.getAddress(), packet.getPort(), "bet accepted");
        } else {
            sendResponse(packet.getAddress(), packet.getPort(), "bet declined unbekannter Spieler");
        }
    }

    private void handleHit(DatagramPacket packet, String name) throws IOException {
        Optional<Player> playerOpt = players.stream().filter(p -> p.getName().equals(name)).findFirst();
        if (playerOpt.isPresent() && !playerStanding.get(name)) {
            List<Card> hand = playerHands.get(name);
            List<Card> splitHand = splitHands.get(name);

            if (!hand.isEmpty() && (splitHand == null || splitHand.isEmpty() || playerSplit.get(name))) {
                Card card = deck.dealCard();
                hand.add(card);
                sendCardToPlayer(playerOpt.get(), card);
                sendResponse(packet.getAddress(), packet.getPort(), "action accepted");

                if (calculateHandValue(hand) > 21) {
                    playerBets.put(name, 0); // Einsatz sofort einziehen
                    sendResponse(packet.getAddress(), packet.getPort(), "gameover Du hast verloren");
                }
            } else if (!splitHand.isEmpty() && !playerSplit.get(name)) {
                Card card = deck.dealCard();
                splitHand.add(card);
                sendCardToPlayer(playerOpt.get(), card);
                sendResponse(packet.getAddress(), packet.getPort(), "action accepted");

                if (calculateHandValue(splitHand) > 21) {
                    playerBets.put(name, 0); // Einsatz sofort einziehen
                    sendResponse(packet.getAddress(), packet.getPort(), "gameover Du hast verloren");
                }
            }
        } else {
            sendResponse(packet.getAddress(), packet.getPort(), "action declined unbekannter Spieler oder bereits gestanden");
        }
    }

    private void handleStand(DatagramPacket packet, String name) throws IOException {
        Optional<Player> playerOpt = players.stream().filter(p -> p.getName().equals(name)).findFirst();
        if (playerOpt.isPresent()) {
            playerStanding.put(name, true);
            sendResponse(packet.getAddress(), packet.getPort(), "action accepted");
        } else {
            sendResponse(packet.getAddress(), packet.getPort(), "action declined unbekannter Spieler");
        }
    }

    private void handleSplit(DatagramPacket packet, String name) throws IOException {
        Optional<Player> playerOpt = players.stream().filter(p -> p.getName().equals(name)).findFirst();
        if (playerOpt.isPresent() && !playerSplit.get(name)) {
            List<Card> hand = playerHands.get(name);
            if (hand.size() == 2 && hand.get(0).getRank() == hand.get(1).getRank()) {
                List<Card> splitHand = new ArrayList<>();
                splitHand.add(hand.remove(1)); // Eine Karte in die gesplittete Hand verschieben
                playerHands.put(name, hand);
                splitHands.put(name, splitHand);
                playerSplit.put(name, true);

                // Beide Hände erhalten eine neue Karte
                Card newCard1 = deck.dealCard();
                hand.add(newCard1);
                sendCardToPlayer(playerOpt.get(), newCard1);

                Card newCard2 = deck.dealCard();
                splitHand.add(newCard2);
                sendCardToPlayer(playerOpt.get(), newCard2);

                sendResponse(packet.getAddress(), packet.getPort(), "action accepted");
            } else {
                sendResponse(packet.getAddress(), packet.getPort(), "action declined keine passenden Karten zum Split");
            }
        } else {
            sendResponse(packet.getAddress(), packet.getPort(), "action declined unbekannter Spieler oder bereits gesplittet");
        }
    }

    private void handleDoubleDown(DatagramPacket packet, String name) throws IOException {
        Optional<Player> playerOpt = players.stream().filter(p -> p.getName().equals(name)).findFirst();
        if (playerOpt.isPresent() && !playerStanding.get(name)) {
            List<Card> hand = playerHands.get(name);
            if (hand.size() == 2) {
                playerBets.put(name, playerBets.get(name) * 2); // Einsatz verdoppeln
                Card newCard = deck.dealCard();
                hand.add(newCard);
                sendCardToPlayer(playerOpt.get(), newCard);
                playerStanding.put(name, true);
                sendResponse(packet.getAddress(), packet.getPort(), "action accepted");

                if (calculateHandValue(hand) > 21) {
                    sendResponse(packet.getAddress(), packet.getPort(), "gameover Du hast verloren");
                }
            } else {
                sendResponse(packet.getAddress(), packet.getPort(), "action declined nicht genug Karten für DoubleDown");
            }
        } else {
            sendResponse(packet.getAddress(), packet.getPort(), "action declined unbekannter Spieler oder bereits gestanden");
        }
    }

    private void handleSurrender(DatagramPacket packet, String name) throws IOException {
        Optional<Player> playerOpt = players.stream().filter(p -> p.getName().equals(name)).findFirst();
        if (playerOpt.isPresent()) {
            List<Card> hand = playerHands.get(name);
            if (hand.size() == 2) {
                int currentBet = playerBets.get(name);
                playerBets.put(name, currentBet / 2); // Spieler bekommt die Hälfte seines Einsatzes zurück

                // Spieler aus dem Spiel entfernen
                players.remove(playerOpt.get());
                playerHands.remove(name);
                splitHands.remove(name);
                playerStanding.remove(name);
                playerSplit.remove(name);

                sendResponse(packet.getAddress(), packet.getPort(), "action accepted gameover surrender erfolgreich");
            } else {
                sendResponse(packet.getAddress(), packet.getPort(), "action declined nicht genug Karten für Surrender");
            }
        } else {
            sendResponse(packet.getAddress(), packet.getPort(), "action declined unbekannter Spieler");
        }
    }

    private void handleRegisterCounter(DatagramPacket packet, String ip, String port, String playerName) throws IOException {
        Optional<Player> playerOpt = players.stream().filter(p -> p.getName().equals(playerName)).findFirst();
        if (playerOpt.isPresent()) {
            sendResponse(packet.getAddress(), packet.getPort(), "registration successful");
        } else {
            sendResponse(packet.getAddress(), packet.getPort(), "registration declined unbekannter Spielername");
        }
    }

    private void handleRemovePlayer(DatagramPacket packet, String playerName) throws IOException {
        Optional<Player> playerOpt = players.stream().filter(p -> p.getName().equals(playerName)).findFirst();
        if (playerOpt.isPresent()) {
            players.remove(playerOpt.get());
            playerHands.remove(playerName);
            splitHands.remove(playerName);
            playerStanding.remove(playerName);
            playerSplit.remove(playerName);
            playerBets.remove(playerName);
            sendResponse(packet.getAddress(), packet.getPort(), "player removed");
        } else {
            sendResponse(packet.getAddress(), packet.getPort(), "player not found");
        }
    }

    private void sendResponse(InetAddress address, int port, String message) throws IOException {
        byte[] buffer = message.getBytes();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
        socket.send(packet);
    }

    private void sendCardToPlayer(Player player, Card card) throws IOException {
        String message = "card " + card.getSuit() + " " + card.getRank();
        sendResponse(InetAddress.getByName(player.getIp()), player.getPort(), message);
    }

    private int calculateHandValue(List<Card> hand) {
        int value = 0;
        int aces = 0;

        for (Card card : hand) {
            switch (card.getRank()) {
                case 1: // Ass
                    aces++;
                    value += 11;
                    break;
                case 11: // Bube
                case 12: // Dame
                case 13: // König
                    value += 10;
                    break;
                default:
                    value += card.getRank();
                    break;
            }
        }

        while (value > 21 && aces > 0) {
            value -= 10;
            aces--;
        }

        return value;
    }
}
