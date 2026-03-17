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
VersionResponse ver = client.version(); // { version: "0.4.0", gitCommit: "abc1234" }
```

## Async Operations

All async methods return `AsyncOperation<T>` immediately — the stream ID is available
right away, and `done` resolves with the typed result when the terminal event arrives.

```java
// Request a single payment → resolves to InvoiceStatus
AsyncOperation<InvoiceStatus> op = client.requestSinglePayment(
    mainKey, List.of(),
    new SinglePaymentRequestContent("Coffee", 1000, Currency.MILLISATS, null, null, null)
);

System.out.println("Stream ID: " + op.streamId());
```

### Option A — Polling (no inbound HTTP required)

```java
InvoiceStatus result = client.pollUntilComplete(
    op,
    PollOptions.defaults()
        .intervalMs(500)
        .timeoutMs(60_000)
        .onEvent(event -> System.out.println("event: " + event.type))
);
System.out.println("status: " + result.status); // "paid", "timeout", etc.
```

### Option B — Webhooks (recommended for production)

Mount the webhook handler in your HTTP server. The SDK verifies the
`X-Portal-Signature` HMAC-SHA256 header automatically.

```java
// POST /portal/webhook
byte[] rawBody = request.getBody();
String signature = request.getHeader("X-Portal-Signature");
client.deliverWebhookPayload(rawBody, signature);

// done resolves with the typed result
InvoiceStatus result = op.done().get(60, TimeUnit.SECONDS);
```

## All Async Methods

| Method | Resolves to |
|--------|-------------|
| `requestSinglePayment(...)` | `AsyncOperation<InvoiceStatus>` |
| `requestPaymentRaw(...)` | `AsyncOperation<InvoiceStatus>` |
| `requestRecurringPayment(...)` | `AsyncOperation<RecurringPaymentResponseContent>` |
| `requestInvoice(...)` | `AsyncOperation<InvoicePaymentResponse>` |
| `requestCashu(...)` | `AsyncOperation<CashuResponseStatus>` |
| `authenticateKey(...)` | `AsyncOperation<AuthResponseData>` |
| `newKeyHandshakeUrl(...)` | `AsyncOperation<KeyHandshakeResult>` |

## Other Operations

```java
// Recurring payment
AsyncOperation<RecurringPaymentResponseContent> sub = client.requestRecurringPayment(
    mainKey, List.of(), recurringContent
);
RecurringPaymentResponseContent r = client.pollUntilComplete(sub, PollOptions.defaults());

// Invoice request (ask recipient to generate a BOLT11)
AsyncOperation<InvoicePaymentResponse> inv = client.requestInvoice(
    recipientKey, List.of(),
    new RequestInvoiceParams(1000, Currency.MILLISATS, "Test invoice")
);
InvoicePaymentResponse paid = client.pollUntilComplete(inv, PollOptions.defaults());
System.out.println(paid.invoice); // lnbc...

// Pay a BOLT11 invoice directly
PayInvoiceResponse p = client.payInvoice("lnbc...");

// Key handshake
AsyncOperation<KeyHandshakeResult> hs = client.newKeyHandshakeUrl(null, false);
KeyHandshakeResult k = client.pollUntilComplete(hs, PollOptions.defaults());

// JWT
String token = client.issueJwt(targetKey, 24);
String key    = client.verifyJwt(pubkey, token);

// Profile
Profile profile = client.fetchProfile(mainKey);

// Wallet
WalletInfoResponse info = client.getWalletInfo();
```

## Architecture

```
PortalClient
  ├── Sync methods   → return result directly
  └── Async methods  → return AsyncOperation<T> { streamId, CompletableFuture<T> done }
        │
        ├── Polling path:   pollUntilComplete(op, PollOptions) → T
        └── Webhook path:   deliverWebhookPayload(rawBody, signature) → resolves done
```

The internal `StreamEvent` type is used only for per-event callbacks in `PollOptions.onEvent`
and `onEvent(streamId, callback)`. Terminal payloads are deserialized directly to `T` —
no raw JSON is ever exposed in the public API.

## Versioning & Compatibility

The Java SDK `major.minor` must match the portal-rest (sdk-daemon) version.
Patch versions are independent.

| Java SDK | sdk-daemon |
|----------|------------|
| 0.4.x    | 0.4.x      |
| 0.3.x    | 0.3.x      |
