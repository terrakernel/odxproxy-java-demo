package com.terrakernel.odx;

import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        // Launches the MainFrame on the Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(MainFrame::new);
    }
}