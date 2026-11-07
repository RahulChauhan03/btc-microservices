# BTC Microservices Project

A backend system built using **Java, Spring Boot, and Spring Cloud Microservices Architecture**. The project demonstrates how independent services communicate, discover each other, and manage centralized configuration through a distributed system.

## Architecture Overview

- **API Gateway** – Single entry point for client requests.
- **Service Registry (Eureka)** – Dynamic service discovery.
- **Config Server** – Centralized configuration management.
- **User Service** – User-related operations.
- **Trip Service** – Trip management.
- **Expense Service** – Expense tracking.
- **Payment Service** – Payment processing.
- **Notification Service** – Email/SMS notifications.
- **Claim Service** – Claim management and workflows.

## Tech Stack

- Java
- Spring Boot
- Spring Cloud
- Eureka Service Discovery
- Spring Cloud API Gateway
- REST APIs

## Architecture Workflow

1. Client requests enter through the API Gateway.
2. Services register with Eureka Server.
3. Services communicate through REST APIs.
4. Configuration is managed centrally through Config Server.

## Application Screenshots

<table>
  <tr>
    <td align="center"><b>Screenshot 1</b></td>
    <td align="center"><b>Screenshot 2</b></td>
    <td align="center"><b>Screenshot 3</b></td>
  </tr>
  <tr>
    <td><img src="https://github.com/user-attachments/assets/1b382d6e-1d31-45c3-99aa-d72a9682a683" width="100%"></td>
    <td><img src="https://github.com/user-attachments/assets/29d1473c-7a51-4a16-8a28-bf0215f814d7" width="100%"></td>
    <td><img src="https://github.com/user-attachments/assets/66c41d2f-53f2-48cf-9576-f4615701d047" width="100%"></td>
  </tr>
  <tr>
    <td align="center"><b>Screenshot 4</b></td>
    <td align="center"><b>Screenshot 5</b></td>
    <td align="center"><b>Screenshot 6</b></td>
  </tr>
  <tr>
    <td><img src="https://github.com/user-attachments/assets/48d8da80-9ff0-4985-b96b-f86a6afc951e" width="100%"></td>
    <td><img src="https://github.com/user-attachments/assets/539e9868-3e60-43fa-b046-2416c22f46a3" width="100%"></td>
    <td><img src="https://github.com/user-attachments/assets/0262748f-aa2c-4b06-b958-bf4540a81e5b" width="100%"></td>
  </tr>
  <tr>
    <td align="center"><b>Screenshot 7</b></td>
    <td align="center"><b>Screenshot 8</b></td>
    <td align="center"><b>Screenshot 9</b></td>
  </tr>
  <tr>
    <td><img src="https://github.com/user-attachments/assets/0c1f2216-d3a4-48d4-b38e-b25ed6c01d93" width="100%"></td>
    <td><img src="https://github.com/user-attachments/assets/90e40344-345e-42ed-929a-e6e02a081348" width="100%"></td>
    <td><img src="https://github.com/user-attachments/assets/0f73572b-513a-4b67-9e5b-43dab1f8ab11" width="100%"></td>
  </tr>
</table>

<p align="center">
  <img src="https://github.com/user-attachments/assets/8de91b86-cf64-4f0f-9240-c2dd90142b5a" width="65%">
</p>

## How to Run the Project

1. Start Config Server.
2. Start Service Registry (Eureka).
3. Start the microservices.
4. Start API Gateway.
5. Access the services through the Gateway.

## Why I Built This

This project helped me gain practical experience with:

- Microservices architecture.
- Service-to-service communication.
- Centralized configuration.
- Service discovery.
- Building distributed backend applications.

## Future Improvements

- Add Docker support.
- Implement CI/CD pipelines.
- Improve logging and monitoring.
- Extend security with OAuth2 and JWT.

---

**Note:** This project is a learning-focused implementation designed to demonstrate practical microservices architecture and backend development concepts.
