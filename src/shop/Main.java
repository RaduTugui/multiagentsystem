package shop;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class Main extends JFrame {
    // Control Panel
    private JSpinner customerCountSpinner;
    private JTextField budgetField;
    private JTextField basePriceField;
    private JComboBox<String> itemSelector;

    // Status Panels
    private JLabel brokerStatusLabel;
    private JLabel sellerStatusLabel;
    private JLabel inventoryStatusLabel;
    private JLabel shippingStatusLabel;
    private Map<String, JLabel> customerStatusLabels = new HashMap<>();

    // Info Labels
    private JLabel stockLabel;
    private JLabel priceLabel;
    private JLabel marginLabel;
    private JLabel transactionsLabel;

    // Log Area
    private JTextArea logArea;

    // Buttons
    private JButton startButton;
    private JButton stopButton;
    private JButton addCustomerButton;

    // JADE Container
    private ContainerController container;
    private AgentController brokerAgent;
    private AgentController sellerAgent;
    private AgentController inventoryAgent;
    private AgentController shippingAgent;
    private final List<AgentController> customerAgents = new ArrayList<>();

    // Stats
    private int completedTransactions = 0;
    private int failedTransactions = 0;

    public Main() {
        setTitle("E-Commerce Multi-Agent System");
        setSize(900, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Main padding
        ((JPanel)getContentPane()).setBorder(new EmptyBorder(10, 10, 10, 10));

        // Build UI
        add(createHeaderPanel(), BorderLayout.NORTH);
        add(createCenterPanel(), BorderLayout.CENTER);
        add(createLogPanel(), BorderLayout.SOUTH);

        setLocationRelativeTo(null);
        setVisible(true);
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(44, 62, 80));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JLabel title = new JLabel("🛒 E-Commerce Multi-Agent System", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(Color.WHITE);

        panel.add(title, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        // Left: Controls
        panel.add(createControlPanel(), BorderLayout.WEST);

        // Right: Agent Status
        panel.add(createStatusPanel(), BorderLayout.CENTER);

        return panel;
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(236, 240, 241));

        // System Controls
        JPanel sysPanel = new JPanel(new GridLayout(3, 2, 5, 10));
        sysPanel.setBackground(new Color(236, 240, 241));
        sysPanel.setBorder(new TitledBorder("System Settings"));

        sysPanel.add(new JLabel("Initial Customers:"));
        customerCountSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 40, 1));
        customerCountSpinner.setToolTipText("Max 20 customers. Use 3–8 to observe strategy changes.");
        sysPanel.add(customerCountSpinner);

        sysPanel.add(new JLabel("Default Budget: $"));
        budgetField = new JTextField("1000", 8);
        budgetField.setToolTipText("Set below $960 to trigger rejections (base $800 × 1.20 margin = $960).");
        sysPanel.add(budgetField);

        sysPanel.add(new JLabel("Base Price: $"));
        basePriceField = new JTextField("800", 8);
        basePriceField.setToolTipText("Final price = base × (1 + margin). Margin starts at 20%.");
        sysPanel.add(basePriceField);

        panel.add(sysPanel);
        panel.add(Box.createVerticalStrut(10));

        // Item Selection
        JPanel itemPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        itemPanel.setBackground(new Color(236, 240, 241));
        itemPanel.setBorder(new TitledBorder("Product Settings"));

        itemSelector = new JComboBox<>(new String[]{"laptop", "phone", "tablet", "watch"});
        itemPanel.add(new JLabel("Item:"));
        itemPanel.add(itemSelector);

        panel.add(itemPanel);
        panel.add(Box.createVerticalStrut(10));

        // Stock Info Panel
        JPanel stockInfoPanel = new JPanel(new GridLayout(5, 1, 2, 2));
        stockInfoPanel.setBackground(new Color(214, 234, 248));
        stockInfoPanel.setBorder(new TitledBorder("📦 Stock Limits"));

        stockInfoPanel.add(makeHintLabel("laptop  → max 10 units"));
        stockInfoPanel.add(makeHintLabel("phone   → max 18 units"));
        stockInfoPanel.add(makeHintLabel("tablet  → max 14 units"));
        stockInfoPanel.add(makeHintLabel("watch   → max 20 units"));
        stockInfoPanel.add(makeHintLabel("⚠️ Low stock alert at 30%"));

        panel.add(stockInfoPanel);
        panel.add(Box.createVerticalStrut(10));

        // Decision Hint Panel
        JPanel hintPanel = new JPanel(new GridLayout(5, 1, 2, 2));
        hintPanel.setBackground(new Color(253, 245, 230));
        hintPanel.setBorder(new TitledBorder("🧠 Decision Hints"));

        hintPanel.add(makeHintLabel("Budget < $960 → rejections"));
        hintPanel.add(makeHintLabel("3 rejects → CONSERVATIVE"));
        hintPanel.add(makeHintLabel("3 accepts → AGGRESSIVE"));
        hintPanel.add(makeHintLabel("Rate < 40% → CONSERVATIVE"));
        hintPanel.add(makeHintLabel("Rate > 75% → AGGRESSIVE"));

        panel.add(hintPanel);
        panel.add(Box.createVerticalStrut(10));

        // Action Buttons
        JPanel btnPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        btnPanel.setBackground(new Color(236, 240, 241));

        startButton = createStyledButton("▶ Start System", new Color(39, 174, 96));
        stopButton = createStyledButton("⏹ Stop System", new Color(192, 57, 43));
        addCustomerButton = createStyledButton("➕ Add Customer", new Color(41, 128, 185));

        stopButton.setEnabled(false);
        addCustomerButton.setEnabled(false);

        startButton.addActionListener(this::startSimulation);
        stopButton.addActionListener(this::stopSimulation);
        addCustomerButton.addActionListener(this::addNewCustomer);

        btnPanel.add(startButton);
        btnPanel.add(stopButton);
        btnPanel.add(addCustomerButton);

        panel.add(btnPanel);
        panel.add(Box.createVerticalGlue());

        // Wrap in scroll pane so buttons are always reachable
        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setBorder(new CompoundBorder(
                new TitledBorder(new LineBorder(new Color(52, 152, 219), 2), "Simulation Controls"),
                new EmptyBorder(0, 0, 0, 0)
        ));
        scrollPane.setPreferredSize(new Dimension(280, 400));
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(10);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(scrollPane, BorderLayout.CENTER);
        return wrapper;
    }

    private JLabel makeHintLabel(String text) {
        JLabel label = new JLabel("  " + text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        label.setForeground(new Color(60, 60, 60));
        return label;
    }

    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 3, 10, 10));
        panel.setBorder(new CompoundBorder(
                new TitledBorder(new LineBorder(new Color(46, 204, 113), 2), "Agent Status Monitor"),
                new EmptyBorder(10, 10, 10, 10)
        ));

        // Broker Card
        brokerStatusLabel = createAgentCard("🤝 Broker", "Offline", new Color(149, 165, 166));
        panel.add(wrapInPanel(brokerStatusLabel, new Color(155, 89, 182)));

        // Seller Card
        sellerStatusLabel = createAgentCard("🏪 Seller", "Offline", new Color(149, 165, 166));
        panel.add(wrapInPanel(sellerStatusLabel, new Color(230, 126, 34)));

        // Inventory Card
        inventoryStatusLabel = createAgentCard("📦 Inventory", "Offline", new Color(149, 165, 166));
        JPanel invPanel = wrapInPanel(inventoryStatusLabel, new Color(241, 196, 15));
        stockLabel = new JLabel("Stock: -", SwingConstants.CENTER);
        stockLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        invPanel.add(stockLabel);
        panel.add(invPanel);

        // Shipping Card
        shippingStatusLabel = createAgentCard("🚚 Shipping", "Offline", new Color(149, 165, 166));
        panel.add(wrapInPanel(shippingStatusLabel, new Color(26, 188, 156)));

        // Price Monitor
        JPanel pricePanel = new JPanel(new GridLayout(3, 1));
        pricePanel.setBackground(new Color(52, 152, 219));
        pricePanel.setBorder(new LineBorder(new Color(52, 152, 219), 3, true));

        JLabel priceTitle = new JLabel("💰 Pricing", SwingConstants.CENTER);
        priceTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        priceTitle.setForeground(Color.WHITE);

        priceLabel = new JLabel("Current: $-", SwingConstants.CENTER);
        priceLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        priceLabel.setForeground(Color.WHITE);

        marginLabel = new JLabel("Margin: -%", SwingConstants.CENTER);
        marginLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        marginLabel.setForeground(Color.WHITE);

        pricePanel.add(priceTitle);
        pricePanel.add(priceLabel);
        pricePanel.add(marginLabel);
        panel.add(pricePanel);

        // Transactions Monitor
        JPanel transPanel = new JPanel(new GridLayout(3, 1));
        transPanel.setBackground(new Color(231, 76, 60));
        transPanel.setBorder(new LineBorder(new Color(231, 76, 60), 3, true));

        JLabel transTitle = new JLabel("📊 Statistics", SwingConstants.CENTER);
        transTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        transTitle.setForeground(Color.WHITE);

        transactionsLabel = new JLabel("Completed: 0", SwingConstants.CENTER);
        transactionsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        transactionsLabel.setForeground(Color.WHITE);

        JLabel failedLabel = new JLabel("Failed: 0", SwingConstants.CENTER);
        failedLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        failedLabel.setForeground(Color.WHITE);
        failedLabel.setName("failedLabel");

        transPanel.add(transTitle);
        transPanel.add(transactionsLabel);
        transPanel.add(failedLabel);
        panel.add(transPanel);

        return panel;
    }
    public void updateSellerStrategy(String strategy) {
        SwingUtilities.invokeLater(() -> {
            Color color;
            String label;
            switch (strategy) {
                case "AGGRESSIVE":
                    color = new Color(192, 57, 43);
                    label = "🔴 AGGRESSIVE";
                    break;
                case "CONSERVATIVE":
                    color = new Color(39, 174, 96);
                    label = "🟢 CONSERVATIVE";
                    break;
                default:
                    color = new Color(230, 126, 34);
                    label = "🟡 NEUTRAL";
                    break;
            }
            sellerStatusLabel.setText("<html><center><b>🏪 Seller</b><br>"
                    + "<font color='#" + String.format("%02x%02x%02x",
                    color.getRed(), color.getGreen(), color.getBlue())
                    + "'>● Active</font><br><small>" + label + "</small></center></html>");
        });
    }

    public void updateStockAlert(String item, boolean lowStock) {
        SwingUtilities.invokeLater(() -> {
            if (lowStock) {
                stockLabel.setForeground(new Color(192, 57, 43));
                stockLabel.setText("⚠️ LOW: " + item);
            } else {
                stockLabel.setForeground(Color.BLACK);
            }
        });
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder(new LineBorder(new Color(127, 140, 141), 2), "System Log"));
        panel.setPreferredSize(new Dimension(0, 200));

        logArea = new JTextArea();
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setEditable(false);
        logArea.setBackground(new Color(44, 62, 80));
        logArea.setForeground(new Color(236, 240, 241));
        logArea.setCaretColor(Color.WHITE);

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JButton createStyledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        return btn;
    }

    private JLabel createAgentCard(String name, String status, Color statusColor) {
        JLabel label = new JLabel("<html><center><b>" + name + "</b><br><font color='#" +
                String.format("%02x%02x%02x", statusColor.getRed(), statusColor.getGreen(), statusColor.getBlue()) +
                "'>● " + status + "</font></center></html>", SwingConstants.CENTER);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        return label;
    }

    private JPanel wrapInPanel(JLabel label, Color bg) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(bg);
        panel.setBorder(new LineBorder(bg, 3, true));
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    // ==================== SIMULATION CONTROL ====================

    private void startSimulation(ActionEvent e) {
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        addCustomerButton.setEnabled(true);

        Runtime rt = Runtime.instance();
        Profile p = new ProfileImpl();
        p.setParameter(Profile.GUI, "false");
        container = rt.createMainContainer(p);

        try {
            log("🚀 Starting E-Commerce MAS...");

            // Start Infrastructure Agents first
            shippingAgent = createAgent("Shipping1", "shop.ShippingAgent", new Object[]{this});
            updateAgentStatus("Shipping1", "Active", new Color(26, 188, 156));

            inventoryAgent = createAgent("Inventory1", "shop.InventoryAgent", new Object[]{this});
            updateAgentStatus("Inventory1", "Active", new Color(241, 196, 15));

            sellerAgent = createAgent("Seller1", "shop.SellerAgent", new Object[]{this, Double.parseDouble(basePriceField.getText())});
            updateAgentStatus("Seller1", "Active", new Color(230, 126, 34));

            brokerAgent = createAgent("Broker1", "shop.BrokerAgent", new Object[]{this});
            updateAgentStatus("Broker1", "Active", new Color(155, 89, 182));

            // Start Initial Customers
            int count = (Integer) customerCountSpinner.getValue();
            for (int i = 0; i < count; i++) {
                addCustomer(i + 1);
            }

            log("✅ System ready! " + count + " customers shopping.");

        } catch (Exception ex) {
            log("❌ Error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void stopSimulation(ActionEvent e) {
        try {
            log("🛑 Stopping system...");

            for (AgentController ac : customerAgents) ac.kill();
            customerAgents.clear();

            if (brokerAgent != null) brokerAgent.kill();
            if (sellerAgent != null) sellerAgent.kill();
            if (inventoryAgent != null) inventoryAgent.kill();
            if (shippingAgent != null) shippingAgent.kill();

            resetUI();
            log("✅ System stopped.");

        } catch (Exception ex) {
            log("❌ Stop error: " + ex.getMessage());
        }
    }

    private void addNewCustomer(ActionEvent e) {
        addCustomer(customerAgents.size() + 1);
    }

    private void addCustomer(int num) {
        try {
            String name = "Customer" + num;
            double budget = Double.parseDouble(budgetField.getText()) + (Math.random() * 200 - 100);
            String item = (String) itemSelector.getSelectedItem();

            AgentController customer = createAgent(name, "shop.CustomerAgent",
                    new Object[]{this, budget, item});
            customerAgents.add(customer);

            log("👤 " + name + " joined (Budget: $" + String.format("%.2f", budget) + ")");

        } catch (Exception ex) {
            log("❌ Failed to add customer: " + ex.getMessage());
        }
    }

    private AgentController createAgent(String name, String className, Object[] args) throws Exception {
        AgentController ac = container.createNewAgent(name, className, args);
        ac.start();
        return ac;
    }

    private void resetUI() {
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        addCustomerButton.setEnabled(false);

        updateAgentStatus("Broker1", "Offline", new Color(149, 165, 166));
        updateAgentStatus("Seller1", "Offline", new Color(149, 165, 166));
        updateAgentStatus("Inventory1", "Offline", new Color(149, 165, 166));
        updateAgentStatus("Shipping1", "Offline", new Color(149, 165, 166));

        stockLabel.setText("Stock: -");
        priceLabel.setText("Current: $-");
        marginLabel.setText("Margin: -%");
        transactionsLabel.setText("Completed: 0");
        completedTransactions = 0;
        failedTransactions = 0;
    }

    // ==================== PUBLIC UPDATE METHODS ====================

    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + java.time.LocalTime.now().withNano(0) + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public void updateAgentStatus(String agentName, String status, Color color) {
        SwingUtilities.invokeLater(() -> {
            String html = "<html><center><b>" + getAgentIcon(agentName) + " " + agentName.replaceAll("\\d", "") +
                    "</b><br><font color='#" + String.format("%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()) +
                    "'>● " + status + "</font></center></html>";

            if (agentName.startsWith("Broker")) brokerStatusLabel.setText(html);
            else if (agentName.startsWith("Seller")) sellerStatusLabel.setText(html);
            else if (agentName.startsWith("Inventory")) inventoryStatusLabel.setText(html);
            else if (agentName.startsWith("Shipping")) shippingStatusLabel.setText(html);
        });
    }

    public void updateStock(String item, int current, int max) {
        SwingUtilities.invokeLater(() -> {
            stockLabel.setText(item + ": " + current + "/" + max);
        });
    }

    public void updatePrice(double price, double margin) {
        SwingUtilities.invokeLater(() -> {
            priceLabel.setText("Current: $" + String.format("%.2f", price));
            marginLabel.setText("Margin: " + (int)(margin * 100) + "%");
        });
    }

    public void transactionCompleted(boolean success) {
        SwingUtilities.invokeLater(() -> {
            if (success) completedTransactions++;
            else failedTransactions++;
            transactionsLabel.setText("Completed: " + completedTransactions);

            // Update failed label
            Component[] comps = ((JPanel)transactionsLabel.getParent()).getComponents();
            for (Component c : comps) {
                if (c instanceof JLabel && c.getName() != null && c.getName().equals("failedLabel")) {
                    ((JLabel)c).setText("Failed: " + failedTransactions);
                }
            }
        });
    }

    private String getAgentIcon(String name) {
        if (name.startsWith("Broker")) return "🤝";
        if (name.startsWith("Seller")) return "🏪";
        if (name.startsWith("Inventory")) return "📦";
        if (name.startsWith("Shipping")) return "🚚";
        return "👤";
    }

    public static void main(String[] args) {
        // Set look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(Main::new);
    }
}