package com.garbigo.auth.config;

import com.garbigo.auth.dto.UserDto;
import com.garbigo.auth.model.User;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared ModelMapper instance for the whole service.
 * <p>
 * Previously AuthService, SocialAuthService and UserService each instantiated their own
 * {@code new ModelMapper()}. Centralizing it here means the User -> UserDto mapping only
 * needs to be configured once - in particular, User.getUsername() is the Spring Security
 * identity (email), not the person's chosen username, so ModelMapper's default
 * convention-based matching can't be trusted to populate UserDto.username correctly. This
 * explicitly maps it from User.getDisplayUsername() instead.
 */
@Configuration
public class ModelMapperConfig {

    @Bean
    ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();

        modelMapper.createTypeMap(User.class, UserDto.class)
                .addMapping(User::getDisplayUsername, UserDto::setUsername);

        return modelMapper;
    }
}