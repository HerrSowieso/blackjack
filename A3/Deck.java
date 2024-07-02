import java.util.*;

public class Deck {
    private List<Card> cards;

    public Deck(int numberOfDecks) {
        cards = new ArrayList<>();
        String[] suits = {"Pik", "Kreuz", "Herz", "Karo"};
        for (int i = 0; i < numberOfDecks; i++) {
            for (String suit : suits) {
                for (int rank = 1; rank <= 13; rank++) {
                    cards.add(new Card(suit, rank));
                }
            }
        }
        shuffle();
    }

    public void shuffle() {
        Collections.shuffle(cards);
    }

    public Card dealCard() {
        return cards.remove(cards.size() - 1);
    }
}
