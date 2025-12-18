package com.terrakernel.odx;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;

public class POSPanel extends JPanel {
    private final OdxClient client;

    private Integer currentSessionId = null;
    
    // POS Cart components (Right Side)
    private DefaultListModel<Product> cartModel;
    private JLabel totalLabel;

    // Session label
    private JLabel sessionStatusLabel;
    private JButton checkoutButton;
    private JButton storeControlButton;
    
    // Product Selection components (Left Side)
    private JList<Product> productList;
    private JTextArea logArea;
    private JButton fetchProductsButton;

    public POSPanel(OdxClient client) {
        this.client = client;
        this.setLayout(new BorderLayout());
        createUI();
        updateTotal(0.0);
        
        // Automatically fetch products when the POS tab loads (or is created)
        fetchProducts(); 
        checkPosSession();
    }
    
    private void createUI() {
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.6); // Give more space to products
        this.add(splitPane, BorderLayout.CENTER); 

        // --- 1. Left Side: Product Selection (ProductPanel content moved here) ---
        JPanel leftPanel = new JPanel(new BorderLayout());
        
        // Product List Setup
        productList = new JList<>();
        productList.setCellRenderer(new ProductListRenderer());
        leftPanel.add(new JScrollPane(productList), BorderLayout.CENTER);
        
        // Double-click listener to add product to POS cart
        productList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) { // Double-click to add
                    Product selected = productList.getSelectedValue();
                    if (selected != null) {
                        addItemToCart(selected);
                        logArea.append(String.format("Added '%s' to cart.\n", selected.name));
                    }
                }
            }
        });
        
        // Log Area and Button Panel for fetching products
        JPanel southPanel = new JPanel(new BorderLayout());
        logArea = new JTextArea("Fetching products...\n");
        logArea.setEditable(false);
        southPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        
        fetchProductsButton = new JButton("Refresh Products List");
        fetchProductsButton.addActionListener(e -> fetchProducts());
        southPanel.add(fetchProductsButton, BorderLayout.NORTH);
        
        southPanel.setPreferredSize(new Dimension(800, 100)); // Limit log size
        leftPanel.add(southPanel, BorderLayout.SOUTH);
        
        splitPane.setLeftComponent(leftPanel);

        // Store Control Button
        storeControlButton = new JButton("Open Store");
        storeControlButton.setVisible(false); // Hidden until checkPosSession finishes
        storeControlButton.addActionListener(e -> handleStoreControl());

        // --- 2. Right Side: Cart and Total ---
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("Shopping Cart"));
        
        // --- NEW: Session Status Header for Right Side ---
        JPanel sessionHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        sessionStatusLabel = new JLabel("Checking Store Status...");
        sessionStatusLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        sessionHeader.add(sessionStatusLabel);
        sessionHeader.add(storeControlButton);
        rightPanel.add(sessionHeader, BorderLayout.NORTH); // Add to top of cart area

        cartModel = new DefaultListModel<>();
        JList<Product> cartList = new JList<>(cartModel);
        rightPanel.add(new JScrollPane(cartList), BorderLayout.CENTER);
        
        // Total and Checkout
        JPanel checkoutPanel = new JPanel(new GridLayout(2, 1));
        totalLabel = new JLabel("Total: $0.00", SwingConstants.RIGHT);
        checkoutPanel.add(totalLabel);

        checkoutButton = new JButton("Checkout");
        checkoutButton.setEnabled(false);
        checkoutButton.addActionListener(e -> handleCheckout());
        checkoutPanel.add(checkoutButton);
       
        rightPanel.add(checkoutPanel, BorderLayout.SOUTH);
        
        splitPane.setRightComponent(rightPanel);
    }

    private void checkPosSession() {
        sessionStatusLabel.setText("Checking Session...");
        sessionStatusLabel.setForeground(Color.GRAY);
        storeControlButton.setEnabled(false);

        client.getOpenSessionId()
            .thenAccept(sessionId -> SwingUtilities.invokeLater(() -> {
                if (sessionId != null) {
                    this.currentSessionId = sessionId;
                    sessionStatusLabel.setText("● Store Open (#" + this.currentSessionId + ")");
                    sessionStatusLabel.setForeground(new Color(0, 150, 0)); // Dark Green
                    checkoutButton.setEnabled(true);
                    storeControlButton.setEnabled(true);
                    storeControlButton.setText("Close Store");
                    storeControlButton.setVisible(true);
                    logArea.append("POS Session #" + this.currentSessionId + " is active.\n");
                } else {
                    this.currentSessionId = null;
                    sessionStatusLabel.setText("○ Store Closed");
                    sessionStatusLabel.setForeground(Color.RED);
                    checkoutButton.setEnabled(false);
                    storeControlButton.setEnabled(true);
                    storeControlButton.setText("Open Store");
                    storeControlButton.setVisible(true);
                    logArea.append("WARNING: No active POS Session found. Store is closed.\n");
                }
            }))
            .exceptionally(t -> {
                SwingUtilities.invokeLater(() -> {
                    sessionStatusLabel.setText("Session Error");
                    sessionStatusLabel.setForeground(Color.RED);
                    logArea.append("SESSION ERROR: " + t.getMessage() + "\n");
                });
                return null;
            });
    }

    private void handleStoreControl() {
        storeControlButton.setEnabled(false);
        
        if (currentSessionId == null) {
            logArea.append("Attempting to open store...\n");
            client.openStore().thenAccept(newId -> {
                SwingUtilities.invokeLater(() -> {
                    logArea.append("Store Opened! Session ID: " + newId + "\n");
                    checkPosSession(); // Refresh UI state
                    storeControlButton.setEnabled(true);
                });
            }).exceptionally(t -> {
                SwingUtilities.invokeLater(() -> {
                    logArea.append("Open Store Failed: " + t.getMessage() + "\n");
                    storeControlButton.setEnabled(true);
                });
                return null;
            });
        } else {
            logArea.append("Attempting to close store...\n");

            client.closeStore()
                .thenAccept(success -> {
                    SwingUtilities.invokeLater(() -> {
                        logArea.append("Store closed successfully.\n");
                        checkPosSession(); // This will flip the button back to "Open Store"
                        storeControlButton.setEnabled(true);
                    });
                })
                .exceptionally(t -> {
                    SwingUtilities.invokeLater(() -> {
                        // Remove the 'CompletionException' wrapper to get the real Odoo error
                        Throwable cause = (t instanceof java.util.concurrent.CompletionException) ? t.getCause() : t;
                        logArea.append("CLOSE FAILED: " + cause.getMessage() + "\n");
                        
                        // Re-enable so the user can try again after fixing the issue in Odoo
                        storeControlButton.setEnabled(true);
                    });
                    return null;
                });
        }
    }

    private void handleCheckout() {
        List<Product> cart = Collections.list(cartModel.elements());
        checkoutButton.setEnabled(false);
        
        client.addOrderToSession(cart)
            .thenAccept(orderId -> SwingUtilities.invokeLater(() -> {
                logArea.append("Order Created: #" + orderId + "\n");
                cartModel.clear();
                updateTotal(0.0);
                checkoutButton.setEnabled(true);
            }))
            .exceptionally(t -> {
                SwingUtilities.invokeLater(() -> {
                    logArea.append("ORDER FAILED: " + t.getMessage() + "\n");
                    checkoutButton.setEnabled(true);
                });
                return null;
            });
    }
    
    // --- Product Fetching Logic (Moved from ProductPanel) ---
    private void fetchProducts() {
        logArea.setText("Initiating ODXProxy Product request...\n");
        fetchProductsButton.setEnabled(false);
        
        client.fetchProducts()
            .thenAccept(this::handleProductSuccess)
            .exceptionally(this::handleProductFailure);
    }

    private void handleProductSuccess(List<Product> products) {
        SwingUtilities.invokeLater(() -> {
            DefaultListModel<Product> listModel = new DefaultListModel<>();
            products.forEach(listModel::addElement);
            productList.setModel(listModel);
            
            logArea.append("Successfully retrieved " + products.size() + " products. Double-click to add to cart.\n");
            fetchProductsButton.setEnabled(true);
        });
    }

    private Void handleProductFailure(Throwable t) {
        SwingUtilities.invokeLater(() -> {
            Throwable rootCause = (t instanceof CompletionException) ? t.getCause() : t;
            logArea.append("FATAL PRODUCT FETCH ERROR: " + rootCause.getMessage() + "\n");
            fetchProductsButton.setEnabled(true);
        });
        return null;
    }
    
    // --- Cart Manipulation Logic ---
    public void addItemToCart(Product product) {
        cartModel.addElement(product);
        updateTotal(calculateTotal());
    }

    private double calculateTotal() {
        return Collections.list(cartModel.elements())
             .stream()
             .mapToDouble(p -> p.price)
             .sum();
    }
    
    private void updateTotal(double total) {
        totalLabel.setText(String.format("Total: $%.2f", total));
    }
    
    // --- Custom Renderer ---
    private static class ProductListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Product) {
                Product p = (Product) value;
                setText(String.format("<html><b>%s</b> &mdash; Ref: %s (Qty: %.0f) <span style='color: green;'>$%.2f</span></html>", 
                                      p.name, p.defaultCode, p.quantity, p.price));
            }
            return this;
        }
    }
}