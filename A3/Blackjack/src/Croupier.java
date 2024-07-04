//einer meiner vielen erfolglosen Versuche

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Croupier {
    private List<Card> deck;
    private List<Card> dealtCards;
    private List<PlayerInfo> players;
    private List<List<Card>> playerHands;
    private List<Card> dealerHand;
    private static final int MAX_PLAYERS = 5; // Example limit for maximum players
    private static final int MAX_BET = 1000; // Example limit for maximum bet
    private static final int BLACKJACK_PAYOUT_MULTIPLIER = 3; // Payout multiplier for Blackjack

    public Croupier(int numberOfDecks) {
        this.deck = new ArrayList<>();
        this.dealtCards = new ArrayList<>();
        this.players = new ArrayList<>();
        this.playerHands = new ArrayList<>();
        this.dealerHand = new ArrayList<>();
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
    public Card dealCard(String playerName) {
        if (deck.isEmpty()) {
            throw new IllegalStateException("No cards left in the deck");
        }
        Card dealtCard = deck.remove(deck.size() - 1);
        dealtCard.setOwner(playerName);
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
    public String registerPlayer(String playerName, String ipAddress, int port) {
        if (players.size() >= MAX_PLAYERS) {
            return "registration declined: zu viele Spieler";
        }
        players.add(new PlayerInfo(playerName, ipAddress, port));
        playerHands.add(new ArrayList<>());
        return "registration successful";
    }

    // Place a bet
    public String placeBet(String playerName, int betAmount) {
        if (betAmount > MAX_BET) {
            return "bet declined: Einsatz zu hoch";
        }
        PlayerInfo player = findPlayerByName(playerName);
        if (player == null) {
            return "bet declined: Spieler nicht gefunden";
        }
        if (player.getBalance() < betAmount) {
            return "bet declined: Nicht genug Guthaben";
        }
        player.setBet(betAmount);
        return "bet accepted";
    }

    // Deal a card and send it to all players
    public void dealAndSendCard(String playerName) { //Wahrscheinlich durch Methode sendCardToPlayer nutzlos geworden.
        Card card = dealCard(playerName);
        String cardJson;
        try {
            cardJson = card.toJSON();
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return;
        }

        for (PlayerInfo player : players) {
            sendCardToPlayer(player, cardJson);
        }
    }

    // Send a card to a player
    private void sendCardToPlayer(PlayerInfo player, String cardJson) {
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] buffer = cardJson.getBytes();
            InetAddress playerAddress = InetAddress.getByName(player.getIpAddress());
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, playerAddress, player.getPort());
            socket.send(packet);

            // Receive acknowledgment (simulated for demonstration, actual implementation may vary)
            String response = "Card received";
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

                // Process player action
                processPlayerAction(message);

                // Check if all players have completed their actions
                if (allPlayersStandOrBust()) {
                    // Dealer plays
                    playDealerTurn();
                    // Determine game outcome
                    determineGameOutcome();
                    // Calculate and distribute payouts
                    calculateAndDistributePayouts();
                    // Reset hands for the next round
                    resetHands();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Process player action message
    private void processPlayerAction(String message) {
        String[] parts = message.split(" ");
        if (parts.length < 4) {
            if(parts.length == 3 && Objects.equals(parts[0], "bet")) // versucht die Methode placeBet nochmals einzubinden. Wurde zuvor durch die Methode processPlayerAction nutzlos.
                placeBet(parts[1], Integer.parseInt(parts[2]));
            System.out.println("Action declined: Incomplete message");
            return;
        }

        String action = parts[0];
        if(Objects.equals(action, "register")) //versucht die Methode registerPlayer nochmals einzubeziehen. Wurde durch diese Methode zuvor nutzlos.
            registerPlayer(parts[3], parts[1], Integer.parseInt(parts[2]));
        else {
            String playerName = parts[1];
            int deckNumber = Integer.parseInt(parts[2]);
            String lastCardToString = parts[3];

            String response = handlePlayerAction(action, playerName, deckNumber, lastCardToString);
            System.out.println("Response: " + response);
        }
    }

    // Handle player action
    private String handlePlayerAction(String action, String playerName, int deckNumber, String lastCardToString) {
        switch (action) {
            case "hit":
                return handleHit(playerName, deckNumber, lastCardToString);
            case "stand":
                return handleStand(playerName, deckNumber, lastCardToString);
            case "split":
                return handleSplit(playerName, deckNumber, lastCardToString);
            case "doubleDown":
                return handleDoubleDown(playerName, deckNumber, lastCardToString);
            case "surrender":
                return handleSurrender(playerName, deckNumber, lastCardToString);
            default:
                return "action declined: unknown action";
        }
    }

    private String handleHit(String playerName, int deckNumber, String lastCardToString) {
        List<Card> hand = playerHands.get(findPlayerIndex(playerName));
        Card newCard = dealCard(playerName);
        hand.add(newCard);

        // Check if player busts
        if (calculateHandValue(hand) > 21) {
            return "action declined: player busts";
        }

        return "action accepted";
    }

    private String handleStand(String playerName, int deckNumber, String lastCardToString) {
        // Player's turn ends, no additional action needed
        return "action accepted";
    }

    private String handleSplit(String playerName, int deckNumber, String lastCardToString) {
        // Logic to handle player splitting         //hier fehlt noch etwas, was auch wichtig zur Gewinnberechnung gebraucht wird. Implementierung hat nicht funktioniert.
        // Implement as per your game's rules
        return "action accepted";
    }

    private String handleDoubleDown(String playerName, int deckNumber, String lastCardToString) {
        // Logic to handle player doubling down      //hier fehlt noch etwas, was auch wichtig zur Gewinnberechnung gebraucht wird. Implementierung hat nicht funktioniert.
        // Implement as per your game's rules
        return "action accepted";
    }

    private String handleSurrender(String playerName, int deckNumber, String lastCardToString) {
        // Logic to handle player surrendering       //hier fehlt noch etwas, was auch wichtig zur Gewinnberechnung gebraucht wird. Implementierung hat nicht funktioniert.
        // Implement as per your game's rules
        return "action accepted";
    }

    // Find player index by name
    private int findPlayerIndex(String playerName) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getName().equals(playerName)) {
                return i;
            }
        }
        return -1;
    }

    // Find player by name
    private PlayerInfo findPlayerByName(String playerName) {
        for (PlayerInfo player : players) {
            if (player.getName().equals(playerName)) {
                return player;
            }
        }
        return null;
    }

    // Check if all players have stood or busted
    private boolean allPlayersStandOrBust() {
        for (List<Card> hand : playerHands) {
            int handValue = calculateHandValue(hand);
            if (handValue <= 21 && !isBlackjack(hand)) {
                return false; // At least one player is still playing
            }
        }
        return true; // All players have stood or busted
    }

    // Evaluate hand value considering Ace as 1 or 11
    private int calculateHandValue(List<Card> hand) {
        int sum = 0;
        boolean hasAce = false;

        for (Card card : hand) {
            sum += card.getValueNumber();
            if (card.getValue() == Card.Value.ASS) {
                hasAce = true;
            }
        }

        // Adjust Ace value if the hand value is over 21 and an Ace is present
        if (hasAce && sum > 21) {
            sum -= 10; // Treat Ace as 1 instead of 11 without busting
        }

        return sum;
    }

    // Check if the hand is a blackjack (Ace + 10-value card)
    private boolean isBlackjack(List<Card> hand) {
        if (hand.size() != 2) {
            return false;
        }
        boolean hasAce = false;
        boolean hasFaceCard = false;

        for (Card card : hand) {
            if (card.getValue() == Card.Value.ASS) {
                hasAce = true;
            } else if (card.getValue() == Card.Value.BUBE || card.getValue() == Card.Value.DAME ||
                    card.getValue() == Card.Value.KOENIG) {
                hasFaceCard = true;
            }
        }

        return hasAce && hasFaceCard;
    }

    // Play dealer's turn
    private void playDealerTurn() {
        int dealerHandValue = calculateHandValue(dealerHand);
        while (dealerHandValue < 17) {
            Card newCard = dealCard("Dealer");
            dealerHand.add(newCard);
            dealerHandValue = calculateHandValue(dealerHand);
        }
    }

    // Determine game outcome and update player balances
    private void determineGameOutcome() {                           //Vermutlich wird payout hier nicht richtig berechnet. Dazu werden die player actions zu wenig (gar nicht) verwendet.
        int dealerHandValue = calculateHandValue(dealerHand);
        boolean dealerHasBlackjack = isBlackjack(dealerHand);

        for (int i = 0; i < players.size(); i++) {
            PlayerInfo player = players.get(i);
            List<Card> hand = playerHands.get(i);
            int playerHandValue = calculateHandValue(hand);
            boolean playerHasBlackjack = isBlackjack(hand);

            if (playerHasBlackjack && !dealerHasBlackjack) {
                int payout = player.getBet() * BLACKJACK_PAYOUT_MULTIPLIER;
                player.setBalance(player.getBalance() + payout);
                System.out.println("Player " + player.getName() + " wins with Blackjack!");
            } else if (dealerHasBlackjack && !playerHasBlackjack) {
                player.setBalance(player.getBalance() - player.getBet());
                System.out.println("Player " + player.getName() + " loses to dealer's Blackjack!");
            } else if (playerHandValue > 21) {
                player.setBalance(player.getBalance() - player.getBet());
                System.out.println("Player " + player.getName() + " busts!");
            } else if (dealerHandValue > 21) {
                player.setBalance(player.getBalance() + player.getBet());
                System.out.println("Player " + player.getName() + " wins, dealer busts!");
            } else if (playerHandValue > dealerHandValue) {
                player.setBalance(player.getBalance() + player.getBet());
                System.out.println("Player " + player.getName() + " wins!");
            } else if (playerHandValue < dealerHandValue) {
                player.setBalance(player.getBalance() - player.getBet());
                System.out.println("Player " + player.getName() + " loses!");
            } else {
                // Push, player and dealer have the same hand value
                System.out.println("Player " + player.getName() + " pushes, it's a tie!");
            }
        }
    }

    // Calculate and distribute payouts
    private void calculateAndDistributePayouts() {
        // Payouts are handled in determineGameOutcome method       //Durch progressive Implementierung mit ChatGPT anscheinend nutzlos geworden
    }

    // Reset player hands for the next round
    private void resetHands() {
        playerHands.clear();
        for (int i = 0; i < players.size(); i++) {
            playerHands.add(new ArrayList<>());
        }
        dealerHand.clear();
    }

    // PlayerInfo class (replace with your PlayerInfo class if different)
    private static class PlayerInfo {           // implementiert, da zuvor von ChatGPt die Methode player.getIP benutzt wurde, doch die Existenz dieser ist nicht sicher gestellt.
        private String name;
        private String ipAddress;
        private int port;
        private int balance;
        private int bet;

        public PlayerInfo(String name, String ipAddress, int port) {
            this.name = name;
            this.ipAddress = ipAddress;
            this.port = port;
            this.balance = 0; // Initialize balance
            this.bet = 0; // Initialize bet
        }

        public String getName() {
            return name;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        public int getPort() {
            return port;
        }

        public int getBalance() {
            return balance;
        }

        public void setBalance(int balance) {
            this.balance = balance;
        }

        public int getBet() {
            return bet;
        }

        public void setBet(int bet) {
            this.bet = bet;
        }
    }

    public static void main(String[] args) {
        Croupier croupier = new Croupier(1); // Example: 1 deck
        croupier.startUDPServer(8888); // Start UDP server on port 8888
    }
}
