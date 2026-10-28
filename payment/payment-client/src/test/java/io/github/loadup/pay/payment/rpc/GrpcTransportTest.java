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
package io.github.loadup.pay.payment.rpc;

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.components.rpc.grpc.GrpcTransport;
import io.github.loadup.components.rpc.grpc.RpcClientProperties;
import io.github.loadup.components.rpc.grpc.RpcServerSupport;
import io.github.loadup.pay.payment.rpc.proto.PaymentRpcGrpc;
import io.github.loadup.pay.payment.rpc.proto.WireIdQuery;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentExecutionDTO;
import io.grpc.BindableService;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.springframework.grpc.client.GrpcChannelFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrpcTransportTest {
    @Test
    void nativeUnaryCallsReuseChannelAndAttachEachRotatedCredentialWithADeadline() throws Exception {
        var credentials = new ArrayList<String>();
        BindableService service = new PaymentRpcGrpc.PaymentRpcImplBase() {
            @Override
            public void detail(WireIdQuery request, StreamObserver<WirePaymentExecutionDTO> observer) {
                assertNotNull(io.grpc.Context.current().getDeadline());
                if (request.getId().equals("unavailable")) {
                    observer.onError(Status.UNAVAILABLE.asRuntimeException());
                } else {
                    observer.onNext(WirePaymentExecutionDTO.newBuilder().setPaymentId(request.getId()).build());
                    observer.onCompleted();
                }
            }
        };
        var interceptor = new ServerInterceptor() {
            @Override
            public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers,
                                                               ServerCallHandler<Q, R> next) {
                credentials.add(headers.get(RpcServerSupport.AUTHORIZATION));
                return next.startCall(call, headers);
            }
        };
        String name = InProcessServerBuilder.generateName();
        var server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(ServerInterceptors.intercept(service, interceptor)).build().start();
        var channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        var factory = mock(GrpcChannelFactory.class);
        when(factory.createChannel("payment")).thenReturn(channel);
        var transport = new GrpcTransport(factory,
                new RpcClientProperties(Map.of("payment", new RpcClientProperties.Peer("payment", Duration.ofSeconds(3)))));
        try {
            assertEquals("one", transport.call(PaymentRpcOperations.PAYMENT_DETAIL, new IdQuery("one"), "header.payload.first").paymentId());
            assertEquals("two", transport.call(PaymentRpcOperations.PAYMENT_DETAIL, new IdQuery("two"), "header.payload.second").paymentId());
            assertEquals(java.util.List.of("Bearer header.payload.first", "Bearer header.payload.second"), credentials);
            verify(factory, times(1)).createChannel("payment");
            assertThrows(io.grpc.StatusRuntimeException.class,
                    () -> transport.call(PaymentRpcOperations.PAYMENT_DETAIL, new IdQuery("unavailable"), "header.payload.third"));
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }
    }
}
