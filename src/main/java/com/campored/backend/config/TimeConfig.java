package com.campored.backend.config;

import com.campored.backend.dto.validation.OfertaValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfig {

    // El servidor corre en UTC; los cálculos por día calendario usan la fecha de Colombia
    @Bean
    public Clock clock() {
        return Clock.system(OfertaValidator.ZONA_COLOMBIA);
    }
}
