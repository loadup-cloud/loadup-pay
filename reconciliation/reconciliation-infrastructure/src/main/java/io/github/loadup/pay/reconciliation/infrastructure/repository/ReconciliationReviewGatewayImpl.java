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
package io.github.loadup.pay.reconciliation.infrastructure.repository;

import com.mybatisflex.core.query.QueryWrapper;
import io.github.loadup.commons.domain.PageResult;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationReviewGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReview;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewEvent;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewOperation;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewProposal;
import io.github.loadup.pay.reconciliation.infrastructure.converter.ReconciliationReviewStorageConverter;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationReviewDO;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationReviewEventDO;
import io.github.loadup.pay.reconciliation.infrastructure.mapper.ReconciliationReviewDOMapper;
import io.github.loadup.pay.reconciliation.infrastructure.mapper.ReconciliationReviewEventDOMapper;
import io.github.loadup.pay.reconciliation.infrastructure.mapper.ReconciliationReviewProposalDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.reconciliation.infrastructure.dataobject.table.Tables.RECONCILIATION_REVIEW_DO;
import static io.github.loadup.pay.reconciliation.infrastructure.dataobject.table.Tables.RECONCILIATION_REVIEW_EVENT_DO;
import static io.github.loadup.pay.reconciliation.infrastructure.dataobject.table.Tables.RECONCILIATION_REVIEW_PROPOSAL_DO;

@Repository
public class ReconciliationReviewGatewayImpl implements ReconciliationReviewGateway {
    private final ReconciliationReviewDOMapper heads;
    private final ReconciliationReviewProposalDOMapper proposals;
    private final ReconciliationReviewEventDOMapper events;
    private final ReconciliationReviewStorageConverter converter;

    public ReconciliationReviewGatewayImpl(ReconciliationReviewDOMapper heads, ReconciliationReviewProposalDOMapper proposals,
                                           ReconciliationReviewEventDOMapper events, ReconciliationReviewStorageConverter converter) {
        this.heads = heads;
        this.proposals = proposals;
        this.events = events;
        this.converter = converter;
    }

    @Override
    public Optional<ReconciliationReview> find(String tenant, String id) {
        ReconciliationStoreContext.readable(tenant);
        return headOrInitial(tenant, id, heads.selectOneByQuery(key(tenant, id)));
    }

    @Override
    public Optional<ReconciliationReview> lock(String tenant, String id) {
        ReconciliationStoreContext.writable(tenant);
        return headOrInitial(tenant, id, heads.selectOneByQuery(key(tenant, id).forUpdate()));
    }

    @Override
    public Optional<ReconciliationReviewEvent> findRequest(String tenant, String caller, String requestKey) {
        ReconciliationStoreContext.readable(tenant);
        DomainChecks.id(caller);
        DomainChecks.text(requestKey, 128);
        return event(events.selectOneByQuery(eventScope(tenant).and(RECONCILIATION_REVIEW_EVENT_DO.CALLER_SERVICE_ID.eq(caller))
                .and(RECONCILIATION_REVIEW_EVENT_DO.REQUEST_KEY.eq(requestKey))));
    }

    @Override
    public Optional<ReconciliationReviewEvent> event(String tenant, String id) {
        ReconciliationStoreContext.readable(tenant);
        DomainChecks.id(id);
        return event(events.selectOneByQuery(eventScope(tenant).and(RECONCILIATION_REVIEW_EVENT_DO.ID.eq(id))));
    }

    @Override
    public Optional<ReconciliationReviewProposal> proposal(String tenant, String id) {
        ReconciliationStoreContext.readable(tenant);
        DomainChecks.id(id);
        var row = proposals.selectOneByQuery(QueryWrapper.create().where(RECONCILIATION_REVIEW_PROPOSAL_DO.TENANT_ID.eq(tenant))
                .and(RECONCILIATION_REVIEW_PROPOSAL_DO.DELETED.eq(0)).and(RECONCILIATION_REVIEW_PROPOSAL_DO.ID.eq(id)));
        if (row == null) return Optional.empty();
        try {
            return Optional.of(converter.proposal(row));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public PageResult<ReconciliationReviewEvent> history(String tenant, String differenceId, int page, int size) {
        ReconciliationStoreContext.readable(tenant);
        DomainChecks.id(differenceId);
        if (!TransactionSynchronizationManager.isCurrentTransactionReadOnly() || page < 1 || size < 1 || size > 100)
            throw new CommonException(INVALID_STATE);
        var query = eventScope(tenant).and(RECONCILIATION_REVIEW_EVENT_DO.DIFFERENCE_ID.eq(differenceId));
        long total = events.selectCountByQuery(query);
        var rows = events.selectListByQuery(query.orderBy(RECONCILIATION_REVIEW_EVENT_DO.SOURCE_VERSION.asc()).limit(size).offset((long) (page - 1) * size));
        return PageResult.of(rows.stream().map(row -> event(row).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID))).toList(), total, page, size);
    }

    @Override
    public void commit(String tenant, ReconciliationReview previous, ReconciliationReview next,
                       ReconciliationReviewProposal proposed, ReconciliationReviewEvent event) {
        ReconciliationStoreContext.writable(tenant);
        if (previous == null || next == null || event == null || !previous.reference().equals(next.reference()) || next.version() != Math.incrementExact(previous.version()))
            throw new CommonException(SNAPSHOT_INVALID);
        final ReconciliationReview expected;
        if (proposed != null) {
            if (event.operation() != ReconciliationReviewOperation.PROPOSE || !proposed.proposerServiceId().equals(event.callerServiceId())
                    || !proposed.proposedAt().equals(event.occurredAt())) throw new CommonException(SNAPSHOT_INVALID);
            expected = previous.propose(proposed, event.id(), previous.version());
        } else {
            if (event.operation() == ReconciliationReviewOperation.PROPOSE) throw new CommonException(SNAPSHOT_INVALID);
            var proposal = proposal(tenant, previous.proposalId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            expected = previous.decide(proposal, event.callerServiceId(), event.id(), event.operation() == ReconciliationReviewOperation.APPROVE, previous.version());
        }
        if (!expected.equals(next)) throw new CommonException(SNAPSHOT_INVALID);
        event.verifyHead(next);
        var current = lock(tenant, previous.reference().differenceId());
        if (previous.version() == 0L ? current.isPresent() : current.isEmpty() || !current.get().equals(previous))
            throw new CommonException(CONCURRENT_UPDATE);
        if (proposed != null && proposals.insert(converter.toDO(tenant, proposed)) != 1)
            throw new CommonException(CONCURRENT_UPDATE);
        if (events.insert(converter.toDO(tenant, event)) != 1) throw new CommonException(CONCURRENT_UPDATE);
        if (previous.version() == 0L) {
            if (heads.insert(converter.toDO(tenant, next)) != 1) throw new CommonException(CONCURRENT_UPDATE);
        } else if (heads.updateByQuery(converter.toDO(tenant, next), true, key(tenant, previous.reference().differenceId())
                .and(RECONCILIATION_REVIEW_DO.ROW_VERSION.eq(previous.version()))) != 1)
            throw new CommonException(CONCURRENT_UPDATE);
    }

    private Optional<ReconciliationReview> headOrInitial(String tenant, String id, ReconciliationReviewDO row) {
        if (row == null && events.selectCountByQuery(eventScope(tenant).and(RECONCILIATION_REVIEW_EVENT_DO.DIFFERENCE_ID.eq(id))) != 0)
            throw new CommonException(SNAPSHOT_INVALID);
        return head(row);
    }

    private Optional<ReconciliationReview> head(ReconciliationReviewDO row) {
        if (row == null) return Optional.empty();
        try {
            if (row.getRowVersion() == null || row.getRowVersion() < 1) throw new CommonException(SNAPSHOT_INVALID);
            return Optional.of(converter.head(row));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private Optional<ReconciliationReviewEvent> event(ReconciliationReviewEventDO row) {
        if (row == null) return Optional.empty();
        try {
            if (row.getSourceVersion() == null) throw new CommonException(SNAPSHOT_INVALID);
            return Optional.of(converter.event(row));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private QueryWrapper key(String tenant, String id) {
        DomainChecks.id(id);
        return QueryWrapper.create().where(RECONCILIATION_REVIEW_DO.TENANT_ID.eq(tenant)).and(RECONCILIATION_REVIEW_DO.DELETED.eq(0)).and(RECONCILIATION_REVIEW_DO.ID.eq(id));
    }

    private QueryWrapper eventScope(String tenant) {
        return QueryWrapper.create().where(RECONCILIATION_REVIEW_EVENT_DO.TENANT_ID.eq(tenant)).and(RECONCILIATION_REVIEW_EVENT_DO.DELETED.eq(0));
    }
}
