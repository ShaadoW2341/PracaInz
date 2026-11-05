package pl.mycomap.config;

import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ValidationConfig {
    @Bean
    ValidationConfigurationCustomizer polishCalendar() {
        // „Dzisiaj” w formularzu i walidacji daty oznacza tę samą datę, także po północy.
        return configuration -> configuration.clockProvider(() -> Clock.system(ZoneId.of("Europe/Warsaw")));
    }
}
