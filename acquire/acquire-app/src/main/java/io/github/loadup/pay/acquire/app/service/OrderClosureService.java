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
package io.github.loadup.pay.acquire.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.acquire.app.security.AcquireActorProvider;
import io.github.loadup.pay.acquire.client.command.AcquireOrderCloseCommand;
import io.github.loadup.pay.acquire.client.dto.AcquireOrderDTO;
import io.github.loadup.pay.acquire.client.facade.AcquireOrderClosureFacade;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;

/**
 * Authenticated merchant entry point; background continuation uses the persisted original ownership.
 */
@Service
public class OrderClosureService implements AcquireOrderClosureFacade {
    private final AcquireActorProvider actors;
    private final OrderClosureOperations operations;
    private final Validator validator;

    public OrderClosureService(AcquireActorProvider actors, OrderClosureOperations operations, Validator validator) {
        this.actors = actors;
        this.operations = operations;
        this.validator = validator;
    }

    @Override
    public AcquireOrderDTO close(AcquireOrderCloseCommand command) {
        if (command == null || !validator.validate(command).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
        var actor = actors.current();
        if (!actor.permissions().contains("pay:acquire:close")) throw new CommonException(ACTION_FORBIDDEN);
        return operations.close(new OrderPaymentScope(actor.tenantId(), actor.scopeHash()), command);
    }
}
