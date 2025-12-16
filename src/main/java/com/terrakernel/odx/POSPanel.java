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
    
    // POS Cart components (Right Side)
    private DefaultListModel<Product> cartModel;
    private JLabel totalLabel;
    
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

        // --- 2. Right Side: Cart and Total (Unchanged) ---
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("Shopping Cart"));
        
        cartModel = new DefaultListModel<>();
        JList<Product> cartList = new JList<>(cartModel);
        rightPanel.add(new JScrollPane(cartList), BorderLayout.CENTER);
        
        // Total and Checkout
        JPanel checkoutPanel = new JPanel(new GridLayout(2, 1));
        totalLabel = new JLabel("Total: $0.00", SwingConstants.RIGHT);
        checkoutPanel.add(totalLabel);

        JButton checkoutButton = new JButton("Checkout");
        checkoutPanel.add(checkoutButton);
        
        rightPanel.add(checkoutPanel, BorderLayout.SOUTH);
        
        splitPane.setRightComponent(rightPanel);
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
    
    // --- Custom Renderer (Moved from ProductPanel) ---
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