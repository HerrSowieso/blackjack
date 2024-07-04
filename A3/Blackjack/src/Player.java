import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Player {
    private String ipAddress;
    private int port;
    private String name;
    private static final ObjectMapper serializer = new ObjectMapper();

    public Player(String ipAddress, int port, String name) {
        this.ipAddress = ipAddress;
        this.port = port;
        this.name = name;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    public String getName() {
        return name;
    }

    // Method to send registration request to the Croupier
    public void registerWithCroupier(String croupierIp, int croupierPort) {
        sendUDPMessage(croupierIp, croupierPort, "registerPlayer " + ipAddress + " " + port + " " + name);
    }

    // Method to send bet request to the Croupier
    public void placeBetWithCroupier(String croupierIp, int croupierPort, int betAmount) {
        sendUDPMessage(croupierIp, croupierPort, "bet " + name + " " + betAmount);
    }

    // Method to receive a card and send acknowledgment
    public void receiveCard(Card card) {
        System.out.println("Received card: " + card.toString());
        sendUDPMessage("localhost", 9999, "player " + name + " received " + card.getDeck() + " " + card.toString());
    }

    // Generic method to send UDP message
    private void sendUDPMessage(String croupierIp, int croupierPort, String message) {
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] buffer = message.getBytes();
            InetAddress croupierAddress = InetAddress.getByName(croupierIp);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, croupierAddress, croupierPort);
            socket.send(packet);

            // Receive response
            byte[] responseBuffer = new byte[1024];
            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
            socket.receive(responsePacket);
            String response = new String(responsePacket.getData(), 0, responsePacket.getLength());
            System.out.println("Croupier response: " + response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Player player = new Player("192.168.1.1", 12345, "Alice");
        player.registerWithCroupier("localhost", 9999); // Example Croupier IP and port
        player.placeBetWithCroupier("localhost", 9999, 50); // Place a bet of 50 units

        // Example of receiving a card
        Card exampleCard = new Card(Card.Color.HERZ, Card.Value.ASS, 1);
        player.receiveCard(exampleCard); // Simulate receiving a card
    }
}
