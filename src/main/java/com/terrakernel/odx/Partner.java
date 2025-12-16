package com.terrakernel.odx;

// Model Layer: Simple Java Object to hold data
public class Partner {
    // Fields must be public or have public getters/setters for easy access in the UI
    public int id;
    public String name;
    public String email;
    public String street;
    public String street2;
    public String city;
    public String country; 
    public String phone;
    public boolean isCustomer;
    public boolean isSupplier;
    public String vat;

    @Override
    public String toString() {
        // This is what the JList will display
        return name;
    }
}