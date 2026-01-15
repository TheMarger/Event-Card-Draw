// gamePanel.java
// Full file updated per request:
// - Rank mapping: A=1, 2..10 numeric, J=11, K=12, Q=13
// - No fallback number multiplier field (only mulNumberOdd / mulNumberEven used)
// - Settings UI adjusted to edit odd/even multipliers
// - Result card uses consistent half-screen sizing for both win/lose
// Paste this entire file over your existing gamePanel.java

import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.List;

public class gamePanel extends JPanel {

    // UI states
    private enum State { SETUP, PLAY, RESULT }
    private State currentState = State.SETUP;

    // Card/type model
    private enum Suit { HEARTS("♥"), DIAMONDS("♦"), CLUBS("♣"), SPADES("♠");
        final String glyph; Suit(String g){ glyph = g; }
        public String glyph(){ return glyph; }
    }
    private enum ColorType { RED, BLACK }
    private static final String[] RANKS = {"A","2","3","4","5","6","7","8","9","10","J","Q","K"};

    private static class Card {
        final String rank;
        final Suit suit;
        Card(String rank, Suit suit){ this.rank = rank; this.suit = suit; }
        boolean isFace(){ return "J".equals(rank) || "Q".equals(rank) || "K".equals(rank); }
        ColorType color(){ return (suit == Suit.HEARTS || suit == Suit.DIAMONDS) ? ColorType.RED : ColorType.BLACK; }
        @Override public String toString(){ return rank + suit.glyph(); }
    }

    private static class Deck {
        private final List<Card> cards = new ArrayList<>();
        Deck(){ resetToFull(); }
        void resetToFull(){
            cards.clear();
            for (Suit s : Suit.values()){
                for (String r : RANKS) cards.add(new Card(r,s));
            }
        }
        void clear(){ cards.clear(); }
        int size(){ return cards.size(); }
        List<Card> asList(){ return Collections.unmodifiableList(cards); }
        void removeSuit(Suit suit){ cards.removeIf(c -> c.suit == suit); }
        void addSuit(Suit suit){
            for (String r : RANKS){
                Card c = new Card(r,suit);
                boolean exists = false;
                for (Card cc : cards) if (cc.rank.equals(c.rank) && cc.suit == c.suit) { exists = true; break; }
                if (!exists) cards.add(c);
            }
        }
        void removeColor(ColorType color){ cards.removeIf(c -> c.color() == color); }
        void addColor(ColorType color){
            for (Suit s : Suit.values()){
                if ((color == ColorType.RED && (s==Suit.HEARTS || s==Suit.DIAMONDS)) ||
                    (color == ColorType.BLACK && (s==Suit.CLUBS || s==Suit.SPADES))){
                    addSuit(s);
                }
            }
        }
        void removeFaces(){ cards.removeIf(Card::isFace); }
        void addFaces(){
            for (Suit s : Suit.values()){
                for (String r : new String[]{"J","Q","K"}) {
                    boolean exists = false;
                    for (Card c : cards) if (c.rank.equals(r) && c.suit == s) { exists = true; break; }
                    if (!exists) cards.add(new Card(r,s));
                }
            }
        }
        Card drawRandom(Random rng){
            if (cards.isEmpty()) return null;
            int idx = rng.nextInt(cards.size());
            return cards.remove(idx);
        }
        boolean removeCard(String rank, Suit suit){
            return cards.removeIf(c -> c.rank.equals(rank) && c.suit == suit);
        }
        boolean contains(String rank, Suit suit){
            for (Card c : cards) if (c.rank.equals(rank) && c.suit == suit) return true;
            return false;
        }
        void addCard(String rank, Suit suit){
            boolean exists = false;
            for (Card c : cards) if (c.rank.equals(rank) && c.suit == suit) { exists = true; break; }
            if (!exists) cards.add(new Card(rank, suit));
        }
        void shuffle(Random rng){
            Collections.shuffle(cards, rng);
        }

        // counts
        int countSuit(Suit suit){
            int c = 0;
            for (Card card : cards) if (card.suit == suit) c++;
            return c;
        }
        int countColor(ColorType color){
            int c = 0;
            for (Card card : cards) if (card.color() == color) c++;
            return c;
        }
        int countRankSuit(String rank, Suit suit){
            int c = 0;
            for (Card card : cards) if (card.rank.equals(rank) && card.suit == suit) c++;
            return c;
        }
        int countRank(String rank){
            int c = 0;
            for (Card card : cards) if (card.rank.equals(rank)) c++;
            return c;
        }
        int countFaces(){
            int c = 0;
            for (Card card : cards) if (card.isFace()) c++;
            return c;
        }

        // --- rankValue mapping A=1, 2..10 numeric, J=11, K=12, Q=13 ---
        private int rankValue(String r){
            if (r == null) return -1;
            switch (r) {
                case "A": return 1;
                case "J": return 11;
                case "K": return 12;
                case "Q": return 13;
                default:
                    try { return Integer.parseInt(r); }
                    catch (NumberFormatException ex) { return -1; }
            }
        }

        void removeOdd(){
            cards.removeIf(c -> {
                int v = rankValue(c.rank);
                return v > 0 && (v % 2 == 1);
            });
        }
        void removeEven(){
            cards.removeIf(c -> {
                int v = rankValue(c.rank);
                return v > 0 && (v % 2 == 0);
            });
        }
        void addOdd(){
            for (Suit s : Suit.values()){
                for (String r : RANKS){
                    int v = rankValue(r);
                    if (v > 0 && (v % 2 == 1)) addCard(r, s);
                }
            }
        }
        void addEven(){
            for (Suit s : Suit.values()){
                for (String r : RANKS){
                    int v = rankValue(r);
                    if (v > 0 && (v % 2 == 0)) addCard(r, s);
                }
            }
        }
    }

    // Chosen bet & type
    private enum ChosenType { INDIVIDUAL, SUIT, COLOUR, NUMBER }
    private int betAmount = 0;
    private ChosenType chosenType = ChosenType.INDIVIDUAL;
    private String chosenRank = "A"; // reused for number bets as well ("A","2",..."K")
    private Suit chosenSuit = Suit.SPADES;
    private ColorType chosenColor = ColorType.RED;
    private final Deck deck = new Deck();
    private final Random rng = new Random();
    private Card lastDrawn = null;

    // draw history - records drawn cards in order
    private final List<Card> drawHistory = new ArrayList<>();

    // Multipliers (editable in settings)
    private double mulIndividual = 17.4;
    private double mulSuit = 2.17;
    private double mulColour = 1.46;
    // Number multipliers - separate for odd and even (no fallback single multiplier)
    private double mulNumberOdd = 4.61;
    private double mulNumberEven = 4.34;

    // Swing components
    private final CardComponent cardComponent = new CardComponent();
    private JLabel deckCountLabel = new JLabel();
    private JButton drawButton = new JButton("Draw");
    private JLabel topInfoLabel = new JLabel();
    private JPanel centerPanel = new JPanel(new BorderLayout());

    // Left-side remaining-cards list model & UI
    private DefaultListModel<String> deckListModel = new DefaultListModel<>();
    private JList<String> deckList = new JList<>(deckListModel);

    // For Probability tab
    private JEditorPane probabilityPane;

    // Theme
    private Color panelBg = new Color(28,34,40);
    private Color accent = new Color(45,160,200);

    // Base resolution for scaling calculations
    private static final int BASE_WIDTH = 1200;
    private static final int BASE_HEIGHT = 820;

    // base font sizes used when scaling
    private static final float BASE_TOPINFO_FONT = 14f;
    private static final float BASE_TITLE_FONT = 26f;
    private static final float BASE_CONTROLS_TITLE_FONT = 16f;
    private static final float BASE_LIST_FONT = 12f;
    private static final float BASE_BUTTON_FONT = 14f;
    private static final float BASE_SMALL_FONT = 11f;
    private static final float BASE_RESULT_TITLE_FONT = 36f;

    // keep a reference to the last result big card component to scale it when resizing
    private CardComponent lastResultCardComponent = null;

    // Right tabbed pane reference so top-bar button can toggle its visibility
    private JTabbedPane rightTabs = null;

    public gamePanel() {
        setLayout(new BorderLayout());
        setBackground(panelBg);
        updateGlobalFont(new Font("Segoe UI", Font.PLAIN, 14));
        setupGame();

        // listen to resize and apply scaling
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                applyScaling();
            }
        });
    }

    private void updateGlobalFont(Font f){
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while(keys.hasMoreElements()){
            Object k = keys.nextElement();
            Object v = UIManager.get(k);
            if (v instanceof Font) UIManager.put(k, f);
        }
    }

    /* ---------------------- Setup screen UI ---------------------- */
    public void setupGame(){
        removeAll();
        deck.resetToFull();
        drawHistory.clear();
        lastDrawn = null;
        currentState = State.SETUP;
        setUpSetupScreen();
        revalidate();
        repaint();
    }

    private void setUpSetupScreen(){
        this.removeAll();

        // Top toolbar (no balance)
        JPanel topBar = createTopBar();
        add(topBar, BorderLayout.NORTH);

        // Center: single clean setup card (no preview, no multipliers)
        JPanel main = new JPanel(new GridBagLayout());
        main.setOpaque(false);

        JPanel card = new RoundedPanel(new Color(40,46,54), 16);
        card.setLayout(new BorderLayout(12,12));
        card.setBorder(new EmptyBorder(18,18,18,18));

        // prefer a minimum size but allow growth
        card.setMinimumSize(new Dimension(400, 220));

        JLabel title = new JLabel("Place your bet");
        title.setFont(title.getFont().deriveFont(Font.BOLD, BASE_TITLE_FONT));
        title.setForeground(Color.WHITE);
        card.add(title, BorderLayout.NORTH);

        JPanel inputs = new JPanel(new GridBagLayout());
        inputs.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8,10,8,10);
        c.anchor = GridBagConstraints.WEST;

        // Bet
        c.gridx = 0; c.gridy = 0;
        JLabel betLbl = new JLabel("Bet (integer): ");
        betLbl.setForeground(Color.WHITE);
        inputs.add(betLbl, c);
        c.gridx = 1;
        JTextField betField = stylizeField(new JTextField("10",10));
        inputs.add(betField, c);

        // Type
        c.gridx = 0; c.gridy = 1;
        JLabel pickLbl = new JLabel("Pick type: ");
        pickLbl.setForeground(Color.WHITE);
        inputs.add(pickLbl, c);
        c.gridx = 1;
        String[] typeOptions = {"Individual card", "Suit", "Colour", "Number"};
        JComboBox<String> typeBox = stylizeCombo(new JComboBox<>(typeOptions));
        inputs.add(typeBox, c);

        // Subchoice area
        c.gridx = 0; c.gridy = 2; c.gridwidth = 2;
        JPanel subChoicePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        subChoicePanel.setOpaque(false);
        inputs.add(subChoicePanel, c);

        JComboBox<String> rankBox = stylizeCombo(new JComboBox<>(RANKS));
        JComboBox<String> suitBox = stylizeCombo(new JComboBox<>(new String[]{"HEARTS","DIAMONDS","CLUBS","SPADES"}));
        subChoicePanel.add(new JLabel("Rank:")); subChoicePanel.add(rankBox);
        subChoicePanel.add(new JLabel("Suit:")); subChoicePanel.add(suitBox);

        JComboBox<String> colorBox = stylizeCombo(new JComboBox<>(new String[]{"RED","BLACK"}));

        typeBox.addActionListener(e -> {
            String sel = (String) typeBox.getSelectedItem();
            subChoicePanel.removeAll();
            if ("Individual card".equals(sel)){
                subChoicePanel.add(new JLabel("Rank:")); subChoicePanel.add(rankBox);
                subChoicePanel.add(new JLabel("Suit:")); subChoicePanel.add(suitBox);
            } else if ("Suit".equals(sel)){
                subChoicePanel.add(new JLabel("Suit:")); subChoicePanel.add(suitBox);
            } else if ("Colour".equals(sel)){
                subChoicePanel.add(new JLabel("Colour:")); subChoicePanel.add(colorBox);
            } else {
                // Number selection: rank only (any suit)
                JLabel numInfo = new JLabel("Number = rank");
                numInfo.setForeground(Color.LIGHT_GRAY);
                subChoicePanel.add(new JLabel("Rank:")); subChoicePanel.add(rankBox);
                subChoicePanel.add(numInfo);
            }
            subChoicePanel.revalidate();
            subChoicePanel.repaint();
        });

        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setOpaque(false);
        JButton nextBtn = stylizeButton("Next →");
        JButton resetBtn = stylizeButtonSmall("Reset Deck");
        buttons.add(resetBtn); buttons.add(nextBtn);

        resetBtn.addActionListener(e -> {
            deck.resetToFull();
            drawHistory.clear();
            updateDeckList();
            updateDeckStatus();
            JOptionPane.showMessageDialog(this, "Deck reset to full 52 cards.", "Deck Reset", JOptionPane.INFORMATION_MESSAGE);
        });

        nextBtn.addActionListener(e -> {
            try {
                int bet = Integer.parseInt(betField.getText().trim());
                if (bet <= 0) throw new NumberFormatException();
                betAmount = bet;
                String sel = (String) typeBox.getSelectedItem();
                if ("Individual card".equals(sel)){
                    chosenType = ChosenType.INDIVIDUAL;
                    chosenRank = (String) rankBox.getSelectedItem();
                    chosenSuit = Suit.valueOf((String) suitBox.getSelectedItem());
                } else if ("Suit".equals(sel)){
                    chosenType = ChosenType.SUIT;
                    chosenSuit = Suit.valueOf((String) suitBox.getSelectedItem());
                } else if ("Colour".equals(sel)){
                    chosenType = ChosenType.COLOUR;
                    chosenColor = ColorType.valueOf((String) colorBox.getSelectedItem());
                } else {
                    chosenType = ChosenType.NUMBER;
                    chosenRank = (String) rankBox.getSelectedItem();
                }
                enterPlayState();
            } catch (NumberFormatException ex){
                JOptionPane.showMessageDialog(this, "Please enter a valid positive integer bet.", "Invalid input", JOptionPane.ERROR_MESSAGE);
            }
        });

        card.add(inputs, BorderLayout.CENTER);
        card.add(buttons, BorderLayout.SOUTH);

        main.add(card);
        add(main, BorderLayout.CENTER);

        revalidate();
        repaint();
    }

    /* ---------------------- Play screen UI ---------------------- */
    private void enterPlayState(){
        currentState = State.PLAY;
        removeAll();

        // Top toolbar
        JPanel topBar = createTopBar();
        add(topBar, BorderLayout.NORTH);

        // center: large card area (live)
        centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);

        // ensure the main card component has a reasonable preferred size so it displays
        cardComponent.setPreferredSize(new Dimension(320, 440));

        JPanel centerWrapper = new RoundedPanel(new Color(40,46,54), 14);
        centerWrapper.setOpaque(false);
        centerWrapper.setBorder(new EmptyBorder(20,20,20,20));
        centerWrapper.setLayout(new BorderLayout());
        centerWrapper.add(cardComponent, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 8));
        bottomBar.setOpaque(false);
        drawButton = stylizeButton("Draw");
        JButton endButton = stylizeButton("End Game");
        JButton shuffleBtn = stylizeButton("Shuffle");
        bottomBar.add(drawButton); bottomBar.add(endButton); bottomBar.add(shuffleBtn);
        centerWrapper.add(bottomBar, BorderLayout.SOUTH);

        centerPanel.add(centerWrapper, BorderLayout.CENTER);

        // Right: tabs
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBorder(new EmptyBorder(8,8,8,8));
        Color rightBg = new Color(34,38,44); // darker background requested
        tabs.setBackground(rightBg);
        tabs.setForeground(Color.WHITE);

        // Deck controls tab
        JPanel deckTab = new JPanel();
        deckTab.setOpaque(true);
        deckTab.setBackground(rightBg);
        deckTab.setLayout(new BoxLayout(deckTab, BoxLayout.Y_AXIS));
        deckTab.setBorder(new EmptyBorder(10,10,10,10));

        JLabel controlsTitle = new JLabel("Deck Controls");
        controlsTitle.setForeground(Color.WHITE);
        controlsTitle.setFont(controlsTitle.getFont().deriveFont(Font.BOLD, BASE_CONTROLS_TITLE_FONT));
        deckTab.add(controlsTitle);
        deckTab.add(Box.createVerticalStrut(8));

        deckCountLabel = new JLabel("Deck: " + deck.size() + " cards");
        deckCountLabel.setForeground(Color.WHITE);
        deckTab.add(deckCountLabel);
        deckTab.add(Box.createVerticalStrut(10));

        // remove/add suit
        JLabel lbl1 = new JLabel("Remove suit:");
        lbl1.setForeground(Color.WHITE);
        deckTab.add(lbl1);
        deckTab.add(Box.createVerticalStrut(6));
        JPanel removeSuitPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        removeSuitPanel.setOpaque(false);
        JComboBox<String> removeSuitBox = stylizeCombo(new JComboBox<>(new String[]{"HEARTS","DIAMONDS","CLUBS","SPADES"}));
        JButton removeSuitBtn = stylizeButtonSmall("Remove");
        removeSuitPanel.add(removeSuitBox); removeSuitPanel.add(removeSuitBtn);
        deckTab.add(removeSuitPanel);

        removeSuitBtn.addActionListener(e -> {
            Suit s = Suit.valueOf((String) removeSuitBox.getSelectedItem());
            deck.removeSuit(s);
            updateDeckStatus();
        });

        // add suit
        JLabel lbl2 = new JLabel("Add suit:");
        lbl2.setForeground(Color.WHITE);
        deckTab.add(lbl2);
        deckTab.add(Box.createVerticalStrut(6));
        JPanel addSuitPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addSuitPanel.setOpaque(false);
        JComboBox<String> addSuitBox = stylizeCombo(new JComboBox<>(new String[]{"HEARTS","DIAMONDS","CLUBS","SPADES"}));
        JButton addSuitBtn = stylizeButtonSmall("Add");
        addSuitPanel.add(addSuitBox); addSuitPanel.add(addSuitBtn);
        deckTab.add(addSuitPanel);
        addSuitBtn.addActionListener(e -> {
            Suit s = Suit.valueOf((String) addSuitBox.getSelectedItem());
            deck.addSuit(s);
            updateDeckStatus();
        });

        deckTab.add(Box.createVerticalStrut(8));
        // Remove color
        JLabel lbl3 = new JLabel("Remove colour:");
        lbl3.setForeground(Color.WHITE);
        deckTab.add(lbl3);
        deckTab.add(Box.createVerticalStrut(6));
        JPanel removeColPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        removeColPanel.setOpaque(false);
        JComboBox<String> removeColBox = stylizeCombo(new JComboBox<>(new String[]{"RED","BLACK"}));
        JButton removeColBtn = stylizeButtonSmall("Remove");
        removeColPanel.add(removeColBox); removeColPanel.add(removeColBtn);
        deckTab.add(removeColPanel);
        removeColBtn.addActionListener(e -> {
            ColorType color = ColorType.valueOf((String) removeColBox.getSelectedItem());
            deck.removeColor(color);
            updateDeckStatus();
        });

        // Add color
        JLabel lbl4 = new JLabel("Add colour:");
        lbl4.setForeground(Color.WHITE);
        deckTab.add(lbl4);
        deckTab.add(Box.createVerticalStrut(6));
        JPanel addColPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addColPanel.setOpaque(false);
        JComboBox<String> addColBox = stylizeCombo(new JComboBox<>(new String[]{"RED","BLACK"}));
        JButton addColBtn = stylizeButtonSmall("Add");
        addColPanel.add(addColBox); addColPanel.add(addColBtn);
        deckTab.add(addColPanel);
        addColBtn.addActionListener(e -> {
            ColorType color = ColorType.valueOf((String) addColBox.getSelectedItem());
            deck.addColor(color);
            updateDeckStatus();
        });

        deckTab.add(Box.createVerticalStrut(8));
        JLabel lbl5 = new JLabel("Face cards (J,Q,K):");
        lbl5.setForeground(Color.WHITE);
        deckTab.add(lbl5);
        deckTab.add(Box.createVerticalStrut(6));
        JPanel facesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        facesPanel.setOpaque(false);
        JButton removeFacesBtn = stylizeButtonSmall("Remove Faces (J,Q,K)");
        JButton addFacesBtn = stylizeButtonSmall("Add Faces");
        facesPanel.add(removeFacesBtn); facesPanel.add(addFacesBtn);
        deckTab.add(facesPanel);
        removeFacesBtn.addActionListener(e -> { deck.removeFaces(); updateDeckStatus(); });
        addFacesBtn.addActionListener(e -> { deck.addFaces(); updateDeckStatus(); });

        deckTab.add(Box.createVerticalStrut(10));
        JLabel lbl6 = new JLabel("Remove/Add specific card:");
        lbl6.setForeground(Color.WHITE);
        deckTab.add(lbl6);
        JComboBox<String> specificRank = stylizeCombo(new JComboBox<>(RANKS));
        JComboBox<String> specificSuit = stylizeCombo(new JComboBox<>(new String[]{"HEARTS","DIAMONDS","CLUBS","SPADES"}));
        JPanel specificPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        specificPanel.setOpaque(false);
        JButton removeSpecific = stylizeButtonSmall("Remove");
        JButton addSpecific = stylizeButtonSmall("Add");
        specificPanel.add(specificRank); specificPanel.add(specificSuit);
        specificPanel.add(removeSpecific); specificPanel.add(addSpecific);
        deckTab.add(specificPanel);

        removeSpecific.addActionListener(e -> {
            String r = (String) specificRank.getSelectedItem();
            Suit s = Suit.valueOf((String) specificSuit.getSelectedItem());
            boolean changed = deck.removeCard(r,s);
            updateDeckStatus();
            JOptionPane.showMessageDialog(this, changed ? "Card removed." : "That card was not in the deck.", "Specific Remove", JOptionPane.INFORMATION_MESSAGE);
        });
        addSpecific.addActionListener(e -> {
            String r = (String) specificRank.getSelectedItem();
            Suit s = Suit.valueOf((String) specificSuit.getSelectedItem());
            if (!deck.contains(r,s)){
                deck.addCard(r,s);
                updateDeckStatus();
                JOptionPane.showMessageDialog(this, "Card added.", "Specific Add", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "That card already exists in the deck.", "Specific Add", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        deckTab.add(Box.createVerticalStrut(12));
        // NEW: Odd/Even controls (note mapping shown per your request: J=11, K=12, Q=13)
        JLabel oddEvenLbl = new JLabel("Odd / Even numbers (A=1; J=11, K=12, Q=13):");
        oddEvenLbl.setForeground(Color.WHITE);
        deckTab.add(oddEvenLbl);
        deckTab.add(Box.createVerticalStrut(6));
        JPanel oddEvenPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        oddEvenPanel.setOpaque(false);
        JButton removeOddBtn = stylizeButtonSmall("Remove Odd");
        JButton addOddBtn = stylizeButtonSmall("Add Odd");
        JButton removeEvenBtn = stylizeButtonSmall("Remove Even");
        JButton addEvenBtn = stylizeButtonSmall("Add Even");
        oddEvenPanel.add(removeOddBtn); oddEvenPanel.add(addOddBtn); oddEvenPanel.add(removeEvenBtn); oddEvenPanel.add(addEvenBtn);
        deckTab.add(oddEvenPanel);

        removeOddBtn.addActionListener(e -> { deck.removeOdd(); updateDeckStatus(); });
        addOddBtn.addActionListener(e -> { deck.addOdd(); updateDeckStatus(); });
        removeEvenBtn.addActionListener(e -> { deck.removeEven(); updateDeckStatus(); });
        addEvenBtn.addActionListener(e -> { deck.addEven(); updateDeckStatus(); });

        deckTab.add(Box.createVerticalStrut(12));
        JButton resetDeckBtn = stylizeButtonSmall("Reset to Full Deck");
        deckTab.add(resetDeckBtn);
        resetDeckBtn.addActionListener(e -> { deck.resetToFull(); drawHistory.clear(); updateDeckStatus(); updateDeckList(); });

        tabs.addTab("Deck", deckTab);

        /// ---------------- SETTINGS TAB (fixed layout) ----------------
        JPanel settingsTab = new JPanel(new GridBagLayout());
        settingsTab.setBackground(rightBg);
        settingsTab.setBorder(new EmptyBorder(12,12,12,12));

        GridBagConstraints s = new GridBagConstraints();
        s.insets = new Insets(8,6,8,6);
        s.anchor = GridBagConstraints.WEST;
        s.fill = GridBagConstraints.HORIZONTAL;
        s.weightx = 1.0;

        JLabel settingsTitle = new JLabel("Multipliers");
        settingsTitle.setFont(settingsTitle.getFont().deriveFont(Font.BOLD, BASE_CONTROLS_TITLE_FONT));
        settingsTitle.setForeground(Color.WHITE);
        s.gridx = 0; s.gridy = 0; s.gridwidth = 2;
        settingsTab.add(settingsTitle, s);

        s.gridwidth = 1;

        JLabel lblInd = new JLabel("Individual card");
        lblInd.setForeground(Color.WHITE);
        s.gridx = 0; s.gridy = 1;
        settingsTab.add(lblInd, s);

        JTextField fieldInd = makeMulField(mulIndividual);
        s.gridx = 1;
        settingsTab.add(fieldInd, s);

        JLabel lblSuit = new JLabel("Suit");
        lblSuit.setForeground(Color.WHITE);
        s.gridx = 0; s.gridy = 2;
        settingsTab.add(lblSuit, s);

        JTextField fieldSuit = makeMulField(mulSuit);
        s.gridx = 1;
        settingsTab.add(fieldSuit, s);

        JLabel lblCol = new JLabel("Colour");
        lblCol.setForeground(Color.WHITE);
        s.gridx = 0; s.gridy = 3;
        settingsTab.add(lblCol, s);

        JTextField fieldCol = makeMulField(mulColour);
        s.gridx = 1;
        settingsTab.add(fieldCol, s);

        // number multipliers split odd/even
        JLabel lblNumOdd = new JLabel("Number (odd)");
        lblNumOdd.setForeground(Color.WHITE);
        s.gridx = 0; s.gridy = 4;
        settingsTab.add(lblNumOdd, s);

        JTextField fieldNumOdd = makeMulField(mulNumberOdd);
        s.gridx = 1;
        settingsTab.add(fieldNumOdd, s);

        JLabel lblNumEven = new JLabel("Number (even)");
        lblNumEven.setForeground(Color.WHITE);
        s.gridx = 0; s.gridy = 5;
        settingsTab.add(lblNumEven, s);

        JTextField fieldNumEven = makeMulField(mulNumberEven);
        s.gridx = 1;
        settingsTab.add(fieldNumEven, s);

        // current values row
        JLabel currentLbl = new JLabel("Current values update when applied");
        currentLbl.setForeground(Color.LIGHT_GRAY);
        currentLbl.setFont(currentLbl.getFont().deriveFont(BASE_SMALL_FONT));
        s.gridx = 0; s.gridy = 6; s.gridwidth = 2;
        settingsTab.add(currentLbl, s);

        s.gridwidth = 1;

        JButton applyMulBtn = stylizeButton("Apply Multipliers");
        s.gridx = 0; s.gridy = 7; s.gridwidth = 2;
        settingsTab.add(applyMulBtn, s);

        applyMulBtn.addActionListener(e -> {
            try {
                mulIndividual = Double.parseDouble(fieldInd.getText().trim());
                mulSuit       = Double.parseDouble(fieldSuit.getText().trim());
                mulColour     = Double.parseDouble(fieldCol.getText().trim());
                mulNumberOdd  = Double.parseDouble(fieldNumOdd.getText().trim());
                mulNumberEven = Double.parseDouble(fieldNumEven.getText().trim());
                JOptionPane.showMessageDialog(this,
                    "Multipliers updated successfully.",
                    "Updated",
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex){
                JOptionPane.showMessageDialog(this,
                    "Enter valid numeric multiplier values.",
                    "Invalid Input",
                    JOptionPane.ERROR_MESSAGE);
            }
        });

        tabs.addTab("Settings", settingsTab);

        /// ---------------- PROBABILITY TAB (NEW) ----------------
        JPanel probTab = new JPanel(new BorderLayout());
        probTab.setBackground(rightBg);
        probTab.setBorder(new EmptyBorder(12,12,12,12));

        JLabel probTitle = new JLabel("Probability");
        probTitle.setForeground(Color.WHITE);
        probTitle.setFont(probTitle.getFont().deriveFont(Font.BOLD, BASE_CONTROLS_TITLE_FONT));
        probTab.add(probTitle, BorderLayout.NORTH);

        probabilityPane = new JEditorPane();
        probabilityPane.setContentType("text/html");
        probabilityPane.setEditable(false);
        probabilityPane.setBackground(new Color(34,38,44));
        probabilityPane.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));

        JScrollPane probScroll = new JScrollPane(probabilityPane);
        probScroll.setBorder(BorderFactory.createLineBorder(new Color(60,60,60)));
        probTab.add(probScroll, BorderLayout.CENTER);

        // quick action row (recalculate)
        JPanel probActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        probActions.setOpaque(false);
        JButton recalcBtn = stylizeButtonSmall("Recalculate");
        probActions.add(recalcBtn);
        probTab.add(probActions, BorderLayout.SOUTH);

        recalcBtn.addActionListener(e -> updateProbabilityPane());

        tabs.addTab("Probability", probTab);

        // Left: Remaining cards panel (live-updating) - only here
        JPanel leftInfo = new RoundedPanel(new Color(40,46,54), 12);
        leftInfo.setOpaque(false);
        leftInfo.setLayout(new BorderLayout());
        leftInfo.setBorder(new EmptyBorder(12,12,12,12));

        JLabel leftTitle = new JLabel("Remaining Cards");
        leftTitle.setForeground(Color.WHITE);
        leftTitle.setBorder(new EmptyBorder(6,6,6,6));
        leftInfo.add(leftTitle, BorderLayout.NORTH);

        // configure deckList appearance
        deckList.setForeground(Color.WHITE);
        deckList.setBackground(new Color(30,34,40));
        deckList.setSelectionBackground(new Color(70,80,95));
        deckList.setFont(deckList.getFont().deriveFont(BASE_LIST_FONT));

        JScrollPane leftScroll = new JScrollPane(deckList);
        leftScroll.setBorder(BorderFactory.createLineBorder(new Color(60,60,60)));
        leftInfo.add(leftScroll, BorderLayout.CENTER);

        // Layout main content
        JPanel content = new JPanel(new BorderLayout(12,12));
        content.setOpaque(false);
        content.add(centerPanel, BorderLayout.CENTER);
        content.add(tabs, BorderLayout.EAST);    // smaller & darker
        content.add(leftInfo, BorderLayout.WEST);

        add(content, BorderLayout.CENTER);

        // store reference so top-bar toggle can show/hide it
        rightTabs = tabs;
        // start hidden (user requested it to appear only when clicking Edit Deck)
        rightTabs.setVisible(false);
        rightTabs.setPreferredSize(new Dimension(0, rightTabs.getPreferredSize().height));

        // Hook up actions
        drawButton.addActionListener(e -> {
            Card c = deck.drawRandom(rng);
            lastDrawn = c;
            if (c != null) drawHistory.add(c);
            updateDeckStatus();
            updateDeckList();
            cardComponent.setCard(c);
            cardComponent.repaint();
            if (deck.size() == 0) drawButton.setEnabled(false);
            if (c == null) JOptionPane.showMessageDialog(this, "Deck is empty. Reset or add cards.", "Empty Deck", JOptionPane.WARNING_MESSAGE);
        });

        shuffleBtn.addActionListener(ev -> {
            deck.shuffle(rng);
            updateDeckStatus();
            updateDeckList();
            JOptionPane.showMessageDialog(this, "Deck shuffled.", "Shuffle", JOptionPane.INFORMATION_MESSAGE);
        });

        endButton.addActionListener(e -> enterResultState());

        // ensure all labels inside these tabs are white (extra safety)
        setLabelsWhite(deckTab);
        setLabelsWhite(settingsTab);
        setLabelsWhite(probTab);

        updateDeckStatus();
        updateDeckList();
        cardComponent.setCard(lastDrawn);

        // initial probability render
        updateProbabilityPane();

        // apply scaling after layout
        SwingUtilities.invokeLater(this::applyScaling);

        revalidate();
        repaint();
    }

    // return the most recent matching card among the last up-to-3 draws, or null if none
    private Card getMostRecentHit() {
        int n = drawHistory.size();
        int start = Math.max(0, n - 3);
        for (int i = n - 1; i >= start; i--) {
            Card c = drawHistory.get(i);
            if (matchesChoice(c)) return c;
        }
        return null;
    }

    // Update the left list with all remaining cards
    private void updateDeckList(){
        deckListModel.clear();
        for (Card c : deck.asList()){
            deckListModel.addElement(c.toString());
        }
    }

    // helper: walk a container and set JLabel foreground to white (ensures contrast)
    private void setLabelsWhite(Container c){
        for (Component comp : c.getComponents()){
            if (comp instanceof JLabel) ((JLabel) comp).setForeground(Color.WHITE);
            if (comp instanceof Container) setLabelsWhite((Container) comp);
        }
    }

    private String chosenSummary(){
        switch (chosenType){
            case INDIVIDUAL: return String.format("Chosen: %s of %s (Individual).", chosenRank, chosenSuit.name());
            case SUIT: return String.format("Chosen suit: %s.", chosenSuit.name());
            case COLOUR: return String.format("Chosen colour: %s.", chosenColor.name());
            default: return String.format("Chosen rank: %s (Number).", chosenRank);
        }
    }

    /* ---------------------- Result screen UI ---------------------- */
    private void enterResultState(){
        currentState = State.RESULT;
        removeAll();

        JPanel topBar = createTopBar();
        add(topBar, BorderLayout.NORTH);

        JPanel container = new JPanel(new BorderLayout(18,18));
        container.setOpaque(false);
        container.setBorder(new EmptyBorder(18,18,18,18));

        // choose which card to show large: prefer most recent hit among last 3, else lastDrawn
        Card hitCard = getMostRecentHit();
        Card displayCard = (hitCard != null) ? hitCard : lastDrawn;

        final CardComponent bigCardComp = new CardComponent();
        bigCardComp.setCard(displayCard);
        // default preferred (will adjust later)
        bigCardComp.setPreferredSize(new Dimension(320, 440));

        // determine win: true if any of last 3 matched (hitCard != null)
        boolean won = (hitCard != null);

        // build hit list and last-3 summary for labels
        StringBuilder hitList = new StringBuilder();
        int n = drawHistory.size();
        int start = Math.max(0, n - 3);
        for (int i = start; i < n; i++){
            Card chk = drawHistory.get(i);
            if (matchesChoice(chk)){
                if (hitList.length() > 0) hitList.append(", ");
                hitList.append(chk.toString());
            }
        }

        String hitInfo;
        if (n == 0){
            hitInfo = "No cards were drawn.";
        } else {
            StringBuilder tmp = new StringBuilder();
            tmp.append(String.format("Last %d draw(s): ", Math.min(3, n)));
            for (int i = Math.max(0, n-3); i < n; i++){
                tmp.append(drawHistory.get(i).toString());
                if (i < n-1) tmp.append(", ");
            }
            hitInfo = tmp.toString();
        }

        String title = won ? "YOU WON!" : "YOU LOST";
        Color titleColor = won ? new Color(16,140,50) : new Color(200,60,60);

        JPanel right = new RoundedPanel(new Color(40,46,54), 14);
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBorder(new EmptyBorder(16,16,16,16));

        JLabel resultTitle = new JLabel(title);
        resultTitle.setFont(resultTitle.getFont().deriveFont(Font.BOLD, BASE_RESULT_TITLE_FONT));
        resultTitle.setForeground(titleColor);
        right.add(resultTitle);
        right.add(Box.createVerticalStrut(14));

        JLabel chosen = new JLabel("Your bet: $" + betAmount + " on " + chosenSummary());
        chosen.setForeground(Color.WHITE);
        right.add(chosen);
        right.add(Box.createVerticalStrut(8));

        double multiplier = getMultiplierForChosen();
        JLabel potential = new JLabel(String.format("Potential payout: $%.2f (bet × %.2f)", betAmount * multiplier, multiplier));
        potential.setForeground(Color.WHITE);
        right.add(potential);
        right.add(Box.createVerticalStrut(8));

        int net = won ? (int) Math.round(betAmount * multiplier) : -betAmount;
        JLabel netLbl = new JLabel((net >= 0 ? "Return: $" : "Lost: $") + Math.abs(net));
        netLbl.setForeground(net >= 0 ? new Color(18,150,31) : new Color(200,60,60));
        netLbl.setFont(netLbl.getFont().deriveFont(Font.BOLD, 18f));
        right.add(netLbl);
        right.add(Box.createVerticalStrut(16));

        JLabel displayedLbl = new JLabel("<html>Displayed card: " + (displayCard == null ? "None" : displayCard.toString()) +
                                    "<br/><small style='color:#CCCCCC;'>" + hitInfo + "</small></html>");
        displayedLbl.setForeground(Color.WHITE);
        right.add(displayedLbl);
        right.add(Box.createVerticalStrut(8));

        if (hitList.length() > 0){
            JLabel hitsLbl = new JLabel("Hit(s) among last 3: " + hitList.toString());
            hitsLbl.setForeground(new Color(200,200,120));
            right.add(hitsLbl);
            right.add(Box.createVerticalStrut(8));
        }

        JButton restart = stylizeButton("Restart Game");
        restart.addActionListener(e -> { deck.resetToFull(); drawHistory.clear(); lastDrawn = null; setupGame(); });
        right.add(restart);
        right.add(Box.createVerticalStrut(8));
        JButton playAgain = stylizeButton("Play Again (keep deck & choice)");
        playAgain.addActionListener(e -> enterPlayState());
        right.add(playAgain);

        // left wrapper to center the big card nicely
        JPanel leftWrapper = new JPanel(new GridBagLayout());
        leftWrapper.setOpaque(false);
        leftWrapper.add(bigCardComp);

        // Add card in CENTER so it gets maximum space; info on EAST
        container.add(leftWrapper, BorderLayout.CENTER);
        container.add(right, BorderLayout.EAST);

        add(container, BorderLayout.CENTER);
        revalidate();
        repaint();

        // IMPORTANT: adjust bigCardComp size after the panel has layouted so we can use actual panel size.
        SwingUtilities.invokeLater(() -> {
            int panelW = getWidth();
            int panelH = getHeight();
            if (panelW <= 0) panelW = BASE_WIDTH;
            if (panelH <= 0) panelH = BASE_HEIGHT;

            // target width ~ half of content area (leave room for right info)
            int availableForCard = Math.max(200, (int)(panelW * 0.55)); // 55% of overall width for card area
            int targetW = Math.max(360, availableForCard / 1);
            int targetH = (int) (targetW / (380.0/520.0)); // preserve aspect ratio
            // clamp not to exceed panel height minus some margins
            int maxH = panelH - 180;
            if (targetH > maxH) {
                targetH = maxH;
                targetW = (int)(targetH * (380.0/520.0));
            }

            bigCardComp.setPreferredSize(new Dimension(targetW, targetH));
            bigCardComp.revalidate();
            bigCardComp.repaint();
        });

        // play result sound (attempt classpath then filesystem)
        playResultSound(won);
    }

    /* ---------------------- Helpers / logic ---------------------- */
    private boolean evaluateWin(Card drawn){
        if (drawn == null) return false;
        switch (chosenType){
            case INDIVIDUAL:
                return drawn.rank.equals(chosenRank) && drawn.suit == chosenSuit;
            case SUIT:
                return drawn.suit == chosenSuit;
            case COLOUR:
                return drawn.color() == chosenColor;
            default: // NUMBER
                return drawn.rank.equals(chosenRank);
        }
    }

    // check whether a single card would be a winning hit for the current selection
    private boolean matchesChoice(Card c){
        if (c == null) return false;
        switch (chosenType){
            case INDIVIDUAL:
                return c.rank.equals(chosenRank) && c.suit == chosenSuit;
            case SUIT:
                return c.suit == chosenSuit;
            case COLOUR:
                return c.color() == chosenColor;
            case NUMBER:
                return c.rank.equals(chosenRank);
            default:
                return false;
        }
    }

    // helper to convert rank string to numeric value (A=1, 2..10 numeric, J=11, K=12, Q=13)
    private int rankValue(String r){
        if (r == null) return -1;
        switch (r) {
            case "A": return 1;
            case "J": return 11;
            case "K": return 12;
            case "Q": return 13;
            default:
                try { return Integer.parseInt(r); }
                catch (NumberFormatException ex) { return -1; }
        }
    }

    /**
     * Play a short sound on win/loss. Tries several classpath and filesystem locations.
     * Runs playback on a background thread so it doesn't block the EDT.
     */
    private void playResultSound(boolean won){
        final String[][] candidates = new String[][]{
            // win sounds
            {"/res/hehehe-ha.wav","res/hehehe-ha.wav","/res/hehehe-ha.mp3","res/hehehe-ha.mp3"},
            // lose sounds
            {"/res/clash-royale-king-cry.wav","res/clash-royale-king-cry.wav","/res/clash-royale-king-cry.mp3","res/clash-royale-king-cry.mp3"}
        };
        final String[] chosenList = won ? candidates[0] : candidates[1];

        new Thread(() -> {
            AudioInputStream ais = null;
            for (String path : chosenList){
                try {
                    // try classpath first
                    URL url = getClass().getResource(path);
                    if (url != null){
                        ais = AudioSystem.getAudioInputStream(url);
                        break;
                    }
                } catch (Exception ignored) {}

                try {
                    File f = new File(path.startsWith("/") ? path.substring(1) : path);
                    if (f.exists()){
                        ais = AudioSystem.getAudioInputStream(f);
                        break;
                    }
                } catch (Exception ignored) {}
            }

            if (ais == null) return; // no sound available

            try (AudioInputStream stream = ais){
                Clip clip = AudioSystem.getClip();
                clip.open(stream);
                clip.start();
            } catch (Exception ex){
                // silently ignore playback errors to avoid crashing the UI
                System.err.println("Failed to play result sound: " + ex.getMessage());
            }
        }, "ResultSoundPlayer").start();
    }

    // Return the correct multiplier for the player's current choice
    private double getMultiplierForChosen(){
        switch (chosenType){
            case INDIVIDUAL: return mulIndividual;
            case SUIT: return mulSuit;
            case COLOUR: return mulColour;
            case NUMBER: {
                int v = rankValue(chosenRank);
                // no fallback multiplier field: if rank invalid, treat as odd (safe default)
                if (v <= 0) return mulNumberOdd;
                return (v % 2 == 1) ? mulNumberOdd : mulNumberEven;
            }
            default: return mulNumberOdd;
        }
    }

    private JPanel createTopBar(){
        JPanel top = new JPanel(new BorderLayout(12,0));
        top.setOpaque(false);
        top.setBorder(new EmptyBorder(10,12,10,12));

        JLabel logo = new JLabel("<html><span style='color:#fff;font-weight:bold;font-size:16px;'>Card<span style='color:#2BC0E4;'>Draw</span></span></html>");
        top.add(logo, BorderLayout.WEST);

        topInfoLabel = new JLabel();
        topInfoLabel.setForeground(Color.WHITE);
        updateTopInfo();
        top.add(topInfoLabel, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));
        right.setOpaque(false);
        JButton reset = stylizeButtonSmall("Reset Deck");
        reset.addActionListener(e -> { deck.resetToFull(); drawHistory.clear(); updateDeckStatus(); updateDeckList(); });
        right.add(reset);
        JButton help = stylizeButtonSmall("Help");
        help.addActionListener(a -> JOptionPane.showMessageDialog(this,
                "How to play: 1) Enter bet and choose type on the first screen.\r\n"
                + "2) Click Next and use Draw to draw a random card.\r\n"
                + "3) Use Deck controls to alter the deck.\r\n"
                + "4) End game to see results.\r\n"
                + "Multipliers are in Settings (inside the game).",
                "Help", JOptionPane.INFORMATION_MESSAGE));
        right.add(help);

        // NEW: Edit Deck toggle button (top-right)
        JButton editToggle = stylizeButtonSmall("Edit Deck");
        editToggle.addActionListener(e -> toggleRightTabs());
        right.add(editToggle);

        top.add(right, BorderLayout.EAST);
        return top;
    }

    private void updateTopInfo(){
        String txt = String.format("<html><div style='color:white;padding:6px;'>Bet: $%d &nbsp;&nbsp; | &nbsp;&nbsp; Choice: %s &nbsp;&nbsp; | &nbsp;&nbsp; Deck size: %d</div></html>",
                betAmount, chosenSummary(), deck.size());
        if (topInfoLabel != null) topInfoLabel.setText(txt);
    }

    /* ---------------------- Probability content builder ---------------------- */

    // greatest common divisor helper for fraction reduction
    private int gcd(int a, int b){
        a = Math.abs(a); b = Math.abs(b);
        if (a == 0) return b;
        if (b == 0) return a;
        while (b != 0){
            int t = a % b;
            a = b;
            b = t;
        }
        return a;
    }

    // build HTML content for probability pane
    private void updateProbabilityPane(){
        if (probabilityPane == null) return;
        int total = deck.size();
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Segoe UI, Sans-Serif; color:#ECECEC; background:#222;'>");
        html.append("<div style='padding:6px;'>");
        html.append("<h3 style='margin:6px 0 10px 0; color:#FFFFFF;'>Probability Breakdown</h3>");

        if (total == 0){
            html.append("<p style='color:#FFCCBB;'>Deck is empty — no probability available. Add or reset cards.</p>");
            html.append("</div></body></html>");
            probabilityPane.setText(html.toString());
            probabilityPane.setCaretPosition(0);
            return;
        }

        switch (chosenType){
            case INDIVIDUAL: {
                int fav = deck.countRankSuit(chosenRank, chosenSuit);
                html.append(String.format("<p><b>Chosen:</b> %s of %s (individual card)</p>", chosenRank, chosenSuit.name()));
                html.append("<ol>");
                html.append(String.format("<li>Count favorable outcomes (the chosen card) = <b>%d</b>.</li>", fav));
                html.append(String.format("<li>Total possible outcomes (cards in deck) = <b>%d</b>.</li>", total));
                html.append(String.format("<li>Probability = favorable / total = <b>%d / %d</b>.</li>", fav, total));
                if (fav == 0){
                    html.append("<li><span style='color:#FFAAAA;'>This card is NOT present in the deck — probability is 0.</span></li>");
                } else {
                    int g = gcd(fav, total);
                    int rn = fav / g, rd = total / g;
                    double pct = (100.0 * fav) / total;
                    double odds = (double) total / fav;
                    html.append(String.format("<li>Reduced fraction: <b>%d/%d</b> (dividing numerator and denominator by %d).</li>", rn, rd, g));
                    html.append(String.format("<li>Percentage: <b>%.3f%%</b>.</li>", pct));
                    html.append(String.format("<li>Odds: roughly <b>1 in %.2f</b> chance.</li>", odds));
                }
                html.append("</ol>");
                html.append("<p style='color:#CCCCCC; font-size:11px;'>Brief: The probability of drawing the exact card equals how many copies of that card are currently in the deck divided by total cards left.</p>");
                break;
            }
            case SUIT: {
                int fav = deck.countSuit(chosenSuit);
                html.append(String.format("<p><b>Chosen:</b> Suit = %s</p>", chosenSuit.name()));
                html.append("<ol>");
                html.append(String.format("<li>Count favorable outcomes (cards of that suit) = <b>%d</b>.</li>", fav));
                html.append(String.format("<li>Total possible outcomes (cards in deck) = <b>%d</b>.</li>", total));
                html.append(String.format("<li>Probability = <b>%d / %d</b>.</li>", fav, total));
                int g = gcd(fav, total);
                int rn = fav / g, rd = total / g;
                double pct = (100.0 * fav) / total;
                double odds = (fav == 0) ? Double.POSITIVE_INFINITY : ((double) total / fav);
                html.append(String.format("<li>Reduced fraction: <b>%d/%d</b>.</li>", rn, rd));
                html.append(String.format("<li>Percentage: <b>%.3f%%</b>.</li>", pct));
                html.append(String.format("<li>Odds: roughly <b>1 in %.2f</b>.</li>", odds));
                html.append("</ol>");
                html.append("<p style='color:#CCCCCC; font-size:11px;'>Brief: For suits, favorable outcomes are all cards that share the suit (e.g., all Hearts).</p>");
                break;
            }
            case COLOUR: {
                int fav = deck.countColor(chosenColor);
                html.append(String.format("<p><b>Chosen:</b> Colour = %s</p>", chosenColor.name()));
                html.append("<ol>");
                html.append(String.format("<li>Count favorable outcomes (cards of that colour) = <b>%d</b>.</li>", fav));
                html.append(String.format("<li>Total possible outcomes (cards in deck) = <b>%d</b>.</li>", total));
                html.append(String.format("<li>Probability = <b>%d / %d</b>.</li>", fav, total));
                int g = gcd(fav, total);
                int rn = fav / g, rd = total / g;
                double pct = (100.0 * fav) / total;
                double odds = (fav == 0) ? Double.POSITIVE_INFINITY : ((double) total / fav);
                html.append(String.format("<li>Reduced fraction: <b>%d/%d</b>.</li>", rn, rd));
                html.append(String.format("<li>Percentage: <b>%.3f%%</b>.</li>", pct));
                html.append(String.format("<li>Odds: roughly <b>1 in %.2f</b>.</li>", odds));
                html.append("</ol>");
                html.append("<p style='color:#CCCCCC; font-size:11px;'>Brief: Colours group suits into two categories (Hearts & Diamonds = RED, Clubs & Spades = BLACK).</p>");
                break;
            }
            case NUMBER: {
                int fav = deck.countRank(chosenRank);
                html.append(String.format("<p><b>Chosen:</b> Rank = %s (number)</p>", chosenRank));
                html.append("<ol>");
                html.append(String.format("<li>Count favorable outcomes (cards of that rank) = <b>%d</b>.</li>", fav));
                html.append(String.format("<li>Total possible outcomes (cards in deck) = <b>%d</b>.</li>", total));
                html.append(String.format("<li>Probability = <b>%d / %d</b>.</li>", fav, total));
                if (fav == 0){
                    html.append("<li><span style='color:#FFAAAA;'>That rank is NOT present in the deck — probability is 0.</span></li>");
                } else {
                    int g = gcd(fav, total);
                    int rn = fav / g, rd = total / g;
                    double pct = (100.0 * fav) / total;
                    double odds = (double) total / fav;
                    html.append(String.format("<li>Reduced fraction: <b>%d/%d</b>.</li>", rn, rd));
                    html.append(String.format("<li>Percentage: <b>%.3f%%</b>.</li>", pct));
                    html.append(String.format("<li>Odds: roughly <b>1 in %.2f</b>.</li>", odds));
                }
                html.append("</ol>");
                html.append("<p style='color:#CCCCCC; font-size:11px;'>Brief: Number bets match the rank regardless of suit (e.g., betting '7' wins if any 7 is drawn).</p>");
                break;
            }
        }

        // Optionally show expected value (brief)
        double p = 0.0;
        switch (chosenType){
            case INDIVIDUAL:
                p = (double) deck.countRankSuit(chosenRank, chosenSuit) / (double) total; break;
            case SUIT:
                p = (double) deck.countSuit(chosenSuit) / (double) total; break;
            case COLOUR:
                p = (double) deck.countColor(chosenColor) / (double) total; break;
            case NUMBER:
                p = (double) deck.countRank(chosenRank) / (double) total; break;
        }
        double mult = getMultiplierForChosen();
        double expected = p * (betAmount * mult) + (1 - p) * (0); // expected payout (not net) quickly shown
        html.append(String.format("<hr style='border-color:#444;'/>"));
        html.append(String.format("<p style='font-size:11px;color:#DDDDDD;'>Quick note: If you want expected net value: EV = p × (payout) − (1 − p) × (bet).<br/>Current quick payout expectation: <b>%.2f</b> (not net).</p>", expected));
        html.append("</div></body></html>");

        probabilityPane.setText(html.toString());
        probabilityPane.setCaretPosition(0);
    }

    /* ---------------------- Card painter (robust) ---------------------- */
    private static class CardComponent extends JComponent {
        private Card card = null;
        void setCard(Card c){ this.card = c; repaint(); }

        private static Image cardBack = null;
        static {
            try {
                java.net.URL res = gamePanel.class.getResource("/res/card back.png");
                if (res != null) {
                    cardBack = new ImageIcon(res).getImage();
                } else {
                    java.io.File f = new java.io.File("res/card back.png");
                    if (!f.exists()) f = new java.io.File("/res/card back.png");
                    if (f.exists()) cardBack = new ImageIcon(f.getAbsolutePath()).getImage();
                }
                if (cardBack != null) {
                    ImageIcon ii = new ImageIcon(cardBack);
                    if (ii.getIconWidth() <= 0 || ii.getIconHeight() <= 0) cardBack = null;
                }
            } catch (Exception e){
                cardBack = null;
            }
        }

        public CardComponent(){
            setPreferredSize(new Dimension(320, 440));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g){
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            g2.setColor(new Color(20,22,26));
            g2.fillRect(0,0,w,h);

            int cardW = (int) (Math.min(w * 0.92, w - 20));
            int cardH = (int) (Math.min(h * 0.92, h - 20));
            double aspect = 380.0 / 520.0;
            if (cardW > cardH * aspect) cardW = (int)(cardH * aspect);
            if (cardH > cardW / aspect) cardH = (int)(cardW / aspect);
            int x = (w - cardW)/2, y = (h - cardH)/2;

            g2.setColor(new Color(0,0,0,80));
            g2.fillRoundRect(x+6, y+8, cardW, cardH, 26, 26);

            GradientPaint gp = new GradientPaint(x, y, new Color(255,255,255), x, y+cardH, new Color(240,240,240));
            g2.setPaint(gp);
            RoundRectangle2D.Float rr = new RoundRectangle2D.Float(x,y,cardW,cardH,26,26);
            g2.fill(rr);

            g2.setColor(new Color(150,150,150));
            g2.setStroke(new BasicStroke(Math.max(1f, cardW / 210f)));
            g2.draw(rr);

            if (card == null){
                boolean drewImage = false;
                if (cardBack != null) {
                    int iw = cardBack.getWidth(this);
                    int ih = cardBack.getHeight(this);
                    if (iw > 0 && ih > 0) {
                        int imgMaxW = (int)(cardW * 0.7);
                        int imgMaxH = (int)(cardH * 0.7);
                        double is = Math.min((double)imgMaxW/iw, (double)imgMaxH/ih);
                        int drawW = (int)(iw * is);
                        int drawH = (int)(ih * is);
                        int ix = x + (cardW - drawW)/2;
                        int iy = y + (cardH - drawH)/2;
                        g2.drawImage(cardBack, ix, iy, drawW, drawH, this);
                        drewImage = true;
                    }
                }
                if (!drewImage) {
                    int fontSize = Math.max(14, cardW / 12);
                    g2.setFont(new Font("SansSerif", Font.BOLD, fontSize));
                    g2.setColor(new Color(140,140,140));
                    String s = "No card drawn";
                    FontMetrics fm = g2.getFontMetrics();
                    int sw = fm.stringWidth(s);
                    g2.drawString(s, x + (cardW-sw)/2, y + cardH/2);
                }
            } else {
                boolean isRed = (card.color() == ColorType.RED);
                Color suitColor = isRed ? new Color(180,40,40) : new Color(40,40,40);

                int rankFont = Math.max(18, cardW / 12);
                g2.setFont(new Font("SansSerif", Font.BOLD, rankFont));
                g2.setColor(suitColor);
                g2.drawString(card.rank, x + 18, y + 36);

                int glyphFont = Math.max(18, cardW / 12);
                g2.setFont(new Font("Serif", Font.PLAIN, glyphFont));
                g2.drawString(card.suit.glyph(), x + 18, y + 60);

                if (!card.isFace()){
                    int centerFont = Math.max(64, cardW / 2);
                    g2.setFont(new Font("Serif", Font.BOLD, centerFont));
                    FontMetrics fmCenter = g2.getFontMetrics();
                    String glyph = card.suit.glyph();
                    int gw = fmCenter.stringWidth(glyph);
                    g2.setColor(suitColor);
                    g2.drawString(glyph, x + (cardW - gw)/2, y + cardH/2 + fmCenter.getAscent()/3);
                } else {
                    drawFaceArt(g2, x, y, cardW, cardH, card.rank, card.suit, suitColor);
                }

                g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(14, cardW / 12)));
                String rank = card.rank;
                int sw = g2.getFontMetrics().stringWidth(rank);
                g2.drawString(rank, x + cardW - 18 - sw, y + cardH - 18);
                g2.setFont(new Font("Serif", Font.PLAIN, Math.max(12, cardW / 14)));
                String glyph2 = card.suit.glyph();
                int sgw = g2.getFontMetrics().stringWidth(glyph2);
                g2.drawString(glyph2, x + cardW - 18 - sgw, y + cardH - 40);
            }

            g2.dispose();
        }

        private void drawFaceArt(Graphics2D g2, int x, int y, int w, int h, String rank, Suit suit, Color suitColor){
            int px = x + Math.max(24, w/10), pw = w - Math.max(48, w/5), py = y + Math.max(40, h/12), ph = h - Math.max(120, h/6);
            GradientPaint gp = new GradientPaint(px, py, new Color(245,245,245), px, py+ph, new Color(230,230,230));
            g2.setPaint(gp);
            g2.fillRoundRect(px, py, pw, ph, 18, 18);

            g2.setColor(new Color(200,200,200));
            g2.setStroke(new BasicStroke(Math.max(1f, w/200f)));
            g2.drawRoundRect(px, py, pw, ph, 18, 18);

            int cx = px + pw/2;
            int cy = py + ph/3;
            g2.setColor(rank.equals("Q") ? new Color(150,80,200) : new Color(30,40,90));
            g2.fillOval(cx - Math.min(48, pw/6), cy - Math.min(60, ph/6), Math.min(96, pw/3), Math.min(96, ph/3));
            g2.setColor(new Color(245,224,195));
            g2.fillOval(cx - Math.min(30, pw/10), cy - Math.min(20, ph/10), Math.min(60, pw/6), Math.min(78, ph/6));
            g2.setColor(new Color(30,30,30));
            g2.fillOval(cx - 12, cy + 2, 8, 6);
            g2.fillOval(cx + 6, cy + 2, 8, 6);
            g2.drawArc(cx - 10, cy + 26, 20, 10, 0, -180);

            g2.setColor(suit == Suit.HEARTS || suit == Suit.DIAMONDS ? new Color(220,100,110) : new Color(30,80,140));
            Polygon collar = new Polygon();
            collar.addPoint(cx - Math.min(50, pw/6), py + ph - Math.min(40, ph/8));
            collar.addPoint(cx, py + ph - Math.min(10, ph/12));
            collar.addPoint(cx + Math.min(50, pw/6), py + ph - Math.min(40, ph/8));
            g2.fillPolygon(collar);

            g2.setFont(new Font("Serif", Font.BOLD, Math.max(24, pw/10)));
            g2.setColor(suitColor);
            FontMetrics fm = g2.getFontMetrics();
            String glyph = suit.glyph();
            int gw = fm.stringWidth(glyph);
            g2.drawString(glyph, cx - gw/2, py + ph/2 + fm.getAscent()/3);

            AffineTransform orig = g2.getTransform();
            int midY = py + ph/2;
            g2.translate(0, midY*2);
            g2.scale(1, -1);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.08f));
            g2.setFont(new Font("Serif", Font.BOLD, Math.max(48, pw/6)));
            g2.setColor(new Color(0,0,0));
            String big = rank;
            FontMetrics fmb = g2.getFontMetrics();
            int bw = fmb.stringWidth(big);
            g2.drawString(big, cx - bw/2, py + ph/2 + fmb.getAscent()/2);
            g2.setTransform(orig);
            g2.setComposite(AlphaComposite.SrcOver);
        }
    }

  /* ---------------------- Small UI helpers (styles) ---------------------- */
    private JButton stylizeButton(String text){
        JButton b = new JButton(text);
        b.setUI(new BasicButtonUI());
        b.setBackground(accent);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(8,14,8,14));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(b.getFont().deriveFont(BASE_BUTTON_FONT));
        return b;
    }
    private JButton stylizeButtonSmall(String text){
        JButton b = new JButton(text);
        b.setUI(new BasicButtonUI());
        b.setBackground(new Color(70,80,90));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(6,8,6,8));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(b.getFont().deriveFont(BASE_SMALL_FONT));
        return b;
    }
    private JTextField stylizeField(JTextField f){
        f.setOpaque(true);
        f.setBackground(new Color(245,245,245));
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200,200,200)),
                new EmptyBorder(6,6,6,6)
        ));
        return f;
    }
    private JComboBox<String> stylizeCombo(JComboBox<String> c){
        c.setBackground(Color.WHITE);
        return c;
    }

    // helper to create multiplier input fields (class-level method)
    private JTextField makeMulField(double value) {
        JTextField f = new JTextField(String.valueOf(value));
        f.setForeground(Color.WHITE);
        f.setBackground(new Color(55,60,70));
        f.setCaretColor(Color.WHITE);
        f.setBorder(BorderFactory.createEmptyBorder(4,6,4,6));
        f.setPreferredSize(new Dimension(90,26));
        return f;
    }

    /* ---------------------- Utility UI / status helpers ---------------------- */

    // small update method used across the UI
    private void updateDeckStatus(){
        if (deckCountLabel != null) deckCountLabel.setText("Deck: " + deck.size() + " cards");
        updateTopInfo();
        if (drawButton != null) drawButton.setEnabled(deck.size() > 0);
        updateProbabilityPane();
    }

    // toggles right tabs (edit deck) visibility
    private void toggleRightTabs(){
        if (rightTabs == null) return;
        boolean vis = rightTabs.isVisible();
        rightTabs.setVisible(!vis);
        if (!vis) {
            rightTabs.setPreferredSize(new Dimension(360, rightTabs.getPreferredSize().height));
        } else {
            rightTabs.setPreferredSize(new Dimension(0, rightTabs.getPreferredSize().height));
        }
        revalidate();
        repaint();
    }

    // apply basic scaling: scale a few key fonts to maintain layout on resize
    private void applyScaling(){
        int w = getWidth() > 0 ? getWidth() : BASE_WIDTH;
        int h = getHeight() > 0 ? getHeight() : BASE_HEIGHT;
        double scale = Math.min((double)w / BASE_WIDTH, (double)h / BASE_HEIGHT);

        float topFont = Math.max(10f, (float)(BASE_TOPINFO_FONT * scale));
        float titleFont = Math.max(14f, (float)(BASE_TITLE_FONT * scale));
        float controlsTitle = Math.max(12f, (float)(BASE_CONTROLS_TITLE_FONT * scale));
        float listFont = Math.max(10f, (float)(BASE_LIST_FONT * scale));
        float buttonFont = Math.max(12f, (float)(BASE_BUTTON_FONT * scale));
        float smallFont = Math.max(9f, (float)(BASE_SMALL_FONT * scale));
        float resultTitle = Math.max(18f, (float)(BASE_RESULT_TITLE_FONT * scale));

        if (topInfoLabel != null) topInfoLabel.setFont(topInfoLabel.getFont().deriveFont(topFont));
        if (deckList != null) deckList.setFont(deckList.getFont().deriveFont(listFont));
        if (deckCountLabel != null) deckCountLabel.setFont(deckCountLabel.getFont().deriveFont(smallFont));

        revalidate();
        repaint();
    }

    // ---- main method to run standalone ----
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Card Draw Game");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            gamePanel panel = new gamePanel();
            frame.setContentPane(panel);

            frame.setSize(1280, 820);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}

/* ---------------------- Simple rounded panel util ---------------------- */
class RoundedPanel extends JPanel {
    private final Color bg;
    private final int round;
    RoundedPanel(Color bg, int round){
        super();
        this.bg = bg;
        this.round = round;
        setOpaque(false);
    }
    @Override
    protected void paintComponent(Graphics g){
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(bg);
        g2.fillRoundRect(0,0,getWidth(),getHeight(),round,round);
        g2.dispose();
        super.paintComponent(g);
    }
}
