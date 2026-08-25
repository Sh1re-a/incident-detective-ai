package dev.shirwac.incidentdetective.replay;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class ReplayClockConfiguration {

    @Bean
    Clock replayClock() {
        return Clock.systemUTC();
    }
}
