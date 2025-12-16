package com.terrakernel.odx;

import javax.swing.SwingUtilities;

// Entry Point: Launches the application
public class Main {
    public static void main(String[] args) {
        // Launches the PartnerFrame on the Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(PartnerFrame::new);
    }
}