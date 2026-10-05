<img width="1920" height="1000" alt="Screenshot 2026-10-05 at 5 57 32 PM" src="https://github.com/user-attachments/assets/1b382d6e-1d31-45c3-99aa-d72a9682a683" />
<img width="1920" height="1000" alt="Screenshot 2026-10-05 at 5 57 23 PM" src="https://github.com/user-attachments/assets/29d1473c-7a51-4a16-8a28-bf0215f814d7" />
<img width="1920" height="998" alt="Screenshot 2026-10-05 at 5 57 11 PM" src="https://github.com/user-attachments/assets/66c41d2f-53f2-48cf-9576-f4615701d047" />
<img width="1920" height="998" alt="Screenshot 2026-10-05 at 5 56 56 PM" src="https://github.com/user-attachments/assets/48d8da80-9ff0-4985-b96b-f86a6afc951e" />
<img width="1919" height="1001" alt="Screenshot 2026-10-05 at 5 56 45 PM" src="https://github.com/user-attachments/assets/539e9868-3e60-43fa-b046-2416c22f46a3" />
<img width="1918" height="999" alt="Screenshot 2026-10-05 at 5 56 31 PM" src="https://github.com/user-attachments/assets/0262748f-aa2c-4b06-b958-bf4540a81e5b" />
<img width="1906" height="972" alt="Screenshot 2026-10-05 at 5 56 17 PM" src="https://github.com/user-attachments/assets/0c1f2216-d3a4-48d4-b38e-b25ed6c01d93" />
<img width="1915" height="999" alt="Screenshot 2026-10-05 at 5 56 02 PM" src="https://github.com/user-attachments/assets/90e40344-345e-42ed-929a-e6e02a081348" />
<img width="1917" height="998" alt="Screenshot 2026-10-05 at 5 55 45 PM" src="https://github.com/user-attachments/assets/0f73572b-513a-4b67-9e5b-43dab1f8ab11" />
<img width="1920" height="999" alt="Screenshot 2026-10-05 at 5 55 11 PM" src="https://github.com/user-attachments/assets/8de91b86-cf64-4f0f-9240-c2dd90142b5a" />
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
