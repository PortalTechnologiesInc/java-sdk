# Portal Java SDK

Java SDK for the [Portal REST API](https://github.com/PortalTechnologiesInc/lib).

## Installation

Add via JitPack:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}

// build.gradle.kts
dependencies {
    implementation("com.github.PortalTechnologiesInc:java-sdk:0.4.0")
}
```

## Quick Start

```java
PortalClient client = new PortalClient(
    "http://localhost:3000",   // portal-rest base URL
    "your-auth-token",
    "your-webhook-secret"      // null if you only use polling
);

// Check connectivity
String status = client.health(); // "OK"
```

## Async Operations

All async methods return `AsyncOperation<T>` immediately. The `done` future resolves
when a terminal event arrives — either via webhook or polling.

```java
// Request a single payment
AsyncOperation<StreamEvent> op = client.requestSinglePayment(
    mainKey,
    List.of(),  // subkeys
    new SinglePaymentRequestContent("Coffee", 1000, Currency.MILLISATS, null, null, null)
);

System.out.println("Stream ID: " + op.streamId());
```

### Option A — Polling (no inbound HTTP required)

```java
StreamEvent result = client.pollUntilComplete(
    op.streamId(),
    PollOptions.defaults()
        .intervalMs(500)
        .timeoutMs(60_000)
        .onEvent(event -> System.out.println("Event: " + event.type))
);
System.out.println("Terminal event: " + result.type);
```

### Option B — Webhooks (recommended for production)

Mount the webhook endpoint in your HTTP server. The SDK verifies the
`X-Portal-Signature` HMAC-SHA256 header automatically.

```java
// In your HTTP server's POST /portal/webhook handler:
byte[] rawBody = request.getBody();
String signature = request.getHeader("X-Portal-Signature");
client.deliverWebhookPayload(rawBody, signature);

// The done future resolves when the terminal event is delivered
StreamEvent result = op.done().get(60, TimeUnit.SECONDS);
```

## Other Operations

```java
// Key handshake
AsyncOperation<KeyHandshakeResult> handshake = client.newKeyHandshakeUrl(null, false);
KeyHandshakeResult r = client.pollUntilComplete(handshake.streamId(), PollOptions.defaults())
    .data.get... // or use handshake.done() with webhooks

// Recurring payment
AsyncOperation<RecurringPaymentResponseContent> sub = client.requestRecurringPayment(
    mainKey, List.of(), content
);

// Invoice request
AsyncOperation<InvoicePaymentResponse> invoice = client.requestInvoice(
    recipientKey, List.of(),
    new RequestInvoiceParams(1000, Currency.MILLISATS, "Test invoice")
);

// Pay a BOLT11 invoice
PayInvoiceResponse paid = client.payInvoice("lnbc...");

// JWT
String token = client.issueJwt(targetKey, 24);
String key = client.verifyJwt(pubkey, token);

// Profile
Profile profile = client.fetchProfile(mainKey);

// Wallet info
WalletInfoResponse info = client.getWalletInfo();
```

## Versioning & Compatibility

The Java SDK version `major.minor` must match the portal-rest (sdk-daemon) version.
Patch versions are independent.

| Java SDK | sdk-daemon |
|----------|------------|
| 0.4.x    | 0.4.x      |
| 0.3.x    | 0.3.x      |

## Architecture

The new REST + polling/webhooks architecture replaces the previous WebSocket API:

- **Sync operations** return their result directly.
- **Async operations** return `AsyncOperation<T>` with `streamId` (available immediately)
  and `done` (`CompletableFuture<T>` that resolves on terminal event).
- **Polling** — `pollUntilComplete(streamId, options)` loops on `GET /events/:streamId`.
- **Webhooks** — `deliverWebhookPayload(rawBody, signature)` verifies HMAC-SHA256 and
  resolves the matching `done` future.
