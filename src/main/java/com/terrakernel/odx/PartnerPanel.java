package com.terrakernel.odx;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CompletionException;

public class PartnerPanel extends JPanel {
    
    private final OdxClient client;
    
    // UI Components
    private JTextArea logArea;
    private JButton fetchButton;
    private JList<Partner> partnerList;
    private PartnerDetailPanel detailPanel;
    
    // Layout management
    private CardLayout cardLayout;
    private JPanel cardPanel;
    private static final String LIST_VIEW = "ListView";
    private static final String DETAIL_VIEW = "DetailView";

    public PartnerPanel(OdxClient client) {
        this.client = client;
        this.setLayout(new BorderLayout());
        createUI();
    }
    
    private void createUI() {
        // 1. Initialize CardLayout and Card Panel
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        // 2. Create the List View Panel
        JPanel listViewPanel = createListViewPanel();

        // 3. Create the Detail View Panel
        detailPanel = new PartnerDetailPanel();

        // 4. Add both panels to the Card Panel
        cardPanel.add(listViewPanel, LIST_VIEW);
        cardPanel.add(detailPanel, DETAIL_VIEW);

        // 5. Add to the main panel
        this.add(cardPanel, BorderLayout.CENTER);
        
        // Button Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        fetchButton = new JButton("Fetch Partners");
        fetchButton.addActionListener(e -> fetchPartners());
        buttonPanel.add(fetchButton);
        this.add(buttonPanel, BorderLayout.SOUTH);
    }
    
    private JPanel createListViewPanel() {
        // The panel that holds the list of partners and the log area
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
        logArea.setText("Initiating ODXProxy Partner request...\n");
        fetchButton.setEnabled(false);
        
        // Call the new OdxClient service method
        client.fetchPartners()
            .thenAccept(this::handleSuccess)
            .exceptionally(this::handleFailure);
    }
    
    private void handleSuccess(List<Partner> partners) {
        // [PUT BACK CODE]
        SwingUtilities.invokeLater(() -> {
            // JList model update logic
            DefaultListModel<Partner> listModel = new DefaultListModel<>();
            partners.forEach(listModel::addElement);
            partnerList.setModel(listModel);
            partnerList.setVisibleRowCount(partners.size() > 0 ? 10 : 1);
            
            cardLayout.show(cardPanel, LIST_VIEW);
            logArea.append("Successfully retrieved " + partners.size() + " partners. Select one to view details.\n");
            fetchButton.setEnabled(true);
        });
    }

    private Void handleFailure(Throwable t) {
        // [PUT BACK CODE]
        SwingUtilities.invokeLater(() -> {
            Throwable rootCause = (t instanceof CompletionException) ? t.getCause() : t;
            logArea.append("FATAL PARTNER FETCH ERROR: " + rootCause.getMessage() + "\n");
            fetchButton.setEnabled(true);
        });
        return null;
    }

    // --- Inner Classes (Moved to PartnerPanel scope) ---
    private static class PartnerListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Partner) {
                setText(((Partner) value).name);
            }
            return this;
        }
    }
    
    private class PartnerDetailPanel extends JPanel {
        private JLabel vatLabel, streetLabel, street2Label, cityCountryLabel, phoneLabel, emailLabel;
        private JCheckBox customerCheck, supplierCheck;
        
        // [PUT BACK CODE]
        public PartnerDetailPanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // Back Button
            JButton backButton = new JButton("<- Back to List");
            backButton.addActionListener(e -> cardLayout.show(cardPanel, LIST_VIEW));
            backButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(backButton);
            add(Box.createVerticalStrut(10));

            // Initialize components
            emailLabel = new JLabel();
            phoneLabel = new JLabel();
            vatLabel = new JLabel();
            streetLabel = new JLabel();
            street2Label = new JLabel();
            cityCountryLabel = new JLabel();
            
            // Checkboxes
            customerCheck = new JCheckBox("Customer");
            supplierCheck = new JCheckBox("Supplier");
            
            // Add components to panel
            add(new JLabel("<html><h2>Partner Details</h2></html>"));
            add(emailLabel);
            add(phoneLabel);
            add(vatLabel);
            add(Box.createVerticalStrut(10));
            add(new JLabel("--- Address ---"));
            add(streetLabel);
            add(street2Label);
            add(cityCountryLabel);
            add(Box.createVerticalStrut(10));
            add(new JLabel("--- Ranks (Read-Only) ---"));
            add(customerCheck); 
            add(supplierCheck);
        }

        // [PUT BACK CODE]
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