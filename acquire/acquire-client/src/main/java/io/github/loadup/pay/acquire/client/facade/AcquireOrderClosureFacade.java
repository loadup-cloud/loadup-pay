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
package io.github.loadup.pay.acquire.client.facade;

import io.github.loadup.pay.acquire.client.command.AcquireOrderCloseCommand;
import io.github.loadup.pay.acquire.client.dto.AcquireOrderDTO;

public interface AcquireOrderClosureFacade {
    /**
     * Persist closure and drive a bounded batch; native tasks continue CLOSING in the background.
     * A merchant replay must retain the original request key and parameters.
     */
    AcquireOrderDTO close(AcquireOrderCloseCommand command);
}
