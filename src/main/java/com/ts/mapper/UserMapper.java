/*
 * Description: MyBatis mapper for TS_USER table
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.mapper;

import com.ts.model.entity.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis mapper for User entity
 */
public interface UserMapper {

    /**
     * Get user by username
     */
    User getUserByUsername(@Param("username") String username);

    /**
     * Get all users
     */
    List<User> getAllUsers();

    /**
     * Update user password
     */
    int updateUserPassword(@Param("user") User user);

    /**
     * Insert a new user
     */
    int insertUser(@Param("user") User user);

    /**
     * Update user details (fullName, email, phone, status, role, lastUpdatedBy)
     */
    int updateUser(@Param("user") User user);

    /**
     * Delete a user
     */
    int deleteUser(@Param("username") String username);
}
