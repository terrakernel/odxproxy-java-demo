package com.terrakernel.odx;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CompletionException;

// View Layer: Handles GUI components and user interaction
public class PartnerFrame extends JFrame {
    
    private final OdxPartnerClient client;
    
    // UI Components for the controller
    private JTextArea logArea;
    private JButton fetchButton;
    private JList<Partner> partnerList;
    private PartnerDetailPanel detailPanel;
    
    // Layout management
    private CardLayout cardLayout;
    private JPanel cardPanel;
    private static final String LIST_VIEW = "ListView";
    private static final String DETAIL_VIEW = "DetailView";

    public PartnerFrame() {
        super("ODXProxy Client Demo");
        
        // Initialize the Service layer
        this.client = new OdxPartnerClient(); 
        
        // Setup the main window
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setSize(600, 400);
        this.setLayout(new BorderLayout(10, 10));
        
        // Build and display the UI
        createUI();
        this.setLocationRelativeTo(null);
        this.setVisible(true);
    }
    
    private void createUI() {
        // 1. Initialize CardLayout and Card Panel
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        // 2. Create the List View Panel (where the main list and log are)
        JPanel listViewPanel = createListViewPanel();

        // 3. Create the Detail View Panel
        detailPanel = new PartnerDetailPanel();

        // 4. Add both panels to the Card Panel
        cardPanel.add(listViewPanel, LIST_VIEW);
        cardPanel.add(detailPanel, DETAIL_VIEW);

        // 5. Setup the main frame layout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.add(cardPanel, BorderLayout.CENTER);
        
        // Button Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        fetchButton = new JButton("Fetch Partners (res.partner)");
        fetchButton.addActionListener(e -> fetchPartners());
        buttonPanel.add(fetchButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        this.add(mainPanel);
    }
    
    private JPanel createListViewPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        
        logArea = new JTextArea("Click 'Fetch Partners' to begin...\n");
        logArea.setEditable(false);
        panel.add(new JScrollPane(logArea), BorderLayout.SOUTH);

        partnerList = new JList<>();
        partnerList.setCellRenderer(new PartnerListRenderer());
        
        // Add listener for selection change to switch to detail view
        partnerList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && partnerList.getSelectedValue() != null) {
                Partner selected = partnerList.getSelectedValue();
                detailPanel.displayPartner(selected);
                cardLayout.show(cardPanel, DETAIL_VIEW);
            }
        });
        
        panel.add(new JScrollPane(partnerList), BorderLayout.CENTER);
        return panel;
    }

    private void fetchPartners() {
        logArea.setText("Initiating ODXProxy request...\n");
        fetchButton.setEnabled(false);
        
        // Call the service layer method asynchronously
        client.fetchPartners()
            .thenAccept(this::handleSuccess)
            .exceptionally(this::handleFailure);
    }

    // Runs on EDT (SwingUtilities.invokeLater is handled inside CompletableFuture)
    private void handleSuccess(List<Partner> partners) {
        SwingUtilities.invokeLater(() -> {
            DefaultListModel<Partner> listModel = new DefaultListModel<>();
            partners.forEach(listModel::addElement);
            partnerList.setModel(listModel);
            partnerList.setVisibleRowCount(partners.size() > 0 ? 10 : 1);
            
            cardLayout.show(cardPanel, LIST_VIEW);
            logArea.append("Successfully retrieved " + partners.size() + " partners. Select one to view details.\n");
            fetchButton.setEnabled(true);
        });
    }

    // Runs on EDT
    private Void handleFailure(Throwable t) {
        SwingUtilities.invokeLater(() -> {
            // Unwrap the CompletionException if present
            Throwable rootCause = (t instanceof CompletionException) ? t.getCause() : t;
            logArea.append("FATAL ERROR: " + rootCause.getMessage() + "\n");
            rootCause.printStackTrace();
            fetchButton.setEnabled(true);
        });
        return null;
    }

    // --- Inner Classes for UI Components (can be moved to separate files later) ---

    // Part of the View Layer: Custom Renderer
    private static class PartnerListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Partner) {
                // Uses the Partner.toString() method, which is the name
                setText(((Partner) value).name); 
            }
            return this;
        }
    }

    // Part of the View Layer: Detail Screen
    private class PartnerDetailPanel extends JPanel {
        private JLabel vatLabel, streetLabel, street2Label, cityCountryLabel, phoneLabel, emailLabel;
        private JCheckBox customerCheck, supplierCheck;

        public PartnerDetailPanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // Back Button
            JButton backButton = new JButton("<- Back to List");
            backButton.addActionListener(e -> cardLayout.show(cardPanel, LIST_VIEW));
            backButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(backButton);
            add(Box.createVerticalStrut(10));

            // Initialize and add all data labels/checkboxes (removed for brevity, same as original logic)
            // ... (All JLabel and JCheckBox initialization and adding logic here) ...
            emailLabel = new JLabel(); phoneLabel = new JLabel(); vatLabel = new JLabel(); streetLabel = new JLabel(); street2Label = new JLabel(); cityCountryLabel = new JLabel();
            customerCheck = new JCheckBox("Customer"); supplierCheck = new JCheckBox("Supplier");
            
            add(new JLabel("<html><h2>Partner Details</h2></html>"));
            add(emailLabel); add(phoneLabel); add(vatLabel);
            add(Box.createVerticalStrut(10));
            add(new JLabel("--- Address ---"));
            add(streetLabel); add(street2Label); add(cityCountryLabel);
            add(Box.createVerticalStrut(10));
            add(new JLabel("--- Ranks (Read-Only) ---"));
            add(customerCheck); add(supplierCheck);
        }

        public void displayPartner(Partner p) {
            // Update the header with the partner's name
            setBorder(BorderFactory.createTitledBorder(p.name));
            
            // Update Labels
            emailLabel.setText("Email: " + p.email);
            phoneLabel.setText("Phone: " + p.phone);
            vatLabel.setText("VAT: " + p.vat);
            streetLabel.setText("Street: " + p.street);
            street2Label.setText("Street 2: " + p.street2);
            cityCountryLabel.setText("City/Country: " + p.city + (p.country.isEmpty() ? "" : ", " + p.country));
            
            // Update Checkboxes (True/False based on rank)
            customerCheck.setSelected(p.isCustomer);
            supplierCheck.setSelected(p.isSupplier);
            
            // Note: Disabling them makes them act as indicator lights
            customerCheck.setEnabled(false); 
            supplierCheck.setEnabled(false);
        }
    }
}