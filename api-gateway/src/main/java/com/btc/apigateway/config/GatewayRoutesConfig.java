package com.btc.apigateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-service", route -> route
                        .path("/auth/**")
                        .uri("lb://USER-SERVICE"))
                .route("user-service", route -> route
                        .path("/users/**")
                        .uri("lb://USER-SERVICE"))
                .route("expense-service", route -> route
                        .path("/expenses/**")
                        .uri("lb://EXPENSE-SERVICE"))
                .route("claim-service", route -> route
                        .path("/claims/**")
                        .uri("lb://CLAIM-SERVICE"))
                .route("trip-service", route -> route
                        .path("/trips/**")
                        .uri("lb://TRIP-SERVICE"))
                .route("payment-service", route -> route
                        .path("/payments/**")
                        .uri("lb://PAYMENT-SERVICE"))
                .route("notification-service", route -> route
                        .path("/notifications/**")
                        .uri("lb://NOTIFICATION-SERVICE"))
                .route("user-service-openapi", route -> route
                        .path("/user-service/v3/api-docs")
                        .filters(filter -> filter.rewritePath("/user-service/(?<segment>.*)", "/${segment}"))
                        .uri("lb://USER-SERVICE"))
                .route("expense-service-openapi", route -> route
                        .path("/expense-service/v3/api-docs")
                        .filters(filter -> filter.rewritePath("/expense-service/(?<segment>.*)", "/${segment}"))
                        .uri("lb://EXPENSE-SERVICE"))
                .route("claim-service-openapi", route -> route
                        .path("/claim-service/v3/api-docs")
                        .filters(filter -> filter.rewritePath("/claim-service/(?<segment>.*)", "/${segment}"))
                        .uri("lb://CLAIM-SERVICE"))
                .route("trip-service-openapi", route -> route
                        .path("/trip-service/v3/api-docs")
                        .filters(filter -> filter.rewritePath("/trip-service/(?<segment>.*)", "/${segment}"))
                        .uri("lb://TRIP-SERVICE"))
                .route("payment-service-openapi", route -> route
                        .path("/payment-service/v3/api-docs")
                        .filters(filter -> filter.rewritePath("/payment-service/(?<segment>.*)", "/${segment}"))
                        .uri("lb://PAYMENT-SERVICE"))
                .route("notification-service-openapi", route -> route
                        .path("/notification-service/v3/api-docs")
                        .filters(filter -> filter.rewritePath("/notification-service/(?<segment>.*)", "/${segment}"))
                        .uri("lb://NOTIFICATION-SERVICE"))
                .build();
    }
}
