package com.terrakernel.odx;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {
    
    private final OdxClient client;

    public MainFrame() {
        super("ODX Demo Application");
        
        this.client = new OdxClient();
        
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setSize(800, 600); 
        this.setLayout(new BorderLayout());

        createTabbedUI();
        
        this.setLocationRelativeTo(null);
        this.setVisible(true);
    }
    
    private void createTabbedUI() {
        JTabbedPane tabbedPane = new JTabbedPane();

        // 1. Partner Management Tab 
        tabbedPane.addTab("Partners (CRM)", new PartnerPanel(client));

        // 2. POS/Order Entry Tab (Now includes product selection)
        POSPanel posPanel = new POSPanel(client);
        tabbedPane.addTab("POS/Order Entry", posPanel);

        // *** NOTE: The original ProductPanel tab is removed ***

        this.add(tabbedPane, BorderLayout.CENTER);
    }
}