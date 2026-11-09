/*
 * Description: Entity for TS_USER user accounts
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.model.entity;

import java.util.Date;

/**
 * User entity mapped to TS_USER table
 */
public class User {
    private String username;
    private String password;
    private String fullName;
    private String email;
    private String phone;
    private String status;
    private String role;
    private String createdBy;
    private Date   creationDate;
    private String lastUpdatedBy;
    private Date   lastUpdateDate;

    /**
     * Default constructor
     */
    public User() {
    }

    /**
     * Constructs a User with username and (BCrypt-hashed) password only.
     * Other fields default to {@code null}.
     *
     * @param username the login name (must be unique in TS_USER)
     * @param password the BCrypt-hashed password
     */
    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }

    /**
     * Constructs a fully-populated User record.
     *
     * @param username the login name (must be unique in TS_USER)
     * @param password the BCrypt-hashed password
     * @param fullName display name
     * @param email    email address for notifications
     * @param phone    phone number (optional, may be null)
     * @param status   account status ({@code Y} = active, {@code N} = disabled)
     * @param role     RBAC role ({@code ADMIN} or {@code USER})
     * @param createdBy  createdBy value
     * @param creationDate  date/time value
     * @param lastUpdatedBy  date/time value
     * @param lastUpdateDate  date/time value
     * @return the result as a String
     */
    public User(String username, String password, String fullName, String email,
                String phone, String status, String role, String createdBy,
                Date creationDate, String lastUpdatedBy, Date lastUpdateDate) {
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.status = status;
        this.role = role;
        this.createdBy = createdBy;
        this.creationDate = creationDate;
        this.lastUpdatedBy = lastUpdatedBy;
        this.lastUpdateDate = lastUpdateDate;
    }

    // Getters and setters
    /**
     * Returns the username.
     *
     * @return the result as a String
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username.
     *
     * @param username  username name
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Returns the password.
     *
     * @return the result as a String
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password.
     *
     * @param password  password value
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Returns the full name.
     *
     * @return the result as a String
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * Sets the full name.
     *
     * @param fullName  fullName name
     */
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /**
     * Returns the email.
     *
     * @return the result as a String
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets the email.
     *
     * @param email  email value
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Returns the phone.
     *
     * @return the result as a String
     */
    public String getPhone() {
        return phone;
    }

    /**
     * Sets the phone.
     *
     * @param phone  phone value
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /**
     * Returns the status.
     *
     * @return the result as a String
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the status.
     *
     * @param status  status value
     */
    public void setStatus(String status) {
        this.status = status;
    }
	
	/**
	 * Returns the role.
	 *
	 * @return the result as a String
	 */
	public String getRole() {
        return role;
    }

    /**
     * Sets the role.
     *
     * @param role  role value
     */
    public void setRole(String role) {
        this.role = role;
    }

    /**
     * Returns the created by.
     *
     * @return the result as a String
     */
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * Sets the created by.
     *
     * @param createdBy  createdBy value
     */
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * Returns the creation date.
     *
     * @return the requested creationdate object, or null if not found
     */
    public Date getCreationDate() {
        return creationDate;
    }

    /**
     * Sets the creation date.
     *
     * @param creationDate  date/time value
     */
    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    /**
     * Returns the last updated by.
     *
     * @return the result as a String
     */
    public String getLastUpdatedBy() {
        return lastUpdatedBy;
    }

    /**
     * Sets the last updated by.
     *
     * @param lastUpdatedBy  date/time value
     */
    public void setLastUpdatedBy(String lastUpdatedBy) {
        this.lastUpdatedBy = lastUpdatedBy;
    }

    /**
     * Returns the last update date.
     *
     * @return the requested lastupdatedate object, or null if not found
     */
    public Date getLastUpdateDate() {
        return lastUpdateDate;
    }

    /**
     * Sets the last update date.
     *
     * @param lastUpdateDate  date/time value
     */
    public void setLastUpdateDate(Date lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }

    @Override
    /**
     * To the string.
     *
     * @return the result as a String
     */
    public String toString() {
        return "User{" +
                "username='" + username + '\'' +
                ", fullName='" + fullName + '\'' +
                ", role='" + role + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
