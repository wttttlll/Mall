package com.mallagent.module.user.api;

import com.mallagent.module.user.api.dto.UserBrief;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 用户基础信息只读查询契约。
 *
 * <p>仅用于订单等模块展示用户标识，其他模块不得直接使用用户模块 Mapper。</p>
 */
public interface UserQueryApi {

    Optional<UserBrief> findUser(Long userId);

    List<UserBrief> findUsers(Collection<Long> userIds);
}
