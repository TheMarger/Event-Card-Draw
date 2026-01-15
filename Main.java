import javax.swing.*;

public class Main {
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
