# Portal Java SDK - Documentation

## Introduction

A Java client for the Portal WebSocket Server, providing Nostr-based authentication and Lightning Network payment processing capabilities.

---

## Installation

1. Add the Jitpack repository to your `build.gradle`:
   ```groovy
   repositories {
       maven { url 'https://jitpack.io' }
   }
   ```

   Or if you are using Maven, add the following to your `pom.xml`:
    ```xml
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
    ```

2. Add the dependency to your `build.gradle`:
   ```groovy
   dependencies {
      implementation 'com.github.PortalTechnologiesInc:java-sdk:0.3.0'
   }
   ```

   Or if you are using Maven, add the following to your `pom.xml`:
    ```xml
    <dependency>
        <groupId>com.github.PortalTechnologiesInc</groupId>
        <artifactId>java-sdk</artifactId>
        <version>0.3.0</version>
    </dependency>
    ```

3. Once you're done, you may now proceed integrating the SDK into your project.

---

## Versioning & Compatibility

The Java SDK version is kept in sync with the [Portal SDK Daemon](https://hub.docker.com/r/getportal/sdk-daemon) (`getportal/sdk-daemon`).

**Compatibility rule:** the `major.minor` version of the SDK must match the `major.minor` version of the SDK Daemon. The patch version (`x` in `0.3.x`) is independent and can differ — it only contains bug fixes.

| SDK version | SDK Daemon version |
|-------------|-------------------|
| `0.3.x` | `0.3.x` |

**Example:** SDK `0.3.0` works with `getportal/sdk-daemon:0.3.1`, but not with `getportal/sdk-daemon:0.4.0`.

When upgrading to a new `major.minor`, update both the SDK dependency and the Docker image tag together.


---

## Basic Usage

### Initialization

Create an instance of `PortalSDK` by passing the websocket endpoint of your portal server:

```java
var portalSDK = new PortalSDK(wsEndpoint);
```

### Connecting to the server

Establish the WebSocket connection, then authenticate with your token:

```java
portalSDK.connect();
portalSDK.authenticate(authToken);
```


### Sending a command

You can send a command to the server by calling the `sendCommand` method.

```java
portalSDK.sendCommand(request, (response, err) -> {
    if(err != null) {
        logger.error("error sending command: {}", err);
        return;
    }
    logger.info("command sent successfully: {}", response);
});
```

### Basic example

```java
portalSDK.sendCommand(new CalculateNextOccurrenceRequest("weekly", System.currentTimeMillis() / 1000), (res, err) -> {
    if(err != null) {
        logger.error("error calculating next occurrence: {}", err);
        return;
    }
    logger.info("next occurrence: {}", res.next_occurrence());
});
```
---

## Available Commands

Commands are implemented as specific request classes in [`src/main/java/cc/getportal/command/request/`](./src/main/java/cc/getportal/command/request/), and used via the `sendCommand()` method of the [`PortalSDK`](./src/main/java/cc/getportal/PortalSDK.java) class.

Some key available commands include:

- [`AuthRequest`](./src/main/java/cc/getportal/command/request/AuthRequest.java): Authenticate using a token.
- [`KeyHandshakeUrlRequest`](./src/main/java/cc/getportal/command/request/KeyHandshakeUrlRequest.java): Get handshake URL for key and relays.
- [`RequestSinglePaymentRequest`](./src/main/java/cc/getportal/command/request/RequestSinglePaymentRequest.java): Request a single payment.
- [`MintCashuRequest`](./src/main/java/cc/getportal/command/request/MintCashuRequest.java): Mint Cashu tokens.

> See [`src/main/java/cc/getportal/command/request/`](./src/main/java/cc/getportal/command/request/) for all available commands and additional details.

To use a command, instantiate its request class and pass it to `PortalSDK.sendCommand(...)`. The full list of commands may evolve; check the request folder for the latest options.

---

## Example Integrations

- See [portal-demo](https://github.com/PortalTechnologiesInc/portal-demo) for a Kotlin example.

---

## Main API

- `PortalSDK` - Main client class
- `PortalRequest` - Represents a request to the server
- `PortalResponse` - Represents a response from the server
- `PortalNotification` - Represents a notification from the server

---

## Support

For questions or issues, see the official documentation or open an issue on the project's GitHub repository.