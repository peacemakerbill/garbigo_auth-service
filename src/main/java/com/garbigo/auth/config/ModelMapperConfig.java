package com.garbigo.auth.config;

import com.garbigo.auth.dto.UserDto;
import com.garbigo.auth.model.User;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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