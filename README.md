# Garbigo Auth Service

![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk)
![Spring
Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen?style=flat-square&logo=springboot)
![Security](https://img.shields.io/badge/Security-JWT-blue?style=flat-square&logo=jsonwebtokens)
![Build](https://img.shields.io/badge/Build-Maven-red?style=flat-square&logo=apachemaven)
![License](https://img.shields.io/badge/License-MIT-lightgrey?style=flat-square)

## Overview

Garbigo Auth Service is a Spring Boot-based authentication and
authorization microservice designed for the Garbigo Smart Garbage
Management System. It provides secure identity management for users
interacting with the ecosystem, including residents, collectors,
administrators, and integrated services.

The service is responsible for user authentication, token generation,
validation, and role-based access control across the platform.

## System Context

The Garbigo ecosystem is composed of multiple microservices. This
authentication service acts as the central security layer that controls
access across all components.

    Client (Web / Mobile Applications)
            |
            v
       API Gateway
            |
            v
    Authentication Service (This Project)
            |
            v
       Other Microservices (Waste Management, Tracking, Analytics, etc.)

## Core Responsibilities

-   User registration and authentication
-   JWT token generation and validation
-   Role-based access control (RBAC)
-   Secure password storage using encryption
-   Token refresh and session management
-   Identity verification for microservices communication

## Technology Stack

-   Java 21
-   Spring Boot 3.x
-   Spring Security
-   JSON Web Tokens (JWT)
-   Maven
-   MySQL or PostgreSQL
-   Docker (optional for containerization)

## Features

-   Secure user registration and login
-   JWT-based stateless authentication
-   Role-based authorization (Admin, User, Collector)
-   Password encryption using BCrypt
-   RESTful API design
-   Microservice-ready architecture
-   Token refresh mechanism
-   Scalable and extensible security layer

## API Endpoints

### Authentication

  Method   Endpoint             Description
  -------- -------------------- -------------------
  POST     /api/auth/register   Register new user
  POST     /api/auth/login      Authenticate user
  POST     /api/auth/refresh    Refresh JWT token

### User Management

  Method   Endpoint          Description
  -------- ----------------- --------------------
  GET      /api/users        Retrieve all users
  GET      /api/users/{id}   Get user by ID
  DELETE   /api/users/{id}   Delete user

## Authentication Flow

    User -> Client Application
    Client -> Auth Service (Login Request)
    Auth Service -> Database (Validate Credentials)
    Database -> Auth Service (User Data)
    Auth Service -> Client (JWT Token)
    Client -> Protected Services (Authenticated Requests)

## Setup Instructions

### Prerequisites

-   Java 17+
-   Maven 3+
-   MySQL or PostgreSQL

### Installation

``` bash
git clone git@github.com:peacemakerbill/garbigo_auth-service.git
cd garbigo_auth-service
mvn clean install
mvn spring-boot:run
```

## Configuration

``` yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/garbigo_auth
    username: root
    password: your_password

jwt:
  secret: your_secret_key
  expiration: 86400000
```

## Docker Deployment

``` bash
docker build -t garbigo-auth-service .
docker run -p 8080:8080 garbigo-auth-service
```

## Testing

``` bash
mvn test
```

## Future Improvements

-   OAuth2 integration
-   Multi-factor authentication
-   Centralized logging
-   API Gateway enhancement
-   Distributed session management
-   Advanced role system

## License

MIT License

## Author

Garbigo System Team

## Vision

Secure authentication backbone for smart city waste management systems.
