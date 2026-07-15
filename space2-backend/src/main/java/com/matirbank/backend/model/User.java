package com.matirbank.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name; // encrypted

    @Column(name = "email", unique = true, nullable = false)
    private String email; // encrypted

    @Column(name = "password", nullable = false)
    private String password; // BCrypt hash

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "nid")
    private String nid; // encrypted

    @Column(name = "phone")
    private String phone; // encrypted

    @Column(name = "address")
    private String address; // encrypted

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    // === Enums ===
    public enum Role { CUSTOMER, ADMIN, MANAGER }

    // === Constructors ===
    public User() {}

    public User(String name, String email, String password, Role role, String nid, String phone, String address) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = role;
        this.nid = nid;
        this.phone = phone;
        this.address = address;
    }

    // === Getters / Setters ===
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getNid() { return nid; }
    public void setNid(String nid) { this.nid = nid; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
