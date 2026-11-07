package com.mallagent.module.user.service;

import com.mallagent.module.user.api.UserQueryApi;
import com.mallagent.module.user.api.dto.UserBrief;
import com.mallagent.module.user.entity.SysUser;
import com.mallagent.module.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 用户基础信息只读查询实现。
 */
@Service
@RequiredArgsConstructor
public class UserQueryApiImpl implements UserQueryApi {

    private final SysUserMapper userMapper;

    @Override
    public Optional<UserBrief> findUser(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        SysUser user = userMapper.selectById(userId);
        return user == null ? Optional.empty() : Optional.of(toBrief(user));
    }

    @Override
    public List<UserBrief> findUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = userIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .map(this::toBrief)
                .toList();
    }

    private UserBrief toBrief(SysUser user) {
        return new UserBrief(user.getId(), user.getUsername(), user.getNickname());
    }
}
