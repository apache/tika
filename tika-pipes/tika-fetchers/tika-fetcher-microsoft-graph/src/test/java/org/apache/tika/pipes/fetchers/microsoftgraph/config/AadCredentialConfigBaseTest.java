/*
 *
 *  * Licensed to the Apache Software Foundation (ASF) under one or more
 *  * contributor license agreements.  See the NOTICE file distributed with
 *  * this work for additional information regarding copyright ownership.
 *  * The ASF licenses this file to You under the Apache License, Version 2.0
 *  * (the "License"); you may not use this file except in compliance with
 *  * the License.  You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

/*
 *
 *  * Licensed to the Apache Software Foundation (ASF) under one or more
 *  * contributor license agreements.  See the NOTICE file distributed with
 *  * this work for additional information regarding copyright ownership.
 *  * The ASF licenses this file to You under the Apache License, Version 2.0
 *  * (the "License"); you may not use this file except in compliance with
 *  * the License.  You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package org.apache.tika.pipes.fetchers.microsoftgraph.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AadCredentialConfigBaseTest {
    @Test
    void checkFormat() throws JsonProcessingException {
        MicrosoftGraphFetcherConfig microsoftGraphFetcherConfig = new MicrosoftGraphFetcherConfig();
        var creds = new ClientCertificateCredentialsConfig();
        microsoftGraphFetcherConfig.setCredentials(creds);
        creds.setCertificateBytes("nick".getBytes());
        creds.setCertificatePassword("xx");
        creds.setClientId("clientid");
        creds.setTenantId("tenantid");

        String str = new ObjectMapper().writeValueAsString(microsoftGraphFetcherConfig);
        MicrosoftGraphFetcherConfig backAgain = new ObjectMapper().readValue(str, MicrosoftGraphFetcherConfig.class);
        Assertions.assertEquals(microsoftGraphFetcherConfig.getCredentials().getClientId(), backAgain.getCredentials().getClientId());
        ClientCertificateCredentialsConfig backAgainCreds = (ClientCertificateCredentialsConfig) backAgain.getCredentials();
        Assertions.assertEquals(microsoftGraphFetcherConfig.getCredentials().getClientId(), backAgain.getCredentials().getClientId());
        Assertions.assertEquals("nick", new String(backAgainCreds.getCertificateBytes()));
    }
}
