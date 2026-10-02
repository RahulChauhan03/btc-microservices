package com.btc.claimservice.config;

import java.util.List;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fails startup before any bean (including the DataSource) is created when the shared datasource
 * credentials from Config Server cannot be resolved. Without this, Spring binds an unresolved
 * ${MYSQL_PASSWORD} literally and the failure surfaces as a misleading "Access denied" from MySQL.
 */
@Component
public class RequiredSettingsValidator implements BeanFactoryPostProcessor, EnvironmentAware {

    private static final List<String> REQUIRED = List.of("spring.datasource.username", "spring.datasource.password");

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        for (String key : REQUIRED) {
            String value;
            try {
                value = environment.getRequiredProperty(key);
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Missing required setting '" + key
                        + "': set MYSQL_USER and MYSQL_PASSWORD in this service's environment", exception);
            }
            if (value.isBlank()) {
                throw new IllegalStateException("Required setting '" + key + "' is blank");
            }
        }
    }
}
