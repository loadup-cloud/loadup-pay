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

/** Protobuf IDL entry point for the reconciliation service. */
@DubboService(interfaceClass = ReconciliationRpc.class)
public class ReconciliationRpcService extends DubboReconciliationRpcTriple.ReconciliationRpcImplBase {
    private final ReconciliationFacade reconciliation;

    public ReconciliationRpcService(ReconciliationFacade reconciliation) {
        this.reconciliation = reconciliation;
    }

    @Override
    public WireReconciliationBatchDTO create(WireReconciliationBatchCreateCommand request) {
        return ReconciliationRpcMapper.INSTANCE.toWire(reconciliation.create(ReconciliationRpcMapper.INSTANCE.fromWire(request)));
    }

    @Override
    public WireReconciliationBatchDTO attach(WireReconciliationInputAttachCommand request) {
        return ReconciliationRpcMapper.INSTANCE.toWire(reconciliation.attach(ReconciliationRpcMapper.INSTANCE.fromWire(request)));
    }

    @Override
    public WireReconciliationBatchDTO start(WireReconciliationBatchStartCommand request) {
        return ReconciliationRpcMapper.INSTANCE.toWire(reconciliation.start(ReconciliationRpcMapper.INSTANCE.fromWire(request)));
    }

    @Override
    public WireReconciliationBatchDTO retry(WireReconciliationBatchRetryCommand request) {
        return ReconciliationRpcMapper.INSTANCE.toWire(reconciliation.retry(ReconciliationRpcMapper.INSTANCE.fromWire(request)));
    }

    @Override
    public WireReconciliationBatchTaskDTO task(WireReconciliationBatchQuery request) {
        return ReconciliationRpcMapper.INSTANCE.toWire(reconciliation.task(ReconciliationRpcMapper.INSTANCE.fromWire(request)));
    }

    @Override
    public WireReconciliationBatchDTO detail(WireReconciliationBatchQuery request) {
        return ReconciliationRpcMapper.INSTANCE.toWire(reconciliation.detail(ReconciliationRpcMapper.INSTANCE.fromWire(request)));
    }
}
