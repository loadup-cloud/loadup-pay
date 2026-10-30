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

/**
 * Protobuf IDL entry point for the reconciliation service.
 */
@DubboService(interfaceClass = ReconciliationRpc.class)
public class ReconciliationRpcService extends DubboReconciliationRpcTriple.ReconciliationRpcImplBase {
    private final ReconciliationFacade reconciliation;

    private final ReconciliationRpcMapper reconciliationRpcMapper;

    public ReconciliationRpcService(ReconciliationRpcMapper reconciliationRpcMapper, ReconciliationFacade reconciliation) {
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
