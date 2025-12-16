package com.terrakernel.odx;

// Model Layer: Simple Java Object to hold Product data
public class Product {
    public int id;
    public String name;
    public double price;
    public String defaultCode; // Internal Reference/SKU
    public double quantity; // On hand quantity (for display)

    @Override
    public String toString() {
        return String.format("%s (Ref: %s) - $%.2f", name, defaultCode, price);
    }
}