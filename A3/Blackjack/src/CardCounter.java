//zum Testen von einem Kommilitonen kopiert

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CardCounter {
    private record ConnectionPoint(String ip, int port) {}
    private record DataPacket(ConnectionPoint sender, ConnectionPoint receiver, String request, String data) {}

    private int numberOfDecks;
    private int runningCount;
    private double trueCount;
    private static ConnectionPoint host;

    Map<String, GameStats> playerStats;

    public class GameStats {
        int win;
        int loss;
        int blackjack;

        public GameStats() {
            this.win = 0;
            this.loss = 0;
            this.blackjack = 0;
        }
    }
    public CardCounter(ConnectionPoint dealer, int hostPort) {
        playerStats = new HashMap<>();
        try (DatagramSocket socket = new DatagramSocket()){
            socket.connect(InetAddress.getByName("1.1.1.1"), 12345);
            String hostIp = socket.getLocalAddress().getHostAddress();
            socket.disconnect();

            host = new ConnectionPoint(hostIp, hostPort);

            socket.connect(InetAddress.getByName(dealer.ip), dealer.port);
            DataPacket message = new DataPacket(host, dealer, "numberOfDecks", "");

            send(message, socket, InetAddress.getByName(dealer.ip), dealer.port);
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.numberOfDecks = 0;
        this.runningCount = 0;
        this.trueCount = 0;

        System.out.println("Counting cards and give rec and stats requests on "+host.ip+":"+host.port);
        listen(12345);
    }

    public void updateCount(String card){
        switch (card) {
            case "2": case "3": case "4": case "5": case "6":
                runningCount += 1;
                break;
            case "10": case "J": case "Q": case "K": case "A":
                runningCount -= 1;
                break;
        }

        if (numberOfDecks > 0) {
            trueCount = (double) runningCount / numberOfDecks;
        }
    }

    public String suggestAction(int playerHand) {
        if (trueCount > 2) {
            if (playerHand >= 16) {
                return "stand";
            } else {
                return "hit";
            }
        } else if (playerHand >= 17) {
            return "stand";
        } else {
            return "hit";
        }
    }

    public void updateGameStats(String player, String result){
        if (!playerStats.containsKey(player)){
            playerStats.put(player, new GameStats());
        }
        GameStats stats = playerStats.get(player);
        switch (result) {
            case "win":
                stats.win += 1;
                break;
            case "loss":
                stats.loss += 1;
                break;
            case "blackjack":
                stats.blackjack += 1;
                break;
        }
    }

    public String provideGameStats(String player){
        GameStats stats = playerStats.get(player);
        return (stats == null) ? null :
                ("Statistics: Wins: " +  stats.win +
                        "\nLosses: " + stats.loss +
                        "\nBlackjacks: " + stats.blackjack);
    }

    public void listen(int port){
        DataPacket response = null;
        try (DatagramSocket socket = new DatagramSocket(port)) {
            byte[] buffer = new byte[1024];
            while (true){
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                DataPacket request = fromJson(new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8));
                System.out.println("Received: " + request);

                switch (request.request) {
                    case "numberOfDecks":
                        numberOfDecks = Integer.parseInt(request.data);
                        response = new DataPacket(host, request.sender, "get_decks", "success");
                        break;
                    case "update_game":
                        updateCount(request.data);
                        response = new DataPacket(host, request.sender, "update_game", "success");
                        break;
                    case "recommend_action":
                        response = new DataPacket(host, request.sender, "recommend_action", suggestAction(Integer.parseInt(request.data)));
                        break;
                    case "update_stats":
                        String[] data = request.data.split(":");
                        updateGameStats(data[0], data[1]);
                        response = new DataPacket(host, request.sender, "update_stats", "success");
                        break;
                    case "provide_stats":
                        response = new DataPacket(host, request.sender, "provide_stats", provideGameStats(request.data));
                        break;
                    default:
                        response = new DataPacket(host, request.sender, request.request, "Invalid request");
                        break;
                }
                send(response, socket ,packet.getAddress(), packet.getPort());
            }
        } catch (Exception e) {
            System.out.println("Something went wrong: " + e.getMessage());
            response = new DataPacket(host, host, "error", "Something went wrong while processing the request");
        }
    }

    public void send(DataPacket message, DatagramSocket socket, InetAddress address, int port){
        try {
            byte[] buffer = toJson(message).getBytes(StandardCharsets.UTF_8);
            DatagramPacket response = new DatagramPacket(buffer, buffer.length, address, port);
            socket.send(response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String toJson(DataPacket message){
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "";
        }
    }

    public DataPacket fromJson(String json){
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, DataPacket.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("Specify the dealer's IP address and port number (<ip>:<port>):");
            String dealerProcess = reader.readLine();
            String[] dealer = dealerProcess.split(":");
            System.out.println("Specify the port number for the card counter:");
            String port = reader.readLine();
            if (port.isBlank()) port = "54321";
            new CardCounter(new ConnectionPoint(dealer[0], Integer.parseInt(dealer[1])), Integer.parseInt(port));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}