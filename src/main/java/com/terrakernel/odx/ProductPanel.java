package com.terrakernel.odx;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.CompletionException;

// View Layer: Displays a list of products and allows adding them to the POS cart
public class ProductPanel extends JPanel {

    private final OdxClient client;
    private final POSPanel posPanel; // Reference to the POS cart
    
    // UI Components
    private JTextArea logArea;
    private JButton fetchButton;
    private JList<Product> productList;

    public ProductPanel(OdxClient client, POSPanel posPanel) {
        this.client = client;
        this.posPanel = posPanel;
        this.setLayout(new BorderLayout());
        createUI();
    }
    
    private void createUI() {
        // --- Center: Product List ---
        productList = new JList<>();
        productList.setCellRenderer(new ProductListRenderer());
        
        // Add double-click listener to add product to POS cart
        productList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) { // Double-click to add
                    Product selected = productList.getSelectedValue();
                    if (selected != null) {
                        posPanel.addItemToCart(selected);
                        logArea.append(String.format("Added '%s' to POS cart.\n", selected.name));
                    }
                }
            }
        });
        
        this.add(new JScrollPane(productList), BorderLayout.CENTER);

        // --- South: Log Area and Button Panel ---
        JPanel southPanel = new JPanel(new BorderLayout());
        logArea = new JTextArea("Click 'Fetch Products' to load items. Double-click to add to POS.\n");
        logArea.setEditable(false);
        southPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        
        fetchButton = new JButton("Fetch Products (product.product)");
        fetchButton.addActionListener(e -> fetchProducts());
        southPanel.add(fetchButton, BorderLayout.NORTH);
        
        southPanel.setPreferredSize(new Dimension(800, 100)); // Limit log size
        this.add(southPanel, BorderLayout.SOUTH);
    }
    
    // --- Data Fetching ---
    private void fetchProducts() {
        logArea.setText("Initiating ODXProxy Product request...\n");
        fetchButton.setEnabled(false);
        
        client.fetchProducts()
            .thenAccept(this::handleSuccess)
            .exceptionally(this::handleFailure);
    }

    // --- Handlers (Run on EDT via CompletableFuture) ---
    private void handleSuccess(List<Product> products) {
        SwingUtilities.invokeLater(() -> {
            DefaultListModel<Product> listModel = new DefaultListModel<>();
            products.forEach(listModel::addElement);
            productList.setModel(listModel);
            
            logArea.append("Successfully retrieved " + products.size() + " products.\n");
            fetchButton.setEnabled(true);
        });
    }

    private Void handleFailure(Throwable t) {
        SwingUtilities.invokeLater(() -> {
            Throwable rootCause = (t instanceof CompletionException) ? t.getCause() : t;
            logArea.append("FATAL PRODUCT FETCH ERROR: " + rootCause.getMessage() + "\n");
            fetchButton.setEnabled(true);
        });
        return null;
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