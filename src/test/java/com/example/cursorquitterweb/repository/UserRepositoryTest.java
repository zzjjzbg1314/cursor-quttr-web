package com.example.cursorquitterweb.repository;

import com.example.cursorquitterweb.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserRepository测试类
 */
@DataJpaTest
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
public class UserRepositoryTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    public void testSaveUser() {
        // 创建测试用户
        User user = new User("测试用户");
        user.setAvatarUrl("https://example.com/avatar.jpg");
        
        // 保存用户
        User savedUser = userRepository.save(user);
        
        // 验证保存结果
        assertNotNull(savedUser.getId());
        assertEquals("测试用户", savedUser.getNickname());
        assertEquals("https://example.com/avatar.jpg", savedUser.getAvatarUrl());
    }
    
    @Test
    public void testFindByNicknameContainingIgnoreCase() {
        // 创建测试用户
        User user1 = new User("张三");
        entityManager.persistAndFlush(user1);
        
        User user2 = new User("李四");
        entityManager.persistAndFlush(user2);
        
        // 搜索包含"三"的用户
        List<User> users = userRepository.findByNicknameContainingIgnoreCase("三");
        
        // 验证搜索结果
        assertEquals(1, users.size());
        assertEquals("张三", users.get(0).getNickname());
    }
    
    @Test
    public void testFindById() {
        // 创建测试用户
        User user = new User("测试用户");
        entityManager.persistAndFlush(user);
        
        // 根据ID查找用户
        Optional<User> foundUser = userRepository.findById(user.getId());
        
        // 验证查找结果
        assertTrue(foundUser.isPresent());
        assertEquals("测试用户", foundUser.get().getNickname());
    }
}
