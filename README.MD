# Portal Java SDK

Java 17 SDK for the [Portal REST API](https://github.com/PortalTechnologiesInc/lib).

## Installation

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories { maven { url = uri("https://jitpack.io") } }
}

// build.gradle.kts
dependencies {
    implementation("com.github.PortalTechnologiesInc:java-sdk:0.4.1")
}
```

## Setup

Choose how you want to receive async results:

```java
// Manual polling — you call pollUntilComplete() yourself, no background threads
PortalClient client = new PortalClient(
    PortalClientConfig.create("http://localhost:3000", "token")
);

// Auto-polling — background scheduler, just use done()
PortalClient client = new PortalClient(
    PortalClientConfig.create("http://localhost:3000", "token")
        .autoPolling(500) // poll every 500ms
);

// Webhooks — portal-rest POSTs to your server, just use done()
PortalClient client = new PortalClient(
    PortalClientConfig.create("http://localhost:3000", "token")
        .webhookSecret("my-secret")
);
```

## Async operations

All async methods return `AsyncOperation<T>` with `streamId` (available immediately)
and `done` (`CompletableFuture<T>` that resolves when the operation completes).

### Manual polling

```java
AsyncOperation<InvoiceStatus> op = client.requestSinglePayment(
    mainKey, List.of(),
    new SinglePaymentRequestContent("Coffee", 1000, Currency.MILLISATS, null, null, null)
);

// blocks until paid/rejected/timeout
InvoiceStatus result = client.pollUntilComplete(op, PollOptions.defaults().timeoutMs(60_000));
System.out.println(result.status); // "paid", "timeout", "user_rejected", ...
```

### Auto-polling

```java
// client configured with .autoPolling(500)
AsyncOperation<InvoiceStatus> op = client.requestSinglePayment(...);
op.done().thenAccept(result -> System.out.println(result.status));
```

### Webhooks

```java
// client configured with .webhookSecret("my-secret")
AsyncOperation<InvoiceStatus> op = client.requestSinglePayment(...);
op.done().thenAccept(result -> System.out.println(result.status));

// in your HTTP server's POST /webhook handler:
client.deliverWebhookPayload(rawBody, request.getHeader("X-Portal-Signature"));
```

## Async methods

| Method | Resolves to |
|--------|-------------|
| `requestSinglePayment(mainKey, subkeys, content)` | `AsyncOperation<InvoiceStatus>` |
| `requestPaymentRaw(mainKey, subkeys, content)` | `AsyncOperation<InvoiceStatus>` |
| `requestRecurringPayment(mainKey, subkeys, content)` | `AsyncOperation<RecurringPaymentResponseContent>` |
| `requestInvoice(recipientKey, subkeys, params)` | `AsyncOperation<InvoicePaymentResponse>` |
| `requestCashu(recipientKey, subkeys, mintUrl, unit, amount)` | `AsyncOperation<CashuResponseStatus>` |
| `authenticateKey(mainKey, subkeys)` | `AsyncOperation<AuthResponseData>` |
| `newKeyHandshakeUrl(staticToken, noRequest)` | `AsyncOperation<KeyHandshakeResult>` |
| `createVerificationSession(relayUrls)` | `VerificationSession` |
| `requestVerificationToken(recipientKey, subkeys)` | `AsyncOperation<CashuResponseStatus>` |

### Age Verification

```java
VerificationSession session = client.createVerificationSession();
System.out.println("Redirect user to: " + session.session.session_url);

CashuResponseStatus result = client.pollUntilComplete(session.operation, new PollOptions(1000, 300_000));
if (result.status.equals("success")) {
    System.out.println("Verified! Token: " + result.token);
}
```

## Sync methods

`health()`, `version()`, `info()`, `fetchProfile()`, `payInvoice()`,
`closeRecurringPayment()`, `issueJwt()`, `verifyJwt()`, `addRelay()`, `removeRelay()`,
`mintCashu()`, `burnCashu()`, `sendCashuDirect()`, `calculateNextOccurrence()`,
`fetchNip05Profile()`, `getWalletInfo()`

## Versioning

Java SDK `major.minor` must match the portal-rest (sdk-daemon) version.

| Java SDK | sdk-daemon |
|----------|------------|
| 0.4.x    | 0.4.x      |
| 0.3.x    | 0.3.x      |
