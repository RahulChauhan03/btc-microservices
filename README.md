#  BTC Microservices Project

This project is a backend system built using a **microservices architecture**.
The idea was to break down a large application into smaller, independent services that can scale and communicate with each other efficiently.

---

## Services Included

* **API Gateway** – Acts as the single entry point for all client requests
* **Service Registry (Eureka)** – Helps services discover each other dynamically
* **Config Server** – Centralised configuration management
* **User Service** – Handles user-related operations
* **Trip Service** – Manages trip data and operations
* **Expense Service** – Tracks and manages expenses
* **Payment Service** – Handles payment processing
* **Notification Service** – Sends notifications (email/SMS)
* **Claim Service** – Manages claims and related workflows

---

## Tech Stack

* Java
* Spring Boot
* Spring Cloud
* Eureka (Service Discovery)
* API Gateway
* REST APIs

---

## Architecture Overview

This project follows a typical **Spring Cloud Microservices architecture**:

* All requests first go through the **API Gateway**
* Services register themselves with **Eureka Server**
* Each service communicates using REST APIs
* Configuration is managed centrally using **Config Server**

This setup makes the system:

* Scalable
* Easy to maintain
* Fault-tolerant

---

## How to Run the Project

1. Start **Config Server**
2. Start **Service Registry (Eureka)**
3. Start all microservices (User, Trip, Payment, etc.)
4. Start **API Gateway**

Once everything is running, you can hit APIs through the Gateway.

---

## Why I Built This

I wanted to get hands-on experience with:

* Real-world microservices architecture
* Service-to-service communication
* Centralised configuration and discovery

This project helped me understand how large-scale backend systems are structured in production.

---

## Future Improvements

* Add authentication (JWT / OAuth2)
* Dockerize services
* Add CI/CD pipeline
* Improve logging & monitoring

---

## Final Note

This is a learning-focused project, but structured in a way that reflects real-world backend development practices.
