package com.skysport.datn.enums;

/**
 * Tên role trong DB (Role.name) — thay thế string literal "Admin", "Nhân viên", "Khách hàng"
 */
public enum RoleName {

    ADMIN(1, "Admin"),
    STAFF(2, "Nhân viên"),
    CUSTOMER(3, "Khách hàng");

    private final int id;
    private final String dbName;

    RoleName(int id, String dbName) {
        this.id = id;
        this.dbName = dbName;
    }

    public int getId() { return id; }
    public String getDbName() { return dbName; }

    public boolean matches(String name) {
        return this.dbName.equals(name);
    }

    public static RoleName ofId(int id) {
        for (RoleName r : values()) {
            if (r.id == id) return r;
        }
        return CUSTOMER;
    }
}