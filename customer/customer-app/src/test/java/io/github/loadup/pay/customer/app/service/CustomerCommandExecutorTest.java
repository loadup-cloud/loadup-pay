/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.customer.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.globalunique.GlobalUniqueTemplate;
import io.github.loadup.components.globalunique.model.GlobalUniqueClaim;
import io.github.loadup.components.globalunique.model.GlobalUniqueRecord;
import io.github.loadup.pay.customer.app.security.CustomerActor;
import io.github.loadup.pay.customer.client.command.MerchantRelationshipCreateCommand;
import io.github.loadup.pay.customer.client.dto.MerchantRelationshipDTO;
import io.github.loadup.pay.customer.domain.model.GrantState;
import io.github.loadup.pay.customer.client.error.CustomerResultCodeEnum;
import io.github.loadup.pay.customer.domain.model.PrincipalType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerCommandExecutorTest {
    private final GlobalUniqueTemplate unique = mock(GlobalUniqueTemplate.class);
    private final CustomerCommandExecutor executor = new CustomerCommandExecutor(unique);
    private final CustomerActor actor = new CustomerActor("tenant", PrincipalType.OPERATOR, "operator", null, true);
    private final OffsetDateTime from = OffsetDateTime.parse("2026-10-21T09:00:00.123456+08:00");
    private final MerchantRelationshipCreateCommand command =
            new MerchantRelationshipCreateCommand("parent", "child", from, null, "request");
    private final MerchantRelationshipDTO original =
            new MerchantRelationshipDTO("relationship", "parent", "child", GrantState.ACTIVE, from, null, 1);

    private void durableStore(Map<String, GlobalUniqueRecord> records) {
        when(unique.find(anyString(), anyString()))
                .thenAnswer(call ->
                        Optional.ofNullable(records.get(call.<String>getArgument(0) + call.<String>getArgument(1))));
        when(unique.claim(any(GlobalUniqueClaim.class))).thenAnswer(call -> {
            GlobalUniqueClaim claim = call.getArgument(0);
            String key = claim.bizType() + claim.uniqueKey();
            return records.putIfAbsent(
                    key,
                    new GlobalUniqueRecord(
                            "claim",
                            "tenant",
                            claim.bizType(),
                            claim.uniqueKey(),
                            claim.bizId(),
                            claim.requestData(),
                            null,
                            null))
                    == null;
        });
    }

    @Test
    void replayReturnsOriginalSnapshotWithoutLockingOrWriting() {
        durableStore(new HashMap<>());
        AtomicInteger writes = new AtomicInteger();
        executor.execute(
                actor,
                "create.v1",
                "request",
                command,
                MerchantRelationshipDTO.class,
                () -> {
                },
                () -> new CustomerCommandExecutor.Prepared<>(original.id(), original, writes::incrementAndGet));
        MerchantRelationshipDTO replay = executor.execute(
                actor,
                "create.v1",
                "request",
                command,
                MerchantRelationshipDTO.class,
                () -> fail("Replay must not lock current state"),
                () -> {
                    throw new AssertionError("Replay must not recompute current state");
                });
        assertEquals(original, replay);
        assertEquals(1, writes.get());
        assertEquals(123456000, replay.validFrom().getNano());
    }

    @Test
    void sameKeyWithDifferentInputConflictsBeforeLocking() {
        durableStore(new HashMap<>());
        executor.execute(
                actor,
                "create.v1",
                "request",
                command,
                MerchantRelationshipDTO.class,
                () -> {
                },
                () -> new CustomerCommandExecutor.Prepared<>(original.id(), original, () -> {
                }));
        var changed = new MerchantRelationshipCreateCommand("different", "child", from, null, "request");
        CommonException failure = assertThrows(
                CommonException.class,
                () -> executor.execute(
                        actor,
                        "create.v1",
                        "request",
                        changed,
                        MerchantRelationshipDTO.class,
                        () -> fail("Must detect reused input key"),
                        () -> {
                            throw new AssertionError();
                        }));
        assertSame(CustomerResultCodeEnum.IDEMPOTENCY_CONFLICT, failure.getResultCode());
    }

    @Test
    void rechecksReceiptAfterWaitingForPayeeLock() {
        Map<String, GlobalUniqueRecord> records = new HashMap<>();
        durableStore(records);
        executor.execute(
                actor,
                "create.v1",
                "request",
                command,
                MerchantRelationshipDTO.class,
                () -> {
                },
                () -> new CustomerCommandExecutor.Prepared<>(original.id(), original, () -> {
                }));
        GlobalUniqueRecord stored = records.values().iterator().next();
        when(unique.find(anyString(), anyString())).thenReturn(Optional.empty(), Optional.of(stored));
        assertEquals(
                original,
                executor.execute(
                        actor, "create.v1", "request", command, MerchantRelationshipDTO.class, () -> {
                        }, () -> {
                            throw new AssertionError("Already committed");
                        }));
    }

    @Test
    void tenantIsPartOfTheClaimKeyEvenWhenTheUnderlyingCollationIgnoresCase() {
        durableStore(new HashMap<>());
        AtomicInteger writes = new AtomicInteger();
        executor.execute(
                actor,
                "create.v1",
                "request",
                command,
                MerchantRelationshipDTO.class,
                () -> {
                },
                () -> new CustomerCommandExecutor.Prepared<>(original.id(), original, writes::incrementAndGet));
        CustomerActor other = new CustomerActor("TENANT", PrincipalType.OPERATOR, "operator", null, true);
        executor.execute(
                other,
                "create.v1",
                "request",
                command,
                MerchantRelationshipDTO.class,
                () -> {
                },
                () -> new CustomerCommandExecutor.Prepared<>(original.id(), original, writes::incrementAndGet));
        assertEquals(2, writes.get());
    }

    @Test
    void receiptFromAnotherTenantIsRejectedBeforeReadingItsResponse() {
        when(unique.find(anyString(), anyString()))
                .thenReturn(Optional.of(new GlobalUniqueRecord(
                        "claim", "other-tenant", "create.v1", "key", "relation", null, null, null)));
        CommonException failure = assertThrows(
                CommonException.class,
                () -> executor.execute(
                        actor,
                        "create.v1",
                        "request",
                        command,
                        MerchantRelationshipDTO.class,
                        () -> fail("Must reject before locking"),
                        () -> {
                            throw new AssertionError();
                        }));
        assertSame(CustomerResultCodeEnum.ACTION_FORBIDDEN, failure.getResultCode());
    }

    @Test
    void failedPreparationDoesNotClaimAnIdempotencyKey() {
        when(unique.find(anyString(), anyString())).thenReturn(Optional.empty());
        assertThrows(
                CommonException.class,
                () -> executor.execute(
                        actor, "create.v1", "request", command, MerchantRelationshipDTO.class, () -> {
                        }, () -> {
                            throw new CommonException(CustomerResultCodeEnum.MERCHANT_INACTIVE);
                        }));
        verify(unique, never()).claim(any(GlobalUniqueClaim.class));
    }

    @Test
    void losingGlobalClaimReplaysWithoutPersistingCandidate() {
        Map<String, GlobalUniqueRecord> records = new HashMap<>();
        durableStore(records);
        executor.execute(
                actor,
                "create.v1",
                "request",
                command,
                MerchantRelationshipDTO.class,
                () -> {
                },
                () -> new CustomerCommandExecutor.Prepared<>(original.id(), original, () -> {
                }));
        GlobalUniqueRecord stored = records.values().iterator().next();
        when(unique.find(anyString(), anyString())).thenReturn(Optional.empty(), Optional.empty(), Optional.of(stored));
        when(unique.claim(any(GlobalUniqueClaim.class))).thenReturn(false);
        assertEquals(
                original,
                executor.execute(
                        actor,
                        "create.v1",
                        "request",
                        command,
                        MerchantRelationshipDTO.class,
                        () -> {
                        },
                        () -> new CustomerCommandExecutor.Prepared<>(
                                "loser", original, () -> fail("Losing candidate must not persist"))));
    }
}
