/*
 * #%L
 * LoadUp Pay Reconciliation adapter
 * %%
 * Copyright (C) 2025 - 2026 LoadUp Cloud
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package io.github.loadup.pay.reconciliation.adapter.rpc;

import io.github.loadup.pay.reconciliation.client.facade.ReconciliationFacade;
import io.github.loadup.pay.reconciliation.rpc.ReconciliationRpcMapper;
import io.github.loadup.pay.reconciliation.rpc.proto.DubboReconciliationRpcTriple;
import io.github.loadup.pay.reconciliation.rpc.proto.ReconciliationRpc;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationBatchCreateCommand;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationBatchDTO;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationBatchQuery;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationBatchRetryCommand;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationBatchStartCommand;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationBatchTaskDTO;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationInputAttachCommand;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService(interfaceClass = ReconciliationRpc.class)
public class ReconciliationRpcService extends DubboReconciliationRpcTriple.ReconciliationRpcImplBase {
    private final ReconciliationFacade reconciliation;

    private final ReconciliationRpcMapper reconciliationRpcMapper;

    public ReconciliationRpcService(
            ReconciliationRpcMapper reconciliationRpcMapper, ReconciliationFacade reconciliation) {
        this.reconciliationRpcMapper = reconciliationRpcMapper;
        this.reconciliation = reconciliation;
    }

    @Override
    public WireReconciliationBatchDTO create(WireReconciliationBatchCreateCommand request) {
        return reconciliationRpcMapper.toWire(reconciliation.create(reconciliationRpcMapper.fromWire(request)));
    }

    @Override
    public WireReconciliationBatchDTO attach(WireReconciliationInputAttachCommand request) {
        return reconciliationRpcMapper.toWire(reconciliation.attach(reconciliationRpcMapper.fromWire(request)));
    }

    @Override
    public WireReconciliationBatchDTO start(WireReconciliationBatchStartCommand request) {
        return reconciliationRpcMapper.toWire(reconciliation.start(reconciliationRpcMapper.fromWire(request)));
    }

    @Override
    public WireReconciliationBatchDTO retry(WireReconciliationBatchRetryCommand request) {
        return reconciliationRpcMapper.toWire(reconciliation.retry(reconciliationRpcMapper.fromWire(request)));
    }

    @Override
    public WireReconciliationBatchTaskDTO task(WireReconciliationBatchQuery request) {
        return reconciliationRpcMapper.toWire(reconciliation.task(reconciliationRpcMapper.fromWire(request)));
    }

    @Override
    public WireReconciliationBatchDTO detail(WireReconciliationBatchQuery request) {
        return reconciliationRpcMapper.toWire(reconciliation.detail(reconciliationRpcMapper.fromWire(request)));
    }
}
