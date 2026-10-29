package com.bunny.rtt_api.mapper;

import com.bunny.rtt_api.dto.response.CredentialRowResponse;
import com.bunny.rtt_api.dto.response.UserResponse;
import com.bunny.rtt_api.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    UserResponse toUserResponse(User user);

    CredentialRowResponse toCredentialRow(User user);
}
