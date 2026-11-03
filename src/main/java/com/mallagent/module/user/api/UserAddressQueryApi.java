package com.mallagent.module.user.api;

import com.mallagent.module.user.api.dto.AddressSnapshot;

import java.util.Optional;

/**
 * 用户收货地址只读查询契约。
 *
 * <p>其他模块只能通过本接口读取当前用户自己的地址，不得直接使用用户模块 Mapper。</p>
 */
public interface UserAddressQueryApi {

    /**
     * 查询属于指定用户的地址快照；地址不存在、已删除或不属于该用户时返回空。
     */
    Optional<AddressSnapshot> findForUser(Long userId, Long addressId);
}
