package com.retailpos.model;

/** A supplier the store buys products from. */
public class Supplier {

    private int supplierID;
    private String name;
    private String address;
    private String contactInfo;

    public Supplier() {
    }

    public Supplier(int supplierID, String name, String address, String contactInfo) {
        this.supplierID = supplierID;
        this.name = name;
        this.address = address;
        this.contactInfo = contactInfo;
    }

    public int getSupplierID() {
        return supplierID;
    }

    public void setSupplierID(int supplierID) {
        this.supplierID = supplierID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getContactInfo() {
        return contactInfo;
    }

    public void setContactInfo(String contactInfo) {
        this.contactInfo = contactInfo;
    }

    @Override
    public String toString() {
        return "Supplier{"
                + "supplierID=" + supplierID
                + ", name='" + name + '\''
                + ", address='" + address + '\''
                + ", contactInfo='" + contactInfo + '\''
                + '}';
    }
}
