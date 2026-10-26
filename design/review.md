

─── acquire/acquire-app/src/main/java/io/github/loadup/pay/acquire/app/port/PaymentAcceptanceClient.java:35-36 ───
[maintainability · low] The new method uses fully-qualified type names while every other signature
in this interface relies on imports (`PaymentInputPrepareCommand`, `PaymentInputLookupDTO`, ...).
Please add imports for `PaymentInputExpiryDTO` and `PaymentInputExpireCommand` and use the simple
names, so the interface stays consistent and readable.

-     io.github.loadup.pay.payment.client.dto.PaymentInputExpiryDTO expireInput(String tenant,
-             io.github.loadup.pay.payment.client.command.PaymentInputExpireCommand command);
+     PaymentInputExpiryDTO expireInput(String tenant, PaymentInputExpireCommand command);


─── acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/client/PaymentAcceptanceGrpcClient.java:97-101 ───
[maintainability · low] Same fully-qualified usage here; the class already imports the other
payment-client commands/DTOs, so these should be imported too (also add the two imports at the top)
instead of inlining the package path in the signature.

      @Override
-     public io.github.loadup.pay.payment.client.dto.PaymentInputExpiryDTO expireInput(String tenant,
-             io.github.loadup.pay.payment.client.command.PaymentInputExpireCommand command) {
+     public PaymentInputExpiryDTO expireInput(String tenant, PaymentInputExpireCommand command) {
          return exchange(tenant, PAYMENT_INPUT_EXPIRE, command);
      }


─── acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/client/PaymentAcceptanceGrpcClient.java:153-153 ───
[maintainability · low] This descriptor-comparison chain has now grown past reasonable line length
and must be edited each time a new operation with typed error mapping is added (easy to forget,
silently degrading error codes to `DEPENDENCY_UNAVAILABLE`). Consider wrapping the condition one
clause per line, or extracting a `Set<MethodDescriptor<?, ?>>` of operations that share this mapping
and checking `contains(...)`.

-                     || operation.descriptor().equals(PAYMENT_INPUT_LOOKUP.descriptor()) || operation.descriptor().equals(PAYMENT_INPUT_EXPIRE.descriptor())) {
+                     || operation.descriptor().equals(PAYMENT_INPUT_LOOKUP.descriptor())
+                     || operation.descriptor().equals(PAYMENT_INPUT_EXPIRE.descriptor())) {


─── acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/OrderClosureGatewayImpl.java:88-88 ───
[bug · low] Missing null guard on `tenant`, unlike every other gateway in this package
(`AllocationRecoveryGatewayImpl`, `AcquireRecoveryRetryGatewayImpl`, `OrderFeeGatewayImpl`, ... all
use `tenant == null || !tenant.equals(TenantUtil.getTenantId())`). A null tenant yields an unhandled
NPE instead of `ACTION_FORBIDDEN`. Also worth importing `TenantUtil` rather than inlining the FQN,
for consistency with the rest of the class.

-         if (!tenant.equals(io.github.loadup.commons.util.TenantUtil.getTenantId()))
+         if (tenant == null || !tenant.equals(TenantUtil.getTenantId()))


─── acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/OrderClosureGatewayImpl.java:93-93 ───
[maintainability · low] `keepPending` reuses `converter.toClosingPatch(...)`, a patch intended for
the INIT/PENDING→CLOSING transition; it additionally writes `closeEpoch` (a no-op here since the
value is unchanged, but semantically part of closure). If closure-specific columns are ever added to
`toClosingPatch`, this INIT→PENDING deadline path would silently write them too. Consider a
dedicated mapping (e.g. `toPendingPatch` mapping only `state` and `rowVersion`) to keep the two
transitions decoupled.



─── payment/payment-app/src/main/java/io/github/loadup/pay/payment/app/service/RefundExecutionLocks.java:58-58 ───
[maintainability · low] `refunds.lock(tenant, requestId)` selects the row with
`REFUND_REQUEST_DO.ID.eq(id)` (see `RefundGatewayImpl#lock`), so `current.request().id()` is always
equal to `requestId`. Likewise `refunds.reference` selects by the same key, so `reference.id()` also
always equals `requestId`. The first clause can therefore never evaluate to true and fully
duplicates the second one — dropping it (or comparing only the two objects with `Objects.equals`)
keeps the guard readable and avoids a condition that is impossible to hit.

-         if (!current.request().id().equals(requestId) || !current.request().id().equals(reference.id())
+         if (!current.request().id().equals(reference.id())


─── acquire/acquire-app/src/main/java/io/github/loadup/pay/acquire/app/service/OrderCreationService.java:135-136 ───
[maintainability · medium] The DIRECT_PAY first-action invariants are re-implemented here, again in
`OrderCreationCodecImpl.decodeDirectPay` (`firstPaymentAmountMinor() > amountMinor()` /
`interactionKind().supports(choice().method())`), and a third time in
`DirectPaymentRecoveryService.recover`. Three copies of the same rule will drift once the
first-payment policy changes (e.g. installments), and the raw `"DIRECT_PAY"` literal is also
duplicated. Consider a single validation helper (e.g. on `DirectPayRecoverCommand`/a shared checker)
invoked from all entry points.



─── acquire/acquire-app/src/main/java/io/github/loadup/pay/acquire/app/service/OrderCreationService.java:242-242 ───
[maintainability · low] The registered deadline generation is a hardcoded literal `1L` that must
stay in sync with the persisted `deadline_version` default (`OrderCreationStorageConverter` maps
`deadlineVersion` to constant `"1L"`). If the initial stored version ever differs from the literal
used here, `OrderDeadlineService` treats the task as stale (`key.generation() < version` → `return
true`) and the order never expires. Please share a single constant (e.g.
`AcquireOrder.INITIAL_DEADLINE_VERSION`) between the storage mapping and this registration instead
of duplicating the magic number.



─── acquire/acquire-app/src/main/java/io/github/loadup/pay/acquire/app/service/OrderCreationService.java:152-152 ───
[maintainability · low] `mode` is derived from two nested ternaries whose correctness depends on
right associativity, and the same mapping is repeated in `checked()` and in
`OrderExtensionService.originalDue`. A fourth entry mode would have to be added in three places with
no compiler help. Suggest extracting a small helper (or constants) that maps the command variant to
the entry mode once and reusing it.



─── acquire/acquire-app/src/main/java/io/github/loadup/pay/acquire/app/service/OrderCreationService.java:263-263 ───
[maintainability · low] This nested ternary mixes a payload comparison for three modes inside one
boolean expression, making it easy to attach the `!`/comparison to the wrong branch. Consider
resolving the decoded payload first, then comparing:

```java
Object payload = directPay != null ? codec.decodeDirectPay(intent)
        : checkout == null ? codec.decodeIntent(intent) : codec.decodeCheckout(intent);
Object requested = directPay != null ? directPay : checkout == null ? command : checkout;
if (... || !payload.equals(requested)) throw new CommonException(IDEMPOTENCY_CONFLICT);
```



─── acquire/acquire-app/src/main/java/io/github/loadup/pay/acquire/app/port/OrderCreationCodec.java:36-36 ───
[style · low] The new members use fully-qualified type names instead of imports; the same pattern is
repeated in `OrderCreationCodecImpl`, `AcquireOrderCreationFacade` and `OrderCreationService`, which
hurts readability and is inconsistent with the rest of these files. Please add `import
io.github.loadup.pay.acquire.client.command.DirectPayRecoverCommand;` and use the simple name.

-     String encodeDirectPay(io.github.loadup.pay.acquire.client.command.DirectPayRecoverCommand command);
+     String encodeDirectPay(DirectPayRecoverCommand command);


─── acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/OrderClosureGatewayImpl.java:90-92 ───
[bug · medium] The guard re-derives the expected result with `before.orderDueAt()` as "now", so it
never proves the precondition stated in the Javadoc ("prescribed by an **expired** KEEP_PENDING
order"). For an INIT + KEEP_PENDING order whose due time is still in the future,
`checkDue(orderDueAt)` already yields the same PENDING/version+1 value, so a caller passing that
value passes every check and the row is flipped INIT -> PENDING before expiry (with a version bump
that invalidates concurrent optimistic updates). Today only `OrderDeadlineService` calls this and it
checks expiry first, but the gateway is the only enforcement point for the DB transition and should
not rely on the caller. Consider taking the observed deadline/now used for the transition and
asserting `!now.isBefore(before.orderDueAt())` (and `after.state() == AcquireOrderState.PENDING`)
instead of silently using `orderDueAt` as the clock.



─── acquire/acquire-app/src/main/java/io/github/loadup/pay/acquire/app/service/PaymentInitiationOperations.java:253-254 ───
[maintainability · low] This guard is unreachable dead code. To reach line 251 with `state == HELD
&& plan.inputRequired()`, the earlier block at line 240 (`state == HELD && plan.inputRequired() &&
preparedInputRef == null`) must have been skipped, which means `execute.preparedInputRef() != null`;
and when that block *was* taken, `attachInput(...)` re-encodes the command via
`withPreparedInput(original, reference)` with a non-blank reference validated by `validatePrepared`,
so `codec.decode(stored)` again yields a non-null `preparedInputRef`. Either drop the check or turn
it into an invariant assertion (`if (execute.preparedInputRef() == null) throw new
CommonException(SNAPSHOT_INVALID);`) so the branch cannot silently mask a future regression as an
INPUT_REQUIRED result.



─── acquire/acquire-app/src/main/java/io/github/loadup/pay/acquire/app/service/AcquireRecoveryOperationsService.java:86-87 ───
[performance · medium] `detail` performs a read-only inspection but executes it through
`transactions.write`, and `inspect` calls `orders.lockOrder` (`SELECT ... FOR UPDATE`, see
OrderCreationGatewayImpl#lockOrder) plus `allocations.lockPayment` / `cursors.lock` for
PAYMENT_ALLOCATION. Each operator poll therefore holds exclusive row locks on acquire_order (and the
payment/cursor rows) inside a read-write REQUIRES_NEW transaction, so diagnostic traffic serializes
with payment acceptance, order extension and closure on the same order. If a strictly
lock-consistent snapshot is not required for this projection, consider a non-locking read path
(find*-style gateway reads) so /detail cannot block financial writes; otherwise please document the
lock scope/duration explicitly since it is a behavioural guarantee of this endpoint.



─── acquire/acquire-app/src/main/java/io/github/loadup/pay/acquire/app/service/AcquireRecoveryOperationsService.java:165-165 ───
[maintainability · low] This mixes `||` and `&&` without parentheses. The precedence is currently
correct (`&&` binds tighter), but the intent ("future generation OR (current generation AND
mismatched due time)") is easy to misread and easy to break when the condition is extended, which
could silently accept a stale/foreign deadline generation. Please parenthesize the `&&` clause.

-                 if (generation > version || generation == version && !order.order().orderDueAt().equals(task.executeAt()))
+                 if (generation > version
+                         || (generation == version && !order.order().orderDueAt().equals(task.executeAt())))


─── payment/payment-client/src/main/java/io/github/loadup/pay/payment/client/command/PaymentRecoveryRetryCommand.java:23-23 ───
[style · low] Wildcard import deviates from the payment-client convention: all other 37 client files
(e.g. `PaymentExecuteCommand`, `PaymentInputExpireCommand`, and the queries/DTOs) import each
constraint explicitly. `PaymentRecoveryTaskQuery` in this same change has the same wildcard import.
Explicit imports keep the API contract readable and make unused constraints obvious.

- import jakarta.validation.constraints.*;
+ import jakarta.validation.constraints.NotBlank;
+ import jakarta.validation.constraints.NotNull;
+ import jakarta.validation.constraints.Positive;
+ import jakarta.validation.constraints.PositiveOrZero;
+ import jakarta.validation.constraints.Size;


─── payment/payment-client/src/main/java/io/github/loadup/pay/payment/client/dto/PaymentRecoveryRetryDTO.java:33-33 ───
[maintainability · low] `result` is a closed value set ("RETRIED" / "NO_LONGER_NEEDED") but is typed
as a bare `String`, while the producer hardcodes those literals (`String result =
"NO_LONGER_NEEDED"` / `result = "RETRIED"` in `PaymentRecoveryOperationsService`). Operations
tooling must string-match, and a typo or a new value would slip through silently. The client module
already models comparable closed value sets with enums (e.g. `PaymentInputExpiryDisposition`), so
consider a dedicated enum for this field to make the contract exhaustive and typo-proof.



─── acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/codec/OrderCreationCodecImpl.java:175-176 ───
[maintainability · low] `directPayDigest` is now the third copy of the UTF-8 byte-length framing
protocol (`requestDigest`, `checkoutDigest`, `directPayDigest`). This framing is a
persisted/idempotency-affecting protocol, so having three hand-maintained copies (including the `if
(field == null) canonical.append("-1:")` branch) means a future tweak to one copy can silently break
digest compatibility with the other entry modes. Suggest extracting one private helper (e.g.
`private String framed(String... fields)`) and reusing it from all three digest methods.

-         var canonical = new StringBuilder();
-         for (String field : Arrays.asList("acquire.direct-pay-create.v1", tenant, scope, "DIRECT_PAY", command.payeeMerchantId(),
+         return framed("acquire.direct-pay-create.v1", tenant, scope, "DIRECT_PAY", command.payeeMerchantId(),


─── acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/codec/OrderCreationCodecImpl.java:170-170 ───
[style · low] This one-line `catch` is inconsistent with
`decodeIntent`/`decodeCheckout`/`decodeAdmission` in the same class (multi-line block), and it
discards the original `failure`, which makes diagnosing corrupted `DIRECT_PAY` payloads harder than
for the other entry modes. Please format it like the sibling methods (and consider attaching the
cause).



─── acquire/acquire-app/src/main/java/io/github/loadup/pay/acquire/app/service/OrderDeadlineService.java:66-71 ───
[bug · medium] The `isCurrentLocked` return value is discarded here, so this branch only
re-validates the intent and then completes the deadline task without driving the closure forward.

A `CLOSING` order is progressed exclusively by `OrderClosureTasks` poll tasks, and a `FAILED` poll
task is only revived by an explicit operator retry (`AcquireRecoveryOperationsService.retry` →
`retryFailed`). If that chain is ever lost (attempts exhausted / task cancelled / `advance` skipped
because a concurrent poll already bumped `rowVersion`), the deadline task — which is one-shot and
never re-registered for the same `deadlineVersion` — is the last wake-up available and it silently
does nothing, leaving the order in `CLOSING` indefinitely.

Either drop the dead call, or use its result to re-drive the closure, e.g.:

```java
if (!operations.isCurrentLocked(scope, order, original, original.rowVersion())) return true;
else operations.resume(scope, original);
```



─── acquire/acquire-app/src/main/java/io/github/loadup/pay/acquire/app/service/OrderClosureRecoveryService.java:65-66 ───
[maintainability · low] This `validate(DelayedTask)` guard is byte-for-byte identical to
`OrderDeadlineService.validate(DelayedTask)` (tenant/task-type check, `OrderTaskKey.parse`, then the
`tasks.find(...)` lease/attempts/`nextExecuteAt`/`RUNNING` verification). This is the
security-sensitive “is this native claim still live” gate, so two copies will inevitably diverge
when the lease rules change. Consider extracting it (e.g. a helper on `OrderTaskKey` or a small
shared component) and reusing it from both handlers.



─── acquire/acquire-app/src/main/java/io/github/loadup/pay/acquire/app/service/OrderClosureOperations.java:167-169 ───
[maintainability · low] `actor.tenantId()` is never read in `isCurrentLocked`: the ambient tenant is
not re-verified, unlike `startLocked` in this same class
(`actor.tenantId().equals(TenantUtil.getTenantId())`) and the sibling
`AllocationRecoveryService.isCurrentLocked`. On a guard that backs operator-facing recovery paths
this silently accepts a scope bound to a different tenant; add the tenant check for consistency.



─── acquire/acquire-app/src/main/java/io/github/loadup/pay/acquire/app/service/OrderDeadlineService.java:37-37 ───
[style · low] Readability: this file (and `OrderClosureRecoveryService`) mixes wildcard imports
(`io.github.loadup.components.delayedtask.*`, `io.github.loadup.pay.acquire.domain.model.*`) with
fully-qualified usages in the middle of code
(`io.github.loadup.pay.acquire.domain.gateway.OrderExtensionGateway` in the field/constructor, `new
io.github.loadup.pay.acquire.client.command.AcquireOrderCloseCommand(...)` in `handle`,
`java.util.Optional` in `OrderClosureRecoveryService`), which is inconsistent with the
explicit-import convention used by the sibling services in this package. Please normalise the
imports.



─── payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentRecoveryRetryGatewayImpl.java:44-45 ───
[maintainability · low] `scope(tenant)` is invoked only for its tenant-assertion side effect; the
`QueryWrapper` it builds is discarded. This makes the write guard harder to read than in the rest of
the module (`PaymentRecoveryGatewayImpl`, `RefundRecoveryGatewayImpl`, ... all use a dedicated
`writable(tenant)` helper for the transaction + tenant checks). Extracting the same helper here
keeps the intent explicit and avoids building a throw-away query object on every insert.

      @Override public void insert(String tenant, PaymentRecoveryRetryReceipt receipt) {
-         scope(tenant);
+         writable(tenant);


─── payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentRecoveryRetryGatewayImpl.java:50-50 ───
[maintainability · low] Add the module-standard write guard next to `scope(...)` so `insert`
expresses intent directly (`writable(tenant)`), consistent with the other payment repositories.

+     private void writable(String tenant) {
+         if (!TransactionSynchronizationManager.isActualTransactionActive()
+                 || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
+             throw new CommonException(INVALID_STATE);
+         if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
+     }
      private QueryWrapper scope(String tenant) {


─── acquire/acquire-app/src/main/java/io/github/loadup/pay/acquire/app/service/AcquireRecoveryOperationsService.java:159-159 ───
[bug · low] `AllocationRecoveryService.isCurrentLocked` returns `false` immediately when
`cursor.completed()` (before it ever runs `settled(...)`). So a *completed* recovery cursor combined
with a still non-terminal allocation (HELD/INIT — i.e. a corrupted snapshot) is reported as
`businessCurrent == false`, and `retry` then writes a `NO_LONGER_NEEDED` receipt instead of
surfacing `SNAPSHOT_INVALID`. The payment-module counterpart (`PaymentRecoveryOperationsService`)
explicitly verifies `completed && !terminal` and fails the inspection. Consider validating
settlement terminality before treating a completed cursor as "no longer needed".



─── acquire/acquire-app/src/main/java/io/github/loadup/pay/acquire/app/service/OrderClosureOperations.java:145-145 ───
[maintainability · low] `advance(...)` (cursor bump + registration of the next-generation
`pay.acquire.order.close.progress` task) runs before this same pass decides whether the order can be
finished. When the following `transactions.write(...)` reaches `finishClose`, the just-registered
next-generation task can only no-op (`isCurrentLocked` returns false for a terminal order), which
defeats the `!terminal(order.order())` guard inside `advance()` and the documented “no further batch
once terminal” rule: every successful closure leaves one dead native task row plus one wasted
execution. Suggest making the next-cursor advance conditional on the convergence/finish decision
(e.g. advance only in the branch that returns CLOSING), or skip `tasks.schedule` when the pass
transitions the order to CLOSED.
