/*
 * Description: MyBatis implementation of user account persistence
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao.impl;

import com.ts.mapper.UserMapper;
import com.ts.model.entity.User;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.List;
/**
 * MyBatis-based implementation of user account persistence operations,
 * delegating to {@link com.ts.mapper.UserMapper}.
 */
public class UserMapperImpl implements UserMapper {

    private final SqlSessionFactory sqlSessionFactory;

    /**
     * Constructs a new UserMapperImpl instance.
     *
     * @param sqlSessionFactory  factory instance
     */
    public UserMapperImpl(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @Override
    /**
     * Returns the user by username.
     *
     * @param username  username name
     * @return the requested userbyusername object, or null if not found
     */
    public User getUserByUsername(String username) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            return mapper.getUserByUsername(username);
        }
    }

    @Override
    /**
     * Returns the all users.
     *
     * @return a list of matching records
     */
    public List<User> getAllUsers() {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            return mapper.getAllUsers();
        }
    }

    @Override
    /**
     * Updates the user password.
     *
     * @param user  user value
     * @return the numeric result
     */
    public int updateUserPassword(User user) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            int result = mapper.updateUserPassword(user);
            sqlSession.commit();
            return result;
        }
    }

    @Override
    /**
     * Inserts the user.
     *
     * @param user  user value
     * @return the numeric result
     */
    public int insertUser(User user) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            int result = mapper.insertUser(user);
            sqlSession.commit();
            return result;
        }
    }

    @Override
    /**
     * Updates the user.
     *
     * @param user  user value
     * @return the numeric result
     */
    public int updateUser(User user) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            int result = mapper.updateUser(user);
            sqlSession.commit();
            return result;
        }
    }

    @Override
    /**
     * Deletes the user.
     *
     * @param username  username name
     * @return the numeric result
     */
    public int deleteUser(String username) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            UserMapper mapper = sqlSession.getMapper(UserMapper.class);
            int result = mapper.deleteUser(username);
            sqlSession.commit();
            return result;
        }
    }
}
